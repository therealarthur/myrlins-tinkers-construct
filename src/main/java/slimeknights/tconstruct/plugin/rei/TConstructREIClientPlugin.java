package slimeknights.tconstruct.plugin.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

import java.util.List;

@REIPluginClient
public class TConstructREIClientPlugin implements REIClientPlugin {
  static final CategoryIdentifier<SmelteryDisplay> CASTING_BASIN = category("casting_basin");
  static final CategoryIdentifier<SmelteryDisplay> CASTING_TABLE = category("casting_table");
  static final CategoryIdentifier<SmelteryDisplay> MELTING = category("melting");
  static final CategoryIdentifier<SmelteryDisplay> FOUNDRY = category("foundry");
  static final CategoryIdentifier<SmelteryDisplay> ALLOY = category("alloy");
  static final CategoryIdentifier<SmelteryDisplay> FUEL = category("fuel");
  static final List<CategoryIdentifier<SmelteryDisplay>> CATEGORIES = List.of(CASTING_BASIN, CASTING_TABLE, MELTING, FOUNDRY, ALLOY, FUEL);

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
  }

  private static void add(CategoryRegistry registry, CategoryIdentifier<SmelteryDisplay> id, Component title, ItemLike first, ItemLike... others) {
    registry.add(new SmelteryCategory(id, title, first));
    registry.addWorkstations(id, EntryStacks.of(first));
    for (ItemLike item : others) {
      registry.addWorkstations(id, EntryStacks.of(item));
    }
    // These machines consume fluids in-world. They have no safe inventory recipe-transfer path.
    registry.removePlusButton(id);
  }

  @Override
  public void registerDisplays(DisplayRegistry registry) {
    SmelteryDisplayGenerator.Recipes recipes = new SmelteryDisplayGenerator.Recipes();
    for (var category : CATEGORIES) {
      registry.registerDisplayGenerator(category, new SmelteryDisplayGenerator(category, recipes));
    }
  }
}
