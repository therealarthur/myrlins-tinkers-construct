package aebm.continuumtests;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.conditions.ICondition;
import slimeknights.tconstruct.library.json.TinkerEnchantmentLoadable;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierManager;

/**
 * Test helper for the parity/modifiers stream (added 2026-09-29).
 * Reads this checkout's generated data files and runs them through the real {@link ModifierManager} parser,
 * the same private method the datapack reload uses, with an isolated manager instance.
 * Vanilla enchantments come from {@link VanillaRegistries} so enchantment modules resolve without a server.
 */
final class ModifierJsonLoader {
  private static boolean enchantmentsReady;

  private ModifierJsonLoader() {}

  /** Root of this checkout, passed in by build.gradle as {@code aebm.continuum.projectRoot}. */
  static Path projectRoot() {
    String root = System.getProperty("aebm.continuum.projectRoot");
    if (root == null || root.isBlank()) {
      throw new IllegalStateException("aebm.continuum.projectRoot is not set; run through Gradle");
    }
    return Path.of(root);
  }

  /** Path to a generated tconstruct data file, for example {@code tinkering/modifiers/rugged.json}. */
  static Path generated(String relative) {
    return projectRoot().resolve("src/generated/resources/data/tconstruct").resolve(relative);
  }

  /** Reads a generated tconstruct data file as JSON. */
  static JsonObject readGenerated(String relative) throws IOException {
    return JsonParser.parseString(Files.readString(generated(relative), StandardCharsets.UTF_8)).getAsJsonObject();
  }

  /** Reads the generated modifier JSON for the given tconstruct modifier path. */
  static JsonObject readModifier(String path) throws IOException {
    return readGenerated("tinkering/modifiers/" + path + ".json");
  }

  /**
   * Parses modifier JSON with the real loader. Load conditions are removed first so that modifiers gated on absent
   * mods (headlight, the one probe) or empty tags still have their definition checked.
   */
  static Modifier load(String path, JsonObject json) throws Exception {
    return load(path, json, new HashMap<>());
  }

  /** As {@link #load(String, JsonObject)}, collecting redirect entries into the given map like the reload does. */
  static Modifier load(String path, JsonObject json, java.util.Map<slimeknights.tconstruct.library.modifiers.ModifierId, slimeknights.tconstruct.library.modifiers.ModifierId> redirects) throws Exception {
    prepare();
    JsonObject copy = json.deepCopy();
    copy.remove("condition");
    copy.remove("conditions");
    copy.remove("neoforge:conditions");
    Constructor<ModifierManager> constructor = ModifierManager.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    ModifierManager manager = constructor.newInstance();
    manager.injectContext(ICondition.IContext.EMPTY, RegistryAccess.EMPTY);
    Method method = ModifierManager.class.getDeclaredMethod("loadModifier", Identifier.class, JsonElement.class, java.util.Map.class);
    method.setAccessible(true);
    return (Modifier) method.invoke(manager, Identifier.fromNamespaceAndPath("tconstruct", path), copy, redirects);
  }

  /** Reads and parses the generated modifier with the real loader. */
  static Modifier load(String path) throws Exception {
    return load(path, readModifier(path));
  }

  /** Makes vanilla data driven enchantments resolvable for enchantment modules, once per test JVM. */
  private static synchronized void prepare() {
    ComponentTestSetup.initialize();
    if (!enchantmentsReady) {
      TinkerEnchantmentLoadable.setLookupProvider(VanillaRegistries.createLookup());
      enchantmentsReady = true;
    }
  }
}
