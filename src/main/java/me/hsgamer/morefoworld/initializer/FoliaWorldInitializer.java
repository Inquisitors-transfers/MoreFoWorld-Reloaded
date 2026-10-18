package me.hsgamer.morefoworld.initializer;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/** Stable API boundary: select by runtime structure, never by Minecraft version text. */
public final class FoliaWorldInitializer implements WorldInitializer {
    private static final List<String> ADAPTERS = List.of(
            "me.hsgamer.morefoworld.initializer.Folia26WorldAdapter"
    );
    private final WorldInitializer delegate;
    private final IllegalStateException incompatibility;

    public FoliaWorldInitializer() {
        ClassLoader loader = getClass().getClassLoader();
        Logger logger = Bukkit.getLogger();
        WorldInitializer selected = null;
        StringBuilder diagnostics = new StringBuilder();
        for (String adapter : ADAPTERS) {
            try {
                List<String> problems = RuntimeAdapterCheck.check(adapter, loader);
                if (!problems.isEmpty()) {
                    diagnostics.append(adapter).append(": ").append(String.join("; ", problems)).append('\n');
                    continue;
                }
                // Initialization also verifies the adapter's private unload VarHandles.
                selected = Class.forName(adapter, true, loader).asSubclass(WorldInitializer.class)
                        .getConstructor().newInstance();
                logger.info("[MoreFoWorld] Selected runtime world adapter " + adapter + " on " + Bukkit.getVersion());
                break;
            } catch (java.io.IOException | ReflectiveOperationException | LinkageError | RuntimeException exception) {
                diagnostics.append(adapter).append(": ").append(exception).append('\n');
            }
        }
        delegate = selected;
        incompatibility = selected == null ? new IllegalStateException(
                "No compatible MoreFoWorld world adapter on " + Bukkit.getVersion()
                        + ". World load/unload is unavailable; an updated adapter is required.\n" + diagnostics) : null;
        if (incompatibility != null) {
            logger.severe("[MoreFoWorld] " + incompatibility.getMessage());
        }
    }

    @Override
    public CompletableFuture<FeedbackWorld> addWorld(WorldCreator creator) {
        if (Bukkit.getWorld(creator.name()) != null || Bukkit.getWorld(creator.key()) != null) {
            return CompletableFuture.completedFuture(Feedback.WORLD_ALREADY_EXISTS.toFeedbackWorld());
        }
        if (delegate == null) {
            return CompletableFuture.completedFuture(Feedback.ERROR.toFeedbackWorld(incompatibility));
        }
        // Never retry another adapter after an operation starts: it may already have changed world state.
        try {
            return delegate.addWorld(creator);
        } catch (LinkageError | RuntimeException exception) {
            return CompletableFuture.completedFuture(Feedback.ERROR.toFeedbackWorld(exception));
        }
    }

    @Override
    public CompletableFuture<Feedback> unloadWorld(Plugin plugin, World world, boolean save) {
        if (delegate == null) {
            return CompletableFuture.failedFuture(incompatibility);
        }
        try {
            return delegate.unloadWorld(plugin, world, save);
        } catch (LinkageError | RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}
