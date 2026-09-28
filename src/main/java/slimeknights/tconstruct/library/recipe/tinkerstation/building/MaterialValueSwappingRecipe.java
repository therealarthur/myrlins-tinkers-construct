package slimeknights.tconstruct.library.recipe.tinkerstation.building;

import lombok.Getter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
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
import slimeknights.mantle.recipe.IMultiRecipe;
import slimeknights.tconstruct.library.json.predicate.material.MaterialPredicate;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipe;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.tinkerstation.IMutableTinkerStationContainer;
import slimeknights.tconstruct.library.recipe.tinkerstation.IDisplayToolTinkering;
import slimeknights.tconstruct.library.tools.definition.module.material.ToolMaterialHook;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.MaterialIdNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tables.TinkerTables;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.IntStream;

/** Recipe for swapping materials on a tool based on material items. For best results, there needs to not be a repairable part that supports the same materials. */
public class MaterialValueSwappingRecipe extends MaterialIndexSwappingRecipe implements IMultiRecipe<IDisplayToolTinkering> {
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
  /* Viewer-neutral display */

  private record Alternative(MaterialVariant material, ItemStack input, ItemStack remainder) {}

  @Override
  public List<IDisplayToolTinkering> getRecipes(RegistryAccess access) {
    if (!hasDisplayInputs()) return List.of();
    List<Alternative> alternatives = new ArrayList<>();
    for (MaterialRecipe recipe : MaterialRecipeCache.getAllRecipes()) {
      MaterialVariant variant = recipe.getMaterial();
      if (!material.matches(variant.getVariant())) continue;
      int used = recipe.getItemsUsed(cost);
      for (ItemStack stack : recipe.getDisplayItems()) {
        if (!stack.isEmpty() && used > 0 && used <= stack.getMaxStackSize()) {
          alternatives.add(new Alternative(variant, stack.copyWithCount(used), recipe.getLeftover(cost)));
        }
      }
    }
    List<IDisplayToolTinkering> displays = new ArrayList<>();
    for (ItemStack tool : MaterialRecipeCache.getDisplayItems(tools)) {
      List<MaterialStatsId> stats = ToolMaterialHook.stats(IModifiable.getToolDefinition(tool.getItem()));
      for (int index : Arrays.stream(indices).filter(VALID_SLOT).filter(i -> i < stats.size()).distinct().toArray()) {
        List<Alternative> valid = alternatives.stream().filter(entry -> stats.get(index).canUseMaterial(entry.material.getId())).toList();
        if (!valid.isEmpty()) displays.add(new ValueDisplayRecipe(index, tool, valid));
      }
    }
    return List.copyOf(displays);
  }

  /** One filtered list controls input quantities, outputs and refunds, including focus filtering. */
  private class ValueDisplayRecipe extends LinkedDisplayRecipe {
    private final List<Alternative> alternatives;

    ValueDisplayRecipe(int index, ItemStack tool, List<Alternative> alternatives) {
      super(index, alternatives.stream().map(Alternative::input).toList(),
        List.of(withMaterial(tool.copy(), index, MaterialVariant.of(ToolBuildHandler.getRenderMaterial(index)))),
        alternatives.stream().map(entry -> withMaterial(tool.copy(), index, entry.material)).toList(),
        alternatives.stream().map(Alternative::material).toList());
      this.alternatives = alternatives;
    }

    @Override
    public Component getTitle() {
      return MATERIAL_TITLE;
    }

    @Override
    public Component getTooltip() {
      return MATERIAL_TOOLTIP;
    }

    private IntStream selected(ItemStack focus, boolean output) {
      IntStream indices = IntStream.range(0, alternatives.size());
      if (focus.isEmpty() || !isTool(focus)) return indices;
      MaterialVariantId current = MaterialIdNBT.from(focus).getMaterial(index);
      if (output) return indices.filter(i -> current.matchesVariant(materials.get(i)));
      // This display describes material changes. Ordinary repair remains in its own category.
      return indices.filter(i -> !materials.get(i).sameVariant(current));
    }

    @Override
    public List<ItemStack> getDisplayItems(int slot, ItemStack focus, boolean output) {
      return slot == index ? selected(focus, output).mapToObj(input::get).toList() : getDisplayItems(slot);
    }

    @Override
    public List<ItemStack> getDisplayRemainders(int slot) {
      return slot == index ? alternatives.stream().map(Alternative::remainder).toList() : List.of();
    }

    @Override
    public List<ItemStack> getDisplayRemainders(int slot, ItemStack focus, boolean output) {
      return slot == index ? selected(focus, output).mapToObj(i -> alternatives.get(i).remainder).toList() : List.of();
    }

    @Override
    public List<ItemStack> getToolWithModifier(ItemStack focus, boolean output) {
      if (focus.isEmpty() || !isTool(focus)) return toolWithModifier;
      if (output) {
        MaterialIdNBT original = MaterialIdNBT.from(focus);
        return selected(focus, true).mapToObj(i -> replaceMaterial(original, materials.get(i).getVariant(), focus)).toList();
      }
      ToolStack tool = ToolStack.copyFrom(focus);
      return selected(focus, false).mapToObj(i -> replaceMaterial(tool.copy(), materials.get(i), focus)).toList();
    }
  }
}
