package slimeknights.tconstruct.plugin.rei.category;

import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;
import slimeknights.tconstruct.library.client.recipe.RecipeLayout;
import slimeknights.tconstruct.plugin.rei.SmelteryDisplay;
import slimeknights.tconstruct.plugin.rei.category.ReiLayout.Origin;

import java.util.List;

/**
 * Molding, official {@code MoldingRecipeCategory} (70 by 57): material over the table or basin, the pattern pressed in
 * from above (consumed, or kept and shown again on the result side) and the result.
 */
public class MoldingCategory extends TinkerDisplayCategory {
  public MoldingCategory(CategoryIdentifier<SmelteryDisplay> id, Component title, ItemLike icon) {
    super(id, title, EntryStacks.of(icon), 70, 57);
  }

  @Override
  protected void layout(SmelteryDisplay display, CompoundTag layout, Origin origin, List<Widget> widgets) {
    int patternRole = RecipeLayout.getInt(layout, RecipeLayout.CAST, RecipeLayout.ROLE_NONE);
    boolean hasPattern = patternRole != RecipeLayout.ROLE_NONE;
    boolean basin = RecipeLayout.getBoolean(layout, RecipeLayout.BASIN);

    widgets.add(ReiLayout.arrow(origin, 24, 23, 0));
    // up arrow means the item is picked up from the table; down means the pattern is pressed in
    widgets.add(ReiLayout.texture(origin, ReiLayout.CASTING, 8, 17, hasPattern ? 70 : 76, 55, 6, 6));
    widgets.add(ReiLayout.texture(origin, ReiLayout.CASTING, 3, 40, 117, basin ? 16 : 0, 16, 16));
    if (hasPattern) {
      widgets.add(ReiLayout.texture(origin, ReiLayout.CASTING, 51, 40, 117, basin ? 16 : 0, 16, 16));
    }

    widgets.add(ReiLayout.slot(origin, 3, 24, get(display.inputs(), 0), true).markInput());
    widgets.add(ReiLayout.slot(origin, 51, 24, get(display.outputs(), 0), true).markOutput());

    if (hasPattern) {
      if (patternRole == RecipeLayout.ROLE_CONSUMED) {
        EntryIngredient pattern = ReiLayout.withTooltip(get(display.inputs(), 1),
          List.of(Component.translatable("jei.tconstruct.molding.pattern_consumed")));
        widgets.add(ReiLayout.slot(origin, 3, 1, pattern, true).markInput());
      } else {
        EntryIngredient pattern = get(display.catalysts(), 0);
        widgets.add(ReiLayout.slot(origin, 3, 1, pattern, true));
        // the kept pattern is shown again pressed into the result
        widgets.add(ReiLayout.slot(origin, 51, 8, pattern, false));
      }
    }
  }
}
