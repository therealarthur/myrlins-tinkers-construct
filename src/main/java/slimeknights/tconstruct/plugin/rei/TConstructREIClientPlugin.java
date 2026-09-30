package slimeknights.tconstruct.plugin.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import me.shedaniel.rei.forge.REIPluginClient;
import me.shedaniel.rei.impl.common.plugins.ReloadManagerImpl;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.level.ItemLike;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.library.client.recipe.ClientRecipeCache;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.plugin.rei.category.AlloyCategory;
import slimeknights.tconstruct.plugin.rei.category.CastingCategory;
import slimeknights.tconstruct.plugin.rei.category.EntityCategory;
import slimeknights.tconstruct.plugin.rei.category.FuelCategory;
import slimeknights.tconstruct.plugin.rei.category.FuelFacts;
import slimeknights.tconstruct.plugin.rei.category.LazyIcon;
import slimeknights.tconstruct.plugin.rei.category.MaterialCategory;
import slimeknights.tconstruct.plugin.rei.category.MeltingCategory;
import slimeknights.tconstruct.plugin.rei.category.MoldingCategory;
import slimeknights.tconstruct.plugin.rei.category.PartBuilderCategory;
import slimeknights.tconstruct.plugin.rei.category.ReiLayout;
import slimeknights.tconstruct.plugin.rei.category.TinkerDisplayCategory;
import slimeknights.tconstruct.plugin.rei.category.TinkerStationCategory;
import slimeknights.tconstruct.plugin.rei.category.ToolBuildingCategory;
import slimeknights.tconstruct.plugin.rei.category.WorktableCategory;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.tools.item.CreativeSlotItem;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import me.shedaniel.rei.api.common.entry.EntryStack;

@REIPluginClient
public class TConstructREIClientPlugin implements REIClientPlugin {
  private static final AtomicBoolean LISTENING = new AtomicBoolean();
  private static final SmelteryDisplayGenerator.Recipes RECIPES = new SmelteryDisplayGenerator.Recipes();
  private static volatile EntryRegistry entries;
  private static final ClientEntryRefresh ENTRY_REFRESH = new ClientEntryRefresh();

  public TConstructREIClientPlugin() {
    // REI discovers this class only on clients with REI. Reloading plugins must not accumulate listeners.
    if (LISTENING.compareAndSet(false, true)) NeoForge.EVENT_BUS.addListener(TConstructREIClientPlugin::clientTick);
  }
  static final CategoryIdentifier<SmelteryDisplay> CASTING_BASIN = category("casting_basin");
  static final CategoryIdentifier<SmelteryDisplay> CASTING_TABLE = category("casting_table");
  static final CategoryIdentifier<SmelteryDisplay> MELTING = category("melting");
  static final CategoryIdentifier<SmelteryDisplay> FOUNDRY = category("foundry");
  static final CategoryIdentifier<SmelteryDisplay> ALLOY = category("alloy");
  static final CategoryIdentifier<SmelteryDisplay> FUEL = category("fuel");
  static final CategoryIdentifier<SmelteryDisplay> MOLDING = category("molding");
  static final CategoryIdentifier<SmelteryDisplay> ENTITY_MELTING = category("entity_melting");
  static final CategoryIdentifier<SmelteryDisplay> MODIFIERS = category("modifiers");
  static final CategoryIdentifier<SmelteryDisplay> SEVERING = category("severing");
  static final CategoryIdentifier<SmelteryDisplay> TOOL_BUILDING = category("tool_recipes");
  static final CategoryIdentifier<SmelteryDisplay> TOOL_TINKERING = category("tool_modification");
  static final CategoryIdentifier<SmelteryDisplay> MATERIALS = category("materials");
  static final CategoryIdentifier<SmelteryDisplay> PART_BUILDER = category("part_builder");
  static final CategoryIdentifier<SmelteryDisplay> WORKTABLE = category("worktable");
  static final List<CategoryIdentifier<SmelteryDisplay>> CATEGORIES = List.of(CASTING_BASIN, CASTING_TABLE, MELTING, FOUNDRY, ALLOY, FUEL,
    MOLDING, ENTITY_MELTING, MODIFIERS, SEVERING, TOOL_BUILDING, TOOL_TINKERING, MATERIALS, PART_BUILDER, WORKTABLE);

  private static CategoryIdentifier<SmelteryDisplay> category(String path) {
    return CategoryIdentifier.of(TConstruct.getResource(path));
  }

  /**
   * Registers the official per-category layouts (parity/rei). The generic {@link SmelteryCategory} registration below
   * is kept as {@link #registerGenericCategories(CategoryRegistry)} for reference and emergency fallback.
   */
  @Override
  public void registerCategories(CategoryRegistry registry) {
    java.util.function.Supplier<FuelFacts> fuels = TConstructREIClientPlugin::fuelFacts;
    add(registry, new CastingCategory(CASTING_BASIN, Component.translatable("jei.tconstruct.casting.basin"), TinkerSmeltery.searedBasin, true),
      TinkerSmeltery.searedBasin, TinkerSmeltery.scorchedBasin);
    add(registry, new CastingCategory(CASTING_TABLE, Component.translatable("jei.tconstruct.casting.table"), TinkerSmeltery.searedTable, false),
      TinkerSmeltery.searedTable, TinkerSmeltery.scorchedTable);
    add(registry, new MeltingCategory(MELTING, Component.translatable("jei.tconstruct.melting.title"), TinkerSmeltery.searedMelter, false, fuels),
      TinkerSmeltery.searedMelter, TinkerSmeltery.smelteryController);
    add(registry, new MeltingCategory(FOUNDRY, Component.translatable("jei.tconstruct.foundry.title"), TinkerSmeltery.foundryController, true, fuels),
      TinkerSmeltery.foundryController);
    add(registry, new AlloyCategory(ALLOY, Component.translatable("jei.tconstruct.alloy.title"), TinkerSmeltery.smelteryController, fuels),
      TinkerSmeltery.smelteryController, TinkerSmeltery.scorchedAlloyer);
    add(registry, new FuelCategory(FUEL, title("fuel", "Smeltery Fuel"), EntryStacks.of(TinkerFluids.blazingBlood)),
      TinkerSmeltery.searedHeater, TinkerSmeltery.searedMelter, TinkerSmeltery.smelteryController, TinkerSmeltery.foundryController, TinkerSmeltery.scorchedAlloyer);
    add(registry, new MoldingCategory(MOLDING, title("molding", "Molding"), TinkerSmeltery.blankSandCast),
      TinkerSmeltery.searedTable, TinkerSmeltery.searedBasin, TinkerSmeltery.scorchedTable, TinkerSmeltery.scorchedBasin);
    add(registry, new EntityCategory(ENTITY_MELTING, title("entity_melting", "Entity Melting"), LazyIcon.texture(ReiLayout.MELTING, 174, 41), false, fuels),
      TinkerSmeltery.smelteryController, TinkerSmeltery.foundryController);
    add(registry, new TinkerStationCategory(MODIFIERS, title("modifiers", "Modifiers"),
        LazyIcon.item(() -> CreativeSlotItem.withSlot(new ItemStack(TinkerModifiers.creativeSlotItem), SlotType.UPGRADE), TinkerModifiers.creativeSlotItem), true),
      TinkerTables.tinkerStation, TinkerTables.tinkersAnvil, TinkerTables.scorchedAnvil);
    // The station is an icon here, not a workstation for the mob-drop mechanic.
    registry.add(new EntityCategory(SEVERING, title("severing", "Severing"), LazyIcon.item(() -> TinkerTools.cleaver.get().getRenderTool(), TinkerTools.cleaver), true, fuels));
    registry.removePlusButton(SEVERING);
    add(registry, new ToolBuildingCategory(TOOL_BUILDING, Component.translatable("jei.tconstruct.tinkering.tool_building"),
        LazyIcon.item(() -> TinkerTools.pickaxe.get().getRenderTool(), TinkerTools.pickaxe)),
      TinkerTables.tinkerStation, TinkerTables.tinkersAnvil, TinkerTables.scorchedAnvil);
    add(registry, new TinkerStationCategory(TOOL_TINKERING, title("tool_tinkering", "Tool Tinkering"), EntryStacks.of(TinkerTables.tinkersAnvil), false),
      TinkerTables.tinkerStation, TinkerTables.tinkersAnvil, TinkerTables.scorchedAnvil);
    add(registry, new MaterialCategory(MATERIALS, title("materials", "Materials"),
        LazyIcon.item(() -> TinkerToolParts.largePlate.get().withMaterialForDisplay(MaterialIds.cobalt), TinkerToolParts.largePlate)),
      TinkerTables.partBuilder);
    add(registry, new PartBuilderCategory(PART_BUILDER, title("part_builder", "Part Builder"), TinkerTables.partBuilder), TinkerTables.partBuilder);
    add(registry, new WorktableCategory(WORKTABLE, Component.translatable("jei.tconstruct.modifier_worktable.title"), TinkerTables.modifierWorktable),
      TinkerTables.modifierWorktable);
    registerToolWorkstations(registry);
  }

  /**
   * Official catalysts beyond the tables (JEIPlugin.registerRecipeCatalysts): the heater for vanilla fuel, modifiers in
   * the {@code jei/*} modifier tags, and every tool whose traits carry one of those modifiers. Tool traits and modifier
   * tags come from synchronized data, so this is only complete when REI reloads in a connected world; any failure is
   * logged and leaves the table workstations in place.
   */
  private static void registerToolWorkstations(CategoryRegistry registry) {
    registry.addWorkstations(me.shedaniel.rei.plugin.common.BuiltinPlugin.FUEL, EntryStacks.of(TinkerSmeltery.searedHeater));
    // official addTableCatalyst(craftingStation, LOGS, true, CRAFTING); retextured variants are hidden entries in REI
    registry.addWorkstations(me.shedaniel.rei.plugin.common.BuiltinPlugin.CRAFTING, EntryStacks.of(TinkerTables.craftingStation));
    try {
      addModifierWorkstations(registry, slimeknights.tconstruct.common.TinkerTags.Modifiers.CRAFTING, me.shedaniel.rei.plugin.common.BuiltinPlugin.CRAFTING);
      addModifierWorkstations(registry, slimeknights.tconstruct.common.TinkerTags.Modifiers.SMELTING, me.shedaniel.rei.plugin.common.BuiltinPlugin.SMELTING);
      addModifierWorkstations(registry, slimeknights.tconstruct.common.TinkerTags.Modifiers.SEVERING, SEVERING);
      addModifierWorkstations(registry, slimeknights.tconstruct.common.TinkerTags.Modifiers.MELTING, MELTING, ENTITY_MELTING);
      for (var entry : slimeknights.tconstruct.library.client.recipe.RecipeWorkstations.all().entrySet()) {
        if (!(entry.getKey() instanceof slimeknights.tconstruct.library.tools.item.IModifiableDisplay modifiable)) continue;
        EntryStack<?> tool = EntryStacks.of(modifiable.getRenderTool());
        for (var station : entry.getValue()) {
          switch (station) {
            case CRAFTING -> registry.addWorkstations(me.shedaniel.rei.plugin.common.BuiltinPlugin.CRAFTING, tool);
            case SMELTING -> registry.addWorkstations(me.shedaniel.rei.plugin.common.BuiltinPlugin.SMELTING, tool);
            case SEVERING -> registry.addWorkstations(SEVERING, tool);
            case MELTING -> registry.addWorkstations(MELTING, tool);
            case ENTITY_MELTING -> registry.addWorkstations(ENTITY_MELTING, tool);
          }
        }
      }
    } catch (RuntimeException exception) {
      TConstruct.LOG.warn("Could not register tool and modifier workstations for REI; table workstations remain", exception);
    }
  }

  /** Adds every modifier in the tag as a workstation entry, like official addModifierCatalyst. */
  private static void addModifierWorkstations(CategoryRegistry registry, net.minecraft.tags.TagKey<slimeknights.tconstruct.library.modifiers.Modifier> tag,
                                              CategoryIdentifier<?>... categories) {
    for (var modifier : slimeknights.tconstruct.library.modifiers.ModifierManager.getTagValues(tag)) {
      EntryStack<?> entry = TinkerEntryTypes.entry(new slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.ModifierValue(
        new slimeknights.tconstruct.library.modifiers.ModifierEntry(modifier, 1)));
      for (CategoryIdentifier<?> category : categories) {
        registry.addWorkstations(category, entry);
      }
    }
  }

  /** Registers a layout category with its workstations. The "+" button stays removed until a transfer handler is registered. */
  private static void add(CategoryRegistry registry, TinkerDisplayCategory category, ItemLike... workstations) {
    registry.add(category);
    for (ItemLike item : workstations) {
      registry.addWorkstations(category.getCategoryIdentifier(), EntryStacks.of(item));
    }
    if (!TRANSFER_CATEGORIES.contains(category.getCategoryIdentifier())) {
      registry.removePlusButton(category.getCategoryIdentifier());
    }
  }

  /**
   * Categories whose "+" button is backed by a conservation-tested transfer handler, like official JEI's tinker station
   * transfer for modifiers and tool building. Every other Tinkers category keeps its "+" button removed.
   */
  static final java.util.Set<CategoryIdentifier<?>> TRANSFER_CATEGORIES = java.util.Set.of(MODIFIERS, TOOL_BUILDING);

  /**
   * Hides filled tanks, gauges, lanterns and cans unless the showFilledFluidTanks client config asks for them, matching
   * official JEI's hidden fluids tab (REI does not read creative tab tags). Evaluated by REI's reload, which runs again
   * once a world is joined and tags are bound; before that nothing is hidden.
   */
  @Override
  public void registerBasicEntryFiltering(me.shedaniel.rei.api.client.entry.filtering.base.BasicFilteringRule<?> rule) {
    // parity (oracle): keep the minotaur axe out of the viewer without Twilight Forest (see registerEntries)
    if (!isMinotaurAxeActive()) {
      rule.hide(() -> List.of(EntryStacks.of(new ItemStack(slimeknights.tconstruct.tools.TinkerTools.minotaurAxe.get()))));
    }
    boolean showFilled;
    try {
      showFilled = slimeknights.tconstruct.common.config.Config.CLIENT.showFilledFluidTanks.get();
    } catch (RuntimeException exception) {
      showFilled = true; // config not loaded: change nothing
    }
    if (showFilled) return;
    rule.hide(() -> {
      if (Minecraft.getInstance().level == null) return List.of();
      List<EntryStack<?>> hidden = new java.util.ArrayList<>();
      for (ItemStack stack : slimeknights.tconstruct.library.client.recipe.RecipeViewerHiding.filledContainers()) {
        hidden.add(EntryStacks.of(stack));
      }
      return hidden;
    });
  }

  /** Crafting station, tool inventory crafting and tinker station transfer, sent as ordinary menu clicks. */
  @Override
  public void registerTransferHandlers(me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry registry) {
    registry.register(new slimeknights.tconstruct.plugin.rei.transfer.TinkerTransferHandler(MODIFIERS, TRANSFER_CATEGORIES));
  }

  /** Fuel facts for the layouts from the latest expanded snapshot. */
  private static FuelFacts fuelFacts() {
    SmelteryDisplayGenerator.FuelInfo info = RECIPES.fuelInfo();
    return new FuelFacts() {
      @Override public List<net.neoforged.neoforge.fluids.FluidStack> usableFuels(int temperature) { return info.usable(temperature); }
      @Override public boolean hasSolid() { return info.solid() != null; }
      @Override public int solidTemperature() { return info.solid() == null ? 0 : info.solid().getTemperature(); }
      @Override public int solidRate() { return info.solid() == null ? 0 : info.solid().getRate(); }
      @Override public List<ItemStack> solidExamples() { return info.examples(); }
      @Override public int[] liquidFuel(net.neoforged.neoforge.fluids.FluidStack fluid) {
        for (var fuel : info.fuels()) {
          if (fuel.getInput() != slimeknights.mantle.recipe.ingredient.FluidIngredient.EMPTY && fuel.matches(fluid.getFluid())) {
            return new int[] {fuel.getTemperature(), fuel.getRate()};
          }
        }
        return null;
      }
    };
  }

  /** Original generic registration from arthur.3, kept unused; every category now has an official layout. */
  @SuppressWarnings("unused")
  private static void registerGenericCategories(CategoryRegistry registry) {
    add(registry, CASTING_BASIN, Component.translatable("jei.tconstruct.casting.basin"), TinkerSmeltery.searedBasin, TinkerSmeltery.scorchedBasin);
    add(registry, CASTING_TABLE, Component.translatable("jei.tconstruct.casting.table"), TinkerSmeltery.searedTable, TinkerSmeltery.scorchedTable);
    add(registry, MELTING, Component.translatable("jei.tconstruct.melting.title"), TinkerSmeltery.searedMelter, TinkerSmeltery.smelteryController);
    add(registry, FOUNDRY, Component.translatable("jei.tconstruct.foundry.title"), TinkerSmeltery.foundryController);
    add(registry, ALLOY, Component.translatable("jei.tconstruct.alloy.title"), TinkerSmeltery.smelteryController, TinkerSmeltery.scorchedAlloyer);
    add(registry, FUEL, Component.translatableWithFallback("jei.tconstruct.fuel.title", "Fuel"), TinkerSmeltery.searedHeater,
      TinkerSmeltery.searedMelter, TinkerSmeltery.smelteryController, TinkerSmeltery.foundryController, TinkerSmeltery.scorchedAlloyer);
    add(registry, MOLDING, title("molding", "Molding"), TinkerSmeltery.searedTable, TinkerSmeltery.searedBasin, TinkerSmeltery.scorchedTable, TinkerSmeltery.scorchedBasin);
    add(registry, ENTITY_MELTING, title("entity_melting", "Entity Melting"), TinkerSmeltery.smelteryController, TinkerSmeltery.foundryController);
    add(registry, MODIFIERS, title("modifiers", "Modifiers"), TinkerTables.tinkerStation, TinkerTables.tinkersAnvil, TinkerTables.scorchedAnvil);
    // The station is an icon here, not a workstation for the mob-drop mechanic.
    registry.add(new SmelteryCategory(SEVERING, title("severing", "Severing"), TinkerTables.tinkerStation));
    registry.removePlusButton(SEVERING);
    add(registry, TOOL_BUILDING, title("tool_building", "Tool Building"), TinkerTables.tinkerStation, TinkerTables.tinkersAnvil, TinkerTables.scorchedAnvil);
    add(registry, TOOL_TINKERING, title("tool_tinkering", "Tool Tinkering"), TinkerTables.tinkerStation, TinkerTables.tinkersAnvil, TinkerTables.scorchedAnvil);
    add(registry, MATERIALS, title("materials", "Materials"), TinkerTables.partBuilder);
    add(registry, PART_BUILDER, title("part_builder", "Part Builder"), TinkerTables.partBuilder);
    add(registry, WORKTABLE, title("worktable", "Modifier Worktable"), TinkerTables.modifierWorktable);
  }

  private static Component title(String category, String fallback) {
    return Component.translatableWithFallback("jei.tconstruct." + category + ".title", fallback);
  }

  private static void add(CategoryRegistry registry, CategoryIdentifier<SmelteryDisplay> id, Component title, ItemLike first, ItemLike... others) {
    registry.add(new SmelteryCategory(id, title, first));
    registry.addWorkstations(id, EntryStacks.of(first));
    for (ItemLike item : others) {
      registry.addWorkstations(id, EntryStacks.of(item));
    }
    // Inventory transfer must be implemented against each menu's actual transaction contract.
    registry.removePlusButton(id);
  }

  @Override
  public void registerDisplays(DisplayRegistry registry) {
    for (var category : CATEGORIES) {
      registry.registerDisplayGenerator(category, new SmelteryDisplayGenerator(category, RECIPES));
    }
  }

  @Override
  public void registerEntries(EntryRegistry registry) {
    entries = registry;
    ENTRY_REFRESH.reset();
    // parity (oracle): official registers the minotaur axe only with Twilight Forest, so it never reaches a recipe viewer
    // without it. The port keeps the item registered for world safety, so hide it here instead.
    if (!isMinotaurAxeActive()) {
      registry.removeEntryIf(entry -> entry.getValue() instanceof ItemStack stack && stack.is(slimeknights.tconstruct.tools.TinkerTools.minotaurAxe.get()));
    }
  }

  /** The minotaur axe is only "present" when Twilight Forest is loaded, matching official registration */
  private static boolean isMinotaurAxeActive() {
    return net.neoforged.fml.ModList.get().isLoaded("twilightforest");
  }

  private static void clientTick(ClientTickEvent.Post event) {
    EntryRegistry registry = entries;
    if (registry == null) return;
    // Match REI's own display tick boundary: the task counter covers the gaps
    // between plugin phases, when manager flags alone are already false.
    boolean reloadBusy = registry.isReloading() || PluginManager.areAnyReloading()
      || ReloadManagerImpl.countRunningReloadTasks() > 0;
    var snapshot = ClientRecipeCache.getSnapshot();
    boolean ready = Minecraft.getInstance().level != null && snapshot.recipes() != RecipeMap.EMPTY && MaterialRegistry.isFullyLoaded();
    // REI's tag filters need a connected world. Never refilter from a title-screen
    // tick, incomplete synchronization, or logout. Only our custom entries change.
    ENTRY_REFRESH.refresh(reloadBusy, ready, snapshot.revision(), () -> registry.removeEntryIf(TinkerEntryTypes::isOwned), () -> {
      var unique = new LinkedHashSet<EntryStack<?>>();
      for (SmelteryDisplay display : RECIPES.all()) {
        java.util.stream.Stream.concat(display.getRequiredEntries().stream(), display.outputs().stream())
          .flatMap(List::stream).filter(TinkerEntryTypes::isOwned).map(EntryStack::normalize).forEach(unique::add);
      }
      registry.addEntries(unique);
    }, registry::refilter);
  }
}
