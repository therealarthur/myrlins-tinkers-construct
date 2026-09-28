package aebm.continuumtests;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.definition.module.weapon.MeleeHitToolHook;
import slimeknights.tconstruct.library.tools.helper.ToolAttackUtil;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.material.MaterialIds;

/** Actual attack methods on detached entities; no entity is added to the server world. */
@EventBusSubscriber(modid = "aebmcontinuumtests")
public final class CombatParityServerFixture {
  @SubscribeEvent
  public static void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("aebmcombattest")
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

    @SuppressWarnings("removal")
    private int run() {
      test("default_damage_reports_actual_acceptance", () -> {
        FakePlayer attacker = attacker();
        LivingEntity target = target();
        float health = target.getHealth();
        target.setInvulnerable(true);
        require(!ToolAttackUtil.dealDefaultDamage(attacker, target, 4), "invulnerable target must reject positive damage");
        close(health, target.getHealth(), "rejected damage health");
        target.setInvulnerable(false);
        require(ToolAttackUtil.dealDefaultDamage(attacker, target, 4), "ordinary target must accept damage");
        close(health - 4, target.getHealth(), "accepted damage health");
      });
      test("secondary_damage_accumulates_threshold_and_preserves_timer", () -> {
        LivingEntity target = target();
        float health = target.getHealth();
        target.lastHurt = 4;
        target.invulnerableTime = 17;
        require(ToolAttackUtil.hurtNoInvulnerableTime(target, target, level.damageSources().generic(), 3), "secondary hit must land during cooldown");
        close(health - 3, target.getHealth(), "secondary health");
        close(7, target.lastHurt, "secondary accumulated threshold");
        require(target.invulnerableTime == 17, "secondary hit must restore original cooldown");
      });
      test("rejected_secondary_keeps_previous_damage_threshold", () -> {
        LivingEntity target = target();
        target.lastHurt = 4;
        target.invulnerableTime = 17;
        target.setInvulnerable(true);
        float health = target.getHealth();
        require(!ToolAttackUtil.hurtNoInvulnerableTime(target, target, level.damageSources().generic(), 3), "invulnerable secondary hit must fail");
        close(health, target.getHealth(), "rejected secondary health");
        close(4, target.lastHurt, "rejected secondary must not double prior threshold");
        require(target.invulnerableTime == 17, "rejected secondary cooldown");
      });
      test("canceled_secondary_returns_false_and_restores_knockback", () -> {
        LivingEntity target = target();
        target.lastHurt = 4;
        target.invulnerableTime = 17;
        double resistance = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        float health = target.getHealth();
        AtomicInteger events = new AtomicInteger();
        Consumer<LivingIncomingDamageEvent> cancel = event -> {
          if (event.getEntity() == target) {
            events.incrementAndGet();
            event.setCanceled(true);
          }
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
          require(!ToolAttackUtil.attackEntitySecondary(level.damageSources().generic(), 3, target, target, true), "canceled secondary must fail");
        } finally {
          NeoForge.EVENT_BUS.unregister(cancel);
        }
        require(events.get() == 1, "actual incoming event must be reached exactly once");
        close(health, target.getHealth(), "canceled secondary health");
        close(4, target.lastHurt, "canceled secondary threshold");
        require(target.invulnerableTime == 17, "canceled secondary cooldown");
        require(resistance == target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), "temporary knockback resistance must be removed");
      });
      test("full_tool_attack_does_not_charge_durability_on_rejection", () -> {
        FakePlayer attacker = attacker();
        ItemStack stack = ToolBuildHandler.createSingleMaterial(TinkerTools.sword.get(), MaterialVariant.of(MaterialIds.iron, ""));
        require(!stack.isEmpty(), "loaded material data must build sword");
        attacker.setItemInHand(InteractionHand.MAIN_HAND, stack);
        ToolStack tool = ToolStack.from(stack);
        LivingEntity target = target();
        target.setInvulnerable(true);
        int damage = tool.getDamage();
        var attack = ToolAttackContext.attacker(attacker).target(target).baseDamage(4).extraAttack().build();
        require(!ToolAttackUtil.performAttack(tool, attack), "full tool attack must report rejected target");
        require(tool.getDamage() == damage, "rejected attack must not consume durability");
        target.setInvulnerable(false);
        float health = target.getHealth();
        require(ToolAttackUtil.performAttack(tool, attack), "full tool attack must report accepted target");
        require(target.getHealth() < health, "accepted attack must damage target");
        require(tool.getDamage() > damage, "accepted attack must consume durability");
      });
      test("melee_tool_hook_runs_only_after_accepted_damage", () -> {
        FakePlayer attacker = attacker();
        LivingEntity target = target();
        ToolStack tool = ToolStack.from(ToolBuildHandler.createSingleMaterial(TinkerTools.sword.get(), MaterialVariant.of(MaterialIds.iron, "")));
        AtomicInteger callbacks = new AtomicInteger();
        MeleeHitToolHook hook = (view, context, damage) -> callbacks.incrementAndGet();
        // Delegate every tool operation except this observable test-only post-hit hook.
        IToolStackView observed = (IToolStackView) Proxy.newProxyInstance(IToolStackView.class.getClassLoader(),
          new Class<?>[]{IToolStackView.class}, (proxy, method, arguments) -> {
            if (method.getName().equals("getHook") && arguments[0] == ToolHooks.MELEE_HIT) {
              return hook;
            }
            return method.invoke(tool, arguments);
          });
        var attack = ToolAttackContext.attacker(attacker).target(target).baseDamage(4).extraAttack().build();
        target.setInvulnerable(true);
        require(!MeleeHitToolHook.dealDamage(observed, attack, 4), "hook API must reject invulnerable target");
        require(callbacks.get() == 0, "failed hit must not invoke successful-hit hook");
        target.setInvulnerable(false);
        require(MeleeHitToolHook.dealDamage(observed, attack, 4), "hook API must accept normal target");
        require(callbacks.get() == 1, "successful-hit hook must run exactly once");
      });
      source.sendSuccess(() -> Component.literal("AEBM_COMBAT_SUMMARY passed=" + passed + " failed=" + failed), false);
      return failed == 0 ? 1 : 0;
    }

    private FakePlayer attacker() {
      FakePlayer attacker = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ContinuumCombat"));
      attacker.setPos(0, level.getMaxY() + 64, 0);
      attacker.setSilent(true);
      return attacker;
    }

    private LivingEntity target() {
      LivingEntity target = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
      require(target != null, "cow fixture must be constructible");
      target.setPos(1, level.getMaxY() + 64, 0);
      target.setSilent(true);
      return target;
    }

    private void test(String name, Runnable check) {
      try {
        check.run();
        passed++;
        source.sendSuccess(() -> Component.literal("AEBM_COMBAT_PASS " + name), false);
      } catch (Throwable failure) {
        failed++;
        source.sendFailure(Component.literal("AEBM_COMBAT_FAIL " + name + ": " + failure));
      }
    }

    private static void close(float expected, float actual, String message) {
      require(Math.abs(expected - actual) < 0.0001f, message + ": expected=" + expected + " actual=" + actual);
    }

    private static void require(boolean condition, String message) {
      if (!condition) {
        throw new AssertionError(message);
      }
    }
  }
}
