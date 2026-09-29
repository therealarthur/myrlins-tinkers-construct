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
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagBuilder;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import slimeknights.mantle.datagen.MantleTags;
import slimeknights.mantle.registration.object.EnumObject;
import slimeknights.mantle.registration.object.FluidObject;
import slimeknights.mantle.registration.object.IdAwareObject;
import slimeknights.mantle.registration.object.MetalItemObject;
import slimeknights.mantle.registration.object.WoodBlockObject;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.registration.CastItemObject;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.gadgets.TinkerGadgets;
import slimeknights.tconstruct.gadgets.entity.FrameType;
import slimeknights.tconstruct.library.data.recipe.CostTagAppender;
import slimeknights.tconstruct.library.tools.definition.ModifiableArmorMaterial;
import slimeknights.tconstruct.shared.TinkerCommons;
import slimeknights.tconstruct.shared.TinkerMaterials;
import slimeknights.tconstruct.shared.block.SlimeType;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.world.TinkerHeadType;
import slimeknights.tconstruct.world.TinkerWorld;
import slimeknights.tconstruct.world.block.DirtType;
import slimeknights.tconstruct.world.block.FoliageType;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import static net.minecraft.tags.ItemTags.CLUSTER_MAX_HARVESTABLES;
import static slimeknights.mantle.Mantle.commonResource;
import static slimeknights.tconstruct.common.TinkerTags.Items.AMMO;
import static slimeknights.tconstruct.common.TinkerTags.Items.ANCIENT_TOOLS;
import static slimeknights.tconstruct.common.TinkerTags.Items.AOE;
import static slimeknights.tconstruct.common.TinkerTags.Items.ARMOR;
import static slimeknights.tconstruct.common.TinkerTags.Items.BALLISTAS;
import static slimeknights.tconstruct.common.TinkerTags.Items.BALLISTA_AMMO;
import static slimeknights.tconstruct.common.TinkerTags.Items.BANNER;
import static slimeknights.tconstruct.common.TinkerTags.Items.BASIC_ARMOR;
import static slimeknights.tconstruct.common.TinkerTags.Items.BONUS_SLOTS;
import static slimeknights.tconstruct.common.TinkerTags.Items.BOOK_ARMOR;
import static slimeknights.tconstruct.common.TinkerTags.Items.BOOTS;
import static slimeknights.tconstruct.common.TinkerTags.Items.BOWS;
import static slimeknights.tconstruct.common.TinkerTags.Items.BROAD_RANGED;
import static slimeknights.tconstruct.common.TinkerTags.Items.BROAD_TOOLS;
import static slimeknights.tconstruct.common.TinkerTags.Items.CHESTPLATES;
import static slimeknights.tconstruct.common.TinkerTags.Items.CROSSBOWS;
import static slimeknights.tconstruct.common.TinkerTags.Items.DURABILITY;
import static slimeknights.tconstruct.common.TinkerTags.Items.DYEABLE;
import static slimeknights.tconstruct.common.TinkerTags.Items.EMBELLISHMENT_SLIME;
import static slimeknights.tconstruct.common.TinkerTags.Items.EMBELLISHMENT_WOOD;
import static slimeknights.tconstruct.common.TinkerTags.Items.FANTASTIC_ARMOR;
import static slimeknights.tconstruct.common.TinkerTags.Items.FISHING_RODS;
import static slimeknights.tconstruct.common.TinkerTags.Items.GADGETRY_ARMOR;
import static slimeknights.tconstruct.common.TinkerTags.Items.HARVEST;
import static slimeknights.tconstruct.common.TinkerTags.Items.HARVEST_PRIMARY;
import static slimeknights.tconstruct.common.TinkerTags.Items.HELD;
import static slimeknights.tconstruct.common.TinkerTags.Items.HELD_ARMOR;
import static slimeknights.tconstruct.common.TinkerTags.Items.HELMETS;
import static slimeknights.tconstruct.common.TinkerTags.Items.HIDDEN_IN_RECIPE_VIEWERS;
import static slimeknights.tconstruct.common.TinkerTags.Items.INTERACTABLE;
import static slimeknights.tconstruct.common.TinkerTags.Items.INTERACTABLE_ARMOR;
import static slimeknights.tconstruct.common.TinkerTags.Items.INTERACTABLE_CHARGE;
import static slimeknights.tconstruct.common.TinkerTags.Items.INTERACTABLE_CHARGE_MODIFIER;
import static slimeknights.tconstruct.common.TinkerTags.Items.INTERACTABLE_DUAL;
import static slimeknights.tconstruct.common.TinkerTags.Items.INTERACTABLE_LEFT;
import static slimeknights.tconstruct.common.TinkerTags.Items.INTERACTABLE_RIGHT;
import static slimeknights.tconstruct.common.TinkerTags.Items.LAUNCHERS;
import static slimeknights.tconstruct.common.TinkerTags.Items.LEGGINGS;
import static slimeknights.tconstruct.common.TinkerTags.Items.LONGBOWS;
import static slimeknights.tconstruct.common.TinkerTags.Items.LOOT_CAPABLE_TOOL;
import static slimeknights.tconstruct.common.TinkerTags.Items.MELEE;
import static slimeknights.tconstruct.common.TinkerTags.Items.MELEE_PRIMARY;
import static slimeknights.tconstruct.common.TinkerTags.Items.MELEE_WEAPON;
import static slimeknights.tconstruct.common.TinkerTags.Items.MIGHTY_ARMOR;
import static slimeknights.tconstruct.common.TinkerTags.Items.MODIFIABLE;
import static slimeknights.tconstruct.common.TinkerTags.Items.MULTIPART_TOOL;
import static slimeknights.tconstruct.common.TinkerTags.Items.PARRY;
import static slimeknights.tconstruct.common.TinkerTags.Items.PUNY_ARMOR;
import static slimeknights.tconstruct.common.TinkerTags.Items.RANGED;
import static slimeknights.tconstruct.common.TinkerTags.Items.RANGED_BOUNCE;
import static slimeknights.tconstruct.common.TinkerTags.Items.RANGED_POWER;
import static slimeknights.tconstruct.common.TinkerTags.Items.RANGED_QUICK_CHARGE;
import static slimeknights.tconstruct.common.TinkerTags.Items.SHIELDS;
import static slimeknights.tconstruct.common.TinkerTags.Items.SINGLEPART_TOOL;
import static slimeknights.tconstruct.common.TinkerTags.Items.SINGLE_USE;
import static slimeknights.tconstruct.common.TinkerTags.Items.SKULLS;
import static slimeknights.tconstruct.common.TinkerTags.Items.SMALL_RANGED;
import static slimeknights.tconstruct.common.TinkerTags.Items.SMALL_TOOLS;
import static slimeknights.tconstruct.common.TinkerTags.Items.SPECIAL_TOOLS;
import static slimeknights.tconstruct.common.TinkerTags.Items.STAFFS;
import static slimeknights.tconstruct.common.TinkerTags.Items.STONE_HARVEST;
import static slimeknights.tconstruct.common.TinkerTags.Items.SWAPPABLE_SKULLS;
import static slimeknights.tconstruct.common.TinkerTags.Items.SWORD;
import static slimeknights.tconstruct.common.TinkerTags.Items.THROWN_AMMO;
import static slimeknights.tconstruct.common.TinkerTags.Items.TOOL_PARTS;
import static slimeknights.tconstruct.common.TinkerTags.Items.TRADER_TOOLS;
import static slimeknights.tconstruct.common.TinkerTags.Items.TRIM;
import static slimeknights.tconstruct.common.TinkerTags.Items.TRIM_NO_PATTERN;
import static slimeknights.tconstruct.common.TinkerTags.Items.UNARMED;
import static slimeknights.tconstruct.common.TinkerTags.Items.UNRECYCLABLE;
import static slimeknights.tconstruct.common.TinkerTags.Items.UNSALVAGABLE;
import static slimeknights.tconstruct.common.TinkerTags.Items.UNSWAPPABLE;
import static slimeknights.tconstruct.common.TinkerTags.Items.UNSWAPPABLE_PARTS;
import static slimeknights.tconstruct.common.TinkerTags.Items.UNSWAPPABLE_TOOLS;
import static slimeknights.tconstruct.common.TinkerTags.Items.WORN_ARMOR;

@SuppressWarnings({"unchecked", "removal"})
public class ItemTagProvider extends TagsProvider<Item> {
  /** Twlight forest uncrafting table blacklist */
  private static final TagKey<Item> BANNED_UNCRAFTABLE = ItemTags.create(Identifier.fromNamespaceAndPath("twilightforest", "banned_uncraftables"));
  private final Function<Identifier, TagAppender<ResourceKey<Item>, Item>> MAKE_TAG = identifier -> rawTag(ItemTags.create(identifier));
  private final CompletableFuture<TagsProvider.TagLookup<Block>> blockTags;

  public ItemTagProvider(PackOutput output, CompletableFuture<Provider> lookupProvider, CompletableFuture<TagsProvider.TagLookup<Block>> blockTags) {
    super(output, Registries.ITEM, lookupProvider);
    this.blockTags = blockTags;
  }

  @Override
  protected void addTags(HolderLookup.Provider lookupProvider) {
    this.addCommon();
    this.addWorld();
    this.addSmeltery();
    this.addTools();
  }

  @SuppressWarnings("unchecked")
  private void addCommon() {
    this.tag(TinkerTags.Items.TINKERS_GUIDES)
        .add(TinkerCommons.materialsAndYou.get(), TinkerCommons.tinkersGadgetry.get(),
             TinkerCommons.punySmelting.get(), TinkerCommons.mightySmelting.get(),
             TinkerCommons.fantasticFoundry.get(), TinkerCommons.encyclopedia.get());
    this.tag(ItemTags.LECTERN_BOOKS).addTag(TinkerTags.Items.TINKERS_GUIDES);
    this.tag(ItemTags.BOOKSHELF_BOOKS).addTag(TinkerTags.Items.TINKERS_GUIDES);
    this.tag(TinkerTags.Items.GUIDEBOOKS).addTag(TinkerTags.Items.TINKERS_GUIDES);
    this.tag(TinkerTags.Items.BOOKS).addTag(TinkerTags.Items.GUIDEBOOKS);

    var slimeballs = this.tag(Tags.Items.SLIME_BALLS);
    var legacySlimeballs = this.tag(ItemTags.create(Identifier.parse("c:slimeballs")));
    var slimeballAmmo = this.tag(TinkerTags.Items.SLIMEBALL_AMMO);
    for (SlimeType type : SlimeType.values()) {
      slimeballs.addTag(type.getSlimeballTag());
      legacySlimeballs.addTag(type.getSlimeballTag());
      slimeballAmmo.addTag(type.getSlimeballTag());
    }
    TinkerCommons.slimeball.forEach((type, ball) -> {
      this.tag(type.getSlimeballTag()).add(ball);
      this.tag(TinkerTags.Items.SLIMEBALL_AMMO).add(ball);
    });
    this.tag(TinkerTags.Items.SLIMEBALL_AMMO).add(Items.MAGMA_CREAM);

    this.tag(ItemTags.create(Identifier.parse("c:ingots/iron"))).add(Items.IRON_INGOT);
    this.tag(ItemTags.create(Identifier.parse("c:ingots/gold"))).add(Items.GOLD_INGOT);
    this.tag(ItemTags.create(Identifier.parse("c:ingots/copper"))).add(Items.COPPER_INGOT);
    this.tag(ItemTags.create(Identifier.parse("c:ingots/netherite"))).add(Items.NETHERITE_INGOT);
    this.tag(ItemTags.create(Identifier.parse("c:nuggets/iron"))).add(Items.IRON_NUGGET);
    this.tag(ItemTags.create(Identifier.parse("c:nuggets/gold"))).add(Items.GOLD_NUGGET);
    this.tag(ItemTags.create(Identifier.parse("c:raw_materials/iron"))).add(Items.RAW_IRON);
    this.tag(ItemTags.create(Identifier.parse("c:raw_materials/gold"))).add(Items.RAW_GOLD);
    this.tag(ItemTags.create(Identifier.parse("c:raw_materials/copper"))).add(Items.RAW_COPPER);
    this.tag(ItemTags.create(Identifier.parse("c:raw_materials"))).addTags(
      ItemTags.create(Identifier.parse("c:raw_materials/iron")),
      ItemTags.create(Identifier.parse("c:raw_materials/gold")),
      ItemTags.create(Identifier.parse("c:raw_materials/copper")));
    this.tag(ItemTags.create(Identifier.parse("c:storage_blocks/iron"))).add(Items.IRON_BLOCK);
    this.tag(ItemTags.create(Identifier.parse("c:storage_blocks/gold"))).add(Items.GOLD_BLOCK);
    this.tag(ItemTags.create(Identifier.parse("c:storage_blocks/copper"))).add(Items.COPPER_BLOCK);
    this.tag(ItemTags.create(Identifier.parse("c:storage_blocks/raw_iron"))).add(Items.RAW_IRON_BLOCK);
    this.tag(ItemTags.create(Identifier.parse("c:storage_blocks/raw_gold"))).add(Items.RAW_GOLD_BLOCK);
    this.tag(ItemTags.create(Identifier.parse("c:storage_blocks/raw_copper"))).add(Items.RAW_COPPER_BLOCK);
    this.tag(ItemTags.create(Identifier.parse("c:storage_blocks/netherite"))).add(Items.NETHERITE_BLOCK);
    this.tag(ItemTags.create(Identifier.parse("c:storage_blocks/diamond"))).add(Items.DIAMOND_BLOCK);
    this.tag(ItemTags.create(Identifier.parse("c:storage_blocks/emerald"))).add(Items.EMERALD_BLOCK);
    this.tag(ItemTags.create(Identifier.parse("c:storage_blocks/lapis"))).add(Items.LAPIS_BLOCK);
    this.tag(ItemTags.create(Identifier.parse("c:storage_blocks/quartz"))).add(Items.QUARTZ_BLOCK);
    this.tag(ItemTags.create(Identifier.parse("c:gems/diamond"))).add(Items.DIAMOND);
    this.tag(ItemTags.create(Identifier.parse("c:gems/emerald"))).add(Items.EMERALD);
    this.tag(ItemTags.create(Identifier.parse("c:gems/lapis"))).add(Items.LAPIS_LAZULI);
    this.tag(ItemTags.create(Identifier.parse("c:gems/quartz"))).add(Items.QUARTZ);
    this.tag(ItemTags.create(Identifier.parse("c:ingots"))).add(TinkerSmeltery.searedBrick.get(), TinkerSmeltery.scorchedBrick.get(), TinkerToolParts.fakeIngot.get()).addTag(TinkerTags.Items.INGOTS_NETHERITE_SCRAP);
    this.tag(ItemTags.create(Identifier.parse("c:nuggets"))).addTags(TinkerTags.Items.NUGGETS_COPPER, TinkerTags.Items.NUGGETS_NETHERITE, TinkerTags.Items.NUGGETS_NETHERITE_SCRAP);
    this.tag(TinkerTags.Items.BONES).add(Items.BONE);
    this.tag(TinkerTags.Items.WITHER_BONES).add(TinkerMaterials.necroticBone.get()).addTag(TinkerTags.Items.WEIRD_WITHER_BONES_TAG);
    this.tag(TinkerTags.Items.WEIRD_WITHER_BONES_TAG).add(TinkerMaterials.necroticBone.get());

    this.tag(TinkerTags.Items.NUGGETS_COPPER).add(TinkerMaterials.copperNugget.get());
    this.tag(TinkerTags.Items.INGOTS_NETHERITE_SCRAP).add(Items.NETHERITE_SCRAP);
    this.tag(TinkerTags.Items.NUGGETS_NETHERITE).add(TinkerMaterials.netheriteNugget.get());
    this.tag(TinkerTags.Items.NUGGETS_NETHERITE_SCRAP).add(TinkerMaterials.debrisNugget.get());
    this.tag(TinkerTags.Items.BALL_OF_MOSS_INGREDIENTS).add(
      Items.MOSSY_COBBLESTONE,
      Items.MOSSY_COBBLESTONE_SLAB,
      Items.MOSSY_COBBLESTONE_STAIRS,
      Items.MOSSY_COBBLESTONE_WALL,
      Items.MOSSY_STONE_BRICKS,
      Items.MOSSY_STONE_BRICK_SLAB,
      Items.MOSSY_STONE_BRICK_STAIRS,
      Items.MOSSY_STONE_BRICK_WALL,
      Items.MOSS_BLOCK,
      Items.MOSS_CARPET);

    this.tag(TinkerTags.Items.STEEL_SHARD).add(TinkerWorld.steelShard.get());
    this.tag(TinkerTags.Items.COBALT_SHARD).add(TinkerWorld.cobaltShard.get());
    this.tag(TinkerTags.Items.KNIGHTMETAL_SHARD).add(TinkerWorld.knightmetalShard.get());

    // ores
    addMetalTags(TinkerMaterials.steel);
    addMetalTags(TinkerMaterials.cobalt);
    // tier 3
    addMetalTags(TinkerMaterials.slimesteel);
    addMetalTags(TinkerMaterials.amethystBronze);
    addMetalTags(TinkerMaterials.roseGold);
    addMetalTags(TinkerMaterials.pigIron);
    // tier 4
    addMetalTags(TinkerMaterials.cinderslime);
    addMetalTags(TinkerMaterials.queensSlime);
    addMetalTags(TinkerMaterials.manyullyn);
    addMetalTags(TinkerMaterials.hepatizon);
    addMetalTags(TinkerMaterials.soulsteel);
    // tier 5
    addMetalTags(TinkerMaterials.knightmetal);
    addMetalTags(TinkerMaterials.knightslime);
    copy(TagKey.create(Registries.BLOCK, Identifier.parse("c:storage_blocks")), ItemTags.create(Identifier.parse("c:storage_blocks")));

    // glass
    copy(TagKey.create(Registries.BLOCK, Identifier.parse("c:glass/silica")), ItemTags.create(Identifier.parse("c:glass/silica")));
    copy(TagKey.create(Registries.BLOCK, Identifier.parse("c:glass/tinted")), ItemTags.create(Identifier.parse("c:glass/tinted")));
    copy(TinkerTags.Blocks.GLASS_PANES_SILICA, TinkerTags.Items.GLASS_PANES_SILICA);
    copy(TagKey.create(Registries.BLOCK, Identifier.parse("c:glass/colorless")), ItemTags.create(Identifier.parse("c:glass/colorless")));
    copy(TagKey.create(Registries.BLOCK, Identifier.parse("c:glass_panes/colorless")), ItemTags.create(Identifier.parse("c:glass_panes/colorless")));
    copy(TagKey.create(Registries.BLOCK, Identifier.parse("c:glass/stained")), ItemTags.create(Identifier.parse("c:glass/stained")));
    copy(TagKey.create(Registries.BLOCK, Identifier.parse("c:glass_panes/stained")), ItemTags.create(Identifier.parse("c:glass_panes/stained")));
    // parity/materials: NeoForge 26.1 names for the official forge:glass family (see BlockTagProvider)
    copy(Tags.Blocks.GLASS_BLOCKS, Tags.Items.GLASS_BLOCKS);
    copy(Tags.Blocks.GLASS_BLOCKS_COLORLESS, Tags.Items.GLASS_BLOCKS_COLORLESS);
    copy(Tags.Blocks.GLASS_BLOCKS_TINTED, Tags.Items.GLASS_BLOCKS_TINTED);
    copy(Tags.Blocks.GLASS_PANES, Tags.Items.GLASS_PANES);
    for (DyeColor color : DyeColor.values()) {
      Identifier name = commonResource("glass/" + color.getSerializedName());
      copy(TagKey.create(Registries.BLOCK, name), TagKey.create(Registries.ITEM, name));
      name = commonResource("glass_panes/" + color.getSerializedName());
      copy(TagKey.create(Registries.BLOCK, name), TagKey.create(Registries.ITEM, name));
    }

    copy(TinkerTags.Blocks.WORKBENCHES, TinkerTags.Items.WORKBENCHES);
    copy(TinkerTags.Blocks.TABLES, TinkerTags.Items.TABLES);
    copy(TinkerTags.Blocks.WORKSTATION_ROCK, TinkerTags.Items.WORKSTATION_ROCK);
    copy(TinkerTags.Blocks.ANVIL_METAL, TinkerTags.Items.ANVIL_METAL);
    copy(TinkerTags.Blocks.PLANKLIKE, TinkerTags.Items.PLANKLIKE);

    // piglins like gold and dislike zombie piglin heads
    this.tag(ItemTags.PIGLIN_LOVED)
        .add(TinkerModifiers.goldReinforcement.get(), TinkerGadgets.itemFrame.get(FrameType.GOLD), TinkerGadgets.itemFrame.get(FrameType.REVERSED_GOLD), TinkerFluids.moltenGold.asItem(), TinkerCommons.goldBars.asItem(), TinkerCommons.goldPlatform.asItem())
        .addTag(TinkerTags.Items.GOLD_CASTS);
    this.tag(ItemTags.PIGLIN_REPELLENTS).add(TinkerWorld.headItems.get(TinkerHeadType.ZOMBIFIED_PIGLIN));
    this.tag(TinkerTags.Items.TREE_ATTACHMENTS).add(TinkerWorld.slimeSapling.get(FoliageType.ENDER).asItem());

    // beacons are happy to accept any expensive ingots
    // mirrors the block list
    this.tag(ItemTags.BEACON_PAYMENT_ITEMS).addTags(
      // ores
      TinkerMaterials.steel.getIngotTag(), TinkerMaterials.cobalt.getIngotTag(),
      // tier 3
      TinkerMaterials.slimesteel.getIngotTag(),
      // tier 4
      TinkerMaterials.cinderslime.getIngotTag(), TinkerMaterials.queensSlime.getIngotTag(),
      TinkerMaterials.manyullyn.getIngotTag(), TinkerMaterials.hepatizon.getIngotTag(),
      TinkerMaterials.knightmetal.getIngotTag(), TinkerMaterials.knightslime.getIngotTag());

    copy(TinkerTags.Blocks.COPPER_PLATFORMS, TinkerTags.Items.COPPER_PLATFORMS);

    this.tag(MantleTags.Items.SPLASH_BOTTLE).add(TinkerFluids.splashBottle.get());
    this.tag(MantleTags.Items.LINGERING_BOTTLE).add(TinkerFluids.lingeringBottle.get());

    // trim materials
    this.tag(ItemTags.TRIM_MATERIALS).add(
      TinkerMaterials.slimesteel.getIngot(), TinkerMaterials.amethystBronze.getIngot(), TinkerMaterials.pigIron.getIngot(), TinkerMaterials.roseGold.getIngot(),
      TinkerMaterials.steel.getIngot(), TinkerMaterials.cobalt.getIngot(), TinkerMaterials.manyullyn.getIngot(), TinkerMaterials.hepatizon.getIngot(), TinkerMaterials.cinderslime.getIngot(), TinkerMaterials.queensSlime.getIngot(),
      TinkerMaterials.knightmetal.getIngot(), TinkerMaterials.knightslime.getIngot(),
      TinkerWorld.earthGeode.asItem(), TinkerWorld.skyGeode.asItem(), TinkerWorld.ichorGeode.asItem(), TinkerWorld.enderGeode.asItem()
    );

    // items to fully hide from JEI
    var hidden = tag(HIDDEN_IN_RECIPE_VIEWERS);
    hidden.add(
      // internal item for modifiers
      TinkerTools.crystalshotItem.asItem(),
      // unused future fluids
      TinkerFluids.moltenSoulsteel.asItem() // parity (oracle): official does not hide the knightslime bucket (was also moltenKnightslime)
    );
    // unused future material items
    TinkerMaterials.soulsteel.forEach(item -> hidden.add(item.asItem()));
    // ichor foliage
    hidden.add(
      TinkerWorld.slimeLeaves.get(FoliageType.ICHOR).asItem(),
      TinkerWorld.slimeTallGrass.get(FoliageType.ICHOR).asItem(),
      TinkerWorld.slimeFern.get(FoliageType.ICHOR).asItem(),
      TinkerWorld.slimeSapling.get(FoliageType.ICHOR).asItem(),
      TinkerWorld.slimeGrassSeeds.get(FoliageType.ICHOR).asItem()
    );
    for (DirtType dirtType : DirtType.values()) {
      hidden.add(TinkerWorld.slimeGrass.get(dirtType).get(FoliageType.ICHOR).asItem());
    }
  }

  private void addWorld() {
    var heads = this.tag(ItemTags.create(Identifier.parse("c:heads")));
    heads.add(Items.PIGLIN_HEAD);
    TinkerWorld.heads.forEach(head -> heads.add(head.asItem()));

    copy(TinkerTags.Blocks.SLIME_BLOCK, TinkerTags.Items.SLIME_BLOCK);
    copy(TinkerTags.Blocks.CONGEALED_SLIME, TinkerTags.Items.CONGEALED_SLIME);
    copy(TinkerTags.Blocks.SLIMY_LOGS, TinkerTags.Items.SLIMY_LOGS);
    copy(TinkerTags.Blocks.SLIMY_PLANKS, TinkerTags.Items.SLIMY_PLANKS);
    copy(TinkerTags.Blocks.SLIMY_LEAVES, TinkerTags.Items.SLIMY_LEAVES);
    copy(TinkerTags.Blocks.SLIMY_VINES, TinkerTags.Items.SLIMY_VINES);
    copy(TinkerTags.Blocks.SLIMY_SAPLINGS, TinkerTags.Items.SLIMY_SAPLINGS);
    copy(TinkerTags.Blocks.ENDERBARK_ROOTS, TinkerTags.Items.ENDERBARK_ROOTS);
    this.tag(ItemTags.LEAVES).addTag(TinkerTags.Items.SLIMY_LEAVES);
    this.tag(ItemTags.SAPLINGS).addTag(TinkerTags.Items.SLIMY_SAPLINGS);

    copy(TagKey.create(Registries.BLOCK, Identifier.parse("c:ores")), ItemTags.create(Identifier.parse("c:ores")));
    copy(TagKey.create(Registries.BLOCK, Identifier.parse("c:ore_rates/singular")), ItemTags.create(Identifier.parse("c:ore_rates/singular")));
    copy(TagKey.create(Registries.BLOCK, Identifier.parse("c:ores_in_ground/netherrack")), ItemTags.create(Identifier.parse("c:ores_in_ground/netherrack")));
    copy(TinkerTags.Blocks.ORES_COBALT, TinkerTags.Items.ORES_COBALT);
    copy(TinkerTags.Blocks.RAW_BLOCK_COBALT, TinkerTags.Items.RAW_BLOCK_COBALT);
    this.tag(TinkerTags.Items.RAW_COBALT).add(TinkerWorld.rawCobalt.get());
    this.tag(ItemTags.create(Identifier.parse("c:raw_materials"))).addTag(TinkerTags.Items.RAW_COBALT);

    // wood
    this.addNonFlammableTag(TinkerWorld.greenheart);
    this.addNonFlammableTag(TinkerWorld.skyroot);
    this.addNonFlammableTag(TinkerWorld.bloodshroom);
    this.addNonFlammableTag(TinkerWorld.enderbark);
    // planks
    this.tag(ItemTags.PLANKS).addTag(TinkerTags.Items.SLIMY_PLANKS);
    this.tag(ItemTags.WOODEN_SLABS).add(TinkerWorld.greenheart.getSlab().asItem(), TinkerWorld.skyroot.getSlab().asItem(), TinkerWorld.bloodshroom.getSlab().asItem(), TinkerWorld.enderbark.getSlab().asItem());
    this.tag(ItemTags.WOODEN_STAIRS).add(TinkerWorld.greenheart.getStairs().asItem(), TinkerWorld.skyroot.getStairs().asItem(), TinkerWorld.bloodshroom.getStairs().asItem(), TinkerWorld.enderbark.getStairs().asItem());
    // logs
    copy(TinkerWorld.greenheart.getLogBlockTag(), TinkerWorld.greenheart.getLogItemTag());
    copy(TinkerWorld.skyroot.getLogBlockTag(), TinkerWorld.skyroot.getLogItemTag());
    copy(TinkerWorld.bloodshroom.getLogBlockTag(), TinkerWorld.bloodshroom.getLogItemTag());
    copy(TinkerWorld.enderbark.getLogBlockTag(), TinkerWorld.enderbark.getLogItemTag());
    this.tag(ItemTags.LOGS).addTag(TinkerTags.Items.SLIMY_LOGS);
    // no burnable woods presently
    // doors
    this.tag(ItemTags.WOODEN_FENCES).add(TinkerWorld.greenheart.getFence().asItem(), TinkerWorld.skyroot.getFence().asItem(), TinkerWorld.bloodshroom.getFence().asItem(), TinkerWorld.enderbark.getFence().asItem());
    this.tag(ItemTags.FENCE_GATES).add(TinkerWorld.greenheart.getFenceGate().asItem(), TinkerWorld.skyroot.getFenceGate().asItem(), TinkerWorld.bloodshroom.getFenceGate().asItem(), TinkerWorld.enderbark.getFenceGate().asItem());
    copy(TagKey.create(Registries.BLOCK, Identifier.parse("c:fences/wooden")), ItemTags.create(Identifier.parse("c:fences/wooden")));
    copy(TagKey.create(Registries.BLOCK, Identifier.parse("c:fence_gates/wooden")), ItemTags.create(Identifier.parse("c:fence_gates/wooden")));
    this.tag(ItemTags.WOODEN_DOORS).add(TinkerWorld.greenheart.getDoor().asItem(), TinkerWorld.skyroot.getDoor().asItem(), TinkerWorld.bloodshroom.getDoor().asItem(), TinkerWorld.enderbark.getDoor().asItem());
    this.tag(ItemTags.WOODEN_TRAPDOORS).add(TinkerWorld.greenheart.getTrapdoor().asItem(), TinkerWorld.skyroot.getTrapdoor().asItem(), TinkerWorld.bloodshroom.getTrapdoor().asItem(), TinkerWorld.enderbark.getTrapdoor().asItem());
    // redstone
    this.tag(ItemTags.WOODEN_BUTTONS).add(TinkerWorld.greenheart.getButton().asItem(), TinkerWorld.skyroot.getButton().asItem(), TinkerWorld.bloodshroom.getButton().asItem(), TinkerWorld.enderbark.getButton().asItem());
    this.tag(ItemTags.WOODEN_PRESSURE_PLATES).add(TinkerWorld.greenheart.getPressurePlate().asItem(), TinkerWorld.skyroot.getPressurePlate().asItem(), TinkerWorld.bloodshroom.getPressurePlate().asItem(), TinkerWorld.enderbark.getPressurePlate().asItem());
    this.tag(ItemTags.SIGNS).add(TinkerWorld.greenheart.getSign().asItem(), TinkerWorld.skyroot.getSign().asItem(), TinkerWorld.bloodshroom.getSign().asItem(), TinkerWorld.enderbark.getSign().asItem());
    this.tag(ItemTags.HANGING_SIGNS).add(TinkerWorld.greenheart.getHangingSign().asItem(), TinkerWorld.skyroot.getHangingSign().asItem(), TinkerWorld.bloodshroom.getHangingSign().asItem(), TinkerWorld.enderbark.getHangingSign().asItem());
  }

  private void addTools() {
    // stone
    addToolTags(TinkerTools.pickaxe,      MULTIPART_TOOL, DURABILITY, HARVEST_PRIMARY, STONE_HARVEST, MELEE_WEAPON,  INTERACTABLE_RIGHT, AOE, CLUSTER_MAX_HARVESTABLES, SMALL_TOOLS, BONUS_SLOTS, ItemTags.PICKAXES);
    addToolTags(TinkerTools.sledgeHammer, MULTIPART_TOOL, DURABILITY, HARVEST_PRIMARY, STONE_HARVEST, MELEE_PRIMARY, INTERACTABLE_RIGHT, AOE, CLUSTER_MAX_HARVESTABLES, BROAD_TOOLS, BONUS_SLOTS, ItemTags.PICKAXES);
    addToolTags(TinkerTools.veinHammer,   MULTIPART_TOOL, DURABILITY, HARVEST_PRIMARY, STONE_HARVEST, MELEE_WEAPON,  INTERACTABLE_RIGHT, AOE, CLUSTER_MAX_HARVESTABLES, BROAD_TOOLS, BONUS_SLOTS, ItemTags.PICKAXES);
    // dirt
    addToolTags(TinkerTools.mattock,   MULTIPART_TOOL, DURABILITY, HARVEST_PRIMARY, MELEE_WEAPON, INTERACTABLE_RIGHT, AOE, SMALL_TOOLS, BONUS_SLOTS, ItemTags.SHOVELS, ItemTags.AXES);
    addToolTags(TinkerTools.pickadze,  MULTIPART_TOOL, DURABILITY, HARVEST_PRIMARY, MELEE_WEAPON, INTERACTABLE_RIGHT, AOE, SMALL_TOOLS, BONUS_SLOTS, ItemTags.SHOVELS, STONE_HARVEST, ItemTags.PICKAXES);
    addToolTags(TinkerTools.excavator, MULTIPART_TOOL, DURABILITY, HARVEST_PRIMARY, MELEE_WEAPON, INTERACTABLE_RIGHT, AOE, BROAD_TOOLS, BONUS_SLOTS, ItemTags.SHOVELS);
    // wood
    addToolTags(TinkerTools.handAxe,  MULTIPART_TOOL, DURABILITY, HARVEST_PRIMARY, MELEE_PRIMARY, INTERACTABLE_RIGHT, AOE, SMALL_TOOLS, BONUS_SLOTS, ItemTags.AXES);
    addToolTags(TinkerTools.broadAxe, MULTIPART_TOOL, DURABILITY, HARVEST_PRIMARY, MELEE_PRIMARY, INTERACTABLE_RIGHT, AOE, BROAD_TOOLS, BONUS_SLOTS, ItemTags.AXES);
    // plants
    addToolTags(TinkerTools.kama,   MULTIPART_TOOL, DURABILITY, HARVEST_PRIMARY, MELEE_WEAPON,  INTERACTABLE_RIGHT, AOE, SMALL_TOOLS, BONUS_SLOTS, ItemTags.HOES);
    addToolTags(TinkerTools.scythe, MULTIPART_TOOL, DURABILITY, HARVEST_PRIMARY, MELEE_PRIMARY, INTERACTABLE_RIGHT, AOE, BROAD_TOOLS, BONUS_SLOTS, ItemTags.HOES);
    // sword
    addToolTags(TinkerTools.dagger,  MULTIPART_TOOL, DURABILITY, HARVEST, MELEE_PRIMARY, INTERACTABLE_RIGHT, PARRY, SMALL_TOOLS, BONUS_SLOTS, ItemTags.SWORDS, UNSALVAGABLE);
    addToolTags(TinkerTools.sword,   MULTIPART_TOOL, DURABILITY, HARVEST, MELEE_PRIMARY, INTERACTABLE_RIGHT, SWORD, SMALL_TOOLS, BONUS_SLOTS, ItemTags.SWORDS, AOE);
    addToolTags(TinkerTools.cleaver, MULTIPART_TOOL, DURABILITY, HARVEST, MELEE_PRIMARY, INTERACTABLE_RIGHT, SWORD, BROAD_TOOLS, BONUS_SLOTS, ItemTags.SWORDS, AOE);
    // ranged
    addToolTags(TinkerTools.crossbow,   MULTIPART_TOOL, DURABILITY, MELEE_WEAPON, CROSSBOWS,    INTERACTABLE_LEFT,  SMALL_RANGED, BONUS_SLOTS, ItemTags.create(Identifier.parse("c:tools/crossbow")));
    addToolTags(TinkerTools.longbow,    MULTIPART_TOOL, DURABILITY, MELEE_WEAPON, LONGBOWS,     INTERACTABLE_LEFT,  BROAD_RANGED, BONUS_SLOTS, ItemTags.create(Identifier.parse("c:tools/bow")), BALLISTAS);
    addToolTags(TinkerTools.fishingRod, MULTIPART_TOOL, DURABILITY, MELEE_WEAPON, FISHING_RODS, INTERACTABLE_DUAL,  SMALL_RANGED, BONUS_SLOTS, ItemTags.create(Identifier.parse("c:tools/fishing_rod")));
    addToolTags(TinkerTools.javelin,    MULTIPART_TOOL, DURABILITY, MELEE_PRIMARY, RANGED,      INTERACTABLE_RIGHT, BROAD_RANGED, BONUS_SLOTS, ItemTags.create(Identifier.parse("c:tools/trident")));
    addToolTags(TinkerTools.arrow,       MULTIPART_TOOL, AMMO,        UNSALVAGABLE, UNSWAPPABLE, SINGLE_USE, DYEABLE, ItemTags.ARROWS);
    addToolTags(TinkerTools.shuriken,    MULTIPART_TOOL, THROWN_AMMO, UNSALVAGABLE, UNSWAPPABLE, SINGLE_USE);
    addToolTags(TinkerTools.throwingAxe, MULTIPART_TOOL, THROWN_AMMO, UNSALVAGABLE, UNSWAPPABLE, SINGLE_USE);
    // specialized
    addToolTags(TinkerTools.flintAndBrick, DURABILITY, MELEE_WEAPON, INTERACTABLE_RIGHT, AOE, SMALL_TOOLS, BONUS_SLOTS);
    addToolTags(TinkerTools.skyStaff,      DURABILITY, STAFFS, SPECIAL_TOOLS, HELD_ARMOR, INTERACTABLE_DUAL, AOE, DYEABLE, EMBELLISHMENT_WOOD, BONUS_SLOTS); // parity (oracle): no EMBELLISHMENT_SLIME, official has none
    addToolTags(TinkerTools.earthStaff,    DURABILITY, STAFFS, SPECIAL_TOOLS, HELD_ARMOR, INTERACTABLE_DUAL, AOE, DYEABLE, EMBELLISHMENT_WOOD, BONUS_SLOTS); // parity (oracle): no EMBELLISHMENT_SLIME, official has none
    addToolTags(TinkerTools.ichorStaff,    DURABILITY, STAFFS, SPECIAL_TOOLS, HELD_ARMOR, INTERACTABLE_DUAL, AOE, DYEABLE, EMBELLISHMENT_WOOD, BONUS_SLOTS); // parity (oracle): no EMBELLISHMENT_SLIME, official has none
    addToolTags(TinkerTools.enderStaff,    DURABILITY, STAFFS, SPECIAL_TOOLS, HELD_ARMOR, INTERACTABLE_DUAL, AOE, DYEABLE, EMBELLISHMENT_WOOD, BONUS_SLOTS); // parity (oracle): no EMBELLISHMENT_SLIME, official has none
    // ancient
    addToolTags(TinkerTools.meltingPan, MULTIPART_TOOL, DURABILITY, ANCIENT_TOOLS, TRADER_TOOLS, HARVEST_PRIMARY, STAFFS, HELD_ARMOR, INTERACTABLE_DUAL, AOE, BONUS_SLOTS);
    addToolTags(TinkerTools.warPick,    MULTIPART_TOOL, DURABILITY, ANCIENT_TOOLS, TRADER_TOOLS, HARVEST_PRIMARY, STONE_HARVEST, MELEE_WEAPON, HELD, AOE, CLUSTER_MAX_HARVESTABLES, CROSSBOWS, BONUS_SLOTS, ItemTags.PICKAXES, ItemTags.create(Identifier.parse("c:tools/crossbow")));
    addToolTags(TinkerTools.battlesign, MULTIPART_TOOL, DURABILITY, ANCIENT_TOOLS, TRADER_TOOLS, MELEE_PRIMARY, SHIELDS, BONUS_SLOTS, ItemTags.create(Identifier.parse("c:tools/shield")));
    addToolTags(TinkerTools.swasher,    MULTIPART_TOOL, DURABILITY, ANCIENT_TOOLS, TRADER_TOOLS, HARVEST, MELEE_PRIMARY, LAUNCHERS, HELD, BONUS_SLOTS, ItemTags.SWORDS, RANGED_POWER, RANGED_QUICK_CHARGE, RANGED_BOUNCE, INTERACTABLE_CHARGE_MODIFIER);
    // parity (oracle): official registers the minotaur axe only with Twilight Forest. The port keeps the item registered so
    // saved copies survive, and adds these same tags from resources/compat/twilightforest_minotaur_axe, a built-in data pack
    // TinkerTools enables only when Twilight Forest is loaded. Without it the axe is in no tool tag, as if absent.
    // optionalToolTags(TinkerTools.minotaurAxe, MULTIPART_TOOL, DURABILITY, ANCIENT_TOOLS, HARVEST_PRIMARY, MELEE_PRIMARY, INTERACTABLE_RIGHT, AOE, BONUS_SLOTS, ItemTags.AXES);

    // armor
    // parity (oracle): official 3.12.1 armor and shield tags. Continuum put the single part armor in
    // modifiable/multipart/single (official leaves that tag unused), marked the slime chest, legs and boots unrecyclable
    // instead of the travelers armor, and left the travelers shield out of modifiable/banner. Former lines:
    //   addArmorTags(travelersGear, SINGLEPART_TOOL, DURABILITY, BONUS_SLOTS, DYEABLE, TRIM, FREEZE_IMMUNE_WEARABLES)
    //   addArmorTags(slimesuit, DURABILITY, BONUS_SLOTS, DYEABLE, TRIM, SINGLEPART_TOOL, UNRECYCLABLE)
    //   addToolTags(slimeWings, DURABILITY, BONUS_SLOTS, DYEABLE, TRIM, SINGLEPART_TOOL, CHESTPLATES, c:armors/chestplates)
    //   addToolTags(slime helmet, SWAPPABLE_SKULLS)
    //   addToolTags(travelersShield, ..., SINGLEPART_TOOL, UNRECYCLABLE, DYEABLE); addToolTags(plateShield, ..., SINGLEPART_TOOL, UNRECYCLABLE, BANNER)
    addArmorTags(TinkerTools.travelersGear, MULTIPART_TOOL, DURABILITY, BONUS_SLOTS, DYEABLE, TRIM, UNRECYCLABLE, ItemTags.FREEZE_IMMUNE_WEARABLES);
    addArmorTags(TinkerTools.plateArmor,    MULTIPART_TOOL, DURABILITY, BONUS_SLOTS, DYEABLE, TRIM);
    addArmorTags(TinkerTools.slimesuit,     DURABILITY, BONUS_SLOTS, DYEABLE, TRIM);
    var slimeMultipart = tag(MULTIPART_TOOL);
    for (ArmorType type : ModifiableArmorMaterial.ARMOR_TYPES) {
      if (type != ArmorType.HELMET) {
        slimeMultipart.add(TinkerTools.slimesuit.get(type));
      }
    }
    addToolTags(TinkerTools.slimeWings, DURABILITY, BONUS_SLOTS, DYEABLE, TRIM, MULTIPART_TOOL, CHESTPLATES, ItemTags.create(Identifier.parse("c:armors/chestplates")), Tags.Items.ARMORS_HUMANOID);
    addToolTags(TinkerTools.slimesuit.get(ArmorType.HELMET), MULTIPART_TOOL, SWAPPABLE_SKULLS, UNRECYCLABLE);

    // shields
    addToolTags(TinkerTools.travelersShield, DURABILITY, BONUS_SLOTS, SHIELDS, INTERACTABLE_LEFT, ItemTags.create(Identifier.parse("c:tools/shield")), MULTIPART_TOOL, UNRECYCLABLE, BANNER, DYEABLE);
    addToolTags(TinkerTools.plateShield,     DURABILITY, BONUS_SLOTS, SHIELDS, INTERACTABLE_LEFT, ItemTags.create(Identifier.parse("c:tools/shield")), MULTIPART_TOOL, UNRECYCLABLE, BANNER);

    // care about order for armor in the book
    tag(BASIC_ARMOR);
    var bookArmor = tag(PUNY_ARMOR);
    for (ArmorType slotType : ModifiableArmorMaterial.ARMOR_TYPES) {
      bookArmor.add(TinkerTools.travelersGear.get(slotType));
    }
    bookArmor.add(TinkerTools.travelersShield.get());
    for (ArmorType slotType : ModifiableArmorMaterial.ARMOR_TYPES) {
      bookArmor.add(TinkerTools.plateArmor.get(slotType));
    }
    bookArmor.add(TinkerTools.plateShield.get());
    tag(MIGHTY_ARMOR);
    tag(FANTASTIC_ARMOR);
    bookArmor = tag(GADGETRY_ARMOR);
    for (ArmorType slotType : ModifiableArmorMaterial.ARMOR_TYPES) {
      bookArmor.add(TinkerTools.slimesuit.get(slotType));
    }
    bookArmor.add(TinkerTools.slimeWings.asItem());
    tag(BOOK_ARMOR).addTags(BASIC_ARMOR, PUNY_ARMOR, MIGHTY_ARMOR, FANTASTIC_ARMOR, GADGETRY_ARMOR);


    // add tags to other tags
    // harvest primary and stone harvest are both automatically harvest
    this.tag(TinkerTags.Items.HARVEST).addTags(HARVEST_PRIMARY, STONE_HARVEST);
    // melee nesting - currently most all sub-tags are held exclusive as they revolve around tool damage or having an item in hand
    this.tag(MELEE_WEAPON).addTags(MELEE_PRIMARY, SWORD, PARRY);
    this.tag(AMMO).addTag(THROWN_AMMO);
    // by default, this tag just redirects to melee weapon, but you can reconfigure it to suit your pack
    this.tag(BALLISTA_AMMO).addTags(MELEE_WEAPON, HARVEST);
    this.tag(MELEE).addTags(MELEE_WEAPON, UNARMED);
    // modifier helper tags
    this.tag(LOOT_CAPABLE_TOOL).addTags(MELEE, HARVEST, FISHING_RODS);
    this.tag(UNARMED).addTag(CHESTPLATES);
    this.tag(INTERACTABLE_RIGHT).addTags(INTERACTABLE_DUAL);
    this.tag(INTERACTABLE_LEFT).addTag(INTERACTABLE_DUAL);
    this.tag(INTERACTABLE_CHARGE_MODIFIER).addTags(INTERACTABLE_RIGHT, SHIELDS);
    this.tag(INTERACTABLE_CHARGE).addTags(INTERACTABLE_CHARGE_MODIFIER, BOWS);
    // interactable armor is mostly so some mod could disable all chestplate interactions in one swing
    this.tag(INTERACTABLE_ARMOR).addTag(CHESTPLATES);
    // left and right handed are held, but not armor
    this.tag(HELD).addTags(INTERACTABLE_RIGHT, INTERACTABLE_LEFT, HELD_ARMOR);
    this.tag(INTERACTABLE).addTags(INTERACTABLE_LEFT, INTERACTABLE_RIGHT, INTERACTABLE_ARMOR);
    this.tag(WORN_ARMOR).addTags(BOOTS, LEGGINGS, CHESTPLATES, HELMETS);
    this.tag(HELD_ARMOR).addTag(SHIELDS);
    this.tag(ARMOR).addTags(WORN_ARMOR, HELD_ARMOR);
    this.tag(TRIM).addTag(TRIM_NO_PATTERN);
    this.tag(TRIM_NO_PATTERN);
    this.tag(SKULLS).addTag(SWAPPABLE_SKULLS);
    this.tag(AOE).addTag(BOOTS); // boot walk modifiers
    this.tag(LAUNCHERS).addTags(BOWS, STAFFS, FISHING_RODS);
    this.tag(RANGED).addTags(LAUNCHERS, SMALL_RANGED, BROAD_RANGED);
    this.tag(BOWS).addTags(LONGBOWS, CROSSBOWS);
    this.tag(RANGED_POWER).addTags(LONGBOWS, STAFFS, FISHING_RODS);
    this.tag(RANGED_QUICK_CHARGE).addTags(CROSSBOWS, STAFFS, FISHING_RODS);
    this.tag(RANGED_BOUNCE).addTags(LONGBOWS, STAFFS);
    // TODO 1.21: consider dropping unsalvagable from this tag
    this.tag(UNRECYCLABLE).addTags(UNSALVAGABLE, ANCIENT_TOOLS); // ancient tools lack tool parts, but may have special override recipes to salvage
    this.tag(UNSWAPPABLE_TOOLS).addTag(UNSWAPPABLE);
    this.tag(UNSWAPPABLE_PARTS).addTag(UNSWAPPABLE);
    // headlight support
    this.tag(ItemTags.create(Identifier.fromNamespaceAndPath("headlight", "headlight_helmets"))).addTag(HELMETS);

    // general
    // parity (oracle): official only references the single part tag optionally, so it never exists unless a pack fills it
    this.tag(MULTIPART_TOOL).addOptionalTag(SINGLEPART_TOOL);
    this.tag(MODIFIABLE).addTags(MULTIPART_TOOL, DURABILITY, MELEE, HARVEST, RANGED, AMMO, AOE, HELD, BONUS_SLOTS);
    // disable parry mod on our items, we have our own modifier for that
    this.tag(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("parry", "excluded_shields"))).addTag(HELD);

    // kamas are a shear type, when broken we don't pass it to loot tables
    this.tag(ItemTags.create(Identifier.parse("c:shears"))).add(TinkerTools.kama.get());
    // mark kama and scythe for mods like thermal to use
    this.tag(TinkerTags.Items.SCYTHES).add(TinkerTools.kama.get(), TinkerTools.scythe.get());
    // nothing to blacklist, just want the empty tag so it appears in datapacks
    this.tag(TinkerTags.Items.AUTOSMELT_BLACKLIST);
    this.tag(TinkerTags.Items.AUTOSMELT_PLUS_BLACKLIST);

    // carrots and potatoes are not seeds in vanilla, so make a tag with them
    this.tag(TinkerTags.Items.SEEDS)
        .addOptionalTag(TagKey.create(Registries.ITEM, Identifier.parse("c:seeds")))
        .add(Items.CARROT, Items.POTATO, Items.NETHER_WART, Items.SWEET_BERRIES);

    // tags for modifiers
    copy(TinkerTags.Blocks.CHRYSOPHILITE_ORES, TinkerTags.Items.CHRYSOPHILITE_ORES);

    // tag for tool parts, mostly used by JEI right now
    this.tag(TinkerTags.Items.TOOL_PARTS).add(
      // arrow part bartering is weird as they have such low tiers
      TinkerToolParts.arrowHead.get(), TinkerToolParts.arrowShaft.get(), TinkerToolParts.fletching.get(),
      // slimesuit parts are pretty niche, not much you can do with them
      TinkerToolParts.ribcage.get(), TinkerToolParts.shell.get(), TinkerToolParts.laces.get(),
      // repair kit is not strictly a tool part, but this list just helps out JEI
      TinkerToolParts.repairKit.get(), TinkerToolParts.fakeIngot.get(), TinkerToolParts.fakeStorageBlock.asItem()
    ).addTag(TinkerTags.Items.BARTERED_PARTS); // all bartered parts must be tool parts
    this.tag(TinkerTags.Items.BARTERED_PARTS)
        .add(
          TinkerToolParts.pickHead.get(), TinkerToolParts.hammerHead.get(),
          TinkerToolParts.smallAxeHead.get(), TinkerToolParts.broadAxeHead.get(),
          TinkerToolParts.smallBlade.get(), TinkerToolParts.broadBlade.get(),
          TinkerToolParts.adzeHead.get(), TinkerToolParts.largePlate.get(),
          TinkerToolParts.toolBinding.get(), TinkerToolParts.toughBinding.get(),
          TinkerToolParts.toolHandle.get(), TinkerToolParts.toughHandle.get(),
          TinkerToolParts.bowLimb.get(), TinkerToolParts.bowGrip.get(), TinkerToolParts.bowstring.get(),
          TinkerToolParts.maille.get(), TinkerToolParts.shieldCore.get())
        .add(TinkerToolParts.plating.values().toArray(new Item[0]));
    // tag for the part chest items
    this.tag(TinkerTags.Items.CHEST_PARTS).addTag(TinkerTags.Items.TOOL_PARTS).add(TinkerSmeltery.dummyPlating.values().toArray(new Item[0]));

    var slimySeeds = this.tag(TinkerTags.Items.SLIMY_SEEDS);
    TinkerWorld.slimeGrassSeeds.values().forEach(slimySeeds::add);

    // contains any ground stones
    this.tag(TinkerTags.Items.STONESHIELDS)
        .addOptionalTag(TagKey.create(Registries.ITEM, Identifier.parse("c:stone")))
        .addOptionalTag(TagKey.create(Registries.ITEM, Identifier.parse("c:cobblestone")))
        .addOptionalTag(TagKey.create(Registries.ITEM, Identifier.parse("c:sandstone")))
        .addOptionalTag(TagKey.create(Registries.ITEM, Identifier.parse("c:end_stones")))
        .addOptionalTag(TagKey.create(Registries.ITEM, Identifier.parse("c:gravel"))) // for shovels and axes to use
        .add(Items.NETHERRACK, Items.BASALT, Items.POLISHED_BASALT, Items.BLACKSTONE, Items.POLISHED_BLACKSTONE);
    this.tag(TinkerTags.Items.FIREBALLS).add(Items.FIRE_CHARGE);
    this.tag(TinkerTags.Items.TOOL_INVENTORY_BLACKLIST)
        .add(Items.BUNDLE, Items.SHULKER_BOX,
             Items.WHITE_SHULKER_BOX, Items.ORANGE_SHULKER_BOX, Items.MAGENTA_SHULKER_BOX, Items.LIGHT_BLUE_SHULKER_BOX,
             Items.YELLOW_SHULKER_BOX, Items.LIME_SHULKER_BOX, Items.PINK_SHULKER_BOX, Items.GRAY_SHULKER_BOX,
             Items.LIGHT_GRAY_SHULKER_BOX, Items.CYAN_SHULKER_BOX, Items.PURPLE_SHULKER_BOX, Items.BLUE_SHULKER_BOX,
             Items.BROWN_SHULKER_BOX, Items.GREEN_SHULKER_BOX, Items.RED_SHULKER_BOX, Items.BLACK_SHULKER_BOX);
    this.tag(TinkerTags.Items.THROWABLE)
      .add(Items.SNOWBALL, Items.EGG, Items.ENDER_PEARL, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.EXPERIENCE_BOTTLE, Items.ENDER_EYE, Items.FIREWORK_ROCKET)
      .add(TinkerGadgets.efln.get(), TinkerGadgets.flintShuriken.get(), TinkerGadgets.quartzShuriken.get(), TinkerGadgets.glowBall.get())
      .addTag(THROWN_AMMO);
    this.tag(TinkerTags.Items.WHITESTONE_INGOTS)
      .addOptionalTag(TagKey.create(Registries.ITEM, commonResource("ingots/aluminum")))
      .addOptionalTag(TagKey.create(Registries.ITEM, commonResource("ingots/tin")))
      .addOptionalTag(TagKey.create(Registries.ITEM, commonResource("ingots/zinc")))
      .addOptionalTag(TagKey.create(Registries.ITEM, commonResource("ingots/nickel")))
      .addOptionalTag(TagKey.create(Registries.ITEM, commonResource("ingots/chromium")))
      .addOptionalTag(TagKey.create(Registries.ITEM, commonResource("ingots/cadmium")));

    this.tag(TinkerTags.Items.VARIANT_PLANKS)
        .add(Items.CRIMSON_PLANKS, Items.WARPED_PLANKS)
        .addTag(TinkerTags.Items.SLIMY_PLANKS);
    // the logs have "variants" as they have their own recipes
    this.tag(TinkerTags.Items.VARIANT_LOGS).addTags(ItemTags.OAK_LOGS, ItemTags.SPRUCE_LOGS, ItemTags.BIRCH_LOGS, ItemTags.JUNGLE_LOGS, ItemTags.DARK_OAK_LOGS, ItemTags.ACACIA_LOGS, ItemTags.MANGROVE_LOGS, ItemTags.CHERRY_LOGS, ItemTags.CRIMSON_STEMS, ItemTags.WARPED_STEMS, TinkerTags.Items.SLIMY_LOGS);

    // part builder
    this.tag(TinkerTags.Items.DEFAULT_PATTERNS).add(TinkerTables.pattern.get());
    this.tag(TinkerTags.Items.REUSABLE_PATTERNS).addTag(TinkerTags.Items.GOLD_CASTS);
    this.tag(TinkerTags.Items.PATTERNS)
        .addTags(TinkerTags.Items.DEFAULT_PATTERNS, TinkerTags.Items.REUSABLE_PATTERNS, TinkerTags.Items.SAND_CASTS, TinkerTags.Items.RED_SAND_CASTS)
        .add(Items.SAND, Items.RED_SAND, TinkerFluids.venomBottle.get());

    // stone
    copy(TinkerTags.Blocks.STONE,      TinkerTags.Items.STONE);
    copy(TinkerTags.Blocks.GRANITE,    TinkerTags.Items.GRANITE);
    copy(TinkerTags.Blocks.DIORITE,    TinkerTags.Items.DIORITE);
    copy(TinkerTags.Blocks.ANDESITE,   TinkerTags.Items.ANDESITE);
    copy(TinkerTags.Blocks.BLACKSTONE, TinkerTags.Items.BLACKSTONE);
    copy(TinkerTags.Blocks.DEEPSLATE,  TinkerTags.Items.DEEPSLATE);
    copy(TinkerTags.Blocks.BASALT,     TinkerTags.Items.BASALT);

    // twilight forest
    this.tag(BANNED_UNCRAFTABLE).addTag(MODIFIABLE).addTag(TOOL_PARTS).add(
      TinkerTables.tinkersAnvil.asItem(), TinkerTables.scorchedAnvil.asItem(), TinkerTables.modifierWorktable.asItem()
    );
    String tf = "twilightforest";
    Function<String,Identifier> trophy = name -> Identifier.fromNamespaceAndPath(tf, name + "_trophy");
    this.tag(TinkerTags.Items.BOSS_TROPHIES)
      .addOptional(ResourceKey.create(Registries.ITEM, trophy.apply("naga")))
      .addOptional(ResourceKey.create(Registries.ITEM, trophy.apply("lich")))
      .addOptional(ResourceKey.create(Registries.ITEM, trophy.apply("minoshroom")))
      .addOptional(ResourceKey.create(Registries.ITEM, trophy.apply("hydra")))
      .addOptional(ResourceKey.create(Registries.ITEM, trophy.apply("knight_phantom")))
      .addOptional(ResourceKey.create(Registries.ITEM, trophy.apply("ur_ghast")))
      .addOptional(ResourceKey.create(Registries.ITEM, trophy.apply("alpha_yeti")))
      .addOptional(ResourceKey.create(Registries.ITEM, trophy.apply("snow_queen")))
      .addOptional(ResourceKey.create(Registries.ITEM, trophy.apply("quest_ram")));
    this.tag(TinkerTags.Items.THROWABLE)
      .addOptional(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(tf, "ice_bomb")));
    this.tag(TinkerTags.Items.KNIGHTMETAL_SHARD).addOptional(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(tf, "armor_shard")));
  }

  private void addSmeltery() {
    copy(TinkerTags.Blocks.SEARED_BRICKS, TinkerTags.Items.SEARED_BRICKS);
    copy(TinkerTags.Blocks.SEARED_BLOCKS, TinkerTags.Items.SEARED_BLOCKS);
    copy(TinkerTags.Blocks.SMELTERY_BRICKS, TinkerTags.Items.SMELTERY_BRICKS);
    copy(TinkerTags.Blocks.SCORCHED_BLOCKS, TinkerTags.Items.SCORCHED_BLOCKS);
    copy(TinkerTags.Blocks.FOUNDRY_BRICKS, TinkerTags.Items.FOUNDRY_BRICKS);
    this.tag(ItemTags.SOUL_FIRE_BASE_BLOCKS).add(TinkerCommons.soulGlass.asItem(), TinkerSmeltery.searedSoulGlass.asItem(), TinkerSmeltery.scorchedSoulGlass.asItem());

    this.tag(TinkerTags.Items.NON_SINGULAR_ORE_RATES).addOptionalTag(TagKey.create(Registries.ITEM, Identifier.parse("c:ore_rates/dense"))).addOptionalTag(TagKey.create(Registries.ITEM, Identifier.parse("c:ore_rates/sparse")));

    // smeltery and foundry structure blocks
    this.tag(TinkerTags.Items.SMELTERY)
        .addTag(TinkerTags.Items.SEARED_BLOCKS)
        .addTag(TinkerTags.Items.SEARED_TANKS)
        .add(TinkerSmeltery.smelteryController.asItem(), TinkerSmeltery.searedLadder.asItem(),
             TinkerSmeltery.searedDrain.asItem(), TinkerSmeltery.searedChute.asItem(), TinkerSmeltery.searedDuct.asItem(),
             TinkerSmeltery.searedGlass.asItem(), TinkerSmeltery.searedSoulGlass.asItem(), TinkerSmeltery.searedTintedGlass.asItem());
    this.tag(TinkerTags.Items.FOUNDRY)
        .addTag(TinkerTags.Items.SCORCHED_BLOCKS)
        .addTag(TinkerTags.Items.SCORCHED_TANKS)
        .add(TinkerSmeltery.foundryController.asItem(), TinkerSmeltery.scorchedLadder.asItem(),
             TinkerSmeltery.scorchedDrain.asItem(), TinkerSmeltery.scorchedChute.asItem(), TinkerSmeltery.scorchedDuct.asItem(),
             TinkerSmeltery.scorchedGlass.asItem(), TinkerSmeltery.scorchedSoulGlass.asItem(), TinkerSmeltery.scorchedTintedGlass.asItem());
    // structure debug
    this.tag(TinkerTags.Items.GENERAL_STRUCTURE_DEBUG);
    this.tag(TinkerTags.Items.SMELTERY_DEBUG).addTag(TinkerTags.Items.GENERAL_STRUCTURE_DEBUG).addTag(TinkerTags.Items.SMELTERY);
    this.tag(TinkerTags.Items.FOUNDRY_DEBUG).addTag(TinkerTags.Items.GENERAL_STRUCTURE_DEBUG).addTag(TinkerTags.Items.FOUNDRY);

    // tag each type of cast
    var goldCasts = this.tag(TinkerTags.Items.GOLD_CASTS);
    var sandCasts = this.tag(TinkerTags.Items.SAND_CASTS);
    var redSandCasts = this.tag(TinkerTags.Items.RED_SAND_CASTS);
    var singleUseCasts = this.tag(TinkerTags.Items.SINGLE_USE_CASTS);
    var multiUseCasts = this.tag(TinkerTags.Items.MULTI_USE_CASTS);
    Consumer<CastItemObject> addCast = cast -> {
      // tag based on material
      goldCasts.add(cast.get());
      sandCasts.add(cast.getSand());
      redSandCasts.add(cast.getRedSand());
      // tag based on usage
      singleUseCasts.addTag(cast.getSingleUseTag());
      this.tag(cast.getSingleUseTag()).add(cast.getSand(), cast.getRedSand());
      multiUseCasts.addTag(cast.getMultiUseTag());
      this.tag(cast.getMultiUseTag()).add(cast.get());
    };
    // blank sand casts, no blank gold or this would use the helper
    sandCasts.add(TinkerSmeltery.blankSandCast.get());
    redSandCasts.add(TinkerSmeltery.blankRedSandCast.get());
    singleUseCasts.addTag(TinkerTags.Items.BLANK_SINGLE_USE_CASTS);
    this.tag(TinkerTags.Items.BLANK_SINGLE_USE_CASTS).add(TinkerSmeltery.blankSandCast.get(), TinkerSmeltery.blankRedSandCast.get());
    // basic
    addCast.accept(TinkerSmeltery.ingotCast);
    addCast.accept(TinkerSmeltery.nuggetCast);
    addCast.accept(TinkerSmeltery.gemCast);
    addCast.accept(TinkerSmeltery.rodCast);
    addCast.accept(TinkerSmeltery.repairKitCast);
    // compatibility
    addCast.accept(TinkerSmeltery.plateCast);
    addCast.accept(TinkerSmeltery.gearCast);
    addCast.accept(TinkerSmeltery.coinCast);
    addCast.accept(TinkerSmeltery.wireCast);
    // small heads
    addCast.accept(TinkerSmeltery.pickHeadCast);
    addCast.accept(TinkerSmeltery.smallAxeHeadCast);
    addCast.accept(TinkerSmeltery.smallBladeCast);
    addCast.accept(TinkerSmeltery.adzeHeadCast);
    // large heads
    addCast.accept(TinkerSmeltery.hammerHeadCast);
    addCast.accept(TinkerSmeltery.broadAxeHeadCast);
    addCast.accept(TinkerSmeltery.broadBladeCast);
    addCast.accept(TinkerSmeltery.largePlateCast);
    // bindings
    addCast.accept(TinkerSmeltery.toolBindingCast);
    addCast.accept(TinkerSmeltery.toughBindingCast);
    // tool rods
    addCast.accept(TinkerSmeltery.toolHandleCast);
    addCast.accept(TinkerSmeltery.toughHandleCast);
    // bow
    addCast.accept(TinkerSmeltery.bowLimbCast);
    addCast.accept(TinkerSmeltery.bowGripCast);
    // armor
    addCast.accept(TinkerSmeltery.helmetPlatingCast);
    addCast.accept(TinkerSmeltery.chestplatePlatingCast);
    addCast.accept(TinkerSmeltery.leggingsPlatingCast);
    addCast.accept(TinkerSmeltery.bootsPlatingCast);
    addCast.accept(TinkerSmeltery.mailleCast);

    // arrow patterns are basically a gold cast
    goldCasts.add(TinkerSmeltery.arrowCast.get());

    // add all casts to a common tag
    this.tag(TinkerTags.Items.CASTS)
        .addTags(TinkerTags.Items.GOLD_CASTS, TinkerTags.Items.SAND_CASTS, TinkerTags.Items.RED_SAND_CASTS, TinkerTags.Items.TABLE_EMPTY_CASTS, TinkerTags.Items.BASIN_EMPTY_CASTS);
    this.tag(TinkerTags.Items.TABLE_EMPTY_CASTS).add(TinkerCommons.goldBars.asItem());
    this.tag(TinkerTags.Items.BASIN_EMPTY_CASTS).add(TinkerCommons.goldPlatform.asItem());

    this.tag(TinkerTags.Items.DUCT_CONTAINERS).add(Items.BUCKET, TinkerSmeltery.copperCan.get(), TinkerSmeltery.searedLantern.asItem(), TinkerSmeltery.scorchedLantern.asItem());
    // parity/rei: example solid fuels for the recipe viewer fuel category, same as official 3.12.1
    this.tag(TinkerTags.Items.FUEL_EXAMPLES).add(Items.COAL, Items.CHARCOAL, Items.COAL_BLOCK, Items.OAK_LOG, Items.OAK_PLANKS, Items.BLAZE_ROD);

    // tank tag
    copy(TinkerTags.Blocks.SEARED_TANKS, TinkerTags.Items.SEARED_TANKS);
    copy(TinkerTags.Blocks.SCORCHED_TANKS, TinkerTags.Items.SCORCHED_TANKS);
    this.tag(TinkerTags.Items.TANKS)
        .addTag(TinkerTags.Items.SEARED_TANKS)
        .addTag(TinkerTags.Items.SCORCHED_TANKS);

    // blacklist for proxy tank - mostly to encourage you to use the better suited casting tank
    this.tag(TinkerTags.Items.PROXY_TANK_BLACKLIST)
      .add(Items.BUCKET, Items.GLASS_BOTTLE, Items.BOWL, TinkerSmeltery.copperCan.get())
      .addTag(TinkerTags.Items.AMMO); // ammo has exact size tanks, unlike other modifiable items that have variable sized

    // melting tags //
    // ores
    Function<String,Identifier> ie = path -> Identifier.fromNamespaceAndPath("immersiveengineering", path);
    String tf = "twilightforest";
    moltenTools(TinkerFluids.moltenCopper).add(1, Items.BRUSH).toolTags().toolsComplement();
    moltenTools(TinkerFluids.moltenIron).minecraft()
      .add(1, Items.FLINT_AND_STEEL, Items.SHIELD).fdKnife()
      .add(2, Items.SHEARS)
      .add(2, true, ie.apply("hammer"))
      .crowbar().excavatorSpikeMaul();
    moltenTools(TinkerFluids.moltenGold).minecraft("golden")
      .add(1, true,  Identifier.fromNamespaceAndPath("farmersdelight", "golden_knife"))
      .add(4, false, Identifier.parse("golden_boots"))
      .add(4, true,  Identifier.fromNamespaceAndPath(tf, "gold_minotaur_axe"));
    moltenTools(TinkerFluids.moltenSteel).toolTags().leggingsPaxel().crowbar()
      .toolTag(1, "shovel")
      .add(1, true, ie.apply("shovel_steel"))
      .add(2, true, ie.apply("sword_steel")).add(2, true, ie.apply("hoe_steel"))
      .add(3, true, ie.apply("axe_steel")).add(3, true, ie.apply("pickaxe_steel"))
      .armorTag(5, "helmets"    ).add(5, true, ie.apply("armor_steel_helmet"))
      .armorTag(8, "chestplates").add(8, true, ie.apply("armor_steel_chestplate"))
                                              .add(7, true, ie.apply("armor_steel_leggings"))
      .armorTag(4, "boots"      ).add(4, true, ie.apply("armor_steel_boots"));
    moltenTools(TinkerFluids.moltenNetherite).minecraft().fdKnife();
    moltenTools(TinkerFluids.moltenKnightmetal)
      .optionalMetal(3, tf, "axe", "pickaxe")
      .optionalMetal(7, tf, "leggings", "shield");
    // gems
    moltenTools(TinkerFluids.moltenDiamond).minecraft().excavatorSpikeMaul().crowbar().fdKnife()
      .add(4, false, Identifier.parse("diamond_boots"))
      .add(4, true,  Identifier.fromNamespaceAndPath(tf, "diamond_minotaur_axe"));
    // mod ores
    moltenTools(TinkerFluids.moltenTin).toolTags().toolsComplement();
    moltenTools(TinkerFluids.moltenLead).toolTags().toolsComplement();
    moltenTools(TinkerFluids.moltenSilver).toolTags().toolsComplement();
    moltenTools(TinkerFluids.moltenNickel).toolTags().toolsComplement();
    moltenTools(TinkerFluids.moltenOsmium).toolTags().leggingsPaxel();
    // mod alloys
    moltenTools(TinkerFluids.moltenBronze).toolTags().toolsComplement().leggingsPaxel();
    moltenTools(TinkerFluids.moltenElectrum).toolTags().toolsComplement();
    moltenTools(TinkerFluids.moltenInvar).toolTags().toolsComplement();
    moltenTools(TinkerFluids.moltenConstantan).toolTags().toolsComplement();
    // special alloys
    moltenTools(TinkerFluids.moltenRefinedGlowstone).toolTags().leggingsPaxel();
    moltenTools(TinkerFluids.moltenRefinedObsidian).toolTags().leggingsPaxel();
    // twilight forest
    CostTagAppender.moltenToolMelting(TConstruct.MOD_ID, "ironwood", MAKE_TAG)
      .optionalMetal(2, tf, "sword", "hoe")
      .optionalMetal(3, tf, "axe", "pickaxe");
    moltenTools(TinkerFluids.moltenSteeleaf)
      .optionalMetal(2, tf, "sword", "hoe")
      .optionalMetal(3, tf, "axe", "pickaxe");
  }

  @Override
  public String getName() {
    return "Continuum Construct Item Tags";
  }


  /** Adds the non-flammable wood tag to all relevant wood in the object */
  private void addNonFlammableTag(WoodBlockObject object) {
    this.tag(ItemTags.NON_FLAMMABLE_WOOD)
        .add(object.asItem(), object.getSlab().asItem(), object.getStairs().asItem(),
             object.getFence().asItem(), object.getFenceGate().asItem(), object.getDoor().asItem(), object.getTrapdoor().asItem(),
             object.getPressurePlate().asItem(), object.getButton().asItem())
        .addTag(object.getLogItemTag());
  }

  /**
   * Adds relevant tags for a metal object
   * @param metal  Metal object
   */
  private void addMetalTags(MetalItemObject metal) {
    this.tag(metal.getIngotTag()).add(metal.getIngot());
    this.tag(ItemTags.create(Identifier.parse("c:ingots"))).addTag(metal.getIngotTag());
    this.tag(metal.getNuggetTag()).add(metal.getNugget());
    this.tag(ItemTags.create(Identifier.parse("c:nuggets"))).addTag(metal.getNuggetTag());
    copy(metal.getBlockTag(), metal.getBlockItemTag());
  }

  @SafeVarargs
  private void addToolTags(ItemLike tool, TagKey<Item>... tags) {
    Item item = tool.asItem();
    for (TagKey<Item> tag : tags) {
      this.tag(tag).add(item);
    }
  }

  @SafeVarargs
  private void optionalToolTags(IdAwareObject tool, TagKey<Item>... tags) {
    Identifier id = tool.getId();
    for (TagKey<Item> tag : tags) {
      this.tag(tag).addOptional(ResourceKey.create(Registries.ITEM, id));
    }
  }

  private TagKey<Item> getArmorTag(ArmorType slotType) {
    return switch (slotType) {
      case BOOTS -> BOOTS;
      case LEGGINGS -> LEGGINGS;
      case CHESTPLATE -> CHESTPLATES;
      case HELMET -> HELMETS;
      default -> HELMETS;
    };
  }

  private TagKey<Item> getCommonArmorTag(ArmorType slotType) {
    return switch (slotType) {
      case BOOTS -> ItemTags.create(Identifier.parse("c:armors/boots"));
      case LEGGINGS -> ItemTags.create(Identifier.parse("c:armors/leggings"));
      case CHESTPLATE -> ItemTags.create(Identifier.parse("c:armors/chestplates"));
      case HELMET -> ItemTags.create(Identifier.parse("c:armors/helmets"));
      default -> ItemTags.create(Identifier.parse("c:armors/helmets"));
    };
  }

  @SafeVarargs
  private void addArmorTags(EnumObject<ArmorType,? extends Item> armor, TagKey<Item>... tags) {
    armor.forEach((type, item) -> {
      for (TagKey<Item> tag : tags) {
        this.tag(tag).add(item);
      }
      this.tag(getArmorTag(type)).add(item);
      this.tag(getCommonArmorTag(type)).add(item);
      // parity (oracle): NeoForge 26.1 builds c:armors from c:armors/humanoid, which only reads the vanilla slot tags
      this.tag(Tags.Items.ARMORS_HUMANOID).add(item);
    });
  }

  /** Creates a builder for a melting tag with a molten fluid */
  protected CostTagAppender moltenTools(FluidObject<?> fluid) {
    return CostTagAppender.moltenToolMelting(fluid, MAKE_TAG);
  }

  private ItemTagAppender tag(TagKey<Item> key) {
    return new ItemTagAppender(rawTag(key));
  }

  private TagAppender<ResourceKey<Item>, Item> rawTag(TagKey<Item> key) {
    return TagAppender.forBuilder(getOrCreateRawBuilder(key));
  }

  private static ResourceKey<Item> key(Item item) {
    return BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow();
  }

  private void copy(TagKey<Block> source, TagKey<Item> dest) {
    TagBuilder destBuilder = getOrCreateRawBuilder(dest);
    blockTags.join().apply(source).ifPresentOrElse(
      builder -> builder.build().forEach(destBuilder::add),
      () -> destBuilder.addTag(source.location())
    );
  }

  private static final class ItemTagAppender implements Consumer<Item> {
    private final TagAppender<ResourceKey<Item>, Item> delegate;

    private ItemTagAppender(TagAppender<ResourceKey<Item>, Item> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void accept(Item item) {
      add(item);
    }

    public ItemTagAppender add(ItemLike item) {
      delegate.add(key(item.asItem()));
      return this;
    }

    public ItemTagAppender add(ItemLike... items) {
      for (ItemLike item : items) {
        delegate.add(key(item.asItem()));
      }
      return this;
    }

    @SafeVarargs
    public final ItemTagAppender add(ResourceKey<Item>... keys) {
      delegate.add(keys);
      return this;
    }

    public ItemTagAppender add(Object first, Object second, Object... rest) {
      addAny(first);
      addAny(second);
      for (Object value : rest) {
        addAny(value);
      }
      return this;
    }

    @SuppressWarnings("unchecked")
    private void addAny(Object value) {
      if (value instanceof ItemLike item) {
        delegate.add(key(item.asItem()));
      } else if (value instanceof ResourceKey<?> key) {
        delegate.add((ResourceKey<Item>)key);
      } else if (value instanceof TagEntry entry) {
        delegate.add(entry);
      } else {
        throw new IllegalArgumentException("Unsupported item tag entry " + value);
      }
    }

    public ItemTagAppender add(TagEntry entry) {
      delegate.add(entry);
      return this;
    }

    @SafeVarargs
    public final ItemTagAppender addTags(TagKey<Item>... tags) {
      delegate.addTags(tags);
      return this;
    }

    public ItemTagAppender addTag(TagKey<Item> tag) {
      delegate.addTag(tag);
      return this;
    }

    public ItemTagAppender addOptional(ResourceKey<Item> key) {
      delegate.addOptional(key);
      return this;
    }

    public ItemTagAppender addOptionalTag(TagKey<Item> tag) {
      delegate.addOptionalTag(tag);
      return this;
    }
  }
}
