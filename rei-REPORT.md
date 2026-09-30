# parity/rei report: REI recipe viewer (Continuum Construct, Tinkers parity)

Branch `parity/rei` in `<parity-workspace>\wt-rei`, based on `parity/integration` `6d420d65`. Release string `3.12.2-arthur.8-rei`. Written 2026-09-29 by the REI stream (Opus). No graphical client was started, nothing was pushed, the pack servers were not touched.

## Status by item

| Item | Status | Evidence |
|-|-|-|
| R1 per-category layouts | Done for all 15 categories, with the deviations listed below | `src/main/java/slimeknights/tconstruct/plugin/rei/category/*.java`; layout facts in `library/client/recipe/RecipeLayout.java`; `RecipeLayoutFactsTest` (6 FML tests); fixture cases `rei_layout_casting_facts_match_recipes`, `rei_layout_melting_one_display_with_controller_amounts` |
| R2 transfer ("+") | Done: crafting station, tool inventory crafting, tinker station and both anvils (modifiers, tool building). All other Tinkers categories keep "+" removed | `library/client/recipe/transfer/*`, `plugin/rei/transfer/TinkerTransferHandler.java`; `TransferConservationTest` (14 FML tests); fixture cases `rei_transfer_crafting_station_real_menu_conserves`, `rei_transfer_tinker_station_real_menu_conserves` |
| R3 focus | Done: material variant, modifier, slot, pattern and entity focus with official identities; tool tinkering item focus (kept from arthur.3); bucket, tank and can focus to the contained fluid | `library/client/recipe/RecipeFocus.java`, `plugin/rei/SmelteryDisplayGenerator.java`; `RecipeFocusWorkstationTest`; fixture cases `rei_material_focus_is_an_exact_partition`, `modifier_focus_lists_only_accepting_tools` |
| R4 workstations and tags | Done: tools with jei modifier traits, those modifiers, heater for vanilla fuel, crafting station for vanilla crafting; four `tinkering/tags/modifiers/jei/*` tags, `tags/item/fuel_examples`, `c:hidden_from_recipe_viewers` creative tab tag; filled tanks, gauges, lanterns and cans hidden unless `showFilledFluidTanks` | `library/client/recipe/RecipeWorkstations.java`, `RecipeViewerHiding.java`, `TConstructREIClientPlugin.registerToolWorkstations`; generated JSON under `src/generated/resources/data`; fixture cases `rei_tool_workstations_follow_traits`, `rei_hidden_filled_containers_keep_empty_containers` |
| R5 lang | Done: all 37 official `jei.*` and `recipe.*` keys, official English, inserted beside related keys | `src/main/resources/assets/tconstruct/lang/en_us.json` (commit `92101021`) |
| Reload safety | Done: display cache lifecycle extracted to `SnapshotGate` and tested with the shipped `ClientEntryRefresh` | `SnapshotGateTest` (2 FML tests); `ClientEntryRefreshTest` still passes |

FML suite (`jar craftingRegression returningFixtureJar` on the final code, log `<parity-workspace>\tools\logs\wt-rei-20260929-145747.log`): **90 passed, 5 skipped (the same client-only book and model cases as the baseline), 0 failed**. Baseline was 64 passed. `ClientEntryRefreshTest`, `FirstMaterialConcurrencyTest` and `SideInventoryTest` (105 assertions) pass.

Server fixtures (`tools\run-fixture-server.ps1`, headless, 3 GB, stopped by the runner; result `<parity-workspace>\server-fixture\runs\wt-rei-20260929-150840\fixture-result.json`): **36 of 38 pass**. `aebmrecipeviewertest` 14/14 (all eight new REI cases, including both real-menu transfer conservation cases), `aebmrecipemappertest` 14/14, `aebmtooltinkeringtest` 8/10. The two failures are older cases that fail the same way on `parity/fixes` runs since 13:10 today (for example `server-fixture\runs\wt-fixes-20260929-130953`) and come from code outside the viewer; see "Findings for other streams".

Artifacts (not deployed; built from the tree committed as `7d26fd0d`, so the jar's `Source-Revision` still reads the previous commit `21495c14`): `build\libs\ContinuumConstruct-26.1.2-3.12.2-arthur.8-rei.jar` (SHA-256 `e11e096befdd39e7001f293e39248c44b78f6e82385b96c7dfc70112f7f94611`) and `build\libs\aebm-continuum-returning-fixture-0.0.5.jar` (`928b0e6d7cf5f2060de7a47f68adfd5bd62567fe4ae907badb24fc4533794ffd`). No arthur.1 to .7 artifact was touched. The client keeps using `RoughlyEnoughItems-26.1.819-arthur.1.jar`: the new code uses REI's API packages plus the default plugin's public `BuiltinPlugin` IDs and `CraftingDisplay` (the arthur.7 reload gate's `ReloadManagerImpl` use is unchanged), compiled against the .819 jars that the arthur.1 patch leaves API-identical, and nothing needs REI on the server.

## Findings for other streams (not fixed here, outside my files)

1. **Tinker station tool damaging changes the real tool on preview (gameplay bug).** `tables/recipe/TinkerStationDamagingRecipe.getValidatedResult` damages a copy of the tool but passes `inv.getTinkerableStack()`, the live stack in the station's tool slot, to `ToolDamageUtil.directDamage`, and Continuum's `ToolDamageUtil.syncToolStack` (added in `e3b5e33b`) writes the damaged copy back into that stack. So computing the result preview damages the tool in the slot, and `updateInputs` then measures zero damage taken. `onFocused` does the same to its argument. Suggested fix: pass `inv.getTinkerableStack().copy()` in `getValidatedResult` and `focus.copy()` in `onFocused`. Fixture case `damage_focus_matches_station` shows it (station Damage 15, display Damage 30). The viewer side is already guarded: the mapper and REI generator only pass copies.
2. **Material value swapping shows a cost the station will not use.** `library/recipe/tinkerstation/building/MaterialValueSwappingRecipe.getRecipes` lists every display item of every matching material recipe, but the station uses `MaterialRecipeCache.findRecipe(stack)`, the first recipe that accepts the item. Bamboo planks are shown at 2 for cost 2, while the station reads them as `tconstruct:bamboo` and needs 4. Suggested fix: in the display loop, skip a stack when `MaterialRecipeCache.findRecipe(stack) != recipe`. Fixture case `six_loaded_costs_and_indexed_holes` shows it. This is the swapping code the materials stream owns.
3. REI 26.1.819 draws no fluids anywhere (sidebar, vanilla and other mods' categories). If wanted, the Tinkers plugin can register the same fluid renderer for every fluid entry through `registerEntryRenderers`; I did not, since it changes other mods' displays.
4. `jei.tconstruct.temperature` in `en_us.json` holds mojibake (`%sÂ°C`) on integration; official is `%s°C`. Not edited because existing keys are add-only.

## R1: layouts

Every category class draws the official JEI content area at the official size, from `plugin/jei/**` in v3.12.1.231, with REI adding a 4 pixel border. Positions, texture regions and text come from the official category source.

| Category | Official elements drawn | Differences and why |
|-|-|-|
| Casting table / basin (117x54) | background, 32x32 tank with scale overlay and material units, faucet stream, cast slot, table or basin block, cooling arrow with "Cooling time" tooltip, cast consumed/kept marker with tooltip | REI's own arrow (same size) instead of the texture arrow |
| Melting (132x40) | background, input, output tank with overlay, ore marker, temperature, melting time arrow, usable liquid fuels, solid fuel examples when hot enough | one page per recipe as official; the old arthur.3 split into a melter page and a smeltery page is gone, both amounts are in the output tooltip |
| Foundry (132x40) | background, input, output and byproduct tanks sharing one scale, temperature, arrow, ore marker, fuels | none |
| Alloying (172x62) | background, inputs and catalysts in recipe order sharing the output scale, "Not consumed" on catalysts, output tank, temperature, arrow, fuel with overlay | none |
| Fuel (132x40) | liquid: fuel bar and tank, fluid name, temperature, speed, "Lasts: X s / Y mb"; solid: example item cycling `fuel_examples`, "Solid Fuel", "Lasts: X s / item" | a focused solid page names the container the fuel leaves behind (lava bucket to bucket) on the item tooltip, official has no such line |
| Entity melting (150x62) | background, entity box, damage in hearts, output tank with "Per X Hearts Damage", fuel with overlay, arrow | the entity is its spawn egg at double size (or its name) instead of official's animated model: REI 26.1 has no entity widget and a hand-made one cannot be verified headlessly |
| Severing (100x38) | entity box, arrow, result slot | same entity box as above; the chance terms from arthur.3 are the arrow tooltip |
| Molding (70x57) | material, pattern pressed from above (consumed or kept, kept pattern shown again on the result), table or basin under each, arrows | REI slot backgrounds on the item slots, official draws bare items |
| Part builder (121x36) | material name row, material item (ingot pattern placeholder when free), pattern item with Reusable/Consumed, pattern button with "Cost", result | official hides containers and leftovers; here they are listed on the result tooltip because the material change matters |
| Materials (132x36) | item or fluid source, value or "Per unit: X mb", craftable marker, leftover slot, composite base, example parts and tools of the material | the pack's `melting.png` lacks official's part builder and casting table icons, so the marker draws those blocks as items |
| Modifiers (128x77) | five station inputs at station positions with empty-slot icons, tool before and after, modifier name, slot cost or slotless marker, requirements and incremental markers, level or variant text | none |
| Tool building (134x66) | tool drawn 3.7x behind the translucent cover, slot frames at the tool's station layout, parts at those slots, anvil marker, result | the result does not recompute materials from the focused parts (official `onDisplayedIngredientsUpdate`); REI has no per-render slot callback |
| Tool tinkering (128x77) | station inputs at their real station indices, tool before and after, title with tooltip, variant text | refunds and containers are on the result tooltip (official shows them nowhere) |
| Modifier worktable (121x35) | title with description tooltip, tool slot, two item slots with icons, modifier button with the options | none |

Found while doing this: REI 26.1.819 draws nothing for fluid entries (`FluidEntryDefinition$FluidEntryRenderer.render` returns right after reading the sprite; checked with javap). So Tinkers tanks were blank before this change. The layouts use the adapter's own tiled fluid renderer. REI's sidebar and other mods' fluid slots stay blank; that is an REI issue, not changed here.

## R2: transfer

REI's own transfer runs REI's server-side slot crafter (`NewInputSlotCrafter`). It grows stacks returned by slot getters without writing them back and discards an item it has already taken when the target slot refuses it. With Tinkers' copy-returning side inventory slots that is the same lost-write path as the shift-click loss (#29), so it is not used.

Instead the "+" button plans the move as ordinary PICKUP left and right clicks (`TransferPlanner`): it first returns everything in the target area to the player's inventory, then moves the recipe's items from the sources, then replays the whole plan on a `ClickModel` that follows vanilla `AbstractContainerMenu.doClick` with the real slots' rules and refuses to start unless every item identity and count is conserved and every target holds exactly what the recipe needs. `TransferExecutor` sends the clicks through the game mode and compares each real click with the model; on the first difference it stops. The server treats each click exactly like a player's click, so its own menu rules decide every move and no server-side REI support is needed. Shift-click on "+" fills as many crafts as fit (up to one stack, within a 512 click budget).

Sources and targets follow official `plugin/jei/transfer/*`: crafting station grid 0 to 8 from the player's inventory and then adjacent storage slots that hold items and allow modification; tinker station and anvils fill the station input slots only (the tool slot is left alone, as official); tool inventory crafting fills the 3x3 or 2x2 grid from the tool's own inventory, the offhand and the player's inventory, never the read-only slot holding the tool. Cleared items and leftovers only go to player inventory slots. A tinker station whose selected layout hides a needed slot says so instead of moving anything.

Conservation tests (`TransferConservationTest`, real slot classes including the side-inventory transfer slot over NeoForge transactional storage): matching stacks with the remainder returned to its source slot, partial inventory (exact missing list, nothing moved), full inventory while clearing (blocked, grid kept), empty requirements and empty inventory, component-distinct stacks, adjacent storage present and absent, storage that refuses the put-back (rest goes to the player inventory), storage that cannot release a whole stack (never used), max transfer split evenly, existing grid items cleared and reused, targets that reject an item, executor stop on the first differing click, executor full run. Every case checks exact item totals before and after.

## R3: focus

- Material focus (`MaterialValue` entries): exact variant, amounts ignored, as official `MaterialIngredientHelper`. Recipes for a material are the materials pages that produce it; uses are its part builder pages (the material name row is now an input entry, taken from the same material lookup the part builder uses on the input item) and composites built on it. The fixture checks the recipe and use sets are an exact partition of all loaded pages.
- Modifier focus: modifier ID at any level, as official `ModifierIngredientHelper`. The fixture checks that a modifier focus opens only that modifier's pages and that each page lists exactly the modifiable tools the recipe's tool requirement accepts.
- Slot, pattern and entity focus: slot type, pattern ID, entity type (baby and adult together).
- Tool tinkering item focus: kept from arthur.3 (focused `IDisplayToolTinkering` expansion).
- Bucket, tank and can focus: an item focus also matches the fluid it contains, through REI's own item to fluid support.
- Solid fuel focus: the usage of any burnable item opens the solid fuel page for that item with its burn time, as official `FuelCategory`.

## R4: workstations and tags

`RecipeWorkstations` applies official `registerRecipeCatalysts`: a tool whose tool-definition traits include a modifier in `jei/crafting`, `jei/smelting`, `jei/severing` or `jei/melting` is a workstation for vanilla crafting, vanilla smelting, severing or melting, and melee melting tools also for entity melting. The tagged modifiers themselves are workstations too. The seared heater is a workstation for REI's vanilla fuel category and the crafting station for vanilla crafting. Tool traits and modifier tags come from synchronized data, so these entries appear after REI's reload in a joined world, as official does in `registerRecipeCatalysts`.

Tags (same IDs and contents as official): `tconstruct:tinkering/tags/modifiers/jei/{crafting,melting,severing,smelting}.json`, `tconstruct:tags/item/fuel_examples.json`, `c:tags/creative_mode_tab/hidden_from_recipe_viewers.json` (tables and fluids tabs). REI reads item, block and fluid hiding tags but not creative tab tags, so the filled container duplicates are hidden with a REI filtering rule built from the same helpers that fill the fluids tab; `showFilledFluidTanks=true` shows them. Retextured table variants (official `showAllTableVariants`) are not hidden in REI: hiding them could remove the tables from REI entirely if their only creative tab stacks are the variants, and that cannot be checked without a client.

## Shared-file changes (all additive)

- `common/TinkerTags.java`: `Modifiers.CRAFTING/SMELTING/MELTING/SEVERING`, `Items.FUEL_EXAMPLES`, new `CreativeTabs.HIDDEN_IN_RECIPE_VIEWERS` and its `init()` call (official names).
- `common/data/tags/ModifierTagProvider.java`, `ItemTagProvider.java`: the official tag lines. New `common/data/tags/CreativeTabTagProvider.java`, registered in `TConstruct.gatherData` (one line).
- `runData` was broken on my base (`Components not bound yet`), so the generated JSON was written by hand to match those providers in the same commit. The fixes stream has since repaired datagen on `parity/fixes` (`7be2d25f`); a regeneration after the merge should reproduce these six files byte for byte (they match official exactly).
- `en_us.json`: 37 keys added, none edited. The existing `jei.tconstruct.temperature` value contains mojibake (`Â°`) on integration; I did not edit it (add-only rule). Official text is `%s°C`.
- `local-audit/recipe-viewer-parity-plan.md`: new "Parity .8" section. `local-audit/rei-fixture-suites.json`: my suite case lists for the fixture runner.

## What I need from the coordinator

1. Fold the case lists in `local-audit/rei-fixture-suites.json` into `local-tests/returning/fixture-suites.json` when merging (14 viewer cases, 10 tool tinkering cases, 14 mapper cases).
2. My branch has no `FixtureAutorun` (it is on `parity/fixes`). I ran the suites with a copy of my fixture jar plus the two `FixtureAutorun` classes from `wt-fixes\build\libs\aebm-continuum-returning-fixture-0.0.6.jar`, built in `<parity-workspace>\tmp\wt-rei\fixture\`. After the merge the normal build covers this.
3. Route findings 1 and 2 above to their owners; once both are fixed, `aebmtooltinkeringtest` should pass 10/10 with no viewer change.
4. Display IDs of Tinkers pages changed (the layout facts are part of the content hash), so a REI display bookmark saved on arthur.7 may point at an old page. REI keeps the serialized copy; nothing breaks, but the bookmark may not refresh.
5. Visual acceptance by Arthur with the checklist below.

## Click-through checklist for Arthur (test server, BMC6 client with REI)

Open REI, press the category tabs or use R/U on the named item.

1. Casting table (R on an iron ingot): tank shows molten iron at the right fill, faucet stream above the cast, ingot cast on a table, cooling arrow animates, hover the arrow for "Cooling time", hover the small icon under the arrow for cast kept or consumed.
2. Casting basin (R on a block of iron): same with the basin block; no cast means a taller faucet stream.
3. Melting (U on iron ore): ore on the left, molten iron tank on the right, temperature at the top, plus marker near the arrow; hover the tank for "In melter" and "In smeltery" amounts; the left strip shows lava and other fuels, with a coal-like slot when solid fuel is hot enough.
4. Foundry (U on iron ore, Foundry tab): output plus byproduct tanks side by side.
5. Alloying (R on a bronze fluid or ingot fluid): input tanks in order, catalyst tooltip "Not consumed", fuel tank at the bottom, temperature at the top.
6. Smeltery Fuel (tab, then U on coal): liquid pages show the fuel bar and "Lasts: X s / Y mb"; U on coal shows the Solid Fuel page with coal and its own seconds; U on a lava bucket also mentions the bucket it leaves.
7. Entity Melting (tab): spawn egg in the box, red heart count, output tank with "Per X Hearts Damage".
8. Severing (tab, or U on a zombie spawn egg): egg, arrow (hover for chances), zombie head in the result.
9. Molding (U on sand): pattern above, table or basin under the item and under the result.
10. Part Builder (U on a pattern or R on a pick head): material name on top, material item, pattern item marked Reusable or Consumed, pattern button with "Cost", result; hover the result for any change returned.
11. Materials (tab, or U on an iron ingot): value, part builder or casting table marker, example part and tool of the material on the right; a fluid page shows "Per unit: X mb".
12. Modifiers (tab, or U on redstone): inputs at the station positions, tool before and after, modifier name on top, slot cost bottom right, level text in the middle; icons for requirements and incremental where they apply.
13. Tool Building (R on a pickaxe): big faded pickaxe behind the slots, parts at the station positions, anvil icon on broad tools.
14. Tool Tinkering (U on a tool): title with tooltip, inputs at station positions, tool before and after.
15. Modifier Worktable (tab): title with description tooltip, tool slot, two item slots, modifier button.
16. Workstations: the category tab icons list the tables, heater and controllers; any tool with the melting modifier appears as a workstation on Melting and (if melee) Entity Melting.
17. Transfer, crafting station: open a crafting station with planks and sticks in your inventory and some junk in the grid, R on a wooden pickaxe, click "+": junk goes to your inventory, the pickaxe pattern appears, nothing is lost; shift-click "+" fills as many as possible. Try with a chest next to the station holding the planks.
18. Transfer, tinker station and anvil: open a tinker station with a tool in the tool slot, R on a modifier (for example haste), click "+": the redstone moves into the station inputs; with a layout that hides a needed slot the button says to select a layout.
19. Transfer, tool inventory: open a tool with a crafting grid, "+" on a 2x2 recipe works, a 3x3 recipe on a 2x2 grid says it is too small.
20. No "+" on the other Tinkers categories.
21. Sidebar: filled seared tanks, lanterns and copper cans are not listed (empty ones are); set `showFilledFluidTanks=true` in the client config and they come back.
22. Reconnect and `/reload`: all of the above still work, no crash on the title screen.
