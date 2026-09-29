package slimeknights.tconstruct.plugin.rei.category;

import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Slot;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayMapper;
import slimeknights.tconstruct.library.client.recipe.RecipeLayout;
import slimeknights.tconstruct.plugin.rei.SmelteryDisplay;
import slimeknights.tconstruct.plugin.rei.category.ReiLayout.Origin;

import java.util.List;

/**
 * Smeltery fuel, official {@code melting/FuelCategory} (132 by 40): the fuel (liquid tank or solid item slot), its
 * name, temperature, speed multiplier and "Lasts:" duration for the displayed fuel.
 */
public class FuelCategory extends TinkerDisplayCategory {
  public FuelCategory(CategoryIdentifier<SmelteryDisplay> id, Component title, Renderer icon) {
    super(id, title, icon, 132, 40);
  }

  @Override
  protected void layout(SmelteryDisplay display, CompoundTag layout, Origin origin, List<Widget> widgets) {
    boolean solid = RecipeLayout.getBoolean(layout, RecipeLayout.SOLID);
    int temperature = RecipeLayout.getInt(layout, RecipeLayout.TEMPERATURE, 0);
    int rate = RecipeLayout.getInt(layout, RecipeLayout.RATE, 0);
    int duration = RecipeLayout.getInt(layout, RecipeLayout.DURATION, 0);
    Font font = Minecraft.getInstance().font;

    Slot fuel;
    Component title;
    if (solid) {
      var input = get(display.inputs(), 0);
      // a focused page also names the container left behind, such as the bucket from lava; official has no room for a slot
      if (!display.outputs().isEmpty() && !get(display.outputs(), 0).isEmpty()) {
        Component remainder = get(display.outputs(), 0).get(0).asFormattedText();
        input = ReiLayout.withTooltip(input, List.of(Component.translatableWithFallback("rei.tconstruct.fuel_remainder", "Leaves: %s", remainder)
          .withStyle(net.minecraft.ChatFormatting.GRAY)));
      }
      fuel = ReiLayout.slot(origin, 2, 12, input, true).markInput();
      title = Component.translatable("jei.tconstruct.fuel.solid");
    } else {
      widgets.add(ReiLayout.texture(origin, ReiLayout.MELTING, 3, 3, 3, 3, 14, 34));
      fuel = ReiLayout.tank(origin, 4, 4, 12, 32, get(display.inputs(), 0), 100).markInput();
      EntryStack<?> first = get(display.inputs(), 0).isEmpty() ? null : get(display.inputs(), 0).get(0);
      title = first == null ? Component.empty() : first.asFormattedText();
    }
    widgets.add(fuel);
    widgets.add(ReiLayout.text(origin, 21, 0, title, 0xFFFFFFFF, true));
    widgets.add(ReiLayout.text(origin, 21, 11, Component.translatable("jei.tconstruct.temperature", temperature), ReiLayout.GRAY, false));
    widgets.add(ReiLayout.text(origin, 21, 21, Component.translatable("jei.tconstruct.melting.multiplier", rate / 10f), ReiLayout.GRAY, false));

    // "Lasts:" line follows the entry the slot is currently cycling to, like official's animated widgets
    int x = origin.x() + 21;
    int y = origin.y() + 31;
    widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) -> {
      EntryStack<?> current = fuel.getCurrentEntry();
      if (current == null || current.isEmpty()) return;
      Component amount;
      if (current.getType().equals(VanillaEntryTypes.FLUID)) {
        long mb = current.<dev.architectury.fluid.FluidStack>castValue().getAmount();
        amount = Component.translatable("jei.tconstruct.fuel.duration.liquid", duration / 5, mb);
      } else if (current.getType().equals(VanillaEntryTypes.ITEM)) {
        ItemStack stack = current.castValue();
        int burn = duration > 0 && display.inputs().size() == 1 && get(display.inputs(), 0).size() == 1
          ? duration : RecipeDisplayMapper.solidFuelDuration(stack, Minecraft.getInstance().level);
        // official shows the furnace burn time in seconds; heater units are a quarter of that
        amount = Component.translatable("jei.tconstruct.fuel.duration.solid", burn / 5);
      } else {
        return;
      }
      graphics.drawString(font, Component.translatable("jei.tconstruct.fuel.duration.lasts"), x, y, ReiLayout.GRAY, false);
      graphics.drawString(font, amount, x + 105 - font.width(amount), y, ReiLayout.GRAY, false);
    }));
  }
}
