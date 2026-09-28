package aebm.continuumtests;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import slimeknights.mantle.recipe.helper.IngredientHelper;
import slimeknights.tconstruct.common.recipe.TinkerRecipeCacheRebuilder;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.*;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayMapper;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.material.IMaterialValue;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipe;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.partbuilder.IPartBuilderContainer;
import slimeknights.tconstruct.library.recipe.partbuilder.PartRecipe;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;
import slimeknights.tconstruct.tables.recipe.PartBuilderToolRecycle;

/** Actual loaded recipe objects exercise the neutral mapping with neither optional viewer present. */
@EventBusSubscriber(modid = "aebmcontinuumtests")
public final class RecipeMapperServerFixture {
  private RecipeMapperServerFixture() {}

  @SubscribeEvent
  public static void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("aebmrecipemappertest")
      .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
      .executes(context -> new Suite(context.getSource()).run()));
  }

  private static final class Suite {
    private final CommandSourceStack source;
    private final ServerLevel level;
    private final RecipeMap recipes;
    private int passed;
    private int failed;

    Suite(CommandSourceStack source) {
      this.source = source;
      level = source.getLevel();
      recipes = source.getServer().getRecipeManager().recipeMap();
    }

    int run() {
      Map<String,List<RecipeType<?>>> categories = Map.of(
        "molding", List.of(TinkerRecipeTypes.MOLDING_TABLE.get(), TinkerRecipeTypes.MOLDING_BASIN.get()),
        "entity_melting", List.of(TinkerRecipeTypes.ENTITY_MELTING.get()),
        "modifiers", List.of(TinkerRecipeTypes.TINKER_STATION.get()),
        "severing", List.of(TinkerRecipeTypes.SEVERING.get()),
        "tool_recipes", List.of(TinkerRecipeTypes.TINKER_STATION.get()),
        "tool_modification", List.of(TinkerRecipeTypes.TINKER_STATION.get()),
        "materials", List.of(TinkerRecipeTypes.MATERIAL.get(), TinkerRecipeTypes.DATA.get()),
        "part_builder", List.of(TinkerRecipeTypes.PART_BUILDER.get()),
        "worktable", List.of(TinkerRecipeTypes.MODIFIER_WORKTABLE.get()));
      categories.forEach((category, types) -> test("loaded_category_" + category, () -> {
        for (RecipeType<?> type : types) {
          for (RecipeHolder<?> holder : holders(type)) {
            List<RecipeDisplayData> displays = map(holder).stream().filter(display -> display.category().getPath().equals(category)).toList();
            if (!displays.isEmpty()) {
              displays.forEach(this::validateValues);
              return;
            }
          }
        }
        throw new AssertionError("No real mapped recipe for " + category);
      }));
      test("material_recipe_actual_needed_and_value", this::materialQuantities);
      test("part_builder_actual_cost_result_and_change", this::partQuantities);
      test("same_recipe_object_rebuilds_after_material_recipe_change", this::sameObjectReload);
      test("tool_recycling_real_pattern_assembly_and_display", this::toolRecycling);
      test("default_entity_melting_uses_real_entities_and_fluid", () -> {
        var known = recipes.byType(TinkerRecipeTypes.ENTITY_MELTING.get()).stream().map(RecipeHolder::value).toList();
        var defaults = RecipeDisplayMapper.defaultEntityMelting(known);
        require(!defaults.isEmpty(), "unmatched living entities need default melting");
        for (var display : defaults) {
          validateValues(display);
          for (var value : display.inputs().getFirst()) {
            var entity = ((EntityValue)value).entity();
            require(known.stream().noneMatch(recipe -> recipe.matches(entity)), "default duplicates an explicit entity melting recipe");
          }
          require(display.outputs().getFirst().getFirst() instanceof FluidValue, "default output must be actual fluid");
        }
      });
      source.sendSuccess(() -> Component.literal("AEBM_RECIPE_MAPPER_SUMMARY passed=" + passed + " failed=" + failed), false);
      return failed == 0 ? 1 : 0;
    }

    private void materialQuantities() {
      for (var holder : recipes.byType(TinkerRecipeTypes.MATERIAL.get())) {
        MaterialRecipe recipe = holder.value();
        if (recipe.getNeeded() <= 1) continue;
        for (var display : map(holder)) {
          for (var value : display.inputs().getFirst()) {
            ItemStack stack = ((ItemValue)value).stack();
            require(stack.getCount() == recipe.getNeeded(), "material input count differs from recipe needed");
            require(IngredientHelper.test(recipe.getIngredient(), stack), "material display is not accepted by the original ingredient");
          }
          var output = (MaterialValue)display.outputs().getFirst().getFirst();
          require(output.amount() == recipe.getValue(), "typed material output has wrong quantity");
          require(output.material().equals(recipe.getMaterial().getVariant()), "typed material output has wrong variant");
          return;
        }
      }
      throw new AssertionError("Needs one loaded material recipe with needed > 1");
    }

    private void partQuantities() {
      boolean counted = false;
      boolean change = false;
      for (var holder : recipes.byType(TinkerRecipeTypes.PART_BUILDER.get())) {
        if (!(holder.value() instanceof PartRecipe recipe) || recipe.getCost() < 2) continue;
        for (var display : map(holder)) {
          ItemStack material = ((ItemValue)display.inputs().getFirst().getFirst()).stack();
          IMaterialValue value = MaterialRecipeCache.getAllRecipes().stream()
            .filter(candidate -> IngredientHelper.test(candidate.getIngredient(), material)).findFirst().orElse(null);
          if (value == null) continue;
          Pattern pattern = display.catalysts().stream().flatMap(List::stream).filter(PatternValue.class::isInstance)
            .map(PatternValue.class::cast).map(PatternValue::pattern).findFirst().orElseThrow();
          // Pattern item is either consumed (last input) or reusable (last catalyst).
          ItemStack patternItem = java.util.stream.Stream.concat(display.inputs().stream().skip(1), display.catalysts().stream())
            .flatMap(List::stream).filter(ItemValue.class::isInstance).map(ItemValue.class::cast).map(ItemValue::stack).findFirst().orElseThrow();
          var input = new PartInput(material, patternItem, value);
          require(recipe.partialMatch(input) && recipe.matches(input, level), "mapped part input fails actual recipe");
          require(material.getCount() == recipe.getItemsUsed(input), "part material count differs from actual consumption");
          ItemStack expected = recipe.assemble(input, pattern);
          ItemStack shown = ((ItemValue)display.outputs().getFirst().getFirst()).stack();
          require(ItemStack.isSameItemSameComponents(expected, shown) && expected.getCount() == shown.getCount(), "part result differs from actual assembly");
          ItemStack leftover = recipe.getLeftover(input, pattern);
          if (!leftover.isEmpty()) {
            require(display.outputs().stream().skip(1).flatMap(List::stream).filter(ItemValue.class::isInstance).map(ItemValue.class::cast)
              .map(ItemValue::stack).anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, leftover) && stack.getCount() == leftover.getCount()),
              "actual material change is absent or has wrong count");
            change = true;
          }
          counted |= material.getCount() > 1;
          if (counted && change) return;
        }
      }
      require(counted && change, "Needs multi-item consumption and a real leftover to exercise quantity mapping");
    }

    private void sameObjectReload() {
      var recipe = recipes.byType(TinkerRecipeTypes.PART_BUILDER.get()).stream().map(RecipeHolder::value)
        .filter(PartRecipe.class::isInstance).map(PartRecipe.class::cast)
        .filter(part -> !part.getRecipes(level.registryAccess()).isEmpty()).findFirst().orElseThrow();
      var before = recipe.getRecipes(level.registryAccess());
      try {
        MaterialRecipeCache.rebuildRecipes(List.of());
        MaterialRecipeCache.setDisplayRegistryAccess(level.registryAccess());
        require(recipe.getRecipes(level.registryAccess()).isEmpty(), "same PartRecipe object retained obsolete material alternatives");
      } finally {
        TinkerRecipeCacheRebuilder.rebuild(level.registryAccess(), recipes);
      }
      require(!recipe.getRecipes(level.registryAccess()).isEmpty(), "reloaded recipe failed to recover material alternatives");
      require(recipe.getRecipes(level.registryAccess()) != before, "old display array survived the recipe reload");
    }

    private void toolRecycling() {
      for (var holder : recipes.byType(TinkerRecipeTypes.PART_BUILDER.get())) {
        if (!(holder.value() instanceof PartBuilderToolRecycle recipe)) continue;
        for (var display : map(holder)) {
          ItemStack tool = ((ItemValue)display.inputs().getFirst().getFirst()).stack();
          Pattern pattern = display.catalysts().stream().flatMap(List::stream).filter(PatternValue.class::isInstance)
            .map(PatternValue.class::cast).map(PatternValue::pattern).findFirst().orElseThrow();
          ItemStack patternItem = java.util.stream.Stream.concat(display.inputs().stream().skip(1), display.catalysts().stream())
            .flatMap(List::stream).filter(ItemValue.class::isInstance).map(ItemValue.class::cast).map(ItemValue::stack).findFirst().orElseThrow();
          var input = new PartInput(tool, patternItem, null);
          require(recipe.partialMatch(input) && recipe.matches(input, level), "tool recycling example must match actual recipe");
          ItemStack before = tool.copy();
          ItemStack actual = recipe.assemble(input, new Pattern(pattern.getId()));
          require(!actual.isEmpty(), "Pattern wrapper must match the selected part's registry identifier");
          require(pattern.getId().equals(BuiltInRegistries.ITEM.getKey(actual.getItem())), "recycled primary part differs from selection");
          ItemStack shown = ((ItemValue)display.outputs().getFirst().getFirst()).stack();
          require(ItemStack.isSameItemSameComponents(actual, shown) && actual.getCount() == shown.getCount(), "shown recycled material differs from runtime");
          require(ItemStack.isSameItemSameComponents(before, tool), "display assembly changed its input tool");
          require(!recipe.getDisplayLeftovers(input, new Pattern(pattern.getId())).isEmpty(), "representative multipart tool needs extra-part alternatives");
          return;
        }
      }
      throw new AssertionError("No valid tool-recycling display survived real assembly");
    }

    private List<RecipeDisplayData> map(RecipeHolder<?> holder) {
      return RecipeDisplayMapper.map(holder, level.registryAccess(), level);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Collection<RecipeHolder<?>> holders(RecipeType<?> type) {
      return (Collection)recipes.byType((RecipeType)type);
    }

    private void validateValues(RecipeDisplayData display) {
      for (var group : List.of(display.inputs(), display.outputs(), display.catalysts())) {
        for (var alternatives : group) {
          require(!alternatives.isEmpty(), "required slot lost every accepted alternative");
          for (var value : alternatives) {
            if (value instanceof ItemValue item) require(!item.stack().isEmpty(), "empty item placeholder");
            if (value instanceof FluidValue fluid) require(!fluid.stack().isEmpty(), "empty fluid placeholder");
            if (value instanceof MaterialValue material) require(material.amount() > 0, "invalid material quantity");
          }
        }
      }
    }

    private void test(String name, Runnable test) {
      try {
        test.run(); passed++;
        source.sendSuccess(() -> Component.literal("AEBM_RECIPE_MAPPER_PASS " + name), false);
      } catch (Exception | AssertionError failure) {
        failed++;
        source.sendFailure(Component.literal("AEBM_RECIPE_MAPPER_FAIL " + name + " " + failure));
      }
    }
  }

  private record PartInput(ItemStack stack, ItemStack pattern, IMaterialValue material) implements IPartBuilderContainer {
    @Override public ItemStack getStack() { return stack; }
    @Override public ItemStack getPatternStack() { return pattern; }
    @Override public IMaterialValue getMaterial() { return material; }
  }

  private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
