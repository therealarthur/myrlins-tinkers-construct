package slimeknights.tconstruct.plugin.rei.category;

import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.SlotValue;
import slimeknights.tconstruct.library.client.recipe.RecipeLayout;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.plugin.rei.SmelteryDisplay;
import slimeknights.tconstruct.plugin.rei.category.ReiLayout.Origin;

import java.util.ArrayList;
import java.util.List;

/**
 * Modifiers (official {@code modifiers/ModifierRecipeCategory}) and tool tinkering (official
 * {@code modifiers/ToolTinkeringCategory}), sharing official {@code AbstractTinkerStationCategory} (128 by 77): the
 * five station inputs at their station positions, the tool before and after, level or variant text, and for
 * modifiers the modifier name, slot cost, requirements and incremental markers.
 */
public class TinkerStationCategory extends TinkerDisplayCategory {
  /** Station input positions from official AbstractTinkerStationCategory. */
  static final int[][] INPUTS = {{3, 33}, {25, 15}, {47, 33}, {43, 58}, {7, 58}};
  private final boolean modifiers;

  public TinkerStationCategory(CategoryIdentifier<SmelteryDisplay> id, Component title, Renderer icon, boolean modifiers) {
    super(id, title, icon, 128, 77);
    this.modifiers = modifiers;
  }

  @Override
  protected void layout(SmelteryDisplay display, CompoundTag layout, Origin origin, List<Widget> widgets) {
    boolean toolInput = RecipeLayout.getBoolean(layout, RecipeLayout.TOOL_INPUT);
    int[] stationSlots = RecipeLayout.getIntArray(layout, RecipeLayout.STATION_SLOTS);
    // tool tinkering lists the replaced tool first when it is an input
    int firstStationInput = !modifiers && toolInput ? 1 : 0;

    // station inputs, with the station icon on empty slots like official
    EntryIngredient[] station = new EntryIngredient[INPUTS.length];
    for (int i = 0; i < stationSlots.length; i++) {
      int slot = stationSlots[i];
      if (slot >= 0 && slot < station.length) station[slot] = get(display.inputs(), firstStationInput + i);
    }
    for (int i = 0; i < INPUTS.length; i++) {
      EntryIngredient entries = station[i] == null ? EntryIngredient.empty() : station[i];
      widgets.add(ReiLayout.slot(origin, INPUTS[i][0], INPUTS[i][1], entries, true).markInput());
      if (entries.isEmpty()) {
        widgets.add(ReiLayout.texture(origin, ReiLayout.TINKER_STATION, INPUTS[i][0], INPUTS[i][1], 128 + (i + 1) * 16, 0, 16, 16));
      }
    }

    // tool before and after; for modifiers and catalyst tinkering the tool stays in the station
    EntryIngredient before;
    EntryIngredient after;
    if (modifiers) {
      before = get(display.catalysts(), 0);
      after = get(display.renderOnly(), 0);
    } else if (toolInput) {
      before = get(display.inputs(), 0);
      after = get(display.outputs(), 0);
    } else {
      before = get(display.catalysts(), 0);
      after = get(display.catalysts(), 1);
    }
    var beforeSlot = ReiLayout.slot(origin, 25, 38, before, true);
    widgets.add(toolInput && !modifiers ? beforeSlot.markInput() : beforeSlot);
    widgets.add(Widgets.createResultSlotBackground(origin.at(105, 34)));
    List<Component> extra = modifiers ? List.of() : returnsTooltip(display);
    var afterSlot = ReiLayout.slot(origin, 105, 34, ReiLayout.withTooltip(after, extra), false);
    widgets.add(toolInput && !modifiers ? afterSlot.markOutput() : afterSlot);

    widgets.add(ReiLayout.arrow(origin, 71, 33, 0));

    Component variant = variantText(display, layout);
    if (variant != null) {
      widgets.add(ReiLayout.centered(origin, 43, 16, 85, variant, ReiLayout.GRAY, false));
    }

    if (modifiers) {
      modifierExtras(display, layout, origin, widgets);
    } else {
      // title and tooltip from the recipe
      Component title = note(display, 0);
      Component tooltip = note(display, 1);
      if (title != null) {
        var label = ReiLayout.centered(origin, 3, 3, 124, title, 0xFFFFFFFF, true);
        if (tooltip != null) label.tooltip(tooltip);
        widgets.add(label);
      }
    }
  }

  /** Modifier name, slot cost or slotless marker, requirements and incremental markers. */
  private void modifierExtras(SmelteryDisplay display, CompoundTag layout, Origin origin, List<Widget> widgets) {
    widgets.add(ReiLayout.area(origin, 3, 3, 124, 10, TitleRenderer.titles(get(display.outputs(), 0), true)).markOutput());

    int requirements = RecipeLayout.getInt(layout, RecipeLayout.REQUIREMENTS, -1);
    Component requirementsText = note(display, requirements);
    if (requirements >= 0 && requirementsText != null) {
      widgets.add(ReiLayout.texture(origin, ReiLayout.TINKER_STATION, 66, 58, 128, 17, 16, 16));
      widgets.add(ReiLayout.tooltip(origin, 66, 58, 16, 16, List.of(requirementsText)));
    }
    if (RecipeLayout.getBoolean(layout, RecipeLayout.INCREMENTAL)) {
      widgets.add(ReiLayout.texture(origin, ReiLayout.TINKER_STATION, 83, 59, 128, 33, 16, 16));
      widgets.add(ReiLayout.tooltip(origin, 83, 59, 16, 16, List.of(Component.translatable("jei.tconstruct.modifiers.incremental"))));
    }
    if (RecipeLayout.getBoolean(layout, RecipeLayout.FREE)) {
      // official draws the slotless sprite in the input slot position with the "free" tooltip
      widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) -> {
        var sprite = Minecraft.getInstance().getAtlasManager().get(new SpriteId(TextureAtlas.LOCATION_ITEMS, TConstruct.getResource("item/slot/slotless")));
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, origin.x() + 110, origin.y() + 58, 16, 16);
      }));
      widgets.add(ReiLayout.tooltip(origin, 102, 58, 24, 16, List.of(Component.translatable("jei.tconstruct.modifiers.free"))));
    } else {
      // slot cost is the last input; official draws the count then the slot sprite in a 24 wide area
      EntryIngredient cost = get(display.inputs(), display.inputs().size() - 1);
      EntryIngredient costEntries = cost.stream().filter(entry -> entry.getValue() instanceof SlotValue).map(entry -> entry.copy()
        .withRenderer(new SlotCostRenderer().cast())).collect(EntryIngredient.collector());
      widgets.add(ReiLayout.area(origin, 102, 58, 24, 16, costEntries).markInput());
    }
  }

  /** Official variant text: the recipe's variant, else the level requirement when it is not the default range. */
  static Component variantText(SmelteryDisplay display, CompoundTag layout) {
    if (RecipeLayout.getBoolean(layout, RecipeLayout.VARIANT)) {
      return note(display, 0);
    }
    if (!layout.contains(RecipeLayout.LEVEL_MIN)) {
      // tool tinkering: third note is the variant when present
      Component variant = note(display, 2);
      return variant != null && !variant.getString().equals(Component.translatableWithFallback("rei.tconstruct.tool_modified",
        "The tool is modified; before and after are shown below.").getString()) ? variant : null;
    }
    int min = RecipeLayout.getInt(layout, RecipeLayout.LEVEL_MIN, 1);
    int max = RecipeLayout.getInt(layout, RecipeLayout.LEVEL_MAX, ModifierEntry.VALID_LEVEL.max());
    int validMax = ModifierEntry.VALID_LEVEL.max();
    if (min == 1) {
      return max < validMax ? Component.translatable("jei.tconstruct.modifiers.level.max", max) : null;
    }
    if (min == max) return Component.translatable("jei.tconstruct.modifiers.level.exact", min);
    if (max == validMax) return Component.translatable("jei.tconstruct.modifiers.level.min", min);
    return Component.translatable("jei.tconstruct.modifiers.level.range", min, max);
  }

  /** Refunds and containers the tinkering returns, listed on the result tooltip. */
  private static List<Component> returnsTooltip(SmelteryDisplay display) {
    List<Component> lines = new ArrayList<>();
    for (int i = 1; i < display.outputs().size(); i++) {
      EntryIngredient output = display.outputs().get(i);
      if (output.isEmpty()) continue;
      EntryStack<?> first = output.get(0);
      Component name = first.asFormattedText();
      if (first.getValue() instanceof net.minecraft.world.item.ItemStack stack && stack.getCount() > 1) {
        name = Component.literal(stack.getCount() + " x ").append(name);
      }
      lines.add(Component.translatableWithFallback("rei.tconstruct.also_returns", "Also returns: %s", name).withStyle(ChatFormatting.GRAY));
    }
    return lines;
  }

  /** Official SlotIngredientRenderer INPUT: gray count then the slot sprite. */
  private static final class SlotCostRenderer implements me.shedaniel.rei.api.client.entry.renderer.EntryRenderer<SlotValue> {
    @Override
    public void render(EntryStack<SlotValue> entry, me.shedaniel.rei.api.client.gui.compat.GuiGraphics graphics, me.shedaniel.math.Rectangle bounds,
                       int mouseX, int mouseY, float delta) {
      SlotValue value = entry.getValue();
      Font font = Minecraft.getInstance().font;
      if (value.slots().count() > 0) {
        String text = Integer.toString(value.slots().count());
        graphics.drawString(font, text, bounds.x + 9 - font.width(text), bounds.y + 5, ReiLayout.GRAY, false);
      }
      String name = value.slots().type().getName();
      String texture = switch (name) {
        case "abilities" -> "item/slot/ability";
        case "upgrades" -> "item/slot/upgrade";
        case "souls" -> "item/materials/hollow_gem";
        default -> "item/slot/" + name;
      };
      var sprite = Minecraft.getInstance().getAtlasManager().get(new SpriteId(TextureAtlas.LOCATION_ITEMS, TConstruct.getResource(texture)));
      graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, bounds.x + 8, bounds.y, 16, 16);
    }

    @Override
    public me.shedaniel.rei.api.client.gui.widgets.Tooltip getTooltip(EntryStack<SlotValue> entry, me.shedaniel.rei.api.client.gui.widgets.TooltipContext context) {
      SlotValue value = entry.getValue();
      int count = value.slots().count();
      Component text = count == 1
        ? Component.translatable("jei.tconstruct.modifiers.slot", value.slots().type().getDisplayName())
        : Component.translatable("jei.tconstruct.modifiers.slots", count, value.slots().type().getDisplayName());
      return me.shedaniel.rei.api.client.gui.widgets.Tooltip.create(context.getPoint(), text);
    }
  }
}
