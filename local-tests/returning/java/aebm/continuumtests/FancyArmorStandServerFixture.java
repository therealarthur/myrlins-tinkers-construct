package aebm.continuumtests;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import slimeknights.tconstruct.gadgets.TinkerGadgets;
import slimeknights.tconstruct.gadgets.entity.FancyArmorStandEntity;
import slimeknights.tconstruct.gadgets.entity.FancyArmorStandEntity.StandType;

/**
 * Nineteen real server checks for inherited ArmorStand damage dispatch and drop conservation.
 * Run with execute positioned in a disposable, already loaded, empty region. No blocks are
 * written and no chunks are requested. Only owned entities are removed; both changed rules
 * are restored before returning. The fake actor is never added to the world/player list.
 */
@EventBusSubscriber(modid = "aebmcontinuumtests")
public final class FancyArmorStandServerFixture {
  private FancyArmorStandServerFixture() {}

  @SubscribeEvent
  public static void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("aebm_continuum_fancy_stand")
      .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
      .executes(context -> new Suite(context.getSource()).run()));
  }

  private static final class Suite {
    private final CommandSourceStack source;
    private final ServerLevel level;
    private final BlockPos origin;
    private final AABB bounds;
    private final List<Entity> owned = new ArrayList<>();
    private final Consumer<EntityJoinLevelEvent> observer = this::observeDrop;
    private final Consumer<LivingDropsEvent> deathObserver = this::observeDeathLoot;
    private Probe active;
    private int passed;
    private int failed;

    private Suite(CommandSourceStack source) {
      this.source = source;
      this.level = source.getLevel();
      this.origin = BlockPos.containing(source.getPosition());
      this.bounds = new AABB(origin.getX() - 2, origin.getY() - 1, origin.getZ() - 2,
        origin.getX() + 3, origin.getY() + 4, origin.getZ() + 3);
    }

    private void requireEmptyRegion() {
      for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(2, 3, 2))) {
        require(!level.isOutsideBuildHeight(pos), "outside build height: " + pos);
        require(level.hasChunkAt(pos), "chunk must already be loaded: " + pos);
        require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "region must be empty: " + pos);
      }
      require(level.getEntities((Entity)null, bounds, entity -> true).isEmpty(), "region contains an entity");
      require(!level.restoringBlockSnapshots, "cannot test during block snapshot restoration");
    }

    private int run() {
      try {
        requireEmptyRegion();
      } catch (Throwable failure) {
        source.sendFailure(Component.literal("AEBM_FANCY_STAND_REFUSED " + failure));
        return 0;
      }
      boolean oldBlockDrops = level.getGameRules().get(GameRules.BLOCK_DROPS);
      boolean oldMobDrops = level.getGameRules().get(GameRules.MOB_DROPS);
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, observer);
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingDropsEvent.class, deathObserver);
      try {
        test("transformed_parent_dispatch_contract", () -> {
          for (String method : List.of("brokenByPlayer", "brokenByAnything")) {
            require(Modifier.isProtected(ArmorStand.class.getDeclaredMethod(method,
              ServerLevel.class, DamageSource.class).getModifiers()), "missing protected access transform: " + method);
          }
          require(Modifier.isProtected(FancyArmorStandEntity.class.getDeclaredMethod("brokenByPlayer",
            ServerLevel.class, DamageSource.class).getModifiers()), "Fancy must override the player hook");
          require(FancyArmorStandEntity.class.getMethod("hurtServer", ServerLevel.class, DamageSource.class,
            float.class).getDeclaringClass() == ArmorStand.class, "fixture must exercise inherited hurtServer");
          require(Arrays.stream(FancyArmorStandEntity.class.getDeclaredMethods())
            .noneMatch(method -> method.getName().equals("dropCustomDeathLoot")), "old stand death-loot hook remains");
        });
        for (StandType type : StandType.values()) {
          test("player_break_" + type.name().toLowerCase(java.util.Locale.ROOT), () -> {
            try (Probe probe = fancy(type)) { probe.breakNormally(true); }
          });
        }
        test("explosion_bamboo", () -> explosion(StandType.BAMBOO));
        test("explosion_bone", () -> explosion(StandType.BONE));
        test("bamboo_mob_drops_disabled", () -> {
          level.getGameRules().set(GameRules.MOB_DROPS, false, level.getServer());
          try (Probe probe = fancy(StandType.BAMBOO)) { probe.breakNormally(false); }
        });
        for (StandType type : List.of(StandType.BAMBOO, StandType.BONE)) {
          test(type.name().toLowerCase(java.util.Locale.ROOT) + "_block_drops_disabled", () -> {
            level.getGameRules().set(GameRules.BLOCK_DROPS, false, level.getServer());
            try (Probe probe = fancy(type)) {
              probe.stand.lastHit = level.getGameTime();
              require(probe.hit(probe.playerDamage), "second hit should be accepted");
              require(probe.stand.isRemoved(), "stand must be removed even with block drops disabled");
              probe.expectDrops(List.of());
              probe.expectEquipmentEmpty();
              probe.expectDeathLootCalls(1);
            }
          });
        }
        test("prevent_equipment_drop", () -> {
          try (Probe probe = fancy(StandType.BONE)) {
            ItemStack cursed = probe.head.copy();
            cursed.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
              .getOrThrow(Enchantments.VANISHING_CURSE), 1);
            require(EnchantmentHelper.has(cursed, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP),
              "loaded vanishing enchantment must prevent equipment drops");
            probe.stand.setItemSlot(EquipmentSlot.HEAD, cursed);
            probe.stand.lastHit = level.getGameTime();
            require(probe.hit(probe.playerDamage) && probe.stand.isRemoved(), "cursed-equipped stand must break");
            probe.expectDrops(List.of(probe.standItem, probe.hand));
            probe.expectEquipmentEmpty();
            probe.expectDeathLootCalls(1);
          }
        });
        test("creative_break_no_items", () -> {
          try (Probe probe = fancy(StandType.BONE)) {
            probe.actor.getAbilities().instabuild = true;
            require(probe.playerDamage.isCreativePlayer(), "damage must identify creative player");
            require(probe.hit(probe.playerDamage) && probe.stand.isRemoved(), "creative hit must remove stand");
            probe.expectDrops(List.of());
            probe.expectDeathLootCalls(0);
          }
        });
        test("invulnerable_rejected", () -> {
          try (Probe probe = fancy(StandType.BONE)) {
            probe.stand.setInvulnerable(true);
            probe.expectRejected(probe.playerDamage);
          }
        });
        test("invisible_rejected", () -> {
          try (Probe probe = fancy(StandType.BONE)) {
            probe.stand.setInvisible(true);
            probe.expectRejected(probe.playerDamage);
          }
        });
        test("player_may_build_false_rejected", () -> {
          try (Probe probe = fancy(StandType.BONE)) {
            probe.actor.getAbilities().mayBuild = false;
            probe.expectRejected(probe.playerDamage);
          }
        });
        test("ineligible_damage_rejected", () -> {
          try (Probe probe = fancy(StandType.BONE)) {
            DamageSource damage = level.damageSources().generic();
            require(!damage.is(DamageTypeTags.CAN_BREAK_ARMOR_STAND)
              && !damage.is(DamageTypeTags.ALWAYS_KILLS_ARMOR_STANDS)
              && !damage.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
              && !damage.is(DamageTypeTags.IS_EXPLOSION)
              && !damage.is(DamageTypeTags.IGNITES_ARMOR_STANDS)
              && !damage.is(DamageTypeTags.BURNS_ARMOR_STANDS), "generic damage must be ineligible");
            probe.expectRejected(damage);
          }
        });
        test("generic_kill_no_items", () -> {
          try (Probe probe = fancy(StandType.BONE)) {
            DamageSource damage = level.damageSources().genericKill();
            require(damage.is(DamageTypeTags.BYPASSES_INVULNERABILITY), "generic kill tag must be loaded");
            require(!probe.hit(damage) && probe.stand.isRemoved(), "generic kill removes stand but returns false");
            probe.expectDrops(List.of());
            probe.expectDeathLootCalls(0);
          }
        });
        test("vanilla_stand_control", () -> {
          try (Probe probe = new Probe(new ArmorStand(EntityType.ARMOR_STAND, level), new ItemStack(Items.ARMOR_STAND))) {
            probe.breakNormally(false);
          }
        });
      } finally {
        active = null;
        NeoForge.EVENT_BUS.unregister(observer);
        NeoForge.EVENT_BUS.unregister(deathObserver);
        try {
          for (Entity entity : owned) entity.discard();
        } finally {
          level.getGameRules().set(GameRules.BLOCK_DROPS, oldBlockDrops, level.getServer());
          level.getGameRules().set(GameRules.MOB_DROPS, oldMobDrops, level.getServer());
        }
      }
      // This final check must not reset the restored rules through test().
      report("cleanup_restores_rules_and_removes_owned_entities", () -> {
        require(level.getGameRules().get(GameRules.BLOCK_DROPS) == oldBlockDrops, "block drops rule not restored");
        require(level.getGameRules().get(GameRules.MOB_DROPS) == oldMobDrops, "mob drops rule not restored");
        for (Entity entity : owned) {
          require(entity.isRemoved() && level.getEntity(entity.getId()) == null, "owned entity survived cleanup");
        }
        requireEmptyRegion();
      });
      source.sendSuccess(() -> Component.literal("AEBM_FANCY_STAND_SUMMARY passed=" + passed + " failed=" + failed), false);
      return failed == 0 && passed == 19 ? 1 : 0;
    }

    private Probe fancy(StandType type) {
      FancyArmorStandEntity stand = new FancyArmorStandEntity(TinkerGadgets.armorStandEntity.get(), level);
      type.onPlace(stand);
      require(stand.getStandType() == type && stand.isSmall() == (type == StandType.BAMBOO), "variant placement setup");
      return new Probe(stand, new ItemStack(stand.getStandItem()));
    }

    private void explosion(StandType type) {
      try (Probe probe = fancy(type)) {
        DamageSource damage = level.damageSources().explosion(probe.actor, probe.actor);
        require(damage.is(DamageTypeTags.IS_EXPLOSION)
          && !damage.is(DamageTypeTags.BYPASSES_INVULNERABILITY), "explosion tags must select explosion branch");
        require(!probe.hit(damage) && probe.stand.isRemoved(), "explosion removes stand but returns false");
        probe.expectDrops(List.of(probe.head, probe.hand));
        probe.expectEquipmentEmpty();
        require(!probe.hit(probe.playerDamage), "removed stand must reject later damage");
        probe.expectDrops(List.of(probe.head, probe.hand));
        probe.expectDeathLootCalls(1);
      }
    }

    private void observeDeathLoot(LivingDropsEvent event) {
      Probe probe = active;
      if (probe != null && probe.hitting && event.getEntity() == probe.stand) probe.deathLootCalls++;
    }

    private void observeDrop(EntityJoinLevelEvent event) {
      Probe probe = active;
      if (probe != null && probe.hitting && event.getLevel() == level && event.getEntity() instanceof ItemEntity item
          && bounds.contains(item.position()) && !probe.drops.contains(item)) {
        // Observe only; never cancel admission or query world state from the join callback.
        probe.drops.add(item);
        owned.add(item);
      }
    }

    private void test(String name, CheckedRunnable body) {
      report(name, () -> {
        level.getGameRules().set(GameRules.BLOCK_DROPS, true, level.getServer());
        level.getGameRules().set(GameRules.MOB_DROPS, true, level.getServer());
        body.run();
      });
    }

    private void report(String name, CheckedRunnable body) {
      try {
        body.run();
        passed++;
        source.sendSuccess(() -> Component.literal("AEBM_FANCY_STAND_PASS " + name), false);
      } catch (Throwable failure) {
        failed++;
        source.sendFailure(Component.literal("AEBM_FANCY_STAND_FAIL " + name + " " + failure));
      }
    }

    private final class Probe implements AutoCloseable {
      private final ArmorStand stand;
      private final FakePlayer actor;
      private final DamageSource playerDamage;
      private final ItemStack standItem;
      private final ItemStack head = new ItemStack(Items.DIAMOND_HELMET);
      private final ItemStack hand = new ItemStack(Items.DIAMOND, 3);
      private final List<ItemEntity> drops = new ArrayList<>();
      private boolean hitting;
      private int deathLootCalls;

      private Probe(ArmorStand stand, ItemStack standItem) {
        this.stand = stand;
        this.standItem = standItem;
        owned.add(stand);
        Component name = Component.literal("AEBM fancy stand " + stand.getUUID());
        stand.setCustomName(name);
        standItem.set(DataComponents.CUSTOM_NAME, name);
        head.set(DataComponents.CUSTOM_NAME, Component.literal("AEBM helmet components"));
        head.set(DataComponents.DAMAGE, 7);
        hand.set(DataComponents.CUSTOM_NAME, Component.literal("AEBM three diamonds"));
        stand.setItemSlot(EquipmentSlot.HEAD, head.copy());
        stand.setItemSlot(EquipmentSlot.MAINHAND, hand.copy());
        stand.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        stand.setNoGravity(true);
        actor = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "AEBMStandTest"));
        actor.setPos(origin.getX() + 1.5, origin.getY(), origin.getZ() + 0.5);
        actor.getAbilities().mayBuild = true;
        actor.getAbilities().instabuild = false;
        playerDamage = level.damageSources().playerAttack(actor);
        require(playerDamage.getEntity() == actor && playerDamage.is(DamageTypeTags.CAN_BREAK_ARMOR_STAND)
          && !playerDamage.is(DamageTypeTags.ALWAYS_KILLS_ARMOR_STANDS)
          && !playerDamage.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
          && !playerDamage.is(DamageTypeTags.IS_EXPLOSION), "player damage must select ordinary incremental breaking");
        require(!EnchantmentHelper.has(head, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)
          && !EnchantmentHelper.has(hand, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP), "ordinary equipment must be droppable");
        require(level.addFreshEntity(stand) && level.getEntity(stand.getId()) == stand, "stand admission failed");
        active = this;
      }

      private boolean hit(DamageSource damage) {
        hitting = true;
        try { return stand.hurtServer(level, damage, 1); }
        finally { hitting = false; }
      }

      private void breakNormally(boolean checkExpiredWindow) {
        stand.lastHit = level.getGameTime() - 6;
        require(hit(playerDamage) && !stand.isRemoved(), "first hit must wobble without removal");
        require(stand.lastHit == level.getGameTime(), "first hit must record wobble time");
        expectDrops(List.of());
        expectEquipmentIntact();
        expectDeathLootCalls(0);
        if (checkExpiredWindow) {
          stand.lastHit = level.getGameTime() - 6;
          require(hit(playerDamage) && !stand.isRemoved(), "expired hit window must wobble again");
          expectDrops(List.of());
          expectEquipmentIntact();
          expectDeathLootCalls(0);
        }
        require(hit(playerDamage) && stand.isRemoved(), "second hit within window must remove stand");
        expectDrops(List.of(standItem, head, hand));
        expectEquipmentEmpty();
        require(!hit(playerDamage), "removed stand must reject further damage");
        expectDrops(List.of(standItem, head, hand));
        expectDeathLootCalls(1);
      }

      private void expectRejected(DamageSource damage) {
        stand.lastHit = level.getGameTime();
        require(!hit(damage) && !stand.isRemoved(), "damage should be rejected without removal");
        expectDrops(List.of());
        expectEquipmentIntact();
        expectDeathLootCalls(0);
      }

      private void expectDeathLootCalls(int count) {
        require(deathLootCalls == count, "expected " + count + " death-loot callbacks, observed " + deathLootCalls);
      }

      private void expectEquipmentIntact() {
        require(sameStack(stand.getItemBySlot(EquipmentSlot.HEAD), head), "helmet changed before break");
        require(sameStack(stand.getItemBySlot(EquipmentSlot.MAINHAND), hand), "held stack changed before break");
      }

      private void expectEquipmentEmpty() {
        for (EquipmentSlot slot : EquipmentSlot.VALUES) require(stand.getItemBySlot(slot).isEmpty(), "equipment not cleared: " + slot);
      }

      private void expectDrops(List<ItemStack> expected) {
        List<ItemStack> remaining = new ArrayList<>();
        expected.forEach(stack -> remaining.add(stack.copy()));
        for (ItemEntity item : drops) {
          require(!item.isRemoved() && level.getEntity(item.getId()) == item, "drop was observed but not admitted to level");
          ItemStack actual = item.getItem();
          require(!actual.isEmpty(), "empty dropped item");
          int match = -1;
          for (int index = 0; index < remaining.size(); index++) {
            if (sameStack(actual, remaining.get(index))) { match = index; break; }
          }
          require(match >= 0, "unexpected or duplicate drop: " + actual);
          remaining.remove(match);
        }
        require(remaining.isEmpty(), "missing drops: " + remaining);
      }

      @Override
      public void close() {
        active = null;
        stand.discard();
        for (ItemEntity item : drops) item.discard();
      }
    }
  }

  private static boolean sameStack(ItemStack left, ItemStack right) {
    return left.getCount() == right.getCount() && ItemStack.isSameItemSameComponents(left, right);
  }

  @FunctionalInterface
  private interface CheckedRunnable { void run() throws Exception; }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
