# Restoration and verification ledger

September 28, 2026. Reference: official Tinkers `v3.12.1.231`, commit `a5a0324954f71b7620f766fadea0a0adf2984ae0`. The baseline inventories in this directory stay pinned to the original releases. This document records subsequent fixes; it does not mark changed source files or whole domains equivalent merely because a patch builds.

## Verified compatibility checkpoint

Frozen source commit `ba6b9098977b104f04d50e3eb168304e08c7c306`, Construct `3.12.2-arthur.1`, fixture `0.0.1`, unchanged Core `1.12.0`. Exact hashes are in `../../../docs/handoffs/continuum.md`.

| Behavior | Implementation | Actual evidence | Remaining acceptance |
| --- | --- | --- | --- |
| Adjacent-inventory crafting transfer | Copy-aware slots, transactional insert/extract checks, capacity restrictions, rollback | FML/JUnit actual Core/NeoForge/vanilla adapter regression: 105 assertions with an executable old-slot loss control | Installed storage-mod menus, real client clicks, reconnect |
| Full crafting destination | Consume inputs only after some output transfers | Coordinator combined 208-JAR run 017: four crafting cases passed, including full destination, repeated crafts, partial remainder and bucket remainders | Actual player GUI and network interaction |
| Returning weapons | Vanilla loyalty/impact fields, preserved legacy damage state | Run 017: nine cases passed, including impact/ground/void, pickup, legacy state and binary reload | Natural flight/collision, real owner reconnect, two players |
| Tool/casting persistence | Regression coverage of existing serialization and ticks | Run 017: seven cases passed, including tool components/rebuild/broken state and partial/mid-cooling casting | Process restart/chunk unload, larger structures and smeltery/foundry state |

Run 017 evidence is under `../../../evidence/combined-candidates-017/`; `acceptance.json`, actual `latest.log` case lines and `summary.json` were read back. Exit code was zero without a forced stop. This accepts the listed controlled checks, not the whole pack or all original Tinkers behavior.

## Separate arthur.2 source restoration

The following source restoration compiles on the target. `build/local-evidence/build-20260928-034130.log` compiled all main and server-fixture sources. `build/local-evidence/build-20260928-034315.log` passed 27 headless FML/JUnit tests with zero failures; three client-only material-book tests were correctly skipped and are not counted as passed. XML reports are under `build/test-results/test/`. The `.1` artifacts remain unchanged. No `.2` server or graphical acceptance is claimed.

| Original behavior / defect | Restoration scope | Evidence planned or available |
| --- | --- | --- |
| Original armor materials and traits | Independent sky/ender slimeskin and venom; wool/vine cuirass support; Travelers swapping; original primary/trim indices, short legacy helmet remapping; airborn/rugged. Retain complete old material arrays and IDs. | `armor-parity-plan.md`, separate actual server fixture; source restoration is not runtime acceptance |
| Block-walker placement cancellation | Restore target NeoForge placement-veto event before block mutation/tick scheduling | Commit `fe264161`; five controlled real-world fixture cases, not yet run |
| Vanilla ore-drop formula | Replace additive stub with the original vanilla fortune algorithm; unknown formula identifiers report a codec error | Three FML tests passed, comparing all adapters against actual target vanilla classes across base counts, levels and seeds, including RNG consumption |
| Fluid particles | Restore original still-texture fragments, tint, gravity, quarter-sprite UVs and fluid lighting through current SingleQuadParticle API; enable provider registration | Compilation plus JSON/network roundtrips through the actual global particle codecs; visual acceptance pending |
| Particle type lost on decode | Make each codec capture its owning registered particle type | Two FML tests passed: global JSON/network roundtrip and re-encode/relay with water, lava, amounts and custom components |
| Conditional modifier definitions/redirects | Evaluate original single conditions and generated NeoForge condition arrays; first passing redirect precedes fallback; serialize redirect conditions | Seven actual-parser FML tests passed for present/missing mods, tag context replacement, branches, malformed conditions and datagen roundtrip |
| Datapack reload condition context | Modifier, station layout, fluid effect and mob equipment loaders use the injected current registry/tag context; nested data receives that context | Source trace against NeoForge ContextAwareReloadListener; parser tests cover actual injected context, broader datapack/server checks remain |
| Damage result and secondary-hit state | Use the actual target hurt result rather than `damage > 0`; preserve timer and accumulated accepted damage; rejected hits do not count as successes | Six actual server fixture cases in `local-tests/returning/COMBAT.md`, pending execution |
| Solid fuel container remainders | Restore the extracted stack's crafting remainder; return/insert it and drop only the uninserted portion | Six real-module fixture cases with component-sensitive remainder and partial insertion, pending execution |
| Client movement and modifier HUD | Restore current input, forced hands, stored item/shield/sleeve/map HUD through target render APIs | Separate source/API review; graphical acceptance pending |
| Recipe visibility in the selected browser | Shared synchronized recipe cache plus optional REI adapter using installed REI 26.1.819 | `recipe-viewer-parity-plan.md`; initial adapter is not all 15 official categories or interaction parity |
| Material recipe preview fidelity | Preserve component-bearing ingredients and usable material alternatives; distinguish absent casts from unresolved required casts; calculate cooling from the resized fluid amount once; retain composite conflict filtering | Ingredient FML test passed; seven loaded-recipe server cases prepared |
| Material book sections | Restore slime/ribcage/shell/laces pages, grouped material indexes and skull details; use synchronized recipes and reset on next open after updates; retain existing skull bookmark paths | `material-book-parity-plan.md`; main compilation and static checks, three client-only tests and actual book UI acceptance pending |
| Extra-block damage overlay | Feed original bounded AOE positions into the target's extracted breaking-state list using observed vanilla progress | `aoe-breaking-overlay-restoration.md`; compiled, no visual acceptance |
| Smeltery item/fluid rendering | Submit extracted item states with their declared transforms; restore drain/duct fluid ModelData while preserving retexture | Narrow render dispatch audit; target compilation and actual visual acceptance recorded separately |
| Developer melting generation | Restore bounded static-recipe inference, correct multiplicity, component checks, recipe codecs and current pack metadata | Eleven FML tests passed, with explicit unsupported-recipe reporting; command filesystem/reload acceptance pending |
| Named tool-forge crafting | Native ItemStackTemplate custom-name components replace the unused no-op CraftingNBTWrapper; preserve names in four shipped recipes and their providers | Actual retextured builder/codec/crafted-output FML regression passed; full datagen and material fallback gameplay acceptance remain separate |

The remaining three passing tests cover recipe cache replacement, component-aware ingredient display, and the existing 105-assertion actual inventory regression. The first `.2` FML attempt (`033807`) retained four failures: particle tests lacked vanilla pending-component initialization; the named-forge test lacked an unlock criterion; and the generator asserted equality of a legacy recipe object's synthetic ID rather than the holder-owned resource key. Test setup/expectations were repaired against the target APIs; production behavior assertions were retained. Earlier compiler failures and their logs remain available. Malformed-condition negative controls deliberately emit load errors; all seven assertions pass.

Keep the existing Continuum numerical tuning while the optional original-balance preference is unanswered, as confirmed by the integration coordinator. Record all differences. Missing behavior and concrete migration defects are restored independently of that choice.

## Separate arthur.3 source restoration

This checkpoint adds the eight original instrument-specific horn material recipes
and fallback, component-sensitive material lookup, five modifier-tooltip contexts,
and the original common fluid variables, capacity modules, action predicate,
durability-change hook and material-user APIs. Material references now follow
completed reloads, potion casting retains modern components, and copied modifier
display data is written before freezing its component. See `common-api-parity.md`
and `modifier-tooltip-parity.md` for boundaries and actual-class tests.

All 15 original recipe category types now have REI display data, with five custom
entry types, correlated tool-tinkering producers, actual cost/refund calculations,
component-aware cache invalidation and recycling corrections. This does not close
focus behavior, dynamic slots, transfer, crafting extensions, client reload or
JEI adapter parity. `recipe-viewer-parity-plan.md` retains those open items. The
server fixture adds nine tool-tinkering and fourteen recipe-mapper cases to the
previous 54; all 77 compile, with execution left to the integration coordinator.

Three missing recipes are restored: honey-block material, earthslime-to-magma
conversion, and bacon crafting repair. Optional ore-melting alternatives across
61 families now preserve the original first-passing-branch behavior; the retained
truth-table report checks all 252 combinations. Tank model data and blockstate
dispatch, modifier-sensitive model cache keys, extruded tool geometry, the proxy
tank's GUI geometry and three book tooltip overrides are also restored. Five
client-only tests remain explicitly separate from dedicated-distribution tests.

Authoritative test counts, XML hashes and retained evidence for this checkpoint
are in `VERIFICATION-ARTHUR-3.json`; its artifact hashes and source commit are in
`../../../docs/handoffs/continuum.md`. Neither frozen earlier checkpoint is
overwritten. Server fixtures and graphical acceptance are separate obligations.

## Open coverage

The complete source/resource review queue remains authoritative. This restoration does not close recipe focus/transfer behavior, actual client rendering/book behavior, translated books and changed assets, runtime developer-command generation, optional external integrations, all 1,527 changed Java bodies, or the full material/modifier/resource comparison. Known additional gaps include fluid tint/emission, remaining embedded tank GUI geometry and generic resource-pack GUI loaders, modular edible effects and their schemas, conditional-stat registration, and the wandering-trader ancient-tool trade. Some paths are current API replacements and require an explicit mapping rather than a duplicate implementation.

Next checks must also cover smeltery/foundry reconstruction and saved contents, fluid/container conservation, modifier conversion and tag reloads, actual recipe registration, world generation, equipment interactions, and Arthur-operated client visuals. Do not use file counts, successful compilation, or a server boot as evidence that these are complete.
