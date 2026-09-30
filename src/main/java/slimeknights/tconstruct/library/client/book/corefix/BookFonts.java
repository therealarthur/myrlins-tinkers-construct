package slimeknights.tconstruct.library.client.book.corefix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.book.data.BookData;
import slimeknights.mantle.client.screen.book.BookScreen;

import javax.annotation.Nullable;

/**
 * File: BookFonts.java (Myrlin's Tinkers' Construct; folded in from the private Continuum Core fork 1.12.1-arthur.1,
 * commit a3d6c780, BookScreen#getAltFont, BookScreen#getUniformFont and the font line of BookData#load; based on
 * SlimeKnights Mantle, MIT).
 *
 * Why: stock Continuum Core 1.12.1 returns the game font from both BookScreen font getters, so books that ask for the
 * uniform font draw wide text that wraps and clips, and BookData#load replaces a font a mod assigned to its book with
 * the game font on every load, so the Tinkers books lose the small uniform font official Tinkers gives them
 * ({@code CommonsClientEvents#clientSetup}). The BookScreenFontMixin and BookDataFontMixin route both through here.
 */
public final class BookFonts {
  private BookFonts() {}

  /** Cached alt font, built on first use */
  @Nullable
  private static Font altFont;
  /** Cached uniform font, built on first use */
  @Nullable
  private static Font uniformFont;

  /** Gets the alt Minecraft font (the enchanting table glyphs), as official Mantle */
  public static Font getAltFont() {
    if (altFont == null) {
      altFont = fontOverSet(Minecraft.ALT_FONT);
    }
    return altFont;
  }

  /** Gets the uniform version of the Minecraft font (the small unifont glyphs), as official Mantle */
  public static Font getUniformFont() {
    if (uniformFont == null) {
      uniformFont = fontOverSet(Minecraft.UNIFORM_FONT);
    }
    return uniformFont;
  }

  /**
   * Official Mantle built these fonts over one FontManager font set. 26.1 no longer exposes the font sets, so this wraps the
   * game font's glyph provider and sends every font resource to the given set, which draws the same glyphs and shares
   * the game's glyph cache. Falls back to the game font if the provider cannot be read. Same code and log line as the
   * Core fork, so a book behaves the same whichever of the two carries the fix.
   */
  private static Font fontOverSet(Identifier fontSet) {
    Font base = Minecraft.getInstance().font;
    try {
      java.lang.reflect.Field field = Font.class.getDeclaredField("provider");
      field.setAccessible(true);
      Font.Provider provider = (Font.Provider) field.get(base);
      net.minecraft.network.chat.FontDescription target = new net.minecraft.network.chat.FontDescription.Resource(fontSet);
      return new Font(new Font.Provider() {
        @Override
        public net.minecraft.client.gui.GlyphSource glyphs(net.minecraft.network.chat.FontDescription font) {
          // sprite and player head glyphs keep their own source
          return provider.glyphs(font instanceof net.minecraft.network.chat.FontDescription.Resource ? target : font);
        }

        @Override
        public net.minecraft.client.gui.font.glyphs.EffectGlyph effect() {
          return provider.effect();
        }
      });
    } catch (ReflectiveOperationException | RuntimeException e) {
      Mantle.logger.error("Could not create the {} font for books, using the default font", fontSet, e);
      return base;
    }
  }

  /**
   * Stores the font BookData#load picked, the way the Core fork does. Stock Core writes
   * {@code appearance.uniformFont ? BookScreen.getUniformFont() : Minecraft.getInstance().font} unconditionally; official
   * Mantle set the uniform font when the appearance asks for it, cleared only its own uniform font otherwise, and kept
   * any other font a mod assigned.
   * @param book         Book being loaded
   * @param stockChoice  Font the stock expression evaluated to: the uniform font when the appearance asks for it, the game font otherwise
   */
  public static void assignLoadedFont(BookData book, Font stockChoice) {
    if (book.appearance.uniformFont) {
      book.fontRenderer = stockChoice;
    } else if (book.fontRenderer == null || book.fontRenderer == BookScreen.getUniformFont()) {
      book.fontRenderer = stockChoice;
    }
  }
}
