package slimeknights.tconstruct.library.client.book.corefix;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

/**
 * File: BookText.java (Myrlin's Tinkers' Construct; folded in from the private Continuum Core fork 1.12.1-arthur.1,
 * commit 71e3a774, BookScreen, TextComponentDataRenderer, TextDataRenderer, SelectionElement and MultiModuleScreen;
 * based on SlimeKnights Mantle, MIT).
 *
 * Why: stock Continuum Core 1.12.1 draws several book labels through {@code GuiGraphicsExtractor#textRenderer()},
 * which always uses the game font in white with a shadow, and gives station titles a color without an alpha byte,
 * which 26.1 skips. These are the colors official Mantle used; the book text mixins draw with them.
 */
public final class BookText {
  private BookText() {}

  /** Page number color used by official Mantle (light gray, drawn without a shadow) */
  public static final int PAGE_NUMBER_COLOR = 0xFFAAAAAA;
  /** Book text color used by official Mantle for the overflow ellipsis */
  public static final int ELLIPSIS_COLOR = 0xFF000000;
  /** Section list label color while hovered */
  public static final int SECTION_LABEL_HOVER_COLOR = 0xFF000000;
  /** Section list label color otherwise: half transparent black */
  public static final int SECTION_LABEL_COLOR = 0x7F000000;
  /** Debug marker color (white, no shadow) */
  public static final int DEBUG_COLOR = 0xFFFFFFFF;
  /** Station title and Inventory label color: official 0x404040 drew opaque, 26.1 needs the alpha byte */
  public static final int CONTAINER_LABEL_COLOR = 0xFF404040;

  /** Makes a color opaque, as BookScreen#opaque does */
  public static int opaque(int color) {
    return color | 0xFF000000;
  }

  /**
   * Draws scaled cover text the way official Mantle did: in the book's cover text color, with a shadow.
   * 26.1 skips text without an alpha byte, and the appearance colors are stored without one, so the color is made opaque.
   */
  public static void drawCoverString(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, float scale, int color) {
    Matrix3x2fStack pose = graphics.pose();
    pose.pushMatrix();
    pose.translate(x, y);
    pose.scale(scale, scale);
    graphics.text(font, text, 0, 0, opaque(color), true);
    pose.popMatrix();
  }
}
