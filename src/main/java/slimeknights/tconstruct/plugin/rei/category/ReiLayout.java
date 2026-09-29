package slimeknights.tconstruct.plugin.rei.category;

import dev.architectury.hooks.fluid.forge.FluidStackHooksForge;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.entry.renderer.EntryRenderer;
import me.shedaniel.rei.api.client.gui.compat.GuiGraphics;
import me.shedaniel.rei.api.client.gui.widgets.Label;
import me.shedaniel.rei.api.client.gui.widgets.Slot;
import me.shedaniel.rei.api.client.gui.widgets.Tooltip;
import me.shedaniel.rei.api.client.gui.widgets.TooltipContext;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.fluid.tooltip.FluidTooltipHandler;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.RenderUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Shared drawing helpers for the per-category REI layouts, which reproduce official 3.12.1 JEI coordinates.
 * <p>
 * All positions are relative to an {@link Origin} at the top left of the official JEI recipe area. Fluids are drawn
 * by this adapter: REI 26.1.819's own fluid entry renderer draws nothing, so without these renderers every tank
 * would be blank.
 */
public final class ReiLayout {
  private ReiLayout() {}

  /** Padding between REI's recipe panel border and the official content area. */
  public static final int PADDING = 4;

  public static final Identifier CASTING = TConstruct.getResource("textures/gui/jei/casting.png");
  public static final Identifier MELTING = TConstruct.getResource("textures/gui/jei/melting.png");
  public static final Identifier ALLOY = TConstruct.getResource("textures/gui/jei/alloy.png");
  public static final Identifier TINKER_STATION = TConstruct.getResource("textures/gui/jei/tinker_station.png");

  /** Gray used by official JEI text, {@code java.awt.Color.GRAY}. */
  public static final int GRAY = 0xFF808080;
  /** Official dark label color for the worktable title. */
  public static final int DARK = 0xFF404040;
  /** Red used for entity melting damage, {@code java.awt.Color.RED}. */
  public static final int RED = 0xFFFF0000;

  /** Top left of the official content area inside REI's display bounds. */
  public record Origin(int x, int y) {
    public static Origin of(Rectangle bounds) {
      return new Origin(bounds.x + PADDING, bounds.y + PADDING);
    }

    public Point at(int dx, int dy) {
      return new Point(x + dx, y + dy);
    }

    public Rectangle rect(int dx, int dy, int width, int height) {
      return new Rectangle(x + dx, y + dy, width, height);
    }
  }

  /** Draws a region of a 256 by 256 GUI texture. */
  public static Widget texture(Origin origin, Identifier texture, int x, int y, int u, int v, int width, int height) {
    return Widgets.createTexturedWidget(texture, origin.x + x, origin.y + y, u, v, width, height);
  }

  /** Draws a region of a 256 by 256 GUI texture with the given alpha, for the tool building overlays. */
  public static Widget texture(Origin origin, Identifier texture, int x, int y, int u, int v, int width, int height, float alpha) {
    int color = ((int) (Math.max(0, Math.min(1, alpha)) * 255) << 24) | 0xFFFFFF;
    return Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) ->
      graphics.blit(RenderPipelines.GUI_TEXTURED, texture, origin.x + x, origin.y + y, u, v, width, height, 256, 256, color));
  }

  /** Adds a tooltip area over part of the layout. */
  public static Widget tooltip(Origin origin, int x, int y, int width, int height, Collection<Component> lines) {
    return Widgets.createTooltip(origin.rect(x, y, width, height), lines);
  }

  /** Adds a text label, left aligned, without shadow by default. */
  public static Label text(Origin origin, int x, int y, Component text, int color, boolean shadow) {
    Label label = Widgets.createLabel(origin.at(x, y), text).leftAligned().color(color);
    return shadow ? label.shadow() : label.noShadow();
  }

  /** Adds text centered in the horizontal span {@code [x, x + width)}, like JEI's centered text widgets. */
  public static Label centered(Origin origin, int x, int y, int width, Component text, int color, boolean shadow) {
    Label label = Widgets.createLabel(origin.at(x + width / 2, y), text).centered().color(color);
    return shadow ? label.shadow() : label.noShadow();
  }

  /** Adds text right aligned to {@code x + width}. */
  public static Label right(Origin origin, int x, int y, int width, Component text, int color, boolean shadow) {
    Label label = Widgets.createLabel(origin.at(x + width, y), text).rightAligned().color(color);
    return shadow ? label.shadow() : label.noShadow();
  }

  /** Adds REI's recipe arrow, animated over the given duration when positive. */
  public static Widget arrow(Origin origin, int x, int y, int ticks) {
    var arrow = Widgets.createArrow(origin.at(x, y));
    return ticks > 0 ? arrow.animationDurationTicks(ticks) : arrow;
  }

  /** Standard 18 by 18 item slot with the item at {@code (x, y)}. */
  public static Slot slot(Origin origin, int x, int y, EntryIngredient entries, boolean background) {
    Slot slot = Widgets.createSlot(origin.at(x, y)).entries(entries);
    return background ? slot : slot.disableBackground();
  }

  /** Slot covering an arbitrary area, drawing its entries inside {@code width} by {@code height}. */
  public static Slot area(Origin origin, int x, int y, int width, int height, EntryIngredient entries) {
    return Widgets.createSlot(new Rectangle(origin.x + x - 1, origin.y + y - 1, width + 2, height + 2)).disableBackground().entries(entries);
  }

  /** Adds tooltip lines to every entry; entries are copied so cached displays are never changed. */
  public static EntryIngredient withTooltip(EntryIngredient entries, List<Component> lines) {
    if (lines.isEmpty()) return entries;
    return entries.stream().map(entry -> entry.copy().tooltip(lines)).collect(EntryIngredient.collector());
  }

  /** Adds tooltip lines computed per entry. */
  public static EntryIngredient withTooltip(EntryIngredient entries, Function<EntryStack<?>, List<Component>> lines) {
    return entries.stream().map(entry -> entry.copy().tooltip(stack -> lines.apply(stack))).collect(EntryIngredient.collector());
  }

  /**
   * Fluid tank like JEI's fluid renderer: the fluid fills a share of the area proportional to amount over capacity.
   * @param capacity  amount of a full tank; zero or less draws every fluid full height
   * @param extra     extra tooltip lines for a displayed fluid, after the official material units
   */
  public static Slot tank(Origin origin, int x, int y, int width, int height, EntryIngredient fluids, int capacity,
                          Function<FluidStack, List<Component>> extra) {
    FluidRenderer renderer = new FluidRenderer(capacity, extra);
    EntryIngredient rendered = fluids.stream().map(entry -> entry.getType().equals(VanillaEntryTypes.FLUID)
      ? entry.copy().withRenderer(renderer.cast()) : entry.copy()).collect(EntryIngredient.collector());
    return area(origin, x, y, width, height, rendered);
  }

  /** Fluid tank without extra tooltip lines. */
  public static Slot tank(Origin origin, int x, int y, int width, int height, EntryIngredient fluids, int capacity) {
    return tank(origin, x, y, width, height, fluids, capacity, fluid -> List.of());
  }

  /** Converts NeoForge fluids into a REI ingredient. */
  public static EntryIngredient fluidIngredient(List<FluidStack> fluids) {
    return EntryIngredients.from(fluids.stream().filter(stack -> !stack.isEmpty()).toList(),
      stack -> EntryStacks.of(FluidStackHooksForge.fromForge(stack)));
  }

  /** Largest fluid amount among the ingredients, at least {@code minimum}; official uses this as the shared tank capacity. */
  public static int maxAmount(Collection<EntryIngredient> ingredients, int minimum) {
    long max = minimum;
    for (EntryIngredient ingredient : ingredients) {
      for (EntryStack<?> entry : ingredient) {
        if (entry.getType().equals(VanillaEntryTypes.FLUID)) {
          max = Math.max(max, entry.<dev.architectury.fluid.FluidStack>castValue().getAmount());
        }
      }
    }
    return (int) Math.min(Integer.MAX_VALUE, max);
  }

  /**
   * Draws several fluids side by side in one area, splitting the width like official CategoryUtil.drawMultipleFluids.
   * @param marks  per ingredient, true to mark the slot as an input (null for no marks)
   */
  public static List<Slot> multipleFluids(Origin origin, int x, int y, int totalWidth, int height, List<EntryIngredient> fluids, int capacity,
                                          Function<Integer, Function<FluidStack, List<Component>>> extra) {
    List<Slot> slots = new ArrayList<>();
    int count = fluids.size();
    if (count == 0) return slots;
    int width = totalWidth / count;
    for (int i = 0; i < count; i++) {
      int slotWidth = i == count - 1 ? totalWidth - width * (count - 1) : width;
      slots.add(tank(origin, x + i * width, y, slotWidth, height, fluids.get(i), capacity, extra.apply(i)));
    }
    return slots;
  }

  /** Draws an item as a fixed icon, used where official draws a texture this pack's copy lacks. */
  public static Widget item(Origin origin, ItemStack stack, int x, int y) {
    return Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) -> graphics.item(stack, origin.x + x, origin.y + y));
  }

  /** Official material unit lines for a fluid tooltip, then extras. */
  static List<Component> fluidTooltip(FluidStack fluid, List<Component> extra) {
    List<Component> lines = new ArrayList<>();
    lines.add(fluid.getHoverName());
    try {
      FluidTooltipHandler.appendMaterial(fluid, lines);
    } catch (RuntimeException exception) {
      // tooltips must never break the recipe screen; fall back to the raw amount
      lines.add(Component.literal(fluid.getAmount() + " mB").withStyle(ChatFormatting.GRAY));
    }
    lines.addAll(extra);
    return lines;
  }

  /** Tiled fluid renderer with a fill ratio, replacing REI's no-op fluid renderer inside our layouts. */
  public static final class FluidRenderer implements EntryRenderer<dev.architectury.fluid.FluidStack> {
    private final int capacity;
    private final Function<FluidStack, List<Component>> extra;

    public FluidRenderer(int capacity, Function<FluidStack, List<Component>> extra) {
      this.capacity = capacity;
      this.extra = extra;
    }

    @Override
    public void render(EntryStack<dev.architectury.fluid.FluidStack> entry, GuiGraphics graphics, Rectangle bounds, int mouseX, int mouseY, float delta) {
      FluidStack fluid = FluidStackHooksForge.toForge(entry.getValue());
      if (fluid.isEmpty() || bounds.width <= 0 || bounds.height <= 0) return;
      int height = bounds.height;
      if (capacity > 0) {
        // JEI keeps at least one pixel visible for any non-empty fluid
        height = (int) Math.max(1, Math.min(bounds.height, (long) bounds.height * fluid.getAmount() / capacity));
      }
      drawFluid(graphics, fluid, bounds.x, bounds.y + bounds.height - height, bounds.width, height);
    }

    @Override
    public Tooltip getTooltip(EntryStack<dev.architectury.fluid.FluidStack> entry, TooltipContext context) {
      FluidStack fluid = FluidStackHooksForge.toForge(entry.getValue());
      if (fluid.isEmpty()) return null;
      List<Component> lines = fluidTooltip(fluid, extra.apply(fluid));
      if (context.getFlag().isAdvanced()) {
        lines.add(Component.literal(String.valueOf(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid()))).withStyle(ChatFormatting.DARK_GRAY));
      }
      return Tooltip.create(context.getPoint(), lines);
    }
  }

  /** Draws a fluid sprite tiled over an area, cropping partial tiles with a scissor instead of stretching them. */
  public static void drawFluid(GuiGraphics graphics, FluidStack fluid, int x, int y, int width, int height) {
    if (fluid.isEmpty() || width <= 0 || height <= 0) return;
    try {
      TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
      TextureAtlasSprite sprite = atlas.getSprite(RenderUtils.getStillTexture(fluid, fluid.getFluid().getFluidType()));
      int color = RenderUtils.getFluidColor(fluid, fluid.getFluid().getFluidType());
      if (color == -1) {
        color = 0xFFFFFFFF;
      } else if ((color & 0xFF000000) == 0) {
        color |= 0xFF000000;
      }
      graphics.enableScissor(x, y, x + width, y + height);
      try {
        // tile from the bottom so the fluid surface sits at the top of the drawn height
        for (int dy = height; dy > -16; dy -= 16) {
          for (int dx = 0; dx < width; dx += 16) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x + dx, y + dy - 16, 16, 16, color);
          }
        }
      } finally {
        graphics.disableScissor();
      }
    } catch (RuntimeException exception) {
      // a broken fluid texture must not break the recipe screen
      graphics.fill(x, y, x + width, y + height, 0xFF7F7F7F);
    }
  }
}
