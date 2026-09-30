package slimeknights.tconstruct.smeltery.block.entity.module;

import static slimeknights.tconstruct.library.fluid.FluidActions.EXECUTE;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import slimeknights.mantle.block.entity.MantleBlockEntity;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuel;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuelLookup;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * Module handling fuel consumption for the melter and smeltery
 */
@RequiredArgsConstructor
public abstract class FuelModule implements ContainerData {
  /** Parent TE */
  protected final MantleBlockEntity parent;

  /** Last fuel recipe used */
  @Nullable
  private MeltingFuel lastRecipe;
  /** Last fluid handler where fluid was extracted */
  @Nullable
  protected IFluidHandler fluidHandler;

  /** Current amount of fluid in the TE */
  @Getter
  protected int fuel = 0;
  /** Amount of fuel produced by the last source */
  @Getter
  protected int fuelQuality = 0;
  /** Temperature of the current fuel */
  @Getter
  protected int temperature = 0;
  /** Amount to progress recipes by per time step */
  @Getter
  protected int rate = 0;


  /*
   * Helpers
   */

  /** Resets the cached fluid handler. */
  protected void resetHandler(@Nullable Object source) {
    if (source == null || source == fluidHandler) {
      fluidHandler = null;
    }
  }

  /** Gets a nonnull world instance from the parent */
  protected Level getLevel() {
    return Objects.requireNonNull(parent.getLevel(), "Parent tile entity has null world");
  }


  /**
   * Finds a recipe for the given fluid
   * @param fluid  Fluid
   * @return  Recipe
   */
  @Nullable
  protected MeltingFuel findRecipe(Fluid fluid) {
    return findRecipe(fluid, Integer.MAX_VALUE);
  }

  /**
   * Finds a liquid fuel this tank can actually spend. The hottest fitting recipe wins, so a
   * larger unrelated fuel cannot hide blazing blood that the tank still holds.
   */
  @Nullable
  protected MeltingFuel findRecipe(Fluid fluid, int available) {
    if (lastRecipe != null && acceptsFuel(lastRecipe, fluid, available)) {
      return lastRecipe;
    }
    MeltingFuel best = bestFuel(MeltingFuelLookup.getAll(), fluid, available);
    Level level = getLevel();
    if (best == null && level.getServer() != null) {
      best = bestFuel(level.getServer().getRecipeManager().recipeMap().byType(TinkerRecipeTypes.FUEL.get()).stream().map(RecipeHolder::value).toList(), fluid, available);
    }
    if (best != null) {
      lastRecipe = best;
    }
    return best;
  }

  @Nullable
  private static MeltingFuel bestFuel(List<MeltingFuel> fuels, Fluid fluid, int available) {
    MeltingFuel best = null;
    for (MeltingFuel candidate : fuels) {
      if (!acceptsFuel(candidate, fluid, available)) {
        continue;
      }
      if (best == null || candidate.getTemperature() > best.getTemperature()) {
        best = candidate;
      }
    }
    return best;
  }

  /** Liquid fuel whose cost fits in the tank. Solid fuel has no fluid input and is ignored here. */
  private static boolean acceptsFuel(MeltingFuel recipe, Fluid fluid, int available) {
    if (recipe.getInput() == FluidIngredient.EMPTY || !matchesFuel(recipe, fluid)) {
      return false;
    }
    int amount = recipe.getAmount(fluid);
    return amount > 0 && available >= amount;
  }

  /** Matches a fuel recipe by its ingredient, then by the fluid's registry id. */
  private static boolean matchesFuel(MeltingFuel recipe, Fluid fluid) {
    if (recipe.matches(fluid)) {
      return true;
    }
    Identifier id = BuiltInRegistries.FLUID.getKey(fluid);
    if (id == null) {
      return false;
    }
    for (FluidStack input : recipe.getInputs()) {
      if (id.equals(BuiltInRegistries.FLUID.getKey(input.getFluid()))) {
        return true;
      }
    }
    return false;
  }

  /* Fuel attributes */

  /**
   * Checks if we have fuel
   * @return  True if we have fuel
   */
  public boolean hasFuel() {
    return fuel > 0;
  }

  /**
   * Consumes fuel from the module
   * @param amount  Amount of fuel to consume
   */
  public void decreaseFuel(int amount) {
    fuel = Math.max(0, fuel - amount);
    parent.setChangedFast();
  }


  /* Fuel updating */

  /**
   * Trys to consume fuel from the given fluid handler
   * @param handler  Handler to consume fuel from
   * @return   Temperature of the consumed fuel, 0 if none found
   */
  protected int tryLiquidFuel(IFluidHandler handler, boolean consume) {
    FluidStack fluid = handler.getFluidInTank(0);
    if (fluid.isEmpty()) {
      return 0;
    }
    MeltingFuel recipe = findRecipe(fluid.getFluid(), fluid.getAmount());
    if (recipe == null) {
      return 0;
    }
    int amount = recipe.getAmount(fluid.getFluid());
    if (consume) {
      // The tank only holds this fluid. Take the amount directly so a component mismatch
      // cannot refuse the drain and leave the structure on an empty tank.
      FluidStack drained = handler.drain(amount, EXECUTE);
      if (drained.getAmount() < amount) {
        return 0;
      }
      fuel += recipe.getDuration();
      fuelQuality = recipe.getDuration();
      temperature = recipe.getTemperature();
      rate = recipe.getRate();
      parent.setChangedFast();
      return temperature;
    }
    return recipe.getTemperature();
  }

  /**
   * Attempts to consume fuel from one of the tanks
   * @return  temperature of the found fluid, 0 if none
   */
  public abstract int findFuel(boolean consume);

  /* NBT */
  private static final String TAG_FUEL = "fuel";
  private static final String TAG_TEMPERATURE = "temperature";
  private static final String TAG_RATE = "rate";

  /**
   * Reads the fuel from NBT
   * @param nbt  Tag to read from
   */
  public void readFromTag(CompoundTag nbt) {
    if (nbt.contains(TAG_FUEL)) {
      fuel = nbt.getInt(TAG_FUEL).orElse(0);
    }
    if (nbt.contains(TAG_TEMPERATURE)) {
      temperature = nbt.getInt(TAG_TEMPERATURE).orElse(0);
      rate = nbt.getInt(TAG_RATE).orElse(0);
    }
  }

  /**
   * Writes the fuel to NBT
   * @param nbt  Tag to write to
   * @return  Tag written to
   */
  public CompoundTag writeToTag(CompoundTag nbt) {
    nbt.putInt(TAG_FUEL, fuel);
    nbt.putInt(TAG_TEMPERATURE, temperature);
    nbt.putInt(TAG_RATE, rate);
    return nbt;
  }


  /* UI syncing */
  private static final int FUEL = 0;
  private static final int FUEL_QUALITY = 1;
  private static final int TEMPERATURE = 2;
  private static final int RATE = 3;

  @Override
  public int getCount() {
    return 4;
  }

  @Override
  public int get(int index) {
    return switch (index) {
      case FUEL         -> fuel;
      case FUEL_QUALITY -> fuelQuality;
      case TEMPERATURE  -> temperature;
      case RATE         -> rate;
      default -> 0;
    };
  }

  @Override
  public void set(int index, int value) {
    switch (index) {
      case FUEL -> fuel = value;
      case FUEL_QUALITY -> fuelQuality = value;
      case TEMPERATURE -> temperature = value;
      case RATE -> rate = value;
    }
  }

  /**
   * Called client side to get the fuel info for the current tank
   * Note this relies on the client side fuel handlers containing fuel, which is common for our blocks as show fluid in world.
   * If a tank does not do that this won't work.
   * @return  Fuel info
   */
  public FuelInfo getFuelInfo() {
    if (fluidHandler == null) {
      return FuelInfo.EMPTY;
    }
    IFluidHandler handler = fluidHandler;
    if (handler == null) {
      return FuelInfo.EMPTY;
    }
    FluidStack fluid = handler.getFluidInTank(0);
      int temperature = 0;
      if (!fluid.isEmpty()) {
        MeltingFuel fuel = findRecipe(fluid.getFluid());
        if (fuel != null) {
          temperature = fuel.getTemperature();
        }
      }
    return FuelInfo.of(fluid, handler.getTankCapacity(0), temperature);
  }

  /** Data class to hold information about the current fuel */
  @Getter
  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  public static class FuelInfo {
    /** Empty fuel instance */
    public static final FuelInfo EMPTY = new FuelInfo(FluidStack.EMPTY, 0, 0, 0);
    /** Item fuel instance, doesn't really matter what data we set as it will all be ignored */
    public static final FuelInfo ITEM = new FuelInfo(FluidStack.EMPTY, 0, 0, 0);

    private final FluidStack fluid;
    private int totalAmount;
    private int capacity;
    private final int temperature;

    /**
     * Gets fuel info from the given stack and capacity
     * @param fluid     Fluid
     * @param capacity  Capacity
     * @return  Fuel info
     */
    public static FuelInfo of(FluidStack fluid, int capacity, int temperature) {
      if (fluid.isEmpty()) {
        return EMPTY;
      }
      return new FuelInfo(fluid, fluid.getAmount(), Math.max(capacity, fluid.getAmount()), temperature);
    }

    /**
     * Adds an additional amount and capacity to this info
     * @param amount    Amount to add
     * @param capacity  Capacity to add
     */
    protected void add(int amount, int capacity) {
      this.totalAmount += amount;
      this.capacity += capacity;
    }

    /**
     * Checks if this fuel info is an item
     * @return  True if an item
     */
    public boolean isItem() {
      return this == ITEM;
    }

    /** Checks if this fuel info has no fluid */
    public boolean isEmpty() {
      return fluid.isEmpty() || totalAmount == 0 || capacity == 0;
    }
  }
}
