package slimeknights.tconstruct.common.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingLookup;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialFluidRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.ToolCastingRecipe;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuel;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuelLookup;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipe;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;

import java.util.List;
import java.util.stream.Stream;

/** Rebuilds recipe side caches from loaded recipes instead of relying on recipe constructor side effects. */
public final class TinkerRecipeCacheRebuilder {
  private TinkerRecipeCacheRebuilder() {}

  /** Rebuilds material and material casting lookups from a recipe manager. */
  public static void rebuild(RegistryAccess registryAccess, RecipeManager manager) {
    rebuild(registryAccess, manager.recipeMap());
  }

  /** Rebuilds material and material casting lookups from a recipe map. */
  public static void rebuild(RegistryAccess registryAccess, RecipeMap recipeMap) {
    rebuild(registryAccess, recipeMap, false);
  }

  /** Client snapshots are complete: an empty DATA type must remove old fluids, never borrow another server's data. */
  public static void rebuildClient(RegistryAccess registryAccess, RecipeMap recipeMap) {
    rebuild(registryAccess, recipeMap, true);
  }

  private static void rebuild(RegistryAccess registryAccess, RecipeMap recipeMap, boolean clientSnapshot) {
    MaterialRecipeCache.setDisplayRegistryAccess(registryAccess);
    MaterialRecipeCache.rebuildRecipes(getRecipes(recipeMap, TinkerRecipeTypes.MATERIAL.get(), MaterialRecipe.class));

    List<MaterialCastingRecipe> materialCastingRecipes = Stream.concat(
      getRecipes(recipeMap, TinkerRecipeTypes.CASTING_TABLE.get(), MaterialCastingRecipe.class).stream(),
      getRecipes(recipeMap, TinkerRecipeTypes.CASTING_BASIN.get(), MaterialCastingRecipe.class).stream()).toList();
    List<ToolCastingRecipe> toolCastingRecipes = Stream.concat(
      getRecipes(recipeMap, TinkerRecipeTypes.CASTING_TABLE.get(), ToolCastingRecipe.class).stream(),
      getRecipes(recipeMap, TinkerRecipeTypes.CASTING_BASIN.get(), ToolCastingRecipe.class).stream()).toList();

    List<MaterialFluidRecipe> fluids = getRecipes(recipeMap, TinkerRecipeTypes.DATA.get(), MaterialFluidRecipe.class);
    if (!clientSnapshot && fluids.isEmpty()) {
      var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
      if (server != null) {
        fluids = getRecipes(server.getRecipeManager().recipeMap(), TinkerRecipeTypes.DATA.get(), MaterialFluidRecipe.class);
      }
    }

    if (clientSnapshot) {
      MaterialCastingLookup.rebuildRecipesExact(fluids, materialCastingRecipes, toolCastingRecipes);
    } else {
      MaterialCastingLookup.rebuildRecipes(fluids, materialCastingRecipes, toolCastingRecipes);
    }
    // Upstream 3.12.4: rebuild liquid fuels from the loaded recipes on both sides (upstream also runs this from its
    // JEI plugin on the client). An empty FUEL list only drops the negative cache, so a client snapshot without
    // fuel recipes keeps the fuels its recipe constructors registered.
    MeltingFuelLookup.rebuild(getRecipes(recipeMap, TinkerRecipeTypes.FUEL.get(), MeltingFuel.class));
  }

  /** Gets all recipes of the given type and class from a loaded recipe map. */
  @SuppressWarnings({"rawtypes", "unchecked"})
  public static <C> List<C> getRecipes(RecipeMap recipeMap, RecipeType<?> type, Class<C> clazz) {
    return ((RecipeMap)recipeMap).byType((RecipeType)type).stream()
      .map(holder -> ((RecipeHolder<?>)holder).value())
      .filter(clazz::isInstance)
      .map(clazz::cast)
      .toList();
  }
}
