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

The following changes are in progress. Build, runtime and client results must be recorded separately when available. The `.1` artifacts remain unchanged.

| Original behavior / defect | Restoration scope | Evidence planned or available |
| --- | --- | --- |
| Original armor materials and traits | Independent sky/ender slimeskin and venom; wool/vine cuirass support; Travelers swapping; original primary/trim indices, short legacy helmet remapping; airborn/rugged. Retain complete old material arrays and IDs. | `armor-parity-plan.md`, separate actual server fixture; source restoration is not runtime acceptance |
| Block-walker placement cancellation | Restore target NeoForge placement-veto event before block mutation/tick scheduling | Commit `fe264161`; five controlled real-world fixture cases, not yet run |
| Vanilla ore-drop formula | Replace additive stub with the original vanilla fortune algorithm; unknown formula identifiers report a codec error | Actual target vanilla class comparison across base counts, levels and seeds, including RNG consumption; tests pending |
| Fluid particles | Restore original still-texture fragments, tint, gravity, quarter-sprite UVs and fluid lighting through current SingleQuadParticle API; enable provider registration | Compilation plus JSON/network roundtrips through the actual global particle codecs; visual acceptance pending |
| Particle type lost on decode | Make each codec capture its owning registered particle type | Roundtrip then re-encode/relay tests with water, lava, amounts and custom components; tests pending |
| Conditional modifier definitions/redirects | Evaluate original single conditions and generated NeoForge condition arrays; first passing redirect precedes fallback; serialize redirect conditions | Real modifier parser tests for present/missing mods, tag context replacement, branches, malformed conditions and datagen roundtrip; tests pending |
| Datapack reload condition context | Modifier, station layout, fluid effect and mob equipment loaders use the injected current registry/tag context; nested data receives that context | Source trace against NeoForge ContextAwareReloadListener; parser tests cover actual injected context, broader datapack/server checks remain |
| Damage result and secondary-hit state | Use the actual target hurt result rather than `damage > 0`; preserve timer and accumulated accepted damage; rejected hits do not count as successes | Six actual server fixture cases in `local-tests/returning/COMBAT.md`, pending execution |
| Solid fuel container remainders | Restore the extracted stack's crafting remainder; return/insert it and drop only the uninserted portion | Six real-module fixture cases with component-sensitive remainder and partial insertion, pending execution |
| Client movement and modifier HUD | Restore current input, forced hands, stored item/shield/sleeve/map HUD through target render APIs | Separate source/API review; graphical acceptance pending |
| Recipe visibility in the selected browser | Shared synchronized recipe cache plus optional REI adapter using installed REI 26.1.819 | `recipe-viewer-parity-plan.md`; initial adapter is not all 15 official categories or interaction parity |

Keep the existing Continuum numerical tuning while the optional original-balance preference is unanswered, as confirmed by the integration coordinator. Record all differences. Missing behavior and concrete migration defects are restored independently of that choice.

## Open coverage

The complete source/resource review queue remains authoritative. This first restoration does not close: all 15 recipe categories and their focus/transfer behavior; newer material book renderers; translated books and changed assets; developer melting generation; datagen item naming; extra-block damage overlays; optional external integrations; all 1,527 changed Java bodies; or the full material/modifier/resource comparison. Some paths are current API replacements and require an explicit mapping rather than a duplicate implementation.

Next checks must also cover smeltery/foundry reconstruction and saved contents, fluid/container conservation, modifier conversion and tag reloads, actual recipe registration, world generation, equipment interactions, and Arthur-operated client visuals. Do not use file counts, successful compilation, or a server boot as evidence that these are complete.
