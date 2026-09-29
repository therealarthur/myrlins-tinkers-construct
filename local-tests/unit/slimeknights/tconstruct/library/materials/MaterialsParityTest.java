package slimeknights.tconstruct.library.materials;

import aebm.continuumtests.ComponentTestSetup;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.command.RemoveRecipesCommand;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.data.predicate.item.ItemPredicate;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialManager;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.materials.stats.IMaterialStats;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsManager;
import slimeknights.tconstruct.library.materials.traits.MaterialTraitsManager;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialFluidRecipe;
import slimeknights.tconstruct.library.recipe.melting.MaterialMeltingRecipe;
import slimeknights.tconstruct.library.recipe.melting.MeltingRecipe;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipe;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.tools.stats.StatlessMaterialStats;

import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * parity/materials FML test (2026-09-29).
 * <p>
 * Loads the committed generated material JSON through the real {@link MaterialManager}, {@link MaterialStatsManager}
 * and {@link MaterialTraitsManager} of a fresh {@link MaterialRegistry}, over a real resource manager, then compares the
 * loaded registry with official Tinkers' Construct 3.12.1.231 data condensed into
 * {@code local-tests/unit/aebm/continuumtests/data/official-materials-v3.12.1.231.json} (see make_official_materials.py).
 * Numeric stat values are balance data owned by parity/balance and are not compared; stat type presence, traits,
 * definitions and redirects are. Continuum-only additions that are kept on purpose are listed in {@link #EXTRA_STATS}
 * and {@link #EXTRA_TRAITS} with the reason in materials-REPORT.md. The static registry state is restored afterwards.
 */
final class MaterialsParityTest {
  private static final Path ROOT = Path.of(System.getProperty("aebm.continuum.projectRoot", "."));
  private static final Path OFFICIAL = ROOT.resolve("local-tests/unit/aebm/continuumtests/data/official-materials-v3.12.1.231.json");
  private static final String SKULL = "tconstruct:skull";
  private static final String MAILLE = "tconstruct:maille";
  /** Continuum-only stat types kept for saved items: legacy bone skulls and vine maille (see armor-parity-plan.md). */
  private static final Map<String,Set<String>> EXTRA_STATS = Map.of(
    "tconstruct:venombone", Set.of(SKULL),
    "tconstruct:blazing_bone", Set.of(SKULL),
    "tconstruct:necronium", Set.of(SKULL),
    "tconstruct:vine", Set.of(MAILLE),
    "tconstruct:skyslime_vine", Set.of(MAILLE),
    "tconstruct:enderslime_vine", Set.of(MAILLE),
    "tconstruct:twisting_vine", Set.of(MAILLE),
    "tconstruct:weeping_vine", Set.of(MAILLE));
  /**
   * Continuum-only traits appended after the official list. Empty since release/arthur.8: the golden trait that was
   * appended to the gold and rose gold skulls (entries "tconstruct:gold/" + SKULL and "tconstruct:rose_gold/" + SKULL,
   * both "tconstruct:golden@1") was removed to match official, so those skulls must now equal the official lists exactly.
   */
  private static final Map<String,List<String>> EXTRA_TRAITS = Map.of();

  private static final Map<Field,Object> SAVED = new HashMap<>();
  private static JsonObject official;
  private static IMaterialRegistry registry;
  private static Map<MaterialStatsId,MaterialStatsId> fallbacks;
  private static MultiPackResourceManager resources;

  /** Condition context that reports every tag as filled, so all conditional compat materials load. */
  private static final ICondition.IContext ALL_TAGS_FILLED = new ICondition.IContext() {
    @Override
    public <T> boolean isTagLoaded(TagKey<T> key) {
      return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Collection<Holder<T>> getTag(TagKey<T> key) {
      return List.of(Holder.direct((T) key));
    }
  };

  @BeforeAll
  static void loadRegistryFromCommittedData() throws Exception {
    ComponentTestSetup.initialize();
    for (String name : new String[]{"INSTANCE", "fullyLoaded", "materialsLoaded", "statsLoaded", "traitsLoaded", "FIRST_MATERIALS"}) {
      Field field = MaterialRegistry.class.getDeclaredField(name);
      field.setAccessible(true);
      Object value = field.get(null);
      SAVED.put(field, value instanceof Map<?,?> map ? new HashMap<>(map) : value);
    }
    try (Reader reader = Files.newBufferedReader(OFFICIAL, StandardCharsets.UTF_8)) {
      official = JsonParser.parseReader(reader).getAsJsonObject();
    }
    MaterialRegistry fresh = new MaterialRegistry();
    MaterialRegistry.INSTANCE = fresh;
    List<PackResources> packs = new ArrayList<>();
    for (String folder : List.of("src/generated/resources", "src/main/resources")) {
      Path root = ROOT.resolve(folder);
      assertTrue(Files.isDirectory(root.resolve("data")), "missing data folder " + root);
      packs.add(new PathPackResources(new PackLocationInfo("parity_" + folder.replace('/', '_'), Component.literal(folder), PackSource.BUILT_IN, Optional.empty()), root));
    }
    resources = new MultiPackResourceManager(PackType.SERVER_DATA, packs);
    MaterialManager materials = field(fresh, "materialManager");
    MaterialStatsManager stats = field(fresh, "materialStatsManager");
    MaterialTraitsManager traits = field(fresh, "materialTraitsManager");
    materials.setConditionContext(ALL_TAGS_FILLED);
    // same order and entry points as a server datapack reload
    Method prepare = SimpleJsonResourceReloadListener.class.getDeclaredMethod("prepare", ResourceManager.class, ProfilerFiller.class);
    prepare.setAccessible(true);
    Object prepared = prepare.invoke(materials, resources, InactiveProfiler.INSTANCE);
    Method apply = MaterialManager.class.getDeclaredMethod("apply", Map.class, ResourceManager.class, ProfilerFiller.class);
    apply.setAccessible(true);
    apply.invoke(materials, prepared, resources, InactiveProfiler.INSTANCE);
    stats.onResourceManagerReload(resources);
    traits.onResourceManagerReload(resources);
    assertTrue(MaterialRegistry.isFullyLoaded(), "all three managers must report loaded");
    registry = MaterialRegistry.getInstance();
    fallbacks = field(traits, "statTypeFallbacks");
  }

  @AfterAll
  static void restoreRegistry() throws Exception {
    if (resources != null) {
      resources.close();
    }
    for (var entry : SAVED.entrySet()) {
      if (entry.getKey().getName().equals("FIRST_MATERIALS")) {
        @SuppressWarnings("unchecked") Map<Object,Object> map = (Map<Object,Object>) entry.getKey().get(null);
        map.clear();
        map.putAll((Map<?,?>) entry.getValue());
      } else {
        entry.getKey().set(null, entry.getValue());
      }
    }
  }

  @Test
  void everyOfficialMaterialResolvesWithOfficialDefinitionStatTypesAndTraits() {
    JsonObject materials = official.getAsJsonObject("materials");
    assertEquals(99, materials.size(), "official 3.12.1.231 defines 99 materials besides redirects");
    List<String> problems = new ArrayList<>();
    for (var entry : materials.entrySet()) {
      String id = entry.getKey();
      JsonObject expected = entry.getValue().getAsJsonObject();
      MaterialId materialId = new MaterialId(id);
      IMaterial material = registry.getMaterial(materialId);
      if (material == IMaterial.UNKNOWN) {
        problems.add(id + ": missing from MaterialRegistry");
        continue;
      }
      // definition: tier, sort order, craftability and hidden flag are restored to official values
      check(problems, id + " tier", expected.get("tier").getAsInt(), material.getTier());
      check(problems, id + " sortOrder", expected.get("sortOrder").getAsInt(), material.getSortOrder());
      check(problems, id + " craftable", expected.get("craftable").getAsBoolean(), material.isCraftable());
      check(problems, id + " hidden", expected.get("hidden").getAsBoolean(), material.isHidden());

      // stat types: every official one resolves; extras must be documented additions
      Set<String> officialStats = new TreeSet<>();
      expected.getAsJsonArray("stats").forEach(stat -> officialStats.add(stat.getAsString()));
      Set<String> loadedStats = registry.getAllStats(materialId).stream()
        .map(IMaterialStats::getIdentifier).map(Object::toString).collect(Collectors.toCollection(TreeSet::new));
      for (String stat : officialStats) {
        MaterialStatsId statsId = new MaterialStatsId(stat);
        if (registry.getStatType(statsId) == null) {
          problems.add(id + ": official stat type " + stat + " is not registered");
        } else if (registry.getMaterialStats(materialId, statsId).isEmpty()) {
          problems.add(id + ": missing official stat type " + stat);
        }
      }
      Set<String> extra = new TreeSet<>(loadedStats);
      extra.removeAll(officialStats);
      extra.removeAll(EXTRA_STATS.getOrDefault(id, Set.of()));
      if (!extra.isEmpty()) {
        problems.add(id + ": undocumented extra stat types " + extra);
      }

      // traits: default list and, for each official stat and per-stat key, the list the registry returns
      JsonObject traits = expected.getAsJsonObject("traits");
      List<String> officialDefault = strings(traits.getAsJsonArray("default"));
      check(problems, id + " default traits", officialDefault, names(registry.getDefaultTraits(materialId)));
      JsonObject perStat = traits.getAsJsonObject("perStat");
      Set<String> keys = new TreeSet<>(officialStats);
      keys.addAll(perStat.keySet());
      for (String key : keys) {
        MaterialStatsId statsId = new MaterialStatsId(key);
        List<String> expectedTraits = new ArrayList<>(officialTraits(perStat, officialDefault, statsId));
        expectedTraits.addAll(EXTRA_TRAITS.getOrDefault(id + "/" + key, List.of()));
        check(problems, id + " traits for " + key, expectedTraits, names(registry.getTraits(materialId, statsId)));
      }
    }
    assertEquals(List.of(), problems, "official material parity differences (" + problems.size() + ")");
  }

  @Test
  void officialRedirectsResolve() {
    JsonObject redirects = official.getAsJsonObject("redirects");
    for (var entry : redirects.entrySet()) {
      MaterialId legacy = new MaterialId(entry.getKey());
      // the first redirect wins; with every tag filled that is the first official target (tungsten goes to lead)
      String target = entry.getValue().getAsJsonArray().get(0).getAsString();
      assertEquals(target, registry.resolve(legacy).toString(), "redirect for " + legacy);
      assertNotSame(IMaterial.UNKNOWN, registry.getMaterial(registry.resolve(legacy)), "redirect target must exist: " + target);
      assertSame(IMaterial.UNKNOWN, registry.getMaterial(legacy), "a redirect is not a material itself: " + legacy);
    }
  }

  @Test
  void legacySkinVariantsStayLoadableAndMigrateThroughLoadedRecipes() throws Exception {
    // Upstream Continuum made sky and ender slimeskin as variants of the slime vines. Those IDs stay loadable:
    // they resolve to the backing vine material, which carries every stat type the old leather composite could
    // have produced, so no saved part, binding, bowstring, laces, cuirass or repair kit loses its stats.
    List<MaterialStatsId> leatherComposites = List.of(StatlessMaterialStats.BINDING.getIdentifier(), StatlessMaterialStats.BOWSTRING.getIdentifier(),
      StatlessMaterialStats.CUIRASS.getIdentifier(), new MaterialStatsId("tconstruct:laces"), StatlessMaterialStats.MAILLE.getIdentifier(),
      StatlessMaterialStats.REPAIR_KIT.getIdentifier());
    Map<MaterialVariantId,MaterialId> legacy = Map.of(MaterialIds.skySlimeskin, MaterialIds.skyslimeVine, MaterialIds.enderSlimeskin, MaterialIds.enderslimeVine);
    Map<MaterialVariantId,MaterialId> standalone = Map.of(MaterialIds.skySlimeskin, MaterialIds.skyslimeskin, MaterialIds.enderSlimeskin, MaterialIds.enderslimeskin);
    Map<MaterialVariantId,String> fluids = Map.of(MaterialIds.skySlimeskin, "sky", MaterialIds.enderSlimeskin, "ender");
    Fluid venom = fluid("tconstruct:venom");
    for (var entry : legacy.entrySet()) {
      MaterialVariantId variant = entry.getKey();
      assertEquals(entry.getValue().toString() + "#slimeskin", variant.toString());
      IMaterial backing = MaterialVariant.of(variant).get();
      assertEquals(entry.getValue().toString(), backing.getIdentifier().toString(), "legacy variant must resolve to its backing vine");
      for (MaterialStatsId stat : leatherComposites) {
        assertTrue(registry.getMaterialStats(entry.getValue(), stat).isPresent(), variant + " lost stat " + stat);
        assertTrue(registry.getMaterialStats(MaterialIds.leather, stat).isPresent(), "leather must accept the cleaned " + stat);
      }
      // step 1: venom cleans the legacy skin back to leather (Continuum-only *_legacy_cleaning recipe)
      String name = fluids.get(variant);
      MaterialFluidRecipe cleaning = recipe("tools/materials/slimeskin/composite/" + name + "_legacy_cleaning", MaterialFluidRecipe.class);
      assertTrue(cleaning.matches(venom, variant), "legacy cleaning must accept " + variant);
      assertEquals(MaterialIds.leather.toString(), cleaning.getOutput().getVariant().toString());
      assertEquals(official("tools/materials/slimeskin/composite/" + name + "_cleaning").get("fluid_amount").getAsInt(), cleaning.getFluidAmount(venom));
      // step 2: the official composite turns that leather into the standalone skin for maille and cuirass uses
      MaterialFluidRecipe composite = recipe("tools/materials/slimeskin/composite/" + name, MaterialFluidRecipe.class);
      Fluid slime = fluid(name.equals("sky") ? "tconstruct:sky_slime" : "tconstruct:ender_slime");
      assertTrue(composite.matches(slime, MaterialIds.leather), "composite must accept leather");
      assertFalse(composite.matches(slime, variant), "the legacy skin must be cleaned first, not dipped twice");
      assertEquals(standalone.get(variant).toString(), composite.getOutput().getVariant().toString());
      for (MaterialStatsId stat : List.of(StatlessMaterialStats.CUIRASS.getIdentifier(), StatlessMaterialStats.MAILLE.getIdentifier())) {
        assertTrue(registry.getMaterialStats(standalone.get(variant), stat).isPresent(), "standalone skin must support " + stat);
      }
    }
  }

  @Test
  void slimeskinCompositeAndCleaningRecipesMatchOfficialAmounts() throws Exception {
    Map<String,String> slimes = Map.of("earth", "tconstruct:earth_slime", "sky", "tconstruct:sky_slime", "ichor", "tconstruct:ichor", "ender", "tconstruct:ender_slime");
    Fluid venom = fluid("tconstruct:venom");
    for (var entry : slimes.entrySet()) {
      String path = "tools/materials/slimeskin/composite/" + entry.getKey();
      JsonObject expected = official(path);
      MaterialFluidRecipe dip = recipe(path, MaterialFluidRecipe.class);
      Fluid slime = fluid(entry.getValue());
      MaterialId input = new MaterialId(expected.get("input").getAsString());
      assertTrue(dip.matches(slime, input), path + " must accept its official fluid and input");
      assertEquals(expected.get("fluid_amount").getAsInt(), dip.getFluidAmount(slime), path);
      assertEquals(expected.get("output").getAsString(), dip.getOutput().getVariant().toString(), path);
      assertEquals(expected.get("temperature").getAsInt(), dip.getTemperature(), path);

      JsonObject expectedCleaning = official(path + "_cleaning");
      MaterialFluidRecipe cleaning = recipe(path + "_cleaning", MaterialFluidRecipe.class);
      MaterialId skin = new MaterialId(expectedCleaning.get("input").getAsString());
      assertTrue(cleaning.matches(venom, skin), path + "_cleaning must accept venom");
      assertEquals(expectedCleaning.get("fluid_amount").getAsInt(), cleaning.getFluidAmount(venom));
      assertEquals(expectedCleaning.get("output").getAsString(), cleaning.getOutput().getVariant().toString());
    }
  }

  @Test
  void venomAndVenomboneMeltingAndCastingAmountsMatchOfficial() throws Exception {
    Fluid venom = fluid("tconstruct:venom");
    for (String path : List.of("smeltery/melting/venom/eye", "smeltery/melting/venom/fermented_eye")) {
      MeltingRecipe melting = recipe(path, MeltingRecipe.class);
      JsonObject expected = official(path);
      assertEquals(expected.get("result_amount").getAsInt(), melting.getOutput().getAmount(), path);
      assertSame(venom, melting.getOutput().getFluid(), path);
      assertEquals(expected.get("temperature").getAsInt(), melting.getTemperature(null), path);
    }
    for (String path : List.of("smeltery/casting/slime/venom/bone", "smeltery/casting/slime/venom/bottle")) {
      ItemCastingRecipe casting = recipe(path, ItemCastingRecipe.class);
      JsonObject expected = official(path);
      int amount = expected.get("fluid_amount").getAsInt();
      // a fluid entry resolves here; a tag entry (bottle, like official) only resolves once a server binds tags,
      // so its loaded amount and tag are read back through the same serializer instead
      if (!casting.getFluids().isEmpty()) {
        assertTrue(casting.getFluids().stream().anyMatch(stack -> stack.getFluid() == venom && stack.getAmount() == amount), path + " amount");
      } else {
        JsonObject fluid = encode(casting).getAsJsonObject("fluid");
        assertEquals(amount, fluid.get("amount").getAsInt(), path + " amount");
        assertEquals("tconstruct:venom", fluid.get("tag").getAsString(), path + " fluid tag");
      }
      assertEquals(expected.get("result").getAsString(), BuiltInRegistries.ITEM.getKey(casting.getOutput().getItem()).toString(), path);
    }
    // material casting and melting of the venom slime material, and the venombone composite
    MaterialFluidRecipe cast = recipe("tools/materials/casting/venom", MaterialFluidRecipe.class);
    assertEquals(official("tools/materials/casting/venom").get("fluid_amount").getAsInt(), cast.getFluidAmount(venom));
    assertEquals(MaterialIds.venom.toString(), cast.getOutput().getVariant().toString());
    assertTrue(cast.matches(venom), "venom material casting must accept the venom fluid");
    JsonObject melt = encode(recipe("tools/materials/melting/venom", MaterialMeltingRecipe.class));
    assertEquals(official("tools/materials/melting/venom").get("result_amount").getAsInt(), melt.getAsJsonObject("result").get("amount").getAsInt());
    assertEquals(MaterialIds.venom.toString(), melt.get("input").getAsString());
    MaterialFluidRecipe venombone = recipe("tools/materials/composite/venombone", MaterialFluidRecipe.class);
    assertTrue(venombone.matches(venom, MaterialIds.bone));
    assertEquals(official("tools/materials/composite/venombone").get("fluid_amount").getAsInt(), venombone.getFluidAmount(venom));
    assertEquals(MaterialIds.venombone.toString(), venombone.getOutput().getVariant().toString());
    // spider eyes give the official material values
    for (String path : List.of("tools/materials/venom_eye", "tools/materials/venom_fermented")) {
      JsonObject loaded = encode(recipe(path, MaterialRecipe.class));
      JsonObject expected = official(path);
      assertEquals(expected.get("value").getAsInt(), loaded.get("value").getAsInt(), path);
      assertEquals(expected.get("needed").getAsInt(), loaded.get("needed").getAsInt(), path);
      assertEquals(MaterialIds.venom.toString(), loaded.get("material").getAsString(), path);
    }
    // bottle filling transfer uses the same 250 mB
    JsonObject fill = json("data/tconstruct/mantle/fluid_transfer/venom_bottle_fill.json");
    JsonObject expectedFill = official.getAsJsonObject("fluid_transfer").getAsJsonObject("tconstruct:venom_bottle_fill");
    assertEquals(expectedFill.get("fluid_amount").getAsInt(), fill.getAsJsonObject("fluid").get("amount").getAsInt());
    assertEquals(expectedFill.get("result").getAsString(), fill.get("result").getAsString());
  }

  @Test
  void mantleRemovalPresetsLoadThroughTheCommandLoaders() throws Exception {
    Map<String,List<RecipeType<?>>> types = Map.of(
      "ingot_smelting", List.of(RecipeType.SMELTING, RecipeType.BLASTING),
      "nugget_smelting", List.of(RecipeType.SMELTING, RecipeType.BLASTING),
      "vanilla_tools", List.of(RecipeType.CRAFTING),
      "netherite_smithing", List.of(RecipeType.SMITHING));
    for (var entry : types.entrySet()) {
      JsonObject preset = json("data/tconstruct/mantle/remove_recipes/" + entry.getKey() + ".json");
      // same calls as RemoveRecipesCommand.runPreset; the loaders consume keys of nested objects, so parse a copy
      JsonObject parsed = preset.deepCopy();
      IJsonPredicate<Item> remove = ItemPredicate.LOADER.getOrDefault(parsed, "result");
      IJsonPredicate<Item> input = ItemPredicate.LOADER.getOrDefault(parsed, "input");
      assertEquals(entry.getValue(), RemoveRecipesCommand.RECIPE_TYPES.getIfPresent(parsed, "recipe_type"), entry.getKey());
      assertSame(ItemPredicate.ANY, input, entry.getKey() + " removes by result only");
      // item sets are unordered (hash order on serialization), so compare with sorted item lists
      assertEquals(sortedSets(preset.get("result")), sortedSets(ItemPredicate.LOADER.serialize(remove)), entry.getKey() + " must round-trip through the loader");
    }
    IJsonPredicate<Item> netherite = ItemPredicate.LOADER.getOrDefault(json("data/tconstruct/mantle/remove_recipes/netherite_smithing.json"), "result");
    assertTrue(netherite.matches(Items.NETHERITE_PICKAXE));
    assertTrue(netherite.matches(Items.NETHERITE_BOOTS));
    assertFalse(netherite.matches(Items.DIAMOND_PICKAXE));
  }

  /* Helpers */

  /** Copy of a predicate JSON with every "items" array sorted, since mantle:set does not keep order. */
  private static JsonElement sortedSets(JsonElement element) {
    if (element.isJsonObject()) {
      JsonObject copy = new JsonObject();
      for (var entry : element.getAsJsonObject().entrySet()) {
        JsonElement value = sortedSets(entry.getValue());
        if (entry.getKey().equals("items") && value.isJsonArray()) {
          List<String> items = strings(value.getAsJsonArray());
          items.sort(null);
          com.google.gson.JsonArray sorted = new com.google.gson.JsonArray();
          items.forEach(sorted::add);
          value = sorted;
        }
        copy.add(entry.getKey(), value);
      }
      return copy;
    }
    if (element.isJsonArray()) {
      com.google.gson.JsonArray copy = new com.google.gson.JsonArray();
      element.getAsJsonArray().forEach(child -> copy.add(sortedSets(child)));
      return copy;
    }
    return element;
  }

  /** Official trait list for a stat, following the same stat type fallback the registry uses. */
  private static List<String> officialTraits(JsonObject perStat, List<String> officialDefault, MaterialStatsId statsId) {
    String key = statsId.toString();
    if (perStat.has(key)) {
      return strings(perStat.getAsJsonArray(key));
    }
    MaterialStatsId fallback = fallbacks.get(statsId);
    if (fallback != null && perStat.has(fallback.toString())) {
      return strings(perStat.getAsJsonArray(fallback.toString()));
    }
    return officialDefault;
  }

  private static List<String> names(List<ModifierEntry> entries) {
    return entries.stream().map(entry -> entry.getId() + "@" + entry.getLevel()).toList();
  }

  private static List<String> strings(com.google.gson.JsonArray array) {
    List<String> list = new ArrayList<>();
    array.forEach(element -> list.add(element.getAsString()));
    return list;
  }

  private static void check(List<String> problems, String what, Object expected, Object actual) {
    if (!expected.equals(actual)) {
      problems.add(what + ": official " + expected + ", loaded " + actual);
    }
  }

  private static JsonObject official(String recipe) {
    JsonObject recipes = official.getAsJsonObject("recipes");
    JsonObject expected = recipes.getAsJsonObject("tconstruct:" + recipe);
    assertNotNull(expected, "no official data for " + recipe);
    return expected;
  }

  private static Fluid fluid(String id) {
    Fluid fluid = BuiltInRegistries.FLUID.getValue(Identifier.parse(id));
    assertNotNull(fluid, id);
    assertNotEquals(BuiltInRegistries.FLUID.getDefaultKey(), BuiltInRegistries.FLUID.getKey(fluid), "fluid must be registered: " + id);
    return fluid;
  }

  private static JsonObject json(String path) throws Exception {
    try (Reader reader = Files.newBufferedReader(ROOT.resolve("src/generated/resources").resolve(path), StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    }
  }

  /** Decodes a committed recipe through the registered serializer, as the recipe manager would. */
  private static <T> T recipe(String path, Class<T> type) throws Exception {
    JsonElement json = json("data/tconstruct/recipe/" + path + ".json");
    Recipe<?> recipe = Recipe.CODEC.parse(ops(), json).getOrThrow(message -> new AssertionError(path + ": " + message));
    return assertInstanceOf(type, recipe, path);
  }

  private static JsonObject encode(Recipe<?> recipe) {
    return Recipe.CODEC.encodeStart(ops(), recipe).getOrThrow().getAsJsonObject();
  }

  private static com.mojang.serialization.DynamicOps<JsonElement> ops() {
    return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
  }

  @SuppressWarnings("unchecked")
  private static <T> T field(Object owner, String name) throws Exception {
    Field field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return (T) field.get(owner);
  }
}
