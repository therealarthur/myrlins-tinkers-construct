package slimeknights.tconstruct.tools.data.material;

import net.minecraft.data.PackOutput;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.DyeColor;
import slimeknights.tconstruct.library.client.data.material.AbstractMaterialRenderInfoProvider;
import slimeknights.tconstruct.library.client.data.material.AbstractMaterialSpriteProvider;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.shared.block.SlimeType;

public class MaterialRenderInfoProvider extends AbstractMaterialRenderInfoProvider {
  public MaterialRenderInfoProvider(PackOutput packOutput, AbstractMaterialSpriteProvider spriteProvider, ResourceManager resourceManager) {
    super(packOutput, spriteProvider, resourceManager);
  }

  @Override
  protected void addMaterialRenderInfo() {
    // fallback
    buildRenderInfo(IMaterial.UNKNOWN_ID);

    // tier 1
    buildRenderInfo(MaterialIds.flint).color(0x3D3C3C).fallbacks("crystal", "rock", "stick");
    buildRenderInfo(MaterialIds.bone).color(0xE8E5D2).fallbacks("bone", "rock");
    buildRenderInfo(MaterialIds.chorus);
    buildRenderInfo(MaterialIds.string).color(0xFFFFFF);
    buildRenderInfo(MaterialIds.leather).color(0xC65C35);
    buildRenderInfo(MaterialIds.vine).color(0x48B518).fallbacks("vine");
    buildRenderInfo(MaterialIds.ice).color(0x74ABFE);
    buildRenderInfo(MaterialIds.cactus).color(0x649832);
    // tier 1 - ammo
    buildRenderInfo(MaterialIds.paper);
    redirect(MaterialIds.leaves, MaterialIds.vine);
    // tier 1 - wood
    buildRenderInfo(MaterialIds.wood).color(0x876627).fallbacks("wood", "stick", "primitive");
    buildRenderInfo(MaterialIds.crimson);
    buildRenderInfo(MaterialIds.warped);
    buildRenderInfo(MaterialIds.bamboo);
    // tier 1 - stone
    redirect(MaterialIds.rock, MaterialIds.stone);
    buildRenderInfo(MaterialIds.stone).color(0xB1AFAD);
    buildRenderInfo(MaterialIds.diorite);
    buildRenderInfo(MaterialIds.granite);
    buildRenderInfo(MaterialIds.blackstone);
    buildRenderInfo(MaterialIds.basalt);
    redirect(MaterialIds.andesite, MaterialIds.stone);
    redirect(MaterialIds.calcite, MaterialIds.diorite);
    redirect(MaterialIds.deepslate, MaterialIds.basalt);
    // tier 1 - wool
    MaterialVariantId whiteWool = MaterialVariantId.create(MaterialIds.wool, DyeColor.WHITE.getName());
    redirect(MaterialIds.wool, whiteWool);
    for (DyeColor color : DyeColor.values()) {
      buildRenderInfo(MaterialVariantId.create(MaterialIds.wool, color.getName()));
    }
    redirect(MaterialIds.feather, whiteWool);

    // tier 2
    buildRenderInfo(MaterialIds.iron).color(0xD8D8D8).fallbacks("metal");
    buildRenderInfo(MaterialIds.oxidizedIron).color(0xE9C8B1).fallbacks("metal");
    buildRenderInfo(MaterialIds.copper).color(0xE77C56).fallbacks("metal");
    buildRenderInfo(MaterialIds.oxidizedCopper).color(0x4FAB90).fallbacks("metal");
    buildRenderInfo(MaterialIds.searedStone).color(0x4F4A47).fallbacks("rock");
    buildRenderInfo(MaterialIds.scorchedStone).color(0x5B4C43).fallbacks("rock");
    buildRenderInfo(MaterialIds.venombone).color(0xA2935E).fallbacks("bone", "rock");
    buildRenderInfo(MaterialIds.necroticBone).color(0x2A2A2A).fallbacks("bone", "rock");
    buildRenderInfo(MaterialIds.endstone);
    redirect(MaterialIds.whitestone, MaterialIds.endstone);
    buildRenderInfo(MaterialIds.skyslimeVine).color(0x00F4DA).fallbacks("vine");
    buildRenderInfo(MaterialIds.weepingVine);
    buildRenderInfo(MaterialIds.twistingVine);
    // slimewood
    redirect(MaterialIds.slimewood, MaterialIds.greenheart);
    buildRenderInfo(MaterialIds.greenheart);
    buildRenderInfo(MaterialIds.skyroot);
    buildRenderInfo(MaterialIds.bloodshroom);
    buildRenderInfo(MaterialIds.enderbark);
    buildRenderInfo(MaterialIds.slimeskin);
    buildRenderInfo(MaterialIds.skyslimeskin);
    buildRenderInfo(MaterialIds.enderslimeskin);
    buildRenderInfo(MaterialIds.venom);
    // slimeball
    redirect(MaterialIds.slimeball, MaterialIds.earthslime);
    redirect(MaterialVariantId.create(MaterialIds.slimeball, "sky"),   MaterialIds.skyslime);
    redirect(MaterialVariantId.create(MaterialIds.slimeball, "ichor"), MaterialIds.ichor);
    redirect(MaterialVariantId.create(MaterialIds.slimeball, "ender"), MaterialIds.enderslime);

    // tier 3
    buildRenderInfo(MaterialIds.slimesteel).color(0x46ECE7).fallbacks("slime_metal", "metal");
    // default texture is tin even though silicon is the one we provide, as it makes the names cleaner
    buildRenderInfo(MaterialIds.amethystBronze).color(0xD9A2D0).fallbacks("metal");
    buildRenderInfo(MaterialIds.nahuatl).color(0x3B2754).fallbacks("wood", "stick");
    buildRenderInfo(MaterialIds.pigIron).color(0xF0A8A4).fallbacks("metal");
    buildRenderInfo(MaterialIds.roseGold).color(0xF7CDBB).fallbacks("metal");
    buildRenderInfo(MaterialIds.cobalt).color(0x2376dd).fallbacks("metal");
    buildRenderInfo(MaterialIds.steel).color(0x959595).fallbacks("metal");
    buildRenderInfo(MaterialIds.darkthread);
    buildRenderInfo(MaterialIds.ichorskin);
    buildRenderInfo(MaterialIds.jadeite);

    // tier 4
    buildRenderInfo(MaterialIds.cinderslime).color(0xE82323).luminosity(SlimeType.ICHOR.getLightLevel());
    buildRenderInfo(MaterialIds.queensSlime).color(0x809912).fallbacks("slime_metal", "metal").luminosity(9);
    buildRenderInfo(MaterialIds.hepatizon).color(0x60496b).fallbacks("metal");
    buildRenderInfo(MaterialIds.manyullyn).color(0x9261cc).fallbacks("metal");
    buildRenderInfo(MaterialIds.knightmetal).color(0xC4D6AE).fallbacks("metal");
    buildRenderInfo(MaterialIds.knightslime);
    buildRenderInfo(MaterialIds.blazingBone).color(0xF2D500).fallbacks("bone", "rock").luminosity(15);
    buildRenderInfo(MaterialIds.blazewood).fallbacks("wood", "stick").luminosity(7);
    buildRenderInfo(MaterialIds.jeweledHide);
    buildRenderInfo(MaterialIds.ancientHide);
    buildRenderInfo(MaterialIds.ancient);
    buildRenderInfo(MaterialIds.enderslimeVine).color(0xa92dff).fallbacks("vine");

    // tier 2 compat
    buildRenderInfo(MaterialIds.osmium).color(0xC1E6F4).fallbacks("metal");
    buildRenderInfo(MaterialIds.ironwood);
    buildRenderInfo(MaterialIds.silver).color(0xDAF3ED).fallbacks("metal");
    buildRenderInfo(MaterialIds.lead).color(0x696579).fallbacks("metal");
    buildRenderInfo(MaterialIds.whitestoneComposite, MaterialIds.whitestone.getLocation('_')).color(0xE0E9EC).fallbacks("rock");
    buildRenderInfo(MaterialIds.treatedWood);
    // redirect whitestone variants to whitestone composite instead of endstone
    redirect(MaterialIds.whitestoneAluminum, MaterialIds.whitestoneComposite);
    redirect(MaterialIds.whitestoneTin, MaterialIds.whitestoneComposite);
    redirect(MaterialIds.whitestoneZinc, MaterialIds.whitestoneComposite);
    buildRenderInfo(MaterialIds.aluminum);

    // tier 3 compat
    buildRenderInfo(MaterialIds.bronze).color(0xD49765).fallbacks("metal");
    buildRenderInfo(MaterialIds.constantan).color(0xFF8B70).fallbacks("metal");
    buildRenderInfo(MaterialIds.invar).color(0xCADBD0).fallbacks("metal");
    buildRenderInfo(MaterialIds.pewter).color(0x999483).fallbacks("metal");
    buildRenderInfo(MaterialIds.necronium).color(0x9CBD89).fallbacks("bone", "metal");
    buildRenderInfo(MaterialIds.electrum).color(0xFFEA65).fallbacks("metal");
    buildRenderInfo(MaterialIds.platedSlimewood).color(0xFFE170).fallbacks("slime_metal", "metal");
    buildRenderInfo(MaterialIds.steeleaf);
    buildRenderInfo(MaterialIds.conductiveAlloy).color(0xC78440).fallbacks("metal");
    buildRenderInfo(MaterialIds.redstoneAlloy).color(0xD02020).fallbacks("metal");
    buildRenderInfo(MaterialIds.pulsatingAlloy).color(0x42C878).fallbacks("metal");
    buildRenderInfo(MaterialIds.energeticAlloy).color(0xF2C94C).fallbacks("metal").luminosity(4);
    buildRenderInfo(MaterialIds.soularium).color(0x6F4A2C).fallbacks("metal");

    // tier 4 compat
    buildRenderInfo(MaterialIds.fiery).color(0x893D14).fallbacks("metal").luminosity(15);
    buildRenderInfo(MaterialIds.nicrosil).color(0xD9E6DC);
    buildRenderInfo(MaterialIds.vibrantAlloy).color(0x5DDB43).fallbacks("metal").luminosity(6);
    buildRenderInfo(MaterialIds.darkSteel).color(0x2A3035).fallbacks("metal");
    buildRenderInfo(MaterialIds.endSteel).color(0xD6D3C8).fallbacks("metal");
    buildRenderInfo(MaterialIds.certusQuartz).color(0xBFEAFF).fallbacks("crystal", "rock").luminosity(3);
    buildRenderInfo(MaterialIds.fluix).color(0x8D63D8).fallbacks("crystal", "rock").luminosity(5);
    buildRenderInfo(MaterialIds.quantumAlloy).color(0x62DFFF).fallbacks("metal").luminosity(5);
    buildRenderInfo(MaterialIds.entro).color(0xD66BFF).fallbacks("crystal", "metal").luminosity(6);
    buildRenderInfo(MaterialIds.energizedSteel).color(0xF4B441).fallbacks("metal").luminosity(3);
    buildRenderInfo(MaterialIds.blazingCrystal).color(0xFF8B2F).fallbacks("crystal", "rock").luminosity(6);
    buildRenderInfo(MaterialIds.nioticCrystal).color(0x41A9FF).fallbacks("crystal", "rock").luminosity(5);
    buildRenderInfo(MaterialIds.spiritedCrystal).color(0xA86BFF).fallbacks("crystal", "rock").luminosity(6);
    buildRenderInfo(MaterialIds.nitroCrystal).color(0xE43232).fallbacks("crystal", "rock").luminosity(7);
    buildRenderInfo(MaterialIds.uraninite).color(0x70D84D).fallbacks("crystal", "rock").luminosity(4);
    buildRenderInfo(MaterialIds.allthemodium).color(0xFFC72E).fallbacks("metal").luminosity(5);
    buildRenderInfo(MaterialIds.vibranium).color(0x18E0D1).fallbacks("metal").luminosity(6);
    buildRenderInfo(MaterialIds.unobtainium).color(0xC43BFF).fallbacks("metal").luminosity(7);

    // ammo
    buildRenderInfo(MaterialIds.amethyst);
    buildRenderInfo(MaterialIds.prismarine);
    buildRenderInfo(MaterialIds.glass);
    buildRenderInfo(MaterialIds.earthslime);
    buildRenderInfo(MaterialIds.skyslime);
    buildRenderInfo(MaterialIds.enderslime);
    buildRenderInfo(MaterialIds.blaze);
    buildRenderInfo(MaterialIds.enderPearl);
    buildRenderInfo(MaterialIds.quartz);
    buildRenderInfo(MaterialIds.ichor).luminosity(10);
    buildRenderInfo(MaterialIds.magma).luminosity(5);
    buildRenderInfo(MaterialIds.glowstone).luminosity(15);
    buildRenderInfo(MaterialIds.gunpowder);
    buildRenderInfo(MaterialIds.redstone);
    buildRenderInfo(MaterialIds.turtle);
    buildRenderInfo(MaterialIds.nautilus).color(0xFFD4CCC3);
    buildRenderInfo(MaterialIds.dragonScale);
    buildRenderInfo(MaterialIds.endRod);
    redirect(MaterialIds.magnetite, MaterialIds.steel);
    redirect(MaterialIds.kobold, MaterialIds.cobalt);
    redirect(MaterialIds.shulker, MaterialIds.chorus);
    redirect(MaterialIds.knightly, MaterialIds.knightmetal);

    // plate
    buildRenderInfo(MaterialIds.gold).color(0xFDF55F).fallbacks("metal");
    buildRenderInfo(MaterialIds.obsidian);
    // slimesuit
    buildRenderInfo(MaterialIds.blood);
    buildRenderInfo(MaterialIds.clay);
    buildRenderInfo(MaterialIds.honey);
    buildRenderInfo(MaterialIds.phantom);
    buildRenderInfo(MaterialIds.horn);
    buildRenderInfo(MaterialIds.cheese);

    // UI internal
    redirect(ToolBuildHandler.getRenderMaterial(0), MaterialIds.iron);
    redirect(ToolBuildHandler.getRenderMaterial(1), MaterialIds.wood);
    redirect(ToolBuildHandler.getRenderMaterial(2), MaterialIds.cobalt);
    redirect(ToolBuildHandler.getRenderMaterial(3), MaterialIds.manyullyn);
    redirect(ToolBuildHandler.getRenderMaterial(4), MaterialIds.copper);
  }

  @Override
  public String getName() {
    return "Continuum Construct Material Render Info Provider";
  }
}
