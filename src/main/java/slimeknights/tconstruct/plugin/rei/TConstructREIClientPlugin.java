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

  @Override
  public void registerCategories(CategoryRegistry registry) {
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
