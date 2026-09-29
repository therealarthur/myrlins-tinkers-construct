package slimeknights.tconstruct.plugin.rei.category;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * Fuel facts from the current synchronized snapshot, used by the melting, foundry, alloy and entity melting layouts
 * for their render-only fuel slots, like official MeltingFuelHandler.
 */
public interface FuelFacts {
  /** Liquid fuels hot enough for the temperature, coolest first. */
  List<FluidStack> usableFuels(int temperature);

  /** True when the server sent a solid fuel recipe. */
  boolean hasSolid();

  /** Solid fuel temperature, or zero without a solid fuel. */
  int solidTemperature();

  /** Solid fuel rate, official shows {@code rate / 10}. */
  int solidRate();

  /** Example solid fuel items from {@code tconstruct:fuel_examples}. */
  List<ItemStack> solidExamples();

  /** Liquid fuel temperature and rate for tooltips, or null when the fluid is not a fuel. */
  int[] liquidFuel(FluidStack fluid);
}
