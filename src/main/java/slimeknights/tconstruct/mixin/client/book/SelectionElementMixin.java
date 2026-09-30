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
import slimeknights.mantle.client.screen.book.element.SelectionElement;
import slimeknights.tconstruct.library.client.book.corefix.BookText;

/**
 * Core fork commit 71e3a774, SelectionElement: stock Continuum Core 1.12.1 draws the section list labels through
 * {@code textRenderer()} (white with a shadow, game font). Official Mantle drew them black while hovered and half
 * transparent black otherwise, without a shadow, in the page font.
 */
@Mixin(value = SelectionElement.class, remap = false)
public abstract class SelectionElementMixin {
  @WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ActiveTextCollector;accept(IILnet/minecraft/network/chat/Component;)V"))
  private void tconstruct$drawSectionLabel(ActiveTextCollector collector, int x, int y, Component text, Operation<Void> original,
                                           @Local(argsOnly = true) GuiGraphicsExtractor graphics, @Local(argsOnly = true) Font fontRenderer,
                                           @Local(ordinal = 1) boolean hover) {
    graphics.text(fontRenderer, text.getString(), x, y, hover ? BookText.SECTION_LABEL_HOVER_COLOR : BookText.SECTION_LABEL_COLOR, false);
  }
}
