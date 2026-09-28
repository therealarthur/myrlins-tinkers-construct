package slimeknights.tconstruct.library.recipe.tinkerstation.building;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.recipe.data.AbstractRecipeBuilder;
import slimeknights.mantle.recipe.ingredient.SizedIngredient;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.IToolPart;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/** Builder for {@link FixedMaterialSwappingRecipe} and {@link PartSwappingOverrideRecipe}. */
@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "tools")
public class MaterialSwappingRecipeBuilder extends AbstractRecipeBuilder<MaterialSwappingRecipeBuilder> {
  /** Tools that support this recipe */
  private final Ingredient tools;
  @Setter
  private int maxStackSize = 16;
  /** List of indices swapped by this recipe */
  private final BitSet indices = new BitSet();
  /** Additional requirements beyond the "part" */
  private final List<SizedIngredient> extraRequirements = new ArrayList<>();

  /** Part to swap, used by part override */
  @Setter
  private IToolPart part = null;

  /** Ingredient for the input part, used by fixed */
  private SizedIngredient ingredient = SizedIngredient.EMPTY;
  /** Material to swap to, used by fixed */
  private MaterialVariantId material = IMaterial.UNKNOWN_ID;
  private IJsonPredicate<MaterialVariantId> materialPredicate;
  private int materialCost;
  /** Repair value on swapping, used by fixed */
  @Setter
  private int repairValue = 0;

  /** Creates a builder for the given tool */
  public static MaterialSwappingRecipeBuilder tool(ItemLike tool) {
    return tools(Ingredient.of(tool));
  }

  /** Creates a builder for the given tool */
  public static MaterialSwappingRecipeBuilder tools(TagKey<Item> tag) {
    return tools(slimeknights.tconstruct.library.recipe.ingredient.LegacyIngredientType.ofTag(tag));
  }

  /** Adds the given index to the recipe */
  public MaterialSwappingRecipeBuilder index(int index) {
    indices.set(index);
    return this;
  }

  /** Sets the material for this builder */
  public MaterialSwappingRecipeBuilder material(MaterialVariantId material, SizedIngredient ingredient) {
    this.material = material;
    this.ingredient = ingredient;
    return this;
  }

  /** Sets the material for this builder */
  public MaterialSwappingRecipeBuilder material(MaterialVariantId material, ItemLike item) {
    return material(material, SizedIngredient.fromItems(item));
  }

  /** Swaps using the material value supplied by ordinary material recipes. */
  public MaterialSwappingRecipeBuilder material(IJsonPredicate<MaterialVariantId> predicate, int cost) {
    if (cost < 1) throw new IllegalArgumentException("Material cost must be positive");
    this.materialPredicate = predicate;
    this.materialCost = cost;
    return this;
  }

  /** Adds an extra ingredient requirement */
  public MaterialSwappingRecipeBuilder addExtraRequirement(SizedIngredient ingredient) {
    extraRequirements.add(ingredient);
    return this;
  }

  /** Adds an extra ingredient requirement */
  public MaterialSwappingRecipeBuilder addExtraRequirement(Ingredient ingredient) {
    return addExtraRequirement(SizedIngredient.of(ingredient));
  }

  /** Adds an extra ingredient requirement */
  public MaterialSwappingRecipeBuilder addExtraRequirement(ItemLike... items) {
    return addExtraRequirement(SizedIngredient.fromItems(items));
  }

  @Override
  public void save(RecipeOutput consumer) {
    save(consumer, Loadables.ITEM.getKey(tools.items().map(ItemStack::new).findFirst().orElseThrow().getItem()));
  }

  @Override
  public void save(RecipeOutput consumer, Identifier id) {
    int[] indices = this.indices.stream().toArray();
    if (indices.length == 0) {
      throw new IllegalStateException("Must set index");
    }
    if (materialPredicate != null) {
      if (part != null || ingredient != SizedIngredient.EMPTY) {
        throw new IllegalStateException("Cannot combine material value with a fixed part or ingredient");
      }
      consumer.accept(recipeKey(id), new MaterialValueSwappingRecipe(id, tools, maxStackSize, materialPredicate, materialCost, indices, extraRequirements), null);
    } else if (part != null) {
      if (ingredient != SizedIngredient.EMPTY) {
        throw new IllegalStateException("Cannot set both part and ingredient");
      }
      consumer.accept(recipeKey(id), new PartSwappingOverrideRecipe(id, tools, maxStackSize, part, indices, extraRequirements), null);
    } else {
      consumer.accept(recipeKey(id), new FixedMaterialSwappingRecipe(id, tools, maxStackSize, ingredient, material, indices, repairValue, extraRequirements), null);
    }
  }
}
