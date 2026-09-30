> Note from Myrlin's Tinkers' Construct: below is justduck25's Continuum Construct README as it was at upstream commit f2aefea3 (3.12.4), moved here from the repository root. It describes that project, not this fork. Only the LICENSE link was adjusted for the new folder. The fork's own README is [../README.md](../README.md).

# Continuum Construct

> A community NeoForge 26.1 port of a classic modular tool and smeltery experience.

**Continuum Construct** is the working name for this community-maintained port to **Minecraft 26.1 / NeoForge 26.1**.

This is not an official SlimeKnights release. The original mod, source code, assets, design, and license remain credited to SlimeKnights. This repository exists to keep the mod playable on the NeoForge 26.1 target while the upstream ecosystem catches up.

## Port target

| Component | Version |
|---|---|
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.78 |
| Java | 25 |
| Gradle | 9.1 |

## Current support

This port currently focuses on the core modular tool experience:

- Tools, materials, parts, modifiers, tool stats, and tooltips.
- Smeltery, foundry, melter, casting, fluids, tanks, and related rendering.
- Recipes, loot, advancements, data generation, tags, and world generation that have been ported to the current data/API format.
- Client features needed for normal play on NeoForge 26.1.

Optional compatibility currently included:

| Mod | Status |
|---|---|
| JEI | Recipe/category integration for the current port. |
| JsonThings | Flex item/block integration for Continuum Construct content where supported. |
| Apotheosis / Apothic Enchanting | Tool enchant/modifier bridge, loot category mapping, post-cap handling, and related recipe/tooltip cleanup for the supported 26.1 versions. |
| Jade | Basic block tooltip integration for fluid tanks and related tank blocks. |

Other historical integrations are not listed as supported here until those mods have usable NeoForge 26.1 builds and the compat has been tested in this port.

## Building from source

Requirements:

- Git available on the system `PATH`.
- JDK 25.
- A working internet connection for Gradle dependencies and Minecraft/NeoForge artifacts.

From the `Tcon4` directory, run:

```powershell
.\gradlew.bat compileJava
.\gradlew.bat processResources
.\gradlew.bat runData
.\gradlew.bat runClientData
```

To start the development client:

```powershell
.\gradlew.bat runClient
```

Build artifacts are written under `build/libs`. Generated resources are written under `src/generated`. Do not edit generated files manually; update the corresponding data provider or source resource and run datagen again.

## Issue reporting

Please include the following information:

- Minecraft version: `26.1.2`.
- NeoForge version/build: `26.1.2.78`.
- Port version or commit.
- Versions of other mods that may be related to the issue.
- Exact steps to reproduce the problem.
- Relevant screenshots or video.
- For crashes or runtime errors, attach the relevant `latest.log`, `debug.log`, or crash report.

Please mention whether the issue happens with this NeoForge 26.1 port only, or also happens in an official upstream build.

## Documentation

For documentation about writing addons or working with datapacks for the original project, see the [SlimeKnights documentation](https://slimeknights.github.io/docs/).

For the original project and official releases, see the [SlimeKnights project page](https://slimeknights.github.io/projects/#tinkers-construct).

## Credits and license

This port is based on the original modular tool project by [SlimeKnights](https://github.com/SlimeKnights).

This NeoForge 26.1 community port is maintained by **justduck**.

Copyright (c) 2022 SlimeKnights.

Code, textures, binaries, and documentation are licensed under the [MIT License](../LICENSE), unless a different license is noted in the relevant file or asset. The copyright notice and license text must be included in all copies or substantial portions of the software.

You may use the mod in a modpack. Modpack authors are responsible for user support for their packs. Official support from the original project applies to official upstream builds, not custom port builds.

## Jar signing

Some jars from official build servers may be signed. Under no circumstances does anyone have permission to verify signatures on jars from other mods. Signing is for informational purposes only.
