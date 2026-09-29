package aebm.continuumtests;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.tools.entity.ThrownTool;

/** Runs actual Continuum/vanilla code after server registries and modifier data have loaded. */
@Mod("aebmcontinuumtests")
public final class ReturningServerFixture {
  public ReturningServerFixture() {
    NeoForge.EVENT_BUS.addListener(ReturningServerFixture::registerCommands);
    NeoForge.EVENT_BUS.addListener(CraftingServerFixture::registerCommands);
    NeoForge.EVENT_BUS.addListener(PersistenceServerFixture::registerCommands);
    NeoForge.EVENT_BUS.addListener(BlockWalkerPlacementServerFixture::registerCommands);
    NeoForge.EVENT_BUS.addListener(MaterialsParityServerFixture::registerCommands);
  }

  private static void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("aebmreturningtest")
      .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
      .executes(context -> new Suite(context.getSource()).run()));
  }

  private static final class Suite {
    private final CommandSourceStack source;
    private final ServerLevel level;
    private int passed;
    private int failed;

    private Suite(CommandSourceStack source) {
      this.source = source;
      this.level = source.getLevel();
    }

    private int run() {
      test("fresh_loyalty_and_no_early_return", () -> {
        FakePlayer owner = owner();
        ThrownTool thrown = thrown(owner, 4);
        require(loyalty(thrown) == 4, "vanilla loyalty must be Returning IV");
        thrown.tick();
        require(!thrown.isNoPhysics(), "unhit projectile must not start returning");
      });
      test("ground_return_and_original_slot_pickup", () -> {
        FakePlayer owner = owner();
        ThrownTool thrown = thrown(owner, 4);
        thrown.setOriginalSlot(3);
        int damage = damage(thrown);
        field(AbstractArrow.class, "inGroundTime").setInt(thrown, 5);
        assertReturningAfterTick(thrown, owner);
        require(damage(thrown) == damage + 1, "ground impact must cost one durability");
        require(parentDealtDamage(thrown), "ground impact must set vanilla damage state");
        thrown.shakeTime = 0;
        thrown.playerTouch(owner);
        require(thrown.isRemoved(), "pickup must remove projectile");
        require(owner.getInventory().getItem(3).is(TinkerTools.javelin.get()), "pickup must use original slot");
        require(countJavelins(owner) == 1, "pickup must recover exactly one javelin");
        require(ModifierUtil.getVolatileInt(owner.getInventory().getItem(3), ThrownTool.LOYALTY) == 4,
          "pickup must retain Returning IV");
      });
      test("zero_returning_control", () -> {
        FakePlayer owner = owner();
        ThrownTool thrown = thrown(owner, 0);
        require(loyalty(thrown) == 0, "control must have no Returning");
        field(AbstractArrow.class, "inGroundTime").setInt(thrown, 5);
        thrown.tick();
        require(!thrown.isNoPhysics(), "unmodified projectile must not return");
      });
      test("entity_impact_state_and_return", () -> {
        FakePlayer owner = owner();
        ThrownTool thrown = thrown(owner, 4);
        var target = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
        require(target != null, "cow fixture must be constructible");
        target.setInvulnerable(true);
        target.setPos(thrown.position());
        method(ThrownTool.class, "onHitEntity", EntityHitResult.class).invoke(thrown, new EntityHitResult(target));
        require(parentDealtDamage(thrown), "entity impact must set vanilla damage state before grounding");
        require(method(ThrownTrident.class, "findHitEntity", Vec3.class, Vec3.class)
          .invoke(thrown, thrown.position(), thrown.position().add(1, 0, 0)) == null,
          "vanilla must suppress another entity hit after impact");
        assertReturningAfterTick(thrown, owner);
      });
      test("below_world_hook_state_and_return", () -> {
        FakePlayer owner = owner();
        ThrownTool thrown = thrown(owner, 4);
        int damage = damage(thrown);
        method(ThrownTool.class, "onBelowWorld").invoke(thrown);
        require(!thrown.isRemoved(), "Returning tool must survive below-world hook");
        require(parentDealtDamage(thrown), "below-world hook must set vanilla damage state");
        require(damage(thrown) == damage + 1, "below-world hook must retain durability cost");
        assertReturningAfterTick(thrown, owner);
      });
      test("save_reload_returning_and_offhand", () -> {
        FakePlayer owner = owner();
        ThrownTool original = thrown(owner, 4);
        original.setOriginalSlot(Inventory.SLOT_OFFHAND);
        field(AbstractArrow.class, "inGroundTime").setInt(original, 5);
        assertReturningAfterTick(original, owner);
        CompoundTag saved = save(original);
        require(saved.getBooleanOr("DealtDamage", false), "vanilla impact state must serialize");
        require(saved.getBooleanOr("TConDealtDamage", false), "legacy impact state must serialize consistently");
        ThrownTool restored = load(saved, owner);
        require(parentDealtDamage(restored), "impact state must reload");
        require(loyalty(restored) == 4, "Returning IV must rebuild after reload");
        require(damage(restored) == damage(original), "damage must survive reload");
        require(ItemStack.isSameItemSameComponents(original.getDisplayTool(), restored.getDisplayTool()),
          "tool components must survive reload");
        assertReturningAfterTick(restored, owner);
        restored.shakeTime = 0;
        restored.playerTouch(owner);
        require(restored.isRemoved() && owner.getOffhandItem().is(TinkerTools.javelin.get()),
          "reloaded original slot must recover to offhand");
        require(countJavelins(owner) == 1, "reloaded pickup must recover exactly one item");
      });
      test("legacy_shadow_state_migration", () -> {
        FakePlayer owner = owner();
        CompoundTag saved = save(thrown(owner, 4));
        saved.putBoolean("DealtDamage", false);
        saved.putBoolean("TConDealtDamage", true);
        ThrownTool restored = load(saved, owner);
        require(parentDealtDamage(restored), "legacy tool-only impact must migrate to vanilla");
        assertReturningAfterTick(restored, owner);
      });
      test("vanilla_state_not_erased_by_legacy_false", () -> {
        FakePlayer owner = owner();
        CompoundTag saved = save(thrown(owner, 4));
        saved.putBoolean("DealtDamage", true);
        saved.putBoolean("TConDealtDamage", false);
        ThrownTool restored = load(saved, owner);
        require(parentDealtDamage(restored), "legacy false must not erase vanilla true");
        assertReturningAfterTick(restored, owner);
      });
      test("occupied_original_slot_preserved", () -> {
        FakePlayer owner = owner();
        ItemStack occupied = new ItemStack(Items.COBBLESTONE, 12);
        owner.getInventory().setItem(3, occupied);
        ThrownTool thrown = thrown(owner, 4);
        thrown.setOriginalSlot(3);
        field(AbstractArrow.class, "inGroundTime").setInt(thrown, 5);
        assertReturningAfterTick(thrown, owner);
        thrown.shakeTime = 0;
        thrown.playerTouch(owner);
        require(thrown.isRemoved(), "fallback inventory pickup must remove projectile");
        require(owner.getInventory().getItem(3) == occupied && occupied.getCount() == 12,
          "occupied original slot must remain unchanged");
        require(countJavelins(owner) == 1, "fallback must recover exactly one javelin");
      });
      source.sendSuccess(() -> Component.literal("AEBM_RETURNING_SUMMARY passed=" + passed + " failed=" + failed), false);
      return failed == 0 ? 1 : 0;
    }

    private FakePlayer owner() {
      // These objects are never added to the world or player list. No shared fake-player cache is modified.
      FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "AEBMReturnTest"));
      player.setPos(source.getPosition().x, level.getMaxY() + 32, source.getPosition().z);
      return player;
    }

    private ThrownTool thrown(FakePlayer owner, int returning) {
      ItemStack stack = ToolBuildHandler.createSingleMaterial(TinkerTools.javelin.get(), MaterialVariant.of(MaterialIds.iron, ""));
      require(!stack.isEmpty(), "loaded material/tool data must build an iron javelin");
      ToolStack tool = ToolStack.copyFrom(stack);
      if (returning > 0) {
        tool.addModifier(ModifierIds.returning, returning);
      }
      stack = tool.createStack();
      require(!tool.isBroken(), "fixture tool must be usable");
      require(ModifierUtil.getVolatileInt(stack, ThrownTool.LOYALTY) == returning, "modifier data must produce requested loyalty");
      ThrownTool thrown = new ThrownTool(level, owner, stack, 1, 1, 0.6f);
      thrown.setPos(owner.getX() + 12, owner.getEyeY(), owner.getZ());
      thrown.setDeltaMovement(Vec3.ZERO);
      return thrown;
    }

    private void assertReturningAfterTick(ThrownTool thrown, FakePlayer owner) {
      double before = thrown.position().distanceTo(owner.getEyePosition());
      thrown.tick();
      require(!thrown.isRemoved(), "return tick must retain recoverable projectile");
      require(thrown.isNoPhysics(), "vanilla return tick must enable no-physics movement");
      require(thrown.position().distanceTo(owner.getEyePosition()) < before, "actual tick must move toward owner");
    }

    private CompoundTag save(ThrownTool thrown) {
      ProblemReporter.Collector problems = new ProblemReporter.Collector();
      TagValueOutput output = TagValueOutput.createWithContext(problems, level.registryAccess());
      thrown.saveWithoutId(output);
      require(problems.isEmpty(), "serialization problems: " + problems.getReport());
      return output.buildResult();
    }

    private ThrownTool load(CompoundTag saved, FakePlayer owner) {
      ProblemReporter.Collector problems = new ProblemReporter.Collector();
      ThrownTool thrown = new ThrownTool(TinkerTools.thrownTool.get(), level);
      thrown.load(TagValueInput.create(problems, level.registryAccess(), saved));
      require(problems.isEmpty(), "deserialization problems: " + problems.getReport());
      // The synthetic owner is not world-registered, so UUID-to-player restoration is outside this fixture.
      thrown.setOwner(owner);
      return thrown;
    }

    private void test(String name, CheckedRunnable test) {
      try {
        test.run();
        passed++;
        source.sendSuccess(() -> Component.literal("AEBM_RETURNING_PASS " + name), false);
      } catch (Exception | AssertionError failure) {
        failed++;
        Throwable cause = failure;
        while (cause.getCause() != null) {
          cause = cause.getCause();
        }
        source.sendFailure(Component.literal("AEBM_RETURNING_FAIL " + name + " " + cause));
      }
    }
  }

  private static int damage(ThrownTool thrown) {
    return ToolStack.from(thrown.getPickupItemStackOrigin()).getDamage();
  }

  private static int countJavelins(FakePlayer owner) {
    int count = 0;
    Inventory inventory = owner.getInventory();
    for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
      ItemStack stack = inventory.getItem(slot);
      if (stack.is(TinkerTools.javelin.get())) {
        count += stack.getCount();
      }
    }
    return count;
  }

  @SuppressWarnings("unchecked")
  private static byte loyalty(ThrownTool thrown) throws ReflectiveOperationException {
    var accessor = (EntityDataAccessor<Byte>) field(ThrownTrident.class, "ID_LOYALTY").get(null);
    return thrown.getEntityData().get(accessor);
  }

  private static boolean parentDealtDamage(ThrownTool thrown) throws ReflectiveOperationException {
    return field(ThrownTrident.class, "dealtDamage").getBoolean(thrown);
  }

  private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  private static Method method(Class<?> owner, String name, Class<?>... arguments) throws ReflectiveOperationException {
    Method method = owner.getDeclaredMethod(name, arguments);
    method.setAccessible(true);
    return method;
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  @FunctionalInterface
  private interface CheckedRunnable {
    void run() throws Exception;
  }
}
