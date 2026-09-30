package slimeknights.tconstruct.mixin.client.book;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.mantle.client.book.data.content.ContentCrafting;
import slimeknights.tconstruct.library.client.book.corefix.BookRecipes;

/**
 * Core fork commit 71e3a774, ContentCrafting#getRecipeHolder: 26.1 clients have no recipe manager, and stock
 * Continuum Core 1.12.1 then only asks the integrated server, so {@code mantle:crafting} pages draw an empty grid in
 * multiplayer. When the level has no recipe manager, the recipes the server synced ({@link BookRecipes}) are used
 * first, then the integrated server as before.
 */
@Mixin(value = ContentCrafting.class, remap = false)
public abstract class ContentCraftingMixin {
  @Inject(method = "getRecipeHolder", cancellable = true,
          at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getSingleplayerServer()Lnet/minecraft/client/server/IntegratedServer;", ordinal = 0))
  private static void tconstruct$useSyncedRecipe(Minecraft minecraft, Level level, Identifier recipe, CallbackInfoReturnable<RecipeHolder<?>> cir) {
    RecipeHolder<?> synced = BookRecipes.byKey(ResourceKey.create(Registries.RECIPE, recipe));
    if (synced != null) {
      cir.setReturnValue(synced);
    }
  }
}
