package slimeknights.tconstruct.library.recipe.casting;

import lombok.Getter;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.common.IngredientLoadable;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.IMultiRecipe;
import slimeknights.mantle.recipe.helper.LoadableRecipeSerializer;
import slimeknights.mantle.recipe.helper.TypeAwareRecipeSerializer;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;

import javax.annotation.Nullable;
import slimeknights.tconstruct.fluids.fluids.PotionFluidType;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import java.util.List;

/**
 * Recipe for casting a fluid onto an item, copying the fluid potion components to the item.
 */
public class PotionCastingRecipe implements ICastingRecipe, IMultiRecipe<DisplayCastingRecipe> {
  protected static final LoadableField<FluidIngredient, PotionCastingRecipe> FLUID_FIELD = FluidIngredient.LOADABLE.requiredField("fluid", r -> r.fluid);
  protected static final LoadableField<Integer, PotionCastingRecipe> COOLING_TIME_FIELD = IntLoadable.FROM_ONE.defaultField("cooling_time", 5, r -> r.coolingTime);
  public static final RecordLoadable<PotionCastingRecipe> LOADER = RecordLoadable.create(
    LoadableRecipeSerializer.TYPED_SERIALIZER.requiredField(), ContextKey.ID.requiredField(), LoadableRecipeSerializer.RECIPE_GROUP,
    IngredientLoadable.DISALLOW_EMPTY.requiredField("bottle", r -> r.bottle),
    FLUID_FIELD,
    Loadables.ITEM.requiredField("result", r -> r.result),
    COOLING_TIME_FIELD,
    PotionCastingRecipe::new);

  protected final TypeAwareRecipeSerializer<?> serializer;
  @Getter
  protected final Identifier id;
  @Getter
  protected final String group;
  /** Input on the casting table, always consumed */
  protected final Ingredient bottle;
  /** Potion ingredient, typically just the potion tag */
  protected final FluidIngredient fluid;
  /** Potion item result, will be given the proper potion component */
  protected final Item result;
  /** Cooling time for this recipe, used for tipped arrows */
  protected final int coolingTime;

  public PotionCastingRecipe(TypeAwareRecipeSerializer<?> serializer, Identifier id, String group, Ingredient bottle, FluidIngredient fluid, Item result, int coolingTime) {
    this.serializer = serializer;
    this.id = id;
    this.group = group;
    this.bottle = bottle;
    this.fluid = fluid;
    this.result = result;
    this.coolingTime = coolingTime;
    CastingRecipeLookup.registerCastable(result);
  }

  @Override
  @SuppressWarnings("unchecked")
  public RecipeType<? extends Recipe<ICastingContainer>> getType() {
    return (RecipeType<? extends Recipe<ICastingContainer>>)serializer.getType();
  }

  @Override
  @SuppressWarnings("unchecked")
  public RecipeSerializer<? extends Recipe<ICastingContainer>> getSerializer() {
    return (RecipeSerializer<? extends Recipe<ICastingContainer>>)serializer.getSerializer();
  }

  @Override
  public boolean matches(ICastingContainer inv, Level level) {
    return slimeknights.mantle.recipe.helper.IngredientHelper.test(bottle, inv.getStack()) && fluid.test(inv.getFluid());
  }

  @Override
  public int getFluidAmount(ICastingContainer inv) {
    return fluid.getAmount(inv.getFluid());
  }

  @Override
  public boolean isConsumed() {
    return true;
  }

  @Override
  public boolean switchSlots() {
    return false;
  }

  @Override
  public int getCoolingTime(ICastingContainer inv) {
    return coolingTime;
  }

  public ItemStack assemble(ICastingContainer inv) {
    ItemStack result = new ItemStack(this.result);
    PotionContents contents = getPotionContents(inv);
    if (contents != PotionContents.EMPTY) {
      result.set(DataComponents.POTION_CONTENTS, contents);
    }
    return result;
  }

  /** Reads current component storage, retaining support for older casting containers. */
  protected static PotionContents getPotionContents(ICastingContainer inv) {
    PotionContents contents = PotionFluidType.getPotionContents(inv.getFluidStack());
    if (contents == PotionContents.EMPTY) {
      contents = getPotionContents(inv.getFluidTag());
    }
    return contents;
  }

  protected static PotionContents potionContents(Potion potion) {
    return new PotionContents(BuiltInRegistries.POTION.wrapAsHolder(potion));
  }

  protected static PotionContents getPotionContents(@Nullable CompoundTag tag) {
    if (tag == null) {
      return PotionContents.EMPTY;
    }
    String potion = tag.getString("Potion").orElse("");
    return potion.isEmpty() ? PotionContents.EMPTY : PotionFluidType.fromPotionId(potion);
  }

  protected static String getPotionId(PotionContents contents) {
    return contents.potion().flatMap(Holder::unwrapKey).map(key -> key.identifier().toString()).orElse("");
  }

  protected static FluidStack withPotion(FluidStack fluid, PotionContents contents) {
    FluidStack stack = fluid.copyWithAmount(fluid.getAmount());
    stack.set(DataComponents.POTION_CONTENTS, contents);
    return stack;
  }


  /* JEI */
  protected List<DisplayCastingRecipe> displayRecipes = null;
  private long displayRevision = -1;

  /** Materials and recipes can change without replacing this recipe object. */
  protected void refreshDisplayCache() {
    long revision = slimeknights.tconstruct.library.client.recipe.ClientRecipeCache.getSnapshot().revision();
    if (displayRevision != revision) {
      displayRevision = revision;
      displayRecipes = null;
    }
  }

  @Override
  public List<DisplayCastingRecipe> getRecipes(RegistryAccess access) {
    refreshDisplayCache();
    if (displayRecipes == null) {
      // create a subrecipe for every potion variant
      List<ItemStack> bottles = MaterialRecipeCache.getDisplayItems(bottle);
      displayRecipes = BuiltInRegistries.POTION.stream()
        .filter(potion -> potion != Potions.WATER.value())
        .map(potion -> {
          PotionContents contents = potionContents(potion);
          ItemStack result = new ItemStack(this.result);
          result.set(DataComponents.POTION_CONTENTS, contents);
          return new DisplayCastingRecipe(getId(), getType(), bottles, fluid.getFluids().stream()
                                                              .map(fluid -> withPotion(fluid, contents))
                                                              .toList(),
                                          result, coolingTime, true);
        }).toList();
    }
    return displayRecipes;
  }


  /* Recipe interface methods */

  public NonNullList<Ingredient> getIngredients() {
    return NonNullList.of(AbstractCastingRecipe.EMPTY_INGREDIENT, bottle);
  }

  /** @deprecated use {@link #assemble(Container, RegistryAccess)} */
  @Deprecated
  public ItemStack getResultItem(RegistryAccess access) {
    return new ItemStack(this.result);
  }
}
