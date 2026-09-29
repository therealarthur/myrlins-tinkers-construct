package slimeknights.tconstruct.common.data.loot;

import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.advancements.criterion.NbtPredicate;
import net.minecraft.advancements.criterion.SlimePredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContext.EntityTarget;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.functions.SmeltItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.gadgets.TinkerGadgets;
import slimeknights.tconstruct.shared.TinkerCommons;
import slimeknights.tconstruct.shared.block.SlimeType;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.world.TinkerWorld;
import slimeknights.tconstruct.world.entity.ArmoredSlimeEntity;

import javax.annotation.Nullable;
import java.util.stream.Stream;

public class EntityLootTableProvider extends EntityLootSubProvider {
  protected EntityLootTableProvider(HolderLookup.Provider registries) {
    super(FeatureFlags.REGISTRY.allFlags(), registries);
  }

  @Override
  protected Stream<EntityType<?>> getKnownEntityTypes() {
    return BuiltInRegistries.ENTITY_TYPE.stream()
                                    .filter(entityType -> TConstruct.MOD_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entityType).getNamespace()));
  }

  @Override
  public void generate() {
    this.add(TinkerTools.indestructibleItem.get(), LootTable.lootTable());
    this.add(TinkerTools.crystalshotEntity.get(), LootTable.lootTable());
    this.add(TinkerTools.fishingHook.get(), LootTable.lootTable());
    this.add(TinkerTools.materialArrow.get(), LootTable.lootTable());
    this.add(TinkerTools.thrownShuriken.get(), LootTable.lootTable());
    this.add(TinkerTools.thrownTool.get(), LootTable.lootTable());
    this.add(TinkerModifiers.fluidSpitEntity.get(), LootTable.lootTable());
    this.add(TinkerModifiers.fireball.get(), LootTable.lootTable());
    // official 3.12.1: "not really sure why armor stands have a loot table, but vanilla does it"
    this.add(TinkerGadgets.armorStandEntity.get(), LootTable.lootTable());
    this.add(TinkerWorld.skySlimeEntity.get(), dropSlimeballs(SlimeType.SKY, TinkerWorld.steelShard.get()));
    this.add(TinkerWorld.enderSlimeEntity.get(), dropSlimeballs(SlimeType.ENDER, TinkerWorld.knightmetalShard.get()));

    LootItemCondition.Builder killedByFrog = killedByFrog(registries.lookupOrThrow(Registries.ENTITY_TYPE));
    this.add(TinkerWorld.terracubeEntity.get(),
             LootTable.lootTable()
                      .withPool(LootPool.lootPool()
                                        .setRolls(ConstantValue.exactly(1))
                                        .add(LootItem.lootTableItem(Items.CLAY_BALL)
                                                     .apply(SetItemCountFunction.setCount(UniformGenerator.between(-2.0F, 1.0F)))
                                                     .apply(EnchantedCountIncreaseFunction.lootingMultiplier(registries, UniformGenerator.between(0.0F, 1.0F)))
                                                     .when(killedByFrog.invert())
                                                     .when(LootItemEntityPropertyCondition.hasProperties(LootContext.EntityTarget.THIS, EntityPredicate.Builder.entity().subPredicate(SlimePredicate.sized(MinMaxBounds.Ints.atLeast(2))))))
                                        .add(LootItem.lootTableItem(TinkerSmeltery.searedLamp)
                                                     .apply(SetItemCountFunction.setCount(ConstantValue.exactly(1.0F)))
                                                     .when(killedByFrog))
                                        .apply(SmeltItemFunction.smelted().when(shouldSmeltLoot()))));
  }

  /** Drops an item using the same chances as slimeballs */
  private LootPoolEntryContainer.Builder<?> slimeball(Item item) {
    return LootItem.lootTableItem(item)
      .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0F, 2.0F)))
      .apply(EnchantedCountIncreaseFunction.lootingMultiplier(registries, UniformGenerator.between(0.0F, 1.0F)));
  }

  /** Drops a frog slimeball */
  private static LootPoolEntryContainer.Builder<?> frogball(Item item) {
    return LootItem.lootTableItem(item).apply(SetItemCountFunction.setCount(ConstantValue.exactly(1)));
  }

  /** Drops slimeballs, and optionally nuggets for metal slimes. */
  private LootTable.Builder dropSlimeballs(SlimeType type, @Nullable Item nugget) {
    LootItemCondition.Builder small = LootItemEntityPropertyCondition.hasProperties(EntityTarget.THIS, EntityPredicate.Builder.entity().subPredicate(SlimePredicate.sized(MinMaxBounds.Ints.exactly(1))));
    LootItemCondition.Builder killedByFrog = killedByFrog(registries.lookupOrThrow(Registries.ENTITY_TYPE));
    LootPool.Builder noFrog = LootPool.lootPool().setRolls(ConstantValue.exactly(1)).when(killedByFrog.invert()).when(small);
    LootPool.Builder frog = LootPool.lootPool().setRolls(ConstantValue.exactly(1)).when(killedByFrog).when(small);
    Item slimeball = TinkerCommons.slimeball.get(type);
    if (nugget != null) {
      CompoundTag isMetal = new CompoundTag();
      isMetal.putBoolean(ArmoredSlimeEntity.TAG_METAL, true);
      LootItemCondition.Builder predicate = LootItemEntityPropertyCondition.hasProperties(EntityTarget.THIS, EntityPredicate.Builder.entity().nbt(new NbtPredicate(isMetal)));
      noFrog.add(slimeball(slimeball).when(predicate.invert()));
      noFrog.add(slimeball(nugget).when(predicate));
      frog.add(frogball(slimeball).when(predicate.invert()));
      frog.add(frogball(nugget).when(predicate));
    } else {
      noFrog.add(slimeball(slimeball));
      frog.add(frogball(slimeball));
    }
    return LootTable.lootTable().withPool(noFrog).withPool(frog);
  }
}
