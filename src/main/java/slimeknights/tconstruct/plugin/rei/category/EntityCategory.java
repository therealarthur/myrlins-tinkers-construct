package slimeknights.tconstruct.plugin.rei.category;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.entry.renderer.EntryRenderer;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.compat.GuiGraphics;
import me.shedaniel.rei.api.client.gui.widgets.Tooltip;
import me.shedaniel.rei.api.client.gui.widgets.TooltipContext;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.entry.renderer.EntryRendererRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.EntityValue;
import slimeknights.tconstruct.library.client.recipe.RecipeLayout;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.plugin.rei.SmelteryDisplay;
import slimeknights.tconstruct.plugin.rei.category.ReiLayout.Origin;

import java.util.List;
import java.util.function.Supplier;

/**
 * Entity melting (official {@code entity/EntityMeltingRecipeCategory}, 150 by 62) and severing (official
 * {@code entity/SeveringCategory}, 100 by 38).
 * <p>
 * Official renders a live, animated entity in a 32 by 32 box. REI 26.1 exposes no entity preview widget and this
 * port cannot verify a hand-made one without a graphical client, so the box shows the entity's spawn egg at double
 * size (or its name when it has no egg); the name, baby state and spawn egg lookup stay on the entry.
 */
public class EntityCategory extends TinkerDisplayCategory {
  private final boolean severing;
  private final Supplier<FuelFacts> fuels;

  public EntityCategory(CategoryIdentifier<SmelteryDisplay> id, Component title, Renderer icon, boolean severing, Supplier<FuelFacts> fuels) {
    super(id, title, icon, severing ? 100 : 150, severing ? 38 : 62);
    this.severing = severing;
    this.fuels = fuels;
  }

  @Override
  protected void layout(SmelteryDisplay display, CompoundTag layout, Origin origin, List<Widget> widgets) {
    if (severing) {
      widgets.add(ReiLayout.area(origin, 3, 3, 32, 32, entities(get(display.inputs(), 0))).markInput());
      widgets.add(ReiLayout.arrow(origin, 42, 10, 0));
      if (!display.notes().isEmpty()) {
        // chance terms from the severing recipe; official has no text, so they live on the arrow
        widgets.add(ReiLayout.tooltip(origin, 42, 10, 24, 17, display.notes()));
      }
      widgets.add(Widgets.createResultSlotBackground(origin.at(76, 11)));
      widgets.add(ReiLayout.slot(origin, 76, 11, get(display.outputs(), 0), false).markOutput());
      return;
    }

    widgets.add(ReiLayout.texture(origin, ReiLayout.MELTING, 0, 0, 0, 41, 150, 62));
    int damage = RecipeLayout.getInt(layout, RecipeLayout.DAMAGE, 2);
    widgets.add(ReiLayout.arrow(origin, 71, 21, 200));
    widgets.add(ReiLayout.right(origin, 0, 8, 84, Component.literal(Float.toString(damage / 2f)), ReiLayout.RED, false));
    widgets.add(ReiLayout.area(origin, 19, 11, 32, 32, entities(get(display.inputs(), 0))).markInput());
    Component perHearts = damage == 2
      ? Component.translatable("jei.tconstruct.entity_melting.per_heart").withStyle(ChatFormatting.GRAY)
      : Component.translatable("jei.tconstruct.entity_melting.per_hearts", damage / 2f).withStyle(ChatFormatting.GRAY);
    widgets.add(ReiLayout.tank(origin, 115, 11, 16, 32, get(display.outputs(), 0), FluidValues.INGOT * 2, fluid -> List.of(perHearts)).markOutput());
    // official lists every liquid fuel (temperature 1) as a catalyst here
    FuelFacts facts = fuels.get();
    widgets.add(ReiLayout.tank(origin, 75, 43, 16, 16, ReiLayout.fluidIngredient(facts.usableFuels(1)), 0, fluid -> List.of()));
    widgets.add(ReiLayout.texture(origin, ReiLayout.MELTING, 75, 43, 150, 74, 16, 16));
  }

  /** Copies entity entries with the large icon renderer. */
  private static EntryIngredient entities(EntryIngredient ingredient) {
    return ingredient.stream().map(entry -> entry.getValue() instanceof EntityValue
      ? entry.copy().withRenderer(new EntityIconRenderer().cast()) : entry.copy()).collect(EntryIngredient.collector());
  }

  /** Spawn egg at the size of the official entity box, or a wrapped name when the entity has no egg. */
  private static final class EntityIconRenderer implements EntryRenderer<EntityValue> {
    @Override
    public void render(EntryStack<EntityValue> entry, GuiGraphics graphics, Rectangle bounds, int mouseX, int mouseY, float delta) {
      EntityValue value = entry.getValue();
      EntityType<?> type = value.entity();
      var egg = SpawnEggItem.byId(type);
      if (egg.isPresent()) {
        int scale = Math.max(1, Math.min(bounds.width, bounds.height) / 16);
        int size = 16 * scale;
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(bounds.x + (bounds.width - size) / 2f, bounds.y + (bounds.height - size) / 2f);
        pose.scale(scale, scale);
        graphics.item(new ItemStack(egg.get()), 0, 0);
        pose.popMatrix();
        return;
      }
      Font font = Minecraft.getInstance().font;
      List<FormattedCharSequence> lines = font.split(type.getDescription(), bounds.width);
      int y = bounds.y + Math.max(0, (bounds.height - lines.size() * 9) / 2);
      for (FormattedCharSequence line : lines) {
        if (y + 9 > bounds.y + bounds.height) break;
        graphics.drawString(font, line, bounds.x + (bounds.width - font.width(line)) / 2, y, -1, true);
        y += 9;
      }
    }

    @Override
    public Tooltip getTooltip(EntryStack<EntityValue> entry, TooltipContext context) {
      // keep the shared entity tooltip (name, baby state, advanced id)
      return EntryRendererRegistry.getInstance().get(entry).getTooltip(entry, context);
    }
  }
}
