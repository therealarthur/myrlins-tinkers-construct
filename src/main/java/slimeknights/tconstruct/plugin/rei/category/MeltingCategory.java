package slimeknights.tconstruct.plugin.rei.category;

import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.fluid.tooltip.FluidTooltipHandler;
import slimeknights.tconstruct.library.client.recipe.RecipeLayout;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.plugin.rei.SmelteryDisplay;
import slimeknights.tconstruct.plugin.rei.category.ReiLayout.Origin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Melting and foundry, official {@code melting/MeltingCategory} and {@code melting/FoundryCategory} (132 by 40):
 * input item, output tank (with byproducts for the foundry), melting time arrow, ore boost marker, temperature, and
 * the usable fuels (solid fuel examples when the solid fuel is hot enough, melting only).
 */
public class MeltingCategory extends TinkerDisplayCategory {
  private final boolean foundry;
  private final Supplier<FuelFacts> fuels;

  public MeltingCategory(CategoryIdentifier<SmelteryDisplay> id, Component title, ItemLike icon, boolean foundry, Supplier<FuelFacts> fuels) {
    super(id, title, EntryStacks.of(icon), 132, 40);
    this.foundry = foundry;
    this.fuels = fuels;
  }

  @Override
  protected void layout(SmelteryDisplay display, CompoundTag layout, Origin origin, List<Widget> widgets) {
    widgets.add(ReiLayout.texture(origin, ReiLayout.MELTING, 0, 0, 0, 0, 132, 40));
    int temperature = RecipeLayout.getInt(layout, RecipeLayout.TEMPERATURE, 0);
    int time = RecipeLayout.getInt(layout, RecipeLayout.TIME, 0);
    int ore = RecipeLayout.getInt(layout, RecipeLayout.ORE, RecipeLayout.ORE_NONE);

    widgets.add(ReiLayout.slot(origin, 24, 18, get(display.inputs(), 0), false).markInput());

    if (foundry) {
      int capacity = ReiLayout.maxAmount(display.outputs(), FluidValues.METAL_BLOCK);
      ReiLayout.multipleFluids(origin, 96, 4, 32, 32, display.outputs(), capacity, index -> fluid -> List.of())
        .forEach(slot -> widgets.add(slot.markOutput()));
    } else {
      int melter = RecipeLayout.getInt(layout, RecipeLayout.MELTER_AMOUNT, -1);
      int smeltery = RecipeLayout.getInt(layout, RecipeLayout.SMELTERY_AMOUNT, -1);
      widgets.add(ReiLayout.tank(origin, 96, 4, 32, 32, get(display.outputs(), 0), FluidValues.METAL_BLOCK,
        fluid -> oreLines(fluid, ore, melter, smeltery)).markOutput());
      widgets.add(ReiLayout.texture(origin, ReiLayout.MELTING, 96, 4, 132, 0, 32, 32));
    }

    // melting time; official animates over time * 5 ticks and shows time / 4 seconds
    widgets.add(ReiLayout.arrow(origin, 56, 18, Math.max(1, time * 5)));
    widgets.add(ReiLayout.tooltip(origin, 56, 18, 24, 17, List.of(Component.translatable("jei.tconstruct.melting.time", time / 4))));
    if (ore != RecipeLayout.ORE_NONE) {
      widgets.add(ReiLayout.texture(origin, ReiLayout.MELTING, 85, 28, 132, 32, 8, 8));
      widgets.add(ReiLayout.tooltip(origin, 83, 26, 12, 12, List.of(Component.translatable("jei.tconstruct.melting.ore"))));
    }
    widgets.add(ReiLayout.centered(origin, 0, 3, 113, Component.translatable("jei.tconstruct.temperature", temperature), ReiLayout.GRAY, false));

    // fuels, render-only in official
    FuelFacts facts = fuels.get();
    int fuelHeight = 32;
    if (!foundry && facts.hasSolid() && temperature <= facts.solidTemperature() && !facts.solidExamples().isEmpty()) {
      fuelHeight = 15;
      widgets.add(ReiLayout.texture(origin, ReiLayout.MELTING, 1, 19, 164, 0, 18, 20));
      EntryIngredient solid = ReiLayout.withTooltip(EntryIngredients.ofItemStacks(facts.solidExamples()), List.of(
        Component.translatable("jei.tconstruct.temperature", facts.solidTemperature()).withStyle(ChatFormatting.GRAY),
        Component.translatable("jei.tconstruct.melting.multiplier", facts.solidRate() / 10f).withStyle(ChatFormatting.GRAY)));
      widgets.add(ReiLayout.slot(origin, 2, 22, solid, false));
    }
    widgets.add(ReiLayout.tank(origin, 4, 4, 12, fuelHeight, ReiLayout.fluidIngredient(facts.usableFuels(temperature)), 0,
      fluid -> fuelLines(facts, fluid)));
  }

  /** Official ore tooltip: melter and smeltery amounts when the ore boost makes them differ. */
  private static List<Component> oreLines(FluidStack fluid, int ore, int melter, int smeltery) {
    List<Component> lines = new ArrayList<>();
    if (ore != RecipeLayout.ORE_NONE && melter >= 0 && smeltery >= 0 && melter != smeltery) {
      lines.add(Component.translatable("jei.tconstruct.melting.melter").withStyle(ChatFormatting.GRAY, ChatFormatting.UNDERLINE));
      FluidTooltipHandler.appendMaterialNoShift(fluid.getFluid(), melter, lines);
      lines.add(Component.empty());
      lines.add(Component.translatable("jei.tconstruct.melting.smeltery").withStyle(ChatFormatting.GRAY, ChatFormatting.UNDERLINE));
      FluidTooltipHandler.appendMaterialNoShift(fluid.getFluid(), smeltery, lines);
    }
    return lines;
  }

  /** Official fuel tooltip: temperature and speed multiplier. */
  static List<Component> fuelLines(FuelFacts facts, FluidStack fluid) {
    int[] fuel = facts.liquidFuel(fluid);
    if (fuel == null) return List.of();
    return List.of(
      Component.translatable("jei.tconstruct.temperature", fuel[0]).withStyle(ChatFormatting.GRAY),
      Component.translatable("jei.tconstruct.melting.multiplier", fuel[1] / 10f).withStyle(ChatFormatting.GRAY));
  }
}
