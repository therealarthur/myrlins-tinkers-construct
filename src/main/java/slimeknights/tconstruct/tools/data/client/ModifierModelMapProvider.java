package slimeknights.tconstruct.tools.data.client;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.equipment.ArmorType;
import slimeknights.tconstruct.library.tools.definition.ModifiableArmorMaterial;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.modifiers.DyedModifierModel;
import slimeknights.tconstruct.library.client.modifiers.NormalModifierModel;
import slimeknights.tconstruct.library.client.modifiers.model.MaterialHasFallbackModifierModel;
import slimeknights.tconstruct.library.client.modifiers.model.ModifierModel;
import slimeknights.tconstruct.library.client.modifiers.model.TrimModifierModel;
import slimeknights.tconstruct.library.data.AbstractModifierModelMapProvider;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.client.MossyModifierModel;
import slimeknights.tconstruct.tools.client.SlimeskullModifierModel;
import slimeknights.tconstruct.tools.data.ModifierIds;

import javax.annotation.Nullable;

/** Provider for modifier models on tools */
public class ModifierModelMapProvider extends AbstractModifierModelMapProvider {
  public ModifierModelMapProvider(PackOutput output) {
    super(output, TConstruct.MOD_ID);
  }

  @Override
  protected void addModels() {
    // small
    tool(TinkerTools.pickaxe).basic(SMALL,
      ModifierIds.diamond, ModifierIds.emerald, ModifierIds.netherite,
      ModifierIds.reinforced, ModifierIds.overforced, ModifierIds.magnetic, ModifierIds.soulbound,
      ModifierIds.experienced, ModifierIds.luck, TinkerModifiers.severing.getId(), ModifierIds.silky,
      ModifierIds.sharpness, ModifierIds.smite, ModifierIds.antiaquatic, ModifierIds.baneOfSssss, ModifierIds.cooling,
      ModifierIds.knockback, ModifierIds.necrotic,
      ModifierIds.blasting, ModifierIds.hydraulic
    ).fluid(ModifierIds.bucketing, SMALL).tank(SMALL)
      .luminosity(7, SMALL, ModifierIds.haste)
      .luminosity(15, SMALL, ModifierIds.lightspeed, ModifierIds.glowing)
      .luminosity(10, SMALL, ModifierIds.fiery)
      .luminosity(2, SMALL, ModifierIds.unbreakable);
    tool(TinkerTools.pickadze).basic(SMALL,
        ModifierIds.emerald, ModifierIds.netherite,
        ModifierIds.reinforced, ModifierIds.overforced, ModifierIds.magnetic, ModifierIds.soulbound,
        ModifierIds.experienced, ModifierIds.luck, ModifierIds.silky,
        ModifierIds.smite, ModifierIds.antiaquatic, ModifierIds.baneOfSssss, ModifierIds.cooling,
        ModifierIds.knockback, ModifierIds.necrotic,
        ModifierIds.blasting, ModifierIds.hydraulic
      ).fluid(ModifierIds.bucketing, SMALL).tank(SMALL)
      .luminosity(7, SMALL, ModifierIds.haste)
      .luminosity(15, SMALL, ModifierIds.lightspeed, ModifierIds.glowing)
      .luminosity(10, SMALL, ModifierIds.fiery)
      .luminosity(2, SMALL, ModifierIds.unbreakable);
    tool(TinkerTools.mattock).basic(SMALL,
        ModifierIds.diamond, ModifierIds.emerald, ModifierIds.netherite,
        ModifierIds.reinforced, ModifierIds.overforced, ModifierIds.magnetic, ModifierIds.soulbound,
        ModifierIds.experienced, ModifierIds.luck, TinkerModifiers.severing.getId(), ModifierIds.silky,
        ModifierIds.sharpness, ModifierIds.smite, ModifierIds.antiaquatic, ModifierIds.baneOfSssss, ModifierIds.cooling,
        ModifierIds.knockback, ModifierIds.necrotic,
        ModifierIds.blasting, ModifierIds.hydraulic
      ).fluid(ModifierIds.bucketing, SMALL).tank(SMALL)
      .luminosity(7, SMALL, ModifierIds.haste)
      .luminosity(15, SMALL, ModifierIds.lightspeed, ModifierIds.glowing)
      .luminosity(10, SMALL, ModifierIds.fiery)
      .luminosity(2, SMALL, ModifierIds.unbreakable);
    tool(TinkerTools.handAxe).basic(SMALL,
        ModifierIds.diamond, ModifierIds.emerald, ModifierIds.netherite,
        ModifierIds.reinforced, ModifierIds.overforced, ModifierIds.magnetic, ModifierIds.soulbound,
        ModifierIds.experienced, ModifierIds.luck, TinkerModifiers.severing.getId(), ModifierIds.silky,
        ModifierIds.sharpness, ModifierIds.smite, ModifierIds.antiaquatic, ModifierIds.baneOfSssss, ModifierIds.cooling,
        ModifierIds.knockback, ModifierIds.necrotic,
        ModifierIds.blasting, ModifierIds.hydraulic
      ).fluid(ModifierIds.bucketing, SMALL).tank(SMALL)
      .luminosity(7, SMALL, ModifierIds.haste)
      .luminosity(15, SMALL, ModifierIds.lightspeed, ModifierIds.glowing)
      .luminosity(10, SMALL, ModifierIds.fiery)
      .luminosity(2, SMALL, ModifierIds.unbreakable);
    tool(TinkerTools.kama).basic(SMALL,
        ModifierIds.diamond, ModifierIds.emerald, ModifierIds.netherite,
        ModifierIds.reinforced, ModifierIds.overforced, ModifierIds.magnetic, ModifierIds.soulbound,
        ModifierIds.experienced, ModifierIds.luck, TinkerModifiers.severing.getId(), ModifierIds.silky,
        ModifierIds.sharpness, ModifierIds.smite, ModifierIds.antiaquatic, ModifierIds.baneOfSssss, ModifierIds.cooling,
        ModifierIds.knockback, ModifierIds.necrotic,
        ModifierIds.blasting, ModifierIds.hydraulic
      ).fluid(ModifierIds.bucketing, SMALL).tank(SMALL)
      .luminosity(7, SMALL, ModifierIds.haste)
      .luminosity(15, SMALL, ModifierIds.lightspeed, ModifierIds.glowing)
      .luminosity(10, SMALL, ModifierIds.fiery)
      .luminosity(2, SMALL, ModifierIds.unbreakable);
    // weapon
    tool(TinkerTools.dagger).basic(SMALL,
        ModifierIds.diamond, ModifierIds.emerald, ModifierIds.netherite,
        ModifierIds.reinforced, ModifierIds.overforced, ModifierIds.magnetic,
        ModifierIds.experienced, ModifierIds.luck, ModifierIds.silky,
        ModifierIds.sharpness, ModifierIds.smite, ModifierIds.antiaquatic, ModifierIds.baneOfSssss, ModifierIds.cooling,
        ModifierIds.knockback,
        ModifierIds.blasting, ModifierIds.hydraulic
      ).fluid(ModifierIds.bucketing, SMALL).tank(SMALL)
      .luminosity(7, SMALL, ModifierIds.haste)
      .luminosity(15, SMALL, ModifierIds.lightspeed)
      .luminosity(10, SMALL, ModifierIds.fiery)
      .luminosity(2, SMALL, ModifierIds.unbreakable);
    tool(TinkerTools.sword).basic(SMALL,
        ModifierIds.diamond, ModifierIds.emerald, ModifierIds.netherite,
        ModifierIds.reinforced, ModifierIds.overforced, ModifierIds.magnetic, ModifierIds.soulbound,
        ModifierIds.experienced, ModifierIds.luck, TinkerModifiers.severing.getId(), ModifierIds.silky,
        ModifierIds.sharpness, ModifierIds.smite, ModifierIds.antiaquatic, ModifierIds.baneOfSssss, ModifierIds.cooling,
        ModifierIds.knockback, ModifierIds.necrotic,
        ModifierIds.blasting, ModifierIds.hydraulic
      ).fluid(ModifierIds.bucketing, SMALL).tank(SMALL)
      .luminosity(7, SMALL, ModifierIds.haste)
      .luminosity(15, SMALL, ModifierIds.lightspeed, ModifierIds.glowing)
      .luminosity(10, SMALL, ModifierIds.fiery)
      .luminosity(2, SMALL, ModifierIds.unbreakable);

    // broad
    tool(TinkerTools.sledgeHammer).basic('/',
        ModifierIds.diamond, ModifierIds.emerald, ModifierIds.netherite,
        ModifierIds.reinforced, ModifierIds.overforced, ModifierIds.magnetic,
        ModifierIds.experienced, ModifierIds.luck, ModifierIds.silky,
        ModifierIds.sharpness, ModifierIds.smite, ModifierIds.antiaquatic, ModifierIds.baneOfSssss, ModifierIds.cooling,
        ModifierIds.knockback,
        ModifierIds.blasting, ModifierIds.hydraulic
      ).basic(SMALL, ModifierIds.soulbound, TinkerModifiers.severing.getId(), ModifierIds.necrotic)
      .fluid(ModifierIds.bucketing, '/').tank('/')
      .luminosity(7, '/', ModifierIds.haste)
      .luminosity(15, '/', ModifierIds.lightspeed)
      .luminosity(15, SMALL, ModifierIds.glowing)
      .luminosity(10, '/', ModifierIds.fiery)
      .luminosity(2, '/', ModifierIds.unbreakable);
    tool(TinkerTools.veinHammer).basic('/',
        ModifierIds.diamond, ModifierIds.emerald, ModifierIds.netherite,
        ModifierIds.reinforced, ModifierIds.overforced, ModifierIds.magnetic,
        ModifierIds.experienced, ModifierIds.luck, ModifierIds.silky,
        ModifierIds.sharpness, ModifierIds.smite, ModifierIds.antiaquatic, ModifierIds.baneOfSssss, ModifierIds.cooling,
        ModifierIds.knockback,
        ModifierIds.blasting, ModifierIds.hydraulic
      ).fluid(ModifierIds.bucketing, '/').tank('/')
      .luminosity(7, '/', ModifierIds.haste)
      .luminosity(15, '/', ModifierIds.lightspeed)
      .luminosity(10, '/', ModifierIds.fiery)
      .luminosity(2, '/', ModifierIds.unbreakable);
    tool(TinkerTools.excavator).basic('/',
        ModifierIds.diamond, ModifierIds.emerald, ModifierIds.netherite,
        ModifierIds.reinforced, ModifierIds.overforced, ModifierIds.magnetic,
        ModifierIds.experienced, ModifierIds.luck, ModifierIds.silky,
        ModifierIds.sharpness, ModifierIds.smite, ModifierIds.antiaquatic, ModifierIds.baneOfSssss, ModifierIds.cooling,
        ModifierIds.knockback,
        ModifierIds.blasting, ModifierIds.hydraulic
      ).basic(SMALL, ModifierIds.soulbound, TinkerModifiers.severing.getId(), ModifierIds.necrotic)
      .fluid(ModifierIds.bucketing, '/').tank('/')
      .luminosity(7, '/', ModifierIds.haste)
      .luminosity(15, '/', ModifierIds.lightspeed)
      .luminosity(15, SMALL, ModifierIds.glowing)
      .luminosity(10, '/', ModifierIds.fiery)
      .luminosity(2, '/', ModifierIds.unbreakable);
    tool(TinkerTools.broadAxe).basic('/',
        ModifierIds.diamond, ModifierIds.emerald, ModifierIds.netherite,
        ModifierIds.reinforced, ModifierIds.overforced, ModifierIds.magnetic,
        ModifierIds.experienced, ModifierIds.luck, ModifierIds.silky,
        ModifierIds.sharpness, ModifierIds.smite, ModifierIds.antiaquatic, ModifierIds.baneOfSssss, ModifierIds.cooling,
        ModifierIds.knockback,
        ModifierIds.blasting, ModifierIds.hydraulic
      ).basic(SMALL, ModifierIds.soulbound, TinkerModifiers.severing.getId(), ModifierIds.necrotic)
      .fluid(ModifierIds.bucketing, '/').tank('/')
      .luminosity(7, '/', ModifierIds.haste)
      .luminosity(15, '/', ModifierIds.lightspeed)
      .luminosity(15, SMALL, ModifierIds.glowing)
      .luminosity(10, '/', ModifierIds.fiery)
      .luminosity(2, '/', ModifierIds.unbreakable);
    tool(TinkerTools.scythe).basic('/',
        ModifierIds.diamond, ModifierIds.emerald, ModifierIds.netherite,
        ModifierIds.reinforced, ModifierIds.overforced, ModifierIds.magnetic,
        ModifierIds.experienced, ModifierIds.luck, ModifierIds.silky,
        ModifierIds.sharpness, ModifierIds.smite, ModifierIds.antiaquatic, ModifierIds.baneOfSssss, ModifierIds.cooling,
        ModifierIds.knockback,
        ModifierIds.blasting, ModifierIds.hydraulic
      ).basic(SMALL, ModifierIds.soulbound, TinkerModifiers.severing.getId(), ModifierIds.necrotic)
      .fluid(ModifierIds.bucketing, '/').tank('/')
      .luminosity(7, '/', ModifierIds.haste)
      .luminosity(15, '/', ModifierIds.lightspeed)
      .luminosity(15, SMALL, ModifierIds.glowing)
      .luminosity(10, '/', ModifierIds.fiery)
      .luminosity(2, '/', ModifierIds.unbreakable);
    // weapon
    tool(TinkerTools.cleaver).basic('/',
        ModifierIds.diamond, ModifierIds.emerald, ModifierIds.netherite,
        ModifierIds.reinforced, ModifierIds.overforced, ModifierIds.magnetic,
        ModifierIds.experienced, ModifierIds.luck, ModifierIds.silky,
        ModifierIds.sharpness, ModifierIds.smite, ModifierIds.antiaquatic, ModifierIds.baneOfSssss, ModifierIds.cooling,
        ModifierIds.knockback,
        ModifierIds.blasting, ModifierIds.hydraulic
      ).basic(SMALL, ModifierIds.soulbound, TinkerModifiers.severing.getId(), ModifierIds.necrotic)
      .fluid(ModifierIds.bucketing, '/').tank('/')
      .luminosity(7, '/', ModifierIds.haste)
      .luminosity(15, '/', ModifierIds.lightspeed)
      .luminosity(15, SMALL, ModifierIds.glowing)
      .luminosity(10, '/', ModifierIds.fiery)
      .luminosity(2, '/', ModifierIds.unbreakable);
    tool(TinkerTools.javelin).basic('/',
        ModifierIds.diamond, ModifierIds.emerald, ModifierIds.netherite,
        ModifierIds.reinforced, ModifierIds.overforced, ModifierIds.magnetic,
        ModifierIds.experienced, ModifierIds.luck, ModifierIds.silky,
        ModifierIds.sharpness, ModifierIds.smite, ModifierIds.antiaquatic, ModifierIds.baneOfSssss, ModifierIds.cooling,
        ModifierIds.knockback,
        ModifierIds.blasting, ModifierIds.hydraulic
      ).fluid(ModifierIds.bucketing, '/').tank('/')
      .luminosity(7, '/', ModifierIds.haste)
      .luminosity(15, '/', ModifierIds.lightspeed)
      .luminosity(10, '/', ModifierIds.fiery)
      .luminosity(2, '/', ModifierIds.unbreakable);

    // ancient tools
    tool(TinkerTools.battlesign).basic(SMALL,
        ModifierIds.diamond, ModifierIds.emerald, ModifierIds.netherite,
        ModifierIds.reinforced, ModifierIds.overforced, ModifierIds.magnetic, ModifierIds.soulbound,
        ModifierIds.experienced, ModifierIds.luck, TinkerModifiers.severing.getId(), ModifierIds.silky,
        ModifierIds.sharpness, ModifierIds.smite, ModifierIds.antiaquatic, ModifierIds.baneOfSssss, ModifierIds.cooling,
        ModifierIds.knockback, ModifierIds.necrotic,
        ModifierIds.blasting, ModifierIds.hydraulic
      ).fluid(ModifierIds.bucketing, SMALL).tank(SMALL)
      .luminosity(7, SMALL, ModifierIds.haste)
      .luminosity(15, SMALL, ModifierIds.lightspeed, ModifierIds.glowing)
      .luminosity(10, SMALL, ModifierIds.fiery)
      .luminosity(2, SMALL, ModifierIds.unbreakable);

    // misc
    tool(TinkerTools.flintAndBrick).basic(SMALL,
      ModifierIds.diamond, ModifierIds.emerald, ModifierIds.netherite,
      ModifierIds.reinforced, ModifierIds.overforced
    ).luminosity(15, SMALL, ModifierIds.glowing)
      .luminosity(2, SMALL, ModifierIds.unbreakable);

    // plate armor
    ModifierId dyed = TinkerModifiers.dyed.getId();
    for (ArmorType type : ModifiableArmorMaterial.ARMOR_TYPES) {
      String root = "armor/plate/" + type.getName() + "/maille";
      String item = "plate/" + type.getName();
      tool(item).modifier(dyed, new MaterialHasFallbackModifierModel(1,
        new DyedModifierModel(toolMaterial(root + "_metal"), null),
        new DyedModifierModel(toolMaterial(root), null),
        "metal"
      )).trim(type);
      tool(item + "_broken").modifier(dyed, new MaterialHasFallbackModifierModel(1,
        new DyedModifierModel(toolMaterial(root + "_broken_metal"), null),
        new DyedModifierModel(toolMaterial(root + "_broken"), null),
        "metal"
      ));
    }
    // we include both folders, but limited for small
    tool("plate/shield").banner("armor/plate/shield/banner_small/", "armor/plate/shield/banner_large/");

    // travelers
    travelersWhiteWool("goggles", null);
    travelers("vest", ArmorType.CHESTPLATE);
    travelers("pants", ArmorType.LEGGINGS);
    travelers("boots", ArmorType.BOOTS);
    travelersWhiteWool("shield", null);
    // official gives the travelers shield banner patterns too, from its own folder of 41 pattern sprites
    tool("travelers/shield").banner("armor/travelers/shield/banner/", null);
    tool("travelers/goggles").customTrim("armor/travelers/goggles", null);

    // slimesuit
    for (ArmorType type : ModifiableArmorMaterial.ARMOR_TYPES) {
      tool("slime/" + type.getName()).trim(type);
    }
    tool("slime/wings")
      .modifier(TinkerModifiers.trim.getId(), new TrimModifierModel.Custom(toolMaterial("armor/slime/wings/trim").sprite(), null))
      .modifier(dyed, new DyedModifierModel(toolMaterial("armor/slime/wings/slime"), null));
    tool("slime/wings_broken").modifier(dyed, new DyedModifierModel(toolMaterial("armor/slime/wings/slime_broken"), null));
    tool("slime/helmet")
      .constant("__skull", new SlimeskullModifierModel(toolMaterial("armor/slime/helmet/skull"), 0, 1))
      .luminosity(10, "armor/modifiers/helmet", null, ModifierIds.fiery);
    tool("slime/leggings").modifier(dyed, new DyedModifierModel(toolMaterial("armor/slime/leggings/shell"), null));
    tool("slime/leggings_broken").modifier(dyed, new DyedModifierModel(toolMaterial("armor/slime/leggings/shell_broken"), null));
    tool("slime/boots").modifier(dyed, new DyedModifierModel(toolMaterial("armor/slime/boots/laces"), null));
    String ribcage = "armor/slime/chestplate/ribcage";
    tool("slime/chestplate").modifier(dyed, new MaterialHasFallbackModifierModel(0,
      new DyedModifierModel(toolMaterial(ribcage + "_bone"), null),
      new DyedModifierModel(toolMaterial(ribcage), null),
      "bone"
    ));
    tool("slime/chestplate_broken").modifier(dyed, new MaterialHasFallbackModifierModel(0,
      new DyedModifierModel(toolMaterial(ribcage + "_broken_bone"), null),
      new DyedModifierModel(toolMaterial(ribcage + "_broken"), null),
      "bone"
    ));

    // ammo
    tool(TinkerTools.arrow).tipped("ammo/arrow_modifiers/tipped").smashing("ammo/arrow_modifiers/smashing")
      .modifier(dyed, new DyedModifierModel(toolMaterial("ammo/arrow_modifiers/dyed"), null));
    tool(TinkerTools.shuriken).tipped("ammo/shuriken_modifiers/tipped").smashing("ammo/shuriken_modifiers/smashing");
    tool(TinkerTools.throwingAxe).tipped("ammo/axe_modifiers/tipped").smashing("ammo/axe_modifiers/smashing");
    // fishing rods just have tipped
    tool(TinkerTools.fishingRod).tipped("fishing_rod/modifiers/tipped").fluid().compact(ModifierIds.tank);
    tool(TinkerTools.fishingRod, "/broken").constant("tipped", ModifierModel.EMPTY);
    tool(TinkerTools.fishingRod, "/cast").constant("tipped", ModifierModel.EMPTY);

    // tanks
    tool(TinkerTools.meltingPan).fluid();
    tool(TinkerTools.swasher).fluid();

    // staffs
    tool("staff").basic('_',
      ModifierIds.diamond, ModifierIds.emerald, ModifierIds.netherite,
      ModifierIds.firestarter,
      ModifierIds.overforced, ModifierIds.reinforced
    ).tank('_').embellishment('_').fluid(ModifierIds.bucketing, '_')
      .luminosity(2, '_', ModifierIds.unbreakable);
    staffDyed("earth");
    staffDyed("sky");
    staffDyed("ichor");
    staffDyed("ender");

    // Overlays official Tinkers' Construct 3.12.1 ships for bows, fishing rods and armor, which the 26.1 maps had lost.
    // The block between the markers is written by tools/assets-myrlin3/gen_modifier_maps.py from the official sprite maps.
    // BEGIN official overlays (generated by gen_modifier_maps.py)
    tool("armor/boots")
      .modifier(mod("depth_strider"), overlay("armor/modifiers/boots/tconstruct_depth_strider", null, 0))
      .modifier(mod("double_jump"), overlay("armor/modifiers/boots/tconstruct_double_jump", null, 0))
      .modifier(mod("emerald"), overlay("armor/modifiers/boots/tconstruct_emerald", null, 0))
      .modifier(mod("feather_fall"), overlay("armor/modifiers/boots/tconstruct_feather_falling", null, 0))
      .modifier(mod("feather_falling"), overlay("armor/modifiers/boots/tconstruct_feather_falling", null, 0))
      .modifier(mod("fiery"), overlay("armor/modifiers/boots/tconstruct_fiery", null, 10))
      .modifier(mod("lightspeed"), overlay("armor/modifiers/boots/tconstruct_lightspeed", null, 15))
      .modifier(mod("long_fall"), overlay("armor/modifiers/boots/tconstruct_long_fall", null, 0))
      .modifier(mod("soulspeed"), overlay("armor/modifiers/boots/tconstruct_soulspeed", null, 0))
      .modifier(mod("thorns"), overlay("armor/modifiers/boots/tconstruct_thorns", null, 0));
    tool("armor/chestplate")
      .modifier(mod("emerald"), overlay("armor/modifiers/chestplate/tconstruct_emerald", null, 0))
      .modifier(mod("fiery"), overlay("armor/modifiers/chestplate/tconstruct_fiery", null, 10))
      .modifier(mod("haste"), overlay("armor/modifiers/chestplate/tconstruct_haste", null, 7))
      .modifier(mod("knockback"), overlay("armor/modifiers/chestplate/tconstruct_knockback", null, 0))
      .modifier(mod("reach"), overlay("armor/modifiers/chestplate/tconstruct_reach", null, 0))
      .modifier(mod("sleeves"), overlay("armor/modifiers/chestplate/tconstruct_sleeves", null, 0))
      .modifier(mod("strength"), overlay("armor/modifiers/chestplate/tconstruct_strength", null, 0))
      .modifier(mod("thorns"), overlay("armor/modifiers/chestplate/tconstruct_thorns", null, 0));
    tool("armor/helmet")
      .modifier(mod("aqua_affinity"), overlay("armor/modifiers/helmet/tconstruct_aqua_affinity", null, 0))
      .modifier(mod("emerald"), overlay("armor/modifiers/helmet/tconstruct_emerald", null, 0))
      .modifier(mod("fiery"), overlay("armor/modifiers/helmet/tconstruct_fiery", null, 10))
      .modifier(mod("thorns"), overlay("armor/modifiers/helmet/tconstruct_thorns", null, 0));
    tool("armor/leggings")
      .modifier(mod("emerald"), overlay("armor/modifiers/leggings/tconstruct_emerald", null, 0))
      .modifier(mod("fiery"), overlay("armor/modifiers/leggings/tconstruct_fiery", null, 10))
      .modifier(mod("leaping"), overlay("armor/modifiers/leggings/tconstruct_leaping", null, 0))
      .modifier(mod("luck"), overlay("armor/modifiers/leggings/tconstruct_luck", null, 0))
      .modifier(mod("shield_strap"), overlay("armor/modifiers/leggings/tconstruct_shield_strap", null, 0))
      .modifier(mod("speedy"), overlay("armor/modifiers/leggings/tconstruct_speedy", null, 0))
      .modifier(mod("step_up"), overlay("armor/modifiers/leggings/tconstruct_step_up", null, 0))
      .modifier(mod("swift_sneak"), overlay("armor/modifiers/leggings/tconstruct_swift_sneak", null, 0))
      .modifier(mod("thorns"), overlay("armor/modifiers/leggings/tconstruct_thorns", null, 0));
    tool("crossbow")
      .modifier(mod("diamond"), overlay("crossbow/modifiers/tconstruct_diamond", null, 0))
      .modifier(mod("emerald"), overlay("longbow/modifiers/tconstruct_emerald", null, 0))
      .modifier(mod("experienced"), overlay("crossbow/modifiers/tconstruct_experienced", null, 0))
      .modifier(mod("fiery"), overlay("crossbow/modifiers/tconstruct_fiery", null, 10))
      .modifier(mod("freezing"), overlay("crossbow/modifiers/tconstruct_freezing", null, 0))
      .modifier(mod("impaling"), overlay("crossbow/modifiers/tconstruct_impaling", null, 0))
      .modifier(mod("netherite"), overlay("crossbow/modifiers/tconstruct_netherite", null, 0))
      .modifier(mod("overforced"), overlay("crossbow/modifiers/tconstruct_overforced", null, 0))
      .modifier(mod("pierce"), overlay("crossbow/modifiers/tconstruct_pierce", null, 0))
      .modifier(mod("quick_charge"), overlay("crossbow/modifiers/tconstruct_quick_charge", null, 7))
      .modifier(mod("reinforced"), overlay("crossbow/modifiers/tconstruct_reinforced", null, 0))
      .modifier(mod("trueshot"), overlay("crossbow/modifiers/tconstruct_trueshot", null, 0))
      .modifier(mod("unbreakable"), overlay("crossbow/modifiers/tconstruct_unbreakable", null, 2));
    tool("crossbow/1")
      .modifier(mod("quick_charge"), overlay("crossbow/modifiers/tconstruct_quick_charge_1", null, 0));
    tool("crossbow/2")
      .modifier(mod("quick_charge"), overlay("crossbow/modifiers/tconstruct_quick_charge_2", null, 0));
    tool("crossbow/3")
      .modifier(mod("quick_charge"), overlay("crossbow/modifiers/tconstruct_quick_charge_3", null, 0));
    tool("crossbow/broken")
      .modifier(mod("quick_charge"), overlay("crossbow/modifiers/tconstruct_quick_charge_broken", null, 0));
    tool("fishing_rod")
      .modifier(mod("bounce"), overlay("fishing_rod/modifiers/tconstruct_bounce", null, 0))
      .modifier(mod("channeling"), overlay("fishing_rod/modifiers/tconstruct_channeling", null, 0))
      .modifier(mod("collecting"), overlay("fishing_rod/modifiers/tconstruct_collecting", null, 0))
      .modifier(mod("diamond"), overlay("fishing_rod/modifiers/tconstruct_diamond", null, 0))
      .modifier(mod("emerald"), overlay("fishing_rod/modifiers/tconstruct_emerald", null, 0))
      .modifier(mod("experienced"), overlay("fishing_rod/modifiers/tconstruct_experienced", null, 0))
      .modifier(mod("fiery"), overlay("fishing_rod/modifiers/tconstruct_fiery", null, 10))
      .modifier(mod("grapple"), overlay("fishing_rod/modifiers/tconstruct_grapple", null, 0))
      .modifier(mod("lure"), overlay("fishing_rod/modifiers/tconstruct_lure", null, 0))
      .modifier(mod("netherite"), overlay("fishing_rod/modifiers/tconstruct_netherite", null, 0))
      .modifier(mod("overforced"), overlay("fishing_rod/modifiers/tconstruct_overforced", null, 0))
      .modifier(mod("punch"), overlay("fishing_rod/modifiers/tconstruct_punch", null, 0))
      .modifier(mod("quick_charge"), overlay("fishing_rod/modifiers/tconstruct_quick_charge", null, 7))
      .modifier(mod("reinforced"), overlay("fishing_rod/modifiers/tconstruct_reinforced", null, 0))
      .modifier(mod("trueshot"), overlay("fishing_rod/modifiers/tconstruct_trueshot", null, 0))
      .modifier(mod("unbreakable"), overlay("fishing_rod/modifiers/tconstruct_unbreakable", null, 2));
    tool("fishing_rod/broken")
      .modifier(mod("emerald"), overlay("fishing_rod/modifiers/tconstruct_emerald_broken", null, 0))
      .modifier(mod("netherite"), overlay("fishing_rod/modifiers/tconstruct_netherite_broken", null, 0))
      .modifier(mod("overforced"), overlay("fishing_rod/modifiers/tconstruct_overforced_broken", null, 0))
      .modifier(mod("reinforced"), overlay("fishing_rod/modifiers/tconstruct_reinforced_broken", null, 0));
    tool("fishing_rod/cast")
      .modifier(mod("bounce"), ModifierModel.EMPTY)
      .modifier(mod("collecting"), ModifierModel.EMPTY)
      .modifier(mod("fiery"), ModifierModel.EMPTY)
      .modifier(mod("freezing"), ModifierModel.EMPTY)
      .modifier(mod("grapple"), ModifierModel.EMPTY)
      .modifier(mod("lure"), ModifierModel.EMPTY)
      .modifier(mod("netherite"), overlay("fishing_rod/modifiers/tconstruct_netherite_broken", null, 0))
      .modifier(mod("quick_charge"), ModifierModel.EMPTY)
      .modifier(mod("trueshot"), ModifierModel.EMPTY)
      .modifier(mod("unbreakable"), ModifierModel.EMPTY);
    tool("longbow")
      .modifier(mod("bounce"), overlay("longbow/modifiers/tconstruct_bounce", "longbow/large/modifiers/tconstruct_bounce", 0))
      .modifier(mod("diamond"), overlay("longbow/modifiers/tconstruct_diamond", "longbow/large/modifiers/tconstruct_diamond", 0))
      .modifier(mod("emerald"), overlay("longbow/modifiers/tconstruct_emerald", "longbow/large/modifiers/tconstruct_emerald", 0))
      .modifier(mod("experienced"), overlay("longbow/modifiers/tconstruct_experienced", "longbow/large/modifiers/tconstruct_experienced", 0))
      .modifier(mod("fiery"), overlay("longbow/modifiers/tconstruct_fiery", "longbow/large/modifiers/tconstruct_fiery", 10))
      .modifier(mod("freezing"), overlay("longbow/modifiers/tconstruct_freezing", "longbow/large/modifiers/tconstruct_freezing", 0))
      .modifier(mod("netherite"), overlay("longbow/modifiers/tconstruct_netherite", "longbow/large/modifiers/tconstruct_netherite", 0))
      .modifier(mod("overforced"), overlay("longbow/modifiers/tconstruct_overforced", "longbow/large/modifiers/tconstruct_overforced", 0))
      .modifier(mod("power"), overlay("longbow/modifiers/tconstruct_power", "longbow/large/modifiers/tconstruct_power", 0))
      .modifier(mod("reinforced"), overlay("longbow/modifiers/tconstruct_reinforced", "longbow/large/modifiers/tconstruct_reinforced", 0))
      .modifier(mod("trueshot"), overlay("longbow/modifiers/tconstruct_trueshot", "longbow/large/modifiers/tconstruct_trueshot", 0))
      .modifier(mod("unbreakable"), overlay("longbow/modifiers/tconstruct_unbreakable", "longbow/large/modifiers/tconstruct_unbreakable", 2));
    tool("longbow/1")
      .modifier(mod("diamond"), overlay("longbow/modifiers/tconstruct_diamond_1", "longbow/large/modifiers/tconstruct_diamond_1", 0))
      .modifier(mod("experienced"), overlay("longbow/modifiers/tconstruct_experienced_2", "longbow/large/modifiers/tconstruct_experienced", 0))
      .modifier(mod("fiery"), overlay("longbow/modifiers/tconstruct_fiery_1", "longbow/large/modifiers/tconstruct_fiery", 10))
      .modifier(mod("freezing"), overlay("longbow/modifiers/tconstruct_freezing", "longbow/large/modifiers/tconstruct_freezing_1", 0))
      .modifier(mod("netherite"), overlay("longbow/modifiers/tconstruct_netherite_1", "longbow/large/modifiers/tconstruct_netherite_1", 0))
      .modifier(mod("overforced"), overlay("longbow/modifiers/tconstruct_overforced_1", "longbow/large/modifiers/tconstruct_overforced_1", 0))
      .modifier(mod("reinforced"), overlay("longbow/modifiers/tconstruct_reinforced_1", "longbow/large/modifiers/tconstruct_reinforced_1", 0))
      .modifier(mod("unbreakable"), overlay("longbow/modifiers/tconstruct_unbreakable_1", "longbow/large/modifiers/tconstruct_unbreakable_1", 2));
    tool("longbow/2")
      .modifier(mod("diamond"), overlay("longbow/modifiers/tconstruct_diamond_2", "longbow/large/modifiers/tconstruct_diamond_2", 0))
      .modifier(mod("experienced"), overlay("longbow/modifiers/tconstruct_experienced_2", "longbow/large/modifiers/tconstruct_experienced_2", 0))
      .modifier(mod("fiery"), overlay("longbow/modifiers/tconstruct_fiery_2", "longbow/large/modifiers/tconstruct_fiery_2", 10))
      .modifier(mod("freezing"), overlay("longbow/modifiers/tconstruct_freezing_2", "longbow/large/modifiers/tconstruct_freezing_2", 0))
      .modifier(mod("netherite"), overlay("longbow/modifiers/tconstruct_netherite_2", "longbow/large/modifiers/tconstruct_netherite_2", 0))
      .modifier(mod("overforced"), overlay("longbow/modifiers/tconstruct_overforced_2", "longbow/large/modifiers/tconstruct_overforced_2", 0))
      .modifier(mod("reinforced"), overlay("longbow/modifiers/tconstruct_reinforced_2", "longbow/large/modifiers/tconstruct_reinforced_2", 0))
      .modifier(mod("unbreakable"), overlay("longbow/modifiers/tconstruct_unbreakable_2", "longbow/large/modifiers/tconstruct_unbreakable_2", 2));
    tool("longbow/3")
      .modifier(mod("diamond"), overlay("longbow/modifiers/tconstruct_diamond_3", "longbow/large/modifiers/tconstruct_diamond_3", 0))
      .modifier(mod("experienced"), overlay("longbow/modifiers/tconstruct_experienced_3", "longbow/large/modifiers/tconstruct_experienced_3", 0))
      .modifier(mod("fiery"), overlay("longbow/modifiers/tconstruct_fiery_3", "longbow/large/modifiers/tconstruct_fiery_3", 10))
      .modifier(mod("freezing"), overlay("longbow/modifiers/tconstruct_freezing_3", "longbow/large/modifiers/tconstruct_freezing_3", 0))
      .modifier(mod("netherite"), overlay("longbow/modifiers/tconstruct_netherite_3", "longbow/large/modifiers/tconstruct_netherite_3", 0))
      .modifier(mod("overforced"), overlay("longbow/modifiers/tconstruct_overforced_3", "longbow/large/modifiers/tconstruct_overforced_3", 0))
      .modifier(mod("reinforced"), overlay("longbow/modifiers/tconstruct_reinforced_3", "longbow/large/modifiers/tconstruct_reinforced_3", 0))
      .modifier(mod("unbreakable"), overlay("longbow/modifiers/tconstruct_unbreakable_3", "longbow/large/modifiers/tconstruct_unbreakable_3", 2));
    tool("longbow/broken")
      .modifier(mod("overforced"), overlay("longbow/modifiers/tconstruct_overforced_broken", "longbow/large/modifiers/tconstruct_overforced_broken", 0))
      .modifier(mod("reinforced"), overlay("longbow/modifiers/tconstruct_reinforced_broken", "longbow/large/modifiers/tconstruct_reinforced_broken", 0));
    tool("plate/boots")
      .modifier(mod("diamond"), overlay("armor/plate/boots/modifiers/tconstruct_diamond", null, 0))
      .modifier(mod("freezing"), overlay("armor/plate/boots/modifiers/tconstruct_freezing", null, 0))
      .modifier(mod("netherite"), overlay("armor/plate/boots/modifiers/tconstruct_netherite", null, 0))
      .modifier(mod("ricochet"), overlay("armor/plate/boots/modifiers/tconstruct_ricochet", null, 0))
      .modifier(mod("springy"), overlay("armor/plate/boots/modifiers/tconstruct_springy", null, 0));
    tool("plate/chestplate")
      .modifier(mod("diamond"), overlay("armor/plate/chestplate/modifiers/tconstruct_diamond", null, 0))
      .modifier(mod("freezing"), overlay("armor/plate/chestplate/modifiers/tconstruct_freezing", null, 0))
      .modifier(mod("netherite"), overlay("armor/plate/chestplate/modifiers/tconstruct_netherite", null, 0))
      .modifier(mod("ricochet"), overlay("armor/plate/chestplate/modifiers/tconstruct_ricochet", null, 0))
      .modifier(mod("springy"), overlay("armor/plate/chestplate/modifiers/tconstruct_springy", null, 0));
    tool("plate/helmet")
      .modifier(mod("diamond"), overlay("armor/plate/helmet/modifiers/tconstruct_diamond", null, 0))
      .modifier(mod("freezing"), overlay("armor/plate/helmet/modifiers/tconstruct_freezing", null, 0))
      .modifier(mod("item_frame"), overlay("armor/plate/helmet/modifiers/tconstruct_item_frame", null, 0))
      .modifier(mod("netherite"), overlay("armor/plate/helmet/modifiers/tconstruct_netherite", null, 0))
      .modifier(mod("respiration"), overlay("armor/plate/helmet/modifiers/tconstruct_respiration", null, 0))
      .modifier(mod("ricochet"), overlay("armor/plate/helmet/modifiers/tconstruct_ricochet", null, 0))
      .modifier(mod("springy"), overlay("armor/plate/helmet/modifiers/tconstruct_springy", null, 0));
    tool("plate/leggings")
      .modifier(mod("diamond"), overlay("armor/plate/leggings/modifiers/tconstruct_diamond", null, 0))
      .modifier(mod("freezing"), overlay("armor/plate/leggings/modifiers/tconstruct_freezing", null, 0))
      .modifier(mod("netherite"), overlay("armor/plate/leggings/modifiers/tconstruct_netherite", null, 0))
      .modifier(mod("ricochet"), overlay("armor/plate/leggings/modifiers/tconstruct_ricochet", null, 0))
      .modifier(mod("springy"), overlay("armor/plate/leggings/modifiers/tconstruct_springy", null, 0));
    tool("slime/chestplate")
      .modifier(mod("emerald"), ModifierModel.EMPTY);
    tool("travelers/pants")
      .modifier(mod("swift_sneak"), overlay("armor/travelers/pants/modifiers/tconstruct_swift_sneak", null, 0));
    // END official overlays

    mossyOverlays();
  }

  /** Creates a modifier id in the mod namespace. */
  private static ModifierId mod(String path) {
    return new ModifierId(TConstruct.MOD_ID, path);
  }

  /** Creates a plain overlay model. Textures are paths under item/tool/, large is null for small only tools. */
  private NormalModifierModel overlay(String small, @Nullable String large, int light) {
    return new NormalModifierModel(toolMaterial(small), large != null ? toolMaterial(large) : null, -1, light);
  }

  @Override
  public String getName() {
    return "Continuum Construct Modifier Model Map Provider";
  }

  /**
   * Adds dyed textures for travelers gear. As in official Tinkers 3.12.1, a cuirass made of a wool material
   * (render info fallback "wool") dyes its own wool texture instead of the generic dyed one: boots, pants and
   * vest have a dyed_wool sprite, goggles and shield dye the white wool cuirass sprite.
   */
  private void travelers(String name, @Nullable ArmorType type, String woolDyed, String woolDyedBroken) {
    String root = "armor/travelers/" + name + "/modifiers/";
    String woolRoot = "armor/travelers/" + name + "/";
    ModifierId dyed = TinkerModifiers.dyed.getId();
    String item = "travelers/" + name;
    Builder b = tool(item).modifier(dyed, new MaterialHasFallbackModifierModel(1,
      new DyedModifierModel(toolMaterial(woolRoot + woolDyed), null),
      new DyedModifierModel(toolMaterial(root + "dyed"), null),
      "wool"
    ));
    if (type != null) {
      b.trim(type);
    }
    tool(item + "_broken").modifier(dyed, new MaterialHasFallbackModifierModel(1,
      new DyedModifierModel(toolMaterial(woolRoot + woolDyedBroken), null),
      new DyedModifierModel(toolMaterial(root + "dyed_broken"), null),
      "wool"
    ));
  }

  /** Adds dyed textures for travelers gear whose wool variant is the dyed_wool sprite */
  private void travelers(String name, @Nullable ArmorType type) {
    travelers(name, type, "dyed_wool", "dyed_wool_broken");
  }

  /** Adds dyed textures for travelers gear whose wool variant is the white wool cuirass sprite */
  private void travelersWhiteWool(String name, @Nullable ArmorType type) {
    travelers(name, type, "cuirass_tconstruct_wool_white", "cuirass_broken_tconstruct_wool_white");
  }

  /** Adds dyed textures to a staff */
  private void staffDyed(String name) {
    String staff = "staff/" + name;
    String small = "staff/modifiers/" + name + "/dyed";
    String large = "staff/large_modifiers/" + name + "/dyed";
    ModifierId dyed = TinkerModifiers.dyed.getId();
    tool(staff).modifier(dyed, new DyedModifierModel(toolMaterial(small), toolMaterial(large)));
    tool(staff + "/broken").modifier(dyed, new DyedModifierModel(toolMaterial(small + "_broken"), toolMaterial(large + "_broken")));
    for (int i = 1; i <= 5; i++) {
      String variant = Integer.toString(i);
      tool(staff + '/' + variant).modifier(dyed, new DyedModifierModel(toolMaterial(small + '_' + variant), toolMaterial(large + '_' + variant)));
    }
  }

  /** Adds animated mossy overlays to durable tools. */
  private void mossyOverlays() {
    mossy(TinkerTools.pickaxe.getId(), "pickaxe", SMALL);
    mossy(TinkerTools.pickadze.getId(), "pickadze", SMALL);
    mossy(TinkerTools.mattock.getId(), "mattock", SMALL);
    mossy(TinkerTools.handAxe.getId(), "hand_axe", SMALL);
    mossy(TinkerTools.kama.getId(), "kama", SMALL);
    mossy(TinkerTools.dagger.getId(), "dagger", SMALL);
    mossy(TinkerTools.sword.getId(), "sword", SMALL);
    mossy(TinkerTools.sledgeHammer.getId(), "sledge_hammer", '/');
    mossy(TinkerTools.veinHammer.getId(), "vein_hammer", '/');
    mossy(TinkerTools.excavator.getId(), "excavator", '/');
    mossy(TinkerTools.broadAxe.getId(), "broad_axe", '/');
    mossy(TinkerTools.scythe.getId(), "scythe", '/');
    mossy(TinkerTools.cleaver.getId(), "cleaver", '/');
    mossy(TinkerTools.javelin.getId(), "javelin", '/');
    mossy(TinkerTools.battlesign.getId(), "battlesign", SMALL);
    mossy(TinkerTools.flintAndBrick.getId(), "flint_and_brick", SMALL);
    mossy(TinkerTools.crossbow.getId(), "crossbow", SMALL);
    mossy(TinkerTools.longbow.getId(), "longbow", '/');
    mossy(TinkerTools.fishingRod.getId(), "fishing_rod", SMALL);
    mossy(TinkerTools.meltingPan.getId(), "melting_pan", SMALL);
    mossy(TinkerTools.swasher.getId(), "swasher", SMALL);
    mossy(Identifier.fromNamespaceAndPath(TConstruct.MOD_ID, "staff"), "staff", '_');
    mossy(Identifier.fromNamespaceAndPath(TConstruct.MOD_ID, "staff/earth"), "staff/earth", '_');
    mossy(Identifier.fromNamespaceAndPath(TConstruct.MOD_ID, "staff/sky"), "staff/sky", '_');
    mossy(Identifier.fromNamespaceAndPath(TConstruct.MOD_ID, "staff/ichor"), "staff/ichor", '_');
    mossy(Identifier.fromNamespaceAndPath(TConstruct.MOD_ID, "staff/ender"), "staff/ender", '_');
  }

  /** Adds a mossy overlay to a single tool model map. */
  private void mossy(Identifier tool, String textureRoot, char largeSeparator) {
    String modifier = suffix(ModifierIds.mossy);
    String small = textureRoot + "/modifiers/" + modifier;
    String large = largeSeparator == SMALL ? null : textureRoot + "/large" + largeSeparator + "modifiers/" + modifier;
    tool(tool).modifier(ModifierIds.mossy, new MossyModifierModel(
      toolMaterial(small + "_1"),
      toolMaterial(small + "_2"),
      toolMaterial(small + "_3"),
      large != null ? toolMaterial(large + "_1") : null,
      large != null ? toolMaterial(large + "_2") : null,
      large != null ? toolMaterial(large + "_3") : null
    ));
  }
}
