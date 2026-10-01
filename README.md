# Myrlin's Tinkers' Construct

Tinkers' Construct 3 for Minecraft 26.1.2 on NeoForge: modular tools, materials, modifiers, the smeltery and the foundry.

This is an unofficial fork. It is not the official Tinkers' Construct, and it is not affiliated with or endorsed by SlimeKnights or by justduck25. Please report problems with it [here](https://github.com/therealarthur/myrlins-tinkers-construct/issues), not to them.

It is a fork of justduck25's [Continuum Construct](https://github.com/justduck25/Tinker-Construct-3-NeoForge), the NeoForge 26.1 port of Tinkers' Construct. Each part of this fork was compared with official Tinkers' Construct 3.12.1 (build 3.12.1.231, Minecraft 1.20.1) and changed back to official behavior where the port differed. Upstream Continuum Construct's own README is kept in [docs/UPSTREAM-README.md](docs/UPSTREAM-README.md).

Current version: 3.12.4-myrlin.2, based on Continuum Construct 3.12.4.

## Install

You need:

- Minecraft 26.1.2 with NeoForge 26.1.2.109 or a later 26.1.2 build (Java 25).
- Continuum Core 1.12.1 or a later 1.12.x release (anything from 1.12.1 up to, but not including, 1.13). The stock CurseForge release works; no patched Core is needed.
- Optional: JEI 29.34.0.90 or later, or REI, for recipe pages.

Put `MyrlinsTinkersConstruct-26.1.2-3.12.4-myrlin.2.jar` in the `mods` folder of the server and of every client.

Do not install it next to Continuum Construct. Both use the mod id `tconstruct`, so the game refuses to start with both. To switch, remove the Continuum Construct jar and add this one. Existing worlds keep working: no item, block, fluid or other registry ID was removed or renamed. Back up your world before switching anyway, and switch the server and all clients together.

## What is different from Continuum Construct

Gameplay and data, compared with official Tinkers' Construct 3.12.1:

- A data comparison against official, last run for the 3.12.2 parity release, ended with 247 modifier definitions equal to official (217 before this work), material stat differences down from 39 to 8, tool definition differences down from 11 to 4, and official language keys missing from the port down from 204 to 8.
- Official v3.12.1 balance for modifiers (for example dragonborn 8% per level, entwined +10% speed), shields, material tiers, chestplate unarmed attack and the tools mobs spawn with.
- 135 encyclopedia entries and 17 official modifier behaviors restored, among them fireborn, sticky, silky, tank and thorns.
- Fixes: the smeltery only melts mobs it can actually hurt; rugged, frost walker and long fall cancel the damage they block completely; the tinker station preview no longer damages the tool in the station; travelers gear crafted in a crafting table keeps its materials; armor keeps repairs made through mending; saved tools recompute their stats once when loaded, without touching materials, upgrades or damage.

Screens, books and recipe viewers:

- The part builder shows its material panel in multiplayer, station titles and the Inventory label are visible again, the smeltery and foundry screens are centered like official, and tool tooltips use official wording and modifier order.
- The Tinkers books use official's small uniform font and colors and show their crafting recipes in multiplayer. These fixes are built into this mod, so the stock Continuum Core release is enough.
- REI: official layouts for all 15 Tinkers categories, a working "+" transfer on the crafting station, tinker station and both anvils, and one worktable page per recipe (25 instead of 250).
- REI's item list matches official (new in myrlin.2): fluids and buckets for compat metals that no installed mod provides (for example vibranium) are hidden, as are modifier crystals and creative slots; Tinkers fluids draw in the list; materials show as their repair kit and entities as their spawn egg. JEI gets the same hiding.
- The queen's slime block renders again (new in myrlin.2); it was invisible when placed.

From upstream:

- Continuum Construct 3.12.3 and 3.12.4 are merged in (10 upstream commits), including their fluid, fuel, casting table, part builder and thrown tool fixes. Where upstream added behavior that official 3.12.1 does not have, such as fluid particles from faucets and fluid surfaces, the upstream behavior was kept.

## How it was checked

The code of this release was tested before release with these results:

- 125 automated tests (unit tests and in-game tests run by NeoForge's test framework): 120 pass, 5 need a graphical client and are skipped in the headless run, 0 fail.
- 153 server test cases in 17 suites on a disposable dedicated server: all pass. They cover thrown tools, crafting, saving and reloading tools and casting tables, armor and materials, frost walker, solid and liquid fuels, combat, modifiers, tool building in the stations, recipe viewer data, entity melting, fancy armor stands, saved tool loading and the upstream 3.12.3 and 3.12.4 fixes.
- A dump of all 3417 registry entries in the `tconstruct` namespace (items, blocks, fluids, entities, menus, serializers and the rest) is byte for byte the same as the previous build of this fork. Every one of the 3411 entries of the Continuum Construct 3.12.2 release jar is still there; the 6 additions are the official chrysophilite attribute, the material value swapping recipe serializer and the instrument ingredient serializer. Upstream 3.12.3 and 3.12.4 added no registry entries.
- A dedicated server with only NeoForge, stock Continuum Core 1.12.1, this mod and JEI starts on a fresh world and on a copy of an existing world, and stops cleanly, with no errors.
- A real client joins a multiplayer test server and runs 78 scripted steps (every station, the smeltery and foundry, casting, REI pages and transfers, all six books): 78 pass. The book pages were compared pixel by pixel with the previous build, which carried the same fixes in a patched Continuum Core: text, fonts, colors and all 11 crafting page recipes are identical.
- Screenshots of 118 screens were compared side by side with official Tinkers' Construct 3.12.1 on Minecraft 1.20.1.

myrlin.2 changed only the recipe viewer integration and one block model. It was checked with 132 automated tests (0 fail, 5 skipped as above), a registry dump identical to myrlin.1, and a client probe that draws every one of REI's 18,177 entries: Tinkers entries with no picture went from 561 to 0.

Engineering notes from this work are in the `*-REPORT.md` files and in `local-audit/`.

## Known issues

- A saved tool whose stats changed with the official balance is refreshed when a player or mob holding it loads. A tool stored in a chest or item frame keeps its old stats until someone picks it up.
- The minotaur axe exists only with Twilight Forest installed, as in official.
- REI 26.1.819 draws other mods' fluids (water and lava included) blank in its item list. Tinkers fluids draw correctly since myrlin.2.
- The JEI hiding added in myrlin.2 compiles and follows the same rules, but was only tested with REI.

## Reporting problems

Open an issue at [github.com/therealarthur/myrlins-tinkers-construct/issues](https://github.com/therealarthur/myrlins-tinkers-construct/issues) with the Minecraft, NeoForge, Continuum Core and Myrlin's Tinkers' Construct versions, the other mods involved, the steps to reproduce, and `latest.log` or the crash report. Please do not send reports about this fork to SlimeKnights or to justduck25.

## Building from source

You need JDK 25 and Git. The build reads a few jars from a local folder given with `-PaebmDepsRoot=<folder>`:

- `<folder>/downloads/ContinuumCore-26.1.2-1.12.1.jar` (Continuum Core, compile and test dependency)
- `<folder>/runtime/bmc6-client/mods/RoughlyEnoughItems-26.1.819.jar`, `architectury-neoforge-20.1.15.jar` and `cloth-config-26.1.154.jar` (compile only, for the REI plugin)

Then run `gradlew.bat jar -PaebmDepsRoot=<folder>` (or `./gradlew` on Linux and macOS). The jar is written to `build/libs`. Generated resources live in `src/generated`; change the data providers and run `runData` instead of editing them by hand.

## Credits and license

- [SlimeKnights](https://github.com/SlimeKnights) made Tinkers' Construct and Mantle.
- justduck25 made [Continuum Construct](https://github.com/justduck25/Tinker-Construct-3-NeoForge) and Continuum Core, the NeoForge 26.1 ports this fork is built on.
- This fork is maintained by Myrlin ([therealarthur](https://github.com/therealarthur)).

Tinkers' Construct, Mantle, Continuum Construct and Continuum Core are released under the MIT License, and so is this fork. See [LICENSE](LICENSE) (Copyright (c) 2022 SlimeKnights). The copyright notice and license text must be included in all copies or substantial portions of the software.
