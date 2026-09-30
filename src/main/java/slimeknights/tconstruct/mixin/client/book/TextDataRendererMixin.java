package slimeknights.tconstruct.mixin.client.book;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import slimeknights.mantle.client.book.data.element.TextData;
import slimeknights.mantle.client.screen.book.TextDataRenderer;

/**
 * Core fork commit 71e3a774, TextDataRenderer: stock Continuum Core 1.12.1 draws the text overflow ellipsis with the
 * 5 argument {@code text()}, which always adds a shadow. Official Mantle used the item's shadow setting.
 */
@Mixin(value = TextDataRenderer.class, remap = false)
public abstract class TextDataRendererMixin {
  @WrapOperation(
    method = "drawText(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII[Lslimeknights/mantle/client/book/data/element/TextData;IILnet/minecraft/client/gui/Font;Ljava/util/List;)Ljava/lang/String;",
    at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
  private static void tconstruct$drawEllipsis(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, Operation<Void> original,
                                              @Local TextData item) {
    graphics.text(font, text, x, y, color, item.dropshadow);
  }
}
