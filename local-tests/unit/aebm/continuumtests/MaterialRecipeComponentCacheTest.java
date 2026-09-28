package aebm.continuumtests;

import java.util.List;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Instruments;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.InstrumentComponent;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.tconstruct.library.client.recipe.ClientRecipeCache;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.recipe.ingredient.InstrumentIngredient;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipe;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;

import static org.junit.jupiter.api.Assertions.*;

final class MaterialRecipeComponentCacheTest {
  @BeforeAll static void components() { ComponentTestSetup.initialize(); }

  @Test
  void alternatingHornComponentsAndNegativeLookupUseTheActualMatchingRecipe() {
    var previous = List.copyOf(MaterialRecipeCache.getAllRecipes());
    try {
      var ponder = recipe("ponder", InstrumentIngredient.of(Items.GOAT_HORN, Instruments.PONDER_GOAT_HORN).toVanilla(), "tconstruct:horn#minecraft.ponder_goat_horn");
      var sing = recipe("sing", InstrumentIngredient.of(Items.GOAT_HORN, Instruments.SING_GOAT_HORN).toVanilla(), "tconstruct:horn#minecraft.sing_goat_horn");
      MaterialRecipeCache.rebuildRecipes(List.of(ponder, sing));
      var instruments = VanillaRegistries.createLookup().lookupOrThrow(Registries.INSTRUMENT);
      ItemStack horn = new ItemStack(Items.GOAT_HORN);
      horn.remove(DataComponents.INSTRUMENT);
      assertSame(MaterialRecipe.EMPTY, MaterialRecipeCache.findRecipe(horn), "a miss must not poison the item cache");
      for (int i = 0; i < 3; i++) {
        horn.set(DataComponents.INSTRUMENT, new InstrumentComponent(instruments.getOrThrow(Instruments.PONDER_GOAT_HORN)));
        assertSame(ponder, MaterialRecipeCache.findRecipe(horn));
        horn.set(DataComponents.INSTRUMENT, new InstrumentComponent(instruments.getOrThrow(Instruments.SING_GOAT_HORN)));
        assertSame(sing, MaterialRecipeCache.findRecipe(horn));
      }
      horn.remove(DataComponents.INSTRUMENT);
      assertSame(MaterialRecipe.EMPTY, MaterialRecipeCache.findRecipe(horn), "a cached positive must still test the current components");
    } finally {
      MaterialRecipeCache.rebuildRecipes(previous);
    }
  }

  @Test
  void cachedBroadRecipeDoesNotOverrideEarlierComponentSpecificRecipe() {
    var previous = List.copyOf(MaterialRecipeCache.getAllRecipes());
    try {
      Component specialName = Component.literal("Component-specific material");
      var special = recipe("special", DataComponentIngredient.of(DataComponents.CUSTOM_NAME, specialName, Items.IRON_INGOT), "tconstruct:iron#special");
      var general = recipe("general", Ingredient.of(Items.IRON_INGOT), "tconstruct:iron");
      MaterialRecipeCache.rebuildRecipes(List.of(special, general));
      ItemStack stack = new ItemStack(Items.IRON_INGOT);
      assertSame(general, MaterialRecipeCache.findRecipe(stack));
      stack.set(DataComponents.CUSTOM_NAME, specialName);
      assertTrue(general.getIngredient().test(stack), "the broad cached recipe still matches, so merely rechecking it is insufficient");
      assertSame(special, MaterialRecipeCache.findRecipe(stack), "recipe priority must be preserved for each component-bearing input");
      stack.remove(DataComponents.CUSTOM_NAME);
      assertSame(general, MaterialRecipeCache.findRecipe(stack));
    } finally {
      MaterialRecipeCache.rebuildRecipes(previous);
    }
  }

  @Test
  void ordinaryDisplayHelperUsesItsExplicitRegistryBeforeClientSnapshotPublication() {
    var previousSnapshot = ClientRecipeCache.getSnapshot();
    var previousRecipes = List.copyOf(MaterialRecipeCache.getAllRecipes());
    try {
      ClientRecipeCache.clear();
      var vanilla = VanillaRegistries.createLookup();
      var instruments = new MappedRegistry<Instrument>(Registries.INSTRUMENT, Lifecycle.stable());
      instruments.register(Instruments.PONDER_GOAT_HORN,
        vanilla.lookupOrThrow(Registries.INSTRUMENT).getOrThrow(Instruments.PONDER_GOAT_HORN).value(), RegistrationInfo.BUILT_IN);
      RegistryAccess access = new RegistryAccess.ImmutableRegistryAccess(List.of(instruments.freeze()));
      MaterialRecipeCache.setDisplayRegistryAccess(access);
      assertSame(RegistryAccess.EMPTY, ClientRecipeCache.getSnapshot().registryAccess(), "fixture must have no published client registry");
      var ingredient = InstrumentIngredient.of(Items.GOAT_HORN, Instruments.PONDER_GOAT_HORN).toVanilla();
      var displayed = MaterialRecipeCache.getDisplayItems(ingredient);
      assertEquals(1, displayed.size(), "ordinary helper must resolve against its explicit received/server registry");
      assertTrue(ingredient.test(displayed.getFirst()));
      assertTrue(displayed.getFirst().get(DataComponents.INSTRUMENT).instrument().is(Instruments.PONDER_GOAT_HORN));
    } finally {
      ClientRecipeCache.receive(previousSnapshot.registryAccess(), previousSnapshot.recipes());
      MaterialRecipeCache.rebuildRecipes(previousRecipes);
    }
  }

  private static MaterialRecipe recipe(String name, Ingredient ingredient, String material) {
    return new MaterialRecipe(Identifier.fromNamespaceAndPath("continuum_test", name), "", ingredient, 4, 1,
      MaterialVariantId.tryParse(material), ItemOutput.EMPTY);
  }
}
