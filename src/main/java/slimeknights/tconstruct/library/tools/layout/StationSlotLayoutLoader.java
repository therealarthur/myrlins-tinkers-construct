package slimeknights.tconstruct.library.tools.layout;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.library.recipe.ingredient.LegacyIngredientType;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loader for tinker station slot layouts, loaded serverside as that makes it easier to modify with recipes and the filters are needed both sides
 */
@Log4j2
public class StationSlotLayoutLoader extends SimpleJsonResourceReloadListener<JsonElement> {
  public static final String FOLDER = "tinkering/station_layouts";
  public static final Gson GSON = (new GsonBuilder())
    .registerTypeHierarchyAdapter(Ingredient.class, new IngredientSerializer())
    .registerTypeHierarchyAdapter(LayoutIcon.class, LayoutIcon.SERIALIZER)
    .registerTypeAdapter(Pattern.class, Pattern.PARSER)
    .setPrettyPrinting()
    .disableHtmlEscaping()
    .create();
  private static final StationSlotLayoutLoader INSTANCE = new StationSlotLayoutLoader();

  /** Map of name to slot layout */
  private Map<Identifier, StationSlotLayout> layoutMap = Collections.emptyMap();
  /** List of layouts that must be loaded for the game to work properly */
  private final List<Identifier> requiredLayouts = new ArrayList<>();

  /** List of all slots in order */
  @Getter
  private List<StationSlotLayout> sortedSlots = Collections.emptyList();

  private StationSlotLayoutLoader() {
    super(JsonHelper.JSON_ELEMENT_CODEC, FileToIdConverter.json(FOLDER));
  }

  /** Sets the slots to the given collection from the packet */
  public void setSlots(Collection<StationSlotLayout> slots) {
    setSlots(slots.stream().collect(Collectors.toMap(StationSlotLayout::getName, Function.identity())));
  }

  /** Updates the slot layouts */
  private void setSlots(Map<Identifier, StationSlotLayout> map) {
    this.layoutMap = map;
    this.sortedSlots = map.values().stream()
                          .filter(layout -> !layout.isMain())
                          .sorted(Comparator.comparingInt(StationSlotLayout::getSortIndex))
                          .collect(Collectors.toList());
  }

  @Override
  protected void apply(Map<Identifier,JsonElement> splashList, ResourceManager resourceManager, ProfilerFiller profiler) {
    long time = System.nanoTime();
    ImmutableMap.Builder<Identifier, StationSlotLayout> builder = ImmutableMap.builder();
    for (Entry<Identifier,JsonElement> entry : splashList.entrySet()) {
      Identifier key = entry.getKey();
      JsonElement value = entry.getValue();
      try {
        // skip empty objects, allows disabling a slot at a lower datapack
        JsonObject object = GsonHelper.convertToJsonObject(value, "station_layout");
        if (!object.entrySet().isEmpty() && ICondition.conditionsMatched(makeConditionalOps(), object)) {
          // just need a valid slot information
          StationSlotLayout layout = GSON.fromJson(object, StationSlotLayout.class);
          int size = layout.getInputSlots().size() + (layout.getToolSlot().isHidden() ? 0 : 1);
          if (size < 2) {
            throw new JsonParseException("Too few slots for layout " + key + ", must have at least 2");
          }
          layout.setName(key);
          builder.put(key, layout);
        }
      } catch (Exception e) {
        log.error("Failed to load station slot layout for name {}", key, e);
      }
    }
    setSlots(builder.build());
    log.info("Loaded {} station slot layouts in {} ms", layoutMap.size(), (System.nanoTime() - time) / 1000000f);
    List<String> missing = requiredLayouts.stream().filter(name -> !layoutMap.containsKey(name)).map(Identifier::toString).collect(Collectors.toList());
    if (!missing.isEmpty()) {
      log.error("Failed to load the following required layouts: {}", String.join(", ", missing));
    }
  }

  /** Gets a layout by name */
  public StationSlotLayout get(Identifier name) {
    return layoutMap.getOrDefault(name, StationSlotLayout.EMPTY);
  }


  /** Registers the name of a layout that should be loaded, if its missing that causes an error */
  public void registerRequiredLayout(Identifier name) {
    requiredLayouts.add(name);
  }

  /* Events */

  /** Called on datapack sync to send the tool data to all players */
  private void onDatapackSync(OnDatapackSyncEvent event) {
    UpdateTinkerSlotLayoutsPacket packet = new UpdateTinkerSlotLayoutsPacket(layoutMap.values());
    TinkerNetwork.getInstance().sendToPlayerList(event.getPlayer(), event.getPlayerList(), packet);
  }

  /** Adds the managers as datapack listeners */
  private void addDataPackListeners(final AddServerReloadListenersEvent event) {
    event.addListener(TConstruct.getResource("station_layouts"), this);
  }


  /* Static */

  /** Gets the singleton instance of the loader */
  public static StationSlotLayoutLoader getInstance() {
    return INSTANCE;
  }

  /** Initializes the tool definition loader */
  public static void init() {
    NeoForge.EVENT_BUS.addListener(INSTANCE::addDataPackListeners);
    NeoForge.EVENT_BUS.addListener(INSTANCE::onDatapackSync);
  }

  /** GSON serializer for ingredients */
  private static class IngredientSerializer implements JsonSerializer<Ingredient>, JsonDeserializer<Ingredient> {
    @Override
    public Ingredient deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
        return fromString(json.getAsString());
      }
      if (json.isJsonArray()) {
        List<Item> items = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray()) {
          if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            addItems(items, element.getAsString());
          } else {
            Ingredient ingredient = deserialize(element, typeOfT, context);
            ingredient.items().forEach(holder -> items.add(holder.value()));
          }
        }
        return Ingredient.of(items.stream());
      }
      if (json.isJsonObject()) {
        JsonObject object = json.getAsJsonObject();
        if (object.has("item")) {
          return fromString(GsonHelper.getAsString(object, "item"));
        }
        if (object.has("tag")) {
          return fromString("#" + GsonHelper.getAsString(object, "tag"));
        }
      }
      return Ingredient.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow(JsonParseException::new);
    }

    private static Ingredient fromString(String name) {
      if (name.startsWith("#")) {
        Identifier id = Identifier.parse(name.substring(1));
        TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
        return LegacyIngredientType.ofTag(tag);
      }
      List<Item> items = new ArrayList<>();
      addItems(items, name);
      return Ingredient.of(items.stream());
    }

    private static void addItems(List<Item> items, String name) {
      if (name.startsWith("#")) {
        Identifier id = Identifier.parse(name.substring(1));
        TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
        BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(holder -> items.add(holder.value()));
      } else {
        Identifier id = Identifier.parse(name);
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item != null) {
          items.add(item);
        }
      }
    }

    @Override
    public JsonElement serialize(Ingredient ingredient, Type typeOfSrc, JsonSerializationContext context) {
      return ingredient.getValues().unwrap()
        .map(tag -> new JsonPrimitive("#" + tag.location()), list -> Ingredient.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, ingredient).getOrThrow(JsonParseException::new));
    }
  }
}
