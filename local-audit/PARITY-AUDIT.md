# Continuum versus official Tinkers: pinned audit

September 28, 2026. Continuum misses newer official material/armor content and viewer behavior, and existing classes contain disabled client features, incomplete condition handling, and a changed ore-drop formula. Arthur's requirement is to retain all original Tinkers features; a smaller themed subset or matching item counts is not an acceptable substitute.

This audit establishes an exhaustive file inventory and a review queue. **It does not establish complete gameplay parity:** 1,527 shared Java implementations changed and have not all been behavior-reviewed. It performed no build, Minecraft launch, server test, or persistence test. The source owner is implementing and validating changes separately.

## Provenance and reproducibility

Official GitHub's latest release endpoint returned [v3.12.1.231](https://github.com/SlimeKnights/TinkersConstruct/releases/tag/v3.12.1.231), published September 22, for Minecraft 1.20.1. Its tag resolves to `a5a0324954f71b7620f766fadea0a0adf2984ae0`. The release is the behavioral reference, not an installable substitute for this pack's Minecraft 26.1.2 mod. No later official GitHub release was returned at audit time.

The two official assets were downloaded once into ignored `upstream/`, streamed as ZIPs, and checked against GitHub's published SHA-256 digests:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| TConstruct-1.20.1-3.12.1.231-sources.jar | 2,925,796 | `8240aca831b79dc76711892f0bb952bea1aa43685240f7aa9b744f3000fbdd6a` |
| TConstruct-1.20.1-3.12.1.231.jar | 23,870,231 | `474bb3e14646be1b4e343aa0d3a8482ff5db6350da19355fac6c5ddd2050ac19` |

All 1,938 Java entries in the official sources archive also match the pinned tag's Git blob IDs after allowing only line-ending normalization; `upstream-source-tag-mapping.json` records zero missing or different files.

Continuum source is [72602856c8403c51f9f488ab1580f1216fa42fda](https://github.com/justduck25/Tinker-s-Continuum/tree/72602856c8403c51f9f488ab1580f1216fa42fda); Core is [c0f1ecc5f1194f6bf746d230e6eb38d46c42d43c](https://github.com/justduck25/Continuum-Mantle/tree/c0f1ecc5f1194f6bf746d230e6eb38d46c42d43c). Both retain the MIT license and 2022 SlimeKnights notice. This is a community continuation, not a SlimeKnights release. The imported history does not establish an exact original upstream base commit.

The installed Construct jar hashes to `e053e6f60582bfa113ae3fedcc104490925a6a83a4389974e35f2aa7557df256`; Core hashes to `fb011d7faaeca846665515734b6c931408095fcca2fa2586bb4dfe325bb5ee7c`. Source `mod_version=3.12.1` does not alone prove stale code: `build.gradle` supports an `ARTIFACT_VERSION` override. The Construct release manifest says `26.1.2-3.12.2`, timestamp `2026-09-20T22:54:15+0700`, 79 seconds after the pinned merge commit's timestamp. This is correlation, not a reproducible bytecode mapping.

`source-release-mapping.json` compares immutable Git resource blob IDs, avoiding both checkout line-ending conversion and concurrent source edits:

- Construct: all 32,451 pinned resources are present; 31,690 byte matches, 756 newline-only matches, five differences. One is expanded mod metadata; four are gauge blockstates.
- Core: all 103 resources are present; 51 byte matches, 51 newline-only matches, one expanded metadata difference.
- The only additional non-class entry in each jar is its manifest.
- All four released gauge blockstates add `"type": "tconstruct:tank"` inside `variants[""]`: `seared_fuel_gauge`, `seared_ingot_gauge`, `scorched_fuel_gauge`, `scorched_ingot_gauge`. The model path is unchanged. These are release changes absent from the pinned source and must survive rebuilding. The source owner was sent the exact deltas.
- Published fixes for emptied tank/chest duplication, enchantment conversion synchronization, and duplicate tool-part swapping appear in the pinned history (`8685ce7…`, `66dae6f…`, `acbf019…`). This audit found no established release-only Java fix; it did not compare all class bytecode to a reproduced build.

## Inventory scope and interpretation

The official recursive Git tree has 32,219 entries, including 29,657 blobs, and reports `truncated=false`. `upstream-tree.json` preserves every path and Git object ID; `upstream-tree-domains.json` covers build files, tests, generated resources, and main source. The main source archive contains 1,938 Java files. Continuum's pinned main source contains 1,909: 319 equal, 1,527 changed, 92 official-only paths, and 63 port-only paths. A changed Java file requires behavior review; a missing class can be an inline implementation or API replacement.

The main official jar contains 27,494 non-class files; Continuum contains 32,452. Paths normalize only Minecraft's documented-style singular registry directories and the biome-modifier directory. JSON comparison retains list order, values, and identifiers. Results: 18,563 byte-equal, 184 newline-equal, eight JSON-equal, 11 limited adaptation candidates, 6,138 changed JSON files, 280 changed non-JSON files, 2,310 official-only paths, and 7,268 port-only paths. These are **file counts, not missing feature counts**. Official-only entries also list any equal-content candidate at another path.

`resource-json-differences.jsonl` contains every JSON-pointer difference for every changed shared JSON file; `old` is official Tinkers and `new` is the shipped Continuum jar. `resource-inventory.json` includes every resource hash and status; `source-inventory.json` includes every main-source hash and status. `registration-literals.json` compares literal registration IDs by source file. Its coverage explicitly excludes computed IDs and loops, so it is not a runtime registry dump.

`feature-domain-coverage.json` accounts for every source/resource domain. `review-queue.json` lists every changed, missing, and added file, with 17,689 open entries across the inventory. Its grouping is triage, not semantic sign-off. `upstream-release-checklist.json` also retains all 134 bullet statements from the official release notes as unverified checks; matching a filename cannot close those behaviors.

`source-absence-classification.json` separately classifies **all 92** official-only source paths: nine package metadata; four datageneration/API; five material-book content; six rendering/API replacements; five relocated-class candidates; twelve unresolved common APIs; eight modular behavior refactors; two tooltip APIs; 32 viewer/display APIs; three material swapping/migration gaps; three optional external integrations; one renamed viewer entrypoint; one gold-modifier replacement; and one villager-trade migration. Every record has a rationale and keeps parity unverified.

## Present classes with missing or changed implementation

The scan in `implementation-risk-candidates.json` covers every pinned main Java file and retains 253 added warning-sign lines with context: 116 port/disabled markers, 119 empty/constant returns, 17 commented gameplay calls, and three unconditional branches (some lines match multiple patterns). These are candidates, not 253 defects. Existing guard returns and player-toggle messages are often correct. The scanner also cannot prove that unmarked code has no missing behavior.

The following findings were checked in their callers and surrounding implementations. Paths below are relative to `src/main/java/slimeknights/tconstruct/` at the pinned commit.

| Finding | Source evidence and actual scope |
| --- | --- |
| Ore-drop multiplier changed | `tools/modifiers/loot/BonusFormula.java:83` returns `base + random.nextInt(level + 1)`. Official Tinkers uses vanilla `ApplyBonusCount.OreDrops`. Read-only `javap` of the already cached official Minecraft 26.1.2 class confirms vanilla returns `base` for nonpositive levels and otherwise `base * (max(nextInt(level + 2) - 1, 0) + 1)`. For base two/level one the vanilla outcomes are two, two, four; the port yields two or three. `ModifierBonusLootFunction` and `ChrysophiliteBonusFunction` call the changed formula. Runtime loot reachability and distributions still need fixtures. |
| Modifier conditions incomplete | `library/modifiers/ModifierManager.java:412` explicitly disables general condition parsing: only literal `neoforge:never` is evaluated. The redirect loop above it takes the first redirect without evaluating its condition. Conditional datapacks therefore need an actual target API implementation and positive/negative fixtures. |
| Placement-veto hook omitted | `library/modifiers/modules/armor/ReplaceBlockWalkerModule.java:74` comments out the old placement-event veto then changes the block. It is registered as `replace_fluid` and used by frost-walker data generation. Restore the target event contract and test claims/protection; this source review does not establish which installed protection mods can currently be bypassed. |
| Use-item movement scaling omitted | Registered `tools/ToolClientEvents.java:332` callback computes/clamps the use-item-speed value and ends without applying it to movement. The missing input adaptation is stated at line 345. Check modern input and other hooks before implementing the replacement. |
| Modifier HUD and forced-hand rendering disabled | Whole `renderHand` and `renderHotbar` methods in `tools/client/ModifierClientEvents.java` are commented out. The latter includes selected shield/sleeve/item-frame displays and the stored-map HUD. Minimap selection and map server ticking still exist; it is specifically the HUD path that is absent. |
| Fluid particle omitted | `shared/client/FluidParticle.java:15` factory always returns null. Its registration in `shared/CommonsClientEvents.java:43` is also commented out. |
| Extra-block break overlay omitted | `tools/client/ToolRenderEvents.java:117` retains the damage-overlay implementation only in a comment. Extra block-outline rendering above it is implemented, so neither mining nor all outlines should be labeled missing. |
| Developer recipe-generation command disabled | `shared/command/subcommand/GenerateMeltingRecipesCommand.java:26` emits a disabled message and returns zero. This affects the command, not normal runtime melting. |
| Datagen name injection omitted | `library/data/recipe/CraftingNBTWrapper.java:15` ignores its NBT argument. Its current caller in `tables/data/TableRecipeProvider.java:223` builds the humorous named tool-forge recipe. Verify generated item components; this is not evidence that all anvil crafting is broken. |
| Legacy model adapter is partial | `library/client/model/LegacyPassthroughModelLoader.java` strips custom loaders and delegates to `CuboidModel`; it is registered for material/tool/fluid-container/GUI legacy loaders. Modern material/item renderers also exist, so trace actual asset dispatch before assigning affected visuals. |
| Book tooltip and shortcut omissions | CycleRecipeElement and FluidItemElement have commented overlay tooltip methods while their base rendering/click behavior remains. `tables/client/inventory/TinkerStationScreen.java:485` hard-disables the macOS Command-key branch. This Windows pack does not validate the cross-platform shortcut. |

False-positive checks: `TrickQuiverModule`, `SleevesModule`, and `MinimapModule` use `DISABLED` only when a player cycles past the final inventory slot; their normal selection/use methods are implemented. The two empty `if (true)` blocks in `HeatingStructureBlockEntity` are dead statements beside implemented structure work, not proof of disabled smeltery reconstruction. These distinctions are retained rather than counting search results as bugs.

## Confirmed content gaps and changed behavior inputs

The following are source/resource findings, not observed runtime defects. They require faithful target-format implementation and tests.

| Area | Concrete official content absent or different in shipped Continuum |
| --- | --- |
| Materials | Standalone `skyslimeskin`, `enderslimeskin`, and `venom` lack their definition, stats, traits, and rendering metadata. The exact material IDs are absent from pinned `MaterialIds.java`. The port already represents sky/ender skins as `skyslime_vine#slimeskin` and `enderslime_vine#slimeskin` variants; **skin gameplay is not wholly absent**. Compare those variants' stats/traits with official standalone records and preserve old serialized IDs during restoration. |
| Travelers armor | Official wool has cuirass stats and armor knockback resistance; Continuum does not. Vine uses cuirass officially and maille in Continuum. The four `tools/armor/travelers/*_cuirass` recipes and `shield_wood` recipe are absent. `MaterialValueSwappingRecipe`, `MaterialIndexSwappingRecipe`, and `RemappingMaterialsModule` source classes are absent. |
| Armor traits | New `airborn` and `rugged` modifier resources and exact source IDs are absent. `airborne` is an existing distinct modifier and cannot substitute by spelling similarity. Leather laces give `rugged` officially and `snow_boots` in Continuum. Ender pearl slime gives `enderclearance` officially and `magic_protection` in Continuum. Enderslime vines give `enderdodging` officially and `enderclearance` in Continuum. |
| Material acquisition | Venom casting, melting, spider-eye and fermented-spider-eye material recipes; honey-block material recipe; magma composite recipe are absent at their official recipe IDs. Check equivalent recipes before declaring each acquisition impossible. |
| Modifier balance | Dragonborn's protection module is 2.0 per level officially versus 2.5 in Continuum. Entwined movement boost is 0.10 versus 0.15. Featherweight and enderdodging also differ; see full pointer diffs rather than replacing only one number. |
| Tool/armor definitions | All 45 official definition IDs exist: 30 byte-identical, 15 changed. Plate/travelers/slime chestplate attack multipliers are respectively 0.5/0.6/0.75 officially and 0.4/0.55/0.6 in Continuum. Slime wings use 0.5 officially and 0.4 in Continuum, and the official base durability 222 module is absent. |
| Slimesuit material semantics | Official primary part is index 1 while Continuum sets 0. Trim hooks use the frame at index 0 with laces/ribcage/skull/shell stats officially, but the port points at slime index 1. Official slime-helmet legacy material remapping is absent. Port slime chestplate adds ambidextrous; official has reach only. Restore this with component/load/migration checks, not a bulk JSON overwrite. |
| Recipe viewer | Latest official adds Fuel, Materials, and Tool Tinkering categories and dynamic tool focusing. Continuum has the earlier twelve JEI categories, and no dedicated REI plugin was found. The display interfaces and managers supporting much of the newer behavior are also absent. |
| Books/API | Latest official has separate laces/ribcage/shell/slime material book content and more granular modifier tooltip control. Several corresponding classes are absent. Book export and displayed stat/trait correctness remain open. |

Other upstream-only resources remain explicitly in the queue: variant-horn ingredient recipes/tag; rugged damage tags; entity-melting tags; solid-fuel example tag; command `generate_melting_recipes`; four Mantle recipe-removal resources; kiln recipes/advancements; an armor-stand loot table; optional metal melting recipes; and minotaur equipment integration. These must be resolved as mapped, restored, intentionally incompatible external integration, or demonstrated unused data. They must not disappear from the plan simply because the server starts.

## Differences that must not be mistaken for absent mechanics

- Official `chrysophilite` and `gold_guard` modifier JSON files are absent, but the port registers concrete `ChrysophiliteModifier` and `GoldGuardModifier` classes. Their behavior/balance needs comparison; re-adding the IDs would collide.
- Official splits edible behavior into several modules and hooks. Continuum has `library/modifiers/modules/behavior/EdibleModule`, used by tasty, honey, and cheese, with representative items, durability use, effect removal, and random cure handling. The split source paths do not prove edible tools are missing.
- Official JEI `JEIPlugin` corresponds to the port's `TConstructJEIPlugin`; its class name changed while newer categories really are absent.
- Fancy item-frame registration changes from `fancy_item_frame` to `item_frame` in `TinkerGadgets`. Verify entity references, save/load, rendering and interaction; this alone does not mean item frames were dropped.
- Eight grant-advancement functions match byte-for-byte after the `functions` to `function` rename. Seven biome modifiers compare after namespace adaptation; one still has JSON differences. World generation is not absent wholesale.
- Old `forge` common tags need mapping to current `c`/NeoForge tags and actual membership checks, not unconditional namespace substitution.
- There are 1,095 official-only book paths: 628 Spanish (Mexico), 268 Russian, 77 Japanese, 37 Simplified Chinese, 21 Korean, 20 Traditional Chinese, 12 Turkish, seven French, seven Brazilian Portuguese, plus 18 covers. Some may be layout/asset moves; language content and book links require review. No English book-page path is in that absent-path list, which does not prove English book correctness.
- The 674 official-only texture paths include rendering-layout changes and new content. The 97 official-only tinkering client resources include 94 modifier sprite-map files plus the three new material render records. Content-equivalent path candidates are preserved in the inventory; no blanket texture parity is claimed.
- Diet, Dummmmmmy, and Immersive Engineering plugin classes are absent. They are optional external integrations; do not install those mods just to eliminate path differences. Preserve capability requirements separately from selected-pack requirements.

## Viewer implementation boundary

The selected BMC6 client contains `RoughlyEnoughItems-26.1.819.jar`; no JEI jar was found among its mod files. Existing REI startup logs list its actual plugin providers and do not list a Tinkers provider. This is startup evidence only, not a visual test of recipe visibility. Continuum's `@JeiPlugin` and twelve JEI categories cannot be assumed to execute in REI. The current release has no REI bridge in the inspected source.

The installed REI API exposes `@me.shedaniel.rei.forge.REIPluginClient`, `REIClientPlugin.registerCategories/registerDisplays/registerEntries/registerTransferHandlers`, and `DisplayRegistry`. A narrow optional client adapter is viable without replacing the pack's recipe browser:

1. Move synchronized recipe-map retention and material/casting-cache rebuilding into a viewer-neutral client owner. Currently `TinkerClient` forwards `RecipesReceivedEvent` to `TConstructJEIPlugin` only when `jei` is installed, and that plugin stores a static map. Include reload, disconnect, and reconnect invalidation. Do not duplicate authoritative server recipes.
2. Reuse actual recipe types and `RecipeHelper.getJEIRecipes` multi-recipe expansion (despite its name, Core's helper has no JEI imports) to create REI displays for casting table/basin, melting/foundry/alloy, and part builder. Preserve fluid amounts, temperature, duration, casts, consumed/reusable inputs, and material variants.
3. Extend to station/modifier/worktable/building/molding/severing/entity melting, then the latest official Fuel/Materials/Tool Tinkering and focusing behaviors. The first adapter slice is not full viewer parity.
4. Add transfer only after conservation checks for actual menus, adjacent inventories, partial capacity, and output remainders. The current shift-click investigation makes untested transfer automation a poor first slice.
5. Arthur performs category layout, hover/focus, recipe lookup/use lookup, material animation, GUI overlap, and real remote-client tests. Headless mapper tests can check display content and cache replacement; they cannot prove layout or interaction correctness.

## Persistence checks needed after source fixes

Use versioned, disposable fixtures through the coordinator; this audit did not run them.

| Fixture | Assertions across save/reload and relevant chunk unload/reconnect |
| --- | --- |
| Multipart tool | Material order, name, damage/broken state, modifier levels/slots, persistent modifier data, overslime, fluid/tank state, and unrelated item components survive serialization. |
| Armor parity | All five slimesuit definitions preserve intended primary part, trim trait, material remapping and repair behavior; old local snapshots load without silently replacing materials. |
| Casting | Partially filled cast plus output/remaining fluid and cooldown preserve conserved quantities; completing after reload neither duplicates nor loses inputs. |
| Smeltery/foundry | Structure rebuild, fluid order/quantity/temperature, fuel, partially melted items, byproducts, and slots above 255 retain state. |
| Tank/chest placement | Fill/place/empty/break/save/reload does not resurrect implicit placement components or lose current contents. |
| Modifier conversion | Modifier/enchantment maps rebuild after datapack reload and remote join; extracting/converting consumes the correct inputs and persists results. |
| Station transfer | Shift-click and recipe-viewer transfer into full/partial inventories with no adjacent inventory, vanilla chest, and installed storage conserve all item counts and remainders. |
| Returning weapon | In-flight, returning, pickup and reload paths preserve exactly one tool with its full components, owner and modifier state. |
| Viewer reload | A second world/server with different recipes replaces caches; no recipe from the previous connection remains visible or transferable. |

## Re-running the audit

Validate an existing D: TEMP/TMP directory and reject reparse points, then run `map_release.py`, `compare_upstream.py`, `make_review_ledger.py`, `classify_absences.py`, and `scan_implementation_risks.py` with Python. These scripts read immutable pinned sources and original jars and write only this audit directory. The two official jars stay ignored and are not committed or added to the runtime. The upstream release/tag/tree responses and hashes make the inputs reviewable without fetching another checkout.

Source or data restoration should update a separate resolved-feature ledger with exact implementation commits and actual tests. These baseline manifests intentionally remain pinned to the shipped 3.12.2 artifact and original source. A successful new build does not close the open parity queue by itself.
