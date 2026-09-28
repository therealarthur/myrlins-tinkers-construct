package aebm.continuumtests;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.recipe.IMultiRecipe;
import slimeknights.mantle.recipe.helper.IngredientHelper;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.recipe.helper.TypeAwareRecipeSerializer;
import slimeknights.mantle.recipe.ingredient.EmptyIngredient;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.common.recipe.TinkerRecipeCacheRebuilder;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.casting.ICastingContainer;
import slimeknights.tconstruct.library.recipe.casting.IDisplayableCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.AbstractMaterialCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.CompositeCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingLookup;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialFluidRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.ToolCastingRecipe;
import slimeknights.tconstruct.library.recipe.ingredient.MaterialIngredient;
import slimeknights.tconstruct.library.recipe.ingredient.LegacyIngredientType;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.melting.IMeltingContainer;
import slimeknights.tconstruct.library.recipe.melting.MaterialMeltingRecipe;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

/** Checks actual loaded recipe displays without loading either viewer or changing world state. */
@EventBusSubscriber(modid = "aebmcontinuumtests")
public final class RecipeViewerServerFixture {
  private RecipeViewerServerFixture() {}

  @SubscribeEvent
  public static void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("aebmrecipeviewertest")
      .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
      .executes(context -> new Suite(context.getSource()).run()));
  }

  private static final class Suite {
    private final CommandSourceStack source;
    private final ServerLevel level;
    private final RecipeMap recipes;
    private int passed;
    private int failed;

    private Suite(CommandSourceStack source) {
      this.source = source;
      this.level = source.getLevel();
      this.recipes = source.getServer().getRecipeManager().recipeMap();
    }

    private int run() {
      test("material_ingredient_preserves_material_and_components", () -> {
        require(MaterialRegistry.isFullyLoaded(), "fixture needs loaded material data");
        var part = TinkerToolParts.pickHead.get();
        Component name = Component.literal("Material ingredient probe");
        var nested = DataComponentIngredient.of(DataComponents.CUSTOM_NAME, name, part);
        var ingredient = MaterialIngredient.of(nested, MaterialIds.iron).toVanilla();
        var displayed = MaterialRecipeCache.getDisplayItems(ingredient);
        require(!displayed.isEmpty(), "iron pick head must have a display");
        for (ItemStack stack : displayed) {
          require(IMaterialItem.getMaterialFromStack(stack).equals(MaterialIds.iron), "requested material was lost");
          require(name.equals(stack.get(DataComponents.CUSTOM_NAME)), "nested component was lost");
          require(ingredient.test(stack), "material display must satisfy the real ingredient");
        }
        require(!ingredient.test(new ItemStack(part)), "plain part must fail the material/component ingredient");
        var anyMaterial = MaterialIngredient.of(part).toVanilla();
        var alternatives = MaterialRecipeCache.getDisplayItems(anyMaterial);
        require(!alternatives.isEmpty(), "ANY material ingredient must retain valid material alternatives");
        for (ItemStack stack : alternatives) {
          var material = IMaterialItem.getMaterialFromStack(stack);
          require(!material.equals(IMaterial.UNKNOWN_ID), "unsupported material must not become an unknown-material display");
          require(part.canUseMaterial(material.getMaterialId()), "displayed material must be usable by the part");
          require(anyMaterial.test(stack), "ANY material display must satisfy its actual ingredient");
        }
      });
      test("material_melting_display_matches_runtime", this::materialMelting);
      test("material_casting_amount_and_cooling", () -> casting(MaterialCastingRecipe.class));
      test("composite_casting_amount_cooling_and_fluid_filter", () -> casting(CompositeCastingRecipe.class));
      test("tool_casting_amount_and_cooling", () -> casting(ToolCastingRecipe.class));
      test("tool_casting_retains_material_casts", this::toolMaterialCasts);
      test("missing_required_cast_differs_from_no_cast", () -> {
        var id = Identifier.fromNamespaceAndPath("aebmcontinuumtests", "missing_cast");
        var missing = LegacyIngredientType.ofTag(TagKey.create(Registries.ITEM, id));
        TypeAwareRecipeSerializer<ItemCastingRecipe> serializer = new TypeAwareRecipeSerializer<>() {
          @Override public RecipeType<?> getType() { return TinkerRecipeTypes.CASTING_TABLE.get(); }
          @Override public RecipeSerializer<ItemCastingRecipe> getSerializer() { return TinkerSmeltery.tableRecipeSerializer.get(); }
        };
        var required = new ItemCastingRecipe(serializer, id, "", missing, FluidIngredient.EMPTY,
          ItemOutput.fromItem(Items.IRON_INGOT), 20, false, false);
        var absent = new ItemCastingRecipe(serializer, id, "", EmptyIngredient.VANILLA, FluidIngredient.EMPTY,
          ItemOutput.fromItem(Items.IRON_INGOT), 20, false, false);
        require(required.getCastItems().isEmpty(), "fixture tag must have no alternatives");
        require(required.hasCast(), "missing required tag must not advertise a free cast slot");
        require(!IngredientHelper.test(required.getCast(), ItemStack.EMPTY), "runtime must reject absent required cast");
        require(!absent.hasCast(), "intentionally absent cast must remain absent");
        require(IngredientHelper.test(absent.getCast(), ItemStack.EMPTY), "runtime must accept intentionally absent cast");
      });
      source.sendSuccess(() -> Component.literal("AEBM_VIEWER_SUMMARY passed=" + passed + " failed=" + failed), false);
      return failed == 0 ? 1 : 0;
    }

    private void materialMelting() {
      int checked = 0;
      for (var original : TinkerRecipeCacheRebuilder.getRecipes(recipes, TinkerRecipeTypes.MELTING.get(), MaterialMeltingRecipe.class)) {
        for (var display : original.getRecipes(level.registryAccess())) {
          var items = MaterialRecipeCache.getDisplayItems(display.getInput());
          require(!items.isEmpty(), original.getId() + " material melting input disappeared");
          for (ItemStack stack : items) {
            require(display.getInput().test(stack), "displayed material melting input fails its ingredient");
            IMeltingContainer input = new IMeltingContainer() {
              @Override public ItemStack getStack() { return stack; }
              @Override public IOreRate getOreRate() { return (type, amount) -> amount; }
            };
            require(original.matches(input, level), "displayed part fails original material melting recipe");
            FluidStack actual = original.getOutput(input);
            require(FluidStack.isSameFluidSameComponents(actual, display.getOutput())
              && actual.getAmount() == display.getOutput().getAmount(), "displayed melting output differs from runtime");
            require(original.getTime(input) == display.getTime(), "displayed melting time differs from runtime");
            checked++;
          }
        }
        if (checked > 0) break;
      }
      require(checked > 0, "fixture found no visible material melting recipe");
    }

    private void toolMaterialCasts() {
      int checked = 0;
      for (var original : TinkerRecipeCacheRebuilder.getRecipes(recipes, TinkerRecipeTypes.CASTING_TABLE.get(), ToolCastingRecipe.class)) {
        for (var display : original.getRecipes(level.registryAccess())) {
          for (ItemStack cast : display.getCastItems()) {
            if (cast.getItem() instanceof IMaterialItem) {
              require(!IMaterialItem.getMaterialFromStack(cast).equals(IMaterial.UNKNOWN_ID), "tool cast lost its material");
              require(IngredientHelper.test(original.getCast(), cast), "material-bearing tool cast fails real cast ingredient");
              checked++;
            }
          }
        }
      }
      require(checked > 0, "fixture needs at least one material-bearing tool cast");
    }

    @SuppressWarnings("unchecked")
    private void casting(Class<? extends AbstractMaterialCastingRecipe> family) {
      var originals = Stream.concat(
        TinkerRecipeCacheRebuilder.getRecipes(recipes, TinkerRecipeTypes.CASTING_TABLE.get(), family).stream(),
        TinkerRecipeCacheRebuilder.getRecipes(recipes, TinkerRecipeTypes.CASTING_BASIN.get(), family).stream())
        .filter(recipe -> recipe.getClass() == family).toList();
      int checked = 0;
      boolean multipleUnits = false;
      for (var original : originals) {
        var displays = ((IMultiRecipe<IDisplayableCastingRecipe>)original).getRecipes(level.registryAccess());
        for (var display : displays) {
          int maximumCooling = 0;
          List<ItemStack> casts = display.hasCast() ? display.getCastItems() : List.of(ItemStack.EMPTY);
          for (FluidStack fluid : display.getFluids()) {
            boolean accepted = false;
            for (ItemStack cast : casts) {
              var input = new CastingInput(cast, fluid);
              if (!original.matches(input, level)) continue;
              accepted = true;
              require(original.getFluidAmount(input) == fluid.getAmount(), original.getId() + " fluid amount differs from runtime");
              maximumCooling = Math.max(maximumCooling, original.getCoolingTime(input));
              MaterialFluidRecipe unit = family == CompositeCastingRecipe.class
                ? MaterialCastingLookup.getCompositeFluid(fluid.getFluid(), IMaterialItem.getMaterialFromStack(cast))
                : MaterialCastingLookup.getCastingFluid(fluid.getFluid());
              if (unit != MaterialFluidRecipe.EMPTY && fluid.getAmount() > unit.getFluidAmount(fluid.getFluid())) multipleUnits = true;
              checked++;
            }
            // Tool part-swap display markers intentionally use a render-only material.
            if (family != ToolCastingRecipe.class) require(accepted, original.getId() + " displays a rejected fluid/cast combination");
          }
          if (maximumCooling > 0) {
            require(display.getCoolingTime() == maximumCooling,
              original.getId() + " display cooling=" + display.getCoolingTime() + " runtime maximum=" + maximumCooling);
          }
        }
        if (multipleUnits) break;
      }
      require(checked > 0, "fixture found no accepted " + family.getSimpleName() + " displays");
      require(multipleUnits, "fixture needs an item cost above one to detect double-scaled cooling for " + family.getSimpleName());
    }

    private void test(String name, Runnable test) {
      try {
        test.run(); passed++;
        source.sendSuccess(() -> Component.literal("AEBM_VIEWER_PASS " + name), false);
      } catch (Exception | AssertionError failure) {
        failed++;
        source.sendFailure(Component.literal("AEBM_VIEWER_FAIL " + name + " " + failure));
      }
    }
  }

  private record CastingInput(ItemStack stack, FluidStack fluid) implements ICastingContainer {
    @Override public ItemStack getStack() { return stack; }
    @Override public Fluid getFluid() { return fluid.getFluid(); }
    @Override public FluidStack getFluidStack() { return fluid; }
  }

  private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
