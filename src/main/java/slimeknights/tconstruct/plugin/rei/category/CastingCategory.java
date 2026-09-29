package slimeknights.tconstruct.plugin.rei.category;

import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;
import slimeknights.tconstruct.library.client.recipe.RecipeLayout;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.plugin.rei.SmelteryDisplay;
import slimeknights.tconstruct.plugin.rei.category.ReiLayout.Origin;

import java.util.List;

/**
 * Casting table and casting basin, official {@code casting/AbstractCastingCategory} (117 by 54): tank with the fluid
 * amount, faucet, cast slot, block icon, cooling arrow and cast consumed or kept marker.
 */
public class CastingCategory extends TinkerDisplayCategory {
  private final boolean basin;

  public CastingCategory(CategoryIdentifier<SmelteryDisplay> id, Component title, ItemLike icon, boolean basin) {
    super(id, title, EntryStacks.of(icon), 117, 54);
    this.basin = basin;
  }

  @Override
  protected void layout(SmelteryDisplay display, CompoundTag layout, Origin origin, List<Widget> widgets) {
    widgets.add(ReiLayout.texture(origin, ReiLayout.CASTING, 0, 0, 0, 0, 117, 54));
    // table or basin block under the cast
    widgets.add(ReiLayout.texture(origin, ReiLayout.CASTING, 38, 35, 117, basin ? 16 : 0, 16, 16));

    int cooling = RecipeLayout.getInt(layout, RecipeLayout.COOLING, 0);
    int castRole = RecipeLayout.getInt(layout, RecipeLayout.CAST, RecipeLayout.ROLE_NONE);
    boolean hasCast = castRole != RecipeLayout.ROLE_NONE;

    // tank, then its overlay so the scale marks sit on top of the fluid
    EntryIngredient fluid = get(display.inputs(), 0);
    widgets.add(ReiLayout.tank(origin, 3, 3, 32, 32, fluid, FluidValues.METAL_BLOCK).markInput());
    widgets.add(ReiLayout.texture(origin, ReiLayout.CASTING, 3, 3, 133, 0, 32, 32));
    // pouring faucet stream, render-only in official; shorter when a cast sits under it
    widgets.add(ReiLayout.tank(origin, 43, 8, 6, hasCast ? 11 : 27, fluid, 0));

    if (hasCast) {
      boolean consumed = castRole == RecipeLayout.ROLE_CONSUMED;
      EntryIngredient cast = consumed ? get(display.inputs(), 1) : get(display.catalysts(), 0);
      var slot = ReiLayout.slot(origin, 38, 19, cast, false);
      if (consumed) slot.markInput();
      widgets.add(slot);
      Component castText = Component.translatable(consumed ? "jei.tconstruct.casting.cast_consumed" : "jei.tconstruct.casting.cast_kept");
      widgets.add(ReiLayout.texture(origin, ReiLayout.CASTING, 63, 39, 141, consumed ? 32 : 43, 13, 11));
      widgets.add(ReiLayout.tooltip(origin, 63, 39, 13, 11, List.of(castText)));
    }

    widgets.add(ReiLayout.slot(origin, 93, 18, get(display.outputs(), 0), false).markOutput());

    // cooling arrow, official animates over max(5, cooling) ticks and shows whole seconds
    widgets.add(ReiLayout.arrow(origin, 58, 18, Math.max(5, cooling)));
    widgets.add(ReiLayout.tooltip(origin, 58, 18, 24, 17, List.of(Component.translatable("jei.tconstruct.casting.time", cooling / 20))));
  }
}
