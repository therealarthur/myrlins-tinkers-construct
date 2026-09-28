package slimeknights.tconstruct.library.client.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.RecipeMap;
import slimeknights.tconstruct.common.recipe.RecipeCacheInvalidator;
import slimeknights.tconstruct.common.recipe.TinkerRecipeCacheRebuilder;

/** Received server recipes, shared by optional viewers without loading either viewer's classes. */
public final class ClientRecipeCache {
  private ClientRecipeCache() {}

  public record Snapshot(RegistryAccess registryAccess, RecipeMap recipes, long revision) {}

  private static volatile Snapshot snapshot = new Snapshot(RegistryAccess.EMPTY, RecipeMap.EMPTY, 0);

  public static Snapshot getSnapshot() {
    return snapshot;
  }

  /** Called on the client thread after recipes and their tags have arrived. Replaces the previous connection's data. */
  public static void receive(RegistryAccess registryAccess, RecipeMap recipes) {
    RecipeCacheInvalidator.reload(true);
    TinkerRecipeCacheRebuilder.rebuildClient(registryAccess, recipes);
    snapshot = new Snapshot(registryAccess, recipes, snapshot.revision() + 1);
  }

  /** Material packets may arrive after the recipe packet; invalidate expanded viewer results as well. */
  public static void materialsUpdated() {
    Snapshot previous = snapshot;
    if (previous.recipes() != RecipeMap.EMPTY) {
      TinkerRecipeCacheRebuilder.rebuildClient(previous.registryAccess(), previous.recipes());
      snapshot = new Snapshot(previous.registryAccess(), previous.recipes(), previous.revision() + 1);
    }
  }

  /** Drops references to the old connection and removes material fluids even when the next server has none. */
  public static void clear() {
    TinkerRecipeCacheRebuilder.rebuildClient(RegistryAccess.EMPTY, RecipeMap.EMPTY);
    snapshot = new Snapshot(RegistryAccess.EMPTY, RecipeMap.EMPTY, snapshot.revision() + 1);
  }
}
