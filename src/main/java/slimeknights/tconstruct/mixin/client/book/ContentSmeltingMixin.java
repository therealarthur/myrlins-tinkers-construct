package slimeknights.tconstruct.mixin.client.book;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import slimeknights.mantle.client.book.data.content.ContentSmelting;
import slimeknights.tconstruct.library.client.book.corefix.BookRecipes;

/**
 * Core fork commit 71e3a774, ContentSmelting#load: same as {@link ContentCraftingMixin} for {@code mantle:smelting}
 * pages. When the level has no recipe manager (every 26.1 client), a recipe the server synced wins over the integrated
 * server lookup, which is what the Core fork's lookup order gives.
 */
@Mixin(value = ContentSmelting.class, remap = false)
public abstract class ContentSmeltingMixin {
  @ModifyVariable(method = "load", at = @At("STORE"), ordinal = 0)
  private RecipeHolder<?> tconstruct$useSyncedRecipe(RecipeHolder<?> holder, @Local Level level, @Local ResourceKey<Recipe<?>> key) {
    if (!(level.recipeAccess() instanceof RecipeManager)) {
      RecipeHolder<?> synced = BookRecipes.byKey(key);
      if (synced != null) {
        return synced;
      }
    }
    return holder;
  }
}
