package slimeknights.tconstruct.library.recipe.casting.container;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.IMultiRecipe;
import slimeknights.mantle.recipe.helper.LoadableRecipeSerializer;
import slimeknights.mantle.recipe.helper.TypeAwareRecipeSerializer;
import slimeknights.tconstruct.library.recipe.casting.DisplayCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.ICastingContainer;
import slimeknights.tconstruct.library.recipe.casting.ICastingRecipe;

import java.util.Collections;
import java.util.List;

/**
 * Casting recipe that takes an arbitrary fluid for a given amount and fills a container
 */
@RequiredArgsConstructor
public class ContainerFillingRecipe implements ICastingRecipe, IMultiRecipe<DisplayCastingRecipe> {
  public static final RecordLoadable<ContainerFillingRecipe> LOADER = RecordLoadable.create(
    LoadableRecipeSerializer.TYPED_SERIALIZER.requiredField(), ContextKey.ID.requiredField(), LoadableRecipeSerializer.RECIPE_GROUP,
    IntLoadable.FROM_ONE.requiredField("fluid_amount", r -> r.fluidAmount),
    Loadables.ITEM.requiredField("container", r -> r.container),
    ContainerFillingRecipe::new);

  private final TypeAwareRecipeSerializer<?> serializer;
  @Getter
  private final Identifier id;
  @Getter
  private final String group;
  private final int fluidAmount;
  private final Item container;

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
  public int getFluidAmount(ICastingContainer inv) {
    return getFillAmount(inv.getStack().copyWithCount(1), makeFluidStack(inv));
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
    return 5;
  }

  @Override
  public boolean matches(ICastingContainer inv, Level worldIn) {
    ItemStack stack = inv.getStack();
    return stack.getItem() == this.container.asItem() && getFillAmount(stack.copyWithCount(1), makeFluidStack(inv)) > 0;
  }

  /** @deprecated use {@link ICastingRecipe#assemble(Container)} */
  @Deprecated
  public ItemStack getResultItem(RegistryAccess access) {
    return new ItemStack(this.container);
  }

  @Override
  public ItemStack assemble(ICastingContainer inv) {
    return fillContainer(inv.getStack().copyWithCount(1), makeFluidStack(inv));
  }

  private FluidStack makeFluidStack(ICastingContainer inv) {
    FluidStack stack = new FluidStack(inv.getFluid(), this.fluidAmount);
    CompoundTag tag = inv.getFluidTag();
    if (tag != null && !tag.isEmpty()) {
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    return stack;
  }

  private static int getFillAmount(ItemStack stack, FluidStack fluid) {
    if (stack.isEmpty() || fluid.isEmpty()) {
      return 0;
    }
    ItemAccess access = ItemAccess.forStack(stack).oneByOne();
    ResourceHandler<FluidResource> handler = access.getCapability(Capabilities.Fluid.ITEM);
    if (handler == null) {
      return getBucketResult(stack, fluid).isEmpty() ? 0 : FluidType.BUCKET_VOLUME;
    }
    TransactionContext current = Transaction.getCurrentOpenedTransaction();
    try (Transaction transaction = Transaction.open(current)) {
      int filled = handler.insert(FluidResource.of(fluid), fluid.getAmount(), transaction);
      if (filled > 0) {
        return filled;
      }
    }
    return getBucketResult(stack, fluid).isEmpty() ? 0 : FluidType.BUCKET_VOLUME;
  }

  private static ItemStack fillContainer(ItemStack stack, FluidStack fluid) {
    if (stack.isEmpty() || fluid.isEmpty()) {
      return ItemStack.EMPTY;
    }
    ItemAccess access = ItemAccess.forStack(stack).oneByOne();
    ResourceHandler<FluidResource> handler = access.getCapability(Capabilities.Fluid.ITEM);
    if (handler == null) {
      return getBucketResult(stack, fluid);
    }
    TransactionContext current = Transaction.getCurrentOpenedTransaction();
    try (Transaction transaction = Transaction.open(current)) {
      int filled = handler.insert(FluidResource.of(fluid), fluid.getAmount(), transaction);
      if (filled > 0) {
        transaction.commit();
        return access.getResource().toStack();
      }
    }
    return getBucketResult(stack, fluid);
  }

  private static ItemStack getBucketResult(ItemStack stack, FluidStack fluid) {
    if (!stack.is(Items.BUCKET) || fluid.getAmount() < FluidType.BUCKET_VOLUME) {
      return ItemStack.EMPTY;
    }
    ItemStack result = fluid.getFluidType().getBucket(fluid);
    if (result.isEmpty() || result.getItem() == Items.AIR || result.getItem() == Items.BUCKET) {
      Item bucket = fluid.getFluid().getBucket();
      if (bucket == Items.AIR || bucket == Items.BUCKET) {
        return ItemStack.EMPTY;
      }
      result = new ItemStack(bucket);
    }
    return result;
  }

  /* Display */
  /** Cache of items to display for this container */
  private List<DisplayCastingRecipe> displayRecipes = null;

  @Override
  public List<DisplayCastingRecipe> getRecipes(RegistryAccess access) {
    if (displayRecipes == null) {
      List<ItemStack> casts = Collections.singletonList(new ItemStack(container));
      displayRecipes = BuiltInRegistries.FLUID.stream()
        .filter(fluid -> {
          // skip flowing fluids (redundant to source) and fluids with no bucket (probably internal)
          // parity (oracle): official also skips fluids hidden from creative tanks (the potion fluid, whose container would
          // lose its potion) and fluids whose bucket is hidden from recipe viewers
          if (fluid.isSource(fluid.defaultFluidState()) && !fluid.is(slimeknights.tconstruct.common.TinkerTags.Fluids.HIDE_IN_CREATIVE_TANKS)) {
            try {
              net.minecraft.world.item.Item bucket = fluid.getBucket();
              return bucket != Items.AIR && !bucket.builtInRegistryHolder().is(slimeknights.tconstruct.common.TinkerTags.Items.HIDDEN_IN_RECIPE_VIEWERS);
            } catch (Exception e) {
              // Registrate (popular dependency for making registration easier) is broken and throws in getBucket for fluids with no bucket
              // we could just skip the bucket check, but its just going to throw when we try to fill an empty bucket in map below
            }
          }
          return false;
        })
        .map(fluid -> {
          FluidStack fluidStack = new FluidStack(fluid, fluidAmount);
          ItemStack stack = new ItemStack(container);
          ItemStack filled = fillContainer(stack, fluidStack);
          return new DisplayCastingRecipe(getId(), getType(), casts, Collections.singletonList(fluidStack), filled.isEmpty() ? stack : filled, 5, true);
        })
        .toList();
    }
    return displayRecipes;
  }
}
