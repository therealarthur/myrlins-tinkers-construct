package slimeknights.tconstruct.plugin.rei.category;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.entry.renderer.EntryRenderer;
import me.shedaniel.rei.api.client.gui.compat.GuiGraphics;
import me.shedaniel.rei.api.client.gui.widgets.Tooltip;
import me.shedaniel.rei.api.client.gui.widgets.TooltipContext;
import me.shedaniel.rei.api.client.entry.renderer.EntryRendererRegistry;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import slimeknights.tconstruct.library.client.materials.MaterialTooltipCache;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.MaterialValue;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.ModifierValue;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.Value;

/**
 * Draws a material or modifier entry as a title row, like official {@code MaterialTitleIngredientRenderer}
 * (colored material name, left aligned) and {@code ModifierIngredientRenderer} (modifier name, centered).
 */
final class TitleRenderer implements EntryRenderer<Value> {
  private final boolean centered;

  private TitleRenderer(boolean centered) {
    this.centered = centered;
  }

  /** Copies the entries with a title renderer. */
  static EntryIngredient titles(EntryIngredient entries, boolean centered) {
    TitleRenderer renderer = new TitleRenderer(centered);
    return entries.stream().map(entry -> entry.getValue() instanceof Value
      ? entry.copy().withRenderer(renderer.cast()) : entry.copy()).collect(EntryIngredient.collector());
  }

  static Component name(Value value) {
    return switch (value) {
      case MaterialValue material -> MaterialTooltipCache.getColoredDisplayName(material.material());
      case ModifierValue modifier -> modifier.modifier().getDisplayName();
      default -> Component.empty();
    };
  }

  @Override
  public void render(EntryStack<Value> entry, GuiGraphics graphics, Rectangle bounds, int mouseX, int mouseY, float delta) {
    Font font = Minecraft.getInstance().font;
    Component name = name(entry.getValue());
    int x = centered ? bounds.x + (bounds.width - font.width(name)) / 2 : bounds.x;
    graphics.drawString(font, name, x, bounds.y + 1, -1, true);
  }

  @Override
  public Tooltip getTooltip(EntryStack<Value> entry, TooltipContext context) {
    return EntryRendererRegistry.getInstance().get(entry).getTooltip(entry, context);
  }
}
