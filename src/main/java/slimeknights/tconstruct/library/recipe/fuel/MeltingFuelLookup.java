package slimeknights.tconstruct.library.recipe.fuel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.recipe.RecipeCacheInvalidator;
import slimeknights.tconstruct.common.recipe.RecipeCacheInvalidator.DuelSidedListener;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Class handling a recipe cache for fuel recipes, since any given entity type has one recipe
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MeltingFuelLookup {
  /**
   * Every JSON fuel is constructed with this id. {@link net.minecraft.world.item.crafting.RecipeManager}
   * no longer passes the recipe key into the codec, so the id cannot tell blaze apart from lava or solid fuel.
   */
  private static final Identifier FALLBACK_ID = Identifier.fromNamespaceAndPath("mantle", "loadable_recipe");
  /** Dummy fuel instance sine caches don't support caching null */
  private static final MeltingFuel EMPTY = new MeltingFuel(Identifier.parse("missingno"), FluidIngredient.EMPTY, 0, 0, 0);
  /** Temperature for solid fuels in the heater */
  private static MeltingFuel SOLID = EMPTY;
  /** List of all recipes */
  private static final List<MeltingFuel> RECIPES = new ArrayList<>();
  /** Mapping from fluid to fuel */
  private static final Map<Fluid,MeltingFuel> CACHE = new HashMap<>();
  /** Logic to fill the cache */
  private static final Function<Fluid,MeltingFuel> LOOKUP = fluid -> {
    for (MeltingFuel recipe : RECIPES) {
      if (recipe.matches(fluid)) {
        return recipe;
      }
    }
    return EMPTY;
  };
  /** Listener to check when recipes reload */
  private static final DuelSidedListener LISTENER = RecipeCacheInvalidator.addDuelSidedListener(() -> {
    SOLID = EMPTY;
    RECIPES.clear();
    CACHE.clear();
  });

  /**
   * Adds a melting fuel to the lookup
   * @param fuel   Fuel
   */
  public static void addFuel(MeltingFuel fuel) {
    // skip empty fuel
    if (fuel.getRate() == 0) {
      return;
    }
    LISTENER.checkClear();
    // Solid fuel shares the fallback id with lava and blazing blood. Removing by that id
    // deletes every liquid fuel already registered, so a full tank never counts as fuel.
    if (fuel.getInput() == FluidIngredient.EMPTY) {
      if (SOLID != EMPTY && SOLID != fuel && !SOLID.getId().equals(fuel.getId())) {
        TConstruct.LOG.warn("Multiple fuel recipes for solid fuel. This usually indicates a datapack error and may cause desyncs. Original {}, latest {}", SOLID.getId(), fuel.getId());
      }
      SOLID = fuel;
      CACHE.clear();
      return;
    }
    // Do not compare fluid stacks here. Recipe loading runs before fluid components
    // are bound, and building those stacks crashes world load. The shared fallback id
    // is also not unique, so removing by it would delete lava when blazing blood loads.
    if (!FALLBACK_ID.equals(fuel.getId())) {
      RECIPES.removeIf(existing -> existing.getId().equals(fuel.getId()));
    }
    CACHE.clear();
    RECIPES.add(fuel);
  }

  /**
   * Replaces the constructor-built list once the recipe manager has finished loading.
   * An empty list leaves recipes already added in place and only drops a negative cache
   * recorded before those recipes existed.
   */
  public static void rebuild(List<MeltingFuel> fuels) {
    CACHE.clear();
    if (fuels.isEmpty()) {
      return;
    }
    LISTENER.cancelQueued();
    SOLID = EMPTY;
    RECIPES.clear();
    for (MeltingFuel fuel : fuels) {
      addFuel(fuel);
    }
  }

  /** Liquid fuels currently registered. Solid fuel is not included. */
  public static List<MeltingFuel> getAll() {
    return List.copyOf(RECIPES);
  }

  /** Checks if the given fluid is a fuel */
  public static boolean isFuel(Fluid fluid) {
    return CACHE.computeIfAbsent(fluid, LOOKUP) != EMPTY;
  }

  /** Gets the properties for solid fuel */
  public static MeltingFuel getSolid() {
    return SOLID;
  }

  /**
   * Gets the recipe for the given fluid
   * @param fluid   Fluid found
   * @return  Recipe, or null if no recipe for this type
   */
  @Nullable
  public static MeltingFuel findFuel(Fluid fluid) {
    MeltingFuel recipe = CACHE.computeIfAbsent(fluid, LOOKUP);
    if (recipe == EMPTY) {
      return null;
    }
    return recipe;
  }
}
