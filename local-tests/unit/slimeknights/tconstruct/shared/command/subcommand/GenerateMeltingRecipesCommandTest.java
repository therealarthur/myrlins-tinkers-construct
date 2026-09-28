package slimeknights.tconstruct.shared.command.subcommand;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.IntList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.recipe.melting.DamageableMeltingRecipe;
import slimeknights.tconstruct.library.recipe.melting.MeltingRecipeLookup;
import slimeknights.tconstruct.shared.command.subcommand.GenerateMeltingRecipesCommand.MeltingResult;
import slimeknights.tconstruct.shared.command.subcommand.GenerateMeltingRecipesCommand.UnsupportedRecipe;

import static org.junit.jupiter.api.Assertions.*;

/** Actual placement, component, recipe-codec and metadata regressions; run with the FML unit-test launcher. */
final class GenerateMeltingRecipesCommandTest {
  private static MeltingResult fluid(Fluid fluid, int amount) {
    return MeltingResult.from(new FluidStack(fluid, amount), null, fluid == Fluids.LAVA ? 1000 : 100);
  }

  private static RecipeDisplay display(SlotDisplay result) {
    return new ShapelessCraftingRecipeDisplay(List.of(), result, new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE));
  }

  @Test
  void literalOutputPreservesCountAndRejectsDemonstrationsOrAmbiguity() {
    var three = display(new SlotDisplay.ItemStackSlotDisplay(new ItemStackTemplate(Items.IRON_BARS, 3)));
    assertEquals(3, GenerateMeltingRecipesCommand.fixedResult(List.of(three)).getCount());
    assertEquals(Items.IRON_BARS, GenerateMeltingRecipesCommand.fixedResult(List.of(three, three)).getItem());
    assertThrows(UnsupportedRecipe.class, () -> GenerateMeltingRecipesCommand.fixedResult(List.of()));
    assertThrows(UnsupportedRecipe.class, () -> GenerateMeltingRecipesCommand.fixedResult(List.of(three,
      display(new SlotDisplay.ItemStackSlotDisplay(new ItemStackTemplate(Items.IRON_BARS, 2))))));
    assertThrows(UnsupportedRecipe.class, () -> GenerateMeltingRecipesCommand.fixedResult(List.of(
      display(new SlotDisplay.Composite(List.of(new SlotDisplay.ItemSlotDisplay(Items.IRON_BARS)))))));
    assertThrows(UnsupportedRecipe.class, () -> GenerateMeltingRecipesCommand.fixedResult(List.of(
      display(new SlotDisplay.TagSlotDisplay(TagKey.create(Registries.ITEM, Identifier.parse("c:ingots")))))));
  }

  @Test
  void repeatedPlacementIndicesCountEverySlotAndOutputCountDividesOnce() throws Exception {
    var constructor = PlacementInfo.class.getDeclaredConstructor(List.class, IntList.class);
    constructor.setAccessible(true);
    PlacementInfo repeated = constructor.newInstance(List.of(Ingredient.of(Items.IRON_INGOT)), IntList.of(0, -1, 0, 0));
    var result = GenerateMeltingRecipesCommand.infer(repeated, 2, item -> true, item -> false, item -> fluid(Fluids.WATER, 90));
    assertEquals(1, result.size());
    assertEquals(135, result.getFirst().fluid().getAmount());
    var ordinary = PlacementInfo.createFromOptionals(List.of(Optional.of(Ingredient.of(Items.IRON_INGOT)), Optional.empty(), Optional.of(Ingredient.of(Items.IRON_INGOT))));
    assertEquals(180, GenerateMeltingRecipesCommand.infer(ordinary, 1, item -> true, item -> false, item -> fluid(Fluids.WATER, 90)).getFirst().fluid().getAmount());
  }

  @Test
  void alternativeIngredientsTakeMinimumAndRejectMixedOrDifferentFluids() {
    var placement = PlacementInfo.create(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT));
    var result = GenerateMeltingRecipesCommand.infer(placement, 1, item -> true, item -> false,
      item -> fluid(Fluids.WATER, item == Items.IRON_INGOT ? 90 : 25));
    assertEquals(25, result.getFirst().fluid().getAmount());
    assertThrows(UnsupportedRecipe.class, () -> GenerateMeltingRecipesCommand.infer(placement, 1, item -> true, item -> true,
      item -> item == Items.IRON_INGOT ? fluid(Fluids.WATER, 90) : MeltingResult.EMPTY));
    assertThrows(UnsupportedRecipe.class, () -> GenerateMeltingRecipesCommand.infer(placement, 1, item -> true, item -> true,
      item -> fluid(item == Items.IRON_INGOT ? Fluids.WATER : Fluids.LAVA, 90)));
    assertThrows(UnsupportedRecipe.class, () -> GenerateMeltingRecipesCommand.infer(placement, 1, item -> false, item -> true, item -> fluid(Fluids.WATER, 90)));
    assertThrows(UnsupportedRecipe.class, () -> GenerateMeltingRecipesCommand.infer(PlacementInfo.NOT_PLACEABLE, 1, item -> true, item -> true, item -> fluid(Fluids.WATER, 90)));
  }

  @Test
  void ignoredSlotsAndFractionalByproductsNeverProduceEmptyFluidOutputs() {
    var placement = PlacementInfo.create(List.of(Ingredient.of(Items.STICK), Ingredient.of(Items.IRON_INGOT)));
    var result = GenerateMeltingRecipesCommand.infer(placement, 3, item -> true, item -> item == Items.STICK,
      item -> item == Items.STICK ? MeltingResult.EMPTY : fluid(Fluids.WATER, 2));
    assertTrue(result.isEmpty(), "rounding below 1 mB must omit the output, not serialize an invalid empty result");
  }

  @Test
  void duplicateRecipeIntersectionAdvancesPastFirstFluidAndKeepsMinimum() {
    var first = List.of(fluid(Fluids.WATER, 90), fluid(Fluids.LAVA, 60));
    var second = List.of(fluid(Fluids.LAVA, 30), fluid(Fluids.WATER, 40));
    var result = MeltingResult.intersection(first, second);
    assertEquals(2, result.size());
    assertEquals(40, result.get(0).fluid().getAmount());
    assertEquals(Fluids.WATER, result.get(0).fluid().getFluid());
    assertEquals(30, result.get(1).fluid().getAmount());
    assertEquals(Fluids.LAVA, result.get(1).fluid().getFluid());
    assertEquals(1, MeltingResult.intersection(first, List.of(fluid(Fluids.LAVA, 12))).size());
    assertTrue(MeltingResult.intersection(first, List.of()).isEmpty());
    assertEquals(90, first.getFirst().fluid().getAmount(), "intersection must not mutate cached source fluids");
  }

  @Test
  void fluidComponentsRemainDistinctAndUnsupportedSerializationIsRejected() {
    FluidStack first = new FluidStack(Fluids.WATER, 90);
    CompoundTag firstTag = new CompoundTag();
    firstTag.putInt("flavor", 1);
    first.set(DataComponents.CUSTOM_DATA, CustomData.of(firstTag));
    FluidStack second = first.copy();
    CompoundTag secondTag = new CompoundTag();
    secondTag.putInt("flavor", 2);
    second.set(DataComponents.CUSTOM_DATA, CustomData.of(secondTag));
    var a = MeltingResult.from(first, null, 100);
    var b = MeltingResult.from(second, null, 100);
    assertFalse(MeltingResult.matches(a, b));
    assertTrue(MeltingResult.intersection(List.of(a), List.of(b)).isEmpty());
    assertTrue(FluidStack.isSameFluidSameComponents(a.fluid(), a.toOutput().get()));
    first.set(DataComponents.CUSTOM_NAME, Component.literal("unserializable by Core FluidOutput JSON"));
    assertThrows(UnsupportedRecipe.class, () -> MeltingResult.from(first, null, 100));
    assertThrows(UnsupportedRecipe.class, () -> MeltingResult.merge(fluid(Fluids.WATER, Integer.MAX_VALUE), fluid(Fluids.WATER, 1)));
  }

  @Test
  void taggedOutputsKeepTagButDifferentTagsDoNotIntersect() {
    var tag = TagKey.create(Registries.FLUID, Identifier.parse("c:water"));
    var tagged = MeltingResult.from(new FluidStack(Fluids.WATER, 90), tag, 100);
    assertEquals(tag, tagged.withAmount(45).toOutput().getTag());
    var otherTag = TagKey.create(Registries.FLUID, Identifier.parse("c:other_water"));
    assertFalse(MeltingResult.matches(tagged, MeltingResult.from(new FluidStack(Fluids.WATER, 90), otherTag, 100)));
    var minimum = MeltingResult.min(tagged, fluid(Fluids.WATER, 20));
    assertNull(minimum.tag(), "mixing a tagged and exact fluid must retain the simpler exact identity");
    assertEquals(20, minimum.fluid().getAmount());
  }

  @Test
  void modernFluidResourceHandlerReadsAmountsAndRejectsUnrepresentableContainers() {
    var single = new ReadOnlyFluidHandler(List.of(FluidResource.of(Fluids.WATER)), List.of(750L));
    var result = GenerateMeltingRecipesCommand.readFluidHandler(single);
    assertEquals(Fluids.WATER, result.fluid().getFluid());
    assertEquals(750, result.fluid().getAmount());
    assertEquals(1000, new GenerateMeltingRecipesCommand.MeltingCache().get(Items.WATER_BUCKET).fluid().getAmount());
    assertThrows(UnsupportedRecipe.class, () -> GenerateMeltingRecipesCommand.readFluidHandler(
      new ReadOnlyFluidHandler(List.of(FluidResource.of(Fluids.WATER)), List.of((long) Integer.MAX_VALUE + 1))));
    assertThrows(UnsupportedRecipe.class, () -> GenerateMeltingRecipesCommand.readFluidHandler(
      new ReadOnlyFluidHandler(List.of(FluidResource.of(Fluids.WATER), FluidResource.of(Fluids.LAVA)), List.of(100L, 50L))));
  }

  private record ReadOnlyFluidHandler(List<FluidResource> resources, List<Long> amounts) implements ResourceHandler<FluidResource> {
    @Override public int size() { return resources.size(); }
    @Override public FluidResource getResource(int index) { return resources.get(index); }
    @Override public long getAmountAsLong(int index) { return amounts.get(index); }
    @Override public long getCapacityAsLong(int index, FluidResource resource) { return amounts.get(index); }
    @Override public boolean isValid(int index, FluidResource resource) { return resources.get(index).equals(resource); }
    @Override public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) { throw new AssertionError("read-only inference must not insert"); }
    @Override public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) { throw new AssertionError("read-only inference must not extract"); }
  }

  @Test
  void lookupUnfreezesOnBuilderFailure() throws Exception {
    var field = MeltingRecipeLookup.class.getDeclaredField("meltingFrozen");
    field.setAccessible(true);
    assertFalse(field.getBoolean(null));
    assertThrows(IllegalStateException.class, () -> GenerateMeltingRecipesCommand.withFrozenLookup(() -> {
      try {
        assertTrue(field.getBoolean(null));
      } catch (IllegalAccessException exception) {
        throw new AssertionError(exception);
      }
      throw new IllegalStateException("synthetic codec failure");
    }));
    assertFalse(field.getBoolean(null), "later recipe reloads must be allowed after an encoding failure");
  }

  @Test
  void generatedRecipeCodecRoundtripsDamageAndByproducts() {
    var registryAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    var ops = registryAccess.createSerializationContext(JsonOps.INSTANCE);
    List<JsonElement> encoded = new ArrayList<>();
    RecipeOutput output = new RecipeOutput() {
      @Override
      public void accept(ResourceKey<Recipe<?>> key, Recipe<?> recipe, AdvancementHolder advancement, ICondition... conditions) {
        assertEquals(Identifier.parse("tinkers_generated:melting/minecraft/iron_pickaxe"), key.identifier());
        assertInstanceOf(DamageableMeltingRecipe.class, recipe);
        assertNull(advancement);
        assertEquals(0, conditions.length);
        encoded.add(Recipe.CODEC.encodeStart(ops, recipe).getOrThrow());
      }
      @Override public Advancement.Builder advancement() { return Advancement.Builder.advancement(); }
      @Override public void includeRootAdvancement() {}
    };
    GenerateMeltingRecipesCommand.withFrozenLookup(() -> {
      GenerateMeltingRecipesCommand.buildRecipe(Items.IRON_PICKAXE, List.of(fluid(Fluids.WATER, 90), fluid(Fluids.LAVA, 40)), output);
      assertEquals(1, encoded.size());
      var json = encoded.getFirst().getAsJsonObject();
      assertEquals(40, json.getAsJsonObject("result").get("amount").getAsInt());
      assertEquals(10, json.getAsJsonObject("result").get("unit_size").getAsInt());
      assertEquals(90, json.getAsJsonArray("byproducts").get(0).getAsJsonObject().get("amount").getAsInt());
      var decoded = assertInstanceOf(DamageableMeltingRecipe.class, Recipe.CODEC.parse(ops, json).getOrThrow());
      assertEquals(json, Recipe.CODEC.encodeStart(ops, decoded).getOrThrow());
    });
  }

  @Test
  void currentPackMetadataDecodesAndRecipePathsUseSingularFolder() {
    var json = GenerateMeltingRecipesCommand.packMetadata();
    var parsed = PackMetadataSection.SERVER_TYPE.codec().parse(JsonOps.INSTANCE, json.get("pack")).getOrThrow();
    assertTrue(parsed.supportedFormats().isValueInRange(net.minecraft.SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA)));
    assertTrue(json.getAsJsonObject("pack").has("min_format"));
    assertTrue(json.getAsJsonObject("pack").has("max_format"));
    var pack = Path.of("generated-pack");
    assertEquals(pack.resolve("data/tinkers_generated/recipe/melting/minecraft/iron_bars.json"),
      GenerateMeltingRecipesCommand.recipePath(pack, Identifier.parse("tinkers_generated:melting/minecraft/iron_bars")));
    assertThrows(UnsupportedRecipe.class, () -> GenerateMeltingRecipesCommand.recipePath(pack, Identifier.parse("tinkers_generated:../../../escape")));
  }
}
