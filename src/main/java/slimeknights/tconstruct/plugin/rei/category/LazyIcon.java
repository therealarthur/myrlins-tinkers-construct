package slimeknights.tconstruct.plugin.rei.category;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.compat.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.function.Supplier;

/**
 * Category icons that need loaded data (render tools, material parts) are created on first draw, not during REI's
 * plugin reload, which also runs on the title screen before any synchronized world exists. Any failure falls back to
 * the plain item, so an icon can never break the recipe screen.
 */
public final class LazyIcon implements Renderer {
  private final Supplier<ItemStack> supplier;
  private final ItemLike fallback;
  private ItemStack stack;

  private LazyIcon(Supplier<ItemStack> supplier, ItemLike fallback) {
    this.supplier = supplier;
    this.fallback = fallback;
  }

  /** Item icon computed on first draw. */
  public static Renderer item(Supplier<ItemStack> supplier, ItemLike fallback) {
    return new LazyIcon(supplier, fallback);
  }

  /** Icon from a region of a 256 by 256 GUI texture, drawn centered in a 16 by 16 area. */
  public static Renderer texture(Identifier texture, int u, int v) {
    return (graphics, bounds, mouseX, mouseY, delta) -> graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
      bounds.getCenterX() - 8, bounds.getCenterY() - 8, u, v, 16, 16, 256, 256);
  }

  @Override
  public void render(GuiGraphics graphics, Rectangle bounds, int mouseX, int mouseY, float delta) {
    if (stack == null || stack.isEmpty()) {
      try {
        stack = supplier.get();
      } catch (RuntimeException exception) {
        stack = ItemStack.EMPTY;
      }
      if (stack.isEmpty()) {
        // retry the real icon on a later draw, once data has loaded
        graphics.item(new ItemStack(fallback), bounds.getCenterX() - 8, bounds.getCenterY() - 8);
        stack = null;
        return;
      }
    }
    graphics.item(stack, bounds.getCenterX() - 8, bounds.getCenterY() - 8);
  }
}
