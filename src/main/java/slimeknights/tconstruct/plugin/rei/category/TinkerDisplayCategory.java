package slimeknights.tconstruct.plugin.rei.category;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.plugin.rei.SmelteryDisplay;
import slimeknights.tconstruct.plugin.rei.category.ReiLayout.Origin;

import java.util.ArrayList;
import java.util.List;

/**
 * Base for the per-category REI layouts. Each subclass reproduces one official 3.12.1 JEI category at its official
 * size; REI adds {@link ReiLayout#PADDING} on each side for its panel border.
 * <p>
 * A layout failure must never take the recipe screen down, so any exception falls back to a plain list of the
 * display's entries with the error logged once per display.
 */
public abstract class TinkerDisplayCategory implements DisplayCategory<SmelteryDisplay> {
  private final CategoryIdentifier<SmelteryDisplay> id;
  private final Component title;
  private final Renderer icon;
  private final int width;
  private final int height;

  protected TinkerDisplayCategory(CategoryIdentifier<SmelteryDisplay> id, Component title, Renderer icon, int width, int height) {
    this.id = id;
    this.title = title;
    this.icon = icon;
    this.width = width;
    this.height = height;
  }

  @Override public CategoryIdentifier<SmelteryDisplay> getCategoryIdentifier() { return id; }
  @Override public Component getTitle() { return title; }
  @Override public Renderer getIcon() { return icon; }
  @Override public int getDisplayHeight() { return height + ReiLayout.PADDING * 2; }
  @Override public int getDisplayWidth(SmelteryDisplay display) { return width + ReiLayout.PADDING * 2; }

  @Override
  public List<Widget> setupDisplay(SmelteryDisplay display, Rectangle bounds) {
    List<Widget> widgets = new ArrayList<>();
    widgets.add(Widgets.createRecipeBase(bounds));
    Origin origin = Origin.of(bounds);
    try {
      layout(display, display.layout(), origin, widgets);
    } catch (RuntimeException exception) {
      TConstruct.LOG.error("Could not lay out REI display {} in {}", display.displayId(), id.getIdentifier(), exception);
      widgets.clear();
      widgets.add(Widgets.createRecipeBase(bounds));
      fallback(display, origin, widgets);
    }
    return widgets;
  }

  /** Adds the category's widgets. */
  protected abstract void layout(SmelteryDisplay display, CompoundTag layout, Origin origin, List<Widget> widgets);

  /** Plain rows of inputs, outputs and catalysts, used only when a layout throws. */
  private void fallback(SmelteryDisplay display, Origin origin, List<Widget> widgets) {
    int y = 0;
    for (List<EntryIngredient> row : List.of(display.inputs(), display.outputs(), display.catalysts())) {
      int x = 0;
      for (EntryIngredient ingredient : row) {
        if (x + 18 > width) break;
        widgets.add(ReiLayout.slot(origin, x + 1, y + 1, ingredient, true));
        x += 18;
      }
      y += 18;
      if (y + 18 > height) break;
    }
  }

  /** Entry at the index, or an empty ingredient when absent. */
  protected static EntryIngredient get(List<EntryIngredient> list, int index) {
    return index >= 0 && index < list.size() ? list.get(index) : EntryIngredient.empty();
  }

  /** Note at the index, or null when absent. */
  protected static Component note(SmelteryDisplay display, int index) {
    return index >= 0 && index < display.notes().size() ? display.notes().get(index) : null;
  }
}
