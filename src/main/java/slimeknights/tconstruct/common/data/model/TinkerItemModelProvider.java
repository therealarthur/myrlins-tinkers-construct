package slimeknights.tconstruct.common.data.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;
import slimeknights.tconstruct.library.tools.definition.ModifiableArmorMaterial;
import net.minecraft.world.level.ItemLike;
import slimeknights.mantle.data.GenericDataProvider;
import slimeknights.mantle.registration.object.ItemObject;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.registration.CastItemObject;
import slimeknights.tconstruct.library.tools.part.MaterialItem;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.world.block.FoliageType;
import slimeknights.tconstruct.world.TinkerHeadType;
import slimeknights.tconstruct.world.TinkerWorld;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;

/** Generates item models that replaced the old Forge item model generator pass. */
public class TinkerItemModelProvider implements DataProvider {
  private final PackOutput.PathProvider itemModels;
  private final PackOutput.PathProvider itemDefinitions;
  private final Set<Identifier> itemDefinitionIds = new HashSet<>();
  private final Path mainItemModelRoot;

  public TinkerItemModelProvider(PackOutput output) {
    this.itemModels = output.createPathProvider(Target.RESOURCE_PACK, "models/item");
    this.itemDefinitions = output.createPathProvider(Target.RESOURCE_PACK, "items");
    Path outputFolder = output.getOutputFolder();
    Path projectRoot = outputFolder.getParent().getParent().getParent();
    this.mainItemModelRoot = projectRoot.resolve("src/main/resources/assets/tconstruct/models/item");
  }

  @Override
  public CompletableFuture<?> run(CachedOutput cache) {
    List<CompletableFuture<?>> tasks = new ArrayList<>();

    part(tasks, cache, TinkerToolParts.pickHead, "pickaxe/head", -2, 1);
    part(tasks, cache, TinkerToolParts.hammerHead, "sledge_hammer/head", -3, 3);
    part(tasks, cache, TinkerToolParts.smallAxeHead, "hand_axe/head", -2, 3);
    part(tasks, cache, TinkerToolParts.broadAxeHead, "broad_axe/blade", 0, 3);
    part(tasks, cache, TinkerToolParts.smallBlade);
    part(tasks, cache, TinkerToolParts.broadBlade, "cleaver/head", -1, 1);
    part(tasks, cache, TinkerToolParts.adzeHead, "pickadze/adze", -5, 1);
    part(tasks, cache, TinkerToolParts.largePlate);
    part(tasks, cache, TinkerToolParts.bowLimb, "longbow/limb_bottom", 5, -2);
    part(tasks, cache, TinkerToolParts.bowGrip, "crossbow/body", -2, -2);
    part(tasks, cache, TinkerToolParts.bowstring);
    part(tasks, cache, TinkerToolParts.arrowHead, "ammo/arrow_head", -4, 3);
    part(tasks, cache, TinkerToolParts.arrowShaft, "ammo/arrow_shaft", 1, -1);
    part(tasks, cache, TinkerToolParts.fletching, "ammo/arrow_feather", 4, -5);
    part(tasks, cache, TinkerToolParts.toolBinding);
    part(tasks, cache, TinkerToolParts.toolHandle);
    part(tasks, cache, TinkerToolParts.toughHandle);
    part(tasks, cache, TinkerToolParts.toughBinding);
    part(tasks, cache, TinkerToolParts.repairKit);
    part(tasks, cache, TinkerToolParts.fakeIngot, "parts/ingot");
    for (ArmorType slot : ModifiableArmorMaterial.ARMOR_TYPES) {
      ItemLike plating = TinkerToolParts.plating.get(slot);
      if (slot == ArmorType.HELMET) {
        part(tasks, cache, plating, "armor/plate/" + slot.getName() + "/plating", 0, 2);
      } else if (slot == ArmorType.LEGGINGS) {
        part(tasks, cache, plating, "armor/plate/" + slot.getName() + "/plating", 0, 1);
      } else {
        part(tasks, cache, plating, "armor/plate/" + slot.getName() + "/plating");
      }
    }
    part(tasks, cache, TinkerToolParts.maille);
    part(tasks, cache, TinkerToolParts.shieldCore, "armor/plate/shield/core");
    part(tasks, cache, TinkerToolParts.ribcage, "armor/slime/chestplate/ribcage", 0, 3);
    part(tasks, cache, TinkerToolParts.shell, "armor/slime/leggings/shell", 0, -1);
    part(tasks, cache, TinkerToolParts.laces, "armor/slime/boots/laces", 0, 1);

    generated(tasks, cache, TinkerSmeltery.copperGauge, "block/smeltery/io/gauge");
    generated(tasks, cache, TinkerSmeltery.obsidianGauge, "block/foundry/io/gauge");
    basic(tasks, cache, TinkerSmeltery.blankSandCast, "sand_cast/blank");
    basic(tasks, cache, TinkerSmeltery.blankRedSandCast, "red_sand_cast/blank");
    cast(tasks, cache, TinkerSmeltery.ingotCast);
    cast(tasks, cache, TinkerSmeltery.nuggetCast);
    cast(tasks, cache, TinkerSmeltery.gemCast);
    cast(tasks, cache, TinkerSmeltery.rodCast);
    cast(tasks, cache, TinkerSmeltery.repairKitCast);
    cast(tasks, cache, TinkerSmeltery.plateCast);
    cast(tasks, cache, TinkerSmeltery.gearCast);
    cast(tasks, cache, TinkerSmeltery.coinCast);
    cast(tasks, cache, TinkerSmeltery.wireCast);
    cast(tasks, cache, TinkerSmeltery.pickHeadCast);
    cast(tasks, cache, TinkerSmeltery.smallAxeHeadCast);
    cast(tasks, cache, TinkerSmeltery.smallBladeCast);
    cast(tasks, cache, TinkerSmeltery.adzeHeadCast);
    cast(tasks, cache, TinkerSmeltery.hammerHeadCast);
    cast(tasks, cache, TinkerSmeltery.broadBladeCast);
    cast(tasks, cache, TinkerSmeltery.broadAxeHeadCast);
    cast(tasks, cache, TinkerSmeltery.largePlateCast);
    cast(tasks, cache, TinkerSmeltery.toolBindingCast);
    cast(tasks, cache, TinkerSmeltery.toughBindingCast);
    cast(tasks, cache, TinkerSmeltery.toolHandleCast);
    cast(tasks, cache, TinkerSmeltery.toughHandleCast);
    cast(tasks, cache, TinkerSmeltery.bowLimbCast);
    cast(tasks, cache, TinkerSmeltery.bowGripCast);
    basic(tasks, cache, TinkerSmeltery.arrowCast.getId(), "cast/arrow");
    cast(tasks, cache, TinkerSmeltery.helmetPlatingCast);
    cast(tasks, cache, TinkerSmeltery.chestplatePlatingCast);
    cast(tasks, cache, TinkerSmeltery.leggingsPlatingCast);
    cast(tasks, cache, TinkerSmeltery.bootsPlatingCast);
    cast(tasks, cache, TinkerSmeltery.mailleCast);
    for (ArmorType type : ModifiableArmorMaterial.ARMOR_TYPES) {
      String texture = type == ArmorType.BODY ? "tool/parts/plating_chestplate" : "tool/parts/plating_" + type.getName();
      basic(tasks, cache, TinkerSmeltery.dummyPlating.get(type), texture);
    }

    basic(tasks, cache, TinkerWorld.steelShard, "materials/steel_shard");
    basic(tasks, cache, TinkerWorld.cobaltShard, "materials/cobalt_shard");
    basic(tasks, cache, TinkerWorld.knightmetalShard, "materials/knightmetal_shard");
    basic(tasks, cache, TinkerModifiers.ballOfMoss, "ball_of_moss");
    slimeCrystal(tasks, cache, TinkerWorld.earthGeode, "earth");
    slimeCrystal(tasks, cache, TinkerWorld.skyGeode, "sky");
    slimeCrystal(tasks, cache, TinkerWorld.ichorGeode, "ichor");
    slimeCrystal(tasks, cache, TinkerWorld.enderGeode, "ender");
    generated(tasks, cache, TinkerWorld.steelCluster, "block/geode/steel_cluster");
    generated(tasks, cache, TinkerWorld.cobaltCluster, "block/geode/cobalt_cluster");
    generated(tasks, cache, TinkerWorld.knightmetalCluster, "block/geode/knightmetal_cluster");
    TinkerWorld.headItems.forEach(head -> {
      Identifier id = key(head);
      String path = id.getPath();
      String typeName = path.substring(0, path.length() - "_head".length());
      TinkerHeadType type = TinkerHeadType.valueOf(typeName.toUpperCase(java.util.Locale.ROOT));
      headItemModel(tasks, cache, id, type);
    });
    creativeSlot(tasks, cache);
    bridgeExistingItemModels(tasks, cache);

    return GenericDataProvider.allOf(tasks);
  }

  /** Textures each built in slot type uses, keyed by the slot name written into custom data */
  private static final List<String[]> CREATIVE_SLOT_TEXTURES = List.of(
    new String[] {"upgrades",  "tconstruct:item/slot/upgrade"},
    new String[] {"abilities", "tconstruct:item/slot/ability"},
    new String[] {"defense",   "tconstruct:item/slot/defense"},
    new String[] {"souls",     "tconstruct:item/materials/hollow_gem"},
    new String[] {"slotless",  "tconstruct:item/slot/slotless"});

  /**
   * Builds the creative modifier slot item, which picks its texture from the slot it grants.
   * The 1.20 version used a Mantle model loader for this; 26.1 dispatches item models up front instead, so the
   * built in slots each get a model and anything a datapack adds falls back to the blank slot outline.
   */
  private void creativeSlot(List<CompletableFuture<?>> tasks, CachedOutput cache) {
    JsonArray cases = new JsonArray();
    for (String[] slot : CREATIVE_SLOT_TEXTURES) {
      Identifier model = TConstruct.getResource("item/slot/" + slot[0]);
      JsonObject json = parent("minecraft:item/generated");
      JsonObject textures = new JsonObject();
      textures.addProperty("layer0", slot[1]);
      json.add("textures", textures);
      tasks.add(save(cache, itemModels.json(TConstruct.getResource("slot/" + slot[0])), json));

      JsonObject switchCase = new JsonObject();
      switchCase.addProperty("when", slot[0]);
      switchCase.add("model", modelReference(model.toString()));
      cases.add(switchCase);
    }

    JsonObject select = new JsonObject();
    select.addProperty("type", "minecraft:select");
    select.addProperty("property", "tconstruct:slot_type");
    select.add("cases", cases);
    select.add("fallback", modelReference("tconstruct:item/creative_slot"));
    JsonObject definition = new JsonObject();
    definition.add("model", select);

    // claim the ID so the bridge below leaves the hand written model alone, it is only the select fallback now
    Identifier id = TConstruct.getResource("creative_slot");
    this.itemDefinitionIds.add(id);
    tasks.add(save(cache, itemDefinitions.json(id), definition));
  }

  private static JsonObject modelReference(String model) {
    JsonObject json = new JsonObject();
    json.addProperty("type", "minecraft:model");
    json.addProperty("model", model);
    return json;
  }

  private void part(List<CompletableFuture<?>> tasks, CachedOutput cache, ItemObject<? extends MaterialItem> part) {
    part(tasks, cache, part.get(), part.getId(), "parts/" + part.getId().getPath(), 0, 0, false);
  }

  private void part(List<CompletableFuture<?>> tasks, CachedOutput cache, ItemObject<? extends MaterialItem> part, String texture) {
    part(tasks, cache, part.get(), part.getId(), texture, 0, 0, false);
  }

  private void part(List<CompletableFuture<?>> tasks, CachedOutput cache, ItemObject<? extends MaterialItem> part, String texture, int x, int y) {
    part(tasks, cache, part.get(), part.getId(), texture, x, y, true);
  }

  private void part(List<CompletableFuture<?>> tasks, CachedOutput cache, ItemLike part, String texture) {
    part(tasks, cache, part, key(part), texture, 0, 0, false);
  }

  private void part(List<CompletableFuture<?>> tasks, CachedOutput cache, ItemLike part, String texture, int x, int y) {
    part(tasks, cache, part, key(part), texture, x, y, true);
  }

  private void part(List<CompletableFuture<?>> tasks, CachedOutput cache, ItemLike part, Identifier id, String texture, int x, int y, boolean hasOffset) {
    JsonObject json = parent("forge:item/default");
    json.addProperty("loader", "tconstruct:material");
    JsonObject textures = new JsonObject();
    textures.addProperty("texture", "tconstruct:item/tool/" + texture);
    json.add("textures", textures);
    if (hasOffset) {
      JsonArray offset = new JsonArray();
      offset.add(x);
      offset.add(y);
      json.add("offset", offset);
    }
    tasks.add(save(cache, itemModels.json(id), json));
    this.itemDefinitionIds.add(id);
    tasks.add(save(cache, itemDefinitions.json(id), materialItemDefinition("tconstruct:item/tool/" + texture, x, y, hasOffset)));
  }
  private void generated(List<CompletableFuture<?>> tasks, CachedOutput cache, ItemLike item, String texture) {
    generated(tasks, cache, key(item), texture);
  }

  private void generated(List<CompletableFuture<?>> tasks, CachedOutput cache, Identifier item, String texture) {
    JsonObject json = parent("minecraft:item/generated");
    JsonObject textures = new JsonObject();
    textures.addProperty("layer0", "tconstruct:" + texture);
    json.add("textures", textures);
    itemModel(tasks, cache, item, json);
  }

  private void basic(List<CompletableFuture<?>> tasks, CachedOutput cache, ItemLike item, String texture) {
    basic(tasks, cache, key(item), texture);
  }

  private void basic(List<CompletableFuture<?>> tasks, CachedOutput cache, Identifier item, String texture) {
    generated(tasks, cache, item, "item/" + texture);
  }

  private void slimeCrystal(List<CompletableFuture<?>> tasks, CachedOutput cache, ItemLike item, String color) {
    basic(tasks, cache, item, "materials/" + color + "_slime_crystal");
  }

  private void bridgeExistingItemModels(List<CompletableFuture<?>> tasks, CachedOutput cache) {
    bridgeExistingItemModels(tasks, cache, this.mainItemModelRoot);
  }

  private void bridgeExistingItemModels(List<CompletableFuture<?>> tasks, CachedOutput cache, Path root) {
    if (!Files.isDirectory(root)) {
      return;
    }
    try (var stream = Files.walk(root)) {
      stream.filter(Files::isRegularFile)
            .filter(path -> path.getFileName().toString().endsWith(".json"))
            .forEach(path -> {
              String relative = root.relativize(path).toString().replace('\\', '/');
              String model = relative.substring(0, relative.length() - ".json".length());
              Identifier id = TConstruct.getResource(model);
              if (this.itemDefinitionIds.add(id)) {
                JsonObject sourceModel;
                try {
                  sourceModel = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                } catch (IOException e) {
                  throw new UncheckedIOException("Failed to read item model " + path, e);
                }
                        if (sourceModel.has("loader") && "tconstruct:tool".equals(sourceModel.get("loader").getAsString())) {
          itemDefinitionIds.remove(id);
        } else if (sourceModel.has("loader") && "tconstruct:fluid_container".equals(sourceModel.get("loader").getAsString())) {
          tasks.add(save(cache, itemDefinitions.json(id), fluidContainerItemDefinition(sourceModel)));
        } else {
          tasks.add(save(cache, itemDefinitions.json(id), itemDefinition(id)));
        }
              }
            });
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to bridge existing item models in " + root, e);
    }
  }
  private void cast(List<CompletableFuture<?>> tasks, CachedOutput cache, CastItemObject cast) {
    String name = cast.getName().getPath();
    basic(tasks, cache, cast.getId(), "cast/" + name);
    basic(tasks, cache, cast.getSand(), "sand_cast/" + name);
    basic(tasks, cache, cast.getRedSand(), "red_sand_cast/" + name);
  }


  private void itemModel(List<CompletableFuture<?>> tasks, CachedOutput cache, Identifier item, JsonObject model) {
    tasks.add(save(cache, itemModels.json(item), model));
    if (this.itemDefinitionIds.add(item)) {
      tasks.add(save(cache, itemDefinitions.json(item), itemDefinition(item)));
    }
  }

  private void headItemModel(List<CompletableFuture<?>> tasks, CachedOutput cache, Identifier item, TinkerHeadType type) {
    tasks.add(save(cache, itemModels.json(item), parent("minecraft:item/template_skull")));
    if (this.itemDefinitionIds.add(item)) {
      tasks.add(save(cache, itemDefinitions.json(item), headItemDefinition(type)));
    }
  }

  private static JsonObject fluidContainerItemDefinition(JsonObject sourceModel) {
    JsonObject definition = new JsonObject();
    JsonObject model = new JsonObject();
    model.addProperty("type", "tconstruct:fluid_container");
    model.add("textures", sourceModel.get("textures"));
    if (sourceModel.has("fluid")) {
      model.add("fluid", sourceModel.get("fluid"));
    } else {
      // Generic containers start empty; the native model requires a default fluid.
      model.addProperty("fluid", "minecraft:empty");
    }
    if (sourceModel.has("flip_gas")) {
      model.add("flip_gas", sourceModel.get("flip_gas"));
    }
    model.addProperty("cover_is_mask", true);
    model.addProperty("apply_fluid_luminosity", true);
    model.addProperty("force_opaque_fluid", true);
    definition.add("model", model);
    return definition;
  }

  private static JsonObject itemDefinition(Identifier item) {
    JsonObject definition = new JsonObject();
    JsonObject model = new JsonObject();
    if (isTankFluidItem(item)) {
      String modelName = item.getNamespace() + ":item/" + item.getPath();
      model.addProperty("type", "minecraft:composite");
      JsonArray models = new JsonArray();
      JsonObject base = new JsonObject();
      base.addProperty("type", "minecraft:model");
      base.addProperty("model", modelName);
      models.add(base);
      JsonObject fluidLayer = new JsonObject();
      fluidLayer.addProperty("type", "minecraft:special");
      fluidLayer.addProperty("base", modelName);
      JsonObject special = new JsonObject();
      special.addProperty("type", "tconstruct:tank_fluid");
      fluidLayer.add("model", special);
      models.add(fluidLayer);
      model.add("models", models);
      definition.add("model", model);
      return definition;
    }
    model.addProperty("type", isRetexturedItem(item) ? "mantle:retextured_item" : "minecraft:model");
    model.addProperty("model", item.getNamespace() + ":item/" + item.getPath());
    if ("tinkers_chest".equals(item.getPath())) {
      model.add("tints", dyeTintSources());
    } else if (isPotionTintedItem(item)) {
      model.add("tints", potionTintSources());
    } else {
      FoliageType slimeFoliage = getSlimeFoliageTint(item.getPath());
      if (slimeFoliage != null) {
        model.add("tints", constantTintSources(0xFF000000 | slimeFoliage.getColor()));
      }
    }
    definition.add("model", model);
    return definition;
  }

  private static boolean isTankFluidItem(Identifier item) {
    if (!TConstruct.MOD_ID.equals(item.getNamespace())) {
      return false;
    }
    String path = item.getPath();
    return path.equals("seared_casting_tank")
      || path.endsWith("_fuel_tank")
      || path.endsWith("_ingot_tank")
      || path.endsWith("_fuel_gauge")
      || path.endsWith("_ingot_gauge")
      || path.endsWith("_lantern")
      || path.endsWith("_fluid_cannon");
  }

  private static JsonObject materialItemDefinition(String texture, int x, int y, boolean hasOffset) {
    JsonObject definition = new JsonObject();
    JsonObject model = new JsonObject();
    model.addProperty("type", "tconstruct:material");
    model.addProperty("texture", texture);
    if (hasOffset) {
      model.addProperty("offset_x", x);
      model.addProperty("offset_y", y);
    }
    definition.add("model", model);
    return definition;
  }
  private static JsonArray dyeTintSources() {
    JsonArray tints = new JsonArray();
    JsonObject tint = new JsonObject();
    tint.addProperty("type", "minecraft:dye");
    // Item tint sources use ARGB; keep the undyed chest opaque instead of alpha 0.
    tint.addProperty("default", 0xFF407686);
    tints.add(tint);
    return tints;
  }

  private static JsonArray potionTintSources() {
    JsonArray tints = new JsonArray();
    JsonObject tint = new JsonObject();
    tint.addProperty("type", "minecraft:potion");
    tint.addProperty("default", -13083194);
    tints.add(tint);
    return tints;
  }

  private static JsonArray constantTintSources(int color) {
    JsonArray tints = new JsonArray();
    JsonObject tint = new JsonObject();
    tint.addProperty("type", "minecraft:constant");
    tint.addProperty("value", color);
    tints.add(tint);
    return tints;
  }

  @Nullable
  private static FoliageType getSlimeFoliageTint(String path) {
    if (path.endsWith("_slime_fern")) {
      return foliageFromPrefix(path.substring(0, path.length() - "_slime_fern".length()));
    }
    if (path.endsWith("_slime_tall_grass")) {
      return foliageFromPrefix(path.substring(0, path.length() - "_slime_tall_grass".length()));
    }
    if (path.endsWith("_slime_leaves")) {
      return foliageFromPrefix(path.substring(0, path.length() - "_slime_leaves".length()));
    }
    if (path.endsWith("_slime_vine")) {
      return foliageFromPrefix(path.substring(0, path.length() - "_slime_vine".length()));
    }
    if (path.endsWith("_slime_grass")) {
      int split = path.indexOf('_');
      if (split > 0) {
        return foliageFromPrefix(path.substring(0, split));
      }
    }
    return null;
  }

  @Nullable
  private static FoliageType foliageFromPrefix(String prefix) {
    for (FoliageType type : FoliageType.values()) {
      if (type.getSerializedName().equals(prefix)) {
        return type;
      }
    }
    return null;
  }

  private static JsonObject headItemDefinition(TinkerHeadType type) {
    JsonObject definition = new JsonObject();
    JsonObject model = new JsonObject();
    model.addProperty("type", "minecraft:special");
    model.addProperty("base", "minecraft:item/template_skull");
    model.add("model", headSpecialModel(type));
    model.add("transformation", headTransformation());
    definition.add("model", model);
    return definition;
  }

  private static JsonObject headSpecialModel(TinkerHeadType type) {
    JsonObject model = new JsonObject();
    model.addProperty("type", "minecraft:head");
    model.addProperty("kind", type.getSerializedName());
    model.addProperty("texture", headTexture(type));
    return model;
  }

  private static String headTexture(TinkerHeadType type) {
    return switch (type) {
      case BLAZE -> "minecraft:blaze/blaze";
      case ENDERMAN -> "tconstruct:skull/enderman";
      case STRAY -> "tconstruct:skull/stray";
      case HUSK -> "minecraft:zombie/husk";
      case DROWNED -> "tconstruct:skull/drowned";
      case SPIDER -> "minecraft:spider/spider";
      case CAVE_SPIDER -> "minecraft:spider/cave_spider";
      case PIGLIN_BRUTE -> "minecraft:piglin/piglin_brute";
      case ZOMBIFIED_PIGLIN -> "minecraft:piglin/zombified_piglin";
      case VENOMBONE -> "tconstruct:skull/venombone";
      case BLAZING_BONE -> "tconstruct:skull/blazing_bone";
      case NECRONIUM -> "tconstruct:skull/necronium";
    };
  }

  private static JsonObject headTransformation() {
    JsonObject transformation = new JsonObject();
    transformation.add("left_rotation", array(1.0, 0.0, 0.0, 0.0));
    transformation.add("right_rotation", array(0.0, 0.0, 0.0, 1.0));
    transformation.add("scale", array(1.0, 1.0, 1.0));
    transformation.add("translation", array(0.5, 0.0, 0.5));
    return transformation;
  }

  private static JsonArray array(double... values) {
    JsonArray array = new JsonArray();
    for (double value : values) {
      array.add(value);
    }
    return array;
  }

  private static boolean isRetexturedItem(Identifier item) {
    if (!TConstruct.MOD_ID.equals(item.getNamespace())) {
      return false;
    }
    return switch (item.getPath()) {
      case "crafting_station", "part_builder", "tinker_station", "modifier_worktable", "tinkers_anvil", "scorched_anvil",
           "smeltery_controller", "seared_drain", "seared_duct", "seared_chute", "seared_melter", "seared_heater",
           "foundry_controller", "scorched_drain", "scorched_duct", "scorched_chute", "scorched_alloyer" -> true;
      default -> false;
    };
  }

  private static boolean isPotionTintedItem(Identifier item) {
    return TConstruct.MOD_ID.equals(item.getNamespace()) && "potion_bucket".equals(item.getPath());
  }

  private static JsonObject parent(String parent) {
    JsonObject json = new JsonObject();
    json.addProperty("parent", parent);
    return json;
  }

  @SuppressWarnings("deprecation")
  private static Identifier key(ItemLike item) {
    return BuiltInRegistries.ITEM.getKey(item.asItem());
  }

  private static CompletableFuture<?> save(CachedOutput cache, Path path, JsonObject json) {
    return DataProvider.saveStable(cache, json, path);
  }

  @Override
  public String getName() {
    return "Continuum Construct item model provider";
  }
}
