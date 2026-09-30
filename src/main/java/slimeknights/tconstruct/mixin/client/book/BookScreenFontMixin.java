package slimeknights.tconstruct.mixin.client.book;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.mantle.client.screen.book.BookScreen;
import slimeknights.tconstruct.library.client.book.corefix.BookFonts;

/**
 * Core fork commit a3d6c780, BookScreen font getters: stock Continuum Core 1.12.1 returns the game font from both, so
 * books that ask for the uniform font draw wide text that wraps and clips. Returns the official alt and uniform fonts
 * instead ({@link BookFonts}). Applied only while the getters are the stock ones (CoreBookFixMixinPlugin).
 */
@Mixin(value = BookScreen.class, remap = false)
public abstract class BookScreenFontMixin {
  @Inject(method = "getAltFont", at = @At("HEAD"), cancellable = true)
  private static void tconstruct$officialAltFont(CallbackInfoReturnable<Font> cir) {
    cir.setReturnValue(BookFonts.getAltFont());
  }

  @Inject(method = "getUniformFont", at = @At("HEAD"), cancellable = true)
  private static void tconstruct$officialUniformFont(CallbackInfoReturnable<Font> cir) {
    cir.setReturnValue(BookFonts.getUniformFont());
  }
}
