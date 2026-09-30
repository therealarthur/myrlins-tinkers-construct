package slimeknights.tconstruct.mixin.client.book;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import slimeknights.mantle.client.screen.MultiModuleScreen;
import slimeknights.tconstruct.library.client.book.corefix.BookText;

/**
 * Core fork commit 71e3a774, MultiModuleScreen: station screen titles and the Inventory label use official 0x404040,
 * which has no alpha byte, and 26.1 skips such text, so the labels were invisible on the crafting station, part
 * builder, tinker station, anvils, worktable, chests and smeltery screens. Draws them opaque (0xFF404040).
 */
@Mixin(value = MultiModuleScreen.class, remap = false)
public abstract class MultiModuleScreenMixin {
  @ModifyConstant(method = {"drawContainerName", "drawPlayerInventoryName"}, constant = @Constant(intValue = 0x404040))
  private int tconstruct$opaqueLabelColor(int color) {
    return BookText.CONTAINER_LABEL_COLOR;
  }
}
