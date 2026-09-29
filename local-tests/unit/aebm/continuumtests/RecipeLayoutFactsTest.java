package aebm.continuumtests;

import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.recipe.helper.FluidOutput;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.recipe.helper.TypeAwareRecipeSerializer;
import slimeknights.mantle.recipe.ingredient.EmptyIngredient;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.FluidValue;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.ItemValue;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayMapper;
import slimeknights.tconstruct.library.client.recipe.RecipeLayout;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe;
import slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe.AlloyIngredient;
import slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuel;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.melting.MeltingRecipe;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Layout facts for the per-category REI layouts, produced by the viewer-neutral mapper from real recipe objects.
 * Neither viewer is loaded; this checks the numbers and roles the layouts draw, not pixels.
 */
final class RecipeLayoutFactsTest {
  private static final Identifier ID = Identifier.fromNamespaceAndPath("continuum_test", "layout");

  @BeforeAll
  static void components() {
    ComponentTestSetup.initialize();
    MaterialRecipeCache.setDisplayRegistryAccess(RegistryAccess.EMPTY);
  }

  @Test
  void castingKeepsCoolingCastRoleAndBlock() {
    var consumed = RecipeDisplayMapper.casting(ID, casting(Ingredient.of(Items.SAND), true, 80, false), false);
    assertEquals(1, consumed.size());
    RecipeDisplayData table = consumed.getFirst();
    assertEquals("casting_table", table.category().getPath());
    assertEquals(80, RecipeLayout.getInt(table.layout(), RecipeLayout.COOLING, -1));
    assertEquals(RecipeLayout.ROLE_CONSUMED, RecipeLayout.getInt(table.layout(), RecipeLayout.CAST, -1));
    assertFalse(RecipeLayout.getBoolean(table.layout(), RecipeLayout.BASIN));
    assertEquals(2, table.inputs().size(), "fluid then consumed cast");
    assertInstanceOf(FluidValue.class, table.inputs().getFirst().getFirst());
    assertEquals(90, ((FluidValue) table.inputs().getFirst().getFirst()).stack().getAmount(), "tank shows the real fluid amount");
    assertTrue(((ItemValue) table.inputs().get(1).getFirst()).stack().is(Items.SAND));
    assertTrue(table.catalysts().isEmpty());

    RecipeDisplayData kept = RecipeDisplayMapper.casting(ID, casting(Ingredient.of(Items.SAND), false, 40, true), true).getFirst();
    assertEquals("casting_basin", kept.category().getPath());
    assertEquals(RecipeLayout.ROLE_KEPT, RecipeLayout.getInt(kept.layout(), RecipeLayout.CAST, -1));
    assertTrue(RecipeLayout.getBoolean(kept.layout(), RecipeLayout.BASIN));
    assertEquals(1, kept.inputs().size(), "a reusable cast is not a consumed input");
    assertEquals(1, kept.catalysts().size());

    RecipeDisplayData none = RecipeDisplayMapper.casting(ID, casting(EmptyIngredient.VANILLA, false, 20, false), false).getFirst();
    assertEquals(RecipeLayout.ROLE_NONE, RecipeLayout.getInt(none.layout(), RecipeLayout.CAST, -1));
    assertEquals(1, none.inputs().size());
  }

  @Test
  void meltingIsOneDisplayWithBothControllerAmountsAndAFoundryPage() {
    var recipe = new MeltingRecipe(ID, "", Ingredient.of(Items.IRON_INGOT), FluidOutput.fromFluid(Fluids.LAVA, 90), 800, 40, List.of(), false);
    var displays = RecipeDisplayMapper.melting(ID, recipe);
    assertEquals(2, displays.size());
    RecipeDisplayData melting = displays.stream().filter(display -> display.category().getPath().equals("melting")).findFirst().orElseThrow();
    assertEquals(800, RecipeLayout.getInt(melting.layout(), RecipeLayout.TEMPERATURE, -1));
    assertEquals(40, RecipeLayout.getInt(melting.layout(), RecipeLayout.TIME, -1));
    assertEquals(RecipeLayout.ORE_NONE, RecipeLayout.getInt(melting.layout(), RecipeLayout.ORE, -1));
    assertEquals(90, RecipeLayout.getInt(melting.layout(), RecipeLayout.MELTER_AMOUNT, -1));
    assertEquals(90, RecipeLayout.getInt(melting.layout(), RecipeLayout.SMELTERY_AMOUNT, -1));
    assertTrue(melting.catalysts().isEmpty(), "controllers are workstations, not per-display catalysts");
    RecipeDisplayData foundry = displays.stream().filter(display -> display.category().getPath().equals("foundry")).findFirst().orElseThrow();
    assertEquals(1, foundry.outputs().size());
    assertEquals(800, RecipeLayout.getInt(foundry.layout(), RecipeLayout.TEMPERATURE, -1));
  }

  @Test
  void alloyKeepsRecipeOrderThroughTheCatalystMask() {
    var recipe = new AlloyRecipe(ID, List.of(
      new AlloyIngredient(FluidIngredient.of(new FluidStack(Fluids.WATER, 10)), false),
      new AlloyIngredient(FluidIngredient.of(new FluidStack(Fluids.LAVA, 20)), true),
      new AlloyIngredient(FluidIngredient.of(new FluidStack(Fluids.WATER, 30)), false)),
      FluidOutput.fromFluid(Fluids.LAVA, 40), 900);
    RecipeDisplayData alloy = RecipeDisplayMapper.alloy(ID, recipe).getFirst();
    assertEquals(0b010, RecipeLayout.getInt(alloy.layout(), RecipeLayout.CATALYST_MASK, -1));
    assertEquals(900, RecipeLayout.getInt(alloy.layout(), RecipeLayout.TEMPERATURE, -1));
    assertEquals(List.of(10, 30), alloy.inputs().stream().map(input -> ((FluidValue) input.getFirst()).stack().getAmount()).toList());
    assertEquals(20, ((FluidValue) alloy.catalysts().getFirst().getFirst()).stack().getAmount());
  }

  @Test
  void liquidFuelFactsAndUsableFuelOrder() {
    var hot = new MeltingFuel(Identifier.fromNamespaceAndPath("continuum_test", "hot"), FluidIngredient.of(new FluidStack(Fluids.LAVA, 50)), 100, 1500, 20);
    var cool = new MeltingFuel(Identifier.fromNamespaceAndPath("continuum_test", "cool"), FluidIngredient.of(new FluidStack(Fluids.WATER, 10)), 80, 800, 10);
    var solid = new MeltingFuel(Identifier.fromNamespaceAndPath("continuum_test", "solid"), FluidIngredient.EMPTY, 40, 800, 10);
    RecipeDisplayData fuel = RecipeDisplayMapper.liquidFuel(ID, hot).getFirst();
    assertEquals(1500, RecipeLayout.getInt(fuel.layout(), RecipeLayout.TEMPERATURE, -1));
    assertEquals(20, RecipeLayout.getInt(fuel.layout(), RecipeLayout.RATE, -1));
    assertEquals(100, RecipeLayout.getInt(fuel.layout(), RecipeLayout.DURATION, -1));
    assertFalse(RecipeLayout.getBoolean(fuel.layout(), RecipeLayout.SOLID));
    assertTrue(RecipeDisplayMapper.liquidFuel(ID, solid).isEmpty(), "the solid fuel is not a liquid page");
    assertTrue(RecipeDisplayMapper.solidFuel(ID, solid, ItemStack.EMPTY, List.of(new ItemStack(Items.COAL)), ItemStack.EMPTY, null).isEmpty(),
      "solid fuel pages need a level for burn times and must not invent them");

    List<FluidStack> usable = RecipeDisplayMapper.usableFuels(List.of(hot, cool, solid), 700);
    assertEquals(List.of(Fluids.WATER, Fluids.LAVA), usable.stream().map(FluidStack::getFluid).toList(), "coolest usable fuel first, solid excluded");
    assertEquals(List.of(Fluids.LAVA), RecipeDisplayMapper.usableFuels(List.of(hot, cool, solid), 1000).stream().map(FluidStack::getFluid).toList());
    assertTrue(RecipeDisplayMapper.usableFuels(List.of(hot, cool), 2000).isEmpty());
  }

  @Test
  void layoutFactsSurviveAJsonRoundTrip() {
    CompoundTag layout = new CompoundTag();
    layout.putBoolean(RecipeLayout.TOOL_INPUT, true);
    layout.put(RecipeLayout.STATION_SLOTS, new IntArrayTag(new int[] {0, 2, 4}));
    var json = CompoundTag.CODEC.encodeStart(JsonOps.INSTANCE, layout).getOrThrow();
    CompoundTag decoded = CompoundTag.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    assertTrue(RecipeLayout.getBoolean(decoded, RecipeLayout.TOOL_INPUT));
    assertArrayEquals(new int[] {0, 2, 4}, RecipeLayout.getIntArray(decoded, RecipeLayout.STATION_SLOTS));
    assertFalse(RecipeLayout.getBoolean(decoded, RecipeLayout.ANVIL), "absent flags read false");
    assertArrayEquals(new int[0], RecipeLayout.getIntArray(decoded, RecipeLayout.LAYOUT_SLOTS));
  }

  @Test
  void displayDataLayoutIsDefensivelyCopied() {
    CompoundTag layout = new CompoundTag();
    layout.putInt(RecipeLayout.COST, 2);
    var data = new RecipeDisplayData(ID, ID, List.of(), List.of(), List.of(), List.of(), List.of(), layout, List.of());
    layout.putInt(RecipeLayout.COST, 7);
    assertEquals(2, RecipeLayout.getInt(data.layout(), RecipeLayout.COST, -1), "caller changes must not reach a cached display");
    data.layout().putInt(RecipeLayout.COST, 9);
    assertEquals(2, RecipeLayout.getInt(data.layout(), RecipeLayout.COST, -1), "readers get a copy");
    assertTrue(new RecipeDisplayData(ID, ID, List.of(), List.of(), List.of(), List.of(), List.of()).layout().isEmpty(), "old constructor has no facts");
  }

  private static ItemCastingRecipe casting(Ingredient cast, boolean consumed, int cooling, boolean basin) {
    TypeAwareRecipeSerializer<ItemCastingRecipe> serializer = new TypeAwareRecipeSerializer<>() {
      @Override public RecipeType<?> getType() { return basin ? TinkerRecipeTypes.CASTING_BASIN.get() : TinkerRecipeTypes.CASTING_TABLE.get(); }
      @Override public RecipeSerializer<ItemCastingRecipe> getSerializer() { return basin ? TinkerSmeltery.basinRecipeSerializer.get() : TinkerSmeltery.tableRecipeSerializer.get(); }
    };
    return new ItemCastingRecipe(serializer, ID, "", cast, FluidIngredient.of(new FluidStack(Fluids.LAVA, 90)),
      ItemOutput.fromItem(Items.IRON_INGOT), cooling, consumed, false);
  }
}
