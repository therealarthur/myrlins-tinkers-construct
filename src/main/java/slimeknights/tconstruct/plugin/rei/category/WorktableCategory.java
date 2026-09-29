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
 * Modifier worktable, official {@code modifiers/ModifierWorktableCategory} (121 by 35): title with the description
 * tooltip, tool slot, two item slots and the modifier selection or result.
 */
public class WorktableCategory extends TinkerDisplayCategory {
  public WorktableCategory(CategoryIdentifier<SmelteryDisplay> id, Component title, ItemLike icon) {
    super(id, title, EntryStacks.of(icon), 121, 35);
  }

  @Override
  protected void layout(SmelteryDisplay display, CompoundTag layout, Origin origin, List<Widget> widgets) {
    boolean toolInput = RecipeLayout.getBoolean(layout, RecipeLayout.TOOL_INPUT);
    boolean modifierOutput = RecipeLayout.getBoolean(layout, RecipeLayout.MODIFIER_OUTPUT);
    int itemSlots = RecipeLayout.getInt(layout, RecipeLayout.ITEM_SLOTS, 0);

    Component title = note(display, 0);
    Component description = note(display, 1);
    if (title != null) widgets.add(ReiLayout.text(origin, 3, 2, title, ReiLayout.DARK, false));
    if (description != null) widgets.add(ReiLayout.tooltip(origin, 0, 2, 121, 11, List.of(description)));

    // tool: the last input when consumed, else the first catalyst
    EntryIngredient tools = toolInput ? get(display.inputs(), itemSlots) : get(display.catalysts(), 0);
    var toolSlot = ReiLayout.slot(origin, 23, 16, tools, true);
    widgets.add(toolInput ? toolSlot.markInput() : toolSlot);
    if (tools.isEmpty()) widgets.add(ReiLayout.texture(origin, ReiLayout.TINKER_STATION, 23, 16, 128, 0, 16, 16));

    // two item slots with their empty-slot icons
    int[] icons = {176, 208};
    for (int i = 0; i < 2; i++) {
      EntryIngredient items = i < itemSlots ? get(display.inputs(), i) : EntryIngredient.empty();
      widgets.add(ReiLayout.slot(origin, 43 + i * 18, 16, items, true).markInput());
      if (items.isEmpty()) widgets.add(ReiLayout.texture(origin, ReiLayout.TINKER_STATION, 43 + i * 18, 16, icons[i], 0, 16, 16));
    }

    // modifier options: an output, or a selection that stays on the tool
    widgets.add(ReiLayout.texture(origin, ReiLayout.TINKER_STATION, 81, 15, 81, 181, 18, 18));
    EntryIngredient modifiers = modifierOutput ? get(display.outputs(), 0) : get(display.catalysts(), toolInput ? 0 : 1);
    var modifierSlot = ReiLayout.slot(origin, 82, 16, modifiers, false);
    widgets.add(modifierOutput ? modifierSlot.markOutput() : modifierSlot);
  }
}
