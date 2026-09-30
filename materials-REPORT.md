# parity/materials report

Stream: materials (Tinkers parity, Continuum Construct). Branch `parity/materials` in `<parity-workspace>\wt-materials`, created from `parity/integration` at `6d420d65`. Written 2026-09-29. Official reference: TinkersConstruct v3.12.1.231 (`a5a0324954f7`). Build version string: `3.12.2-arthur.8-materials`. Nothing was pushed, merged or deployed.

`runData` was not used (broken on this branch, see `SETUP-REPORT.md` section 8). Every provider change below was paired with a hand edit of the matching generated JSON in the same commit.

## Evidence

- **Build and FML suite** (under `tools\build-locked.ps1`, tasks `jar craftingRegression returningFixtureJar`): BUILD SUCCESSFUL, 75 tests, 70 passed, 5 skipped (the same client-only skips as the baseline), 0 failed. Baseline was 64 passed, 5 skipped. Log `<parity-workspace>\tools\logs\wt-materials-20260929-143814.log`. Artifacts in `build\libs`: `ContinuumConstruct-26.1.2-3.12.2-arthur.8-materials.jar` (SHA-256 `0ab805288c47c559b3c5d8e0b8d30e1272e2a09998c69d7602d53bc01d5b13fc`), `aebm-continuum-returning-fixture-0.0.5.jar` (`702fb01533064a3a641e7e37edd00cbd8bd42413669397ee6fa2c8f5f3662a0d`). Earlier attempt `wt-materials-20260929-134338.log` died from a host native memory shortage before any test ran (no code involved).
- **Server fixtures** (`tools\run-fixture-server.ps1`, headless, 3 GB heap, stopped cleanly, no Java process left): `aebmarmortest` 12/12, `aebmmaterialstest` 5/5, `FIXTURE_RESULT ok=True passed=17 expected=17`. Result `<parity-workspace>\server-fixture\runs\wt-materials-20260929-144238\fixture-result.json`, server log `...\logs\latest.log` in the same run directory. The first run (`wt-materials-20260929-142443`, 16/17) found the travelers crafting bug fixed in `07022941`. To run autorun this branch lacks parity/fixes' `FixtureAutorun.java`, so the fixture jar was built with an uncommitted copy of that file from `parity/fixes` `7be2d25f` (deleted afterwards, not part of any commit). The manifest used is reproduced at the end of this report.
- **Regression run of the other suites** (same jars, manifest from `parity/fixes` with my two entries, all suites except the fixes-only `aebm_continuum_fixes_parity` and `aebmpersistencetest`, whose forceload fix exists only on `parity/fixes`): 104/107. The 3 failures (`AEBM_VIEWER material_ingredient_preserves_material_and_components`, `AEBM_TOOL_TINKERING six_loaded_costs_and_indexed_holes`, `damage_focus_matches_station`, all rei-owned display checks) are identical on the fixes stream's runs of unmodified integration code (for example `server-fixture\runs\wt-fixes-20260929-130953`), so they predate this branch. Result `server-fixture\runs\wt-materials-20260929-144556\fixture-result.json`.
- **Parity ledger rerun** (`tools\parity_ledger.py`, output in the untracked `ledger-out\`): material definitions 87 preserved / 17 differ (was 79 / 25; the 17 are condition format only), tool definitions 37 / 8 (was 34 / 11), `tconstruct/mantle` 0 missing (was 4), `recipe/tools` 1 missing (was 2; the remaining one is the modifiers stream's embellishment), official-only lang keys 56 (was 204; none left in materials prefixes).

## Status table

Statuses per the brief: preserved (already equal or equivalent), restored (changed on this branch to official), replaced (26.1 has a different mechanism, named), unresolved (reason given).

### M1 Slimeskins (sky, ender)

| Item | Status | Evidence |
|:-|:-|:-|
| `skyslimeskin`, `enderslimeskin` load into MaterialRegistry with official definition, cuirass and maille stats, and official traits | preserved (runtime verified) | FML `MaterialsParityTest.everyOfficialMaterialResolvesWithOfficialDefinitionStatTypesAndTraits` |
| Part builder makes a leather maille; composite casting turns it into sky and ender slimeskin maille; venom cleans it back | preserved (runtime verified) | fixture `aebmmaterialstest` cases `part_builder_makes_leather_maille`, `composite_casting_makes_sky_and_ender_slimeskin_maille` |
| 8 `recipe/tools/materials/slimeskin/composite/*` recipes differ | preserved: format migration only. Same input, output, amount (250 dip, 50 venom cleaning) and temperature. Continuum writes the fluid as a list of alternatives; for fluids without a common tag the Continuum Core `FluidObject.ingredient` helper returns the still fluid, so the list names the same fluid twice. Functionally identical (tanks only hold the still fluid); cosmetic duplicate in recipe viewers only. | FML `slimeskinCompositeAndCleaningRecipesMatchOfficialAmounts`, table `local-tests/unit/aebm/continuumtests/data/official-materials-v3.12.1.231.json` |
| `sky_legacy_cleaning`, `ender_legacy_cleaning` | added (Continuum only, kept). They clean upstream Continuum's `skyslime_vine#slimeskin` / `enderslime_vine#slimeskin` parts back to leather with 50 mB venom. | FML `legacySkinVariantsStayLoadableAndMigrateThroughLoadedRecipes`, fixture `legacy_vine_skin_parts_migrate_without_loss` |
| Legacy IDs `skyslime_vine#slimeskin`, `enderslime_vine#slimeskin` | preserved, migrated by recipe, not by automatic remap (see Decisions 1). The IDs still resolve to their backing vine, which keeps every stat type the old leather composite could produce (binding, bowstring, cuirass, laces, maille, repair kit), so no saved item loses stats. Venom cleaning then the official dip converts a part to the standalone skin without losing the part. | same as above |
| Slimesuit piece built, repaired and skin swapped with sky, ender and venom | preserved (runtime verified): skeleton skull plus earth slime in a basin builds `[bone, earthslime]`; sky, ender and venom slime swap index 1 only; spider eyes repair it in a tinker station | fixture `slimeskull_build_skin_swap_and_repair`, `slimecage_skin_swap_keeps_ribcage` |
| Armor `setDamage(ItemStack, int)` (vanilla repair paths such as mending, `ItemStack.setDamageValue`) | restored (runtime bug fixed): `ModifiableArmorItem.setDamage` changed a copy and never wrote back on 26.1, unlike `ModifiableItem.setDamage`. Now writes back the same way. | fixture `slimeskull_build_skin_swap_and_repair` (setDamageValue check) |

### M2 Venom

| Item | Status | Evidence |
|:-|:-|:-|
| `venom` definition, stats, traits | preserved (runtime verified) | FML material parity test |
| `venombone` stats and traits differ: extra `tconstruct:skull` stat (durability 175) and skull traits `magic_bones`, `skeleton_disguise` | preserved on purpose (Decisions 2). The number is listed for balance. | FML test allowlist `EXTRA_STATS` |
| 5 lang keys `item.tconstruct.{slime_boots,slime_helmet,slime_leggings,slime_wings,slimy_chestplate}.material.tconstruct.venom` | restored | commit `2a8406aa` |
| `smeltery/casting/slime/venom/{bone,bottle}`, `smeltery/melting/venom/{eye,fermented_eye}`, `tools/materials/casting/venom`, `tools/materials/melting/venom`, `tools/materials/composite/venombone`, `venom_eye`, `venom_fermented`, `mantle/fluid_transfer/venom_bottle_fill` | preserved: format only (ingredient and fluid syntax, `id` field). Amounts equal official (250, 500, 250, 250, 250, 250, values 1 and 2). `venom/bone` names the fluid instead of the `tconstruct:venom` tag; same fluid. | FML `venomAndVenomboneMeltingAndCastingAmountsMatchOfficial` |
| `smeltery/casting/slime/venom/skull` | added (Continuum only, kept): venombone head for the Continuum-only legacy venombone slimeskull. | ledger |

### M3 Travelers cuirass and swapping

| Item | Status | Evidence |
|:-|:-|:-|
| 4 `travelers/*_cuirass` swap recipes, `shield_wood`, `*_leather`, `swapping_metal` | preserved: format only (item and tag syntax, `c:strings` name) | ledger; fixture `six_loaded_travelers_targets` |
| Shaped `travelers/{goggles,chestplate,pants,boots,shield}` crafting | restored (runtime bug fixed). The JSON is format-only equal, but on 26.1 `ShapedMaterialsRecipe.assemble` set the materials on a copy (`ToolStack.from` copies the custom data component), so every crafted travelers piece came out without materials and later initialized with its defaults (rose gold plating, leather cuirass) whatever the player put in. The first fixture run caught it (`goggles must store two materials`). Fixed by writing the tool back (`setToolMaterials`). | fixture `travelers_shaped_crafting_each_piece`; first failing run `server-fixture\runs\wt-materials-20260929-142443\fixture-result.json` |
| Wool and vine cuirass stats and traits apply | preserved (runtime verified): crafted vests carry `knockback_resistance` (wool), `solar_powered` (vine), `tanned` (leather) | fixture `travelers_shaped_crafting_each_piece` |
| Material value swap and index swap conserve materials | preserved (runtime verified) | fixture `six_loaded_travelers_targets`, `shield_log_leftover`, `extra_material_requirement_conservation` (existing), `index_swap_uses_matching_slot_and_conserves_items` (new) |
| RemappingMaterialsModule keeps old skull items | preserved (runtime verified) | fixture `short_legacy_skulls_remap_once`, `complete_legacy_skulls_roundtrip`, `old_and_new_skin_roundtrip` |
| `recipe/tools/armor/slime_skull/swapping/slime` missing | restored (provider plus JSON) | commit `4912e74f`; fixture `slimeskull_build_skin_swap_and_repair` calls it directly |
| `travelers/shield_leather` | added (Continuum only, kept): duplicate of the shield half of `boots_leather` (same cost 2, index 1), kept because saved recipe references may name it | ledger |
| `travelers_chestplate` attack multiplier 0.55 vs official 0.6 | handed to balance, not changed | Balance list |

### M4 Material semantics

| Item | Status | Evidence |
|:-|:-|:-|
| Tiers: `blaze` 2 to 3, `ender_pearl` 2 to 3, `blood` 5 to 2 | restored (no 26.1 reason; Continuum carried older values). See Decisions 3 on tier. | commit `35342506`, FML material parity test |
| Sort order: `glowstone`, `ichor`, `kobold`, `magma`, `quartz` 25 to 35 (ORDER_REPAIR + ORDER_NETHER) | restored | commit `35342506` |
| 17 other definitions (aluminum, bronze, constantan, electrum, fiery, invar, ironwood, lead, necronium, nicrosil, osmium, pewter, plated_slimewood, silver, steeleaf, treated_wood, tungsten) | preserved: condition type `forge:or` / `forge:and` became `neoforge:or` / `neoforge:and` (26.1 format). Craftability, tiers, sorting and hidden flags equal. | ledger rerun, FML test (all compat materials load with tags filled) |
| Official redirects (bloodbone, chain, rotten_flesh, platinum, tungsten) | preserved (runtime verified) | FML `officialRedirectsResolve`; see Findings 1 for saved items |
| Traits `gold`, `rose_gold`: extra `tconstruct:golden` on skull | unresolved, kept (Decisions 4): coupled to the modifiers stream's chrysophilite and gold_guard restoration | FML allowlist `EXTRA_TRAITS` |
| Traits `blazing_bone`, `necronium`, `venombone`: extra skull traits | preserved on purpose (Decisions 2) | FML allowlist `EXTRA_STATS` |
| Stats: 5 vines carry an extra empty `maille` stat | preserved on purpose: kept for saved port items (armor-parity-plan.md) | FML allowlist |
| Stats: 31 plating shield durabilities and 3 skull durabilities | handed to balance | Balance list |

### M5 Slimesuit semantics

| Item | Status | Evidence |
|:-|:-|:-|
| `slime_helmet`, `slime_leggings`, `slime_boots`, `slime_chestplate` module order | restored: generated JSON reordered to match ToolDefinitionDataProvider, which already matched official (material_traits right after material_stats). Module order decides trait insertion order among equal-priority modifiers. Chestplate now differs only in its multiplier. | commit `16374e89`, ledger rerun (37 tool definitions preserved, was 34) |
| `slime_chestplate` multiplier 0.6 vs 0.75, `slime_wings` multiplier 0.4 vs 0.5 and missing base durability 222 | handed to balance | Balance list |

### M6 Other gaps

| Item | Status | Evidence |
|:-|:-|:-|
| `mantle/remove_recipes/{ingot_smelting,nugget_smelting,netherite_smithing,vanilla_tools}` | restored. Not config gated: they are presets for `/mantle remove_recipes preset tconstruct:<name>`. The Continuum ConfigurationDataProvider declared them but runData never produced them. `vanilla_tools` now uses the official shield, bow, crossbow and fishing rod tags (26.1 names) instead of `minecraft:enchantable/durability`, which also removed mace and fungus-on-a-stick crafting. See Findings 2 for a Continuum Core bug in this command. | commit `dadbb5fd`, FML `mantleRemovalPresetsLoadThroughTheCommandLoaders` |
| `c:` glass family: `glass`, `glass_panes`, `stained_glass`, `stained_glass_panes` (block and item) | replaced by NeoForge 26.1 `c:glass_blocks` and `c:glass_panes`, membership restored: soul glass and clear stained glass in `c:glass_blocks`, clear glass in `c:glass_blocks/colorless`, clear tinted glass in `c:glass_blocks/tinted`, soul and clear stained panes in `c:glass_panes`. Before this, Tinkers glass was only in the port's legacy `c:glass/*` names, so daggers (`MINABLE_WITH_DAGGER` uses `c:glass_blocks`) and other mods did not treat it as glass. | commit `90b1b74c` |
| `c:needs_gold_tool`, `c:needs_netherite_tool` | replaced by `neoforge:needs_gold_tool` / `neoforge:needs_netherite_tool` (generated by Continuum) | `src/generated/resources/data/neoforge/tags/block/` |
| `c:needs_wood_tool` (official: `blood_vanilla_slime_grass`) | replaced by `neoforge:needs_wood_tool`; Continuum emits no wood tier tag. Wood is the lowest tier, so there is no gameplay effect. | BlockTagProvider `harvestTag` |
| `c:tools/{bows,crossbows,fishing_rods,shields,tridents}` | replaced by NeoForge `c:tools/{bow,crossbow,fishing_rod,shield,trident}`, same contents | `src/generated/resources/data/c/tags/item/tools/` |
| 11 entity groups (`c:axolotls`, `bees`, `blazes`, `ghasts`, `guardians`, `phantoms`, `silverfish`, `spiders`, `squids`, `striders`, `turtles`) | replaced by explicit entity lists in Continuum's `entity_melting` recipes (fixes stream owns entity melting). Mobs other mods add to those tags will not melt. | `recipe/smeltery/entity_melting/*.json` |
| `c/loot_modifiers/global_loot_modifiers` | replaced: NeoForge 26.1 `LootModifierManager` loads every file under `data/*/loot_modifiers`, no index file | NeoForge `LootModifierManager` |
| `c:hidden_from_recipe_viewers` creative tab tag (`tconstruct:tables`, `tconstruct:fluids`) | unresolved: no consumer in NeoForge 26.1 or REI found; the item-level tag exists. Left for the rei stream. | |
| 23 station layouts | 21 preserved (format: icon without display NBT, since 26.1 item icons render through the tool's render materials; filters written as item id or `#tag`). `tinkers_anvil`, `scorched_anvil`: restored official slot order (dust, lapis, ingot, gem, quartz). | commit `4912e74f` |
| 8 horn material names | restored | commit `2a8406aa` |
| 135 material encyclopedia keys | restored as keys; not yet displayed (Decisions 5) | commit `2a8406aa` |

## Decisions and reasons

1. **No automatic remap of legacy skin variants.** Upstream Continuum's composite made `skyslime_vine#slimeskin` from leather on any leather part: bindings, bowstrings, laces, repair kits, maille and travelers cuirass. The official standalone skins only have cuirass and maille stats, so remapping every legacy ID would strip stats from the other parts. A stat-aware remap needs an item load hook, and NeoForge 26.1 has none (Findings 1). The IDs therefore stay loadable with their vine stats, and players convert parts with the venom cleaning plus the official dip. The FML and fixture tests prove both halves.
2. **Legacy bone skulls kept.** `venombone`, `blazing_bone` and `necronium` keep their skull stats and traits and their Continuum-only slimeskull recipes (Continuum still has the venombone, blazing bone and necronium head blocks). Removing them would leave saved two-slot helmets without skull stats. Official remaps these only for one-slot saves, which Continuum's RemappingMaterialsModule already does.
3. **Tier treated as semantic.** The brief names traits, craftability, hidden flags and sort order; material tier is not a stat, it sets book tier grouping, rarity color and random mob material ranges. It was restored with the sort order. If the coordinator counts tier as balance, revert `35342506` for the three tier lines only.
4. **`golden` on gold and rose gold skulls kept for now.** Official 3.12.1 builds chrysophilite and gold_guard as JSON modifiers that make piglins neutral only at level 2 (the skull trait plus an armor trim). Continuum ships them as Java modifiers without that flag and adds `golden` so gold skulls still calm piglins. Dropping `golden` alone would make gold skulls never calm piglins. Remove it in the same merge in which the modifiers stream restores the official chrysophilite and gold_guard JSON.
5. **Encyclopedia keys added but not wired.** Continuum's Ammo/Armor/Ranged/MeleeHarvest material pages never look up `material.*.encyclopedia.<category>` (official does, after the legacy category key). Wiring it is a small change in `library/client/book/content/*MaterialContent.java`, but it would make two arthur.2 keys reachable that use a bare `%` (`material.tconstruct.skyslimeskin.encyclopedia.armor`, `material.tconstruct.enderslimeskin.encyclopedia.armor`); the book text goes through `String.format`, so they would render "Format error". The rules forbid editing existing keys, so both steps wait for the coordinator.

## Findings for the coordinator (outside my files)

1. **Saved tools are never re-verified on load.** Continuum keeps `verifyTagAfterLoad(CompoundTag)` on its tool items, but NeoForge 26.1 `IItemExtension` has no such hook and nothing calls it. Consequences: material redirects (bloodbone to venombone, chain to rose gold, tungsten, platinum, rotten flesh) are never written into saved tools, and the registry returns the unknown material for a redirect ID itself (asserted in `officialRedirectsResolve`), so a saved tool carrying one should lose that material's stats when it is next rebuilt (inferred from the code, not run); and data changes (materials, traits, tool definitions, and any `parity/balance` numbers) only reach a saved tool when something rebuilds it (tinker station, modifier, repair). Official 1.20.1 re-verified and rebuilt stats on every load. Fix location: `library/tools/nbt/ToolStack` (for example in `ensureHasData`, which runs on inventory tick) or a component upgrade; owner to be decided.
2. **Continuum Core `RemoveRecipesCommand` writes removal files to `data/<ns>/recipes/`** (1.20.1 folder). 26.1 reads `recipe/`, so the generated removal pack does nothing. Core is read-only here; the presets restored above parse and are ready once Core is fixed.
3. Composite casting fluid lists name the still fluid twice for non-common fluids (Continuum Core `FluidObject.ingredient`). Harmless, but recipe viewers show a duplicate alternative.
4. Same copy-not-write-back pattern outside my files: `tools/recipe/TippedToolTransformRecipe.getDisplayOutput` puts the potion into `ToolStack.from(copy).getPersistentData()` and never writes it back, so viewer displays of tipped tools lack the potion (rei or modifiers stream). No other `ToolStack.from(x).set*` call without write-back was found.
5. Continuum `ToolCastingRecipe` has no `fluid_swapping` flag, so every slimeskull casting recipe (19 basin plus 19 table) also acts as a helmet slime swap. Results are identical to the restored single recipe (index 1, cost 5); recipe viewers may show duplicate swap displays.
6. Pre-existing rei-owned fixture failures (also on unmodified integration): `AEBM_TOOL_TINKERING six_loaded_costs_and_indexed_holes` reports a wrong displayed cost for the travelers value swaps. The station transaction itself is right (my `six_loaded_travelers_targets` and `index_swap_*` cases pass), so the defect is in the display side of `MaterialValueSwappingRecipe`, which sits in a directory this stream owns; I left it to the rei stream, which is working on those suites, to avoid a conflicting edit.

## Balance list (numbers only, not changed on this branch)

- Plating shield durability, 31 materials, all equal to official x 22/18 (Continuum `ArmorModuleBuilder.SHIELD_DAMAGE` 22 against the official release factor 18): aluminum 234 to 286, amethyst_bronze 504 to 616, ancient 450 to 550, bronze 504 to 616, cinderslime 756 to 924, cobalt 540 to 660, constantan 450 to 550, copper 234 to 286, electrum 252 to 308, fiery 450 to 550, gold 126 to 154, hepatizon 576 to 704, invar 432 to 528, iron 270 to 330, knightmetal 360 to 440, knightslime 594 to 726, lead 216 to 264, manyullyn 630 to 770, nicrosil 504 to 616, obsidian 198 to 242, osmium 450 to 550, pewter 288 to 352, pig_iron 414 to 506, queens_slime 900 to 1100, rose_gold 162 to 198, scorched_stone 180 to 220, seared_stone 252 to 308, silver 324 to 396, slimesteel 720 to 880, steel 522 to 638, steeleaf 180 to 220 (official to Continuum).
- Tool definition multipliers: `plate_chestplate` attack 0.5 to 0.4, `slime_chestplate` 0.75 to 0.6, `travelers_chestplate` 0.6 to 0.55, `slime_wings` 0.5 to 0.4; `slime_wings` also lacks the official base durability 222 (`SetStatsModule`).
- Continuum-only skull durability (no official value, legacy): blazing_bone 205, necronium 157, venombone 175.

## Files changed outside the materials-owned list (additive, listed per the shared-file rules)

- `library/recipe/material/ShapedMaterialsRecipe.java`: new private `setToolMaterials` writes crafted tool materials back to the stack (M3 bug above). Affects the 5 travelers shaped recipes; single-material outputs (anvils, forges, fake ingots) use the unchanged `IMaterialItem` path.
- `library/tools/item/armor/ModifiableArmorItem.java`: `setDamage` writes back (the brief lists `tools/item/armor/**`; this is the only armor item class, under `library`).
- `common/data/ConfigurationDataProvider.java`: vanilla_tools preset tags (M6).
- `common/data/tags/BlockTagProvider.java`, `common/data/tags/ItemTagProvider.java`: NeoForge glass tag membership (M6 c: tags). Generated output is under `c/tags/**`, which this stream owns.
- `local-tests/returning/java/aebm/continuumtests/ReturningServerFixture.java`: one `addListener` line for the new fixture.
- `en_us.json`: 148 added keys, none edited or removed.

## Tests added

- `local-tests/unit/slimeknights/tconstruct/library/materials/MaterialsParityTest.java` (FML, 6 tests): loads the committed material JSON through the real managers of a fresh MaterialRegistry over a real resource manager (every tag reported filled so compat materials load), compares all 99 official materials (definition, stat types, default and per-stat traits with the registry's fallbacks) and 5 redirects with the official table, the legacy skin chain, slimeskin and venom recipe amounts through the registered serializers, and the Mantle presets through the command's own loaders. Restores the static registry afterwards.
- `local-tests/unit/aebm/continuumtests/data/official-materials-v3.12.1.231.json` and its generator `make_official_materials.py`.
- `local-tests/returning/java/aebm/continuumtests/MaterialsParityServerFixture.java` (`aebmmaterialstest`, 5 cases) and 2 new `aebmarmortest` cases (now 12).

## Fixture manifest entries for `local-tests/returning/fixture-suites.json` (on parity/fixes)

```json
{ "command": "aebmarmortest", "label": "AEBM_ARMOR", "owner": "materials", "position": null, "cases": ["loaded_materials_and_traits", "cuirass_stats_and_venom_values", "trim_and_rebalance_keep_material_positions", "short_legacy_skulls_remap_once", "complete_legacy_skulls_roundtrip", "old_and_new_skin_roundtrip", "six_loaded_travelers_targets", "shield_log_leftover", "extra_material_requirement_conservation", "rugged_and_airborn_actual_hooks", "travelers_shaped_crafting_each_piece", "index_swap_uses_matching_slot_and_conserves_items"] },
{ "command": "aebmmaterialstest", "label": "AEBM_MATERIALS", "owner": "materials", "position": null, "cases": ["part_builder_makes_leather_maille", "composite_casting_makes_sky_and_ender_slimeskin_maille", "legacy_vine_skin_parts_migrate_without_loss", "slimeskull_build_skin_swap_and_repair", "slimecage_skin_swap_keeps_ribcage"] }
```
