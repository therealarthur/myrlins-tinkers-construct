package slimeknights.tconstruct.plugin.rei.category;

import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;
import slimeknights.tconstruct.library.client.GuiUtil;
import slimeknights.tconstruct.library.client.recipe.RecipeLayout;
import slimeknights.tconstruct.library.tools.layout.Patterns;
import slimeknights.tconstruct.plugin.rei.SmelteryDisplay;
import slimeknights.tconstruct.plugin.rei.category.ReiLayout.Origin;

import java.util.ArrayList;
import java.util.List;

/**
 * Part builder, official {@code partbuilder/PartBuilderCategory} (121 by 36): material name, material item, pattern
 * item (consumed or reusable), pattern button with its cost, and the result.
 * <p>
 * Official hides containers and leftovers as invisible outputs. They stay outputs here for lookup and are listed on
 * the result tooltip, since the material change matters to players.
 */
public class PartBuilderCategory extends TinkerDisplayCategory {
  public PartBuilderCategory(CategoryIdentifier<SmelteryDisplay> id, Component title, ItemLike icon) {
    super(id, title, EntryStacks.of(icon), 121, 36);
  }

  @Override
  protected void layout(SmelteryDisplay display, CompoundTag layout, Origin origin, List<Widget> widgets) {
    boolean materialItem = RecipeLayout.getBoolean(layout, RecipeLayout.MATERIAL_ITEM);
    boolean materialName = RecipeLayout.getBoolean(layout, RecipeLayout.MATERIAL_NAME);
    boolean reusable = RecipeLayout.getBoolean(layout, RecipeLayout.REUSABLE);
    int cost = RecipeLayout.getInt(layout, RecipeLayout.COST, 0);

    widgets.add(ReiLayout.arrow(origin, 66, 15, 0));

    // material name row
    if (materialName) {
      widgets.add(ReiLayout.area(origin, 3, 2, 118, 10, TitleRenderer.titles(get(display.inputs(), display.inputs().size() - 1), false)).markInput());
    }

    // material item, with the ingot pattern as a placeholder when the pattern costs no material
    if (materialItem) {
      widgets.add(ReiLayout.slot(origin, 25, 16, get(display.inputs(), 0), true).markInput());
    } else {
      widgets.add(Widgets.createSlotBackground(origin.at(25, 16)));
      widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) ->
        GuiUtil.renderPattern(graphics, Patterns.INGOT, origin.x() + 25, origin.y() + 16)));
    }

    // pattern item, consumed or reusable
    EntryIngredient patternItem = reusable ? get(display.catalysts(), 1) : get(display.inputs(), materialItem ? 1 : 0);
    Component role = Component.translatable(reusable ? "jei.tconstruct.part_builder.reusable" : "jei.tconstruct.part_builder.consumed").withStyle(ChatFormatting.GRAY);
    var patternSlot = ReiLayout.slot(origin, 4, 16, ReiLayout.withTooltip(patternItem, List.of(role)), true);
    widgets.add(reusable ? patternSlot : patternSlot.markInput());

    // pattern button with the cost
    widgets.add(ReiLayout.texture(origin, ReiLayout.TINKER_STATION, 45, 15, 45, 132, 18, 18));
    EntryIngredient pattern = get(display.catalysts(), 0);
    if (cost > 0) {
      pattern = ReiLayout.withTooltip(pattern, List.of(Component.translatable("jei.tconstruct.part_builder.cost", cost).withStyle(ChatFormatting.GRAY)));
    }
    widgets.add(ReiLayout.slot(origin, 46, 16, pattern, false).markInput());

    // result, with any extra outputs and recycling notes on its tooltip
    List<Component> extra = new ArrayList<>();
    for (int i = 1; i < display.outputs().size(); i++) {
      EntryIngredient output = display.outputs().get(i);
      if (output.isEmpty()) continue;
      EntryStack<?> first = output.get(0);
      Component name = first.asFormattedText();
      if (first.getValue() instanceof net.minecraft.world.item.ItemStack stack && stack.getCount() > 1) {
        name = Component.literal(stack.getCount() + " x ").append(name);
      }
      extra.add(Component.translatableWithFallback("rei.tconstruct.also_returns", "Also returns: %s", name).withStyle(ChatFormatting.GRAY));
    }
    for (Component note : display.notes()) {
      // the cost is already on the pattern button
      if (!note.getString().equals(Component.translatable("jei.tconstruct.part_builder.cost", cost).getString())) {
        extra.add(note.copy().withStyle(ChatFormatting.GRAY));
      }
    }
    widgets.add(Widgets.createResultSlotBackground(origin.at(96, 15)));
    widgets.add(ReiLayout.slot(origin, 96, 15, ReiLayout.withTooltip(get(display.outputs(), 0), extra), false).markOutput());
  }
}
