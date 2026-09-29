package slimeknights.tconstruct.library.modifiers.fluid.general;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.tconstruct.library.modifiers.fluid.EffectLevel;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffect;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffectContext;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;

/** Effect to drop an item at the target */
public record DropItemFluidEffect(ItemOutput item) implements FluidEffect<FluidEffectContext> {
  public static final RecordLoadable<DropItemFluidEffect> LOADER = ItemOutput.Loadable.REQUIRED_STACK.flatXmap(DropItemFluidEffect::new, DropItemFluidEffect::item);

  public DropItemFluidEffect(ItemLike item) {
    this(ItemOutput.fromItem(item));
  }

  @Override
  public RecordLoadable<DropItemFluidEffect> getLoader() {
    return LOADER;
  }

  @Override
  public float apply(FluidStack fluid, EffectLevel level, FluidEffectContext context, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action) {
    int count = (int)(level.value() * item.getCount());
    if (count > 0) {
      if (action.execute()) {
        ModifierUtil.dropItem(context.getLevel(), context.getLocation(), item.get().copyWithCount(count * item.getCount()));
      }
      return (float) count / item.getCount();
    }
    return 0;
  }

  @Override
  public Component getDescription(RegistryAccess registryAccess) {
    // Official used item.get(). In 26.1 the output caches a stack made while data loads, before item components are
    // bound, so it has no name component and the page read "Drops ". copy() rebinds the stack to the registered item.
    return FluidEffect.makeTranslation(getLoader(), item.copy().getHoverName());
  }
}
