# Modifier, fluid effect and mob equipment triage (parity/modifiers)

Written 2026-09-29 by the modifiers stream. Official = TConstruct v3.12.1.231 (`a5a03249`), Continuum = `parity/integration` `6d420d65`.
Source of the differing file list: `<parity-workspace>\ledger-data\balance-leaf-diffs.md`. Every file below was re-diffed after normalizing the format migrations in the legend; the bucket is what remained.

Buckets: **format** (26.1 migration, no change), **semantic** (different module, hook, condition, slot type or trait; restored on `parity/modifiers` unless a 26.1 reason is written), **number** (pure number, proposed in `BALANCE-PROPOSAL.md` on `parity/balance`), **replaced** (another implementation does the same job, recorded).

## Legend for format parts

- **A**: attribute ID moved to its 26.1 name (generic prefix dropped; forge entity_gravity, step_height_addition, swim_speed, block_reach and entity_reach became minecraft:gravity, minecraft:step_height, neoforge:swim_speed, minecraft:block_interaction_range, minecraft:entity_interaction_range).
- **O**: attribute operation renamed (addition, multiply_base, multiply_total became add_value, add_multiplied_base, add_multiplied_total).
- **S**: slot list also names the 26.1 body and saddle slots (tools cannot sit there, no gameplay effect).
- **T**: legacy tooltip_display key (never, tinker_station) instead of show_in_tooltips (parts_only, bonus_slot); ModifierTooltipsField maps them one to one and logs a deprecation warning.
- **C**: load condition or common tag syntax (condition to neoforge:conditions, forge and c condition types to neoforge, forge tags to c tags).
- **P**: Continuum Core predicate renamed (has_effect to has_mob_effect, raining_at to raining with the same isRainingAt check, attacker to source_attacker, is_indirect to indirect, mob type water to the minecraft:aquatic entity tag).
- **I**: 26.1 ingredient or item stack syntax.
- **F**: fluid ingredient names the fluid instead of the local fluid tag; the tag only lists that fluid and its flowing form, and tank contents are always the source fluid, so matching is the same.
- Continuum's Apotheosis bridge modules (`tconstruct:apothic_enchantment_cap`, `tconstruct:luck_apothic_enchantment_cap`, and `tconstruct:requirements` naming `tconstruct:apotheosis`) are Continuum additions and stay on every modifier that has them.
- Continuum writes `"tooltip_display": "always"` on every modifier. It loads identically but makes `ModifierTooltipsField` log one deprecation warning per modifier per reload; a full datagen run would drop the key.

## Modifiers (86 files)

| File | Bucket | Action | Detail |
| - | - | - | - |
| airborne | semantic | restored | level display was default, official no_levels (airborne has no per level scaling) |
| antiaquatic | format | none | P |
| antitoxin | format | none | P |
| balm_of_sssss | semantic | restored | port had +20% per level, which lengthened negative effects and contradicted the description; official is -10%, -10% per level (same -20% at level 1 on the darkthread skull) Format parts: OS. |
| boon_of_sssss | number | balance | good effect duration 0.25 per level vs official 0.15 flat + 0.10 per level (equal at level 1) Format parts: OS. |
| bouncy | format | none | OS |
| boundless | format | none | OS |
| cobalamin | semantic | restored | port dropped the +10% per level use item speed module Format parts: AOS. |
| consecrated_skull | format | none | T |
| crystalstrike | format | none | AOS |
| double_jump | format | none | O |
| draconic | format | none | T |
| dragonborn | number | balance | airborne protection 2.5 per level vs official 2.0 Format parts: O. |
| dragonfall | format | none | OS |
| dragonheart | format | none | A |
| embossed | format | none | CT |
| enderclearance | number | balance | chance 0.25 per level vs official 0.25 flat; level display follows the scaling (official single_level), so it moves with the number |
| enderdodging | number | balance | teleport dodge chance 0.1 + 0.1 per level and 0.05 + 0.05 per level vs official 0.15 and 0.075 per level |
| entangled | semantic | restored | port added armor knockback resistance +0.10 and split the unequip damage into 1 (tools) and 2 (worn armor); official has neither |
| entwined | number | balance | movement speed 0.15 per level vs official 0.10 Format parts: AOS. |
| experienced | format | none | OS |
| feather_falling | format | none | T |
| featherweight | number | balance | port swapped +5% movement speed and +0.05 use item speed for +0.10 use item speed, and doubled the protection penalty to -1.25 per level (official -0.625); kept together as one balance change Format parts: AOS. |
| fins_ammo | format | none | T |
| fireborn | semantic | restored | port used the older design (blocks on_fire damage, attacks set targets on fire); official v3.12.1 is fire protection 7 that does not count as protection (26.1 burning_time -105%) plus conductive immunity. Official lang text still describes the older design in both trees |
| forecast | format | none | T |
| fortunate | format | none | AOS |
| fortune | format | none | AOS |
| gilded | format | none | T |
| glowing | format | none | I: block item provider writes an item stack object |
| godspeed | semantic | restored | attack speed and movement speed modules were swapped; official sorts them numerically (tooltip order only, same values) Format parts: AOS. |
| harmonious | format | none | T |
| haste | format | none | OS |
| headlight | semantic | restored | variant formatter ID was the misspelled tconstruct:paraoeter; official tconstruct:parameter now registered as an alias in SwappableModifierRecipe (additive) and used by the modifier and its 3 recipes. Only loads with the Headlight mod Format parts: C. |
| heavy | format | none | AOS |
| hydraulic | format | none | P |
| knockback | format | none | AOS |
| knockback_resistance | semantic | restored | level display was default (official single_level) and the wool name color module was missing |
| leaping | format | none | OS |
| lightspeed | format | none | AO |
| looter | format | none | OS |
| luck | semantic | restored | weapon looting was limited to melee weapons and launchers; official uses the same predicate as looting (any melee tool, plus projectiles through the air placeholder). Apotheosis luck cap kept Format parts: AOS. |
| lure_rod | format | none | T |
| magic_protection | format | none | O |
| maintained | format | none | AOS |
| melee_protection | format | none | OP |
| overwield | format | none | AOS |
| projectile_protection | format | none | AO |
| ram_attack | semantic | restored | horn module used default hooks, which include damage_dealt; official limits it to melee_hit and monster_melee_hit |
| reach | format | none | AOS |
| recapitated | format | none | T |
| redirected | format | none | T |
| respiration_skull | format | none | T |
| resurrected | format | none | T |
| revenge | format | none | P |
| revitalizing | format | none | AOS |
| ricochet | format | none | OS |
| savory | replaced | replaced | official edible trait plus edible_* modules and an edible_counter_chance stat; Continuum uses one tconstruct:edible module with the same durability, counter chance 0.15 per level, 16 tick duration and cure random effect |
| scorch_protection | format | none | P |
| scrumptious | replaced | replaced | as savory; remove poison effect kept |
| shock | format | none | P |
| shulking | format | none | O |
| silky | semantic | restored | port applied silk touch only while harvesting through a main hand harvest flag; official applies it constantly on harvest tools (NeoForge 26.1 loot predicates read getAllEnchantments, so constant works) |
| skyfall | format | none | AOS |
| slimeball | semantic | restored | fireball options matched single items and played the vanilla slime block sound; official matches the c:slimeball tags and plays tconstruct:slimy_bounce. FireballModule still falls back to item identity if a tag ingredient has no bound holders Format parts: I. |
| sliver | semantic | restored | filter was a fixed item set and the menu opened on shift only; official filters by the tconstruct:slimeball_ammo tag (which lists the same items plus tags) and opens on any key |
| smashing_ammo | format | none | T |
| solar_powered | number | balance | tool damage reduction 0.05 x light vs official (0.01 x level + 0.04) x light; equal at level 1; level display follows the scaling |
| solid | format | none | AOS |
| speedy | format | none | AOS |
| spilling_rod | format | none | T |
| step_up | format | none | AO |
| sticky | semantic | restored | port used weapon_mob_effect (100% on hit, no counterattack); official uses the legacy mob_effect module: 25% per level on melee, projectile and counterattack. Logs the same deprecation warning official does |
| stoneshield | format | none | I: consume ingredient written as a tag string |
| strength | format | none | AO |
| tank | semantic | restored | port added a shift inventory menu module; official tank has none |
| tasty | replaced | replaced | as savory; priority 40 matches the official edible trait priority |
| the_one_probe | format | none | C |
| thorns_shell | semantic | restored | built without traitTwoPlusOne, so the map level display and the thorns translation key were missing Format parts: T. |
| turtle_shell | format | none | AO |
| turtles_grace | format | none | AOS |
| unburdened | number | balance | armor use item speed 0.05 per level vs official 0.10 Format parts: O. |
| vintage | format | none | AOS |
| vital_protection_skull | format | none | T |
| worldbound | semantic | restored | rarity was uncommon, official rare |
| writable | format | none | T |

## Fluid effects (56 files)

| File | Bucket | Action | Detail |
| - | - | - | - |
| biodiesel | format | none | C |
| concrete | semantic | restored | datagen wrote minecraft:air for the sprayed concrete block (FakeRegistryEntry.block cannot build an unregistered block in 26.1), so with Immersive Engineering loaded the effect would have placed air; hand corrected to immersiveengineering:concrete_sprayed. Also C Format parts: C. |
| creosote | format | none | C |
| earth_slime | semantic | restored | move block sound was minecraft:block.slime_block.fall, official tconstruct:slime_sling |
| ender_slime | format | none | FP |
| fiery_liquid | format | none | CF |
| ichor | format | none | F |
| liquid_soul | format | none | F |
| molten_aluminum | format | none | C |
| molten_amethyst | format | none | F |
| molten_bendalloy | format | none | C |
| molten_brass | format | none | C |
| molten_bronze | format | none | C |
| molten_cadmium | format | none | C |
| molten_chromium | format | none | C |
| molten_cinderslime | format | none | F |
| molten_clay | format | none | F |
| molten_constantan | format | none | C |
| molten_debris | format | none | F |
| molten_diamond | format | none | F |
| molten_duralumin | format | none | C |
| molten_electrum | format | none | C |
| molten_emerald | format | none | F |
| molten_enderium | format | none | C |
| molten_glass | format | none | F |
| molten_invar | format | none | C |
| molten_knightmetal | format | none | F |
| molten_knightslime | format | none | F |
| molten_lead | format | none | C |
| molten_lumium | format | none | C |
| molten_nickel | format | none | C |
| molten_nicrosil | format | none | C |
| molten_obsidian | format | none | F |
| molten_osmium | format | none | C |
| molten_pewter | format | none | C |
| molten_pig_iron | format | none | F |
| molten_platinum | format | none | C |
| molten_quartz | format | none | F |
| molten_queens_slime | format | none | F |
| molten_refined_glowstone | format | none | C |
| molten_refined_obsidian | format | none | C |
| molten_signalum | format | none | C |
| molten_silver | format | none | C |
| molten_slimesteel | format | none | F |
| molten_steeleaf | format | none | CF |
| molten_tin | format | none | C |
| molten_tungsten | format | none | C |
| molten_uranium | format | none | C |
| molten_zinc | format | none | C |
| phenolic_resin | format | none | C |
| potion_create | format | none | C |
| redstone_acid | format | none | C |
| scorched_stone | format | none | F |
| seared_stone | format | none | F |
| sky_slime | semantic | restored | move block sound was minecraft:block.slime_block.fall, official tconstruct:slime_sling. Also F Format parts: F. |
| venom | format | none | F |

## Mob equipment (5 files)

| File | Bucket | Action | Detail |
| - | - | - | - |
| drowned | number | balance | swasher materials fixed flint, wood, iron vs official random ancient materials |
| melting_pan | number | balance | fixed iron, wood vs official random ancient materials |
| piglins | number | balance | battlesign fixed wood, iron, wood vs official random ancient materials. Also C Format parts: C. |
| war_pick | number | balance | fixed rock, wood, iron vs official random ancient materials |
| wither_skeleton | number | balance | fixed flint, wood, iron vs official random ancient materials |

## Counts

- modifiers: format 58, number 8, replaced 3, semantic 17
- fluid_effects: format 53, semantic 3
- mob_equipment: number 5

## Missing and replaced official IDs (X4)

| Official ID | Status | Detail |
| - | - | - |
| `tinkering/modifiers/chrysophilite` | replaced | Static Java modifier `TinkerModifiers.chrysophilite` (the pre-3.12 official implementation). Official 3.12.1 is JSON: `tconstruct:golden_attribute` on `tconstruct:generic.chrysophilite` (1 + 1 per gold piece) plus a piglin neutral flag at level 2. Continuum counts gold pieces in the helmet's data and the gold skull carries the extra `golden` trait, so the count includes the skull itself: 1 + other gold pieces, the same number official gets from its flat 1. Differences: the official skull is piglin neutral only at level 2 (Continuum always, through `golden`), official watches every armor slot change (Continuum the helmet), Continuum has no `generic.chrysophilite` attribute. Porting `golden_attribute` needs a new module and attribute; left for a follow-up. |
| `tinkering/modifiers/gold_guard` | replaced | Static Java modifier `TinkerModifiers.goldGuard`. Official: max health +4 + 4 per gold piece through `golden_attribute`, clamps current health when removed. Continuum: +4 per gold piece including the skull (so the same +4 + 4 per other piece), no health clamp on removal. Same follow-up as chrysophilite. |
| `tinkering/modifiers/edible` | replaced | Official trait modifier (`tconstruct:edible` module plus edible tooltip, priority 40) is folded into the `tconstruct:edible` module on savory, scrumptious and tasty (`library/modifiers/modules/behavior/EdibleModule`). Numbers checked in `ModifierDataParityTest.edibleReplacementKeepsOfficialFoodNumbers`. Lang: `tool_stat.tconstruct.hunger`, `.hunger.description`, `.saturation`, `.saturation.description` added (the stats exist as `EdibleModule.HUNGER` and `SATURATION`). `eat_duration` and `edible_counter_chance` keys not added: Continuum has no such stats (duration and counter chance are module fields). |
| `recipe/tools/modifiers/slotless/embellishment/wood/wood` | restored | `woodTexture(consumer, MaterialIds.wood, Items.STICK, folder)` added back to `ModifierRecipeProvider`, JSON written in the Continuum recipe format. |
| `tinkering/mob_equipment/minotaur` | restored | It existed as `tconstruct:twilightforest_minotaur`; renamed to the official ID in the provider and the JSON (still gated on Twilight Forest, which is not in the pack). Its fixed materials are in the balance proposal with the other mob equipment. |

## Other semantic differences found and fixed on parity/modifiers

| Where | Detail |
| - | - |
| `tools/logic/ModifierEvents.bounceOnFall` (the "update airborn status" block) | Official sets `hasImpulse` so the server sends the bounce velocity to tracking clients at once. 26.1 renamed the field to `needsSync`; the port replaced it with the no-op `setDeltaMovement(getDeltaMovement())`. Restored as `living.needsSync = true`. |
| `tools/logic/ModifierEvents.bounceOnFall` threshold | Official skips bouncing while `fallDistance <= 0.5 + STEP_HEIGHT_ADDITION` (Forge attribute, default 0). The port used the vanilla `STEP_HEIGHT` value (player base 0.6), which raised the minimum bounce fall to 1.1 blocks. Now `0.5 + (step height value - base value)`. |
| `tools/logic/ModifierEvents.onPotionStart` | Official only rescales effects that have curative items, which excludes the tinkers no milk effects (teleport and fireball cooldowns, bleeding, calcified, momentum, insatiable, self destruct) and helmet charging. The port rescaled everything, so balm shortened cooldowns and boon stretched momentum. 26.1 has no per effect cure list; `ModifierEvents.hasNoCurativeItems` matches those classes. |
| `tools/modifiers/traits/skull/PlagueModifier` | Same curative items check: official plague does not copy cooldowns or bleeding onto the target. Restored with the shared helper. |

## Modifier tags and the enchantment map

| File | Bucket | Action | Detail |
| - | - | - | - |
| `tinkering/tags/modifiers/extract_blacklist/upgrade` | semantic | restored | port left the tag empty; official blocks extracting leaping, reflecting and returning (different slot types per level) |
| `tinkering/tags/modifiers/extract_blacklist/slotless` | semantic | restored | feather_fall was missing |
| `tinkering/tags/modifiers/extract_blacklist/tools` | semantic | restored | banner was missing, so the banner cosmetic could be extracted |
| `tinkering/tags/modifiers/invisible_ink_blacklist` | semantic | restored | banner was missing |
| `tinkering/tags/modifiers/abilities/general` | added | none | Continuum adds its apotheosis modifier |
| `tinkering/tags/modifiers/jei/*` (4) | missing | none | recipe viewer stream |
| `tinkering/tags/materials/*` (4 differ) | not triaged | none | material tags, materials stream |
| `tinkering/enchantments_to_modifiers.json` | format | none | keys written without the minecraft namespace (parsed the same) and 1.20 `sweeping` is 26.1 `sweeping_edge`; every mapped modifier is equal |

The blacklist entries come from `common/data/tags/ModifierTagProvider.java`, which is not in this stream's file list. The entries were added there too, as extra `.add(...)` calls only, so provider and JSON stay in step; listed in the report.

## Checked and unchanged

| Item | Result |
| - | - |
| `airborn` | JSON equal to official. `ProtectionModule` attacker predicate resolves to `TinkerPredicate.AIRBORNE` (same singleton dragonshot and ModifierEvents use), flat 2.5, single level. Sky slimeskin default traits give airborn 1 on a cuirass. Runtime cases in `ModifierParityServerFixture`. |
| `rugged` level display | Official's provider calls `levelDisplay` twice (`SINGLE_LEVEL`, then `NO_LEVELS`); the last call wins, so official JSON and Continuum's provider and JSON all resolve to `no_levels`. The brief's drift note was a misread; no change needed. |
| `tconstruct/tags/damage_type` | The one differing file is `protection/melee.json`, not a rugged tag. Continuum inlines the contents of `tconstruct:is_melee` instead of referencing it; the resolved set is identical (a datapack adding to `is_melee` would not reach melee protection in Continuum). Its provider is outside this stream's files; left unchanged and reported. `rugged/terrain` and `rugged/attacks` equal official (only a trailing newline differs). |
| Rugged damage canceling | Not equal to official. ToolEvents zeroes the amount (`event.setAmount(0)`) where official cancels the event, so vanilla `hurtServer` still starts the hurt animation and sound, sets invulnerability frames and applies the knockback hop (for example walking on cactus or magma). ToolEvents is outside this stream's files: the one line change is written out in `modifiers-REPORT.md` (section "Needs the coordinator"), fixture case `rugged_cancels_hurt_like_official` fails until it is applied. It also affects frost walker and long fall, the other damage block users. |

## Outside this stream's files (reported, not changed)

- `tools/logic/ToolEvents.java` damage block cancel (see the rugged row above).
- `tools/modules/ReduceEffectOnUnequipModule.java`: official skips effects without curative items; the port reduces every non infinite effect of the category. Same helper applies.
- `shared/item/CheeseItem.java`: official cheese only removes milk curable effects.
- `common/data/tags/DamageTypeTagProvider.java`: `protection/melee` inlines `is_melee` (see above).
- `common/data/FakeRegistryEntry.block`: returns air in 26.1 datagen, which is how `concrete.json` got `minecraft:air`. Check `concrete.json` after any `runData`.
- `tools/logic/ModifierEvents` reflecting: official sets `projectile.leftOwner = true`; the 26.1 field is private, so the port drops it. Needs an access transformer if it matters; not changed.
