package slimeknights.tconstruct.plugin.rei;

import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.recipe.helper.RecipeHelper;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.client.recipe.ClientRecipeCache;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayMapper;
import slimeknights.tconstruct.library.client.recipe.RecipeFocus;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe;
import slimeknights.tconstruct.library.recipe.casting.IDisplayableCastingRecipe;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuel;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.melting.MeltingRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.IDisplayToolTinkering;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/** Searches the latest received snapshot, including syncs arriving after REI's registration phase. */
final class SmelteryDisplayGenerator implements DynamicDisplayGenerator<SmelteryDisplay> {
  private final CategoryIdentifier<SmelteryDisplay> category;
  private final Recipes recipes;

  SmelteryDisplayGenerator(CategoryIdentifier<SmelteryDisplay> category, Recipes recipes) {
    this.category = category;
    this.recipes = recipes;
  }

  @Override
  public Optional<List<SmelteryDisplay>> getRecipeFor(EntryStack<?> entry) {
    return Optional.of(recipes.focused(category, entry, true).stream().filter(display -> matches(display.outputs(), entry)).toList());
  }

  @Override
  public Optional<List<SmelteryDisplay>> getUsageFor(EntryStack<?> entry) {
    return Optional.of(recipes.focused(category, entry, false).stream().filter(display -> matches(display.getRequiredEntries(), entry)).toList());
  }

  @Override
  public Optional<List<SmelteryDisplay>> generate(ViewSearchBuilder builder) {
    if (builder.getRecipesFor().isEmpty() && builder.getUsagesFor().isEmpty()
        && (builder.getCategories().isEmpty() || builder.getCategories().contains(category))) {
      return Optional.of(recipes.get(category));
    }
    return Optional.empty();
  }

  private static boolean matches(List<EntryIngredient> ingredients, EntryStack<?> entry) {
    // Tinkers entries follow official JEI identities: exact material variant, modifier ID at any level, amounts ignored
    if (entry.getValue() instanceof RecipeDisplayData.Value focus) {
      return ingredients.stream().flatMap(List::stream)
        .anyMatch(candidate -> candidate.getValue() instanceof RecipeDisplayData.Value value && RecipeFocus.matches(focus, value));
    }
    if (ingredients.stream().anyMatch(ingredient -> EntryIngredients.testFuzzy(ingredient, entry))) {
      return true;
    }
    // bucket, tank and can focus also finds the contained fluid, through REI's own item to fluid support
    if (entry.getType().equals(VanillaEntryTypes.ITEM)) {
      List<EntryStack<dev.architectury.fluid.FluidStack>> fluids = containedFluids(entry.cast());
      return !fluids.isEmpty() && ingredients.stream().anyMatch(ingredient -> fluids.stream().anyMatch(fluid -> EntryIngredients.testFuzzy(ingredient, fluid)));
    }
    return false;
  }

  /** Fluids REI's fluid support finds in an item, or none; never throws into a lookup. */
  private static List<EntryStack<dev.architectury.fluid.FluidStack>> containedFluids(EntryStack<ItemStack> item) {
    try {
      return me.shedaniel.rei.api.common.fluid.FluidSupportProvider.getInstance().itemToFluids(item)
        .map(stream -> stream.toList()).orElse(List.of());
    } catch (RuntimeException exception) {
      return List.of();
    }
  }

  /**
   * Fuel facts the melting, alloy and entity melting layouts draw from the current snapshot, like official
   * MeltingFuelHandler.
   * @param fuels     every fuel recipe in the snapshot
   * @param solid     the solid fuel recipe, or null when the server has none
   * @param examples  solid fuel examples from {@code tconstruct:fuel_examples}
   */
  record FuelInfo(List<MeltingFuel> fuels, MeltingFuel solid, List<ItemStack> examples) {
    static final FuelInfo EMPTY = new FuelInfo(List.of(), null, List.of());

    /** Liquid fuels hot enough for the given temperature. */
    List<FluidStack> usable(int temperature) {
      return RecipeDisplayMapper.usableFuels(fuels, temperature);
    }
  }

  /** One expansion per snapshot, shared by all category generators. No level or recipe objects are retained. */
  static final class Recipes {
    private long revision = -1;
    /** Drop, rebuild or keep decisions, tested headlessly in SnapshotGateTest. */
    private final SnapshotGate gate = new SnapshotGate();
    private Map<Identifier,List<SmelteryDisplay>> displays = Map.of();
    /** Fuel facts for the current revision. Fuel recipes are small immutable objects owned by the snapshot. */
    private volatile FuelInfo fuelInfo = FuelInfo.EMPTY;
    /** Source ID of the solid fuel recipe, for focused solid fuel pages. */
    private Identifier solidFuelSource = null;

    synchronized List<SmelteryDisplay> all() {
      get(TConstructREIClientPlugin.CASTING_TABLE);
      return displays.values().stream().flatMap(List::stream).toList();
    }

    /** Fuel facts for layouts; empty until a synchronized snapshot has been expanded. */
    FuelInfo fuelInfo() {
      return fuelInfo;
    }

    synchronized List<SmelteryDisplay> focused(CategoryIdentifier<SmelteryDisplay> category, EntryStack<?> focus, boolean output) {
      if (category.equals(TConstructREIClientPlugin.FUEL) && !output && focus.getType().equals(VanillaEntryTypes.ITEM)) {
        return solidFuelFocus(focus.<ItemStack>castValue().copy());
      }
      if (!category.equals(TConstructREIClientPlugin.TOOL_TINKERING) || !focus.getType().equals(VanillaEntryTypes.ITEM)) return get(category);
      var snapshot = ClientRecipeCache.getSnapshot();
      if (snapshot.recipes() == RecipeMap.EMPTY || Minecraft.getInstance().level == null || !MaterialRegistry.isFullyLoaded()) return List.of();
      // a copy: focus-dependent producers may write into their argument, and this is REI's own entry stack
      ItemStack item = focus.<ItemStack>castValue().copy();
      var expanded = new LinkedHashMap<Identifier,SmelteryDisplay>();
      Builder builder = new Builder(snapshot.registryAccess(), expanded);
      for (RecipeHolder<?> holder : holders(snapshot, TinkerRecipeTypes.TINKER_STATION.get())) {
        for (var recipe : RecipeHelper.getJEIRecipes(snapshot.registryAccess(), Stream.of(holder), IDisplayToolTinkering.class)) {
          // Focus-only recipes must pass their visibility guard before any focus-dependent getters run.
          if (recipe.isFiltered() && !recipe.isVisibleFromItem(item, output)) continue;
          if (!recipe.isTool(item) && !recipe.matchesItem(stack -> EntryIngredients.testFuzzy(EntryIngredients.of(stack), focus), output)) continue;
          if (output && recipe.isToolCatalyst()) continue;
          try {
            RecipeDisplayMapper.tinkering(holder.id().identifier(), recipe, item, output).forEach(builder::mapped);
          } catch (RuntimeException exception) {
            TConstruct.LOG.warn("Could not map focused REI tinkering display for {}", holder.id().identifier(), exception);
          }
        }
      }
      return List.copyOf(expanded.values());
    }

    /**
     * Usage of a burnable item: the focused solid fuel page for that item, like official FuelCategory, plus any
     * liquid fuel page that lists the item (none today, kept for completeness).
     */
    private List<SmelteryDisplay> solidFuelFocus(ItemStack stack) {
      List<SmelteryDisplay> standard = get(TConstructREIClientPlugin.FUEL);
      Level level = Minecraft.getInstance().level;
      FuelInfo info = fuelInfo;
      if (level == null || info.solid() == null || solidFuelSource == null || RecipeDisplayMapper.solidFuelDuration(stack, level) <= 0) {
        return standard;
      }
      var snapshot = ClientRecipeCache.getSnapshot();
      var expanded = new LinkedHashMap<Identifier,SmelteryDisplay>();
      Builder builder = new Builder(snapshot.registryAccess(), expanded);
      RecipeDisplayMapper.solidFuel(solidFuelSource, info.solid(), new ItemStack(TinkerSmeltery.searedHeater), info.examples(), stack, level)
        .forEach(builder::mapped);
      List<SmelteryDisplay> result = new ArrayList<>(expanded.values());
      // keep liquid fuel pages that also match; the unfocused examples page is replaced by the focused one
      standard.stream().filter(display -> !slimeknights.tconstruct.library.client.recipe.RecipeLayout.getBoolean(display.layout(),
        slimeknights.tconstruct.library.client.recipe.RecipeLayout.SOLID)).forEach(result::add);
      return List.copyOf(result);
    }

    synchronized List<SmelteryDisplay> get(CategoryIdentifier<SmelteryDisplay> category) {
      ClientRecipeCache.Snapshot snapshot = ClientRecipeCache.getSnapshot();
      boolean ready = snapshot.recipes() != RecipeMap.EMPTY && Minecraft.getInstance().level != null && MaterialRegistry.isFullyLoaded();
      SnapshotGate.Action action = gate.next(ready, snapshot.revision());
      if (action == SnapshotGate.Action.CLEAR) {
        displays = Map.of();
        revision = -1;
        fuelInfo = FuelInfo.EMPTY;
        solidFuelSource = null;
        return List.of();
      }
      if (action == SnapshotGate.Action.REBUILD) {
        Map<Identifier,SmelteryDisplay> expanded = new LinkedHashMap<>();
        Builder builder = new Builder(snapshot.registryAccess(), expanded);
        prepareFuels(snapshot);
        expand(snapshot, TinkerRecipeTypes.CASTING_BASIN.get(), IDisplayableCastingRecipe.class,
          (source, recipe) -> builder.casting(TConstructREIClientPlugin.CASTING_BASIN, source, recipe));
        expand(snapshot, TinkerRecipeTypes.CASTING_TABLE.get(), IDisplayableCastingRecipe.class,
          (source, recipe) -> builder.casting(TConstructREIClientPlugin.CASTING_TABLE, source, recipe));
        expand(snapshot, TinkerRecipeTypes.MELTING.get(), MeltingRecipe.class, builder::melting);
        expand(snapshot, TinkerRecipeTypes.ALLOYING.get(), AlloyRecipe.class, builder::alloy);
        expand(snapshot, TinkerRecipeTypes.FUEL.get(), MeltingFuel.class, (source, fuel) -> builder.fuel(source, fuel, fuelInfo.examples()));
        for (var type : List.of(TinkerRecipeTypes.PART_BUILDER.get(), TinkerRecipeTypes.MATERIAL.get(), TinkerRecipeTypes.TINKER_STATION.get(),
            TinkerRecipeTypes.MODIFIER_WORKTABLE.get(), TinkerRecipeTypes.MOLDING_TABLE.get(), TinkerRecipeTypes.MOLDING_BASIN.get(),
            TinkerRecipeTypes.ENTITY_MELTING.get(), TinkerRecipeTypes.SEVERING.get(), TinkerRecipeTypes.DATA.get())) {
          for (RecipeHolder<?> holder : holders(snapshot, type)) {
            try {
              RecipeDisplayMapper.map(holder, snapshot.registryAccess(), Minecraft.getInstance().level).forEach(builder::mapped);
            } catch (RuntimeException exception) {
              TConstruct.LOG.warn("Could not map REI display for {}", holder.id().identifier(), exception);
            }
          }
        }
        RecipeDisplayMapper.defaultEntityMelting(snapshot.recipes().byType(TinkerRecipeTypes.ENTITY_MELTING.get()).stream()
          .map(RecipeHolder::value).toList()).forEach(builder::mapped);
        Map<Identifier,List<SmelteryDisplay>> grouped = new LinkedHashMap<>();
        for (SmelteryDisplay display : expanded.values()) {
          grouped.computeIfAbsent(display.category(), ignored -> new ArrayList<>()).add(display);
        }
        grouped.replaceAll((id, values) -> List.copyOf(values));
        displays = Map.copyOf(grouped);
        revision = snapshot.revision();
        gate.built(revision);
        TConstruct.LOG.info("Prepared {} REI displays across {} categories from received recipe snapshot {}", expanded.size(), grouped.size(), revision);
      }
      return displays.getOrDefault(category.getIdentifier(), List.of());
    }

    /** Collects fuel recipes, the solid fuel and its examples for this revision. */
    private void prepareFuels(ClientRecipeCache.Snapshot snapshot) {
      List<MeltingFuel> fuels = new ArrayList<>();
      MeltingFuel solid = null;
      Identifier solidSource = null;
      for (RecipeHolder<?> holder : holders(snapshot, TinkerRecipeTypes.FUEL.get()).stream()
          .sorted(Comparator.comparing(value -> value.id().identifier().toString())).toList()) {
        if (holder.value() instanceof MeltingFuel fuel) {
          fuels.add(fuel);
          if (fuel.getInput() == FluidIngredient.EMPTY && solid == null && fuel.getRate() != 0) {
            solid = fuel;
            solidSource = holder.id().identifier();
          }
        }
      }
      fuelInfo = new FuelInfo(List.copyOf(fuels), solid, solidFuelExamples(Minecraft.getInstance().level));
      solidFuelSource = solidSource;
    }
  }

  /**
   * Example solid fuels, from {@code tconstruct:fuel_examples} like official. If a server does not send that tag,
   * falls back to every burnable default item so the page is never empty.
   */
  static List<ItemStack> solidFuelExamples(Level level) {
    List<ItemStack> examples = new ArrayList<>();
    for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(TinkerTags.Items.FUEL_EXAMPLES)) {
      examples.add(new ItemStack(holder));
    }
    if (examples.isEmpty() && level != null) {
      for (Item item : BuiltInRegistries.ITEM) {
        ItemStack stack = new ItemStack(item);
        if (RecipeDisplayMapper.solidFuelDuration(stack, level) > 0) examples.add(stack);
      }
    }
    return List.copyOf(examples);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Collection<RecipeHolder<?>> holders(ClientRecipeCache.Snapshot snapshot, RecipeType<?> type) {
    return (Collection)snapshot.recipes().byType((RecipeType)type);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static <T> void expand(ClientRecipeCache.Snapshot snapshot, RecipeType<?> type, Class<T> displayClass, BiConsumer<Identifier,T> consumer) {
    Collection<RecipeHolder<?>> holders = (Collection)snapshot.recipes().byType((RecipeType)type);
    for (RecipeHolder<?> holder : holders.stream().sorted(Comparator.comparing(value -> value.id().identifier().toString())).toList()) {
      for (T recipe : RecipeHelper.getJEIRecipes(snapshot.registryAccess(), Stream.of(holder), displayClass)) {
        try {
          consumer.accept(holder.id().identifier(), recipe);
        } catch (RuntimeException exception) {
          TConstruct.LOG.warn("Could not create REI display for {} ({})", holder.id().identifier(), displayClass.getSimpleName(), exception);
        }
      }
    }
  }

  private record Builder(RegistryAccess access, Map<Identifier,SmelteryDisplay> displays) {
    void mapped(RecipeDisplayData data) {
      SmelteryDisplay display = SmelteryDisplay.create(access, data.category(), data.source(),
        data.inputs().stream().map(SmelteryDisplayGenerator::ingredient).toList(),
        data.outputs().stream().map(SmelteryDisplayGenerator::ingredient).toList(),
        data.catalysts().stream().map(SmelteryDisplayGenerator::ingredient).toList(), data.notes(),
        data.lookupInputs().isEmpty() ? List.of() : List.of(ingredient(data.lookupInputs())),
        data.layout(), data.renderOnly().stream().map(SmelteryDisplayGenerator::ingredient).toList());
      displays.putIfAbsent(display.displayId(), display);
    }

    private void add(CategoryIdentifier<SmelteryDisplay> category, Identifier source, List<EntryIngredient> inputs,
                     List<EntryIngredient> outputs, List<EntryIngredient> catalysts, List<Component> notes) {
      if (Stream.of(inputs, outputs, catalysts).flatMap(List::stream).anyMatch(List::isEmpty)) {
        return; // A missing compatibility tag must not become a recipe with a free input.
      }
      SmelteryDisplay display = SmelteryDisplay.create(access, category.getIdentifier(), source, inputs, outputs, catalysts, notes);
      displays.putIfAbsent(display.displayId(), display);
    }

    /** Casting facts come from the viewer-neutral mapper, so the layout and headless tests read the same data. */
    void casting(CategoryIdentifier<SmelteryDisplay> category, Identifier source, IDisplayableCastingRecipe recipe) {
      RecipeDisplayMapper.casting(source, recipe, category.equals(TConstructREIClientPlugin.CASTING_BASIN)).forEach(this::mapped);
    }

    /** One melting page with both controller amounts (as official), and the foundry page with byproducts. */
    void melting(Identifier source, MeltingRecipe recipe) {
      RecipeDisplayMapper.melting(source, recipe).forEach(this::mapped);
    }

    void alloy(Identifier source, AlloyRecipe recipe) {
      RecipeDisplayMapper.alloy(source, recipe).forEach(this::mapped);
    }

    /** Liquid fuels map directly; the solid fuel becomes the example page, and item focus builds per-item pages. */
    void fuel(Identifier source, MeltingFuel recipe, List<ItemStack> examples) {
      if (recipe.getInput() != FluidIngredient.EMPTY) {
        RecipeDisplayMapper.liquidFuel(source, recipe).forEach(this::mapped);
        return;
      }
      RecipeDisplayMapper.solidFuel(source, recipe, new ItemStack(TinkerSmeltery.searedHeater), examples, ItemStack.EMPTY,
        Minecraft.getInstance().level).forEach(this::mapped);
    }

    /** Original per-item solid fuel builder, kept for reference; the page is now built by the mapper. */
    @SuppressWarnings("unused")
    private void addFuel(Identifier source, MeltingFuel fuel, EntryIngredient input, int duration, List<EntryIngredient> catalysts, List<EntryIngredient> outputs) {
      add(TConstructREIClientPlugin.FUEL, source, List.of(input), outputs, catalysts, List.of(
        temperature(fuel.getTemperature()),
        Component.translatableWithFallback("rei.tconstruct.fuel_duration", "Burn time at minimum fuel use: %s s", duration / 5f),
        Component.translatable("jei.tconstruct.melting.multiplier", fuel.getRate() / 10f),
        Component.translatableWithFallback("rei.tconstruct.fuel_structure_cost", "Larger structures may consume fuel faster")));
    }
  }

  private static Component temperature(int temperature) {
    return Component.translatable("jei.tconstruct.temperature", temperature);
  }

  static EntryIngredient ingredient(List<RecipeDisplayData.Value> values) {
    return values.stream().map(value -> switch (value) {
      case RecipeDisplayData.ItemValue item -> EntryStacks.of(item.stack());
      case RecipeDisplayData.FluidValue fluid -> EntryStacks.of(dev.architectury.fluid.FluidStack.create(
        fluid.stack().getFluid(), fluid.stack().getAmount(), fluid.stack().getComponentsPatch()));
      default -> TinkerEntryTypes.entry(value);
    }).collect(EntryIngredient.collector());
  }

  private static EntryIngredient fluids(List<FluidStack> fluids) {
    return EntryIngredients.from(fluids.stream().filter(stack -> !stack.isEmpty()).toList(), stack -> EntryStacks.of(
      dev.architectury.fluid.FluidStack.create(stack.getFluid(), stack.getAmount(), stack.getComponentsPatch())));
  }

  /** Unused since the mapper owns melting; kept so ore amount behavior stays documented beside the REI builder. */
  @SuppressWarnings("unused")
  private static FluidStack melterAmount(MeltingRecipe recipe) {
    var ore = recipe.getOreType();
    return ore == null ? recipe.getOutput() : Config.COMMON.melterOreRate.applyOreBoost(ore, recipe.getOutput(), true);
  }

  /** Unused helper kept with the original solid fuel item scan. */
  @SuppressWarnings("unused")
  private static List<ItemStack> materialDisplayItems(MeltingRecipe recipe) {
    return MaterialRecipeCache.getDisplayItems(recipe.getInput());
  }
}
