package slimeknights.tconstruct.mixin.client.book;

import net.minecraft.client.gui.Font;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import slimeknights.mantle.client.book.data.BookData;
import slimeknights.tconstruct.library.client.book.corefix.BookFonts;

/**
 * Core fork commit a3d6c780, BookData#load: stock Continuum Core 1.12.1 overwrites the book font with
 * {@code appearance.uniformFont ? BookScreen.getUniformFont() : Minecraft.getInstance().font} on every load, which
 * drops the uniform font Tinkers assigns to its books. The write goes through {@link BookFonts#assignLoadedFont},
 * which keeps an assigned font like official Mantle. Applied only while load() has the single stock write.
 */
@Mixin(value = BookData.class, remap = false)
public abstract class BookDataFontMixin {
  @Redirect(method = "load", at = @At(value = "FIELD", target = "Lslimeknights/mantle/client/book/data/BookData;fontRenderer:Lnet/minecraft/client/gui/Font;", opcode = Opcodes.PUTFIELD))
  private void tconstruct$keepAssignedFont(BookData book, Font stockChoice) {
    BookFonts.assignLoadedFont(book, stockChoice);
  }
}
