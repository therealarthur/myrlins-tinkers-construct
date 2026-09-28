package aebm.continuumtests;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.library.client.recipe.ClientRecipeCache;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingLookup;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialFluidRecipe;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises production recipe maps and casting caches without either viewer on the test runtime classpath. */
final class ClientRecipeCacheTest {
  @Test
  void receivedMapsReplaceOldMaterialFluidsAndLogoutClearsThem() {
    ClientRecipeCache.clear();
    try {
      var first = fluid("first", null, "iron");
      var composite = fluid("composite", new MaterialId("tconstruct:iron"), "steel");
      RecipeMap firstMap = map(first, composite);
      ClientRecipeCache.receive(RegistryAccess.EMPTY, firstMap);
      long firstRevision = ClientRecipeCache.getSnapshot().revision();
      assertSame(firstMap, ClientRecipeCache.getSnapshot().recipes());
      assertEquals(List.of(first), List.copyOf(MaterialCastingLookup.getAllCastingFluids()));
      assertEquals(List.of(composite), List.copyOf(MaterialCastingLookup.getAllCompositeFluids()));

      ClientRecipeCache.materialsUpdated();
      assertTrue(ClientRecipeCache.getSnapshot().revision() > firstRevision);
      assertEquals(1, MaterialCastingLookup.getAllCastingFluids().size(), "late material sync must not append duplicates");

      // The old compatibility fallback kept the previous server's fluid recipes when DATA was empty.
      ClientRecipeCache.receive(RegistryAccess.EMPTY, RecipeMap.create(List.of()));
      assertTrue(MaterialCastingLookup.getAllCastingFluids().isEmpty());
      assertTrue(MaterialCastingLookup.getAllCompositeFluids().isEmpty());

      var second = fluid("second", null, "copper");
      RecipeMap secondMap = map(second);
      ClientRecipeCache.receive(RegistryAccess.EMPTY, secondMap);
      assertSame(secondMap, ClientRecipeCache.getSnapshot().recipes());
      assertEquals(List.of(second), List.copyOf(MaterialCastingLookup.getAllCastingFluids()));
      ClientRecipeCache.clear();
      long disconnected = ClientRecipeCache.getSnapshot().revision();
      assertSame(RecipeMap.EMPTY, ClientRecipeCache.getSnapshot().recipes());
      assertSame(RegistryAccess.EMPTY, ClientRecipeCache.getSnapshot().registryAccess());
      assertTrue(MaterialCastingLookup.getAllCastingFluids().isEmpty());
      ClientRecipeCache.materialsUpdated();
      assertEquals(disconnected, ClientRecipeCache.getSnapshot().revision(), "late material event must not revive a disconnected map");
    } finally {
      ClientRecipeCache.clear();
    }
  }

  private static MaterialFluidRecipe fluid(String name, MaterialId input, String output) {
    return new MaterialFluidRecipe(Identifier.fromNamespaceAndPath("continuum_test", name), FluidIngredient.EMPTY,
      500, input, new MaterialId("tconstruct:" + output));
  }

  private static RecipeMap map(MaterialFluidRecipe... recipes) {
    List<RecipeHolder<?>> holders = java.util.Arrays.stream(recipes)
      .<RecipeHolder<?>>map(recipe -> new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, recipe.getId()), recipe)).toList();
    return RecipeMap.create(holders);
  }
}
