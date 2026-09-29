package slimeknights.tconstruct.gadgets;

import net.minecraft.data.DataGenerator;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters;
import net.minecraft.world.item.CreativeModeTab.Output;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import slimeknights.mantle.registration.object.EnumObject;
import slimeknights.mantle.registration.object.ItemObject;
import slimeknights.tconstruct.common.TinkerModule;
import slimeknights.tconstruct.gadgets.block.FoodCakeBlock;
import slimeknights.tconstruct.gadgets.block.FoodCakeBlock.EffectCombination;
import slimeknights.tconstruct.gadgets.block.InvertedCakeBlock;
import slimeknights.tconstruct.gadgets.block.PunjiBlock;
import slimeknights.tconstruct.gadgets.capability.PiggybackCapability;
import slimeknights.tconstruct.gadgets.data.GadgetRecipeProvider;
import slimeknights.tconstruct.gadgets.entity.DispenseFancyArmorStand;
import slimeknights.tconstruct.gadgets.entity.EFLNEntity;
import slimeknights.tconstruct.gadgets.entity.FancyArmorStandEntity;
import slimeknights.tconstruct.gadgets.entity.FancyArmorStandEntity.StandType;
import slimeknights.tconstruct.gadgets.entity.FancyItemFrameEntity;
import slimeknights.tconstruct.gadgets.entity.FrameType;
import slimeknights.tconstruct.gadgets.entity.GlowballEntity;
import slimeknights.tconstruct.gadgets.entity.shuriken.FlintShurikenEntity;
import slimeknights.tconstruct.gadgets.entity.shuriken.QuartzShurikenEntity;
import slimeknights.tconstruct.gadgets.item.EFLNItem;
import slimeknights.tconstruct.gadgets.item.FancyArmorStandItem;
import slimeknights.tconstruct.gadgets.item.FancyItemFrameItem;
import slimeknights.tconstruct.gadgets.item.GlowBallItem;
import slimeknights.tconstruct.gadgets.item.PiggyBackPackItem;
import slimeknights.tconstruct.gadgets.item.PiggyBackPackItem.CarryPotionEffect;
import slimeknights.tconstruct.gadgets.item.ShootProjectileDispenserBehavior;
import slimeknights.tconstruct.gadgets.item.ShurikenItem;
import slimeknights.tconstruct.shared.TinkerFood;
import slimeknights.tconstruct.world.block.FoliageType;

/**
 * Contains any special tools unrelated to the base tools.
 * TODO: consider merging this into commons, the distinction of what is a gadget is getting pretty narrow.
 */
@SuppressWarnings("unused")
public final class TinkerGadgets extends TinkerModule {
  /* Block base properties */

  /*
   * Blocks
   */
  public static final ItemObject<PunjiBlock> punji = BLOCKS.register("punji", () -> new PunjiBlock(builder(MapColor.PLANT, SoundType.GRASS).strength(3.0F).speedFactor(0.4F).noOcclusion().pushReaction(PushReaction.DESTROY)), TOOLTIP_BLOCK_ITEM);

  /*
   * Items
   */
  public static final ItemObject<PiggyBackPackItem> piggyBackpack = ITEMS.register("piggy_backpack", () -> new PiggyBackPackItem(itemProps().stacksTo(16).equippable(EquipmentSlot.CHEST)));
  public static final EnumObject<FrameType,FancyItemFrameItem> itemFrame = ITEMS.registerEnum(FrameType.values(), "item_frame", (type) -> new FancyItemFrameItem(itemProps(), (world, pos, dir) -> new FancyItemFrameEntity(world, pos, dir, type)));
  public static final EnumObject<StandType,FancyArmorStandItem> armorStand = ITEMS.registerEnum(StandType.values(), "armor_stand", type -> new FancyArmorStandItem(itemProps().stacksTo(16), type));
  public static final EnumObject<FrameType,Block> itemFrameModel = BLOCKS.registerEnumNoItem(FrameType.values(), "item_frame_model", type -> new Block(builder(SoundType.GLASS).noOcclusion()));
  public static final EnumObject<FrameType,Block> itemFrameMapModel = BLOCKS.registerEnumNoItem(FrameType.values(), "item_frame_map_model", type -> new Block(builder(SoundType.GLASS).noOcclusion()));

  // throwballs
  @Deprecated
  public static final ItemObject<GlowBallItem> glowBall;
  @Deprecated
  public static final ItemObject<EFLNItem> efln;
  @Deprecated
  public static final ItemObject<ShurikenItem> quartzShuriken, flintShuriken;
  static {

    glowBall = ITEMS.register("glow_ball", () -> new GlowBallItem(itemProps().stacksTo(16)));
    efln = ITEMS.register("efln_ball", () -> new EFLNItem(itemProps().stacksTo(16)));
    quartzShuriken = ITEMS.register("quartz_shuriken", () -> new ShurikenItem(itemProps().stacksTo(16), QuartzShurikenEntity::new));
    flintShuriken = ITEMS.register("flint_shuriken", () -> new ShurikenItem(itemProps().stacksTo(16), FlintShurikenEntity::new));

  }

  // foods
  public static final EnumObject<FoliageType,FoodCakeBlock> cake;
  public static final ItemObject<FoodCakeBlock> magmaCake;
  static {

    cake = BLOCKS.registerEnum(FoliageType.values(), "cake", type -> {
      if (type == FoliageType.ICHOR) {
        return new InvertedCakeBlock(cakeProps(), TinkerFood.ICHOR_CAKE, EffectCombination.BLOCK, java.util.List.of());
      }
      return new FoodCakeBlock(cakeProps(), TinkerFood.getCake(type), type == FoliageType.ENDER ? EffectCombination.ADD : EffectCombination.BLOCK, java.util.List.of());
    }, UNSTACKABLE_BLOCK_ITEM);
    magmaCake = BLOCKS.register("magma_cake", () -> new FoodCakeBlock(cakeProps(), TinkerFood.MAGMA_CAKE, EffectCombination.BLOCK, java.util.List.of()), UNSTACKABLE_BLOCK_ITEM);
  }

  private static BlockBehaviour.Properties cakeProps() {
    return builder(SoundType.WOOL).forceSolidOn().strength(0.5F).sound(SoundType.WOOL).pushReaction(PushReaction.DESTROY);
  }

  // Shurikens
  @Deprecated
  public static final DeferredHolder<EntityType<?>, EntityType<FancyItemFrameEntity>> itemFrameEntity = ENTITIES.register("item_frame", () ->
    EntityType.Builder.<FancyItemFrameEntity>of(FancyItemFrameEntity::new, MobCategory.MISC)
                      .noLootTable()
                      .sized(0.5F, 0.5F)
                      .clientTrackingRange(10)
                      .updateInterval(Integer.MAX_VALUE));
  public static final DeferredHolder<EntityType<?>, EntityType<FancyArmorStandEntity>> armorStandEntity = ENTITIES.register("armor_stand", () ->
    // Official 3.12.1 gives the fancy stand the (empty) tconstruct:entities/armor_stand loot table, like the
    // vanilla stand; drops come from brokenByPlayer/brokenByAnything and the table stays datapack-overridable.
    // The port had .noLootTable() here, which skipped the table lookup entirely.
    EntityType.Builder.of(FancyArmorStandEntity::new, MobCategory.MISC)
                      .sized(0.5F, 1.975F)
                      .clientTrackingRange(10));
  @Deprecated
  public static final DeferredHolder<EntityType<?>, EntityType<GlowballEntity>> glowBallEntity = ENTITIES.register("glow_ball", () ->
    EntityType.Builder.<GlowballEntity>of(GlowballEntity::new, MobCategory.MISC)
                      .noLootTable()
                      .sized(0.25F, 0.25F)
                      .clientTrackingRange(4)
                      .updateInterval(10));
  @Deprecated
  public static final DeferredHolder<EntityType<?>, EntityType<EFLNEntity>> eflnEntity = ENTITIES.register("efln_ball", () ->
    EntityType.Builder.<EFLNEntity>of(EFLNEntity::new, MobCategory.MISC)
                      .noLootTable()
                      .sized(0.25F, 0.25F)
                      .clientTrackingRange(4)
                      .updateInterval(10));
  @Deprecated
  public static final DeferredHolder<EntityType<?>, EntityType<QuartzShurikenEntity>> quartzShurikenEntity = ENTITIES.register("quartz_shuriken", () ->
    EntityType.Builder.<QuartzShurikenEntity>of(QuartzShurikenEntity::new, MobCategory.MISC)
                      .noLootTable()
                      .sized(0.25F, 0.25F)
                      .clientTrackingRange(4)
                      .updateInterval(10));
  @Deprecated
  public static final DeferredHolder<EntityType<?>, EntityType<FlintShurikenEntity>> flintShurikenEntity = ENTITIES.register("flint_shuriken", () ->
    EntityType.Builder.<FlintShurikenEntity>of(FlintShurikenEntity::new, MobCategory.MISC)
                      .noLootTable()
                      .sized(0.25F, 0.25F)
                      .clientTrackingRange(4)
                      .updateInterval(10));

  /*
   * Potions
   */
  public static final DeferredHolder<net.minecraft.world.effect.MobEffect, CarryPotionEffect> carryEffect = MOB_EFFECTS.register("carry", CarryPotionEffect::new);

  /*
   * Events
   */
  @SubscribeEvent
  void commonSetup(final FMLCommonSetupEvent event) {
    PiggybackCapability.register();
    event.enqueueWork(() -> {
      cake.forEach(block -> ComposterBlock.COMPOSTABLES.put(block.asItem(), 1.0f));
      ComposterBlock.COMPOSTABLES.put(magmaCake.get().asItem(), 1.0f);

      DispenserBlock.registerBehavior(glowBall, new ShootProjectileDispenserBehavior(glowBallEntity.get()));
      DispenserBlock.registerBehavior(efln, new ShootProjectileDispenserBehavior(eflnEntity.get()));
      DispenserBlock.registerBehavior(flintShuriken, new ShootProjectileDispenserBehavior(flintShurikenEntity.get()));
      DispenserBlock.registerBehavior(quartzShuriken, new ShootProjectileDispenserBehavior(quartzShurikenEntity.get()));
      armorStand.forEach((type, item) -> DispenserBlock.registerBehavior(item, new DispenseFancyArmorStand(type)));
    });
  }

  @SubscribeEvent
  void gatherData(final GatherDataEvent.Server event) {
    DataGenerator generator = event.getGenerator();
    generator.addProvider(true, new GadgetRecipeProvider.Runner(generator.getPackOutput(), event.getLookupProvider()));
  }

  @SubscribeEvent
  void entityAttributes(EntityAttributeCreationEvent event) {
    event.put(armorStandEntity.get(), LivingEntity.createLivingAttributes().build());
  }

  /** Adds all relevant items to the creative tab, called by general tab */
  public static void addTabItems(ItemDisplayParameters itemDisplayParameters, Output output) {
    output.accept(punji);
    acceptStand(output, StandType.BAMBOO);
    accept(output, itemFrame);
    acceptStand(output, StandType.CLEAR);
    acceptStand(output, StandType.BONE);
    acceptStand(output, StandType.NECROTIC_BONE);
    output.accept(piggyBackpack);
    accept(output, cake);
    output.accept(magmaCake);
  }

  private static void acceptStand(Output output, StandType standType) {
    output.accept(armorStand.get(standType));
  }
}
