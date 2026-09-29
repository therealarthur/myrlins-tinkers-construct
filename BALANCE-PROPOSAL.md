# Balance proposal: Continuum numbers versus official TConstruct v3.12.1

Written 2026-09-29 by the parity modifiers stream. Official = TConstruct v3.12.1.231 (`a5a03249`); Continuum = `parity/integration` `6d420d65`.
Every row is a pure number difference (value, level scaling, durability, tier, sort order, material choice for mob tools). Semantic differences (modules, hooks, conditions, traits) were restored on `parity/modifiers` instead; see `local-audit/modifiers-triage.md` there.

**Status: proposal only. Arthur approves each domain separately.** `parity/balance` implements every row, one commit per domain, so any subset can be taken by cherry-picking those commits:

1. modifiers (`ModifierProvider` and 8 modifier JSON files)
2. fluid effects: no commit, there is no pure number difference (all 56 files are format migrations or semantic fixes)
3. material stats (`PlatingMaterialStats` shield factor and 52 material stat JSON files: 31 official materials, 21 Continuum compat materials)
4. material definitions (`MaterialDataProvider` tiers and sort order, 8 JSON files)
5. tool definitions (`ToolDefinitionDataProvider`, 4 JSON files)
6. mob equipment (`MobEquipmentProvider`, 5 JSON files plus the Twilight Forest minotaur file, which only loads with Twilight Forest; on `parity/modifiers` that file is renamed to the official `minotaur.json`)

Recommendation key: **official** = take the official number; **keep** = keep Continuum; **either** = no gameplay difference in this pack worth arguing about.
Materials the materials stream may be editing (venom, venombone, slimeskin, vine family, wool) are listed at the end and not changed on `parity/balance`.

## 1. Modifiers

| File | Key | Official | Continuum | Player-facing effect | Recommendation |
| - | - | - | - | - | - |
| `modifiers/boon_of_sssss.json` | attribute good effect duration | +15% flat, +10% per level | +25% per level | Same +25% at level 1 (string skull trait is level 1). Only differs if boon reaches level 2+. | either (official) |
| `modifiers/dragonborn.json` | protection while airborne, per level | 2.0 | 2.5 | Dragon scale armor: 8% instead of 10% damage reduction per level while airborne. | official |
| `modifiers/enderclearance.json` | teleport clearance chance; level display | 25% flat; single level | 25% per level; default display | Same 25% at level 1. Continuum scales to 50% at level 2 and shows the level number. | official |
| `modifiers/enderdodging.json` | dodge chance, module 1 and 2 | 15% per level; 7.5% per level | 10% + 10% per level; 5% + 5% per level | Level 1 dodge 15% and 7.5% instead of 20% and 10%. Level 2 is the same (30% and 15%). | official |
| `modifiers/entwined.json` | movement speed per level (multiply total) | +10% | +15% | Twisting vine armor: 10% faster instead of 15%. | official |
| `modifiers/featherweight.json` | movement speed; use item speed; protection per level | +5% movement (multiply base); +0.05 use item; -0.625 protection | no movement bonus; +0.10 use item; -1.25 protection | Aluminum armor: official gives 5% move speed and half the use item bonus, with half the protection penalty (2.5% instead of 5% less protection per level). | official |
| `modifiers/solar_powered.json` | tool damage reduction formula; level display | (0.01 x level + 0.04) x sky light; single level | 0.05 x sky light; default display | Same at level 1 (up to 75% in full daylight). Official rises to 90% at level 2. | official |
| `modifiers/unburdened.json` | armor use item speed per level | +0.10 | +0.05 | Bamboo armor: less slowdown while using items (shields, bows, eating). | official |

Also changed on `parity/modifiers` as a semantic fix, not a balance choice: `balm_of_sssss` was +20% per level (made negative effects last longer, the opposite of its description); official is -10% flat, -10% per level.

## 2. Fluid effects

No pure number differences. Of the 56 differing files, 53 are format migrations (load condition syntax, fluid ID instead of the single fluid local tag) and 3 were semantic fixes on `parity/modifiers` (`concrete` block, `earth_slime` and `sky_slime` sound). No commit on `parity/balance`.

## 3. Material stats: plating shield durability

Official computes plating shield durability as `durability factor x 18` (`PlatingMaterialStats.Builder.durabilityFactor`). Continuum uses `ArmorModuleBuilder.SHIELD_DAMAGE = 22`, so every plating shield has 22/18 = 1.22x the official durability. `parity/balance` changes the one factor back to 18 and updates every generated file. Player-facing effect: plate shields (and the travelers shield plating) break about 18% sooner. Recommendation: **official**.

| File | Key | Official | Continuum |
| - | - | - | - |
| `materials/stats/aluminum.json` | `tconstruct:plating_shield.durability` | 234 | 286 |
| `materials/stats/amethyst_bronze.json` | `tconstruct:plating_shield.durability` | 504 | 616 |
| `materials/stats/ancient.json` | `tconstruct:plating_shield.durability` | 450 | 550 |
| `materials/stats/bronze.json` | `tconstruct:plating_shield.durability` | 504 | 616 |
| `materials/stats/cinderslime.json` | `tconstruct:plating_shield.durability` | 756 | 924 |
| `materials/stats/cobalt.json` | `tconstruct:plating_shield.durability` | 540 | 660 |
| `materials/stats/constantan.json` | `tconstruct:plating_shield.durability` | 450 | 550 |
| `materials/stats/copper.json` | `tconstruct:plating_shield.durability` | 234 | 286 |
| `materials/stats/electrum.json` | `tconstruct:plating_shield.durability` | 252 | 308 |
| `materials/stats/fiery.json` | `tconstruct:plating_shield.durability` | 450 | 550 |
| `materials/stats/gold.json` | `tconstruct:plating_shield.durability` | 126 | 154 |
| `materials/stats/hepatizon.json` | `tconstruct:plating_shield.durability` | 576 | 704 |
| `materials/stats/invar.json` | `tconstruct:plating_shield.durability` | 432 | 528 |
| `materials/stats/iron.json` | `tconstruct:plating_shield.durability` | 270 | 330 |
| `materials/stats/knightmetal.json` | `tconstruct:plating_shield.durability` | 360 | 440 |
| `materials/stats/knightslime.json` | `tconstruct:plating_shield.durability` | 594 | 726 |
| `materials/stats/lead.json` | `tconstruct:plating_shield.durability` | 216 | 264 |
| `materials/stats/manyullyn.json` | `tconstruct:plating_shield.durability` | 630 | 770 |
| `materials/stats/nicrosil.json` | `tconstruct:plating_shield.durability` | 504 | 616 |
| `materials/stats/obsidian.json` | `tconstruct:plating_shield.durability` | 198 | 242 |
| `materials/stats/osmium.json` | `tconstruct:plating_shield.durability` | 450 | 550 |
| `materials/stats/pewter.json` | `tconstruct:plating_shield.durability` | 288 | 352 |
| `materials/stats/pig_iron.json` | `tconstruct:plating_shield.durability` | 414 | 506 |
| `materials/stats/queens_slime.json` | `tconstruct:plating_shield.durability` | 900 | 1100 |
| `materials/stats/rose_gold.json` | `tconstruct:plating_shield.durability` | 162 | 198 |
| `materials/stats/scorched_stone.json` | `tconstruct:plating_shield.durability` | 180 | 220 |
| `materials/stats/seared_stone.json` | `tconstruct:plating_shield.durability` | 252 | 308 |
| `materials/stats/silver.json` | `tconstruct:plating_shield.durability` | 324 | 396 |
| `materials/stats/slimesteel.json` | `tconstruct:plating_shield.durability` | 720 | 880 |
| `materials/stats/steel.json` | `tconstruct:plating_shield.durability` | 522 | 638 |
| `materials/stats/steeleaf.json` | `tconstruct:plating_shield.durability` | 180 | 220 |

Continuum-only compat materials use the same formula, so the factor change moves them too (official has no value for them; new value = factor x 18):

| File | Key | New (factor x 18) | Continuum (factor x 22) |
| - | - | - | - |
| `materials/stats/allthemodium.json` | `tconstruct:plating_shield.durability` | 36864 | 45056 |
| `materials/stats/blazing_crystal.json` | `tconstruct:plating_shield.durability` | 342 | 418 |
| `materials/stats/certus_quartz.json` | `tconstruct:plating_shield.durability` | 234 | 286 |
| `materials/stats/conductive_alloy.json` | `tconstruct:plating_shield.durability` | 396 | 484 |
| `materials/stats/dark_steel.json` | `tconstruct:plating_shield.durability` | 630 | 770 |
| `materials/stats/end_steel.json` | `tconstruct:plating_shield.durability` | 756 | 924 |
| `materials/stats/energetic_alloy.json` | `tconstruct:plating_shield.durability` | 432 | 528 |
| `materials/stats/energized_steel.json` | `tconstruct:plating_shield.durability` | 378 | 462 |
| `materials/stats/entro.json` | `tconstruct:plating_shield.durability` | 504 | 616 |
| `materials/stats/fluix.json` | `tconstruct:plating_shield.durability` | 396 | 484 |
| `materials/stats/niotic_crystal.json` | `tconstruct:plating_shield.durability` | 432 | 528 |
| `materials/stats/nitro_crystal.json` | `tconstruct:plating_shield.durability` | 684 | 836 |
| `materials/stats/pulsating_alloy.json` | `tconstruct:plating_shield.durability` | 360 | 440 |
| `materials/stats/quantum_alloy.json` | `tconstruct:plating_shield.durability` | 576 | 704 |
| `materials/stats/redstone_alloy.json` | `tconstruct:plating_shield.durability` | 144 | 176 |
| `materials/stats/soularium.json` | `tconstruct:plating_shield.durability` | 324 | 396 |
| `materials/stats/spirited_crystal.json` | `tconstruct:plating_shield.durability` | 540 | 660 |
| `materials/stats/unobtainium.json` | `tconstruct:plating_shield.durability` | 36864 | 45056 |
| `materials/stats/uraninite.json` | `tconstruct:plating_shield.durability` | 216 | 264 |
| `materials/stats/vibranium.json` | `tconstruct:plating_shield.durability` | 36864 | 45056 |
| `materials/stats/vibrant_alloy.json` | `tconstruct:plating_shield.durability` | 540 | 660 |

If Arthur wants official numbers for official materials only, the alternative is a per material `shieldDurability` override; say so and the balance commit changes shape.

Material stat differences that are not numbers (not changed, owned by the materials stream): `blazing_bone`, `necronium`, `venombone` gain Continuum-only `tconstruct:skull` stats (bone skull materials); `enderslime_vine`, `skyslime_vine`, `twisting_vine`, `vine`, `weeping_vine` keep empty `tconstruct:maille` stats so older saves stay valid.

## 4. Material definitions: tier and sort order

| File | Key | Official | Continuum | Player-facing effect | Recommendation |
| - | - | - | - | - | - |
| `materials/definition/blaze.json` | `tier` | 3 | 2 | Shown as tier 3 in books and tooltips; tier gates tool part material listings. | official |
| `materials/definition/ender_pearl.json` | `tier` | 3 | 2 | Same as blaze. | official |
| `materials/definition/blood.json` | `tier` | 2 | 5 | Hidden slimesuit material; tier only shows in the encyclopedia. | official |
| `materials/definition/glowstone.json` | `sortOrder` | 35 | 25 | Listed with the nether materials in the book and material lists. | official |
| `materials/definition/ichor.json` | `sortOrder` | 35 | 25 | Same. | official |
| `materials/definition/kobold.json` | `sortOrder` | 35 | 25 | Same. | official |
| `materials/definition/magma.json` | `sortOrder` | 35 | 25 | Same. | official |
| `materials/definition/quartz.json` | `sortOrder` | 35 | 25 | Same. | official |

The other 17 differing definition files only change the load condition syntax (`c:or` to `neoforge:or`).

## 5. Tool definitions

| File | Key | Official | Continuum | Player-facing effect | Recommendation |
| - | - | - | - | - | - |
| `tool_definitions/plate_chestplate.json` | `multiply_stats` attack damage | 0.5 | 0.4 | Unarmed attack damage from the chestplate 25% higher. | official |
| `tool_definitions/travelers_chestplate.json` | `multiply_stats` attack damage | 0.6 | 0.55 | Unarmed attack damage about 9% higher. | official |
| `tool_definitions/slime_chestplate.json` | `multiply_stats` attack damage | 0.75 | 0.6 | Unarmed attack damage 25% higher. | official |
| `tool_definitions/slime_wings.json` | `multiply_stats` attack damage | 0.5 | 0.4 | Unarmed attack damage 25% higher. | official |
| `tool_definitions/slime_wings.json` | `base_stats` durability | 222 | none | Official wings get 222 base durability on top of the slime material's; Continuum wings only have the material durability, so they break much sooner. | official |

## 6. Mob equipment

Official gives these mob tools random materials from the "ancient" pool (tier limited, excluding `tconstruct:exclude_from_loot`). Continuum fixes the materials. Player-facing effect: tools dropped by these mobs have random tier appropriate materials, as in official, instead of always wood, flint, iron and rock.

| File | Key | Official | Continuum | Recommendation |
| - | - | - | - | - |
| `mob_equipment/drowned.json` | `equip.materials` | ancient, ancient, ancient | flint, wood, iron | official. |
| `mob_equipment/melting_pan.json` | `equip.materials` | ancient, ancient | iron, wood | official. |
| `mob_equipment/piglins.json` | `equip.materials` | ancient, ancient | wood, iron, wood | official. Continuum lists 3 materials for the 2 part battlesign; if this row is declined, still trim it to 2. |
| `mob_equipment/war_pick.json` | `equip.materials` | ancient, ancient, ancient | rock, wood, iron | official. |
| `mob_equipment/wither_skeleton.json` | `equip.materials` | ancient, ancient, ancient | flint, wood, iron | official. |
| `mob_equipment/twilightforest_minotaur.json` (`minotaur.json` on `parity/modifiers`, Twilight Forest only) | `equip.materials` | ancient x3 | iron, wood, iron | official (no effect without Twilight Forest) |

## Not changed on parity/balance (materials stream territory)

- `materials/stats/venombone.json` skull stats (Continuum addition, not a number difference against official).
- Vine family maille stats (see section 3), wool, venom and slimeskin: no number differences against official in the files this stream compared.
- Material traits (`gold`, `rose_gold` extra `golden` skull trait; bone skull traits): semantic, materials stream.

## How the numbers were checked

- Official and Continuum values come from the generated JSON in both trees (`ledger-data/balance-leaf-diffs.md` plus a re-diff after format normalization).
- `parity/balance` carries `local-tests/unit/aebm/continuumtests/ModifierBalanceValuesTest.java` with `OFFICIAL_BALANCE = true`; `parity/modifiers` carries the same test pinned to the Continuum numbers. Keep the version from the branch whose numbers ship when merging.
