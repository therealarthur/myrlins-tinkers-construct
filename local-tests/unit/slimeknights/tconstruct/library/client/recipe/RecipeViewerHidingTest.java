package slimeknights.tconstruct.library.client.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.registration.object.FluidObject;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.EntityValue;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.MaterialValue;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.ModifierValue;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.PatternValue;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.SlotValue;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.smeltery.data.SmelteryCompat;
import slimeknights.tconstruct.tools.data.material.MaterialIds;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * File: RecipeViewerHidingTest.java (Myrlin's Tinkers' Construct myrlin.2, 2026-09-30).
 *
 * Why: in REI the port listed every compat fluid and bucket (molten vibranium and the rest) even without the metal,
 * plus hundreds of material, pattern and entity entries that the list drew as bare text or faint outlines, and the
 * queens slime block model baked to nothing. Official 3.12.1 hides absent compat in JEIPlugin#onRuntimeAvailable and
 * never lists materials, patterns or entities. These pin the shared hiding rules and the model shape.
 */
public class RecipeViewerHidingTest {
  /** Every compat fluid the rule can hide: official SmelteryCompat, the port's three extra metals, porcelain */
  private static List<FluidObject<?>> allCompat() {
    List<FluidObject<?>> all = new ArrayList<>();
    for (SmelteryCompat compat : SmelteryCompat.values()) all.add(compat.getFluid());
    all.add(TinkerFluids.moltenAllthemodium);
    all.add(TinkerFluids.moltenVibranium);
    all.add(TinkerFluids.moltenUnobtainium);
    all.add(TinkerFluids.moltenPorcelain);
    return all;
  }

  @Test
  void everyCompatFluidIsHiddenWhenNothingIsPresent() {
    List<FluidObject<?>> hidden = RecipeViewerHiding.absentCompatFluids(ingot -> false, material -> false, false);
    assertEquals(allCompat(), hidden);
    assertTrue(hidden.contains(TinkerFluids.moltenVibranium), "molten vibranium without the metal");
  }

  @Test
  void anIngotTagShowsItsCompat() {
    List<FluidObject<?>> hidden = RecipeViewerHiding.absentCompatFluids(ingot -> ingot.equals("vibranium") || ingot.equals("tin"), material -> false, false);
    assertFalse(hidden.contains(TinkerFluids.moltenVibranium));
    assertFalse(hidden.contains(TinkerFluids.moltenTin));
    assertTrue(hidden.contains(TinkerFluids.moltenAllthemodium));
    assertTrue(hidden.contains(TinkerFluids.moltenLead));
  }

  @Test
  void aLoadedMaterialShowsItsCompatWithoutTheIngot() {
    List<FluidObject<?>> hidden = RecipeViewerHiding.absentCompatFluids(ingot -> false,
      material -> material.equals(MaterialIds.bronze) || material.equals(MaterialIds.necronium) || material.equals(MaterialIds.unobtainium), false);
    assertFalse(hidden.contains(TinkerFluids.moltenBronze));
    // official: uranium shows with the necronium material
    assertFalse(hidden.contains(TinkerFluids.moltenUranium));
    assertFalse(hidden.contains(TinkerFluids.moltenUnobtainium));
    assertTrue(hidden.contains(TinkerFluids.moltenTin), "tin has no material, only the ingot counts");
  }

  @Test
  void porcelainNeedsCeramics() {
    assertTrue(RecipeViewerHiding.absentCompatFluids(ingot -> true, material -> true, false).contains(TinkerFluids.moltenPorcelain));
    assertFalse(RecipeViewerHiding.absentCompatFluids(ingot -> true, material -> true, true).contains(TinkerFluids.moltenPorcelain));
  }

  @Test
  void nothingIsHiddenWhenEverythingIsPresent() {
    assertEquals(List.of(), RecipeViewerHiding.absentCompatFluids(ingot -> true, material -> true, true));
  }

  @Test
  void onlyModifiersAndSlotsAreListedLikeOfficial() {
    ModifierValue modifier = new ModifierValue(new ModifierEntry(new ModifierId("tconstruct", "haste"), 1));
    assertTrue(RecipeViewerHiding.listedInViewer(modifier, true));
    assertFalse(RecipeViewerHiding.listedInViewer(modifier, false), "showModifiersInJEI off");
    assertTrue(RecipeViewerHiding.listedInViewer(new SlotValue(new SlotType.SlotCount(SlotType.UPGRADE, 1)), true));
    MaterialVariantId iron = MaterialVariantId.tryParse("tconstruct:iron");
    assertNotNull(iron);
    assertFalse(RecipeViewerHiding.listedInViewer(new MaterialValue(iron, 1), true));
    assertFalse(RecipeViewerHiding.listedInViewer(new PatternValue(new Pattern(Identifier.fromNamespaceAndPath("tconstruct", "pick_head"))), true));
    assertFalse(RecipeViewerHiding.listedInViewer(new EntityValue(EntityType.ZOMBIE, false), true));
  }

  /**
   * Continuum Core's mantle:colored_block loader bakes only the model's own "elements" (it does not inherit the
   * parent's), so a model using it without elements is invisible, as the queens slime block was.
   */
  @Test
  void coloredBlockModelsDefineTheirOwnElements() throws IOException {
    String root = System.getProperty("aebm.continuum.projectRoot");
    assertNotNull(root, "aebm.continuum.projectRoot is not set; run through Gradle");
    List<String> missing = new ArrayList<>();
    int checked = 0;
    for (String dir : List.of("src/main/resources/assets", "src/generated/resources/assets", "src/generated/client/assets")) {
      Path base = Path.of(root).resolve(dir);
      if (!Files.isDirectory(base)) continue;
      try (Stream<Path> files = Files.walk(base)) {
        for (Path file : files.filter(p -> p.toString().endsWith(".json") && p.toString().replace('\\', '/').contains("/models/")).toList()) {
          String text = Files.readString(file, StandardCharsets.UTF_8);
          if (!text.contains("mantle:colored_block")) continue;
          JsonObject json = JsonParser.parseString(text).getAsJsonObject();
          if (!json.has("loader") || !"mantle:colored_block".equals(json.get("loader").getAsString())) continue;
          checked++;
          if (!json.has("elements") || json.getAsJsonArray("elements").isEmpty()) {
            missing.add(Path.of(root).relativize(file).toString());
          }
        }
      }
    }
    assertTrue(checked > 0, "expected at least the queens slime block model");
    assertEquals(List.of(), missing, "colored block models without elements render nothing");
  }
}
