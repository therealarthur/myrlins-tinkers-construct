package aebm.continuumtests;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.impl.BasicModifier;
import slimeknights.tconstruct.library.modifiers.impl.ComposableModifier;
import slimeknights.tconstruct.library.modifiers.util.ModifierLevelDisplay;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Table driven parity check added by parity/modifiers (2026-09-29, workstream X3).
 * Every modifier ID in official TConstruct v3.12.1.231 (commit a5a03249) must load from this checkout's generated data
 * through the real modifier parser, with the official level display and the official module types in official order.
 * Continuum's Apotheosis bridge modules are ignored. Entries documented as replaced, or pending the balance decision on
 * parity/balance, are listed below with the reason; see local-audit/modifiers-triage.md.
 */
final class ModifierDataParityTest {
  /**
   * Official modifier path | official level display JSON | official module types in order | official redirect target
   * (empty unless the official file only redirects to another modifier). Generated from official JSON.
   */
  private static final String OFFICIAL = """
    airborne|"tconstruct:no_levels"|tconstruct:conditional_mining_speed,tconstruct:conditional_stat|
    airborn|"tconstruct:single_level"|tconstruct:protection|
    ambush|"tconstruct:default"|tconstruct:conditional_stat,tconstruct:conditional_power,tconstruct:conditional_melee_damage|
    amorphous|"tconstruct:default"|tconstruct:stat_boost|
    antiaquatic|"tconstruct:default"|tconstruct:conditional_melee_damage|
    antitoxin|"tconstruct:default"|tconstruct:conditional_melee_damage,tconstruct:conditional_stat|
    aqua_affinity|"tconstruct:no_levels"|tconstruct:constant_enchantment|
    attractive|"tconstruct:default"|tconstruct:projectile_attract_mobs|
    autosmelt|"tconstruct:pluses"|tconstruct:autosmelt|
    ballista|"tconstruct:no_levels"|tconstruct:volatile_flag|
    balm_of_sssss|"tconstruct:single_level"|tconstruct:attribute|
    bane_of_sssss|"tconstruct:default"|tconstruct:conditional_melee_damage,tconstruct:weapon_mob_effect|
    banner|"tconstruct:no_levels"|tconstruct:banner|
    barebow|"tconstruct:no_levels"|tconstruct:volatile_flag|
    barkskin|"tconstruct:no_levels"|tconstruct:capacity_bar,tconstruct:durability_shield|
    blast_protection|"tconstruct:default"|tconstruct:enchantment_ignoring_protection,tconstruct:protection|
    blasting|"tconstruct:default"|tconstruct:conditional_mining_speed|
    blaze_disguise|"tconstruct:single_level"|tconstruct:mob_disguise|
    blindshot|"tconstruct:default"|tconstruct:stat_boost|
    blockade|"tconstruct:default"|tconstruct:stat_boost,tconstruct:requirements|
    blocking|"tconstruct:no_levels"|tconstruct:blocking|
    blunted|"tconstruct:no_levels"|tconstruct:volatile_flag|
    bonking|"tconstruct:single_level"|tconstruct:sling_knockback|
    boon_of_sssss|"tconstruct:single_level"|tconstruct:attribute,tconstruct:reduce_effect_on_unequip|
    bounce|"tconstruct:default"|tconstruct:projectile_bounce|
    bouncy|"tconstruct:no_levels"|tconstruct:attribute|
    boundless|"tconstruct:default"|tconstruct:attribute|
    brushing|"tconstruct:no_levels"|tconstruct:brush|
    bucketing|"tconstruct:no_levels"|tconstruct:trait,tconstruct:stat_boost,tconstruct:bucket,tconstruct:tank_interaction,tconstruct:show_offhand,tconstruct:show_interaction_source|
    bulk_quiver|"tconstruct:default"|tconstruct:quiver_inventory,tconstruct:bulk_quiver,tconstruct:inventory_menu|
    cave_spider_disguise|"tconstruct:single_level"|tconstruct:mob_disguise|
    channeling|"tconstruct:no_levels"|tconstruct:channeling|
    charge_attack|"tconstruct:no_levels"|tconstruct:conditional_melee_damage|
    cheap|"tconstruct:no_levels"|tconstruct:craft_count|
    chrysophilite|"tconstruct:single_level"|tconstruct:golden_attribute,tconstruct:volatile_flag|
    cobalamin|"tconstruct:single_level"|tconstruct:attribute,tconstruct:attribute,tconstruct:attribute|
    collecting|"tconstruct:no_levels"|tconstruct:tool_actions,tconstruct:requirements|
    conductive|"tconstruct:default"|tconstruct:weapon_mob_effect,tconstruct:counter_mob_effect|
    consecrated_skull|{"level":{"each_level":1,"flat":1},"type":"tconstruct:map_level"}|tconstruct:trait,tconstruct:trait|
    consecrated|"tconstruct:default"|tconstruct:protection|
    cooling|"tconstruct:default"|tconstruct:conditional_melee_damage|
    crafting_table|"tconstruct:no_levels"|tconstruct:inventory_menu,tconstruct:inventory_slot_menu,tconstruct:volatile_flag|
    creeper_disguise|"tconstruct:single_level"|tconstruct:mob_disguise|
    crumbling|"tconstruct:default"|tconstruct:conditional_mining_speed|
    crystalbound|"tconstruct:default"|tconstruct:restrict_projectile_angle,tconstruct:stat_boost,tconstruct:stat_boost|
    crystalshot|"tconstruct:no_levels"|tconstruct:variant_color,tconstruct:infinity|
    crystalstrike|"tconstruct:default"|tconstruct:attribute,tconstruct:attribute,tconstruct:armor_level|
    cultivated|"tconstruct:default"|tconstruct:repair|
    decay|"tconstruct:default"|tconstruct:weapon_mob_effect,tconstruct:tool_usage_mob_effect,tconstruct:counter_mob_effect,tconstruct:counter_mob_effect|
    deciduous|"tconstruct:default"|tconstruct:share_durability|
    dense|"tconstruct:default"|tconstruct:repair,tconstruct:repair,tconstruct:reduce_tool_damage|
    depth_protection|"tconstruct:default"|tconstruct:depth_protection|
    depth_strider|"tconstruct:default"|tconstruct:constant_enchantment|
    diamond|"tconstruct:no_levels"|tconstruct:rarity,tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:set_stat,tconstruct:stat_boost,tconstruct:volatile_flag|
    double_jump|{"first_unique":false,"type":"tconstruct:unique","unique_until":4}|tconstruct:attribute|
    draconic|"tconstruct:single_level"|tconstruct:modifier_slot|
    dragonborn|"tconstruct:default"|tconstruct:max_armor_attribute,tconstruct:protection|
    dragonfall|"tconstruct:single_level"|tconstruct:attribute,tconstruct:attribute|
    dragonheart|"tconstruct:single_level"|tconstruct:adjust_damage|
    dragonshot|"tconstruct:default"|tconstruct:conditional_stat|
    drawback|"tconstruct:default"|tconstruct:reverse_punch|
    drill_attack|"tconstruct:no_levels"|tconstruct:tool_actions,tconstruct:requirements|
    drowned_disguise|"tconstruct:single_level"|tconstruct:mob_disguise,tconstruct:rarity|
    ductile|"tconstruct:default"|tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:stat_boost|
    dyed|"tconstruct:no_levels"|tconstruct:dye|
    economical|"tconstruct:no_levels"|tconstruct:craft_count|
    edible_tooltip|"tconstruct:default"|tconstruct:stat_tooltip,tconstruct:stat_tooltip|
    edible|"tconstruct:default"|tconstruct:edible,tconstruct:trait|
    embellishment|"tconstruct:no_levels"|tconstruct:embellishment|
    embossed|"tconstruct:single_level"|tconstruct:modifier_slot|
    emerald|"tconstruct:no_levels"|tconstruct:rarity,tconstruct:stat_boost,tconstruct:repair,tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:set_stat,tconstruct:stat_boost|
    enderclearance|"tconstruct:single_level"|tconstruct:enderclearance|
    enderdodging|"tconstruct:single_level"|tconstruct:teleport_dodge,tconstruct:teleport_dodge|
    enderference|"tconstruct:default"|tconstruct:volatile_flag,tconstruct:weapon_mob_effect,tconstruct:counter_mob_effect|
    enderman_disguise|"tconstruct:single_level"|tconstruct:mob_disguise,tconstruct:volatile_flag|
    enhanced|"tconstruct:default"|tconstruct:modifier_slot|
    entangled|"tconstruct:no_levels"|tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:damage_on_unequip|
    entwined|"tconstruct:single_level"|tconstruct:attribute,tconstruct:damage_on_unequip|
    erratic|"tconstruct:no_levels"|tconstruct:material_variant_color,tconstruct:stat_boost|
    expanded|"tconstruct:default"|tconstruct:volatile_int|
    experienced|"tconstruct:default"|tconstruct:volatile_float,tconstruct:attribute|
    explosive|"tconstruct:default"|tconstruct:projectile_explosion|
    farsighted|"tconstruct:default"|tconstruct:fov|
    feather_falling|{"level":{"each_level":2},"type":"tconstruct:map_level"}|tconstruct:trait|
    feather_fall|"tconstruct:default"|tconstruct:protection|
    featherweight|"tconstruct:default"|tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:protection,tconstruct:attribute,tconstruct:attribute|
    fiery|"tconstruct:default"|tconstruct:fiery_attack,tconstruct:fiery_counter|
    fins_ammo|"tconstruct:default"|tconstruct:trait|
    fins|"tconstruct:single_level"|tconstruct:stat_boost,tconstruct:conditional_power,tconstruct:volatile_flag|
    fire_protection|"tconstruct:default"|tconstruct:enchantment_ignoring_protection,tconstruct:protection|
    fireborn|"tconstruct:single_level"|tconstruct:enchantment_ignoring_protection,tconstruct:effect_immunity|
    fireprimer|"tconstruct:no_levels"|tconstruct:volatile_int|
    firestarter|"tconstruct:no_levels"|tconstruct:place_fire,tconstruct:show_offhand,tconstruct:show_interaction_source|
    fishing|"tconstruct:no_levels"|tconstruct:fishing,tconstruct:show_interaction_source|
    flame_barrier|"tconstruct:default"|tconstruct:flame_barrier|
    flamestance|"tconstruct:no_levels"|tconstruct:conditional_melee_damage,tconstruct:conditional_stat,tconstruct:protection|
    flamewake|"tconstruct:single_level"|tconstruct:fire_walker|
    flinging|"tconstruct:single_level"|tconstruct:sling_leap|
    float|"tconstruct:default"|tconstruct:weapon_mob_effect|
    forecast|"tconstruct:single_level"|tconstruct:modifier_slot|
    forming|"tconstruct:single_level"|tconstruct:stat_boost,tconstruct:stat_boost|
    fortified|"tconstruct:default"|tconstruct:modifier_slot|
    fortunate|{"first_unique":false,"type":"tconstruct:unique","unique_until":2}|tconstruct:constant_enchantment,tconstruct:attribute,tconstruct:armor_looting|
    fortune|"tconstruct:default"|tconstruct:constant_enchantment,tconstruct:armor_harvest_enchantment,tconstruct:stat_boost,tconstruct:attribute|
    freezing|"tconstruct:default"|tconstruct:freezing_attack,tconstruct:freezing_counter|
    frost_walker|"tconstruct:no_levels"|tconstruct:block_damage,tconstruct:replace_fluid|
    frostshield|"tconstruct:single_level"|tconstruct:capacity_bar,tconstruct:durability_shield,tconstruct:damage_to_capacity|
    frosttouch|"tconstruct:default"||tconstruct:slow_bones
    fuse|"tconstruct:no_levels"|tconstruct:projectile_fuse|
    gilded|"tconstruct:single_level"|tconstruct:modifier_slot|
    glowing|"tconstruct:single_level"|tconstruct:place_glow,tconstruct:glow_walker,tconstruct:projectile_place_glow,tconstruct:show_offhand,tconstruct:show_interaction_source,tconstruct:block_item_provider|
    godspeed|"tconstruct:single_level"|tconstruct:attribute,tconstruct:attribute,tconstruct:attribute|
    gold_guard|"tconstruct:single_level"|tconstruct:golden_attribute,tconstruct:volatile_flag|
    golden|"tconstruct:no_levels"|tconstruct:volatile_flag|
    grapple|"tconstruct:no_levels"|tconstruct:tool_actions,tconstruct:requirements|
    harmonious|"tconstruct:single_level"|tconstruct:modifier_slot|
    harvest|"tconstruct:no_levels"|tconstruct:harvest,tconstruct:show_offhand,tconstruct:show_interaction_source|
    haste|{"first_unique":false,"type":"tconstruct:unique","unique_until":5}|tconstruct:stat_boost,tconstruct:attribute|
    headlight|"tconstruct:no_levels"|tconstruct:variant_name,tconstruct:headlight|
    heavy|"tconstruct:default"|tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:attribute,tconstruct:attribute,tconstruct:attribute|
    holy|"tconstruct:default"|tconstruct:conditional_power|
    hover|"tconstruct:no_levels"|tconstruct:projectile_gravity|
    husk_disguise|"tconstruct:single_level"|tconstruct:mob_disguise,tconstruct:rarity|
    hydraulic|"tconstruct:default"|tconstruct:conditional_mining_speed|
    impaling|"tconstruct:default"|tconstruct:arrow_pierce|
    invariant|"tconstruct:default"|tconstruct:conditional_melee_damage,tconstruct:conditional_stat,tconstruct:protection|
    iron_armor|"tconstruct:default"|tconstruct:volatile_flag|
    item_frame|"tconstruct:default"|tconstruct:inventory|
    jagged|"tconstruct:default"|tconstruct:conditional_melee_damage,tconstruct:conditional_mining_speed|
    keen|"tconstruct:default"|tconstruct:stat_boost|
    killager|"tconstruct:default"|tconstruct:conditional_melee_damage|
    kinetic|"tconstruct:default"|tconstruct:kinetic|
    knockback_resistance|"tconstruct:single_level"|tconstruct:stat_boost,tconstruct:material_variant_color|
    knockback|"tconstruct:default"|tconstruct:knockback,tconstruct:attribute,tconstruct:sling_force,tconstruct:sling_force|
    leaping|"tconstruct:default"|tconstruct:attribute,tconstruct:attribute|
    lightspeed_armor|"tconstruct:default"||tconstruct:lightspeed
    lightspeed|"tconstruct:default"|tconstruct:conditional_mining_speed,tconstruct:lightspeed_attribute|
    lightweight|"tconstruct:default"|tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:stat_boost|
    long_fall|"tconstruct:no_levels"|tconstruct:requirements,tconstruct:block_damage|
    looter|{"first_unique":false,"type":"tconstruct:unique","unique_until":2}|tconstruct:armor_looting,tconstruct:attribute|
    looting|"tconstruct:default"|tconstruct:weapon_looting,tconstruct:armor_looting|
    loyal|"tconstruct:single_level"|tconstruct:protection|
    luck|{"first_unique":false,"type":"tconstruct:unique","unique_until":3}|tconstruct:constant_enchantment,tconstruct:armor_harvest_enchantment,tconstruct:weapon_looting,tconstruct:armor_looting,tconstruct:stat_boost,tconstruct:attribute|
    lure_rod|"tconstruct:default"|tconstruct:trait|
    lure|"tconstruct:default"|tconstruct:stat_boost|
    lustrous|"tconstruct:default"||
    magic_protection|"tconstruct:default"|tconstruct:max_armor_attribute,tconstruct:protection|
    magnetic|"tconstruct:default"|tconstruct:tool_usage_mob_effect,tconstruct:volatile_int,tconstruct:armor_level|
    maintained|"tconstruct:default"|tconstruct:conditional_mining_speed,tconstruct:conditional_stat,tconstruct:attribute|
    melee_protection|"tconstruct:default"|tconstruct:max_armor_attribute,tconstruct:protection|
    melting|"tconstruct:pluses"|tconstruct:trait,tconstruct:stat_boost,tconstruct:melting|
    minimap|"tconstruct:default"|tconstruct:inventory,tconstruct:minimap|
    mithridatism|"tconstruct:no_levels"|tconstruct:effect_immunity|
    multishot|"tconstruct:default"|tconstruct:volatile_int|
    nearsighted|"tconstruct:default"|tconstruct:fov|
    necrotic|"tconstruct:default"|tconstruct:lifesteal|
    netherite|"tconstruct:no_levels"|tconstruct:rarity,tconstruct:volatile_flag,tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:set_stat,tconstruct:stat_boost,tconstruct:volatile_flag|
    offhanded|{"first_unique":false,"type":"tconstruct:unique","unique_until":2}|tconstruct:volatile_flag,tconstruct:volatile_flag|
    overburn|"tconstruct:default"|tconstruct:overburn,tconstruct:stat_boost,tconstruct:trait|
    overcast|"tconstruct:default"|tconstruct:stat_boost,tconstruct:stat_boost|
    overforced|"tconstruct:default"|tconstruct:stat_boost|
    overgrowth|"tconstruct:default"|tconstruct:overgrowth|
    overlord|"tconstruct:default"|tconstruct:stat_copy,tconstruct:stat_boost,tconstruct:stat_boost|
    overshield|"tconstruct:default"|tconstruct:overshield|
    overslime_friend|"tconstruct:default"||
    overwield|"tconstruct:default"|tconstruct:conditional_mining_speed,tconstruct:mining_capacity,tconstruct:conditional_mining_speed,tconstruct:mining_capacity,tconstruct:attribute,tconstruct:melee_capacity,tconstruct:conditional_stat,tconstruct:conditional_stat,tconstruct:launch_capacity|
    padded|"tconstruct:default"|tconstruct:knockback,tconstruct:sling_force|
    path_maker|"tconstruct:default"||tconstruct:pathing
    pathing|"tconstruct:no_levels"|tconstruct:campfire_extinguish,tconstruct:tool_action_transform,tconstruct:tool_action_walk_transform,tconstruct:show_offhand,tconstruct:show_interaction_source|
    pierce|"tconstruct:default"|tconstruct:stat_boost,tconstruct:weapon_mob_effect|
    piercing_guard|"tconstruct:default"|tconstruct:counter_mob_effect|
    piglin_brute_disguise|"tconstruct:single_level"|tconstruct:mob_disguise|
    piglin_disguise|"tconstruct:single_level"|tconstruct:mob_disguise,tconstruct:rarity|
    plowing|"tconstruct:default"||tconstruct:tilling
    pockets|"tconstruct:default"|tconstruct:trait|
    pocket|"tconstruct:default"|tconstruct:inventory,tconstruct:inventory_menu|
    power|"tconstruct:default"|tconstruct:stat_boost|
    preserved|"tconstruct:default"|tconstruct:stat_boost,tconstruct:repair|
    projectile_protection|"tconstruct:default"|tconstruct:max_armor_attribute,tconstruct:protection|
    protection|"tconstruct:default"|tconstruct:protection|
    punch|"tconstruct:default"|tconstruct:punch|
    quick_charge|"tconstruct:default"|tconstruct:stat_boost|
    raging|"tconstruct:default"|tconstruct:conditional_melee_damage,tconstruct:conditional_stat|
    ram_attack|"tconstruct:single_level"|tconstruct:conditional_melee_damage,tconstruct:melee_instrument|
    reach|"tconstruct:default"|tconstruct:attribute,tconstruct:attribute|
    rebalanced|"tconstruct:no_levels"|tconstruct:swappable_slot,tconstruct:swappable_slot,tconstruct:swappable_bonus_slot,tconstruct:swappable_bonus_slot,tconstruct:swappable_tool_traits|
    rebound|"tconstruct:default"|tconstruct:conditional_power|
    rebuff|"tconstruct:single_level"|tconstruct:restore_lost_health|
    recapitated|"tconstruct:single_level"|tconstruct:modifier_slot|
    reclaim|"tconstruct:no_levels"|tconstruct:volatile_flag|
    recurrent_protection|"tconstruct:default"|tconstruct:recurrent_protection|
    redirected|"tconstruct:no_levels"|tconstruct:swappable_tool_traits|
    reflecting|"tconstruct:default"|tconstruct:volatile_int|
    reinforced|"tconstruct:default"|tconstruct:reduce_tool_damage,tconstruct:reduce_tool_damage|
    respiration_skull|{"level":{"each_level":1,"flat":1},"type":"tconstruct:map_level"}|tconstruct:trait,tconstruct:trait|
    respiration|"tconstruct:default"|tconstruct:constant_enchantment|
    restore|"tconstruct:single_level"|tconstruct:restore_lost_health|
    resurrected|"tconstruct:single_level"|tconstruct:modifier_slot,tconstruct:requirements|
    returning|"tconstruct:default"|tconstruct:volatile_int|
    revenge|"tconstruct:single_level"|tconstruct:counter_mob_effect,tconstruct:clear_effect_on_unequip|
    reverted|"tconstruct:no_levels"|tconstruct:modifier_slot,tconstruct:modifier_slot,tconstruct:modifier_slot,tconstruct:trait|
    revitalizing|"tconstruct:default"|tconstruct:attribute,tconstruct:update_health,tconstruct:update_health|
    ricochet|"tconstruct:default"|tconstruct:attribute|
    rugged|"tconstruct:no_levels"|tconstruct:block_damage,tconstruct:block_damage,tconstruct:volatile_flag|
    savory|"tconstruct:single_level"|tconstruct:trait,tconstruct:edible_representative_item,tconstruct:edible_consume_durability,tconstruct:edible_cure_random_effect,tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:stat_boost|
    scope|"tconstruct:no_levels"|tconstruct:zoom|
    scorch_protection|"tconstruct:default"|tconstruct:enchantment_ignoring_protection,tconstruct:protection|
    scorching|"tconstruct:default"|tconstruct:conditional_melee_damage|
    scrumptious|"tconstruct:single_level"|tconstruct:trait,tconstruct:edible_representative_item,tconstruct:edible_consume_durability,tconstruct:edible_remove_effect,tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:stat_boost|
    searing|"tconstruct:default"|tconstruct:conditional_mining_speed|
    severing|"tconstruct:default"|tconstruct:severing|
    sharpness|{"first_unique":true,"type":"tconstruct:unique","unique_until":5}|tconstruct:stat_boost|
    sharpweight|"tconstruct:default"||tconstruct:heavy
    shears|"tconstruct:pluses"|tconstruct:show_offhand,tconstruct:shears,tconstruct:show_interaction_source|
    shell_gut|"tconstruct:single_level"|tconstruct:effect_immunity,tconstruct:effect_immunity,tconstruct:effect_immunity|
    shell_storage|"tconstruct:no_levels"|tconstruct:trait|
    shield_strap|"tconstruct:default"|tconstruct:inventory,tconstruct:shield_strap,tconstruct:inventory_menu,tconstruct:volatile_flag|
    shiny|"tconstruct:no_levels"|tconstruct:volatile_flag,tconstruct:rarity|
    shock|"tconstruct:default"|tconstruct:conditional_melee_damage,tconstruct:conditional_power,tconstruct:thorns,tconstruct:thorns|
    shulker_box|"tconstruct:single_level"|tconstruct:trait,tconstruct:trait|
    shulking|"tconstruct:default"|tconstruct:max_armor_attribute,tconstruct:protection|
    silky_aoe_shears|"tconstruct:default"|tconstruct:shears|
    silky_shears|"tconstruct:pluses"|tconstruct:shears,tconstruct:show_interaction_source|
    silky|"tconstruct:no_levels"|tconstruct:constant_enchantment,tconstruct:armor_harvest_enchantment|
    skeleton_disguise|"tconstruct:single_level"|tconstruct:mob_disguise|
    skyfall|"tconstruct:single_level"|tconstruct:attribute,tconstruct:attribute|
    sleeves|"tconstruct:default"|tconstruct:sleeves,tconstruct:inventory|
    slimeball|"tconstruct:no_levels"|tconstruct:fireball|
    sliver|"tconstruct:default"|tconstruct:inventory,tconstruct:trick_quiver,tconstruct:inventory_menu,tconstruct:requirements|
    slow_bones|"tconstruct:single_level"|tconstruct:effect_immunity,tconstruct:armor_attack_mob_effect|
    slurping|"tconstruct:default"|tconstruct:slurping,tconstruct:trait,tconstruct:stat_boost|
    smashing_ammo|"tconstruct:default"|tconstruct:trait|
    smashing|"tconstruct:default"|tconstruct:smashing|
    smelting|"tconstruct:single_level"|tconstruct:inventory_menu,tconstruct:smelting|
    smite|"tconstruct:default"|tconstruct:conditional_melee_damage|
    snow_boots|"tconstruct:no_levels"|tconstruct:volatile_flag|
    snowdrift|"tconstruct:no_levels"|tconstruct:cover_ground|
    soft|"tconstruct:no_levels"|tconstruct:material_variant_color,tconstruct:stat_boost|
    solar_powered|"tconstruct:single_level"|tconstruct:reduce_tool_damage|
    solid|"tconstruct:default"|tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:protection,tconstruct:attribute,tconstruct:attribute|
    soul_belt|"tconstruct:no_levels"|tconstruct:armor_level,tconstruct:requirements|
    soulbound|"tconstruct:no_levels"|tconstruct:volatile_flag|
    soulspeed|"tconstruct:default"|tconstruct:soulspeed|
    spectral|"tconstruct:default"|tconstruct:weapon_mob_effect,tconstruct:counter_mob_effect,tconstruct:projectile_place_glow|
    speedy|"tconstruct:default"|tconstruct:attribute|
    spider_disguise|"tconstruct:single_level"|tconstruct:mob_disguise|
    spike|"tconstruct:no_levels"|tconstruct:tool_actions,tconstruct:arrow_pierce|
    spilling_rod|"tconstruct:default"|tconstruct:trait|
    spilling|"tconstruct:default"|tconstruct:trait,tconstruct:stat_boost,tconstruct:spilling|
    spiny|"tconstruct:default"|tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:damage_on_shoot|
    spitting|"tconstruct:default"|tconstruct:spitting,tconstruct:trait,tconstruct:stat_boost|
    splashing|"tconstruct:default"|tconstruct:splashing,tconstruct:trait,tconstruct:stat_boost,tconstruct:show_offhand,tconstruct:show_interaction_source|
    springing|"tconstruct:single_level"|tconstruct:sling_leap|
    springy|"tconstruct:default"|tconstruct:knockback,tconstruct:knockback_counter|
    stalwart|"tconstruct:default"|tconstruct:protection|
    step_up|"tconstruct:default"|tconstruct:attribute|
    sticky|"tconstruct:default"|tconstruct:mob_effect|
    stonebound|"tconstruct:default"|tconstruct:conditional_mining_speed,tconstruct:conditional_melee_damage|
    stoneshield|"tconstruct:default"|tconstruct:capacity_bar,tconstruct:durability_shield,tconstruct:loot_to_capacity|
    stray_disguise|"tconstruct:single_level"|tconstruct:mob_disguise|
    strength|"tconstruct:default"|tconstruct:attribute|
    stringy|"tconstruct:default"|tconstruct:material_repair|
    stripping|"tconstruct:no_levels"|tconstruct:tool_action_transform,tconstruct:tool_action_transform,tconstruct:tool_action_transform,tconstruct:show_offhand,tconstruct:show_interaction_source|
    sturdy|"tconstruct:default"||tconstruct:ductile
    supercharged|"tconstruct:default"|tconstruct:stat_boost,tconstruct:stat_boost|
    sweeping_edge|"tconstruct:default"|tconstruct:sweeping_edge|
    swift_sneak|"tconstruct:default"|tconstruct:constant_enchantment|
    swiftstrike|{"first_unique":false,"type":"tconstruct:unique","unique_until":5}|tconstruct:stat_boost|
    tank|"tconstruct:default"|tconstruct:stat_boost,tconstruct:trait|
    tanned|"tconstruct:no_levels"|tconstruct:tool_damage_range,tconstruct:tool_damage_range|
    tasty|"tconstruct:default"|tconstruct:trait,tconstruct:edible_representative_item,tconstruct:edible_consume_durability,tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:stat_boost|
    temperate|"tconstruct:default"|tconstruct:conditional_mining_speed,tconstruct:conditional_stat,tconstruct:protection,tconstruct:reduce_tool_damage|
    tempered_protection|"tconstruct:default"|tconstruct:protection|
    the_one_probe|"tconstruct:no_levels"|tconstruct:the_one_probe|
    thorns_shell|{"level":{"each_level":1,"flat":1},"type":"tconstruct:map_level"}|tconstruct:trait,tconstruct:trait|
    thorns|"tconstruct:default"|tconstruct:thorns|
    throwing|"tconstruct:no_levels"|tconstruct:throwing|
    tilling|"tconstruct:no_levels"|tconstruct:tool_action_transform,tconstruct:tool_action_walk_transform,tconstruct:show_offhand,tconstruct:show_interaction_source|
    tipped|"tconstruct:single_level"|tconstruct:tipped|
    tool_belt|"tconstruct:pluses"|tconstruct:inventory,tconstruct:tool_belt,tconstruct:inventory_menu|
    trick_quiver|"tconstruct:default"|tconstruct:quiver_inventory,tconstruct:trick_quiver,tconstruct:inventory_menu|
    trim|"tconstruct:no_levels"|tconstruct:trim,tconstruct:modifier_slot,tconstruct:swappable_tool_traits|
    trueshot|"tconstruct:default"|tconstruct:stat_boost|
    turtle_shell|"tconstruct:default"|tconstruct:max_armor_attribute,tconstruct:protection,tconstruct:protection|
    turtles_grace|"tconstruct:single_level"|tconstruct:attribute,tconstruct:constant_enchantment|
    unbreakable|"tconstruct:no_levels"|tconstruct:requirements,tconstruct:durability_color,tconstruct:reduce_tool_damage|
    unburdened|"tconstruct:default"|tconstruct:stat_boost,tconstruct:attribute|
    valiant|"tconstruct:default"|tconstruct:conditional_melee_damage,tconstruct:conditional_power|
    venom|"tconstruct:default"|tconstruct:weapon_mob_effect,tconstruct:counter_mob_effect|
    vintage|"tconstruct:default"|tconstruct:modifier_slot,tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:attribute,tconstruct:attribute,tconstruct:attribute|
    vital_protection_skull|{"level":{"each_level":1,"flat":1},"type":"tconstruct:map_level"}|tconstruct:trait,tconstruct:trait|
    vital_protection|"tconstruct:default"|tconstruct:protection|
    war_charge|"tconstruct:no_levels"|tconstruct:capacity_bar,tconstruct:mining_capacity,tconstruct:launch_capacity,tconstruct:conditional_stat|
    warded|"tconstruct:default"|tconstruct:adjust_damage|
    warping|"tconstruct:single_level"|tconstruct:sling_teleport|
    weak|"tconstruct:no_levels"|tconstruct:stat_boost|
    wings|"tconstruct:no_levels"|tconstruct:volatile_flag|
    wither_skeleton_disguise|"tconstruct:single_level"|tconstruct:mob_disguise,tconstruct:rarity|
    withered|"tconstruct:single_level"|tconstruct:effect_immunity,tconstruct:armor_attack_mob_effect|
    woodwind|"tconstruct:default"|tconstruct:stat_boost,tconstruct:stat_boost|
    workbench|"tconstruct:no_levels"|tconstruct:inventory_menu,tconstruct:inventory_slot_menu,tconstruct:volatile_flag|
    worldbound|"tconstruct:no_levels"|tconstruct:volatile_flag,tconstruct:rarity|
    writable|"tconstruct:single_level"|tconstruct:modifier_slot|
    zombie_disguise|"tconstruct:single_level"|tconstruct:mob_disguise|
    zombified_piglin_disguise|"tconstruct:single_level"|tconstruct:mob_disguise|
    zoom|"tconstruct:no_levels"|tconstruct:zoom|
    """;

  /**
   * Official IDs with no JSON in Continuum because another implementation replaces them. Empty since arthur.9 ported
   * chrysophilite and gold_guard to the official golden_attribute JSON. Former entries:
   * "chrysophilite", "static Java modifier TinkerModifiers.chrysophilite (older official implementation)",
   * "gold_guard", "static Java modifier TinkerModifiers.goldGuard (older official implementation)".
   */
  private static final Map<String, String> REPLACED = Map.of();
  // oracle fixes: edible is the official 3.12.1 JSON again (shared trait plus effect modules), so it is compared like any
  // other official modifier. Former entry: "edible" split into the Continuum tconstruct:edible module on each food modifier.

  /**
   * Modifiers whose modules need the server's dynamic enchantment registry through Continuum Core's has_enchantment
   * predicate, which this unit test JVM does not have. Their level display and module types are still compared from the
   * JSON; the fixture server run loads them for real (a load failure is logged as "Failed to load modifier").
   */
  private static final Set<String> SERVER_REGISTRY_ONLY = Set.of("hydraulic");

  /**
   * Official IDs whose module list was replaced by the single Continuum edible module. Empty since the oracle fixes restored
   * the official 3.12.1 module lists (formerly savory, scrumptious and tasty).
   */
  private static final Set<String> EDIBLE_MODULES = Set.of();

  /**
   * Official IDs whose level display or module list is coupled to a number held for Arthur's approval on parity/balance.
   * These accept either the official value or the documented Continuum value, so the check stays valid after the merge.
   */
  private static final Map<String, String> BALANCE_PENDING_DISPLAY = Map.of(
    "enderclearance", "\"tconstruct:default\"",
    "solar_powered", "\"tconstruct:default\"");
  private static final Map<String, String> BALANCE_PENDING_MODULES = Map.of(
    "featherweight", "tconstruct:stat_boost,tconstruct:stat_boost,tconstruct:protection,tconstruct:attribute");

  /**
   * Official IDs that use a deprecated module, replaced here by the non-deprecated modules with the same behavior
   * (arthur.9). The official list is still the reference; these accept only the documented replacement, and
   * {@link #stickyReplacementCarriesTheLegacyValues()} checks that the values are the legacy module's values.
   */
  private static final Map<String, String> DEPRECATED_MODULE_REPLACEMENTS = Map.of(
    "sticky", "tconstruct:weapon_mob_effect,tconstruct:counter_mob_effect");

  /** Continuum-only Apotheosis bridge modules, not part of official parity. */
  private static boolean isApotheosisBridge(JsonObject module) {
    String type = module.get("type").getAsString();
    return type.equals("tconstruct:apothic_enchantment_cap") || type.equals("tconstruct:luck_apothic_enchantment_cap")
      || (type.equals("tconstruct:requirements") && module.toString().contains("tconstruct:apotheosis"));
  }

  @Test
  void everyOfficialModifierLoadsWithOfficialLevelDisplayAndModules() throws Exception {
    List<String> failures = new ArrayList<>();
    int checked = 0;
    for (String line : OFFICIAL.strip().split("\n")) {
      String[] parts = line.strip().split("\\|", -1);
      String path = parts[0];
      String officialDisplay = canonical(JsonParser.parseString(parts[1]));
      String officialModules = parts[2];
      String officialRedirect = parts.length > 3 ? parts[3] : "";
      if (REPLACED.containsKey(path)) {
        assertFalse(Files.exists(ModifierJsonLoader.generated("tinkering/modifiers/" + path + ".json")),
          path + " is documented as replaced but JSON exists; update the triage");
        continue;
      }
      if (!Files.exists(ModifierJsonLoader.generated("tinkering/modifiers/" + path + ".json"))) {
        failures.add(path + ": missing generated JSON");
        continue;
      }
      checked++;
      JsonObject json = ModifierJsonLoader.readModifier(path);

      // official redirect entries load as no modifier plus a redirect to the same target
      if (!officialRedirect.isEmpty()) {
        Map<ModifierId, ModifierId> redirects = new HashMap<>();
        Modifier modifier = ModifierJsonLoader.load(path, json, redirects);
        if (modifier != null || !new ModifierId(officialRedirect).equals(redirects.get(new ModifierId("tconstruct", path)))) {
          failures.add(path + ": expected a redirect to " + officialRedirect + ", got modifier " + modifier + " and redirects " + redirects);
        }
        continue;
      }

      JsonArray rawModules = json.has("modules") ? json.getAsJsonArray("modules") : new JsonArray();
      String display;
      if (SERVER_REGISTRY_ONLY.contains(path)) {
        display = canonical(json.has("level_display") ? json.get("level_display") : new JsonPrimitive("tconstruct:default"));
      } else {
        Modifier modifier;
        try {
          modifier = ModifierJsonLoader.load(path, json);
        } catch (Exception e) {
          failures.add(path + ": loader threw " + e);
          continue;
        }
        if (!(modifier instanceof ComposableModifier composable)) {
          failures.add(path + ": did not load as a composable modifier (" + modifier + ")");
          continue;
        }
        // level display, through the real parsed object
        display = canonical(ModifierLevelDisplay.LOADER.serialize(levelDisplay(composable)));
        // every module in the JSON must have loaded
        int loaded = modules(composable).size();
        if (loaded != rawModules.size()) {
          failures.add(path + ": loaded " + loaded + " modules from " + rawModules.size() + " in JSON");
        }
      }
      if (!display.equals(officialDisplay) && !display.equals(BALANCE_PENDING_DISPLAY.get(path))) {
        failures.add(path + ": level display " + display + " expected " + officialDisplay);
      }
      if (!EDIBLE_MODULES.contains(path)) {
        List<String> types = new ArrayList<>();
        for (JsonElement element : rawModules) {
          JsonObject module = element.getAsJsonObject();
          if (!isApotheosisBridge(module)) {
            types.add(module.get("type").getAsString());
          }
        }
        String joined = String.join(",", types);
        if (!joined.equals(officialModules) && !joined.equals(BALANCE_PENDING_MODULES.get(path)) && !joined.equals(DEPRECATED_MODULE_REPLACEMENTS.get(path))) {
          failures.add(path + ": modules " + joined + " expected " + officialModules);
        }
      }
    }
    assertTrue(checked >= 300, "expected at least 300 official modifiers to be checked, got " + checked);
    assertTrue(failures.isEmpty(), failures.size() + " modifier parity failures:\n" + String.join("\n", failures));
  }

  @Test
  void edibleReplacementKeepsOfficialFoodNumbers() throws Exception {
    // official: durability_usage flat and per level, counter chance 0.15 per level through a stat, eat duration stat default 16
    Object[][] expected = {
      {"savory", 4, 4, "tconstruct:cheese_ingot"},
      {"scrumptious", 5, 3, "minecraft:honey_bottle"},
      {"tasty", 5, 5, "tconstruct:bacon"},
    };
    for (Object[] row : expected) {
      String path = (String) row[0];
      // oracle fixes: official 3.12.1 layout, the shared tconstruct:edible trait plus one module per effect and the
      // tconstruct:edible_counter_chance stat; eat duration is the tconstruct:eat_duration stat default of 16
      JsonObject trait = null, usage = null, representative = null, counter = null;
      for (JsonElement element : ModifierJsonLoader.readModifier(path).getAsJsonArray("modules")) {
        JsonObject module = element.getAsJsonObject();
        switch (module.get("type").getAsString()) {
          case "tconstruct:trait" -> trait = module;
          case "tconstruct:edible_consume_durability" -> usage = module.getAsJsonObject("durability_usage");
          case "tconstruct:edible_representative_item" -> representative = module;
          case "tconstruct:stat_boost" -> {
            if (module.get("stat").getAsString().equals("tconstruct:edible_counter_chance")) {
              counter = module;
            }
          }
          default -> {}
        }
      }
      assertNotNull(trait, path + " must add the tconstruct:edible trait");
      assertEquals("tconstruct:edible", trait.get("name").getAsString(), path + " edible trait");
      assertNotNull(usage, path + " must use the edible_consume_durability module");
      assertEquals((int) row[1], usage.get("flat").getAsInt(), path + " durability flat");
      assertEquals((int) row[2], usage.get("each_level").getAsInt(), path + " durability per level");
      assertNotNull(counter, path + " must boost tconstruct:edible_counter_chance");
      assertEquals(0.15f, counter.get("each_level").getAsFloat(), 1e-6, path + " counter chance");
      assertNotNull(representative, path + " must use the edible_representative_item module");
      assertEquals(row[3], representative.get("representative_item").getAsString(), path + " representative item");
      assertNotNull(ModifierJsonLoader.load(path), path + " must load");
    }
  }

  /**
   * Official sticky uses the deprecated tconstruct:mob_effect module (MobEffectModule.Legacy): slowness, level 0.5 per
   * level, 20 to 30 ticks, 25% per level on melee, monster melee and projectile hits, and the same on attacks against
   * armor that has the modifier (1 durability per counter). arthur.9 expresses it with the weapon and counter modules.
   * This loads sticky through the real parser and compares every value with the legacy module the old provider built.
   */
  @Test
  void stickyReplacementCarriesTheLegacyValues() throws Exception {
    JsonObject json = ModifierJsonLoader.readModifier("sticky");
    Modifier modifier = ModifierJsonLoader.load("sticky", json);
    assertInstanceOf(ComposableModifier.class, modifier, "sticky must load as a composable modifier");
    List<?> loaded = modules((ComposableModifier) modifier);
    assertEquals(2, loaded.size(), "sticky modules");
    slimeknights.tconstruct.library.modifiers.modules.combat.MobEffectModule.Legacy legacy =
      slimeknights.tconstruct.library.modifiers.modules.combat.MobEffectModule.builder(net.minecraft.world.effect.MobEffects.SLOWNESS.value())
        .level(slimeknights.tconstruct.library.json.RandomLevelingValue.perLevel(0, 0.5f))
        .time(slimeknights.tconstruct.library.json.RandomLevelingValue.random(20, 10))
        .build();

    var weapon = assertInstanceOf(slimeknights.tconstruct.library.modifiers.modules.combat.MobEffectModule.Weapon.class, unwrapHooks(loaded.get(0)), "first module");
    assertEquals(legacy.effect(), weapon.effect(), "weapon effect");
    assertEquals(legacy.chance(), weapon.chance(), "weapon chance");
    assertEquals(legacy.applyBeforeMelee(), weapon.applyBeforeMelee(), "weapon timing");
    assertEquals(legacy.holder(), weapon.holder(), "weapon holder");
    assertEquals(legacy.condition(), weapon.condition(), "weapon condition");

    var counter = assertInstanceOf(slimeknights.tconstruct.library.modifiers.modules.combat.MobEffectModule.ArmorCounter.class, unwrapHooks(loaded.get(1)), "second module");
    assertEquals(legacy.effect(), counter.effect(), "counter effect");
    assertEquals(legacy.chance(), counter.chance(), "counter chance");
    assertEquals(legacy.durabilityUsage(), counter.durabilityUsage(), "counter durability");
    assertEquals(legacy.directDamage(), counter.directDamage(), "counter direct damage");
    assertEquals(legacy.damageSource(), counter.damageSource(), "counter damage source");
    assertEquals(legacy.targetSelf(), counter.targetSelf(), "counter target");
    assertEquals(legacy.holder(), counter.holder(), "counter holder");
    // the legacy counter only ran for tools tagged armor (Legacy.onAttacked); the replacement carries that as its tool condition
    JsonObject counterJson = json.getAsJsonArray("modules").get(1).getAsJsonObject();
    assertEquals("{\"tag\":\"tconstruct:modifiable/armor\",\"type\":\"mantle:tag\"}", canonical(counterJson.get("tool")), "counter tool condition");
    assertEquals(legacy.condition().modifierLevel(), counter.condition().modifierLevel(), "counter level range");
    // together the two modules run on exactly the legacy module's hooks (melee, monster melee, projectile, on attacked)
    java.util.Set<Object> hooks = new java.util.HashSet<>(weapon.getDefaultHooks());
    hooks.addAll(counter.getDefaultHooks());
    assertEquals(new java.util.HashSet<Object>(legacy.getDefaultHooks()), hooks, "hooks");
    for (Object module : loaded) {
      if (module instanceof slimeknights.tconstruct.library.module.WithHooks<?> withHooks) {
        assertTrue(withHooks.hooks().isEmpty() || new java.util.HashSet<Object>(withHooks.hooks()).equals(new java.util.HashSet<Object>(withHooks.module().getDefaultHooks())), "sticky modules must use their default hooks");
      }
    }
  }

  /** Loaded modules are stored with their hooks (WithHooks); returns the module itself. */
  private static Object unwrapHooks(Object loaded) {
    return loaded instanceof slimeknights.tconstruct.library.module.WithHooks<?> withHooks ? withHooks.module() : loaded;
  }

  private static ModifierLevelDisplay levelDisplay(BasicModifier modifier) throws Exception {
    Field field = BasicModifier.class.getDeclaredField("levelDisplay");
    field.setAccessible(true);
    return (ModifierLevelDisplay) field.get(modifier);
  }

  private static List<?> modules(ComposableModifier modifier) throws Exception {
    Field field = ComposableModifier.class.getDeclaredField("modules");
    field.setAccessible(true);
    return (List<?>) field.get(modifier);
  }

  /** Sorted-key compact JSON with numbers normalized, so 1 and 1.0 compare equal. */
  static String canonical(JsonElement element) {
    if (element == null || element.isJsonNull()) {
      return "null";
    }
    if (element.isJsonPrimitive()) {
      JsonPrimitive primitive = element.getAsJsonPrimitive();
      if (primitive.isNumber()) {
        return new BigDecimal(primitive.getAsString()).stripTrailingZeros().toPlainString();
      }
      return primitive.toString();
    }
    if (element.isJsonArray()) {
      List<String> parts = new ArrayList<>();
      for (JsonElement child : element.getAsJsonArray()) {
        parts.add(canonical(child));
      }
      return "[" + String.join(",", parts) + "]";
    }
    TreeMap<String, String> sorted = new TreeMap<>();
    for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
      sorted.put(entry.getKey(), canonical(entry.getValue()));
    }
    List<String> parts = new ArrayList<>();
    sorted.forEach((key, value) -> parts.add(new JsonPrimitive(key) + ":" + value));
    return "{" + String.join(",", parts) + "}";
  }
}
