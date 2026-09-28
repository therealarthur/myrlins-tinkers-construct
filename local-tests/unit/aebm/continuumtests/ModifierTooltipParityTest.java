package aebm.continuumtests;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.netty.buffer.Unpooled;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierManager;
import slimeknights.tconstruct.library.modifiers.impl.BasicModifier;
import slimeknights.tconstruct.library.modifiers.impl.BasicModifier.TooltipDisplay;
import slimeknights.tconstruct.library.modifiers.impl.ComposableModifier;
import slimeknights.tconstruct.library.modifiers.util.ModifierLevelDisplay;
import slimeknights.tconstruct.library.modifiers.util.ModifierTooltip;
import slimeknights.tconstruct.library.modifiers.util.ModifierTooltip.ShowInTooltips;
import slimeknights.tconstruct.library.modifiers.util.ModifierTooltip.TooltipsLoadable;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.helper.TooltipBuilder;
import slimeknights.tconstruct.library.tools.helper.TooltipUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import static org.junit.jupiter.api.Assertions.*;
import static slimeknights.tconstruct.library.modifiers.util.ModifierTooltip.*;

/** Real policy loaders and tooltip consumers; no screens, font or Minecraft singleton is used. */
@SuppressWarnings("removal")
final class ModifierTooltipParityTest {
  @BeforeAll
  static void components() { ComponentTestSetup.initialize(); }

  @Test
  void originalPresetsAndArbitrarySetsSurviveJsonAndNetwork() {
    Map<String, Set<ModifierTooltip>> expected = Map.of(
      "always", EnumSet.allOf(ModifierTooltip.class), "never", Set.of(),
      "parts_only", Set.of(TOOL_PART, PART_BUILDER, BOOK),
      "bonus_slot", Set.of(TINKER_STATION, TOOL_PART, PART_BUILDER, BOOK),
      "advanced", Set.of(TINKER_STATION, PART_BUILDER, BOOK));
    for (var entry : expected.entrySet()) {
      ShowInTooltips policy = TooltipsLoadable.INSTANCE.convert(json('"' + entry.getKey() + '"'), "policy", TypedMap.EMPTY);
      check(policy, entry.getValue());
      assertEquals(entry.getKey(), TooltipsLoadable.INSTANCE.serialize(policy).getAsString());
      roundtrip(policy);
    }
    var custom = TooltipsLoadable.INSTANCE.convert(json("[\"tool\",\"book\"]"), "policy", TypedMap.EMPTY);
    check(custom, Set.of(TOOL, BOOK));
    assertEquals(custom, TooltipsLoadable.INSTANCE.convert(TooltipsLoadable.INSTANCE.serialize(custom), "roundtrip", TypedMap.EMPTY));
    roundtrip(custom);
    assertThrows(JsonSyntaxException.class, () -> TooltipsLoadable.INSTANCE.convert(json("[\"unknown_context\"]"), "policy", TypedMap.EMPTY));
  }

  @Test
  void modernPolicyOverridesLegacyAndLegacyNeverStillShowsMaterialTraits() {
    check(load("{}").shouldDisplay(TOOL), true);
    for (var entry : Map.of("always", ShowInTooltips.ALWAYS, "tinker_station", ShowInTooltips.BONUS_SLOT, "never", ShowInTooltips.PARTS_ONLY).entrySet()) {
      var modifier = load("{\"tooltip_display\":\"" + entry.getKey() + "\"}");
      for (ModifierTooltip context : values()) assertEquals(entry.getValue().test(context), modifier.shouldDisplay(context));
      JsonElement serialized = ComposableModifier.LOADER.serialize(modifier);
      assertFalse(serialized.getAsJsonObject().has("tooltip_display"));
      var restored = ComposableModifier.LOADER.convert(serialized, "roundtrip", TypedMap.EMPTY);
      for (ModifierTooltip context : values()) assertEquals(modifier.shouldDisplay(context), restored.shouldDisplay(context));
    }
    var modern = load("{\"tooltip_display\":\"invalid_ignored_legacy_value\",\"show_in_tooltips\":[\"tool\",\"book\"]}");
    for (ModifierTooltip context : values()) assertEquals(context == TOOL || context == BOOK, modern.shouldDisplay(context));
    var hidden = load("{\"tooltip_display\":\"always\",\"show_in_tooltips\":\"never\"}");
    for (ModifierTooltip context : values()) assertFalse(hidden.shouldDisplay(context));
    assertFalse(ComposableModifier.LOADER.serialize(load("{}")).getAsJsonObject().has("show_in_tooltips"), "default always should be omitted");
  }

  @Test
  void originalBooleanOverridesRemainEffectiveInTheirExistingContexts() {
    Modifier legacy = new Modifier() {
      @Override public boolean shouldDisplay(boolean advanced) { return advanced; }
    };
    assertFalse(legacy.shouldDisplay(TOOL));
    assertTrue(legacy.shouldDisplay(TINKER_STATION));
    for (ModifierTooltip context : List.of(TOOL_PART, PART_BUILDER, BOOK)) assertTrue(legacy.shouldDisplay(context), "legacy API never filtered these contexts");
    Modifier hidden = new Modifier() {
      @Override public boolean shouldDisplay(boolean advanced) { return false; }
    };
    assertFalse(hidden.shouldDisplay(TOOL));
    assertFalse(hidden.shouldDisplay(TINKER_STATION));
    assertTrue(hidden.shouldDisplay(BOOK));
    for (ModifierTooltip context : values()) assertFalse(ModifierManager.getValue(new ModifierId("aebm_test:missing_tooltip_modifier")).shouldDisplay(context), "unbound placeholder must remain hidden everywhere");
  }

  @Test
  void oldConstructorsAndBuilderCallsAgreeWithNewPolicyApi() {
    var hooks = ModuleHookMap.builder().build();
    for (TooltipDisplay display : TooltipDisplay.values()) {
      List<Modifier> legacy = List.of(
        new BasicModifier(hooks, ModifierLevelDisplay.DEFAULT, display, 117),
        new BasicModifier.Builder(hooks).tooltipDisplay(display).build(),
        BasicModifier.Builder.builder(hooks).tooltipDisplay(display).build(),
        ComposableModifier.builder().tooltipDisplay(display).build());
      for (Modifier modifier : legacy) {
        for (ModifierTooltip context : values()) assertEquals(display.getShowInTooltips().test(context), modifier.shouldDisplay(context));
        assertEquals(display == TooltipDisplay.ALWAYS, modifier.shouldDisplay(false));
        assertEquals(display != TooltipDisplay.NEVER, modifier.shouldDisplay(true));
      }
    }
    for (Modifier modifier : List.of(BasicModifier.Builder.builder(hooks).showInTooltips(TOOL, BOOK).build(), ComposableModifier.builder().showInTooltips(TOOL, BOOK).build())) {
      for (ModifierTooltip context : values()) assertEquals(context == TOOL || context == BOOK, modifier.shouldDisplay(context));
      assertTrue(modifier.shouldDisplay(false));
      assertFalse(modifier.shouldDisplay(true));
    }
  }

  @Test
  void realToolTooltipAndBuilderFilterByTheRequestedContext() {
    List<ModifierEntry> entries = List.of(
      new ModifierEntry(named("always", ShowInTooltips.ALWAYS), 1),
      new ModifierEntry(named("parts", ShowInTooltips.PARTS_ONLY), 1),
      new ModifierEntry(named("advanced", ShowInTooltips.ADVANCED), 1),
      new ModifierEntry(named("never", ShowInTooltips.NEVER), 1));
    IToolStackView tool = (IToolStackView)Proxy.newProxyInstance(IToolStackView.class.getClassLoader(), new Class<?>[] {IToolStackView.class},
      (proxy, method, arguments) -> {
        if (method.getName().equals("getModifierList")) return entries;
        throw new AssertionError("Unexpected tool access: " + method.getName());
      });
    List<Component> tooltip = new ArrayList<>();
    TooltipUtil.addModifierNames(ItemStack.EMPTY, tool, null, tooltip, TooltipFlag.Default.NORMAL);
    assertEquals(List.of("always"), names(tooltip));
    assertEquals(List.of("always", "advanced"), names(new TooltipBuilder(tool).addModifierInfo(TINKER_STATION, null).getTooltips()));
    assertEquals(List.of("always", "parts"), names(new TooltipBuilder(tool).addModifierInfo(TOOL_PART, null).getTooltips()));
    assertEquals(List.of("always", "parts", "advanced"), names(new TooltipBuilder(tool).addModifierInfo(PART_BUILDER, null).getTooltips()));
    assertEquals(List.of("always", "parts", "advanced"), names(new TooltipBuilder(tool).addModifierInfo(BOOK, null).getTooltips()));
    assertEquals(List.of("always", "advanced"), names(new TooltipBuilder(tool).addModifierInfo(true, null).getTooltips()));
  }

  @Test
  void nineShippedPolicyCorrectionsLoadWithOriginalVisibility() throws Exception {
    Map<String, ShowInTooltips> expected = Map.of(
      "banner", ShowInTooltips.match(TINKER_STATION), "dyed", ShowInTooltips.match(TINKER_STATION),
      "embellishment", ShowInTooltips.match(TINKER_STATION), "rebalanced", ShowInTooltips.match(TINKER_STATION),
      "trim", ShowInTooltips.match(TINKER_STATION), "edible_tooltip", ShowInTooltips.NEVER,
      "iron_armor", ShowInTooltips.NEVER, "pocket", ShowInTooltips.NEVER, "overslime_friend", ShowInTooltips.ADVANCED);
    for (var entry : expected.entrySet()) {
      try (var input = getClass().getResourceAsStream("/data/tconstruct/tinkering/modifiers/" + entry.getKey() + ".json")) {
        assertNotNull(input, "missing shipped modifier: " + entry.getKey());
        JsonElement resource = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        var modifier = ComposableModifier.LOADER.convert(resource, entry.getKey(), TypedMap.EMPTY);
        for (ModifierTooltip context : values()) assertEquals(entry.getValue().test(context), modifier.shouldDisplay(context), entry.getKey() + ':' + context);
      }
    }
  }

  private static Modifier named(String name, ShowInTooltips policy) {
    return new BasicModifier(ModuleHookMap.builder().build(), ModifierLevelDisplay.DEFAULT, policy, 100) {
      @Override public ModifierId getId() { return new ModifierId("aebm_test:" + name); }
      @Override public Component getDisplayName(IToolStackView tool, ModifierEntry entry, RegistryAccess access) { return Component.literal(name); }
    };
  }
  private static List<String> names(List<Component> components) { return components.stream().map(Component::getString).toList(); }
  private static JsonElement json(String text) { return JsonParser.parseString(text); }
  private static ComposableModifier load(String text) { return ComposableModifier.LOADER.convert(json(text), "tooltip_test", TypedMap.EMPTY); }
  private static void check(boolean actual, boolean expected) { assertEquals(expected, actual); }
  private static void check(ShowInTooltips policy, Set<ModifierTooltip> expected) {
    for (ModifierTooltip context : values()) assertEquals(expected.contains(context), policy.test(context), context.name());
  }
  private static void roundtrip(ShowInTooltips policy) {
    var buffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      TooltipsLoadable.INSTANCE.encode(buffer, policy);
      var decoded = TooltipsLoadable.INSTANCE.decode(buffer, TypedMap.EMPTY);
      assertEquals(policy, decoded);
      assertFalse(buffer.isReadable());
    } finally { buffer.release(); }
  }
}
