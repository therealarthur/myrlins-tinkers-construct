package slimeknights.tconstruct.library.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe;
import slimeknights.tconstruct.library.recipe.casting.ICastingRecipe;
import slimeknights.tconstruct.library.recipe.entitymelting.EntityMeltingRecipe;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuel;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipe;
import slimeknights.tconstruct.library.recipe.melting.IMeltingRecipe;
import slimeknights.tconstruct.library.recipe.modifiers.severing.SeveringRecipe;
import slimeknights.tconstruct.library.recipe.molding.MoldingRecipe;
import slimeknights.tconstruct.library.recipe.partbuilder.IPartBuilderRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationRecipe;
import slimeknights.tconstruct.library.recipe.worktable.IModifierWorktableRecipe;

/**
 * Class containing all of Continuum Construct recipe types
 */
public class TinkerRecipeTypes {
  /** Deferred instance */
  private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, TConstruct.MOD_ID);

  public static final DeferredHolder<RecipeType<IPartBuilderRecipe>, RecipeType<IPartBuilderRecipe>> PART_BUILDER = register("part_builder");
  public static final DeferredHolder<RecipeType<MaterialRecipe>, RecipeType<MaterialRecipe>> MATERIAL = register("material");
  public static final DeferredHolder<RecipeType<ITinkerStationRecipe>, RecipeType<ITinkerStationRecipe>> TINKER_STATION = register("tinker_station");
  public static final DeferredHolder<RecipeType<IModifierWorktableRecipe>, RecipeType<IModifierWorktableRecipe>> MODIFIER_WORKTABLE = register("modifier_worktable");

  // casting
  public static final DeferredHolder<RecipeType<ICastingRecipe>, RecipeType<ICastingRecipe>> CASTING_BASIN = register("casting_basin");
  public static final DeferredHolder<RecipeType<ICastingRecipe>, RecipeType<ICastingRecipe>> CASTING_TABLE = register("casting_table");
  public static final DeferredHolder<RecipeType<MoldingRecipe>, RecipeType<MoldingRecipe>> MOLDING_TABLE = register("molding_table");
  public static final DeferredHolder<RecipeType<MoldingRecipe>, RecipeType<MoldingRecipe>> MOLDING_BASIN = register("molding_basin");

  // smeltery
  public static final DeferredHolder<RecipeType<IMeltingRecipe>, RecipeType<IMeltingRecipe>> MELTING = register("melting");
  public static final DeferredHolder<RecipeType<EntityMeltingRecipe>, RecipeType<EntityMeltingRecipe>> ENTITY_MELTING = register("entity_melting");
  public static final DeferredHolder<RecipeType<MeltingFuel>, RecipeType<MeltingFuel>> FUEL = register("fuel");
  public static final DeferredHolder<RecipeType<AlloyRecipe>, RecipeType<AlloyRecipe>> ALLOYING = register("alloying");

  // modifiers
  public static final DeferredHolder<RecipeType<SeveringRecipe>, RecipeType<SeveringRecipe>> SEVERING = register("severing");

  /** Internal recipe type for recipes that are not pulled by any specific crafting block */
  public static final DeferredHolder<RecipeType<Recipe<?>>, RecipeType<Recipe<?>>> DATA = register("data");

  /** Initializes the deferred register */
  public static void init(IEventBus bus) {
    TYPES.register(bus);
  }

  /**
   * NeoForge 26 only sends requested recipe types to the client. JEI and client-side lookups
   * need Continuum Construct's custom types, including part builder and tinker station recipes.
   */
  public static void sendRecipesToClient(OnDatapackSyncEvent event) {
    event.sendRecipes(
      PART_BUILDER.get(),
      MATERIAL.get(),
      TINKER_STATION.get(),
      MODIFIER_WORKTABLE.get(),
      CASTING_BASIN.get(),
      CASTING_TABLE.get(),
      MOLDING_TABLE.get(),
      MOLDING_BASIN.get(),
      MELTING.get(),
      ENTITY_MELTING.get(),
      FUEL.get(),
      ALLOYING.get(),
      SEVERING.get(),
      DATA.get()
    );
    // The Tinkers books show crafting table recipes by ID (Continuum Core "mantle:crafting" pages, 15 recipes in
    // Materials and You, Puny Smelting and the others). Official clients had every recipe; since 26.1 only the
    // requested types arrive. NeoForge merges requests, so packs where another mod already requests crafting
    // (Patchouli in this pack requests every type) send nothing extra.
    event.sendRecipes(RecipeType.CRAFTING);
  }

  /**
   * Registers a new recipe type, prefixing with the mod ID
   * @param name  Recipe type name
   * @param <T>   Recipe type
   * @return  Registered recipe type
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  static <T extends Recipe<?>> DeferredHolder<RecipeType<T>, RecipeType<T>> register(String name) {
    return (DeferredHolder) TYPES.register(name, () -> new RecipeType<T>() {
      @Override
      public String toString() {
        return TConstruct.MOD_ID + ":" + name;
      }
    });
  }
}
