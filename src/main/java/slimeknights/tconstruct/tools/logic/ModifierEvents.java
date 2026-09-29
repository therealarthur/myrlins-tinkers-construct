package slimeknights.tconstruct.tools.logic;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingGetProjectileEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

import slimeknights.mantle.MantleEvents;
import slimeknights.mantle.util.CombatHelper;
import slimeknights.mantle.util.RegistryHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.Sounds;
import slimeknights.tconstruct.common.TinkerDamageTypes;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.library.events.teleport.TinkerTeleportEvent;
import slimeknights.tconstruct.library.json.predicate.TinkerPredicate;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.entity.ReusableProjectile;
import slimeknights.tconstruct.library.modifiers.hook.build.ConditionalStatModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.armor.EffectImmunityModule;
import slimeknights.tconstruct.library.modifiers.modules.technical.ArmorLevelModule;
import slimeknights.tconstruct.library.modifiers.modules.technical.ArmorStatModule;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;
import slimeknights.tconstruct.library.tools.capability.PersistentDataCapability;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability.TinkerDataKey;
import slimeknights.tconstruct.library.tools.capability.TinkerDataKeys;
import slimeknights.tconstruct.library.tools.helper.ModifierLootingHandler;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.item.armor.ModifiableArmorItem;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableBowItem;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.library.utils.SlimeBounceHandler;
import slimeknights.tconstruct.shared.TinkerAttributes;
import slimeknights.tconstruct.shared.TinkerEffects;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.modifiers.effect.MagneticEffect;
import slimeknights.tconstruct.tools.modules.ranged.RestrictAngleModule;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Events to implement modifier specific behaviors, such as those defined by {@link TinkerDataKeys}. General hooks will typically be in {@link ToolEvents} */
public class ModifierEvents {
  /** Multiplier for experience drops from events */
  private static final TinkerDataKey<Float> PROJECTILE_EXPERIENCE = TConstruct.createKey("projectile_experience");
  // TODO: move following to TinkerDataKeys?
  /** Volatile data float for amount of experience granted per level. Used by both projectiles and held tools. */
  public static final Identifier EXPERIENCE = TConstruct.getResource("experience");
  /** Volatile data flag making a modifier grant the tool soulbound */
  public static final Identifier SOULBOUND = TConstruct.getResource("soulbound");
  /** Volatile data int for making a modifier on a shield grant reflecting */
  public static final Identifier REFLECTING = TConstruct.getResource("reflecting");

  @SuppressWarnings("removal")
  @SubscribeEvent
  static void onKnockback(LivingKnockBackEvent event) {
    LivingEntity entity = (LivingEntity) event.getEntity();
    Optional<TinkerDataCapability.Holder> dataCap = Optional.ofNullable(TinkerDataCapability.getData(entity));
    double knockback = entity.getAttributeValue(TinkerAttributes.KNOCKBACK_MULTIPLIER)
                     + dataCap.map(data -> data.get(TinkerDataKeys.KNOCKBACK)).orElse(0f);
    if (knockback != 1) {
      event.setStrength((float) (event.getStrength() * knockback));
    }
    // handle crystalstrike
    dataCap.ifPresent(data -> {
      // apply crystalbound bonus
      int crystalbound = data.get(TinkerDataKeys.CRYSTALSTRIKE, 0);
      if (crystalbound > 0) {
        RestrictAngleModule.onKnockback(event, crystalbound);
      }
    });
  }

  /** Reduce fall distance for fall damage */
  @SuppressWarnings("removal")
  @SubscribeEvent
  static void onLivingFall(LivingFallEvent event) {
    LivingEntity entity = (LivingEntity) event.getEntity();
    double boost = entity.getAttributeValue(TinkerAttributes.SAFE_FALL_DISTANCE) + ArmorStatModule.getStat(entity, TinkerDataKeys.JUMP_BOOST);
    if (boost != 0) {
      event.setDistance((float) Math.max(event.getDistance() - boost, 0));
    }
  }

  /** Called on jumping to boost the jump height of the entity */
  @SuppressWarnings("removal")
  @SubscribeEvent
  public static void onLivingJump(LivingJumpEvent event) {
    LivingEntity entity = (LivingEntity) event.getEntity();
    double boost = entity.getAttributeValue(TinkerAttributes.JUMP_BOOST) + ArmorStatModule.getStat(entity, TinkerDataKeys.JUMP_BOOST);
    if (boost > 0) {
      entity.setDeltaMovement(entity.getDeltaMovement().add(0, boost * 0.1, 0));
    }
  }

  /** Prevents effects on the entity */
  @SubscribeEvent(priority = EventPriority.HIGH)
  static void isPotionApplicable(MobEffectEvent.Applicable event) {
    MobEffectInstance effectInstance = event.getEffectInstance();
    if (effectInstance != null && EffectImmunityModule.getImmunity((LivingEntity) event.getEntity(), effectInstance.getEffect()) > effectInstance.getAmplifier()) {
      event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }
  }

  /** Called when the player dies to store the item in the original inventory */
  @SubscribeEvent
  static void onLivingDeath(LivingDeathEvent event) {
    // if a projectile kills the target, mark the projectile level
    DamageSource source = event.getSource();
    if (source != null && source.getDirectEntity() instanceof Projectile projectile) {
      ModifierNBT modifiers = EntityModifierCapability.getOrEmpty(projectile);
      if (!modifiers.isEmpty()) {
        TinkerDataCapability.Holder data = TinkerDataCapability.getData((LivingEntity) event.getEntity());
        if (data != null) {
          ModDataNBT projectileData = PersistentDataCapability.getOrWarn(projectile);
          data.put(PROJECTILE_EXPERIENCE, projectileData.getFloat(EXPERIENCE));
        }
      }
    }
    // this is the latest we can add slot markers to the items so we can return them to slots
    LivingEntity entity = (LivingEntity) event.getEntity();
    if (entity.level() instanceof ServerLevel serverLevel && !serverLevel.getGameRules().get(GameRules.KEEP_INVENTORY) && entity instanceof Player player && !(player instanceof FakePlayer)) {
      // start with the hotbar, must be soulbound or soul belt
      boolean soulBelt = ArmorLevelModule.getLevel(player, TinkerDataKeys.SOUL_BELT) > 0;
      Inventory inventory = player.getInventory();
      int hotbarSize = Inventory.getSelectionSize();
      for (int i = 0; i < hotbarSize; i++) {
        ItemStack stack = inventory.getItem(i);
        if (!stack.isEmpty() && (soulBelt || ModifierUtil.checkVolatileFlag(stack, SOULBOUND))) {
          CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
          tag.putInt(MantleEvents.SOULBOUND_SLOT, i);
          stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
      }
      // rest of the inventory, only check soulbound (no modifier that moves non-soulbound currently)
      // note this includes armor and offhand
      int totalSize = inventory.getContainerSize();
      for (int i = hotbarSize; i < totalSize; i++) {
        ItemStack stack = inventory.getItem(i);
        if (!stack.isEmpty() && ModifierUtil.checkVolatileFlag(stack, SOULBOUND)) {
          CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
          tag.putInt(MantleEvents.SOULBOUND_SLOT, i);
          stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
      }
    }
  }


  /* Experience */

  @SuppressWarnings("removal")
  @SubscribeEvent
  static void beforeBlockBreak(BlockDropsEvent event) {
    if (!(event.getBreaker() instanceof Player player)) {
      return;
    }
    // directly use modifier for held to ensure the correct hand applies
    // TODO: can we make that datapack configurable?
    double bonus = player.getAttributeValue(TinkerAttributes.EXPERIENCE_MULTIPLIER)
                 + ModifierUtil.getModifierLevel(player.getMainHandItem(), ModifierIds.experienced) * 0.5f
                 + ArmorStatModule.getStat(player, TinkerDataKeys.EXPERIENCE);
    event.setDroppedExperience((int)(event.getDroppedExperience() * bonus));
  }

  @SuppressWarnings("removal")
  @SubscribeEvent
  static void onExperienceDrop(LivingExperienceDropEvent event) {
    // boost entity experience if they are under the effects of experienced
    LivingEntity entity = (LivingEntity) event.getEntity();
    MobEffectInstance instance = entity.getEffect(TinkerEffects.holder(TinkerEffects.experienced));
    double multiplier = 1 + (instance != null ? instance.getAmplifier() : 0);

    // always add armor boost, unfortunately no good way to stop shield stuff here
    Player player = event.getAttackingPlayer();
    if (player != null) {
      multiplier += player.getAttributeValue(TinkerAttributes.EXPERIENCE_MULTIPLIER) + ArmorStatModule.getStat(player, TinkerDataKeys.EXPERIENCE);
    }
    // if the target was killed by an experienced arrow, use that level
    TinkerDataCapability.Holder data = TinkerDataCapability.getData(entity);
    Float projectileBoost = data != null ? data.get(PROJECTILE_EXPERIENCE) : null;
    if (projectileBoost != null) {
      multiplier += projectileBoost;
    // being -1 means no projectile was involved, so boost by held tool
    } else if (player != null) {
      ToolStack tool = Modifier.getHeldTool(player, ModifierLootingHandler.getLootingSlot(player));
      if (tool != null) {
        multiplier += tool.getVolatileData().getFloat(EXPERIENCE);
      }
    }
    event.setDroppedExperience((int) (event.getDroppedExperience() * multiplier));
  }

  /** Boosts critical hit damage */
  @SuppressWarnings("removal")
  @SubscribeEvent
  static void onCritical(CriticalHitEvent event) {
      // force critical if not already critical and in the air
      LivingEntity living = event.getEntity();

      // critical boost is defined where the base value is 150%, setting smaller amounts can reduce the critical damage
      // this event however is defined in terms of adding or subtracting critical, so just treat it as additive
      Holder<Attribute> attribute = TinkerAttributes.CRITICAL_DAMAGE;
      double criticalBoost = living.getAttributeValue(attribute) - attribute.value().getDefaultValue() + ArmorStatModule.getStat(living, TinkerDataKeys.CRITICAL_DAMAGE);
      if (criticalBoost > 0) {
        // make it critical if we meet our simpler conditions, note this does not boost attack damage
        boolean isCritical = event.isVanillaCritical() || event.isCriticalHit();
        if (!isCritical && TinkerPredicate.AIRBORNE.matches(living)) {
          isCritical = true;
          event.setCriticalHit(true);
        }

        // if we either were or became critical, time to boost
        if (isCritical) {
          // adds +5% critical hit per level
          event.setDamageMultiplier((float) (event.getDamageMultiplier() + criticalBoost));
        }
      }
  }

  @SuppressWarnings("removal")
  @SubscribeEvent
  static void onPotionStart(MobEffectEvent.Added event) {
    MobEffectInstance newEffect = event.getEffectInstance();
    // parity: official v3.12.1 skips effects with no curative items, which are the tinkers no milk effects (cooldowns,
    // bleeding, momentum and similar) and helmet charging. 26.1 has no per effect cure list, so match those classes.
    if (!newEffect.isInfiniteDuration() && !hasNoCurativeItems(newEffect.getEffect().value())) {
      // use two different stats based on whether the effect is beneficial
      boolean beneficial = newEffect.getEffect().value().isBeneficial();
      LivingEntity entity = (LivingEntity) event.getEntity();
      double multiplier = entity.getAttributeValue(beneficial ? TinkerAttributes.GOOD_EFFECT_DURATION : TinkerAttributes.BAD_EFFECT_DURATION)
                        + ArmorStatModule.getStat(entity, beneficial ? TinkerDataKeys.GOOD_EFFECT_DURATION : TinkerDataKeys.BAD_EFFECT_DURATION);
      if (multiplier != 1) {
        // adjust duration as requested
        newEffect.mapDuration(duration -> Math.max(1, (int)(duration * multiplier)));
      }
    }
  }

  /**
   * Stand in for the 1.20 check {@code effect.getCurativeItems().isEmpty()}: true for the effects official v3.12.1 gives no
   * curative items (tinkers no milk effects and helmet charging). Official code skips these in effect duration changes and
   * when plague copies effects. 26.1 has no per effect cure list, so this matches the classes instead.
   * Added by parity/modifiers.
   */
  public static boolean hasNoCurativeItems(MobEffect effect) {
    return effect instanceof slimeknights.tconstruct.tools.modifiers.effect.NoMilkEffect
        || effect instanceof slimeknights.tconstruct.tools.modifiers.effect.HelmetChargingEffect;
  }

  /**
   * Step height gained from modifiers and effects, the 26.1 equivalent of Forge's old STEP_HEIGHT_ADDITION attribute value.
   * Added by parity/modifiers for {@link #bounceOnFall(LivingFallEvent)}.
   */
  static double stepHeightBonus(LivingEntity living) {
    var instance = living.getAttribute(Attributes.STEP_HEIGHT);
    if (instance == null) {
      return 0;
    }
    // Forge's addition attribute ranged from -512 to 512, so a negative bonus stays negative here too
    return instance.getValue() - instance.getBaseValue();
  }

  /** Called when an entity lands to handle bouncing */
  @SubscribeEvent
  static void bounceOnFall(LivingFallEvent event) {
    LivingEntity living = (LivingEntity) event.getEntity();
    // using fall distance as the event distance could be reduced by jump boost
    // parity: official v3.12.1 adds Forge's STEP_HEIGHT_ADDITION (default 0) to 0.5. 26.1 only has the vanilla STEP_HEIGHT
    // attribute (player base 0.6), so the port's 0.5 + STEP_HEIGHT raised the minimum bounce height to 1.1 blocks.
    // Only the bonus above the entity's base step height matches the old addition attribute.
    if (living == null || (living.fallDistance < 3 && living.getDeltaMovement().y > -0.3) || living.fallDistance <= 0.5f + stepHeightBonus(living)) {
      return;
    }
    // can the entity bounce?
    if (living.getAttributeValue(TinkerAttributes.BOUNCY) < 1) {
      return;
    }

    // reduced fall damage when crouching
    if (living.isSuppressingBounce()) {
      event.setDamageMultiplier(0.5f);
      return;
    } else {
      event.setDamageMultiplier(0.0f);
    }

    // server players behave differently than non-server players, they have no velocity during the event, so we need to reverse engineer it
    Vec3 motion = living.getDeltaMovement();
    if (living instanceof ServerPlayer) {
      // velocity is lost on server players, but we dont have to defer the bounce
      double gravity = living.getAttributeValue(Attributes.GRAVITY);
      double time = Math.sqrt(living.fallDistance / gravity);
      double velocity = gravity * time;
      living.setDeltaMovement(motion.x / 0.975f, velocity, motion.z / 0.975f);
      living.hurtMarked = true;

      // preserve momentum
      SlimeBounceHandler.addBounceHandler(living);
    } else {
      // for non-players, need to defer the bounce
      // only slow down half as much when bouncing
      float factor = living.fallDistance < 2 ? -0.7f : -0.9f;
      living.setDeltaMovement(motion.x / 0.975f, motion.y * factor, motion.z / 0.975f);
      SlimeBounceHandler.addBounceHandler(living, living.getDeltaMovement());
    }
    // update airborn status
    event.setDistance(0.0F);
    if (!living.level().isClientSide()) {
      // parity: official v3.12.1 sets hasImpulse here so the server sends the bounce velocity to tracking clients right away.
      // 26.1 renamed that field to needsSync. The port had replaced it with a no-op:
      //   living.setDeltaMovement(living.getDeltaMovement());
      living.needsSync = true;
      event.setCanceled(true);
      living.setOnGround(false); // need to be on ground for server to process this event
    }
    living.playSound(Sounds.SLIMY_BOUNCE.getSound(), 1f, 1f);
  }

  @SubscribeEvent
  static void onProjectile(LivingGetProjectileEvent event) {
    // the held projectile method is not stack sensitive, so use this instead
    ItemStack bow = event.getProjectileWeaponItemStack();
    ItemStack ammo = event.getProjectileItemStack();
    // if the bow supports it, and we currently have arrows or nothing, we have a chance to swap the ammo
    // skip if the b
    if (bow.is(TinkerTags.Items.BALLISTAS) && ModifierUtil.checkVolatileFlag(bow, ModifiableBowItem.KEY_BALLISTA) && (ammo.isEmpty() || ammo.is(ItemTags.ARROWS))) {
      // check active flag
      int flag = ModifierUtil.getPersistentInt(bow, ModifiableBowItem.KEY_BALLISTA, 0);

      // if requesting a held ballista or haven't decided, find it in either hand
      if (flag <= ModifiableBowItem.FLAG_BALLISTA_HELD) {
        // try both hands, but don't return the bow itself
        LivingEntity entity = (LivingEntity) event.getEntity();
        ItemStack check = entity.getOffhandItem();
        if (check != bow && check.is(TinkerTags.Items.BALLISTA_AMMO)) {
          event.setProjectileItemStack(check);
        }
        check = entity.getMainHandItem();
        if (check != bow && check.is(TinkerTags.Items.BALLISTA_AMMO)) {
          event.setProjectileItemStack(check);
        }
      // if requesting a ballista from the quiver, cancel whatever stack we got from inventory
      } else if (flag == ModifiableBowItem.FLAG_BALLISTA_QUIVER) {
        event.setProjectileItemStack(ItemStack.EMPTY);
      }
    }
  }

  @SuppressWarnings("removal") // lets us work with Neo 1.20 for now
  @SubscribeEvent(priority = EventPriority.LOW) // lower priority so general modifier hook runs first
  static void projectileImpact(ProjectileImpactEvent event) {
    Entity entity = (Entity) event.getEntity();
    Level level = entity.level();
    Projectile projectile = (Projectile) event.getProjectile();
    HitResult hit = (HitResult) event.getRayTraceResult();
    if (hit.getType() == Type.ENTITY && ((EntityHitResult) hit).getEntity() instanceof LivingEntity target) {
      // reflecting //
      // handle blacklist for projectiles
      if (!level.isClientSide() && !RegistryHelper.contains(TinkerTags.EntityTypes.REFLECTING_BLACKLIST, projectile.getType()) && target != projectile.getOwner() && target.isUsingItem()) {
        ItemStack stack = target.getUseItem();
        // living entity must be using one of our shields
        if (stack.is(TinkerTags.Items.SHIELDS)) {
          ToolStack tool = ToolStack.from(stack);
          // make sure we actually have the modifier
          int reflectingTime = tool.getVolatileData().getInt(REFLECTING);
          if (reflectingTime > 0) {
            ModifierEntry activeModifier = GeneralInteractionModifierHook.getActiveModifier(tool);
            if (activeModifier != ModifierEntry.EMPTY) {
              GeneralInteractionModifierHook hook = activeModifier.getHook(ModifierHooks.GENERAL_INTERACT);
              int time = hook.getUseDuration(tool, activeModifier) - target.getUseItemRemainingTicks();
              // must be blocking, started blocking within the last 2*level seconds, and be within the block angle
              if (hook.getUseAction(tool, activeModifier) == ItemUseAnimation.BLOCK
                && (time >= 5 && time < reflectingTime)
                && InteractionHandler.canBlock(target, projectile.position(), tool)) {

                // time to actually reflect, this code is strongly based on code from the Parry mod
                // take ownership of the projectile so it counts as a player kill, except in the case of fishing bobbers
                if (!RegistryHelper.contains(TinkerTags.EntityTypes.REFLECTING_PRESERVE_OWNER, projectile.getType())) {
                  // arrows are dumb and mutate their pickup status when owner is set, so disagree and set it back
                    if (projectile instanceof AbstractArrow arrow) {
                    AbstractArrow.Pickup pickup = arrow.pickup;
                    arrow.setOwner(target);
                    arrow.pickup = pickup;
                  } else {
                    projectile.setOwner(target);
                  }
                }

                Vec3 reboundAngle = target.getLookAngle();
                // use the shield accuracy and velocity stats when reflecting
                float velocity = ConditionalStatModifierHook.getModifiedStat(tool, target, ToolStats.VELOCITY) * 1.1f;
                projectile.shoot(reboundAngle.x, reboundAngle.y, reboundAngle.z, velocity, ModifierUtil.getInaccuracy(tool, target));
                if (projectile instanceof AbstractHurtingProjectile hurting) {
                  hurting.setDeltaMovement(reboundAngle.normalize().scale(0.1));
                }
                if (target.getType() == EntityType.PLAYER) {
                  TinkerNetwork.getInstance().sendVanillaPacket(new ClientboundSetEntityMotionPacket(projectile), target);
                }
                level.playSound(null, target.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 1.5F + level.getRandom().nextFloat() * 0.4F);
                event.setCanceled(true);
                // damage the shield, and stop using it if needed
                if (ToolDamageUtil.damageAnimated(tool, 3, target, target.getUsedItemHand())) {
                  target.stopUsingItem();
                  entity.playSound(SoundEvents.SHIELD_BREAK.value(), 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
                }
              }
            }
          }
        }
      }

      // enderference //
      // TODO: this is a lot of code to make enderference work. A mixin would likely be better and provide better compatability
      // endermen are hardcoded to not take arrow damage, so disagree by reimplementing arrow damage right here
      // blacklist lets us not run on tridents or thrown tools. Former does not work with enderference, latter does so internally
      EntityType<?> projectileType = projectile.getType();
      if (TinkerEffects.needsEnderferenceOverride(target) && !RegistryHelper.contains(TinkerTags.EntityTypes.ENDERFERENCE_ARROW_BLACKLIST, projectileType) && projectile instanceof AbstractArrow arrow) {
        // first, give up if we reached pierce capacity, and ensure list are created
        if (arrow.getPierceLevel() > 0) {
          if (arrow.piercingIgnoreEntityIds == null) {
            arrow.piercingIgnoreEntityIds = new IntOpenHashSet(5);
          }
          if (arrow.piercedAndKilledEntities == null) {
            arrow.piercedAndKilledEntities = Lists.newArrayListWithCapacity(5);
          }
          if (arrow.piercingIgnoreEntityIds.size() >= arrow.getPierceLevel() + 1) {
            ReusableProjectile.discard(projectile);
            event.setCanceled(true);
            return;
          }
          arrow.piercingIgnoreEntityIds.add(target.getId());
        }

        // calculate damage, bonus on crit
        int damage = Mth.ceil(Mth.clamp(arrow.getDeltaMovement().length() * arrow.baseDamage, 0.0D, Integer.MAX_VALUE));
        if (arrow.isCritArrow()) {
          damage = (int) Math.min(target.getRandom().nextInt(damage / 2 + 2) + (long) damage, Integer.MAX_VALUE);
        }

        // create damage source, do not use projectile sources as that makes endermen ignore it
        Entity owner = arrow.getOwner();
        DamageSource damageSource = CombatHelper.damageSource(TinkerDamageTypes.MELEE_ARROW, projectile, owner);
        LivingEntity livingOwner = ModifierUtil.asLiving(owner);
        if (livingOwner != null) {
          livingOwner.setLastHurtMob(target);
        }

        // handle fire
        int remainingFire = target.getRemainingFireTicks();
        if (arrow.isOnFire()) {
          target.igniteForSeconds(5);
        }

        // hurt the enderman
        if (target.hurtOrSimulate(damageSource, (float) damage)) {
          if (!level.isClientSide() && arrow.getPierceLevel() <= 0) {
            target.setArrowCount(target.getArrowCount() + 1);
          }

          // knockback from punch
          arrow.doKnockback(target, damageSource);

          if (level instanceof ServerLevel serverLevel) {
            EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, target, damageSource, arrow.getWeaponItem());
          }

          arrow.doPostHurtEffects(target);

          if (!target.isAlive() && arrow.piercedAndKilledEntities != null) {
            arrow.piercedAndKilledEntities.add(target);
          }

          if (!level.isClientSide() && owner instanceof ServerPlayer player) {
            if (arrow.piercedAndKilledEntities != null) {
              CriteriaTriggers.KILLED_BY_ARROW.trigger(player, arrow.piercedAndKilledEntities, arrow.getWeaponItem());
            } else if (!target.isAlive()) {
              CriteriaTriggers.KILLED_BY_ARROW.trigger(player, List.of(target), arrow.getWeaponItem());
            }
          }

          arrow.playSound(arrow.soundEvent, 1.0F, 1.2F / (target.getRandom().nextFloat() * 0.2F + 0.9F));
          if (arrow.getPierceLevel() <= 0) {
            ReusableProjectile.discard(projectile);
          }
        } else {
          // reset fire and drop the arrow
          target.setRemainingFireTicks(remainingFire);
          arrow.setDeltaMovement(arrow.getDeltaMovement().scale(-0.1D));
          arrow.setYRot(arrow.getYRot() + 180.0F);
          arrow.yRotO += 180.0F;
          if (level instanceof ServerLevel serverLevel && arrow.getDeltaMovement().lengthSqr() < 1.0E-7D) {
            if (arrow.pickup == AbstractArrow.Pickup.ALLOWED) {
              arrow.spawnAtLocation(serverLevel, arrow.getPickupItemStackOrigin().copy(), 0.1F);
            }

            ReusableProjectile.discard(projectile);
          }
        }
        // cancel event so arrow does not bounce
        event.setCanceled(true);
      }
    }
  }
  @SubscribeEvent
  static void onTeleport(EntityTeleportEvent event) {
    if (event.getEntity() instanceof LivingEntity living && living.hasEffect(TinkerEffects.holder(TinkerEffects.enderference))) {
      event.setCanceled(true);
    }
  }

  @SubscribeEvent
  static void onTinkerTeleport(TinkerTeleportEvent event) {
    if (event.getEntity() instanceof LivingEntity living && living.hasEffect(TinkerEffects.holder(TinkerEffects.enderference))) {
      event.setCanceled(true);
    }
  }

  /** Called to perform the magnet for armor */
  @SubscribeEvent
  static void onLivingTick(EntityTickEvent.Post event) {
    if (!(event.getEntity() instanceof LivingEntity entity)) {
      return;
    }
    if (!entity.isSpectator() && (entity.tickCount & 1) == 0) {
      int level = ArmorLevelModule.getLevel(entity, TinkerDataKeys.MAGNET);
      if (level > 0) {
        MagneticEffect.applyMagnet(entity, level - 1);
      }
    }
    elytraFlightTick(entity);
  }

  /**
   * Runs the elytra flight modifier hooks for a Tinkers chestplate.
   * 26.1 dropped {@code IItemExtension#elytraFlightTick}, so gliding itself comes from the glider component
   * and this only exists to keep the modifier hooks running.
   */
  private static void elytraFlightTick(LivingEntity entity) {
    if (!entity.isFallFlying()) {
      return;
    }
    ItemStack chestplate = entity.getItemBySlot(EquipmentSlot.CHEST);
    if (chestplate.getItem() instanceof ModifiableArmorItem armor
        && !armor.elytraFlightTick(chestplate, entity, entity.getFallFlyingTicks())) {
      entity.stopFallFlying();
    }
  }
}
