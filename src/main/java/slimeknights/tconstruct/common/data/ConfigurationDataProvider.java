package slimeknights.tconstruct.common.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.Tags;
import slimeknights.mantle.command.RemoveRecipesCommand;
import slimeknights.mantle.data.GenericDataProvider;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.data.predicate.item.ItemPredicate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.json.predicate.TinkerPredicate;
import slimeknights.tconstruct.shared.command.subcommand.GenerateMeltingRecipesCommand;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Data generator for someone-off JSON files used for command configuration */
public class ConfigurationDataProvider extends GenericDataProvider {
  private final Map<Identifier, JsonObject> configuration = new LinkedHashMap<>();
  /** Pack output used to resolve each configuration file path, see {@link #saveConfig(CachedOutput, Identifier, JsonObject)} */
  private final PackOutput packOutput;
  public ConfigurationDataProvider(PackOutput output) {
    super(output, Target.DATA_PACK, "");
    this.packOutput = output;
  }

  @Override
  public CompletableFuture<?> run(CachedOutput output) {
    // config for which items to generate melting recipes for and which items to ignore
    JsonObject meltingRecipes = config(GenerateMeltingRecipesCommand.MELTING_CONFIGURATION);
    item(meltingRecipes, "melt", ItemPredicate.or(
      ItemPredicate.tag(TinkerTags.Items.MODIFIABLE),
      ItemPredicate.tag(TinkerTags.Items.BOOKS),
      ItemPredicate.tag(Tags.Items.DYES),
      ItemPredicate.HAS_CONTAINER
    ).inverted());
    item(meltingRecipes, "inputs", ItemPredicate.ANY);
    item(meltingRecipes, "ignore", ItemPredicate.ANY);
    meltingRecipes.add("skip_recipes", new JsonArray());

    // recipe removal preset that makes ingots not smeltable in furnaces
    JsonObject removeIngots = removePreset("ingot_smelting");
    item(removeIngots, "result", ItemPredicate.and(
      ItemPredicate.tag(Tags.Items.INGOTS),
      ItemPredicate.set(Items.BRICK, TinkerSmeltery.searedBrick.get(), TinkerSmeltery.scorchedBrick.get()).inverted(),
      TinkerPredicate.CASTABLE
    ));
    item(removeIngots, "input", ItemPredicate.ANY);
    recipeType(removeIngots, RecipeType.SMELTING, RecipeType.BLASTING);

    // recipe removal preset that makes nuggets not smeltable in furnaces, recycle in the smeltery!
    JsonObject removeNuggets = removePreset("nugget_smelting");
    item(removeNuggets, "result", ItemPredicate.and(
      ItemPredicate.tag(Tags.Items.NUGGETS),
      TinkerPredicate.CASTABLE
    ));
    item(removeNuggets, "input", ItemPredicate.ANY);
    recipeType(removeNuggets, RecipeType.SMELTING, RecipeType.BLASTING);

    // preset to remove vanilla tool crafting
    JsonObject removeVanillaTools = removePreset("vanilla_tools");
    item(removeVanillaTools, "result", ItemPredicate.and(
      ItemPredicate.or(
        ItemPredicate.tag(ItemTags.PICKAXES),
        ItemPredicate.tag(ItemTags.AXES),
        ItemPredicate.tag(ItemTags.SHOVELS),
        ItemPredicate.tag(ItemTags.SWORDS),
        ItemPredicate.tag(ItemTags.HOES),
        // parity/materials: official 3.12.1 lists shields, bows, crossbows and fishing rods here (NeoForge 26.1 names).
        // The broader durability_enchantable tag also removed mace and carrot/fungus-on-a-stick crafting.
        ItemPredicate.tag(Tags.Items.TOOLS_SHIELD),
        ItemPredicate.tag(Tags.Items.TOOLS_BOW),
        ItemPredicate.tag(Tags.Items.TOOLS_CROSSBOW),
        ItemPredicate.tag(Tags.Items.TOOLS_FISHING_ROD),
        ItemPredicate.tag(Tags.Items.ARMORS),
        ItemPredicate.set(Items.FLINT_AND_STEEL, Items.SHEARS, Items.BRUSH)
      ),
      ItemPredicate.tag(TinkerTags.Items.MODIFIABLE).inverted()
    ));
    item(removeVanillaTools, "input", ItemPredicate.ANY);
    recipeType(removeVanillaTools, RecipeType.CRAFTING);

    // preset to remove netherite smithing
    JsonObject removeNetheriteSmithing = removePreset("netherite_smithing");
    item(removeNetheriteSmithing, "result", ItemPredicate.set(
      Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_SWORD, Items.NETHERITE_HOE,
      Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS
    ));
    item(removeNetheriteSmithing, "input", ItemPredicate.ANY);
    recipeType(removeNetheriteSmithing, RecipeType.SMITHING);

    // save all JSON
    // arthur.8: GenericDataProvider was given an empty folder, and 26.1 PackOutput.PathProvider builds kind + "/" + path,
    // so every file got an absolute "/command/..." or "/mantle/..." path that resolved against the drive root
    // (D:\command, D:\mantle) instead of the pack output, and runData then deleted the committed presets as stale.
    // Previous line, kept for reference:
    // return allOf(configuration.entrySet().stream().map(entry -> saveJson(output, entry.getKey(), entry.getValue())));
    return allOf(configuration.entrySet().stream().map(entry -> saveConfig(output, entry.getKey(), entry.getValue())));
  }

  /**
   * Saves one configuration file under data/namespace/path.json of this pack output. The first path segment is used
   * as the path provider kind so the kind is never empty (see the note in {@link #run(CachedOutput)}).
   * @param output    Cached output
   * @param location  Location without extension, for example tconstruct:mantle/remove_recipes/ingot_smelting
   * @param json      File contents
   * @return  Save task
   */
  private CompletableFuture<?> saveConfig(CachedOutput output, Identifier location, JsonObject json) {
    String path = location.getPath();
    int slash = path.indexOf('/');
    Path target = slash > 0
      ? packOutput.createPathProvider(Target.DATA_PACK, path.substring(0, slash)).json(location.withPath(path.substring(slash + 1)))
      : packOutput.getOutputFolder(Target.DATA_PACK).resolve(location.getNamespace()).resolve(path + ".json");
    return DataProvider.saveStable(output, json, target);
  }

  @Override
  public String getName() {
    return "Continuum Construct Configuration Data Provider";
  }

  /** Gets or creates a config object */
  private JsonObject config(Identifier location) {
    String path = location.getPath();
    return configuration.computeIfAbsent(location.withPath(path.substring(0, path.length() - ".json".length())), p -> new JsonObject());
  }

  /** Gets or creates a config object for a recipe removal preset */
  private JsonObject removePreset(String name) {
    return config(RemoveRecipesCommand.PRESETS.idToFile(TConstruct.getResource(name)));
  }

  /** Adds an item predicate */
  private static void item(JsonObject json, String key, IJsonPredicate<Item> value) {
    json.add(key, ItemPredicate.LOADER.serialize(value));
  }

  /** Adds recipe types */
  private static void recipeType(JsonObject json, RecipeType<?>... types) {
    json.add("recipe_type", RemoveRecipesCommand.RECIPE_TYPES.serialize(List.of(types)));
  }
}
