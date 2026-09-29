package slimeknights.tconstruct.library.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.client.recipe.ClientRecipeCache;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * File: SidedRecipeLookup.java (added in 3.12.2-arthur.9).
 *
 * Recipe lookups that work on both logical sides, the way {@code level.getRecipeManager()} did
 * before Minecraft 26.1.
 *
 * Why: since 26.1 a client level only exposes {@code ClientRecipeContainer}, which holds recipe
 * displays and property sets, not recipes. The server still sends every recipe type that a mod
 * requests through {@code OnDatapackSyncEvent#sendRecipes} (Tinkers requests all of its own types in
 * {@link TinkerRecipeTypes#sendRecipesToClient}), and {@link ClientRecipeCache} keeps that map for the
 * connection. So a server level answers from its {@link RecipeManager} exactly as before, and a client
 * level answers from the synced map, which is what official Tinkers read on the client.
 *
 * Only for lookups that decide what the client displays; crafting results stay server authoritative.
 * The client class is side safe to reference: it only holds common types.
 */
public final class SidedRecipeLookup {
  private SidedRecipeLookup() {}

  /**
   * Gets every recipe this side knows.
   * @param level  Level asking
   * @return  The full recipe map on a server level, the recipes synced from the server on a client level, or empty
   */
  public static RecipeMap recipes(@Nullable Level level) {
    if (level == null) {
      return RecipeMap.EMPTY;
    }
    if (level.recipeAccess() instanceof RecipeManager manager) {
      return manager.recipeMap();
    }
    if (level.isClientSide()) {
      return ClientRecipeCache.getSnapshot().recipes();
    }
    return RecipeMap.EMPTY;
  }

  /**
   * Finds the first recipe of the type that matches the input, like {@code RecipeManager#getRecipeFor}.
   * @param level  Level asking, used for matching
   * @param type   Recipe type
   * @param input  Recipe input
   * @return  Matching recipe holder, or empty if this side has none
   */
  public static <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(@Nullable Level level, RecipeType<T> type, I input) {
    if (level == null) {
      return Optional.empty();
    }
    // server levels keep the manager's own lookup, so server behavior is unchanged
    if (level.recipeAccess() instanceof RecipeManager manager) {
      return manager.getRecipeFor(type, input, level);
    }
    if (level.isClientSide()) {
      return ClientRecipeCache.getSnapshot().recipes().getRecipesFor(type, input, level).findFirst();
    }
    return Optional.empty();
  }

  /**
   * Gets a recipe by ID.
   * @param level  Level asking
   * @param id     Recipe ID
   * @return  Recipe holder, or null if this side does not know the recipe
   */
  @Nullable
  public static RecipeHolder<?> byId(@Nullable Level level, Identifier id) {
    return recipes(level).byKey(ResourceKey.create(Registries.RECIPE, id));
  }
}
