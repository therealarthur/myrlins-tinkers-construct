package slimeknights.tconstruct.tools.modifiers.traits.skull;

import static slimeknights.tconstruct.library.fluid.FluidActions.EXECUTE;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.bus.api.EventPriority;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffect;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffectContext;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.modifiers.impl.SingleLevelModifier;
import slimeknights.tconstruct.library.modifiers.modules.technical.ArmorLevelModule;
import slimeknights.tconstruct.library.modifiers.modules.technical.CureOnRemovalModule;
import slimeknights.tconstruct.library.module.ModuleHookMap.Builder;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability.TinkerDataKey;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.tools.TinkerModifiers;

// arthur.9: official extends SingleLevelModifier (level 2 and up show "Strong Bones II") and scales the milk durations by
// level; the port used NoLevelsModifier and fixed durations, which equal official at level 1 only.
public class StrongBonesModifier extends SingleLevelModifier {
  /** Key for modifiers that are boosted by drinking milk */
  public static final TinkerDataKey<Integer> CALCIFIABLE = TConstruct.createKey("calcifable");
  /** Module to add to any calcifiable modifiers */
  public static final ArmorLevelModule CALCIFIABLE_MODULE = new ArmorLevelModule(CALCIFIABLE, false, TinkerTags.Items.HELD_ARMOR);

  public StrongBonesModifier() {
    // TODO: move this out of constructor to generalized logic
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingEntityUseItemEvent.Finish.class, StrongBonesModifier::onItemFinishUse);
  }

  @Override
  protected void registerHooks(Builder hookBuilder) {
    super.registerHooks(hookBuilder);
    hookBuilder.addModule(CureOnRemovalModule.HELMET);
  }

  /** Single duration form used before arthur.9, kept for reference: the same as a flat duration with no per level part */
  @SuppressWarnings("unused")
  private static boolean drinkMilk(LivingEntity living, int duration, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action) {
    return drinkMilk(living, duration, 0, action);
  }

  /** Official form: each effect lasts {@code flat + eachLevel * level} ticks for the level that grants it */
  @SuppressWarnings("unchecked")
  private static boolean drinkMilk(LivingEntity living, int flat, int eachLevel, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action) {
    // strong bones has to be the helmet as we use it for curing
    // TODO 1.20: can use the new cure effects to make this work in any slot
    ItemStack helmet = living.getItemBySlot(EquipmentSlot.HEAD);
    boolean didSomething = false;
    int level = ModifierUtil.getModifierLevel(helmet, TinkerModifiers.strongBones.getId());
    if (level > 0) {
      MobEffectInstance effect = new MobEffectInstance(MobEffects.RESISTANCE, flat + eachLevel * level);
      // on simulate, don't apply the effect, just ask if we can apply
      didSomething = action.execute() ? living.addEffect(effect) : living.canBeAffected(effect);
      // quick exit on simulate: no more information needed
      if (didSomething && action.simulate()) {
        return true;
      }
    }
    level = ArmorLevelModule.getLevel(living, CALCIFIABLE);
    if (level > 0) {
      MobEffectInstance effect = new MobEffectInstance((Holder<MobEffect>)(Holder<?>) TinkerModifiers.calcifiedEffect, flat + eachLevel * level, 0);
      didSomething |= action.execute() ? living.addEffect(effect) : living.canBeAffected(effect);
    }
    return didSomething;
  }

  /** Called when you finish drinking milk */
  private static void onItemFinishUse(LivingEntityUseItemEvent.Finish event) {
    LivingEntity living = (LivingEntity) event.getEntity();
    if (event.getItem().getItem() == Items.MILK_BUCKET) {
      // official: 30 seconds plus 30 seconds per level (60 seconds at level 1, as before)
      drinkMilk(living, 600, 600, EXECUTE);
    }
  }


  /* Spilling effect */

  /** Singleton instance spilling effect */
  public static final FluidEffect<FluidEffectContext.Entity> FLUID_EFFECT = FluidEffect.simple((fluid, scale, context, action) -> {
    LivingEntity target = context.getLivingTarget();
    // while we could scale, doing it flat ensures we don't charge extra
    // official: 10 seconds per level of the granting modifier, scaled by the fluid amount
    if (target != null && drinkMilk(target, 0, (int)(20*10 * scale.value()), action)) {
      return scale.value();
    }
    return 0;
  });
}
