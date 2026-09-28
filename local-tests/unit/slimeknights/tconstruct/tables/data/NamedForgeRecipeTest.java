package slimeknights.tconstruct.tables.data;

import aebm.continuumtests.ComponentTestSetup;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import slimeknights.mantle.recipe.crafting.ShapedRetexturedRecipe;
import slimeknights.mantle.recipe.crafting.ShapedRetexturedRecipeBuilder;
import slimeknights.mantle.util.RetexturedHelper;
import slimeknights.tconstruct.tables.TinkerTables;

import static org.junit.jupiter.api.Assertions.*;

/** Exercise the actual datagen builder, registered Core serializer and crafted textured result. */
final class NamedForgeRecipeTest {
  @BeforeAll
  static void initializeComponents() {
    ComponentTestSetup.initialize();
  }

  @Test
  void forgeNameSurvivesDatagenCodecAndTextureApplication() {
    var access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    var ops = access.createSerializationContext(JsonOps.INSTANCE);
    List<Recipe<?>> generated = new ArrayList<>();
    RecipeOutput output = new RecipeOutput() {
      @Override
      public void accept(ResourceKey<Recipe<?>> key, Recipe<?> recipe, AdvancementHolder advancement, ICondition... conditions) {
        generated.add(recipe);
      }
      @Override public Advancement.Builder advancement() { return Advancement.Builder.advancement(); }
      @Override public void includeRootAdvancement() {}
    };
    for (var anvil : List.of(TinkerTables.tinkersAnvil.get(), TinkerTables.scorchedAnvil.get())) {
      var template = TableRecipeProvider.toolForgeResult(anvil.asItem());
      ShapedRetexturedRecipeBuilder.fromShaped(
        ShapedRecipeBuilder.shaped(BuiltInRegistries.ITEM, RecipeCategory.DECORATIONS, template)
          .define('m', Items.IRON_BLOCK).pattern("mm")
          .unlockedBy("has_iron", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_BLOCK)))
        .setSource(Ingredient.of(Items.IRON_BLOCK)).setMatchAll()
        .build(output, Identifier.fromNamespaceAndPath("aebm_test", "named_" + BuiltInRegistries.BLOCK.getKey(anvil).getPath()));
      var json = Recipe.CODEC.encodeStart(ops, generated.getLast()).getOrThrow();
      assertTrue(json.getAsJsonObject().getAsJsonObject("result").getAsJsonObject("components").has("minecraft:custom_name"));
      var recipe = assertInstanceOf(ShapedRetexturedRecipe.class, Recipe.CODEC.parse(ops, json).getOrThrow());
      var input = CraftingInput.of(2, 1, List.of(new ItemStack(Items.IRON_BLOCK), new ItemStack(Items.IRON_BLOCK)));
      ItemStack crafted = recipe.assemble(input);
      assertEquals(anvil.asItem(), crafted.getItem());
      assertEquals(1, crafted.getCount());
      assertEquals(Component.translatable("block.tconstruct.tool_forge"), crafted.get(DataComponents.CUSTOM_NAME));
      assertEquals(Blocks.IRON_BLOCK, RetexturedHelper.getTexture(crafted));
      assertEquals(crafted.get(DataComponents.CUSTOM_NAME), recipe.getResultItem(Items.IRON_BLOCK).get(DataComponents.CUSTOM_NAME));
    }
    assertEquals(2, generated.size());
  }
}
