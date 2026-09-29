package aebm.continuumtests;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.shared.TinkerAttributes;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;

/**
 * Airborn, rugged and slime bounce behavior against the real damage and fall event paths (parity/modifiers X1 and X2,
 * added 2026-09-29). Command: {@code aebmmodifiertest}. Require {@code AEBM_MODIFIER_SUMMARY passed=N failed=0}.
 *
 * Entities are detached zombies and fake players above the build height; nothing is added to the world. Damage goes
 * through {@code LivingEntity.hurtServer}, so NeoForge's incoming damage event, ToolEvents damage blocking and
 * protection all run. The bounce cases post a real {@link LivingFallEvent} to the NeoForge bus, which runs
 * ModifierEvents.bounceOnFall.
 *
 * Case {@code rugged_cancels_hurt_like_official} checks official v3.12.1 semantics (the hit is canceled: hurtServer
 * returns false and no hurt animation starts). Continuum's ToolEvents zeroes the amount instead of canceling, so this
 * case fails until the one line ToolEvents change in modifiers-REPORT.md is applied.
 */
public final class ModifierParityServerFixture {
  private static final Identifier PROBE = Identifier.fromNamespaceAndPath("aebmcontinuumtests", "modifier_probe");
  private static final List<String> RUGGED_TERRAIN = List.of("minecraft:hot_floor", "minecraft:cactus", "minecraft:sweet_berry_bush", "minecraft:stalagmite", "tconstruct:knightmetal");
  private static final List<String> RUGGED_ATTACKS = List.of("minecraft:cramming", "minecraft:sting", "minecraft:thorns");
  private static final List<String> CONTROLS = List.of("minecraft:generic", "minecraft:fall", "minecraft:magic", "minecraft:mob_attack");

  private ModifierParityServerFixture() {}

  /** Registered from ReturningServerFixture with one addListener line. */
  public static void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("aebmmodifiertest")
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
      test("airborn_trait_from_sky_slimeskin_cuirass", () -> {
        ToolStack boots = tool(TinkerTools.travelersGear.get(ArmorType.BOOTS), MaterialIds.iron, MaterialIds.skyslimeskin);
        require(boots.getModifiers().getLevel(ModifierIds.airborn) == 1, "sky slimeskin cuirass must grant airborn 1");
        ToolStack control = tool(TinkerTools.travelersGear.get(ArmorType.BOOTS), MaterialIds.iron, MaterialIds.leather);
        require(control.getModifiers().getLevel(ModifierIds.airborn) == 0, "leather cuirass must not grant airborn");
      });
      test("airborn_protects_while_attacker_airborne_and_clears_on_landing", () -> {
        Zombie defender = zombie(0);
        defender.setItemSlot(EquipmentSlot.FEET, tool(TinkerTools.travelersGear.get(ArmorType.BOOTS), MaterialIds.iron, MaterialIds.skyslimeskin).createStack());
        Zombie attacker = zombie(2);
        attacker.setOnGround(true);
        float grounded = damageTaken(defender, level.damageSources().mobAttack(attacker), 10);
        attacker.setOnGround(false);
        float airborne = damageTaken(defender, level.damageSources().mobAttack(attacker), 10);
        attacker.setOnGround(true);
        float landed = damageTaken(defender, level.damageSources().mobAttack(attacker), 10);
        require(grounded > 0, "control hit must deal damage");
        require(airborne < grounded - 0.2f, "airborn must reduce damage from an airborne attacker: grounded=" + grounded + " airborne=" + airborne);
        close(grounded, landed, "protection must clear once the attacker lands");
        // same boots without airborn: an airborne attacker gets no reduction
        Zombie plain = zombie(4);
        plain.setItemSlot(EquipmentSlot.FEET, tool(TinkerTools.travelersGear.get(ArmorType.BOOTS), MaterialIds.iron, MaterialIds.leather).createStack());
        attacker.setOnGround(true);
        float plainGrounded = damageTaken(plain, level.damageSources().mobAttack(attacker), 10);
        attacker.setOnGround(false);
        float plainAirborne = damageTaken(plain, level.damageSources().mobAttack(attacker), 10);
        close(plainGrounded, plainAirborne, "without airborn an airborne attacker deals normal damage");
      });
      test("rugged_trim_trait_from_leather_laces", () -> {
        ToolStack boots = tool(TinkerTools.slimesuit.get(ArmorType.BOOTS), MaterialIds.leather, MaterialIds.earthslime);
        ModifierNBT.Builder builder = ModifierNBT.builder();
        boots.getDefinition().getHook(ToolHooks.TRIM_TRAIT).addTraits(boots.getDefinition(), boots.getMaterials(), builder);
        require(builder.build().getLevel(ModifierIds.rugged) == 1, "leather laces must provide rugged through the laces trait hook");
      });
      test("rugged_level_one_blocks_terrain_only", () -> {
        Zombie wearer = ruggedWearer(1, 6);
        for (String type : RUGGED_TERRAIN) {
          close(0, damageTaken(wearer, source(type), 4), type + " must be blocked at rugged 1");
        }
        for (String type : RUGGED_ATTACKS) {
          require(damageTaken(wearer, source(type), 4) > 0, type + " must not be blocked at rugged 1");
        }
        for (String type : CONTROLS) {
          require(damageTaken(wearer, source(type), 4) > 0, type + " must never be blocked");
        }
      });
      test("rugged_level_two_also_blocks_attacks", () -> {
        Zombie wearer = ruggedWearer(2, 8);
        for (String type : RUGGED_TERRAIN) {
          close(0, damageTaken(wearer, source(type), 4), type + " must be blocked at rugged 2");
        }
        for (String type : RUGGED_ATTACKS) {
          close(0, damageTaken(wearer, source(type), 4), type + " must be blocked at rugged 2");
        }
        for (String type : CONTROLS) {
          require(damageTaken(wearer, source(type), 4) > 0, type + " must never be blocked");
        }
      });
      test("rugged_cancels_hurt_like_official", () -> {
        Zombie wearer = ruggedWearer(2, 10);
        for (String type : List.of("minecraft:cactus", "minecraft:hot_floor", "minecraft:sting")) {
          reset(wearer);
          boolean accepted = wearer.hurtServer(level, source(type), 4);
          require(!accepted, type + ": official cancels blocked damage, hurtServer must return false");
          require(wearer.hurtTime == 0, type + ": a canceled hit must not start the hurt animation (hurtTime=" + wearer.hurtTime + ")");
          require(wearer.invulnerableTime == 0, type + ": a canceled hit must not grant invulnerability frames");
        }
      });
      test("slime_bounce_mob_leaves_ground_and_marks_sync", () -> {
        Zombie zombie = zombie(12);
        bouncy(zombie);
        zombie.fallDistance = 5;
        zombie.setDeltaMovement(0, -1, 0);
        zombie.setOnGround(true);
        zombie.needsSync = false;
        LivingFallEvent event = NeoForge.EVENT_BUS.post(new LivingFallEvent(zombie, 5, 1f));
        require(event.isCanceled(), "bounce must cancel the fall on the server");
        require(event.getDistance() == 0, "bounce must clear the fall distance");
        require(!zombie.onGround(), "bounce must leave the entity airborne");
        require(zombie.needsSync, "bounce must flag the velocity for client sync (official hasImpulse)");
        require(zombie.getDeltaMovement().y > 0, "mob bounce must reverse downward motion, y=" + zombie.getDeltaMovement().y);
      });
      test("slime_bounce_player_uses_gravity_velocity", () -> {
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ModifierFixture"));
        player.setPos(14, level.getMaxY() + 64, 0);
        player.setSilent(true);
        bouncy(player);
        player.fallDistance = 5;
        player.setOnGround(true);
        player.needsSync = false;
        LivingFallEvent event = NeoForge.EVENT_BUS.post(new LivingFallEvent(player, 5, 1f));
        require(event.isCanceled(), "player bounce must cancel the fall on the server");
        require(player.needsSync, "player bounce must flag the velocity for client sync");
        require(player.hurtMarked, "player bounce must mark the motion for the player packet");
        require(player.getDeltaMovement().y > 0, "player bounce must launch upward, y=" + player.getDeltaMovement().y);
      });
      test("slime_bounce_threshold_uses_step_bonus_like_official", () -> {
        // official: no bounce while fallDistance <= 0.5 + extra step height (Forge STEP_HEIGHT_ADDITION, default 0)
        require(bounces(zombie(16), 0.9, 0), "0.9 block fall with no step bonus must bounce, as in official");
        require(!bounces(zombie(18), 0.4, 0), "0.4 block fall must not bounce");
        require(!bounces(zombie(20), 0.9, 1.0), "with +1 step height the threshold is 1.5 blocks");
        require(bounces(zombie(22), 1.8, 1.0), "1.8 block fall with +1 step height must bounce");
      });
      source.sendSuccess(() -> Component.literal("AEBM_MODIFIER_SUMMARY passed=" + passed + " failed=" + failed), false);
      return failed == 0 ? 1 : 0;
    }

    private boolean bounces(Zombie zombie, double fall, double stepBonus) {
      bouncy(zombie);
      if (stepBonus != 0) {
        zombie.getAttribute(Attributes.STEP_HEIGHT).addTransientModifier(new AttributeModifier(PROBE, stepBonus, Operation.ADD_VALUE));
      }
      zombie.fallDistance = fall;
      zombie.setDeltaMovement(0, -0.5, 0);
      zombie.setOnGround(true);
      return NeoForge.EVENT_BUS.post(new LivingFallEvent(zombie, fall, 1f)).isCanceled();
    }

    private void bouncy(LivingEntity entity) {
      var instance = entity.getAttribute(TinkerAttributes.BOUNCY);
      require(instance != null, "bouncy attribute must exist on " + entity.getType());
      instance.addTransientModifier(new AttributeModifier(PROBE, 1, Operation.ADD_VALUE));
    }

    /** Zombie wearing slime boots with rugged at the given total level. */
    private Zombie ruggedWearer(int ruggedLevel, int x) {
      ToolStack boots = tool(TinkerTools.slimesuit.get(ArmorType.BOOTS), MaterialIds.leather, MaterialIds.earthslime);
      int existing = boots.getModifiers().getLevel(ModifierIds.rugged);
      if (ruggedLevel > existing) {
        boots.addModifier(ModifierIds.rugged, ruggedLevel - existing);
      }
      require(boots.getModifiers().getLevel(ModifierIds.rugged) == ruggedLevel, "rugged level setup: " + boots.getModifiers().getLevel(ModifierIds.rugged));
      Zombie wearer = zombie(x);
      wearer.setItemSlot(EquipmentSlot.FEET, boots.createStack());
      return wearer;
    }

    private Zombie zombie(int x) {
      Zombie zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
      require(zombie != null, "zombie fixture must be constructible");
      zombie.setPos(x, level.getMaxY() + 64, 0);
      zombie.setSilent(true);
      return zombie;
    }

    private DamageSource source(String id) {
      ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.parse(id));
      Holder<DamageType> holder = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(key)
        .orElseThrow(() -> new AssertionError("damage type not loaded: " + id));
      return new DamageSource(holder);
    }

    /** Health lost from one hit after clearing health, cooldown and hurt state. */
    private float damageTaken(LivingEntity target, DamageSource damage, float amount) {
      reset(target);
      float before = target.getHealth();
      target.hurtServer(level, damage, amount);
      float lost = before - target.getHealth();
      reset(target);
      return lost;
    }

    private static void reset(LivingEntity target) {
      target.setHealth(target.getMaxHealth());
      target.invulnerableTime = 0;
      target.hurtTime = 0;
      target.lastHurt = 0;
      target.clearFire();
    }

    private void test(String name, Runnable check) {
      try {
        check.run();
        passed++;
        source.sendSuccess(() -> Component.literal("AEBM_MODIFIER_PASS " + name), false);
      } catch (Throwable failure) {
        failed++;
        source.sendFailure(Component.literal("AEBM_MODIFIER_FAIL " + name + ": " + failure));
      }
    }

    private static void close(float expected, float actual, String message) {
      require(Math.abs(expected - actual) < 0.0001f, message + ": expected=" + expected + " actual=" + actual);
    }
  }

  private static ToolStack tool(Item item, MaterialVariantId... materials) {
    var variants = java.util.Arrays.stream(materials).map(MaterialVariant::of).toList();
    return ToolStack.createTool(item, IModifiable.getToolDefinition(item), new MaterialNBT(variants));
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }
}
