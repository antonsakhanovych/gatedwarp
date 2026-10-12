# GatedWarp

A server-side Fabric mod for Minecraft 26.3. Ops define warps, and ordinary players use them without ever holding vanilla `/tp` permission. The mod performs the teleport itself.

## Commands

| Command | Who |
|---|---|
| `/warp <name>` | everyone |
| `/warps` | everyone |
| `/warptp <pos>` | everyone |
| `/setwarp <name> [description]` | admins |
| `/delwarp <name>` | admins |
| `/renamewarp <old> <new>` | admins |
| `/warpinfo <name>` | admins |

`/warps` lists every warp alphabetically. Click an entry to warp, or hover it for the description and coordinates.

`/warptp` takes coordinates, for example from the teleport command setting in Xaero's World Map. It finds the nearest warp within `snapRadius` blocks in your current dimension, comparing X and Z only, and sends you to that warp's stored position.

Warp names use lowercase letters, digits, `_` and `-`, up to 32 characters.

## Permissions

Admin and bypass fall back to op level 2 when no permission mod answers. LuckPerms works with all three.

| Permission | Allows |
|---|---|
| `gatedwarp.admin` | the admin commands |
| `gatedwarp.bypass` | skipping the cooldown, warmup and combat tag |
| `gatedwarp.use` | `/warp`, `/warps` and `/warptp` when `requirePermissionToWarp` is true. Otherwise everyone can use them. Admins always can. |

## Config

`config/gatedwarp/config.json` is created on first start. Files from version 1.0.0 (`config/gatedwarp.json` and `config/warps.json`) are moved into `config/gatedwarp/` automatically.

| Key | Default | Meaning |
|---|---|---|
| `cooldownSeconds` | 30 | wait between warps |
| `warmupSeconds` | 3 | time to stand still before the teleport |
| `combatTagSeconds` | 10 | warping is blocked this long after taking damage |
| `snapRadius` | 4.0 | how far `/warptp` looks for a warp |
| `requirePermissionToWarp` | false | require `gatedwarp.use` to use warps |

Moving or taking damage during the warmup cancels the warp.

## Install

You need Java 25, Fabric Loader 0.19.5 or newer, and Fabric API. Put the jar in `mods` on the server. Warps are saved to `config/gatedwarp/warps.json`.

## Build

```sh
./gradlew build
```

The jar is in `build/libs`.

## License

CC0 1.0. See `LICENSE`.
