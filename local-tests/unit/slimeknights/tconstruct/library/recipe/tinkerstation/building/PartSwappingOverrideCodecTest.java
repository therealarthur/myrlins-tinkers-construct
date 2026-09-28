package slimeknights.tconstruct.library.recipe.tinkerstation.building;

import aebm.continuumtests.ComponentTestSetup;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.recipe.ingredient.SizedIngredient;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.tools.TinkerTools;

import static org.junit.jupiter.api.Assertions.*;

/** Exercise registered serializer dispatch, which previously selected an unrelated recipe class. */
final class PartSwappingOverrideCodecTest {
  @BeforeAll
  static void components() { ComponentTestSetup.initialize(); }

  @Test
  void registeredDispatchPreservesPartIndicesAndSizedRequirements() {
    var ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
    var recipe = recipe(false);
    var encoded = Recipe.CODEC.encodeStart(ops, recipe).getOrThrow();
    var json = encoded.getAsJsonObject();
    assertEquals("tconstruct:part_swapping_override", json.get("type").getAsString());
    assertEquals("tconstruct:pick_head", json.get("part").getAsString());
    assertEquals(2, json.getAsJsonArray("index").size());
    assertEquals(1, json.getAsJsonArray("index").get(0).getAsInt());
    assertEquals(2, json.getAsJsonArray("index").get(1).getAsInt());
    assertEquals(7, json.get("max_stack_size").getAsInt());
    assertEquals(2, json.getAsJsonArray("extra_requirements").size());
    var restored = assertInstanceOf(PartSwappingOverrideRecipe.class, Recipe.CODEC.parse(ops, encoded).getOrThrow());
    assertSame(TinkerTables.partSwappingOverride.get(), restored.getSerializer());
    assertEquals(encoded, Recipe.CODEC.encodeStart(ops, restored).getOrThrow(), "all fields, including sized inputs, must survive dispatch roundtrip");
    // Restoring the released upstream accessor reproduces the real invalid dispatch.
    assertThrows(RuntimeException.class, () -> Recipe.CODEC.encodeStart(ops, recipe(true)).getOrThrow());
  }

  private static PartSwappingOverrideRecipe recipe(boolean wrongSerializer) {
    return new PartSwappingOverrideRecipe(Identifier.fromNamespaceAndPath("aebm_test", "override_codec"),
      Ingredient.of(TinkerTools.pickaxe.get()), 7, TinkerToolParts.pickHead.get(), new int[] {1, 2},
      List.of(SizedIngredient.fromItems(2, Items.DIAMOND), SizedIngredient.fromItems(3, Items.GOLD_INGOT))) {
      @Override public RecipeSerializer<? extends Recipe<ITinkerStationContainer>> getSerializer() {
        return wrongSerializer ? TinkerTables.fixedMaterialSwapping.get() : super.getSerializer();
      }
    };
  }
}
