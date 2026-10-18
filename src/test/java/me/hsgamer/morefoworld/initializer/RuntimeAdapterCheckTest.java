package me.hsgamer.morefoworld.initializer;

import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Compiles an adapter once, then changes the simulated server beneath it. */
public final class RuntimeAdapterCheckTest {
    private static final String ORIGINAL = "package fixture; public class Server {"
            + "public int value; public Server() {} public String call(int x) { return \"ok\"; }}";

    @org.junit.Test
    public void checksRuntimeChangesAndBuildAdapter() throws Exception {
        verify("unchanged", ORIGINAL, null);
        verify("compatible additions", ORIGINAL.replace("public int value;", "public int extra; public int value;"), null);
        verify("no server initialization during probes", ORIGINAL.replace("public int value;",
                "static { if (System.nanoTime() != 0) throw new AssertionError(\"must not initialize\"); } public int value;"), null);
        verify("inherited public members", "package fixture; public class Server extends ServerBase { public Server() {} }"
                + " class ServerBase { public int value; public String call(int x) { return \"ok\"; } }", null);
        verify("method removed", ORIGINAL.replace("call(int x)", "renamed(int x)"), "call");
        verify("return changed", ORIGINAL.replace("String call", "Object call"), "call");
        verify("field changed", ORIGINAL.replace("int value", "long value"), "value");
        verify("method became static", ORIGINAL.replace("public String call", "public static String call"), "static/instance");
        verify("field became static", ORIGINAL.replace("public int value", "public static int value"), "static/instance");
        verify("constructor changed", ORIGINAL.replace("Server()", "Server(int x)"), "<init>");
        verify("method became private", ORIGINAL.replace("public String call", "private String call"), "not public");
        verify("class removed", null, "fixture.Server");
        List<String> actual = RuntimeAdapterCheck.check(
                "me.hsgamer.morefoworld.initializer.Folia26WorldAdapter", RuntimeAdapterCheckTest.class.getClassLoader());
        if (!actual.isEmpty()) throw new AssertionError("Build runtime rejected: " + actual);
        System.out.println("PASS: compiled Folia adapter matches the build runtime");
    }

    private static void verify(String label, String replacement, String expectedError) throws Exception {
        Path root = Files.createTempDirectory("morefoworld-adapter-test-");
        try {
            Path pkg = Files.createDirectories(root.resolve("fixture"));
            Path server = pkg.resolve("Server.java");
            Path adapter = pkg.resolve("Adapter.java");
            Files.writeString(server, ORIGINAL);
            Files.writeString(adapter, "package fixture; public class Adapter { public String run() {"
                    + "Server s = new Server(); return s.call(s.value); }}");
            compile(root, server, adapter);
            if (replacement == null) {
                Files.delete(pkg.resolve("Server.class"));
            } else {
                Files.writeString(server, replacement);
                compile(root, server);
            }
            try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()},
                    ClassLoader.getPlatformClassLoader())) {
                List<String> errors = RuntimeAdapterCheck.check("fixture.Adapter", loader, n -> n.startsWith("fixture.Server"));
                if (expectedError == null ? !errors.isEmpty() : errors.stream().noneMatch(e -> e.contains(expectedError))) {
                    throw new AssertionError(label + ": " + errors);
                }
                System.out.println("PASS: " + label);
            }
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void compile(Path root, Path... sources) {
        var args = new java.util.ArrayList<>(List.of("-d", root.toString()));
        for (Path source : sources) args.add(source.toString());
        if (ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)) != 0) {
            throw new AssertionError("Fixture compilation failed");
        }
    }
}
