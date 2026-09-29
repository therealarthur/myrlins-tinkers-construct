package slimeknights.tconstruct.plugin.rei.category;

import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.client.recipe.RecipeLayout;
import slimeknights.tconstruct.library.tools.item.IModifiableDisplay;
import slimeknights.tconstruct.plugin.rei.SmelteryDisplay;
import slimeknights.tconstruct.plugin.rei.category.ReiLayout.Origin;

import java.util.List;

/**
 * Tool building, official {@code ToolBuildingCategory} (134 by 66): the tool drawn large behind a translucent cover,
 * each part at its station layout position, anvil marker and the built tool.
 */
public class ToolBuildingCategory extends TinkerDisplayCategory {
  private static final int WIDTH = 134;

  public ToolBuildingCategory(CategoryIdentifier<SmelteryDisplay> id, Component title, Renderer icon) {
    super(id, title, icon, WIDTH, 66);
  }

  @Override
  protected void layout(SmelteryDisplay display, CompoundTag layout, Origin origin, List<Widget> widgets) {
    int[] positions = RecipeLayout.getIntArray(layout, RecipeLayout.LAYOUT_SLOTS);
    EntryIngredient result = get(display.outputs(), 0);

    // tool background, drawn at 3.7x scale then covered, like official draw()
    ItemStack tool = result.isEmpty() || !(result.get(0).getValue() instanceof ItemStack stack) ? ItemStack.EMPTY
      : stack.getItem() instanceof IModifiableDisplay modifiable ? modifiable.getRenderTool() : stack.getItem().getDefaultInstance();
    if (!tool.isEmpty()) {
      widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) -> {
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(origin.x() + 5, origin.y() + 6.5f);
        pose.scale(3.7f, 3.7f);
        graphics.item(tool, 0, 0);
        pose.popMatrix();
      }));
    }
    widgets.add(ReiLayout.texture(origin, ReiLayout.TINKER_STATION, 5, 6, 122, 77, 70, 60, 0.82f));
    for (int i = 0; i + 1 < positions.length; i += 2) {
      widgets.add(ReiLayout.texture(origin, ReiLayout.TINKER_STATION, positions[i] - 1, positions[i + 1] - 1, 144, 59, 18, 18, 0.28f));
    }
    for (int i = 0; i + 1 < positions.length; i += 2) {
      widgets.add(ReiLayout.texture(origin, ReiLayout.TINKER_STATION, positions[i] - 1, positions[i + 1] - 1, 162, 59, 18, 18));
    }

    // parts and extra requirements at their layout slots
    for (int i = 0; i < display.inputs().size() && i * 2 + 1 < positions.length; i++) {
      widgets.add(ReiLayout.slot(origin, positions[i * 2], positions[i * 2 + 1], display.inputs().get(i), false).markInput());
    }

    widgets.add(ReiLayout.arrow(origin, 74, 22, 0));
    if (RecipeLayout.getBoolean(layout, RecipeLayout.ANVIL)) {
      widgets.add(ReiLayout.texture(origin, ReiLayout.TINKER_STATION, 76, 44, 128, 61, 16, 16));
      widgets.add(ReiLayout.tooltip(origin, 76, 44, 16, 16, List.of(Component.translatable("jei.tconstruct.tinkering.tool_building.anvil"))));
    }
    widgets.add(Widgets.createResultSlotBackground(origin.at(WIDTH - 26, 23)));
    widgets.add(ReiLayout.slot(origin, WIDTH - 26, 23, result, false).markOutput());
  }
}
