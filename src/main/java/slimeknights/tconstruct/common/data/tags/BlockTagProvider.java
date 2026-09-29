package slimeknights.tconstruct.common.data.tags;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagEntry;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;
import slimeknights.mantle.datagen.MantleTags;
import slimeknights.mantle.registration.object.BuildingBlockObject;
import slimeknights.mantle.registration.object.EnumObject;
import slimeknights.mantle.registration.object.MetalItemObject;
import slimeknights.mantle.registration.object.WoodBlockObject;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.registration.GeodeItemObject;
import slimeknights.tconstruct.common.registration.GeodeItemObject.BudSize;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.gadgets.TinkerGadgets;
import slimeknights.tconstruct.shared.TinkerCommons;
import slimeknights.tconstruct.shared.TinkerMaterials;
import slimeknights.tconstruct.shared.block.ClearStainedGlassBlock.GlassColor;
import slimeknights.tconstruct.shared.block.SlimeType;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.data.SmelteryCompat;
import slimeknights.tconstruct.smeltery.data.SmelteryCompat.CompatType;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.world.TinkerHeadType;
import slimeknights.tconstruct.world.TinkerWorld;
import slimeknights.tconstruct.world.block.DirtType;
import slimeknights.tconstruct.world.block.FoliageType;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE;
import static net.minecraft.tags.BlockTags.MINEABLE_WITH_HOE;
import static net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE;
import static net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL;
import static net.minecraft.tags.BlockTags.NEEDS_DIAMOND_TOOL;
import static net.minecraft.tags.BlockTags.NEEDS_IRON_TOOL;
import static net.minecraft.tags.BlockTags.NEEDS_STONE_TOOL;
import static net.neoforged.neoforge.common.Tags.Blocks.NEEDS_GOLD_TOOL;
import static net.neoforged.neoforge.common.Tags.Blocks.NEEDS_NETHERITE_TOOL;
import static slimeknights.mantle.Mantle.commonResource;
import static slimeknights.tconstruct.common.TinkerTags.Blocks.MINEABLE_MELTING_BLACKLIST;
import static slimeknights.tconstruct.common.TinkerTags.Blocks.UNREPLACABLE_BY_LIQUID;

@SuppressWarnings({"unchecked", "SameParameterValue", "removal"})
public class BlockTagProvider extends TagsProvider<Block> {

  public BlockTagProvider(PackOutput output, CompletableFuture<Provider> lookupProvider) {
    super(output, Registries.BLOCK, lookupProvider);
  }

  @Override
  protected void addTags(HolderLookup.Provider pProvider) {
    this.addCommon();
    this.addTools();
    this.addWorld();
    this.addSmeltery();
    this.addFluids();
    this.addHarvest();
  }

  private void addCommon() {
    // ores
    addMetalTags(TinkerMaterials.cobalt, true);
    addMetalTags(TinkerMaterials.steel, true);
    // tier 3
    addMetalTags(TinkerMaterials.slimesteel, true); // beacon: skyslime and seared stone are expensive enough
    addMetalTags(TinkerMaterials.amethystBronze, false); // not beacon: mostly copper and amethyst
    addMetalTags(TinkerMaterials.roseGold, false); // not beacon: 50% copper
    addMetalTags(TinkerMaterials.pigIron, false); // not beacon: 50% food
    // tier 4
    addMetalTags(TinkerMaterials.cinderslime, true); // beacon: ichor and scorched stone are expensive enough
    addMetalTags(TinkerMaterials.queensSlime, true);
    addMetalTags(TinkerMaterials.manyullyn, true);
    addMetalTags(TinkerMaterials.hepatizon, true);
    addMetalTags(TinkerMaterials.soulsteel, true);
    // tier 5
    addMetalTags(TinkerMaterials.knightmetal, true);
    addMetalTags(TinkerMaterials.knightslime, true);

    // glass
    var silicaPanes = tag(TinkerTags.Blocks.GLASS_PANES_SILICA);
    silicaPanes.add(
      key(Blocks.GLASS_PANE), key(TinkerCommons.clearGlassPane.get()),
      key(Blocks.BLACK_STAINED_GLASS_PANE), key(Blocks.BLUE_STAINED_GLASS_PANE), key(Blocks.BROWN_STAINED_GLASS_PANE), key(Blocks.CYAN_STAINED_GLASS_PANE),
      key(Blocks.GRAY_STAINED_GLASS_PANE), key(Blocks.GREEN_STAINED_GLASS_PANE), key(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE), key(Blocks.LIGHT_GRAY_STAINED_GLASS_PANE),
      key(Blocks.LIME_STAINED_GLASS_PANE), key(Blocks.MAGENTA_STAINED_GLASS_PANE), key(Blocks.ORANGE_STAINED_GLASS_PANE), key(Blocks.PINK_STAINED_GLASS_PANE),
      key(Blocks.PURPLE_STAINED_GLASS_PANE), key(Blocks.RED_STAINED_GLASS_PANE), key(Blocks.WHITE_STAINED_GLASS_PANE), key(Blocks.YELLOW_STAINED_GLASS_PANE));
    this.tag(BlockTags.create(Identifier.parse("c:glass/colorless"))).add(key(TinkerCommons.clearGlass.get()));
    this.tag(BlockTags.create(Identifier.parse("c:glass_panes/colorless"))).add(key(TinkerCommons.clearGlassPane.get()));
    addGlass(TinkerCommons.clearStainedGlass, "glass/", tag(BlockTags.create(Identifier.parse("c:glass/stained"))));
    addGlass(TinkerCommons.clearStainedGlassPane, "glass_panes/", tag(BlockTags.create(Identifier.parse("c:glass_panes/stained"))));
    TinkerCommons.clearStainedGlassPane.forEach(pane -> silicaPanes.add(key(pane)));

    // impermeable for all glass
    var impermeable = tag(BlockTags.IMPERMEABLE);
    var silicaGlass = tag(BlockTags.create(Identifier.parse("c:glass/silica")));
    impermeable.add(key(TinkerCommons.clearGlass.get()), key(TinkerCommons.soulGlass.get()), key(TinkerCommons.clearTintedGlass.get()),
                    key(TinkerSmeltery.searedGlass.get()), key(TinkerSmeltery.searedSoulGlass.get()), key(TinkerSmeltery.searedTintedGlass.get()),
                    key(TinkerSmeltery.scorchedGlass.get()), key(TinkerSmeltery.scorchedSoulGlass.get()), key(TinkerSmeltery.scorchedTintedGlass.get()));
    silicaGlass.add(key(TinkerCommons.clearGlass.get()));
    TinkerCommons.clearStainedGlass.values().forEach(b -> impermeable.add(key(b)));
    TinkerCommons.clearStainedGlass.values().forEach(b -> silicaGlass.add(key(b)));
    tag(BlockTags.create(Identifier.parse("c:glass/tinted"))).add(key(TinkerCommons.clearTintedGlass.get()));
    // parity/materials: official 3.12.1 put soul glass in forge:glass, clear stained glass in forge:stained_glass
    // and clear/tinted glass in forge:glass/colorless and forge:glass/tinted, all of which rolled up into forge:glass.
    // NeoForge 26.1 renamed that family to c:glass_blocks and c:glass_panes, so the port's c:glass/* tags above are
    // no longer part of it. Add the same blocks to the NeoForge names so daggers and other mods treat them as glass.
    tag(Tags.Blocks.GLASS_BLOCKS).add(key(TinkerCommons.soulGlass.get())).addTag(BlockTags.create(Identifier.parse("c:glass/stained")));
    tag(Tags.Blocks.GLASS_BLOCKS_COLORLESS).add(key(TinkerCommons.clearGlass.get()));
    tag(Tags.Blocks.GLASS_BLOCKS_TINTED).add(key(TinkerCommons.clearTintedGlass.get()));
    tag(Tags.Blocks.GLASS_PANES).add(key(TinkerCommons.soulGlassPane.get())).addTag(BlockTags.create(Identifier.parse("c:glass_panes/stained")));

    // soul speed on glass
    this.tag(BlockTags.SOUL_SPEED_BLOCKS).add(key(TinkerCommons.soulGlass.get()), key(TinkerCommons.soulGlassPane.get()),
                                              key(TinkerSmeltery.searedSoulGlass.get()), key(TinkerSmeltery.searedSoulGlassPane.get()),
                                              key(TinkerSmeltery.scorchedSoulGlass.get()), key(TinkerSmeltery.scorchedSoulGlassPane.get()));
    this.tag(BlockTags.SOUL_FIRE_BASE_BLOCKS).add(key(TinkerCommons.soulGlass.get()), key(TinkerSmeltery.searedSoulGlass.get()), TinkerSmeltery.scorchedSoulGlass.get());
    this.tag(TinkerTags.Blocks.TRANSPARENT_OVERLAY).add(key(TinkerCommons.soulGlass.get()), key(TinkerCommons.soulGlassPane.get()),
                                                        TinkerSmeltery.searedSoulGlass.get(), TinkerSmeltery.searedSoulGlassPane.get(),
                                                        TinkerSmeltery.scorchedSoulGlass.get(), TinkerSmeltery.scorchedSoulGlassPane.get());
    Function<String,Identifier> createId = name -> Identifier.fromNamespaceAndPath("create", name);
    Function<String,Identifier> quarkId = name -> Identifier.fromNamespaceAndPath("quark", name);
    this.tag(TinkerTags.Blocks.WORKSTATION_ROCK)
      .addTags(TinkerTags.Blocks.STONE, TinkerTags.Blocks.BLACKSTONE, TinkerTags.Blocks.GRANITE, TinkerTags.Blocks.DIORITE, TinkerTags.Blocks.ANDESITE, TinkerTags.Blocks.DEEPSLATE, TinkerTags.Blocks.BASALT)
      .add(key(Blocks.TUFF), key(Blocks.DRIPSTONE_BLOCK), key(Blocks.CALCITE))
      // create stones
      .addOptional(ResourceKey.create(Registries.BLOCK, createId.apply("asurine"))).addOptional(ResourceKey.create(Registries.BLOCK, createId.apply("crimsite"))).addOptional(ResourceKey.create(Registries.BLOCK, createId.apply("limestone"))).addOptional(ResourceKey.create(Registries.BLOCK, createId.apply("ochrum")))
      .addOptional(ResourceKey.create(Registries.BLOCK, createId.apply("scoria"))).addOptional(ResourceKey.create(Registries.BLOCK, createId.apply("scorchia"))).addOptional(ResourceKey.create(Registries.BLOCK, createId.apply("veridium")))
      // quark stones
      .addOptional(ResourceKey.create(Registries.BLOCK, quarkId.apply("jasper"))).addOptional(ResourceKey.create(Registries.BLOCK, quarkId.apply("limestone"))).addOptional(ResourceKey.create(Registries.BLOCK, quarkId.apply("permafrost")))
      .addOptional(ResourceKey.create(Registries.BLOCK, quarkId.apply("shale"))).addOptional(ResourceKey.create(Registries.BLOCK, quarkId.apply("myalite")));

    this.tag(BlockTags.create(Identifier.parse("c:storage_blocks/iron"))).add(key(Blocks.IRON_BLOCK));
    this.tag(BlockTags.create(Identifier.parse("c:storage_blocks/gold"))).add(key(Blocks.GOLD_BLOCK));
    this.tag(BlockTags.create(Identifier.parse("c:storage_blocks/copper"))).add(key(Blocks.COPPER_BLOCK));
    this.tag(BlockTags.create(Identifier.parse("c:storage_blocks/raw_iron"))).add(key(Blocks.RAW_IRON_BLOCK));
    this.tag(BlockTags.create(Identifier.parse("c:storage_blocks/raw_gold"))).add(key(Blocks.RAW_GOLD_BLOCK));
    this.tag(BlockTags.create(Identifier.parse("c:storage_blocks/raw_copper"))).add(key(Blocks.RAW_COPPER_BLOCK));
    this.tag(BlockTags.create(Identifier.parse("c:storage_blocks"))).addTags(
      BlockTags.create(Identifier.parse("c:storage_blocks/raw_iron")),
      BlockTags.create(Identifier.parse("c:storage_blocks/raw_gold")),
      BlockTags.create(Identifier.parse("c:storage_blocks/raw_copper")));
    this.tag(BlockTags.create(Identifier.parse("c:storage_blocks/netherite"))).add(key(Blocks.NETHERITE_BLOCK));
    this.tag(BlockTags.create(Identifier.parse("c:storage_blocks/diamond"))).add(key(Blocks.DIAMOND_BLOCK));
    this.tag(BlockTags.create(Identifier.parse("c:storage_blocks/emerald"))).add(key(Blocks.EMERALD_BLOCK));
    this.tag(BlockTags.create(Identifier.parse("c:storage_blocks/lapis"))).add(key(Blocks.LAPIS_BLOCK));
    this.tag(BlockTags.create(Identifier.parse("c:storage_blocks/quartz"))).add(key(Blocks.QUARTZ_BLOCK));
    var builder = this.tag(TinkerTags.Blocks.ANVIL_METAL)
        // tier 3
        .addTag(TinkerMaterials.slimesteel.getBlockTag())
        .addTag(TinkerMaterials.amethystBronze.getBlockTag())
        .addTag(TinkerMaterials.roseGold.getBlockTag())
        .addTag(TinkerMaterials.pigIron.getBlockTag())
        // tier 4
        .addTag(TinkerMaterials.cinderslime.getBlockTag())
        .addTag(TinkerMaterials.queensSlime.getBlockTag())
        .addTag(TinkerMaterials.manyullyn.getBlockTag())
        .addTag(TinkerMaterials.hepatizon.getBlockTag())
        .addTag(TinkerMaterials.knightmetal.getBlockTag())
        .addTag(TinkerMaterials.knightslime.getBlockTag())
        .addOptionalTag(TagKey.create(Registries.BLOCK, Identifier.parse("c:storage_blocks/netherite")));
    for (SmelteryCompat compat : SmelteryCompat.values()) {
      if (compat.getType() == CompatType.ALLOY) {
        builder.addOptionalTag(TagKey.create(Registries.BLOCK, commonResource("storage_blocks/" + compat.getName())));
      }
    }

    // allow using wood variants to make tables
    this.tag(TinkerTags.Blocks.PLANKLIKE)
        .addTag(BlockTags.PLANKS)
.add(key(TinkerMaterials.blazewood.get()), key(TinkerMaterials.nahuatl.get()));
    // things the platform connects to on the sides
    this.tag(TinkerTags.Blocks.PLATFORM_CONNECTIONS)
      .add(key(Blocks.LEVER), key(Blocks.LADDER), key(Blocks.IRON_BARS), key(TinkerCommons.goldBars.get()), key(Blocks.TRIPWIRE_HOOK), key(Blocks.WALL_TORCH), key(Blocks.SOUL_WALL_TORCH), key(Blocks.REDSTONE_WALL_TORCH), key(Blocks.REDSTONE_WIRE))
      .addTags(BlockTags.create(Identifier.parse("c:glass_panes")), BlockTags.BUTTONS, BlockTags.create(Identifier.parse("c:fences")), BlockTags.WALLS, BlockTags.WALL_SIGNS)
      .addOptionalTag(TagKey.create(Registries.BLOCK, Identifier.parse("architects_palette:nubs")));

    // copper platforms
    var copperPlatforms = this.tag(TinkerTags.Blocks.COPPER_PLATFORMS);
    TinkerCommons.copperPlatform.forEach(block -> copperPlatforms.add(key(block)));
    TinkerCommons.waxedCopperPlatform.forEach(block -> copperPlatforms.add(key(block)));
  }

  private void addTools() {
    // vanilla is not tagged, so tag it
    this.tag(TinkerTags.Blocks.WORKBENCHES)
        .add(key(Blocks.CRAFTING_TABLE), key(TinkerTables.craftingStation.get()))
        .addOptionalTag(TagKey.create(Registries.BLOCK, Identifier.parse("forge:workbench"))); // some mods use a non-standard name here, so support it I guess
    this.tag(TinkerTags.Blocks.TABLES)
.add(key(TinkerTables.craftingStation.get()), key(TinkerTables.partBuilder.get()), key(TinkerTables.tinkerStation.get()));

    // can harvest crops and sugar cane
    this.tag(TinkerTags.Blocks.HARVESTABLE_STACKABLE)
.add(key(Blocks.SUGAR_CANE), key(Blocks.KELP_PLANT));
    this.tag(TinkerTags.Blocks.HARVESTABLE_CROPS)
        .add(key(Blocks.NETHER_WART), key(Blocks.SWEET_BERRY_BUSH)) // berry bushes prefer interact, but can do crops if missing player
        .addTag(BlockTags.CROPS)
        .addOptionalTag(TagKey.create(Registries.BLOCK, commonResource("crops")));
    this.tag(TinkerTags.Blocks.HARVESTABLE_INTERACT)
.add(key(Blocks.SWEET_BERRY_BUSH), key(Blocks.CAVE_VINES), key(Blocks.CAVE_VINES_PLANT));
    this.tag(TinkerTags.Blocks.HARVESTABLE)
        .add(key(Blocks.PUMPKIN), key(Blocks.BEEHIVE), key(Blocks.BEE_NEST))
        .addTag(TinkerTags.Blocks.HARVESTABLE_CROPS)
        .addTag(TinkerTags.Blocks.HARVESTABLE_INTERACT)
        .addTag(TinkerTags.Blocks.HARVESTABLE_STACKABLE);
    // just logs for lumber axe, but modpack makers can add more
    this.tag(TinkerTags.Blocks.TREE_LOGS).addTag(BlockTags.LOGS);
    // blocks that drop gold and should drop more gold
    this.tag(TinkerTags.Blocks.CHRYSOPHILITE_ORES).addOptionalTag(TagKey.create(Registries.BLOCK, Identifier.parse("c:ores/gold"))).add(key(Blocks.GILDED_BLACKSTONE));
  }


  private void addWorld() {
    // ores
    this.tag(TinkerTags.Blocks.ORES_COBALT).add(key(TinkerWorld.cobaltOre.get()));
    this.tag(BlockTags.create(Identifier.parse("c:ores"))).addTag(TinkerTags.Blocks.ORES_COBALT);
    this.tag(BlockTags.create(Identifier.parse("c:ores_in_ground/netherrack"))).add(key(TinkerWorld.cobaltOre.get()));
    this.tag(BlockTags.create(Identifier.parse("c:ore_rates/singular"))).add(key(TinkerWorld.cobaltOre.get()));
    this.tag(TinkerTags.Blocks.RAW_BLOCK_COBALT).add(key(TinkerWorld.rawCobaltBlock.get()));
    this.tag(BlockTags.create(Identifier.parse("c:storage_blocks"))).addTag(TinkerTags.Blocks.RAW_BLOCK_COBALT).add(key(TinkerToolParts.fakeStorageBlock.get()));

    // allow the enderman to hold more blocks
    var endermanHoldable = this.tag(BlockTags.ENDERMAN_HOLDABLE);
    endermanHoldable.addTag(TinkerTags.Blocks.CONGEALED_SLIME).add(key(TinkerSmeltery.grout.get()), key(TinkerSmeltery.netherGrout.get()));

    // wood
    this.tag(TinkerTags.Blocks.SLIMY_LOGS)
        .addTags(TinkerWorld.greenheart.getLogBlockTag(), TinkerWorld.skyroot.getLogBlockTag(), TinkerWorld.bloodshroom.getLogBlockTag(), TinkerWorld.enderbark.getLogBlockTag());
    this.tag(TinkerTags.Blocks.SLIMY_PLANKS).add(key(TinkerWorld.greenheart.get()), key(TinkerWorld.skyroot.get()), TinkerWorld.bloodshroom.get(), TinkerWorld.enderbark.get());
    this.tag(BlockTags.PLANKS).addTag(TinkerTags.Blocks.SLIMY_PLANKS);
    this.tag(BlockTags.LOGS).addTag(TinkerTags.Blocks.SLIMY_LOGS);
    this.addWoodTags(TinkerWorld.greenheart, false);
    this.addWoodTags(TinkerWorld.skyroot, false);
    this.addWoodTags(TinkerWorld.bloodshroom, false);
    this.addWoodTags(TinkerWorld.enderbark, false);

    // slime blocks
    var slimeBlockTagAppender = this.tag(TinkerTags.Blocks.SLIME_BLOCK);
    var congealedTagAppender = this.tag(TinkerTags.Blocks.CONGEALED_SLIME);
    for (SlimeType type : SlimeType.values()) {
      slimeBlockTagAppender.add(key(TinkerWorld.slime.get(type)));
      congealedTagAppender.add(key(TinkerWorld.congealedSlime.get(type)));
    }

    // foliage
    this.tag(TinkerTags.Blocks.SLIMY_VINES).add(key(TinkerWorld.skySlimeVine.get()), key(TinkerWorld.enderSlimeVine.get()));
    var leavesTagAppender = this.tag(TinkerTags.Blocks.SLIMY_LEAVES);
    var wartTagAppender = this.tag(BlockTags.WART_BLOCKS);
    var saplingTagAppender = this.tag(TinkerTags.Blocks.SLIMY_SAPLINGS);
    for (FoliageType type : FoliageType.values()) {
      if (type.isNether()) {
        wartTagAppender.add(key(TinkerWorld.slimeLeaves.get(type)));
        endermanHoldable.add(key(TinkerWorld.slimeSapling.get(type)));
      } else {
        leavesTagAppender.add(key(TinkerWorld.slimeLeaves.get(type)));
        saplingTagAppender.add(key(TinkerWorld.slimeSapling.get(type)));
      }
    }
    this.tag(BlockTags.LEAVES).addTag(TinkerTags.Blocks.SLIMY_LEAVES);
    this.tag(BlockTags.SAPLINGS).addTag(TinkerTags.Blocks.SLIMY_SAPLINGS);

    var slimyGrass = this.tag(TinkerTags.Blocks.SLIMY_GRASS);
    var slimyNylium = this.tag(TinkerTags.Blocks.SLIMY_NYLIUM);
    var slimySoil = this.tag(TinkerTags.Blocks.SLIMY_SOIL);
    for (FoliageType type : FoliageType.values()) {
      (type.isNether() ? slimyNylium : slimyGrass).addTag(type.getGrassBlockTag());
    }
    for (DirtType type : DirtType.values()) {
      slimySoil.addTag(type.getBlockTag());
    }
    TinkerWorld.slimeGrass.forEach((dirtType, blockObj) -> blockObj.forEach((grassType, block) -> {
      this.tag(grassType.getGrassBlockTag()).add(key(block));
      this.tag(dirtType.getBlockTag()).add(key(block));
    }));
    TinkerWorld.slimeDirt.forEach((type, block) -> this.tag(type.getBlockTag()).add(key(block)));
    var enderBarkRoots = this.tag(TinkerTags.Blocks.ENDERBARK_ROOTS).add(key(TinkerWorld.enderbarkRoots.get()));
    TinkerWorld.slimyEnderbarkRoots.forEach((type, block) -> {
      this.tag(type.asDirt().getBlockTag()).add(key(block));
      enderBarkRoots.add(key(block));
    });
    this.tag(TinkerTags.Blocks.CREATE_ROOTS).addTag(TinkerTags.Blocks.ENDERBARK_ROOTS);
    endermanHoldable.addTag(TinkerTags.Blocks.SLIMY_SOIL);
    tagBlocks(BlockTags.SWORD_EFFICIENT, TinkerWorld.slimeTallGrass, TinkerWorld.slimeFern);
    tagBlocks(BlockTags.REPLACEABLE, TinkerWorld.slimeTallGrass, TinkerWorld.slimeFern);
    tagBlocks(BlockTags.REPLACEABLE_BY_TREES, TinkerWorld.slimeTallGrass, TinkerWorld.slimeFern);
    tagBlocks(BlockTags.AZALEA_ROOT_REPLACEABLE, TinkerWorld.slimeTallGrass, TinkerWorld.slimeFern);

    Consumer<Block> flowerPotAppender = b -> this.tag(BlockTags.FLOWER_POTS).add(key(b));
    TinkerWorld.pottedSlimeFern.forEach(flowerPotAppender);
    TinkerWorld.pottedSlimeSapling.forEach(flowerPotAppender);

    this.tag(TinkerTags.Blocks.ENDERBARK_LOGS_CAN_GROW_THROUGH)
        .addTags(TinkerTags.Blocks.SLIMY_VINES, TinkerTags.Blocks.SLIMY_SAPLINGS, TinkerTags.Blocks.CONGEALED_SLIME, TinkerTags.Blocks.ENDERBARK_ROOTS, TinkerTags.Blocks.SLIMY_LEAVES, TinkerTags.Blocks.SLIMY_LOGS);
    this.tag(TinkerTags.Blocks.ENDERBARK_ROOTS_CAN_GROW_THROUGH)
        .addTags(TinkerTags.Blocks.SLIMY_VINES, TinkerTags.Blocks.SLIMY_SAPLINGS, TinkerTags.Blocks.CONGEALED_SLIME, TinkerTags.Blocks.ENDERBARK_ROOTS)
.add(key(Blocks.SNOW));
    // copy of the list of blocks used in vanilla fungus, which really should have been a tag in the first place
    // we use tags so it works with our slimy foliage too
    this.tag(TinkerTags.Blocks.SLIMY_FUNGUS_CAN_GROW_THROUGH)
      .addTags(BlockTags.SAPLINGS, BlockTags.FLOWERS, BlockTags.CROPS, BlockTags.CAVE_VINES)
        .add(key(Blocks.BROWN_MUSHROOM), key(Blocks.RED_MUSHROOM), key(Blocks.SUGAR_CANE), key(Blocks.LILY_PAD), key(Blocks.NETHER_WART), key(Blocks.COCOA), key(Blocks.CHORUS_PLANT), key(Blocks.CHORUS_FLOWER),
             key(Blocks.SWEET_BERRY_BUSH), key(Blocks.WARPED_FUNGUS), key(Blocks.CRIMSON_FUNGUS), key(Blocks.WEEPING_VINES), key(Blocks.WEEPING_VINES_PLANT), key(Blocks.TWISTING_VINES), key(Blocks.TWISTING_VINES_PLANT),
             key(Blocks.SPORE_BLOSSOM), key(Blocks.MOSS_CARPET), key(Blocks.BIG_DRIPLEAF), key(Blocks.BIG_DRIPLEAF_STEM), key(Blocks.SMALL_DRIPLEAF),
             key(TinkerWorld.slimeTallGrass.get(FoliageType.ICHOR)), key(TinkerWorld.slimeTallGrass.get(FoliageType.BLOOD)),
             key(TinkerWorld.slimeFern.get(FoliageType.ICHOR)), key(TinkerWorld.slimeFern.get(FoliageType.BLOOD)));


    // slime spawns
    this.tag(TinkerTags.Blocks.SKY_SLIME_SPAWN).add(key(TinkerWorld.skyGeode.getBlock()), key(TinkerWorld.skyGeode.getBudding())).addTag(FoliageType.SKY.getGrassBlockTag());
    this.tag(TinkerTags.Blocks.EARTH_SLIME_SPAWN).add(key(TinkerWorld.earthGeode.getBlock()), key(TinkerWorld.earthGeode.getBudding())).addTag(FoliageType.EARTH.getGrassBlockTag());
    this.tag(TinkerTags.Blocks.ENDER_SLIME_SPAWN).add(key(TinkerWorld.enderGeode.getBlock()), key(TinkerWorld.enderGeode.getBudding())).addTag(FoliageType.ENDER.getGrassBlockTag());

    // budding tag
    tag(TinkerTags.Blocks.BUDDING).add(key(TinkerWorld.earthGeode.getBudding()), key(TinkerWorld.skyGeode.getBudding()), key(TinkerWorld.ichorGeode.getBudding()), key(TinkerWorld.enderGeode.getBudding()));

    this.tag(BlockTags.GUARDED_BY_PIGLINS)
.add(key(TinkerTables.castChest.get()), key(TinkerCommons.goldBars.get()), key(TinkerCommons.goldPlatform.get()));
    // piglins are not a fan of zombie piglin corpses
    this.tag(BlockTags.PIGLIN_REPELLENTS)
.add(key(TinkerWorld.heads.get(TinkerHeadType.ZOMBIFIED_PIGLIN)), key(TinkerWorld.wallHeads.get(TinkerHeadType.ZOMBIFIED_PIGLIN)));

    // stone variants
    this.tag(TinkerTags.Blocks.STONE).add(key(Blocks.STONE), key(Blocks.COBBLESTONE), key(Blocks.MOSSY_COBBLESTONE));
    this.tag(TinkerTags.Blocks.GRANITE).add(Blocks.GRANITE);
    this.tag(TinkerTags.Blocks.DIORITE).add(Blocks.DIORITE);
    this.tag(TinkerTags.Blocks.ANDESITE).add(Blocks.ANDESITE);
    this.tag(TinkerTags.Blocks.BLACKSTONE).add(Blocks.BLACKSTONE);
    this.tag(TinkerTags.Blocks.DEEPSLATE).add(key(Blocks.DEEPSLATE), key(Blocks.COBBLED_DEEPSLATE));
    this.tag(TinkerTags.Blocks.BASALT).add(Blocks.BASALT);
  }

  private void addSmeltery() {
    // seared
    this.tag(TinkerTags.Blocks.SEARED_BRICKS).add(
      TinkerSmeltery.searedBricks.get(),
      TinkerSmeltery.searedFancyBricks.get(),
      TinkerSmeltery.searedTriangleBricks.get());
    this.tag(TinkerTags.Blocks.SEARED_BLOCKS)
        .add(key(TinkerSmeltery.searedStone.get()), key(TinkerSmeltery.searedCrackedBricks.get()), TinkerSmeltery.searedCobble.get(), TinkerSmeltery.searedPaver.get())
        .addTag(TinkerTags.Blocks.SEARED_BRICKS);
    this.tag(TinkerTags.Blocks.SMELTERY_BRICKS).addTag(TinkerTags.Blocks.SEARED_BLOCKS);
    this.tag(BlockTags.WALLS).add(TinkerSmeltery.searedBricks.getWall(), TinkerSmeltery.searedCobble.getWall());

    // scorched
    this.tag(TinkerTags.Blocks.SCORCHED_BLOCKS).add(
      TinkerSmeltery.scorchedStone.get(),
      TinkerSmeltery.polishedScorchedStone.get(),
      TinkerSmeltery.scorchedBricks.get(),
      TinkerSmeltery.scorchedRoad.get(),
      TinkerSmeltery.chiseledScorchedBricks.get());
    this.tag(TinkerTags.Blocks.FOUNDRY_BRICKS).addTag(TinkerTags.Blocks.SCORCHED_BLOCKS);
    this.tag(BlockTags.FENCES).add(TinkerSmeltery.scorchedBricks.getFence(), TinkerMaterials.blazewood.getFence(), TinkerMaterials.nahuatl.getFence());

    this.tag(TinkerTags.Blocks.CISTERN_CONNECTIONS)
        // cannot add channels as it requires a block state property to properly detect, look into a way to fix this later
.add(key(TinkerSmeltery.searedFaucet.get()), key(TinkerSmeltery.scorchedFaucet.get()));

    // tanks
    var searedTankTagAppender = this.tag(TinkerTags.Blocks.SEARED_TANKS);
    TinkerSmeltery.searedTank.values().forEach(searedTankTagAppender::accept);
    var scorchedTankTagAppender = this.tag(TinkerTags.Blocks.SCORCHED_TANKS);
    TinkerSmeltery.scorchedTank.values().forEach(scorchedTankTagAppender::accept);

    // gauges
    this.tag(MantleTags.Blocks.ATTACHED_GAUGES).add(key(TinkerSmeltery.copperGauge.get()), key(TinkerSmeltery.obsidianGauge.get()));

    // structure tags
    // melter supports the heater as a tank
    this.tag(TinkerTags.Blocks.HEATER_CONTROLLERS)
.add(key(TinkerSmeltery.searedMelter.get()), key(TinkerSmeltery.scorchedAlloyer.get()));
    this.tag(TinkerTags.Blocks.FUEL_TANKS)
        .add(TinkerSmeltery.searedHeater.get())
        .addTag(TinkerTags.Blocks.SEARED_TANKS)
        .addTag(TinkerTags.Blocks.SCORCHED_TANKS);
    this.tag(TinkerTags.Blocks.SMELTERY_TANKS).addTag(TinkerTags.Blocks.SEARED_TANKS);
    this.tag(TinkerTags.Blocks.FOUNDRY_TANKS).addTag(TinkerTags.Blocks.SCORCHED_TANKS);
    this.tag(TinkerTags.Blocks.ALLOYER_TANKS)
        .add(key(TinkerSmeltery.scorchedAlloyer.get()), key(TinkerSmeltery.searedMelter.get()))
        .addTag(TinkerTags.Blocks.SEARED_TANKS)
        .addTag(TinkerTags.Blocks.SCORCHED_TANKS);

    // blocks to ignore like air
    this.tag(TinkerTags.Blocks.STRUCTURE_AIR).add(key(Blocks.LIGHT), key(TinkerCommons.glowBlock.get()));

    // smeltery blocks
    // floor allows any basic seared blocks and all IO blocks
    this.tag(TinkerTags.Blocks.SMELTERY_FLOOR)
        .addTag(TinkerTags.Blocks.SEARED_BLOCKS)
.add(key(TinkerSmeltery.searedLamp.get()), key(TinkerSmeltery.searedDrain.get()), key(TinkerSmeltery.searedChute.get()), key(TinkerSmeltery.searedDuct.get()));
    // wall allows seared blocks, tanks, glass, and IO
    this.tag(TinkerTags.Blocks.SMELTERY_WALL)
        .addTag(TinkerTags.Blocks.SEARED_BLOCKS)
        .addTag(TinkerTags.Blocks.SMELTERY_TANKS)
        .add(key(TinkerSmeltery.searedGlass.get()), key(TinkerSmeltery.searedSoulGlass.get()), TinkerSmeltery.searedTintedGlass.get(),
             TinkerSmeltery.searedLadder.get(), TinkerSmeltery.searedLamp.get(),
             TinkerSmeltery.searedDrain.get(), TinkerSmeltery.searedChute.get(), TinkerSmeltery.searedDuct.get());
    // smeltery allows any of the three
    this.tag(TinkerTags.Blocks.SMELTERY)
        .addTag(TinkerTags.Blocks.SMELTERY_WALL)
        .addTag(TinkerTags.Blocks.SMELTERY_FLOOR)
        .addTag(TinkerTags.Blocks.SMELTERY_TANKS);

    // foundry blocks
    // floor allows any basic seared blocks and all IO blocks
    this.tag(TinkerTags.Blocks.FOUNDRY_FLOOR)
        .addTag(TinkerTags.Blocks.SCORCHED_BLOCKS)
.add(key(TinkerSmeltery.scorchedLamp.get()), key(TinkerSmeltery.scorchedDrain.get()), key(TinkerSmeltery.scorchedChute.get()), key(TinkerSmeltery.scorchedDuct.get()));
    // wall allows seared blocks, tanks, glass, and IO
    this.tag(TinkerTags.Blocks.FOUNDRY_WALL)
        .addTag(TinkerTags.Blocks.SCORCHED_BLOCKS)
        .addTag(TinkerTags.Blocks.FOUNDRY_TANKS)
        .add(key(TinkerSmeltery.scorchedGlass.get()), key(TinkerSmeltery.scorchedSoulGlass.get()), TinkerSmeltery.scorchedTintedGlass.get(),
             TinkerSmeltery.scorchedLadder.get(), TinkerSmeltery.scorchedLamp.get(),
             TinkerSmeltery.scorchedDrain.get(), TinkerSmeltery.scorchedChute.get(), TinkerSmeltery.scorchedDuct.get());
    // foundry allows any of the three
    this.tag(TinkerTags.Blocks.FOUNDRY)
        .addTag(TinkerTags.Blocks.FOUNDRY_WALL)
        .addTag(TinkerTags.Blocks.FOUNDRY_FLOOR)
        .addTag(TinkerTags.Blocks.FOUNDRY_TANKS);

    // climb seared ladder
    this.tag(BlockTags.CLIMBABLE).add(key(TinkerSmeltery.searedLadder.get()), key(TinkerSmeltery.scorchedLadder.get()));
    this.tag(BlockTags.DRAGON_IMMUNE).add(key(TinkerCommons.obsidianPane.get()));
  }

  private void addFluids() {
    this.tag(BlockTags.STRIDER_WARM_BLOCKS).add(TinkerFluids.magma.getBlock(), TinkerFluids.blazingBlood.getBlock());
  }

  private void addHarvest() {
    // commons
    tagBlocks(MINEABLE_WITH_SHOVEL, TinkerCommons.cheeseBlock);
    tagBlocks(MINEABLE_WITH_AXE, TinkerGadgets.punji);
    tagBlocks(MINEABLE_WITH_PICKAXE, NEEDS_DIAMOND_TOOL, TinkerCommons.obsidianPane);
    tagBlocks(MINEABLE_WITH_PICKAXE, NEEDS_STONE_TOOL, TinkerCommons.ironPlatform);
    tagBlocks(MINEABLE_WITH_PICKAXE, NEEDS_IRON_TOOL, TinkerCommons.goldBars, TinkerCommons.goldPlatform, TinkerCommons.cobaltPlatform);
    this.tag(MINEABLE_WITH_PICKAXE).addTag(TinkerTags.Blocks.COPPER_PLATFORMS);
    this.tag(NEEDS_STONE_TOOL).addTag(TinkerTags.Blocks.COPPER_PLATFORMS);

    // materials
    tagBlocks(MINEABLE_WITH_AXE, NEEDS_IRON_TOOL, TinkerMaterials.blazewood);
    tagBlocks(MINEABLE_WITH_AXE, NEEDS_DIAMOND_TOOL, TinkerMaterials.nahuatl);
    tagBlocks(MINEABLE_WITH_PICKAXE, NEEDS_IRON_TOOL,
      TinkerWorld.cobaltOre, TinkerWorld.rawCobaltBlock, TinkerMaterials.steel, TinkerMaterials.cobalt,
      TinkerMaterials.slimesteel, TinkerMaterials.cinderslime, TinkerMaterials.amethystBronze,
      TinkerMaterials.roseGold, TinkerMaterials.pigIron, TinkerToolParts.fakeStorageBlock);
    tagBlocks(MINEABLE_WITH_PICKAXE, NEEDS_DIAMOND_TOOL, TinkerMaterials.queensSlime, TinkerMaterials.manyullyn, TinkerMaterials.hepatizon, TinkerMaterials.soulsteel);
    tagBlocks(MINEABLE_WITH_PICKAXE, NEEDS_NETHERITE_TOOL, TinkerMaterials.knightmetal, TinkerMaterials.knightslime);

    // slime
    tagBlocks(MINEABLE_WITH_SHOVEL, TinkerWorld.congealedSlime, TinkerWorld.slimeDirt, TinkerWorld.vanillaSlimeGrass, TinkerWorld.earthSlimeGrass, TinkerWorld.skySlimeGrass, TinkerWorld.enderSlimeGrass, TinkerWorld.ichorSlimeGrass);
    // harvest tiers on shovel blocks
    TinkerWorld.slimeDirt.forEach((type, block) -> {
      TagKey<Block> tag = harvestTag(type.getHarvestTier());
      if (tag != null) {
        this.tag(tag).add(block);
      }
    });
    for (DirtType dirt : DirtType.values()) {
      for (FoliageType grass : FoliageType.values()) {
        ToolMaterial dirtTier = dirt.getHarvestTier();
        ToolMaterial grassTier = grass.getHarvestTier();
        ToolMaterial tier = tierRank(dirtTier) >= tierRank(grassTier) ? dirtTier : grassTier;
        TagKey<Block> tag = harvestTag(tier);
        if (tag != null) {
          this.tag(tag).add(TinkerWorld.slimeGrass.get(dirt).get(grass));
        }
      }
    }

    tagBlocks(MINEABLE_WITH_HOE, TinkerWorld.slimeLeaves);
    tagLogs(MINEABLE_WITH_AXE, NEEDS_GOLD_TOOL, TinkerWorld.skyroot);
    tagLogs(MINEABLE_WITH_AXE, NEEDS_STONE_TOOL, TinkerWorld.greenheart);
    tagLogs(MINEABLE_WITH_AXE, NEEDS_IRON_TOOL, TinkerWorld.bloodshroom);
    tagLogs(MINEABLE_WITH_AXE, NEEDS_DIAMOND_TOOL, TinkerWorld.enderbark);
    tagPlanks(MINEABLE_WITH_SHOVEL, TinkerWorld.greenheart, TinkerWorld.skyroot, TinkerWorld.bloodshroom, TinkerWorld.enderbark);
    tagPlanks(MINEABLE_WITH_AXE, true, TinkerWorld.greenheart, TinkerWorld.skyroot, TinkerWorld.bloodshroom, TinkerWorld.enderbark);
    tagBlocks(MINEABLE_WITH_SHOVEL, TinkerWorld.slimyEnderbarkRoots);
    tagBlocks(MINEABLE_WITH_AXE, TinkerWorld.skySlimeVine, TinkerWorld.enderSlimeVine, TinkerWorld.enderbarkRoots);
    tagBlocks(MINEABLE_WITH_AXE, TinkerWorld.slimeTallGrass, TinkerWorld.slimeFern);
    tagBlocks(MINEABLE_WITH_PICKAXE, TinkerWorld.earthGeode, TinkerWorld.skyGeode, TinkerWorld.ichorGeode, TinkerWorld.enderGeode);
    tagBlocks(MINEABLE_WITH_PICKAXE, TinkerWorld.steelCluster, TinkerWorld.cobaltCluster, TinkerWorld.knightmetalCluster);
    tagBlocks(NEEDS_DIAMOND_TOOL, TinkerWorld.enderbarkRoots);
    tagBlocks(NEEDS_DIAMOND_TOOL, TinkerWorld.slimyEnderbarkRoots);


    // smeltery
    tagBlocks(MINEABLE_WITH_SHOVEL, TinkerSmeltery.grout, TinkerSmeltery.netherGrout);
    // seared
    tagBlocks(MINEABLE_WITH_PICKAXE, NEEDS_GOLD_TOOL, TinkerSmeltery.searedStone, TinkerSmeltery.searedPaver, TinkerSmeltery.searedCobble, TinkerSmeltery.searedBricks);
    tagBlocks(MINEABLE_WITH_PICKAXE, NEEDS_GOLD_TOOL, TinkerSmeltery.searedCrackedBricks, TinkerSmeltery.searedFancyBricks, TinkerSmeltery.searedTriangleBricks, TinkerSmeltery.searedLadder, TinkerSmeltery.searedLamp, TinkerSmeltery.searedGlass, TinkerSmeltery.searedSoulGlass, TinkerSmeltery.searedTintedGlass, TinkerSmeltery.searedGlassPane, TinkerSmeltery.searedSoulGlassPane);
    // scorched
    tagBlocks(MINEABLE_WITH_PICKAXE, NEEDS_GOLD_TOOL, TinkerSmeltery.scorchedBricks, TinkerSmeltery.scorchedRoad);
    tagBlocks(MINEABLE_WITH_PICKAXE, NEEDS_GOLD_TOOL, TinkerSmeltery.scorchedStone, TinkerSmeltery.polishedScorchedStone, TinkerSmeltery.chiseledScorchedBricks, TinkerSmeltery.scorchedLadder, TinkerSmeltery.scorchedLamp, TinkerSmeltery.scorchedGlass, TinkerSmeltery.scorchedSoulGlass, TinkerSmeltery.scorchedTintedGlass, TinkerSmeltery.scorchedGlassPane, TinkerSmeltery.scorchedSoulGlassPane);
    // fluids
    tagBlocks(MINEABLE_WITH_PICKAXE, NEEDS_GOLD_TOOL, TinkerSmeltery.searedTank, TinkerSmeltery.scorchedTank);
    tagBlocks(MINEABLE_WITH_PICKAXE, NEEDS_GOLD_TOOL, TinkerSmeltery.searedLantern,   TinkerSmeltery.searedFaucet,   TinkerSmeltery.searedChannel,   TinkerSmeltery.searedBasin,   TinkerSmeltery.searedTable,   TinkerSmeltery.searedCastingTank);
    tagBlocks(MINEABLE_WITH_PICKAXE, NEEDS_GOLD_TOOL, TinkerSmeltery.scorchedLantern, TinkerSmeltery.scorchedFaucet, TinkerSmeltery.scorchedChannel, TinkerSmeltery.scorchedBasin, TinkerSmeltery.scorchedTable);
    tagBlocks(MINEABLE_WITH_PICKAXE, NEEDS_GOLD_TOOL, TinkerSmeltery.searedHeater, TinkerSmeltery.searedMelter, TinkerSmeltery.scorchedAlloyer);
    // tough seared + scorched
    tagBlocks(MINEABLE_WITH_PICKAXE, NEEDS_STONE_TOOL, TinkerSmeltery.searedDrain, TinkerSmeltery.searedChute, TinkerSmeltery.smelteryController, TinkerSmeltery.searedFluidCannon, TinkerSmeltery.copperGauge);
    tagBlocks(MINEABLE_WITH_PICKAXE, NEEDS_IRON_TOOL, TinkerSmeltery.searedDuct, TinkerSmeltery.scorchedDuct, TinkerSmeltery.scorchedFluidCannon);
    tagBlocks(MINEABLE_WITH_PICKAXE, NEEDS_DIAMOND_TOOL, TinkerSmeltery.scorchedDrain, TinkerSmeltery.scorchedChute, TinkerSmeltery.scorchedProxyTank, TinkerSmeltery.foundryController, TinkerSmeltery.obsidianGauge, TinkerSmeltery.endFluidCannon);

    // tables
    tagBlocks(MINEABLE_WITH_AXE, TinkerTables.craftingStation, TinkerTables.tinkerStation, TinkerTables.partBuilder, TinkerTables.tinkersChest, TinkerTables.partChest);
    tagBlocks(MINEABLE_WITH_PICKAXE, TinkerTables.modifierWorktable);
    tagBlocks(MINEABLE_WITH_PICKAXE, NEEDS_STONE_TOOL, TinkerTables.castChest);
    tagBlocks(MINEABLE_WITH_PICKAXE, NEEDS_IRON_TOOL, TinkerTables.tinkersAnvil, TinkerTables.scorchedAnvil);

    // custom tool harvest
    // mattock works on all shovel and natural axe
    tag(TinkerTags.Blocks.MINABLE_WITH_MATTOCK).addTags(MINEABLE_WITH_SHOVEL, BlockTags.LOGS).add(
      Blocks.AZALEA, Blocks.BAMBOO, Blocks.GLOW_LICHEN, Blocks.VINE,
      Blocks.BEE_NEST, Blocks.BEEHIVE,
      Blocks.CARVED_PUMPKIN, Blocks.JACK_O_LANTERN, Blocks.PUMPKIN,
      Blocks.CHORUS_FLOWER, Blocks.CHORUS_PLANT, Blocks.COCOA,
      Blocks.BROWN_MUSHROOM_BLOCK, Blocks.MUSHROOM_STEM, Blocks.RED_MUSHROOM_BLOCK);
    // pickadze is shovel or pickaxe
    tag(TinkerTags.Blocks.MINABLE_WITH_PICKADZE).addTags(MINEABLE_WITH_SHOVEL, MINEABLE_WITH_PICKAXE);
    // hand axe has a leaf bonus
    tag(TinkerTags.Blocks.MINABLE_WITH_HAND_AXE).addTags(MINEABLE_WITH_AXE, BlockTags.LEAVES);
    // scythe/kama does hoe or shear blocks
    tag(TinkerTags.Blocks.MINABLE_WITH_SHEARS)
      .add(Blocks.AZALEA, Blocks.COBWEB, Blocks.DRIED_KELP_BLOCK, Blocks.GLOW_LICHEN, Blocks.LILY_PAD, Blocks.REDSTONE_WIRE, Blocks.HANGING_ROOTS,
           Blocks.TRIPWIRE, Blocks.TWISTING_VINES_PLANT, Blocks.TWISTING_VINES, Blocks.VINE, Blocks.WEEPING_VINES_PLANT, Blocks.WEEPING_VINES)
      .addTags(BlockTags.CAVE_VINES, BlockTags.LEAVES, BlockTags.WOOL, BlockTags.SAPLINGS, BlockTags.FLOWERS, BlockTags.CORAL_PLANTS);
    // scythe/kama does hoe or shear blocks
    tag(TinkerTags.Blocks.MINABLE_WITH_SCYTHE)
      .add(Blocks.KELP, Blocks.KELP_PLANT, Blocks.NETHER_WART, Blocks.SMALL_DRIPLEAF, Blocks.SUGAR_CANE)
      .addTags(MINEABLE_WITH_HOE, TinkerTags.Blocks.MINABLE_WITH_SHEARS, TinkerTags.Blocks.MINABLE_WITH_SWORD, BlockTags.CROPS)
      // added by sword effective tag
      .remove(Blocks.PUMPKIN, Blocks.CARVED_PUMPKIN, Blocks.MELON);
    // sword list is filled to best ability, but will be a bit inexact as vanilla uses materials, hopefully putting this tag under forge will get people to tag their blocks
    tag(TinkerTags.Blocks.MINABLE_WITH_SWORD).add(Blocks.COBWEB, Blocks.MOSS_BLOCK).addTags(BlockTags.SWORD_EFFICIENT);
    // dagger does hoe or sword blocks
    tag(TinkerTags.Blocks.MINABLE_WITH_DAGGER).addTags(MINEABLE_WITH_HOE, TinkerTags.Blocks.MINABLE_WITH_SWORD, Tags.Blocks.GLASS_BLOCKS, Tags.Blocks.GLASS_PANES)
      .add(Blocks.GLOWSTONE, Blocks.REDSTONE_LAMP, Blocks.SEA_LANTERN, Blocks.BEACON);

    // melting pan blacklist, basically anything that feels gross due to unsupported melting recipe
    tagBlocks(MINEABLE_MELTING_BLACKLIST, TinkerSmeltery.searedMelter, TinkerSmeltery.smelteryController, TinkerSmeltery.foundryController, TinkerSmeltery.searedLantern, TinkerSmeltery.scorchedLantern, TinkerSmeltery.searedFluidCannon, TinkerSmeltery.scorchedFluidCannon, TinkerSmeltery.endFluidCannon, TinkerSmeltery.searedCastingTank, TinkerSmeltery.scorchedProxyTank);
    tagBlocks(MINEABLE_MELTING_BLACKLIST, TinkerSmeltery.searedTank, TinkerSmeltery.scorchedTank);

    // copy of blocks list from FlowingFluid#canHoldFLuid
    tag(UNREPLACABLE_BY_LIQUID).addTags(BlockTags.SIGNS, BlockTags.DOORS).add(Blocks.LADDER, Blocks.SUGAR_CANE, Blocks.BUBBLE_COLUMN, Blocks.NETHER_PORTAL, Blocks.END_PORTAL, Blocks.END_GATEWAY, Blocks.STRUCTURE_VOID);
  }

  @Override
  public String getName() {
    return "Continuum Construct Block Tags";
  }

  /** Applies a tag to a set of suppliers */
  @SafeVarargs
  private void tagBlocks(TagKey<Block> tag, Supplier<? extends Block>... blocks) {
    var appender = this.tag(tag);
    for (Supplier<? extends Block> block : blocks) {
      appender.add(key(block.get()));
    }
  }

  /** Applies a tag to a set of suppliers */
  private void tagBlocks(TagKey<Block> tag, GeodeItemObject... blocks) {
    var appender = this.tag(tag);
    for (GeodeItemObject geode : blocks) {
      appender.add(key(geode.getBlock()));
      appender.add(key(geode.getBudding()));
      for (BudSize size : BudSize.values()) {
        appender.add(geode.getBud(size));
      }
    }
  }

  /** Applies a set of tags to a block */
  @SuppressWarnings("SameParameterValue")
  private void tagBlocks(TagKey<Block> tag1, TagKey<Block> tag2, Supplier<? extends Block>... blocks) {
    tagBlocks(tag1, blocks);
    tagBlocks(tag2, blocks);
  }

  /** Applies a tag to a set of blocks */
  @SafeVarargs
  private void tagBlocks(TagKey<Block> tag, EnumObject<?,? extends Block>... blocks) {
    var appender = this.tag(tag);
    for (EnumObject<?,? extends Block> block : blocks) {
      block.forEach(b -> appender.add(b));
    }
  }

  /** Applies a tag to a set of blocks */
  @SafeVarargs
  private void tagBlocks(TagKey<Block> tag1, TagKey<Block> tag2, EnumObject<?,? extends Block>... blocks) {
    tagBlocks(tag1, blocks);
    tagBlocks(tag2, blocks);
  }

  /** Applies a set of tags to a block */
  private void tagBlocks(TagKey<Block> tag, BuildingBlockObject... blocks) {
    var appender = this.tag(tag);
    for (BuildingBlockObject block : blocks) {
      block.values().forEach(appender::accept);
    }
  }

  /** Applies a set of tags to a block */
  @SuppressWarnings("SameParameterValue")
  private void tagBlocks(TagKey<Block> tag1, TagKey<Block> tag2, BuildingBlockObject... blocks) {
    tagBlocks(tag1, blocks);
    tagBlocks(tag2, blocks);
  }

  /** Applies a set of tags to either wood or logs from a block */
  @SuppressWarnings("SameParameterValue")
  private void tagLogs(TagKey<Block> tag1, TagKey<Block> tag2, WoodBlockObject... blocks) {
    for (WoodBlockObject block : blocks) {
      tag(tag1).add(block.getLog(), block.getWood());
      tag(tag2).add(block.getLog(), block.getWood());
    }
  }

  /** Adds or removes the planks from the tag. */
  @SuppressWarnings("SameParameterValue")
  private void tagPlanks(TagKey<Block> tag, boolean remove, WoodBlockObject... blocks) {
    for (WoodBlockObject block : blocks) {
      Block[] update = {
        block.getSlab(), block.getStairs(), block.getFence(),
        block.getStrippedLog(), block.getStrippedWood(),
        block.getFenceGate(), block.getDoor(), block.getTrapdoor(),
        block.getPressurePlate(), block.getButton(),
        block.getSign(), block.getWallSign(), block.getHangingSign(), block.getWallHangingSign()
      };
      if (remove) {
        tag(tag).remove(block.get(), update);
      } else {
        tag(tag).add(block.get()).add(update);
      }
    }
  }

  /** Applies a set of tags to either wood or logs from a block */
  private void tagPlanks(TagKey<Block> tag, WoodBlockObject... blocks) {
    tagPlanks(tag, false, blocks);
  }

  /**
   * Adds relevant tags for a metal object
   * @param metal  Metal object
   */
  private void addMetalTags(MetalItemObject metal, boolean beacon) {
    this.tag(metal.getBlockTag()).add(key(metal.get()));
    if (beacon) {
      this.tag(BlockTags.BEACON_BASE_BLOCKS).addTag(metal.getBlockTag());
    }
    this.tag(BlockTags.create(Identifier.parse("c:storage_blocks"))).addTag(metal.getBlockTag());
  }

  /** Adds tags for a glass item object */
  private void addGlass(EnumObject<GlassColor,? extends Block> blockObj, String tagPrefix, BlockTagAppender blockTag) {
    blockObj.forEach((color, block) -> {
      blockTag.add(block);
      this.tag(BlockTags.create(commonResource(tagPrefix + color.getSerializedName()))).add(block);
    });
  }

  /** Adds all tags relevant to the given wood object */
  private void addWoodTags(WoodBlockObject object, boolean doesBurn) {
    // planks, handled by slimy planks tag
    //this.tag(BlockTags.PLANKS).add(key(object.get()));
    this.tag(BlockTags.WOODEN_SLABS).add(key(object.getSlab()));
    this.tag(BlockTags.WOODEN_STAIRS).add(key(object.getStairs()));
    // logs
    this.tag(object.getLogBlockTag()).add(object.getLog(), object.getStrippedLog(), object.getWood(), object.getStrippedWood());

    // doors
    this.tag(BlockTags.WOODEN_FENCES).add(key(object.getFence()));
    this.tag(BlockTags.create(Identifier.parse("c:fences/wooden"))).add(key(object.getFence()));
    this.tag(BlockTags.FENCE_GATES).add(key(object.getFenceGate()));
    this.tag(BlockTags.create(Identifier.parse("c:fence_gates/wooden"))).add(key(object.getFenceGate()));
    this.tag(BlockTags.WOODEN_DOORS).add(key(object.getDoor()));
    this.tag(BlockTags.WOODEN_TRAPDOORS).add(key(object.getTrapdoor()));
    // redstone
    this.tag(BlockTags.WOODEN_BUTTONS).add(key(object.getButton()));
    this.tag(BlockTags.WOODEN_PRESSURE_PLATES).add(key(object.getPressurePlate()));

    if (doesBurn) {
      // regular logs is handled by slimy logs tag
      this.tag(BlockTags.LOGS_THAT_BURN).addTag(object.getLogBlockTag());
    }

    // signs
    this.tag(BlockTags.STANDING_SIGNS).add(key(object.getSign()));
    this.tag(BlockTags.WALL_SIGNS).add(key(object.getWallSign()));
    this.tag(BlockTags.CEILING_HANGING_SIGNS).add(key(object.getHangingSign()));
    this.tag(BlockTags.WALL_HANGING_SIGNS).add(key(object.getWallHangingSign()));
  }

    private BlockTagAppender tag(TagKey<Block> key) {
    return new BlockTagAppender(TagAppender.forBuilder(getOrCreateRawBuilder(key)));
  }

  private static ResourceKey<Block> key(Block block) {
    return BuiltInRegistries.BLOCK.getResourceKey(block).orElseThrow();
  }

  private static TagKey<Block> harvestTag(ToolMaterial material) {
    if (material == ToolMaterial.STONE) {
      return NEEDS_STONE_TOOL;
    }
    if (material == ToolMaterial.GOLD) {
      return NEEDS_GOLD_TOOL;
    }
    if (material == ToolMaterial.IRON) {
      return NEEDS_IRON_TOOL;
    }
    if (material == ToolMaterial.DIAMOND) {
      return NEEDS_DIAMOND_TOOL;
    }
    if (material == ToolMaterial.NETHERITE) {
      return NEEDS_NETHERITE_TOOL;
    }
    return null;
  }

  private static int tierRank(ToolMaterial material) {
    if (material == ToolMaterial.NETHERITE) {
      return 5;
    }
    if (material == ToolMaterial.DIAMOND) {
      return 4;
    }
    if (material == ToolMaterial.IRON) {
      return 3;
    }
    if (material == ToolMaterial.GOLD) {
      return 2;
    }
    if (material == ToolMaterial.STONE) {
      return 1;
    }
    return 0;
  }
  private static final class BlockTagAppender implements Consumer<Block> {
    private final TagAppender<ResourceKey<Block>, Block> delegate;

    private BlockTagAppender(TagAppender<ResourceKey<Block>, Block> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void accept(Block block) {
      add(block);
    }

    public BlockTagAppender add(Block block) {
      delegate.add(key(block));
      return this;
    }

    public BlockTagAppender add(Block... blocks) {
      for (Block block : blocks) {
        delegate.add(key(block));
      }
      return this;
    }

    @SafeVarargs
    public final BlockTagAppender add(ResourceKey<Block>... keys) {
      delegate.add(keys);
      return this;
    }

    public BlockTagAppender add(Object first, Object second, Object... rest) {
      addAny(first);
      addAny(second);
      for (Object value : rest) {
        addAny(value);
      }
      return this;
    }

    @SuppressWarnings("unchecked")
    private void addAny(Object value) {
      if (value instanceof Block block) {
        delegate.add(key(block));
      } else if (value instanceof ResourceKey<?> key) {
        delegate.add((ResourceKey<Block>)key);
      } else if (value instanceof TagEntry entry) {
        delegate.add(entry);
      } else {
        throw new IllegalArgumentException("Unsupported block tag entry " + value);
      }
    }

    public BlockTagAppender add(TagEntry entry) {
      delegate.add(entry);
      return this;
    }

    @SafeVarargs
    public final BlockTagAppender addTags(TagKey<Block>... tags) {
      delegate.addTags(tags);
      return this;
    }

    public BlockTagAppender addTag(TagKey<Block> tag) {
      delegate.addTag(tag);
      return this;
    }

    public BlockTagAppender addOptional(ResourceKey<Block> key) {
      delegate.addOptional(key);
      return this;
    }

    public BlockTagAppender addOptionalTag(TagKey<Block> tag) {
      delegate.addOptionalTag(tag);
      return this;
    }

    public BlockTagAppender remove(Block block, Block... blocks) {
      delegate.remove(key(block), java.util.Arrays.stream(blocks).map(BlockTagProvider::key).toArray(ResourceKey[]::new));
      return this;
    }

    @SafeVarargs
    public final BlockTagAppender remove(ResourceKey<Block> key, ResourceKey<Block>... keys) {
      delegate.remove(key, keys);
      return this;
    }
  }
}