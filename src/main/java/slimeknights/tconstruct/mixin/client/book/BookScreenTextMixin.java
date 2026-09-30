package slimeknights.tconstruct.mixin.client.book;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import slimeknights.mantle.client.book.data.BookData;
import slimeknights.mantle.client.screen.book.BookScreen;
import slimeknights.tconstruct.library.client.book.corefix.BookText;

/**
 * Core fork commit 71e3a774, BookScreen text colors: stock Continuum Core 1.12.1 draws the page numbers, the debug
 * marker and the cover title and subtitle through {@code GuiGraphicsExtractor#textRenderer()}, which uses the game
 * font in white with a shadow. Official Mantle drew page numbers light gray without a shadow in the book font, and the
 * cover text in the book's cover text color. Applied only while the stock calls are there (CoreBookFixMixinPlugin).
 */
@Mixin(value = BookScreen.class, remap = false)
public abstract class BookScreenTextMixin extends Screen {
  @Shadow
  @Final
  public BookData book;

  protected BookScreenTextMixin(Component title) {
    super(title);
  }

  /** Left and right page numbers: official light gray 0xFFAAAAAA without shadow, in the book font */
  @WrapOperation(method = "extractRenderState", at = {
    @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ActiveTextCollector;accept(IILnet/minecraft/network/chat/Component;)V", ordinal = 0),
    @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ActiveTextCollector;accept(IILnet/minecraft/network/chat/Component;)V", ordinal = 1)
  })
  private void tconstruct$drawPageNumber(ActiveTextCollector collector, int x, int y, Component text, Operation<Void> original,
                                         @Local(argsOnly = true) GuiGraphicsExtractor graphics, @Local Font fontRenderer) {
    graphics.text(fontRenderer, text.getString(), x, y, BookText.PAGE_NUMBER_COLOR, false);
  }

  /** Debug marker: white without shadow in the screen font, as the Core fork */
  @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ActiveTextCollector;accept(IILnet/minecraft/network/chat/Component;)V", ordinal = 2))
  private void tconstruct$drawDebugMarker(ActiveTextCollector collector, int x, int y, Component text, Operation<Void> original,
                                          @Local(argsOnly = true) GuiGraphicsExtractor graphics) {
    graphics.text(this.font, text.getString(), x, y, BookText.DEBUG_COLOR, false);
  }

  /** Cover title and subtitle: the book's cover text color with a shadow, as official Mantle */
  @Redirect(method = "renderCover", at = @At(value = "INVOKE", target = "Lslimeknights/mantle/client/screen/book/BookScreen;drawString(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/lang/String;FFF)V"))
  private void tconstruct$drawCoverText(GuiGraphicsExtractor graphics, String text, float x, float y, float scale) {
    BookText.drawCoverString(graphics, this.font, text, x, y, scale, this.book.appearance.getCoverTextColor());
  }
}
