package slimeknights.tconstruct.plugin.rei.category;

import me.shedaniel.rei.api.client.gui.widgets.Slot;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;
import slimeknights.tconstruct.library.client.recipe.RecipeLayout;
import slimeknights.tconstruct.plugin.rei.SmelteryDisplay;
import slimeknights.tconstruct.plugin.rei.category.ReiLayout.Origin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Alloying, official {@code AlloyRecipeCategory} (172 by 62): input fluids in recipe order sharing one scale with the
 * output, catalysts marked "Not consumed", temperature, arrow and the usable fuels.
 */
public class AlloyCategory extends TinkerDisplayCategory {
  private final Supplier<FuelFacts> fuels;

  public AlloyCategory(CategoryIdentifier<SmelteryDisplay> id, Component title, ItemLike icon, Supplier<FuelFacts> fuels) {
    super(id, title, EntryStacks.of(icon), 172, 62);
    this.fuels = fuels;
  }

  @Override
  protected void layout(SmelteryDisplay display, CompoundTag layout, Origin origin, List<Widget> widgets) {
    widgets.add(ReiLayout.texture(origin, ReiLayout.ALLOY, 0, 0, 0, 0, 172, 62));
    int temperature = RecipeLayout.getInt(layout, RecipeLayout.TEMPERATURE, 0);
    int mask = RecipeLayout.getInt(layout, RecipeLayout.CATALYST_MASK, 0);

    // rebuild recipe order from the separate input and catalyst lists
    List<EntryIngredient> ordered = new ArrayList<>();
    List<Boolean> catalyst = new ArrayList<>();
    int input = 0;
    int catalystIndex = 0;
    int total = display.inputs().size() + display.catalysts().size();
    for (int i = 0; i < total; i++) {
      boolean isCatalyst = i < 31 && (mask & (1 << i)) != 0 && catalystIndex < display.catalysts().size() || input >= display.inputs().size();
      ordered.add(isCatalyst ? display.catalysts().get(catalystIndex++) : display.inputs().get(input++));
      catalyst.add(isCatalyst);
    }
    List<EntryIngredient> scaled = new ArrayList<>(ordered);
    scaled.addAll(display.outputs());
    int capacity = ReiLayout.maxAmount(scaled, ReiLayout.maxAmount(display.outputs(), 1));
    Component notConsumed = Component.translatable("jei.tconstruct.alloy.catalyst").withStyle(ChatFormatting.ITALIC);
    List<Slot> slots = ReiLayout.multipleFluids(origin, 19, 11, 48, 32, ordered, capacity,
      index -> fluid -> catalyst.get(index) ? List.of(notConsumed) : List.of());
    for (int i = 0; i < slots.size(); i++) {
      widgets.add(catalyst.get(i) ? slots.get(i) : slots.get(i).markInput());
    }
    widgets.add(ReiLayout.tank(origin, 137, 11, 16, 32, get(display.outputs(), 0), capacity).markOutput());

    widgets.add(ReiLayout.arrow(origin, 90, 21, 200));
    widgets.add(ReiLayout.centered(origin, 33, 5, 139, Component.translatable("jei.tconstruct.temperature", temperature), ReiLayout.GRAY, false));

    FuelFacts facts = fuels.get();
    widgets.add(ReiLayout.tank(origin, 94, 43, 16, 16, ReiLayout.fluidIngredient(facts.usableFuels(temperature)), 0,
      fluid -> {
        int[] fuel = facts.liquidFuel(fluid);
        return fuel == null ? List.of() : List.of(Component.translatable("jei.tconstruct.temperature", fuel[0]).withStyle(ChatFormatting.GRAY));
      }));
    widgets.add(ReiLayout.texture(origin, ReiLayout.ALLOY, 94, 43, 172, 17, 16, 16));
  }
}
