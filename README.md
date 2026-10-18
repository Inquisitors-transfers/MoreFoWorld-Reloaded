# MoreFoWorld

A reference world manager for Folia

## Runtime world-loader compatibility

The Folia world initializer selects an isolated adapter by the server's actual
structure, rather than an exact Minecraft version number. The bundled
`Folia26WorldAdapter` is built against Folia 26.3. A later build can reuse it when
the required internals remain compatible; different internals require an updated
or additional adapter.

Before selecting it, the plugin reads the adapter's compiled bytecode and checks
its direct Bukkit, Paper, Folia, Moonrise and Minecraft class/member references.
Checks cover constructor parameters, method parameters and return types, field
types, public visibility, and static/instance instruction compatibility. Adapter
initialization also checks the private VarHandles used for unloading. No world
creation or removal happens during selection. An incompatible runtime reports
the failing symbols and rejects load/unload requests without invoking the adapter.
Once a world operation begins, it is never retried with another adapter.

This is structural compatibility checking, not a guarantee of future server
behavior. Portal and respawn internals are outside this world-loader boundary.
The plugin still requires Java 25 and the declared minimum API version (26.3);
this change does not add support for older servers. Canvas retains its existing
API-based initializer. There is no synthetic flag or version-string bypass.

Run `./gradlew build` to compile, package, and run the compatibility regression
suite. Before deployment on a new server release, test creating, loading,
unloading and reloading a disposable world, plus portal and respawn behavior.

## Why is it a reference world manager?

This plugin was created sorely as a demonstration to see whether it's possible to add customized worlds in Folia aside from the default worlds. 
This plugin doesn't claim to be a full-fledged world manager like others such as [Multiverse Core](https://modrinth.com/plugin/multiverse-core) or [Worlds](https://modrinth.com/plugin/worlds-1), but rather a reference plugin for other plugins to check, tweak and implement the logic on their own.

## Hacky Patch

> These are the features that are currently not supported by Folia, but work on MoreFoWorld with some hacks.
> They don't guarantee to work similarly to Vanilla Minecraft. Use them at your own risk.

- Respawn World
- Portal Teleporting

## Commands & Permissions

| Command                                               | Permission                    | Description                                        |
|-------------------------------------------------------|-------------------------------|----------------------------------------------------|
| `/mfw current`                                        | `morefoworld.current`         | Shows the current world                            |
| `/mfw current <player>`                               | `morefoworld.current.others`  | Shows the current world of another player          |
| `/mfw teleport <world>`                               | `morefoworld.teleport`        | Teleports you to the specified world               |
| `/mfw teleport <world> <player>`                      | `morefoworld.teleport.others` | Teleports another player to the specified world    |
| `/mfw linkworld <from_world> <to_world> <nether/end>` | `morefoworld.linkportal`      | Link Portals between `from_world` and `to_world`   |
| `/mfw unlinkworld <world> <nether/end>`               | `morefoworld.linkportal`      | Un-link Portals of a `world`                       |
| `/mfw linkrespawn <from> <to>`                        | `morefoworld.linkrespawn`     | Link Respawn points between `from` and `to`        |
| `/mfw unlinkrespawn <world>`                          | `morefoworld.linkrespawn`     | Un-link Respawn points of a `world`                |
| `/mfw setspawn`                                       | `morefoworld.setspawn`        | Set the spawn point when a player joins the server |
| `/mfw setworldspawn`                                  | `morefoworld.setworldspawn`   | Set the spawn point of the current world           |
| `/mfw load <world>`                                   | `morefoworld.load`            | Load a world                                       |
| `/mfw unload <world>`                                 | `morefoworld.load`            | Unload a world                                     |
