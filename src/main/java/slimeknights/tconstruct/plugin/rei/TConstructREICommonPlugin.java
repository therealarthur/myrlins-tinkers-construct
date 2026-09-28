package slimeknights.tconstruct.plugin.rei;

import me.shedaniel.rei.api.common.display.DisplaySerializerRegistry;
import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.api.common.entry.comparison.FluidComparatorRegistry;
import me.shedaniel.rei.api.common.plugins.REICommonPlugin;
import me.shedaniel.rei.forge.REIPluginCommon;
import net.minecraft.core.registries.BuiltInRegistries;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

/** Discovered only by REI. TConstruct's normal initialization never references optional viewer classes. */
@REIPluginCommon
public class TConstructREICommonPlugin implements REICommonPlugin {
  @Override
  public void registerDisplaySerializer(DisplaySerializerRegistry registry) {
    registry.register(TConstruct.getResource("smeltery_display"), SmelteryDisplay.SERIALIZER);
  }

  @Override
  public void registerItemComparators(ItemComparatorRegistry registry) {
    // Ignore damage, names and unrelated components while retaining the same material semantics as JEI.
    for (var item : BuiltInRegistries.ITEM) {
      if (item instanceof IMaterialItem) {
        registry.register((context, stack) -> {
          var material = IMaterialItem.getMaterialFromStack(stack);
          return (context.isExact() ? material.toString() : material.getId().toString()).hashCode();
        }, item);
      }
    }
  }

  @Override
  public void registerFluidComparators(FluidComparatorRegistry registry) {
    registry.registerNbt(TinkerFluids.potion.get());
  }
}
