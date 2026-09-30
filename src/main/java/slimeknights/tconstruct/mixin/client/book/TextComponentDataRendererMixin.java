package slimeknights.tconstruct.mixin.client.book;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import slimeknights.mantle.client.book.data.element.TextComponentData;
import slimeknights.mantle.client.screen.book.TextComponentDataRenderer;
import slimeknights.tconstruct.library.client.book.corefix.BookText;

/**
 * Core fork commit 71e3a774, TextComponentDataRenderer: the text overflow ellipsis is drawn through
 * {@code textRenderer()} in stock Continuum Core 1.12.1 (white with a shadow, game font). Official Mantle drew it in the
 * text color (black) in the page font with the item's shadow setting.
 */
@Mixin(value = TextComponentDataRenderer.class, remap = false)
public abstract class TextComponentDataRendererMixin {
  @WrapOperation(
    method = "drawText(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII[Lslimeknights/mantle/client/book/data/element/TextComponentData;IILnet/minecraft/client/gui/Font;Ljava/util/List;)Ljava/lang/String;",
    at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ActiveTextCollector;accept(IILnet/minecraft/network/chat/Component;)V"))
  private static void tconstruct$drawEllipsis(ActiveTextCollector collector, int x, int y, Component text, Operation<Void> original,
                                              @Local(argsOnly = true) GuiGraphicsExtractor graphics, @Local(argsOnly = true) Font fr, @Local TextComponentData item) {
    graphics.text(fr, text.getString(), x, y, BookText.ELLIPSIS_COLOR, item.dropShadow);
  }
}
