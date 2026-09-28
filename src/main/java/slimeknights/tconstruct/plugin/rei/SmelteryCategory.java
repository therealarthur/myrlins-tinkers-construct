package slimeknights.tconstruct.plugin.rei;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;

/** Explicit consumed-input, output and reusable-catalyst rows, using REI's standard accessible slots. */
final class SmelteryCategory implements DisplayCategory<SmelteryDisplay> {
  private final CategoryIdentifier<SmelteryDisplay> id;
  private final Component title;
  private final ItemLike icon;

  SmelteryCategory(CategoryIdentifier<SmelteryDisplay> id, Component title, ItemLike icon) {
    this.id = id;
    this.title = title;
    this.icon = icon;
  }

  @Override public CategoryIdentifier<SmelteryDisplay> getCategoryIdentifier() { return id; }
  @Override public Component getTitle() { return title; }
  @Override public Renderer getIcon() { return EntryStacks.of(icon); }
  @Override public int getDisplayHeight() { return 158; }

  @Override
  public int getDisplayWidth(SmelteryDisplay display) {
    int slots = Math.max(display.inputs().size(), Math.max(display.outputs().size(), display.catalysts().size()));
    return Math.max(230, 24 + slots * 22);
  }

  @Override
  public List<Widget> setupDisplay(SmelteryDisplay display, Rectangle bounds) {
    List<Widget> widgets = new ArrayList<>();
    widgets.add(Widgets.createRecipeBase(bounds));
    addRow(widgets, bounds, 7, display.inputs(), Component.translatableWithFallback("rei.tconstruct.inputs", "Consumed inputs"), 1);
    addRow(widgets, bounds, 40, display.outputs(), Component.translatableWithFallback("rei.tconstruct.outputs", "Outputs"), 2);
    addRow(widgets, bounds, 73, display.catalysts(), Component.translatableWithFallback("rei.tconstruct.catalysts", "Required, not consumed"), 0);
    for (int i = 0; i < display.notes().size(); i++) {
      widgets.add(Widgets.createLabel(new Point(bounds.x + 8, bounds.y + 108 + i * 11), display.notes().get(i))
        .leftAligned().noShadow().color(0xFF404040, 0xFFB0B0B0));
    }
    return widgets;
  }

  private static void addRow(List<Widget> widgets, Rectangle bounds, int y, List<EntryIngredient> ingredients, Component label, int role) {
    if (ingredients.isEmpty()) {
      return;
    }
    widgets.add(Widgets.createLabel(new Point(bounds.x + 8, bounds.y + y), label).leftAligned().noShadow().color(0xFF404040, 0xFFB0B0B0));
    for (int i = 0; i < ingredients.size(); i++) {
      var slot = Widgets.createSlot(new Point(bounds.x + 9 + i * 22, bounds.y + y + 11));
      // Settings are attached to copies so laying out a page cannot change cached/serialized display data.
      for (EntryStack<?> entry : ingredients.get(i)) {
        var copy = entry.copy().setting(EntryStack.Settings.FLUID_AMOUNT_VISIBLE, true);
        if (role == 0) {
          copy.tooltip(Component.translatable("jei.tconstruct.alloy.catalyst"));
        }
        slot.entry(copy);
      }
      if (role == 1) slot.markInput();
      if (role == 2) slot.markOutput();
      widgets.add(slot);
    }
  }
}
