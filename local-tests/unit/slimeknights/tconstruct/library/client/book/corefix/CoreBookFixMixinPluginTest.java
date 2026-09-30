package slimeknights.tconstruct.library.client.book.corefix;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * File: CoreBookFixMixinPluginTest.java (Myrlin's Tinker Remaster remaster.1, 2026-09-30).
 *
 * Why: the book fix mixins must apply on stock Continuum Core 1.12.1 and must stay out of the way on the private Core
 * fork (1.12.1-arthur.1), which already carries the same fixes; applying them there would fail the injection checks
 * or draw twice. This runs the plugin's stock checks against the real class files of both jars.
 */
public class CoreBookFixMixinPluginTest {
  /** Every mixin of tconstruct.client.mixins.json and the Core class it targets */
  private static final Map<String, String> TARGETS = new LinkedHashMap<>();
  static {
    TARGETS.put("BookDataFontMixin", "slimeknights/mantle/client/book/data/BookData");
    TARGETS.put("BookScreenFontMixin", "slimeknights/mantle/client/screen/book/BookScreen");
    TARGETS.put("BookScreenTextMixin", "slimeknights/mantle/client/screen/book/BookScreen");
    TARGETS.put("ContentCraftingMixin", "slimeknights/mantle/client/book/data/content/ContentCrafting");
    TARGETS.put("ContentSmeltingMixin", "slimeknights/mantle/client/book/data/content/ContentSmelting");
    TARGETS.put("MultiModuleScreenMixin", "slimeknights/mantle/client/screen/MultiModuleScreen");
    TARGETS.put("SelectionElementMixin", "slimeknights/mantle/client/screen/book/element/SelectionElement");
    TARGETS.put("TextComponentDataRendererMixin", "slimeknights/mantle/client/screen/book/TextComponentDataRenderer");
    TARGETS.put("TextDataRendererMixin", "slimeknights/mantle/client/screen/book/TextDataRenderer");
  }

  private static Path projectRoot() {
    String root = System.getProperty("aebm.continuum.projectRoot");
    assertNotNull(root, "aebm.continuum.projectRoot is not set; run through Gradle");
    return Path.of(root);
  }

  /** The Core jar the build compiles and tests against (stock 1.12.1 from CurseForge) */
  private static Path stockCoreJar() {
    String jar = System.getProperty("aebm.continuum.coreJar");
    assertNotNull(jar, "aebm.continuum.coreJar is not set; run through Gradle");
    return Path.of(jar);
  }

  private static ClassNode read(ZipFile zip, String internalName) throws IOException {
    ZipEntry entry = zip.getEntry(internalName + ".class");
    assertNotNull(entry, internalName + " missing from " + zip.getName());
    try (InputStream in = zip.getInputStream(entry)) {
      ClassNode node = new ClassNode();
      new ClassReader(in.readAllBytes()).accept(node, 0);
      return node;
    }
  }

  @Test
  void configListsExactlyTheCheckedMixins() throws IOException {
    Path config = projectRoot().resolve("src/main/resources/tconstruct.client.mixins.json");
    JsonElement json = JsonParser.parseString(Files.readString(config, StandardCharsets.UTF_8));
    TreeSet<String> listed = new TreeSet<>();
    for (JsonElement e : json.getAsJsonObject().getAsJsonArray("client")) {
      String name = e.getAsString();
      listed.add(name.substring(name.lastIndexOf('.') + 1));
    }
    assertEquals(new TreeSet<>(TARGETS.keySet()), listed);
    assertEquals(CoreBookFixMixinPlugin.class.getName(), json.getAsJsonObject().get("plugin").getAsString());
  }

  @Test
  void everyMixinAppliesToStockCore() throws IOException {
    Path jar = stockCoreJar();
    assertTrue(Files.isRegularFile(jar), "stock Core jar missing: " + jar);
    List<String> problems = new ArrayList<>();
    try (ZipFile zip = new ZipFile(jar.toFile())) {
      // the stock jar must not be the fork
      assumeTrue(zip.getEntry("slimeknights/mantle/client/book/BookRecipes.class") == null, "the build Core jar is the fork, not stock");
      for (Map.Entry<String, String> target : TARGETS.entrySet()) {
        String mismatch = CoreBookFixMixinPlugin.checkStock(target.getKey(), read(zip, target.getValue()));
        if (mismatch != null) {
          problems.add(target.getKey() + ": " + mismatch);
        }
      }
    }
    assertEquals(List.of(), problems, "mixins that would be skipped on stock Core 1.12.1");
  }

  @Test
  void everyMixinStandsDownOnTheCoreFork() throws IOException {
    Path fork = projectRoot().resolveSibling("release").resolve("ContinuumCore-26.1.2-1.12.1-arthur.1.jar");
    assumeTrue(Files.isRegularFile(fork), "Core fork jar not present: " + fork);
    List<String> applied = new ArrayList<>();
    try (ZipFile zip = new ZipFile(fork.toFile())) {
      assertNotNull(zip.getEntry("slimeknights/mantle/client/book/BookRecipes.class"), "not the Core fork");
      for (Map.Entry<String, String> target : TARGETS.entrySet()) {
        if (CoreBookFixMixinPlugin.checkStock(target.getKey(), read(zip, target.getValue())) == null) {
          applied.add(target.getKey());
        }
      }
    }
    assertEquals(List.of(), applied, "mixins that would apply a second time on the Core fork");
  }

  @Test
  void unknownMixinIsNeverApplied() {
    assertNotNull(CoreBookFixMixinPlugin.checkStock("SomethingElseMixin", new ClassNode()));
  }
}
