package slimeknights.tconstruct.library.recipe.casting;

import lombok.Getter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.recipe.helper.LoadableRecipeSerializer;
import slimeknights.mantle.recipe.helper.TypeAwareRecipeSerializer;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;

import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import java.util.Arrays;
import java.util.List;

/** Casting recipe that takes a fluid and optional cast and outputs an item. */
@Getter
public class ItemCastingRecipe extends AbstractCastingRecipe implements IDisplayableCastingRecipe {
  /* Shared fields */
  protected static final LoadableField<FluidIngredient,ItemCastingRecipe> FLUID_FIELD = FluidIngredient.LOADABLE.requiredField("fluid", ItemCastingRecipe::getFluid);
  protected static final LoadableField<ItemOutput,ItemCastingRecipe> RESULT_FIELD = ItemOutput.Loadable.REQUIRED_ITEM.requiredField("result", r -> r.result);
  protected static final LoadableField<Integer,ItemCastingRecipe> COOLING_TIME_FIELD = IntLoadable.FROM_ONE.requiredField("cooling_time", ItemCastingRecipe::getCoolingTime);
  /** Loader instance */
  public static final RecordLoadable<ItemCastingRecipe> LOADER = RecordLoadable.create(
    LoadableRecipeSerializer.TYPED_SERIALIZER.requiredField(), ContextKey.ID.requiredField(),
    LoadableRecipeSerializer.RECIPE_GROUP, CAST_FIELD, FLUID_FIELD, RESULT_FIELD, COOLING_TIME_FIELD, CAST_CONSUMED_FIELD, SWITCH_SLOTS_FIELD,
    ItemCastingRecipe::new);

  private final TypeAwareRecipeSerializer<?> serializer;
  protected final FluidIngredient fluid;
  protected final ItemOutput result;
  protected final int coolingTime;
  public ItemCastingRecipe(TypeAwareRecipeSerializer<?> serializer, Identifier id, String group, Ingredient cast, FluidIngredient fluid, ItemOutput result, int coolingTime, boolean consumed, boolean switchSlots) {
    super(serializer.getType(), id, group, cast, consumed, switchSlots);
    this.serializer = serializer;
    this.fluid = fluid;
    this.result = result;
    this.coolingTime = coolingTime;
    CastingRecipeLookup.registerCastable(result);
  }

  @Override
  @SuppressWarnings("unchecked")
  public RecipeSerializer<? extends Recipe<ICastingContainer>> getSerializer() {
    return (RecipeSerializer<? extends Recipe<ICastingContainer>>)serializer.getSerializer();
  }

  @Override
  public int getFluidAmount(ICastingContainer inv) {
    return this.fluid.getAmount(inv.getFluid());
  }

  @Override
  public boolean matches(ICastingContainer inv, Level worldIn) {
    return matchesCast(inv.getStack()) && fluid.test(inv.getFluid());
  }

  public ItemStack getResultItem(RegistryAccess access) {
    return this.result.copy();
  }

  @Override
  public ItemStack assemble(ICastingContainer inv) {
    return this.result.copy();
  }

  @Override
  public int getCoolingTime(ICastingContainer inv) {
    return this.coolingTime;
  }


  /* JEI */

  @Override
  public Identifier getRecipeId() {
    // need a separate method as remapping makes the names mismatch
    return getId();
  }

  @Override
  public boolean hasCast() {
    // An unresolved required tag is still a requirement, not a recipe with an empty cast slot.
    return !matchesCast(ItemStack.EMPTY);
  }

  @Override
  public List<ItemStack> getCastItems() {
    return MaterialRecipeCache.getDisplayItems(getCast());
  }
  @Override
  public List<ItemStack> getOutputs() {
    return MaterialRecipeCache.getDisplayItems(this.result);
  }

  @Override
  public ItemStack getOutput() {
    return this.result.copy();
  }

  /**
   * Gets a list of valid fluid inputs for this recipe, for display in JEI
   * @return  List of fluids
   */
  @Override
  public List<FluidStack> getFluids() {
    return this.fluid.getFluids();
  }
}
