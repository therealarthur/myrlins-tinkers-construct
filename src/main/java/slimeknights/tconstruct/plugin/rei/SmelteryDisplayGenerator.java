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
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.recipe.helper.RecipeHelper;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.client.recipe.ClientRecipeCache;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayMapper;
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
    return ingredients.stream().anyMatch(ingredient -> EntryIngredients.testFuzzy(ingredient, entry));
  }

  /** One expansion per snapshot, shared by all category generators. No level or recipe objects are retained. */
  static final class Recipes {
    private long revision = -1;
    private Map<Identifier,List<SmelteryDisplay>> displays = Map.of();

    synchronized List<SmelteryDisplay> all() {
      get(TConstructREIClientPlugin.CASTING_TABLE);
      return displays.values().stream().flatMap(List::stream).toList();
    }

    synchronized List<SmelteryDisplay> focused(CategoryIdentifier<SmelteryDisplay> category, EntryStack<?> focus, boolean output) {
      if (!category.equals(TConstructREIClientPlugin.TOOL_TINKERING) || !focus.getType().equals(VanillaEntryTypes.ITEM)) return get(category);
      var snapshot = ClientRecipeCache.getSnapshot();
      if (snapshot.recipes() == RecipeMap.EMPTY || Minecraft.getInstance().level == null || !MaterialRegistry.isFullyLoaded()) return List.of();
      ItemStack item = focus.castValue();
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

    synchronized List<SmelteryDisplay> get(CategoryIdentifier<SmelteryDisplay> category) {
      ClientRecipeCache.Snapshot snapshot = ClientRecipeCache.getSnapshot();
      if (snapshot.recipes() == RecipeMap.EMPTY || Minecraft.getInstance().level == null || !MaterialRegistry.isFullyLoaded()) {
        displays = Map.of();
        revision = -1;
        return List.of();
      }
      if (revision != snapshot.revision()) {
        Map<Identifier,SmelteryDisplay> expanded = new LinkedHashMap<>();
        Builder builder = new Builder(snapshot.registryAccess(), expanded);
        expand(snapshot, TinkerRecipeTypes.CASTING_BASIN.get(), IDisplayableCastingRecipe.class,
          (source, recipe) -> builder.casting(TConstructREIClientPlugin.CASTING_BASIN, source, recipe));
        expand(snapshot, TinkerRecipeTypes.CASTING_TABLE.get(), IDisplayableCastingRecipe.class,
          (source, recipe) -> builder.casting(TConstructREIClientPlugin.CASTING_TABLE, source, recipe));
        expand(snapshot, TinkerRecipeTypes.MELTING.get(), MeltingRecipe.class, builder::melting);
        expand(snapshot, TinkerRecipeTypes.ALLOYING.get(), AlloyRecipe.class, builder::alloy);
        expand(snapshot, TinkerRecipeTypes.FUEL.get(), MeltingFuel.class, builder::fuel);
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
        TConstruct.LOG.info("Prepared {} REI displays across {} categories from received recipe snapshot {}", expanded.size(), grouped.size(), revision);
      }
      return displays.getOrDefault(category.getIdentifier(), List.of());
    }
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
        data.lookupInputs().isEmpty() ? List.of() : List.of(ingredient(data.lookupInputs())));
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

    void casting(CategoryIdentifier<SmelteryDisplay> category, Identifier source, IDisplayableCastingRecipe recipe) {
      List<ItemStack> outputs = recipe.getOutputs();
      List<ItemStack> casts = recipe.getCastItems();
      if (outputs.isEmpty() || recipe.getFluids().isEmpty() || recipe.hasCast() && casts.isEmpty()) return;
      // These are paired variants, not independent alternatives. Split them instead of showing impossible combinations.
      int variants = outputs.size() > 1 && casts.size() == outputs.size() ? outputs.size() : 1;
      for (int i = 0; i < variants; i++) {
        List<EntryIngredient> inputs = new ArrayList<>();
        List<EntryIngredient> catalysts = new ArrayList<>();
        inputs.add(fluids(recipe.getFluids()));
        if (recipe.hasCast()) {
          EntryIngredient cast = EntryIngredients.ofItemStacks(variants > 1 ? List.of(casts.get(i)) : casts);
          (recipe.isConsumed() ? inputs : catalysts).add(cast);
        }
        List<Component> notes = new ArrayList<>();
        notes.add(Component.translatableWithFallback("rei.tconstruct.cooling", "Cooling: %s s", recipe.getCoolingTime() / 20f));
        if (recipe.hasCast()) notes.add(Component.translatable(recipe.isConsumed() ? "jei.tconstruct.casting.cast_consumed" : "jei.tconstruct.casting.cast_kept"));
        add(category, source, inputs, List.of(EntryIngredients.ofItemStacks(variants > 1 ? List.of(outputs.get(i)) : outputs)), catalysts, notes);
      }
    }

    void melting(Identifier source, MeltingRecipe recipe) {
      List<EntryIngredient> inputs = List.of(EntryIngredients.ofItemStacks(MaterialRecipeCache.getDisplayItems(recipe.getInput())));
      if (inputs.getFirst().isEmpty() || recipe.getOutput().isEmpty()) return;
      var ore = recipe.getOreType();
      FluidStack melter = ore == null ? recipe.getOutput() : Config.COMMON.melterOreRate.applyOreBoost(ore, recipe.getOutput(), true);
      FluidStack smeltery = ore == null ? recipe.getOutput() : Config.COMMON.smelteryOreRate.applyOreBoost(ore, recipe.getOutput(), true);
      List<Component> notes = List.of(temperature(recipe.getTemperature()),
        Component.translatableWithFallback("rei.tconstruct.base_melting_time", "Base melting time: %s s (fuel speed varies)", recipe.getTime() / 5f));
      add(TConstructREIClientPlugin.MELTING, source, inputs, List.of(fluids(List.of(melter))),
        List.of(EntryIngredients.of(TinkerSmeltery.searedMelter)), notes);
      add(TConstructREIClientPlugin.MELTING, source, inputs, List.of(fluids(List.of(smeltery))),
        List.of(EntryIngredients.of(TinkerSmeltery.smelteryController)), notes);
      add(TConstructREIClientPlugin.FOUNDRY, source, inputs, recipe.getOutputWithByproducts().stream().map(SmelteryDisplayGenerator::fluids).toList(),
        List.of(EntryIngredients.of(TinkerSmeltery.foundryController)), notes);
    }

    void alloy(Identifier source, AlloyRecipe recipe) {
      if (recipe.getOutput().isEmpty()) return;
      List<EntryIngredient> inputs = new ArrayList<>();
      List<EntryIngredient> catalysts = new ArrayList<>();
      for (var ingredient : recipe.getInputs()) {
        (ingredient.catalyst() ? catalysts : inputs).add(fluids(ingredient.fluid().getFluids()));
      }
      add(TConstructREIClientPlugin.ALLOY, source, inputs, List.of(fluids(List.of(recipe.getOutput()))), catalysts,
        List.of(temperature(recipe.getTemperature())));
    }

    void fuel(Identifier source, MeltingFuel recipe) {
      if (recipe.getInput() != FluidIngredient.EMPTY) {
        if (!recipe.getInputs().isEmpty()) {
          addFuel(source, recipe, fluids(recipe.getInputs()), recipe.getDuration(), List.of(), List.of());
        }
        return;
      }
      var level = Minecraft.getInstance().level;
      if (level == null) return;
      for (var item : BuiltInRegistries.ITEM) {
        ItemStack stack = new ItemStack(item);
        int burnTime = level.fuelValues().burnDuration(stack);
        int hookTime = EventHooks.getItemBurnTime(stack, burnTime, TinkerRecipeTypes.FUEL.get(), level.fuelValues());
        // Match SolidFuelModule, including its fallback and integer division.
        int duration = (hookTime > 0 ? hookTime : burnTime) / 4;
        if (duration > 0) {
          var remainder = stack.getCraftingRemainder();
          ItemStack container = remainder == null ? ItemStack.EMPTY : remainder.create();
          List<EntryIngredient> outputs = container.isEmpty() ? List.of() : List.of(EntryIngredients.of(container));
          addFuel(source, recipe, EntryIngredients.of(stack), duration, List.of(EntryIngredients.of(TinkerSmeltery.searedHeater)), outputs);
        }
      }
    }

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

  private static EntryIngredient ingredient(List<RecipeDisplayData.Value> values) {
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
}
