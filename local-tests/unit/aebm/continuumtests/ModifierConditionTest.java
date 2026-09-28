package aebm.continuumtests;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.conditions.AlwaysCondition;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import net.neoforged.neoforge.common.conditions.NotCondition;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.json.JsonRedirect;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierManager;

import static org.junit.jupiter.api.Assertions.*;

/** Call the real modifier parser with isolated state and FML's actual registered condition codecs. */
final class ModifierConditionTest {
  private static final Identifier ID = Identifier.fromNamespaceAndPath("aebm_test", "conditional_modifier");
  private static final ModifierId TARGET = new ModifierId("aebm_test:redirect_target");

  @Test
  void singularAndArrayConditionsBothSelectActualModifiers() throws Exception {
    for (String field : List.of("condition", "neoforge:conditions")) {
      var present = conditional(field, new ModLoadedCondition("tconstruct"));
      var absent = conditional(field, new ModLoadedCondition("aebm_missing_condition_test"));
      assertNotNull(load(manager(ICondition.IContext.EMPTY), present, new HashMap<>()));
      assertNull(load(manager(ICondition.IContext.EMPTY), absent, new HashMap<>()));
      assertNotNull(load(manager(ICondition.IContext.EMPTY),
        conditional(field, new NotCondition(new ModLoadedCondition("aebm_missing_condition_test"))), new HashMap<>()));
    }
    JsonObject both = conditional("condition", AlwaysCondition.INSTANCE);
    both.add("neoforge:conditions", conditional("neoforge:conditions", new NotCondition(AlwaysCondition.INSTANCE)).get("neoforge:conditions"));
    assertNull(load(manager(ICondition.IContext.EMPTY), both, new HashMap<>()));
  }

  @Test
  void tagConditionsUseTheCurrentReloadContext() throws Exception {
    JsonObject json = JsonParser.parseString("""
      {"neoforge:conditions":[{"type":"neoforge:tag_empty","tag":"aebm_test:reload_tag"}]}
      """).getAsJsonObject();
    assertNotNull(load(manager(ICondition.IContext.EMPTY), json, new HashMap<>()));
    ICondition.IContext filled = new ICondition.IContext() {
      @Override
      public <T> boolean isTagLoaded(TagKey<T> key) {
        return key.location().equals(Identifier.fromNamespaceAndPath("aebm_test", "reload_tag"));
      }

      @Override
      @SuppressWarnings({"unchecked", "rawtypes"})
      public <T> Collection<Holder<T>> getTag(TagKey<T> key) {
        return isTagLoaded(key) ? (List) List.of(Items.STONE.builtInRegistryHolder()) : List.of();
      }
    };
    ModifierManager instance = manager(filled);
    assertNull(load(instance, json, new HashMap<>()));
    setContext(instance, ICondition.IContext.EMPTY);
    assertNotNull(load(instance, json, new HashMap<>()), "a subsequent reload must not reuse the previous condition result");
  }

  @Test
  void redirectsSkipFalseBranchesAndPrecedeFallbackConditions() throws Exception {
    JsonObject json = conditional("condition", new NotCondition(AlwaysCondition.INSTANCE));
    JsonArray branches = new JsonArray();
    branches.add(new JsonRedirect(Identifier.fromNamespaceAndPath("aebm_test", "wrong_target"),
      new ModLoadedCondition("aebm_missing_condition_test")).toJson());
    branches.add(new JsonRedirect(TARGET.getId(), AlwaysCondition.INSTANCE).toJson());
    branches.add(new JsonRedirect(Identifier.fromNamespaceAndPath("aebm_test", "later_target"), null).toJson());
    json.add("redirects", branches);
    Map<ModifierId, ModifierId> redirects = new HashMap<>();
    assertNull(load(manager(ICondition.IContext.EMPTY), json, redirects));
    assertEquals(Map.of(new ModifierId(ID), TARGET), redirects);
  }

  @Test
  void falseRedirectFallsBackToModifierAndArrayRedirectsWork() throws Exception {
    JsonObject json = new JsonObject();
    JsonArray branches = new JsonArray();
    JsonObject branch = conditional("neoforge:conditions", new NotCondition(AlwaysCondition.INSTANCE));
    branch.addProperty("id", TARGET.toString());
    branches.add(branch);
    json.add("redirects", branches);
    Map<ModifierId, ModifierId> redirects = new HashMap<>();
    assertNotNull(load(manager(ICondition.IContext.EMPTY), json, redirects));
    assertTrue(redirects.isEmpty());
    branch.add("neoforge:conditions", conditional("neoforge:conditions", AlwaysCondition.INSTANCE).get("neoforge:conditions"));
    assertNull(load(manager(ICondition.IContext.EMPTY), json, redirects));
    assertEquals(TARGET, redirects.get(new ModifierId(ID)));
  }

  @Test
  void malformedOrUnknownConditionsDoNotEnableModifiersOrRedirects() throws Exception {
    for (String invalid : List.of("{\"type\":\"aebm_test:unknown_condition\"}", "17", "{\"type\":\"neoforge:mod_loaded\"}")) {
      JsonObject json = new JsonObject();
      json.add("condition", JsonParser.parseString(invalid));
      assertNull(load(manager(ICondition.IContext.EMPTY), json, new HashMap<>()));
      json.addProperty("id", TARGET.toString());
      JsonArray branches = new JsonArray();
      branches.add(json);
      JsonObject redirect = new JsonObject();
      redirect.add("redirects", branches);
      Map<ModifierId, ModifierId> redirects = new HashMap<>();
      assertNull(load(manager(ICondition.IContext.EMPTY), redirect, redirects));
      assertTrue(redirects.isEmpty());
    }
  }

  @Test
  void nonObjectDefinitionsAreRejectedAtTheParserBoundary() throws Exception {
    for (String malformed : List.of("17", "[]", "null")) {
      assertNull(load(manager(ICondition.IContext.EMPTY), JsonParser.parseString(malformed), new HashMap<>()));
    }
    Map<ModifierId, ModifierId> redirects = new HashMap<>();
    assertNull(load(manager(ICondition.IContext.EMPTY),
      JsonParser.parseString("{\"redirects\":[{\"id\":\"INVALID ID\"}]}"), redirects));
    assertTrue(redirects.isEmpty());
  }

  @Test
  void generatedRedirectRetainsItsCondition() {
    JsonObject json = new JsonRedirect(TARGET.getId(), new ModLoadedCondition("aebm_missing_condition_test")).toJson();
    assertEquals(TARGET.toString(), json.get("id").getAsString());
    assertFalse(ICondition.CODEC.parse(JsonOps.INSTANCE, json.get("condition")).getOrThrow().test(ICondition.IContext.EMPTY));
    assertFalse(new JsonRedirect(TARGET.getId(), null).toJson().has("condition"));
  }

  private static JsonObject conditional(String field, ICondition condition) {
    JsonObject json = new JsonObject();
    JsonElement encoded = ICondition.CODEC.encodeStart(JsonOps.INSTANCE, condition).getOrThrow();
    if (field.equals("condition")) {
      json.add(field, encoded);
    } else {
      JsonArray array = new JsonArray();
      array.add(encoded);
      json.add(field, array);
    }
    return json;
  }

  private static ModifierManager manager(ICondition.IContext context) throws Exception {
    var constructor = ModifierManager.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    ModifierManager instance = constructor.newInstance();
    setContext(instance, context);
    return instance;
  }

  private static void setContext(ModifierManager instance, ICondition.IContext context) throws Exception {
    instance.injectContext(context, RegistryAccess.EMPTY);
  }

  private static Modifier load(ModifierManager instance, JsonElement json, Map<ModifierId, ModifierId> redirects) throws Exception {
    Method method = ModifierManager.class.getDeclaredMethod("loadModifier", Identifier.class, JsonElement.class, Map.class);
    method.setAccessible(true);
    return (Modifier) method.invoke(instance, ID, json, redirects);
  }
}
