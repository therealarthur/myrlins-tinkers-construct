package aebm.continuumtests;

import java.util.List;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.recipe.helper.TypeAwareRecipeSerializer;
import slimeknights.mantle.recipe.ingredient.EmptyIngredient;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayMapper;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.ingredient.LegacyIngredientType;
import slimeknights.tconstruct.library.recipe.molding.MoldingRecipe;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.material.IMaterialValue;
import slimeknights.tconstruct.library.recipe.partbuilder.IPartBuilderContainer;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;
import slimeknights.tconstruct.library.recipe.partbuilder.recycle.PartBuilderRecycle;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

import static org.junit.jupiter.api.Assertions.*;

/** Runs real recipe mapping without REI, JEI, a client, or a synthetic copy of recipe behavior. */
final class RecipeDisplayMapperTest {
  private static final Identifier ID = Identifier.fromNamespaceAndPath("continuum_test", "molding");

  @BeforeAll
  static void components() {
    var lookup = VanillaRegistries.createLookup();
    CommonHooks.markComponentClassAsValid(lookup.lookupOrThrow(Registries.ITEM).getOrThrow(ItemTags.SWORDS).getClass());
    BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach(DataComponentInitializers.PendingComponents::apply);
  }

  @Test
  void moldingPreservesComponentInputAndReusablePattern() {
    Component name = Component.literal("Exact molding input");
    Ingredient material = DataComponentIngredient.of(DataComponents.CUSTOM_NAME, name, Items.SAND);
    MoldingRecipe recipe = recipe(material, Ingredient.of(Items.STICK), false);
    RecipeDisplayData display = map(recipe).getFirst();
    assertEquals(1, display.inputs().size());
    assertEquals(1, display.catalysts().size());
    ItemStack input = ((RecipeDisplayData.ItemValue) display.inputs().getFirst().getFirst()).stack();
    assertTrue(recipe.getMaterial().test(input));
    assertEquals(name, input.get(DataComponents.CUSTOM_NAME));
    assertFalse(recipe.getMaterial().test(new ItemStack(Items.SAND)));
    assertTrue(((RecipeDisplayData.ItemValue) display.catalysts().getFirst().getFirst()).stack().is(Items.STICK));
    assertTrue(((RecipeDisplayData.ItemValue) display.outputs().getFirst().getFirst()).stack().is(Items.GLASS));
  }

  @Test
  void moldingConsumedPatternAndAbsentPatternRemainDifferent() {
    var consumed = map(recipe(Ingredient.of(Items.SAND), Ingredient.of(Items.STICK), true)).getFirst();
    assertEquals(2, consumed.inputs().size());
    assertTrue(consumed.catalysts().isEmpty());
    var absent = map(recipe(Ingredient.of(Items.SAND), EmptyIngredient.VANILLA, false)).getFirst();
    assertEquals(1, absent.inputs().size());
    assertTrue(absent.catalysts().isEmpty());
  }

  @Test
  void unresolvedRequiredPatternDoesNotBecomeFreeRecipe() {
    MaterialRecipeCache.setDisplayRegistryAccess(RegistryAccess.EMPTY);
    Ingredient missing = LegacyIngredientType.ofTag(TagKey.create(Registries.ITEM, ID));
    assertTrue(map(recipe(Ingredient.of(Items.SAND), missing, false)).isEmpty());
  }

  @Test
  void recyclingRejectsUnmatchedUnenchantedAndExhaustedInputs() {
    var selected = new Pattern(ID);
    var recycling = new PartBuilderRecycle(ID, Ingredient.of(Items.IRON_SWORD), Ingredient.of(Items.STICK),
      Map.of(selected, ItemOutput.fromItem(Items.IRON_INGOT)));
    assertFalse(recycling.matches(new PartInput(new ItemStack(Items.DIRT), new ItemStack(Items.STICK)), null),
      "an unenchanted item must still match the tool ingredient");
    assertFalse(recycling.matches(new PartInput(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.DIRT)), null),
      "an unenchanted tool must still match the pattern ingredient");
    ItemStack exhausted = new ItemStack(Items.IRON_SWORD);
    exhausted.setDamageValue(exhausted.getMaxDamage());
    assertFalse(recycling.matches(new PartInput(exhausted, new ItemStack(Items.STICK)), null), "zero recovered material must not match");
    assertTrue(recycling.matches(new PartInput(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.STICK)), null));
  }

  @Test
  void recyclingDisplayAlternativesExcludeEquivalentSelectedPattern() {
    var selected = new Pattern(ID);
    var second = new Pattern("continuum_test:other");
    var recycling = new PartBuilderRecycle(ID, Ingredient.of(Items.IRON_SWORD), Ingredient.of(Items.STICK),
      Map.of(selected, ItemOutput.fromItem(Items.IRON_INGOT), second, ItemOutput.fromItem(Items.STICK)));
    var input = new PartInput(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.STICK));
    var alternatives = recycling.getDisplayLeftovers(input, new Pattern(ID));
    assertEquals(1, alternatives.size());
    assertTrue(alternatives.getFirst().is(Items.STICK));
    // With exactly one eligible alternative the real runtime result is deterministic despite its RNG API.
    assertTrue(recycling.getLeftover(input, new Pattern(ID)).is(Items.STICK));
  }

  private record PartInput(ItemStack stack, ItemStack pattern) implements IPartBuilderContainer {
    @Override public ItemStack getStack() { return stack; }
    @Override public ItemStack getPatternStack() { return pattern; }
    @Override public IMaterialValue getMaterial() { return null; }
  }

  private static List<RecipeDisplayData> map(MoldingRecipe recipe) {
    return RecipeDisplayMapper.map(new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, ID), recipe), RegistryAccess.EMPTY, null);
  }

  private static MoldingRecipe recipe(Ingredient material, Ingredient pattern, boolean consumed) {
    TypeAwareRecipeSerializer<MoldingRecipe> serializer = new TypeAwareRecipeSerializer<>() {
      @Override public RecipeType<?> getType() { return TinkerRecipeTypes.MOLDING_TABLE.get(); }
      @Override public RecipeSerializer<MoldingRecipe> getSerializer() { return TinkerSmeltery.moldingTableSerializer.get(); }
    };
    return new MoldingRecipe(serializer, ID, material, pattern, consumed, ItemOutput.fromItem(Items.GLASS));
  }
}
