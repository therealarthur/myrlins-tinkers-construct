package aebm.continuumtests;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Balance numbers that differ between Continuum and official TConstruct v3.12.1 (parity/modifiers and parity/balance,
 * added 2026-09-29). The same file ships on both branches. Each balance domain is one commit on parity/balance, so each
 * domain is checked on its own: one anchor value decides whether the checkout has the Continuum or the official numbers
 * for that domain, and every other number in the domain must then match the same side. parity/modifiers is expected to
 * report "continuum" for every domain and parity/balance "official"; the detected side is printed for the build log.
 * Modifier files are also run through the real loader.
 */
final class BalanceValuesParityTest {
  /** Plating shield durability factors (durability = factor x 18 official, factor x 22 Continuum). */
  private static final String[] SHIELD_FACTORS = {
    "allthemodium:2048", "aluminum:13", "amethyst_bronze:28", "ancient:25", "blazing_crystal:19", "bronze:28", "certus_quartz:13",
    "cinderslime:42", "cobalt:30", "conductive_alloy:22", "constantan:25", "copper:13", "dark_steel:35", "electrum:14", "end_steel:42",
    "energetic_alloy:24", "energized_steel:21", "entro:28", "fiery:25", "fluix:22", "gold:7", "hepatizon:32", "invar:24", "iron:15",
    "knightmetal:20", "knightslime:33", "lead:12", "manyullyn:35", "nicrosil:28", "niotic_crystal:24", "nitro_crystal:38", "obsidian:11",
    "osmium:25", "pewter:16", "pig_iron:23", "pulsating_alloy:20", "quantum_alloy:32", "queens_slime:50", "redstone_alloy:8",
    "rose_gold:9", "scorched_stone:10", "seared_stone:14", "silver:18", "slimesteel:40", "soularium:18", "spirited_crystal:30",
    "steel:29", "steeleaf:10", "unobtainium:2048", "uraninite:12", "vibranium:2048", "vibrant_alloy:30"};

  @Test
  void modifierNumbersAreAllContinuumOrAllOfficial() throws Exception {
    // anchor: dragonborn airborne protection, 2.5 Continuum, 2.0 official
    float dragonborn = module("dragonborn", "tconstruct:protection", null).get("each_level").getAsFloat();
    boolean official = dragonborn == 2.0f;
    assertTrue(official || dragonborn == 2.5f, "unexpected dragonborn protection " + dragonborn);
    report("modifiers", official);
    Pick pick = new Pick(official);

    assertEquals(pick.of(0.15f, 0.10f), module("entwined", "tconstruct:attribute", "minecraft:movement_speed").get("each_level").getAsFloat(), 1e-6, "entwined movement speed per level");

    JsonObject protection = module("featherweight", "tconstruct:protection", null);
    assertEquals(pick.of(-1.25f, -0.625f), protection.get("each_level").getAsFloat(), 1e-6, "featherweight protection per level");
    assertEquals(pick.of(0.10f, 0.05f), module("featherweight", "tconstruct:attribute", "tconstruct:player.use_item_speed").get("each_level").getAsFloat(), 1e-6, "featherweight use item speed");
    JsonObject movement = find("featherweight", "tconstruct:attribute", "minecraft:movement_speed");
    if (official) {
      assertNotNull(movement, "official featherweight grants movement speed");
      assertEquals(0.05f, movement.get("each_level").getAsFloat(), 1e-6);
      assertEquals("add_multiplied_base", movement.get("operation").getAsString());
    } else {
      assertNull(movement, "Continuum featherweight has no movement speed module");
    }

    JsonObject boon = module("boon_of_sssss", "tconstruct:attribute", "tconstruct:generic.good_effect_duration_multiplier");
    assertEquals(pick.of(0.25f, 0.10f), boon.get("each_level").getAsFloat(), 1e-6, "boon per level");
    assertEquals(pick.of(0f, 0.15f), flat(boon), 1e-6, "boon flat");

    JsonArray dodging = ModifierJsonLoader.readModifier("enderdodging").getAsJsonArray("modules");
    JsonObject first = dodging.get(0).getAsJsonObject().getAsJsonObject("chance");
    JsonObject second = dodging.get(1).getAsJsonObject().getAsJsonObject("chance");
    assertEquals(pick.of(0.10f, 0.15f), first.get("each_level").getAsFloat(), 1e-6, "enderdodging first per level");
    assertEquals(pick.of(0.10f, 0f), flat(first), 1e-6, "enderdodging first flat");
    assertEquals(pick.of(0.05f, 0.075f), second.get("each_level").getAsFloat(), 1e-6, "enderdodging second per level");
    assertEquals(pick.of(0.05f, 0f), flat(second), 1e-6, "enderdodging second flat");

    JsonObject clearance = ModifierJsonLoader.readModifier("enderclearance");
    JsonObject chance = clearance.getAsJsonArray("modules").get(0).getAsJsonObject().getAsJsonObject("chance");
    assertEquals(pick.of(0.25f, 0f), chance.has("each_level") ? chance.get("each_level").getAsFloat() : 0f, 1e-6, "enderclearance per level");
    assertEquals(pick.of(0f, 0.25f), flat(chance), 1e-6, "enderclearance flat");
    assertEquals(official ? "tconstruct:single_level" : "tconstruct:default", clearance.get("level_display").getAsString());

    assertEquals(pick.of(0.05f, 0.10f), module("unburdened", "tconstruct:attribute", "tconstruct:player.use_item_speed").get("each_level").getAsFloat(), 1e-6, "unburdened armor use item speed");

    JsonObject solar = ModifierJsonLoader.readModifier("solar_powered");
    assertEquals(official ? "[0.01,\"$level\",\"*\",0.04,\"+\",\"$light\",\"*\"]" : "[\"$light\",0.05,\"*\"]",
      solar.getAsJsonArray("modules").get(0).getAsJsonObject().get("formula").toString(), "solar powered formula");
    assertEquals(official ? "tconstruct:single_level" : "tconstruct:default", solar.get("level_display").getAsString());

    for (String path : new String[]{"dragonborn", "entwined", "featherweight", "boon_of_sssss", "enderdodging", "enderclearance", "unburdened", "solar_powered"}) {
      assertNotNull(ModifierJsonLoader.load(path), path + " must load with the real parser");
    }
  }

  @Test
  void plateShieldDurabilityUsesOneFactorForEveryMaterial() throws Exception {
    int anchor = shield("iron");
    boolean official = anchor == 15 * 18;
    assertTrue(official || anchor == 15 * 22, "unexpected iron plating shield durability " + anchor);
    report("material stats", official);
    int multiplier = official ? 18 : 22;
    List<String> wrong = new ArrayList<>();
    for (String entry : SHIELD_FACTORS) {
      String[] parts = entry.split(":");
      int expected = Integer.parseInt(parts[1]) * multiplier;
      int actual = shield(parts[0]);
      if (actual != expected) {
        wrong.add(parts[0] + " expected " + expected + " got " + actual);
      }
    }
    assertTrue(wrong.isEmpty(), "plating shield durability mismatches: " + wrong);
  }

  @Test
  void materialTiersAndSortOrder() throws Exception {
    int blaze = definition("blaze").get("tier").getAsInt();
    boolean official = blaze == 3;
    assertTrue(official || blaze == 2, "unexpected blaze tier " + blaze);
    report("material definitions", official);
    assertEquals(official ? 3 : 2, definition("ender_pearl").get("tier").getAsInt(), "ender pearl tier");
    assertEquals(official ? 2 : 5, definition("blood").get("tier").getAsInt(), "blood tier");
    for (String material : new String[]{"glowstone", "ichor", "kobold", "magma", "quartz"}) {
      assertEquals(official ? 35 : 25, definition(material).get("sortOrder").getAsInt(), material + " sort order");
    }
  }

  @Test
  void armorAttackMultipliersAndWingDurability() throws Exception {
    float plate = multiplier("plate_chestplate");
    boolean official = plate == 0.5f;
    assertTrue(official || Math.abs(plate - 0.4f) < 1e-6, "unexpected plate chestplate multiplier " + plate);
    report("tool definitions", official);
    assertEquals(official ? 0.6f : 0.55f, multiplier("travelers_chestplate"), 1e-6, "travelers chestplate");
    assertEquals(official ? 0.75f : 0.6f, multiplier("slime_chestplate"), 1e-6, "slime chestplate");
    assertEquals(official ? 0.5f : 0.4f, multiplier("slime_wings"), 1e-6, "slime wings");
    JsonObject base = null;
    for (JsonElement element : ModifierJsonLoader.readGenerated("tinkering/tool_definitions/slime_wings.json").getAsJsonArray("modules")) {
      if (element.getAsJsonObject().get("type").getAsString().equals("tconstruct:base_stats")) {
        base = element.getAsJsonObject();
      }
    }
    if (official) {
      assertNotNull(base, "official slime wings set base durability");
      assertEquals(222f, base.getAsJsonObject("stats").get("tconstruct:durability").getAsFloat(), 1e-6);
    } else {
      assertNull(base, "Continuum slime wings have no base durability");
    }
  }

  @Test
  void mobEquipmentMaterials() throws Exception {
    String anchor = materials("drowned").get(0).getAsJsonObject().get("type").getAsString();
    boolean official = anchor.equals("tconstruct:ancient");
    assertTrue(official || anchor.equals("tconstruct:fixed"), "unexpected drowned material type " + anchor);
    report("mob equipment", official);
    Object[][] expected = {{"drowned", 3}, {"melting_pan", 2}, {"piglins", 2}, {"war_pick", 3}, {"wither_skeleton", 3}};
    for (Object[] row : expected) {
      JsonArray materials = materials((String) row[0]);
      for (JsonElement element : materials) {
        assertEquals(official ? "tconstruct:ancient" : "tconstruct:fixed", element.getAsJsonObject().get("type").getAsString(), row[0] + " material type");
      }
      if (official) {
        assertEquals((int) row[1], materials.size(), row[0] + " material count must match the tool's parts");
      }
    }
  }

  /* helpers */

  /** Picks the Continuum or the official value for the detected side. */
  private record Pick(boolean official) {
    float of(float continuum, float officialValue) {
      return official ? officialValue : continuum;
    }
  }

  private static void report(String domain, boolean official) {
    System.out.println("AEBM_BALANCE_DOMAIN " + domain + "=" + (official ? "official" : "continuum"));
  }

  private static float flat(JsonObject object) {
    return object.has("flat") ? object.get("flat").getAsFloat() : 0f;
  }

  private static int shield(String material) throws Exception {
    return ModifierJsonLoader.readGenerated("tinkering/materials/stats/" + material + ".json")
      .getAsJsonObject("stats").getAsJsonObject("tconstruct:plating_shield").get("durability").getAsInt();
  }

  private static JsonObject definition(String material) throws Exception {
    return ModifierJsonLoader.readGenerated("tinkering/materials/definition/" + material + ".json");
  }

  private static float multiplier(String tool) throws Exception {
    for (JsonElement element : ModifierJsonLoader.readGenerated("tinkering/tool_definitions/" + tool + ".json").getAsJsonArray("modules")) {
      JsonObject module = element.getAsJsonObject();
      if (module.get("type").getAsString().equals("tconstruct:multiply_stats") && module.getAsJsonObject("multipliers").has("tconstruct:attack_damage")) {
        return module.getAsJsonObject("multipliers").get("tconstruct:attack_damage").getAsFloat();
      }
    }
    throw new AssertionError(tool + " has no attack damage multiplier");
  }

  private static JsonArray materials(String equipment) throws Exception {
    return ModifierJsonLoader.readGenerated("tinkering/mob_equipment/" + equipment + ".json").getAsJsonObject("equip").getAsJsonArray("materials");
  }

  private static JsonObject module(String modifier, String type, String attribute) throws Exception {
    JsonObject found = find(modifier, type, attribute);
    assertNotNull(found, modifier + " has no " + type + (attribute == null ? "" : " for " + attribute));
    return found;
  }

  private static JsonObject find(String modifier, String type, String attribute) throws Exception {
    for (JsonElement element : ModifierJsonLoader.readModifier(modifier).getAsJsonArray("modules")) {
      JsonObject module = element.getAsJsonObject();
      if (module.get("type").getAsString().equals(type) && (attribute == null || attribute.equals(module.has("attribute") ? module.get("attribute").getAsString() : null))) {
        return module;
      }
    }
    return null;
  }
}
