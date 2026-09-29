# Fixes stream report (parity/fixes)

2026-09-29, fixes engineer (Opus). Branch `parity/fixes` in `D:\MC\tinkers-parity\wt-fixes`, based on `parity/integration` `6d420d65`. Version `3.12.2-arthur.8-fixes`, fixture jar `0.0.6`. Nothing pushed, merged or deployed. head2 and PN51 were not contacted.

Machine-readable evidence: `local-audit/VERIFICATION-ARTHUR-8-FIXES.json` (counts, jar SHA-256s, log paths). Datagen drift: `local-audit/fixes-datagen-drift.json`.

## Results

All numbers below are from the final build of `e31cec62` (jar `Source-Revision: e31cec62`) and the runs that used those jars.

- Build: `jar craftingRegression returningFixtureJar` BUILD SUCCESSFUL, log `tools\logs\wt-fixes-20260929-140011.log`. Main jar `ContinuumConstruct-26.1.2-3.12.2-arthur.8-fixes.jar` SHA-256 `c04837e03f432b346ceda3b2ef1bf972de505964487b4d65eea39b6d3be55fe2`; fixture jar `aebm-continuum-returning-fixture-0.0.6.jar` `c9bfb9f8b3510c56e71ef4909c64311eaaad3b28d319515cbfe0bdd6f0daa170`; Core `fb011d7f...` (unchanged).
- FML suite: 75 tests, 70 passed, 5 skipped, 0 failed, 0 errors. The skips are the known client-only cases: `MaterialBookContentTest` (3) and `ToolModelGeometryTest` (2). New: `FixesDataParityTest` (6 cases).
- Server fixtures (`server-fixture\runs\wt-fixes-20260929-141256\fixture-result.json`, server log in the same folder): the original 107 cases of the 12 commands: 104 passed, 3 failed, all owned by rei (below). Every fixes, materials and modifiers case passes: returning 9/9, crafting 4/4, persistence 9/9 (7 original plus 2 new), armor 10/10, block walker 5/5, solid fuel 10/10, combat 6/6, recipe mapper 14/14, entity melting 7/7, fancy stand 19/19. New suite `aebm_continuum_fixes_parity` 6/6. Total 112 of 115.
- Negative controls, both failing as intended: unknown command (`runs\wt-fixes-20260929-141843`) and persistence over a ticking spawn column (`runs\wt-fixes-20260929-141947`, AIOOBE at `ChunkHolder.blockChanged`). Runner parser self-test 13/13.
- runData: works headlessly (F8).

## F1 Fixture server harness (done)

- `D:\MC\tinkers-parity\server-fixture\`: `installer\` (official NeoForge 26.1.2.109 installer, SHA-256 `e7b68f38...0280`, equal to the value published on maven.neoforged.net), `server\` (the installer's installServer output, used read-only as the library set), `runs\<stream>-<stamp>\` (one disposable directory per run: `mods\` with exactly the Continuum, Core and fixture jars, `server.properties`, `plan.txt`, `launch.cmd`, a fresh flat world, `logs\latest.log`, `fixture-result.json`), `controls\` (negative-control manifest).
- `D:\MC\tinkers-parity\tools\run-fixture-server.ps1`: `-Install` once; then `-Worktree <wt> -Version <v>` runs every suite in `<wt>\local-tests\returning\fixture-suites.json`. `-Only a,b` runs a subset, `-ExtraCommand` appends raw commands, `-JvmProperty name=value` passes a `-D` property, `-SelfTest` tests the parser, `-VerifyLog` re-checks a saved log. The server runs through `build-locked.ps1 -RawCmd` (same mutex as Gradle), `-Xmx3g`, `nogui`, stdin closed, bound to 127.0.0.1 on a free port, `online-mode=false`. Per-run directories mean preparing a run while another stream holds the lock is safe. A full run takes 40 to 95 seconds under the lock.
- `local-tests/returning/java/aebm/continuumtests/FixtureAutorun.java` (fixture source set only; the main jar contains no `aebm/continuumtests` class): inert unless `-Daebm.fixture.autorun=<plan>`. After server start it runs one plan step per tick (`forceload add`, `!await_loaded`, `!wait`, plain and `execute positioned x y z run ...` commands), refuses unparsable commands (`AEBM_AUTORUN_UNKNOWN_COMMAND`), logs `AEBM_AUTORUN_*` markers and stops the server like `stop`. A watchdog stops it after `aebm.fixture.autorun.timeoutSeconds` (default 900) and halts the JVM 60 s later.
- `local-tests/returning/fixture-suites.json`: every suite's command, label, owner, position and full case list. The runner fails a suite on a missing or repeated summary, a summary that disagrees with the PASS/FAIL lines, a listed case with no line, a repeated label, an unlisted label, a REFUSED line or any FAIL; it fails the run on unplanned suite output, an unknown command, an autorun error or timeout, or a missing END marker (crash). The forceloaded test area (x/z 2048..2111) is far from spawn so unpositioned suites run over an unloaded spawn column, as in the head2 console runs.
- Self-test: `-SelfTest` 13 synthetic logs (good, unknown command, missing summary, duplicate label, contradictory summary, missing case, FAIL line, REFUSED, missing END, repeated summary, unlisted suite, unplanned suite, autorun error), all verdicts as expected: `server-fixture\runner-selftest-20260929.txt`. Real negative control: `-Only aebmcraftingtest -ExtraCommand aebm_no_such_command` fails the run although crafting passes 4/4.
- Fixture environment finding: `aebmpersistencetest consumed_cast_mid_cooling_reload` failed with `ArrayIndexOutOfBoundsException: Index 25 out of bounds for length 24` when the console column was forceloaded. Stack: `CastingBlockEntity.setItem` -> `ServerLevel.sendBlockUpdated` -> `ChunkHolder.blockChanged`, which indexes the section array without a height check for ticking chunks; the fixture's casting table sits at build height + 32. Reproduced on demand with `-Manifest server-fixture\controls\persistence-spawn-forceload.json -Only aebmpersistencetest -JvmProperty aebm.fixture.persistence.sourceColumn=true`. Real casting tables are always inside build height, so this is not a production defect. The fixture now steps to an unloaded column and logs a full stack on any failure.

For other streams: add a new suite as one line in your worktree's `fixture-suites.json` (command, label prefix, owner, position or null, case names), build `jar returningFixtureJar`, then run the script with your `-Worktree` and `-Version`.

## F3 Solid fuel `d3576350`: restored

10/10 `aebmsolidfueltest`. Compared with official `SolidFuelModule.trySolidFuel` and `HeaterItemHandler.isItemValid`: official `ForgeHooks.getBurnTime(stack, FUEL)` is the item override followed by the burn-time event; Continuum's `stack.getBurnTime(FUEL, fuelValues)` is the NeoForge 26.1 equivalent (override first, event last, zero veto authoritative), with the same `/4` and `>3` threshold. One deliberate difference stays: Continuum drops only the uninserted remainder (`notInserted`), where official drops the whole container and duplicates the inserted part.

## F4 Entity melting after rejected damage `50ceff5a`: restored

7/7 `aebm_continuum_entity_melting`. Matches official `EntityMeltingModule.interactWithEntities`: the tank fills only when the damage call returns true. The skip check is now the official `melting/blacklist` tag instead of `hide_in_default` (see F6).

## F5 Fancy armor stand drops `50ceff5a`: restored, loot table restored

19/19 `aebm_continuum_fancy_stand`. `brokenByPlayer` pops the stand item and calls `brokenByAnything`, exactly as official. Decision: restore the table. Official ships an empty `entities/armor_stand` table (as vanilla does for its stand) so datapacks can add death loot. The port registered the entity with `.noLootTable()`, so no table was consulted or generated. `TinkerGadgets` drops `.noLootTable()`, `EntityLootTableProvider` adds the official entry, and runData wrote `data/tconstruct/loot_table/entities/armor_stand.json`. Default drops are unchanged (empty table). Tests: `FixesDataParityTest.fancyStandLootTableRoundTripsThroughTheTargetCodec` (codec round trip, entity key) and `aebm_continuum_fixes_parity fancy_stand_loot_table_loaded_and_empty` (loaded on a server, rolls nothing).

## F6 Smeltery residuals

- 7 `meltable/*` tags and `melting/blacklist` (missing): **restored** with official membership. `TinkerTags.EntityTypes` constants (additive), `EntityTypeTagProvider`, generated `tags/entity_type/meltable/*` and `melting/blacklist`. Tests: `FixesDataParityTest.meltingTagsMatchOfficialLists`, fixture `meltable_tags_match_official_members`.
- `melting/hide_in_default` (differed): **restored** to giant, `#tconstruct:melting/blacklist` and optional `#c:hidden_from_recipe_viewers`. Tests: `hiddenDefaultTagIncludesTheBlacklistLikeOfficial`, fixture `hide_in_default_keeps_official_members`.
- Entity melting recipes zombie, drowned, skeletons, ender, slime, magma_cube, meat_soup: **restored** to the official tag ingredients. Same vanilla members, so no gameplay change for vanilla mobs; packs can extend the tags again. Tests: `entityMeltingRecipesUseTheOfficialTags`, fixture `tagged_entity_melting_recipes_cover_every_member`.
- Smeltery skip check: **restored** to official `MELTING_BLACKLIST`; hidden-only entities (giants) melt again. Test: fixture `hidden_only_giant_still_melts_like_official`.
- The other 7 differing entity tags (collectables, discardable, enderference, killagers, necrotic, both reflecting tags): **preserved**. The only difference is `#forge:` versus `#c:` inside tag values, which the ledger normalizes for directories only.
- `seared_brick_kiln` and `scorched_brick_kiln` recipes and advancements: blasting branch **restored** under the official IDs (grout blasts to brick in 100 ticks); advancements sit at the 26.1 path `advancement/recipes/building_blocks/smeltery/*/`. The Ceramics `ceramics:kiln` branch is **unresolved**: Ceramics has no build in this pack and its kiln recipe format cannot be verified. Tests: `groutKilnRecipesRestoreTheOfficialBlastingBranch`, fixture `grout_blasting_kiln_recipes_loaded` (the blast furnace lookup finds them).
- 61 optional ore melting paths: **replaced** by ordered alternatives `melting/metal/<metal>/<form>_byproduct_N` with mutually exclusive tag conditions (arthur.3). `OptionalMeltingRecipeTest` passes (61 groups, every tag-presence combination).
- Acceptance rows (slots above 255, empty break, counts): controlled cases added to `PersistenceServerFixture` (now 9 cases). A 392-slot smeltery with items past slot 255 and two fluids survives save and reload, and a legacy byte slot index reads as unsigned; removing an empty controller spawns nothing and its loot is exactly one plain controller. A real process restart stays in Arthur's in-game list. Fixture cases `smeltery_slots_above_255_and_tank_reload`, `empty_smeltery_break_drops_only_controller`.

## F7 Worldgen: preserved, no change

After normalizing 1.21 format moves, the four geodes are identical to official; the two ender slime trees differ only by vanilla's `dirt_provider` + `force_dirt:false` becoming `below_trunk_provider` (dirt unless the block is in `#minecraft:cannot_replace_below_tree_trunk`); `clay_island` differs only by `minecraft:grass` renamed `minecraft:short_grass`. Nothing changed, so the host session needs no notice before pregen.

## F8 Datagen: repaired

`runData` failed with `Components not bound yet` because 26.1 binds item default components only on server resource load or client registry sync. New `common/data/DatagenComponentBootstrap` applies the vanilla initializers once, at highest priority on both data events, from the generator's lookup. runData now completes in about 50 s (`tools\logs\wt-fixes-20260929-131942.log`). The first full run changed 430 generated files: 301 content drifts, all owned by other streams (292 modifier JSON: the committed files carry the hand-added `tooltip_display` and other keys the provider does not emit; 4 slime armor tool definitions; horn material recipe; 4 travelers advancements with `#c:leathers` vs `#c:ingots/copper`) and 137 format-only rewrites (trailing newline, indentation). Rugged's JSON is format-only; the level-display drift named in the setup report did not reproduce. Nothing regenerated for other streams was committed. Caveat for everyone: datagen keeps a hash cache in `src/generated/resources/.cache` (git-ignored). After the generated tree is restored with git checkout, a later run skips files whose provider output did not change, which hides drift. Delete that folder before a full regeneration diff.

## Other change

Item frame tooltips: official shows a gray behavior line per frame; the port removed the override and six keys. Restored in `FancyItemFrameItem` (26.1 signature) and six `en_us.json` keys beside the frame names; `FixesDataParityTest.itemFrameTooltipsUseTheRestoredOfficialKeys`.

## Shared files touched (additive)

`common/TinkerTags.java` (8 new `EntityTypes` constants), `common/data/tags/EntityTypeTagProvider.java` (new tag entries, `hide_in_default` chain), `common/data/loot/EntityLootTableProvider.java` (one entry), new `common/data/DatagenComponentBootstrap.java`, `en_us.json` (6 keys). `build.gradle`: fixture jar 0.0.6. No `TConstruct.java`, `TinkerClient.java`, `library/**` or access-transformer change.

## Failures owned by other streams

First run of these suites on any server; the same three failures appeared on the first harness run at 13:10, before any fixes change beyond the autorun, so they are not caused by this branch.

- rei, `aebmrecipeviewertest material_ingredient_preserves_material_and_components`: `AssertionError: requested material was lost` (display items for an iron `MaterialIngredient` carry a different material).
- rei, `aebmtooltinkeringtest six_loaded_costs_and_indexed_holes`: `AssertionError: displayed cost is wrong`.
- rei, `aebmtooltinkeringtest damage_focus_matches_station`: `AssertionError: focused damage differs from station`.

## Needs from the coordinator

- Merge note: `fixture-suites.json` is the case manifest for the runner. Streams that add fixture suites add one line each there; expect a union merge like `en_us.json`.
- Tell the other streams that datagen and the fixture harness work (usage in F1 and F8), including the `.cache` caveat.
- The fancy stand now has a loot table and the smeltery melts hidden-only entities again; both are official behavior and need no world change. No worldgen change was made.
