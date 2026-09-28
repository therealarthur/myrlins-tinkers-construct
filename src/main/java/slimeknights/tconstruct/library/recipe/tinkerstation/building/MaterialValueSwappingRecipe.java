package slimeknights.tconstruct.library.recipe.tinkerstation.building;

import lombok.Getter;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.recipe.ingredient.SizedIngredient;
import slimeknights.tconstruct.library.json.predicate.material.MaterialPredicate;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipe;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.tinkerstation.IMutableTinkerStationContainer;
import slimeknights.tconstruct.tables.TinkerTables;

import java.util.List;

/** Recipe for swapping materials on a tool based on material items. For best results, there needs to not be a repairable part that supports the same materials. */
public class MaterialValueSwappingRecipe extends MaterialIndexSwappingRecipe {
  public static final RecordLoadable<MaterialValueSwappingRecipe> LOADER = RecordLoadable.create(
    ContextKey.ID.requiredField(), TOOLS_FIELD, STACK_SIZE_FIELD,
    MaterialPredicate.LOADER.requiredField("material", r -> r.material),
    IntLoadable.FROM_ONE.requiredField("cost", r -> r.cost),
    INDICES_FIELD, EXTRA_REQUIREMENTS_FIELD,
    MaterialValueSwappingRecipe::new);

  /** Predicate matching materials to allow */
  @Getter
  private final IJsonPredicate<MaterialVariantId> material;
  /** Amount of material needed to swap the part */
  @Getter
  private final int cost;

  protected MaterialValueSwappingRecipe(Identifier id, Ingredient tools, int maxStackSize, IJsonPredicate<MaterialVariantId> material, int cost, int[] indices, List<SizedIngredient> extraRequirements) {
    super(id, tools, maxStackSize, indices, extraRequirements);
    this.material = material;
    this.cost = cost;
  }

  @Override
  protected boolean isMaterial(ItemStack stack) {
    MaterialRecipe recipe = MaterialRecipeCache.findRecipe(stack);
    return recipe != MaterialRecipe.EMPTY && this.material.matches(recipe.getMaterial().getVariant()) && stack.getCount() >= recipe.getItemsUsed(cost);
  }

  @Override
  protected MaterialVariantId getMaterial(ItemStack stack) {
    return MaterialRecipeCache.findRecipe(stack).getMaterial().getVariant();
  }

  @Override
  protected int getRepairValue() {
    return cost;
  }

  @Override
  public RecipeSerializer<? extends Recipe<ITinkerStationContainer>> getSerializer() {
    return TinkerTables.materialValueSwapping.get();
  }

  @Override
  protected boolean shrinkPart(IMutableTinkerStationContainer inv, int index, ItemStack stack) {
    MaterialRecipe recipe = MaterialRecipeCache.findRecipe(stack);
    // need to ensure the recipe is a valid material, for the sake of extra requirements
    if (isMaterial(stack)) {
      int used = recipe.getItemsUsed(cost);
      inv.shrinkInput(index, used);
      ItemStack leftover = recipe.getLeftover(cost);
      if (!leftover.isEmpty()) {
        inv.giveItem(leftover);
      }
      // The base transaction stops after the selected material input is consumed.
      return true;
    }
    return false;
  }


}
