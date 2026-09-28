package aebm.continuumtests;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialFluidRecipe;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.repairing.ModifierRepairCraftingRecipe;
import slimeknights.tconstruct.shared.TinkerCommons;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;

import static org.junit.jupiter.api.Assertions.*;

/** Decode shipped recipes/conditions through the target codecs, without loading a world. */
final class OptionalMeltingRecipeTest {
  @BeforeAll
  static void initializeComponents() {
    ComponentTestSetup.initialize();
  }

  @Test
  void everyConvertedBranchUsesTheOriginalFirstMatchForAllTagPresenceCombinations() throws Exception {
    var manifest = JsonParser.parseString(Files.readString(auditManifest()))
      .getAsJsonObject();
    var groups = manifest.getAsJsonArray("groups");
    assertEquals(61, groups.size());
    int branches = 0;
    int cases = 0;
    for (var entry : groups) {
      var group = entry.getAsJsonObject();
      var outer = conditions(group.getAsJsonArray("outer_conditions_unchanged"));
      var originals = new ArrayList<List<ICondition>>();
      var converted = new ArrayList<List<ICondition>>();
      var tagNames = new TreeSet<String>();
      collectTags(group.get("outer_conditions_unchanged"), tagNames);
      for (var branchEntry : group.getAsJsonArray("branches")) {
        var branch = branchEntry.getAsJsonObject();
        String path = branch.get("path").getAsString().split("/resources/", 2)[1];
        var shipped = resource(path).getAsJsonArray("neoforge:conditions");
        assertEquals(branch.get("fixed_conditions"), shipped, path);
        originals.add(conditions(branch.getAsJsonArray("original_eligibility")));
        converted.add(conditions(shipped));
        collectTags(branch.get("original_eligibility"), tagNames);
        branches++;
      }
      var tags = List.copyOf(tagNames);
      assertTrue(tags.size() < 10, "Review exhaustive case count if recipe conditions grow");
      for (int mask = 0; mask < (1 << tags.size()); mask++) {
        var filled = new HashSet<Identifier>();
        for (int bit = 0; bit < tags.size(); bit++) {
          if ((mask & (1 << bit)) != 0) {
            filled.add(Identifier.parse(tags.get(bit)));
          }
        }
        ICondition.IContext context = tagContext(filled);
        var expected = new ArrayList<Integer>();
        if (passes(outer, context)) {
          for (int index = 0; index < originals.size(); index++) {
            if (passes(originals.get(index), context)) {
              expected.add(index);
              break;
            }
          }
        }
        var actual = new ArrayList<Integer>();
        for (int index = 0; index < converted.size(); index++) {
          if (passes(converted.get(index), context)) {
            actual.add(index);
          }
        }
        assertEquals(expected, actual, group.get("original_resource") + " tags=" + filled);
        cases++;
      }
    }
    assertEquals(124, branches);
    assertTrue(cases >= 252, "Includes byproduct and outer ingredient tag-presence combinations");
  }

  @Test
  void restoredHoneyMagmaAndTastyRecipesDecodeWithOriginalValues() throws Exception {
    var ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
    var honey = assertInstanceOf(MaterialRecipe.class, Recipe.CODEC.parse(ops,
      resource("data/tconstruct/recipe/tools/materials/honey_block.json")).getOrThrow());
    assertEquals(MaterialIds.honey, honey.getMaterial().getVariant());
    assertEquals(4, honey.getValue());
    assertEquals(1, honey.getNeeded());
    assertTrue(honey.getIngredient().test(Items.HONEY_BLOCK.getDefaultInstance()));
    assertFalse(honey.getIngredient().test(Items.HONEY_BOTTLE.getDefaultInstance()));

    var magmaJson = resource("data/tconstruct/recipe/tools/materials/composite/magma.json");
    var magma = assertInstanceOf(MaterialFluidRecipe.class, Recipe.CODEC.parse(ops, magmaJson).getOrThrow());
    assertNotNull(magma.getInput());
    assertEquals(MaterialIds.earthslime, magma.getInput().getVariant());
    assertEquals(MaterialIds.magma, magma.getOutput().getVariant());
    assertEquals(1500, magma.getTemperature());
    assertEquals(magmaJson.get("fluid"), Recipe.CODEC.encodeStart(ops, magma).getOrThrow().getAsJsonObject().get("fluid"));

    var tasty = assertInstanceOf(ModifierRepairCraftingRecipe.class, Recipe.CODEC.parse(ops,
      resource("data/tconstruct/recipe/tools/modifiers/tasty_crafting_table.json")).getOrThrow());
    assertEquals(ModifierIds.tasty, tasty.getModifier());
    assertEquals(25, tasty.getRepairAmount());
    assertTrue(tasty.getIngredient().test(TinkerCommons.bacon.get().getDefaultInstance()));
    assertFalse(tasty.getIngredient().test(Items.PORKCHOP.getDefaultInstance()));
  }

  private static List<ICondition> conditions(JsonArray array) {
    var result = new ArrayList<ICondition>();
    for (var value : array) {
      result.add(ICondition.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow());
    }
    return result;
  }

  /** FML runs below build/, so resolve the checked-in audit from a project ancestor. */
  private static Path auditManifest() {
    String projectRoot = System.getProperty("aebm.continuum.projectRoot");
    Path start = projectRoot == null ? Path.of("").toAbsolutePath() : Path.of(projectRoot).toAbsolutePath();
    for (Path directory = start; directory != null; directory = directory.getParent()) {
      Path candidate = directory.resolve("local-audit/optional-melting-branch-validation.json");
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
    }
    throw new AssertionError("Cannot find the checked-in melting audit above " + start
      + "; set aebm.continuum.projectRoot when running outside the project");
  }

  private static boolean passes(List<ICondition> conditions, ICondition.IContext context) {
    return conditions.stream().allMatch(condition -> condition.test(context));
  }

  private static void collectTags(JsonElement value, Set<String> tags) {
    if (value.isJsonArray()) {
      value.getAsJsonArray().forEach(element -> collectTags(element, tags));
    } else if (value.isJsonObject()) {
      value.getAsJsonObject().entrySet().forEach(entry -> {
        if (entry.getKey().equals("tag") || entry.getKey().equals("ignore")) {
          tags.add(entry.getValue().getAsString());
        } else if (entry.getKey().equals("match")) {
          entry.getValue().getAsJsonArray().forEach(tag -> tags.add(tag.getAsString()));
        } else {
          collectTags(entry.getValue(), tags);
        }
      });
    }
  }

  private static ICondition.IContext tagContext(Set<Identifier> filled) {
    return new ICondition.IContext() {
      @Override
      public <T> boolean isTagLoaded(TagKey<T> tag) {
        return filled.contains(tag.location());
      }

      @Override
      @SuppressWarnings({"unchecked", "rawtypes"})
      public <T> Collection<Holder<T>> getTag(TagKey<T> tag) {
        return isTagLoaded(tag) ? (List) List.of(Items.STONE.builtInRegistryHolder()) : List.of();
      }
    };
  }

  private static JsonObject resource(String path) throws Exception {
    try (var stream = OptionalMeltingRecipeTest.class.getResourceAsStream("/" + path)) {
      assertNotNull(stream, path);
      return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }
  }
}
