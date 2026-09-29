package aebm.continuumtests;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.gadgets.TinkerGadgets;
import slimeknights.tconstruct.gadgets.entity.FrameType;
import slimeknights.tconstruct.gadgets.item.FancyItemFrameItem;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Shipped data restored by the fixes stream (2026-09-29) against official Tinkers 3.12.1.231:
 * the fancy armor stand loot table, the entity melting tags and the recipes that use them.
 * Expected values are copied from the official generated files named in each test.
 */
final class FixesDataParityTest {
  /** Official src/generated/resources/data/tconstruct/tags/entity_types/meltable/*.json and melting/blacklist.json, in order. */
  private static final Map<String, List<String>> OFFICIAL_TAGS = Map.of(
    "meltable/drowned", List.of("minecraft:drowned"),
    "meltable/ender", List.of("minecraft:enderman", "minecraft:endermite", "minecraft:ender_dragon"),
    "meltable/farm_animals", List.of("minecraft:chicken", "minecraft:rabbit", "minecraft:cow", "minecraft:mooshroom", "minecraft:pig",
      "minecraft:hoglin", "minecraft:sheep", "minecraft:goat", "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish"),
    "meltable/magma", List.of("minecraft:magma_cube"),
    "meltable/skeleton", List.of("#minecraft:skeletons", "minecraft:skeleton_horse"),
    "meltable/slime", List.of("minecraft:slime"),
    "meltable/zombie", List.of("minecraft:zombie", "minecraft:husk", "minecraft:zombie_horse"),
    "melting/blacklist", List.of());

  /** Official recipes/smeltery/entity_melting/*.json entity ingredient tags. */
  private static final Map<String, String> OFFICIAL_RECIPE_TAGS = Map.of(
    "drowned", "tconstruct:meltable/drowned",
    "ender", "tconstruct:meltable/ender",
    "magma_cube", "tconstruct:meltable/magma",
    "meat_soup", "tconstruct:meltable/farm_animals",
    "skeletons", "tconstruct:meltable/skeleton",
    "slime", "tconstruct:meltable/slime",
    "zombie", "tconstruct:meltable/zombie");

  @Test
  void fancyStandLootTableRoundTripsThroughTheTargetCodec() throws Exception {
    JsonObject shipped = resource("data/tconstruct/loot_table/entities/armor_stand.json");
    // Official loot_tables/entities/armor_stand.json: an empty entity table with its random sequence.
    assertEquals("minecraft:entity", shipped.get("type").getAsString());
    assertEquals("tconstruct:entities/armor_stand", shipped.get("random_sequence").getAsString());
    assertFalse(shipped.has("pools") && !shipped.getAsJsonArray("pools").isEmpty(), "official table has no pools");
    var ops = VanillaRegistries.createLookup().createSerializationContext(JsonOps.INSTANCE);
    LootTable table = LootTable.DIRECT_CODEC.parse(ops, shipped).getOrThrow();
    JsonElement encoded = LootTable.DIRECT_CODEC.encodeStart(ops, table).getOrThrow();
    assertEquals(shipped, encoded, "decode then encode must reproduce the shipped table");
    ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("tconstruct", "entities/armor_stand"));
    assertEquals(Optional.of(key), TinkerGadgets.armorStandEntity.get().getDefaultLootTable(),
      "the fancy stand must consult the restored table instead of registering noLootTable()");
  }

  @Test
  void meltingTagsMatchOfficialLists() throws Exception {
    for (var entry : OFFICIAL_TAGS.entrySet()) {
      JsonArray values = resource("data/tconstruct/tags/entity_type/" + entry.getKey() + ".json").getAsJsonArray("values");
      JsonArray expected = new JsonArray();
      entry.getValue().forEach(expected::add);
      assertEquals(expected, values, entry.getKey());
    }
  }

  @Test
  void hiddenDefaultTagIncludesTheBlacklistLikeOfficial() throws Exception {
    JsonArray values = resource("data/tconstruct/tags/entity_type/melting/hide_in_default.json").getAsJsonArray("values");
    JsonArray expected = JsonParser.parseString(
      "[\"minecraft:giant\",\"#tconstruct:melting/blacklist\",{\"id\":\"#c:hidden_from_recipe_viewers\",\"required\":false}]").getAsJsonArray();
    assertEquals(expected, values);
  }

  @Test
  void entityMeltingRecipesUseTheOfficialTags() throws Exception {
    for (var entry : OFFICIAL_RECIPE_TAGS.entrySet()) {
      JsonObject recipe = resource("data/tconstruct/recipe/smeltery/entity_melting/" + entry.getKey() + ".json");
      JsonElement entity = recipe.get("entity");
      assertTrue(entity.isJsonObject(), entry.getKey() + " entity ingredient must be a single tag: " + entity);
      assertEquals(entry.getValue(), entity.getAsJsonObject().get("tag").getAsString(), entry.getKey());
    }
  }

  @Test
  void groutKilnRecipesRestoreTheOfficialBlastingBranch() throws Exception {
    for (String[] kiln : new String[][] {{"seared/seared_brick_kiln", "tconstruct:grout", "tconstruct:seared_brick"},
                                         {"scorched/scorched_brick_kiln", "tconstruct:nether_grout", "tconstruct:scorched_brick"}}) {
      JsonObject recipe = resource("data/tconstruct/recipe/smeltery/" + kiln[0] + ".json");
      assertEquals("minecraft:blasting", recipe.get("type").getAsString(), kiln[0]);
      assertEquals(100, recipe.get("cookingtime").getAsInt(), kiln[0]);
      assertEquals(0.3f, recipe.get("experience").getAsFloat(), 1e-6, kiln[0]);
      assertEquals(kiln[1], recipe.get("ingredient").getAsString(), kiln[0]);
      assertEquals(kiln[2], recipe.getAsJsonObject("result").get("id").getAsString(), kiln[0]);
    }
  }

  @Test
  void itemFrameTooltipsUseTheRestoredOfficialKeys() throws Exception {
    ComponentTestSetup.initialize();
    JsonObject lang = resource("assets/tconstruct/lang/en_us.json");
    for (FrameType type : FrameType.values()) {
      FancyItemFrameItem item = TinkerGadgets.itemFrame.get(type);
      List<Component> lines = new ArrayList<>();
      item.appendHoverText(new ItemStack(item), Item.TooltipContext.EMPTY, TooltipDisplay.DEFAULT, lines::add, TooltipFlag.NORMAL);
      String key = item.getDescriptionId() + ".tooltip";
      assertEquals(List.of(Component.translatable(key).withStyle(ChatFormatting.GRAY)), lines, type.name());
      assertTrue(lang.has(key), "missing lang key " + key);
    }
    // official en_us.json values
    assertEquals("Slowly rotates clockwise", lang.get("item.tconstruct.gold_item_frame.tooltip").getAsString());
    assertEquals("Immune to fire and explosions", lang.get("item.tconstruct.netherite_item_frame.tooltip").getAsString());
  }

  private static JsonObject resource(String path) throws Exception {
    try (var stream = FixesDataParityTest.class.getResourceAsStream("/" + path)) {
      assertNotNull(stream, path);
      return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }
  }
}
