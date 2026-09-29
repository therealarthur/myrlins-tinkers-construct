package aebm.continuumtests;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.json.predicate.TinkerPredicate;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.impl.ComposableModifier;
import slimeknights.tconstruct.library.modifiers.modules.armor.BlockDamageSourceModule;
import slimeknights.tconstruct.library.modifiers.modules.armor.ProtectionModule;
import slimeknights.tconstruct.library.module.WithHooks;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.logic.ModifierEvents;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Airborn and rugged parity (parity/modifiers X1 and X2, 2026-09-29).
 * Loads the generated modifier, tag and material trait data from this checkout with the real parsers and calls the real
 * {@link BlockDamageSourceModule#isDamageBlocked} with damage sources whose type holders carry the tags from the
 * generated rugged tag files. What needs a living world (airborne attackers, cancel semantics in hurtServer, the slime
 * bounce) is covered by ModifierParityServerFixture instead.
 */
final class ArmorTraitParityTest {
  private static final Set<String> OFFICIAL_TERRAIN = Set.of("minecraft:hot_floor", "minecraft:cactus", "minecraft:sweet_berry_bush", "minecraft:stalagmite", "tconstruct:knightmetal");
  private static final Set<String> OFFICIAL_ATTACKS = Set.of("minecraft:cramming", "minecraft:sting", "minecraft:thorns");
  /** Damage types rugged must never block, including the other armor protection families. */
  private static final List<String> CONTROLS = List.of("minecraft:generic", "minecraft:lava", "minecraft:in_fire", "minecraft:on_fire", "minecraft:fall",
    "minecraft:mob_attack", "minecraft:player_attack", "minecraft:arrow", "minecraft:magic", "minecraft:freeze", "minecraft:explosion");

  @Test
  void ruggedTagsMatchOfficialIncludingOptionalTwilightForest() throws Exception {
    assertEquals(OFFICIAL_TERRAIN, required("tags/damage_type/rugged/terrain.json"));
    assertEquals(Set.of("twilightforest:knightmetal", "twilightforest:fiery"), optional("tags/damage_type/rugged/terrain.json"));
    assertEquals(OFFICIAL_ATTACKS, required("tags/damage_type/rugged/attacks.json"));
    assertEquals(Set.of("twilightforest:thorns"), optional("tags/damage_type/rugged/attacks.json"));
  }

  @Test
  void ruggedBlocksTerrainAtLevelOneAndAttacksAtLevelTwoOnly() throws Exception {
    ComposableModifier rugged = assertInstanceOf(ComposableModifier.class, ModifierJsonLoader.load("rugged"));
    List<BlockDamageSourceModule> blockers = modules(rugged, BlockDamageSourceModule.class);
    assertEquals(2, blockers.size(), "rugged must have the terrain and attacks damage blockers");
    // official level display resolves to no_levels (official calls levelDisplay twice, the last call wins)
    assertEquals("\"tconstruct:no_levels\"", ModifierJsonLoader.readModifier("rugged").get("level_display").toString());

    IToolStackView tool = unusedTool();
    ModifierId id = new ModifierId("tconstruct", "rugged");
    for (int level = 1; level <= 2; level++) {
      ModifierEntry entry = new ModifierEntry(id, level);
      for (String type : OFFICIAL_TERRAIN) {
        assertTrue(blocked(blockers, tool, entry, source(type, TinkerTags.DamageTypes.RUGGED_TERRAIN)), type + " must be blocked at level " + level);
      }
      for (String type : OFFICIAL_ATTACKS) {
        assertEquals(level >= 2, blocked(blockers, tool, entry, source(type, TinkerTags.DamageTypes.RUGGED_ATTACKS)), type + " at level " + level);
      }
      for (String type : CONTROLS) {
        assertFalse(blocked(blockers, tool, entry, source(type)), type + " must not be blocked at level " + level);
      }
    }
  }

  @Test
  void leatherLacesGiveRuggedAndSkySlimeskinGivesAirborn() throws Exception {
    JsonObject leather = ModifierJsonLoader.readGenerated("tinkering/materials/traits/leather.json");
    assertTrue(traitNames(leather.getAsJsonObject("perStat").getAsJsonArray("tconstruct:laces")).contains("tconstruct:rugged"), leather.toString());
    JsonObject skin = ModifierJsonLoader.readGenerated("tinkering/materials/traits/skyslimeskin.json");
    assertTrue(traitNames(skin.getAsJsonArray("default")).contains("tconstruct:airborn"), skin.toString());
  }

  @Test
  void airbornIsFlatProtectionAgainstAirborneAttackers() throws Exception {
    ComposableModifier airborn = assertInstanceOf(ComposableModifier.class, ModifierJsonLoader.load("airborn"));
    List<ProtectionModule> protection = modules(airborn, ProtectionModule.class);
    assertEquals(1, protection.size());
    // the attacker predicate must be the same singleton ModifierEvents and dragonshot use for "in the air"
    assertSame(TinkerPredicate.AIRBORNE, protection.get(0).attacker());
    JsonObject module = ModifierJsonLoader.readModifier("airborn").getAsJsonArray("modules").get(0).getAsJsonObject();
    assertEquals(2.5f, module.get("flat").getAsFloat(), 1e-6);
    assertFalse(module.has("each_level"));
    assertEquals("\"tconstruct:single_level\"", ModifierJsonLoader.readModifier("airborn").get("level_display").toString());
  }

  @Test
  void noMilkEffectsAreExemptFromDurationChangesLikeOfficial() {
    // official v3.12.1 skips effects with empty curative items; these are the tinkers ones
    assertTrue(ModifierEvents.hasNoCurativeItems(TinkerModifiers.teleportCooldownEffect.get()));
    assertTrue(ModifierEvents.hasNoCurativeItems(TinkerModifiers.fireballCooldownEffect.get()));
    assertTrue(ModifierEvents.hasNoCurativeItems(TinkerModifiers.calcifiedEffect.get()));
    assertFalse(ModifierEvents.hasNoCurativeItems(net.minecraft.world.effect.MobEffects.POISON.value()));
    assertFalse(ModifierEvents.hasNoCurativeItems(net.minecraft.world.effect.MobEffects.SPEED.value()));
  }

  @Test
  void balmShortensNegativeEffectsLikeItsDescription() throws Exception {
    JsonObject module = ModifierJsonLoader.readModifier("balm_of_sssss").getAsJsonArray("modules").get(0).getAsJsonObject();
    float atLevelOne = module.get("flat").getAsFloat() + module.get("each_level").getAsFloat();
    assertTrue(atLevelOne < 0, "balm must reduce bad effect duration, got " + atLevelOne);
    assertEquals(-0.2f, atLevelOne, 1e-6);
    assertNotNull(ModifierJsonLoader.load("balm_of_sssss"));
  }

  /* helpers */

  private static Set<String> required(String path) throws Exception {
    Set<String> values = new java.util.HashSet<>();
    for (JsonElement element : ModifierJsonLoader.readGenerated(path).getAsJsonArray("values")) {
      if (element.isJsonPrimitive()) {
        values.add(element.getAsString());
      }
    }
    return values;
  }

  private static Set<String> optional(String path) throws Exception {
    Set<String> values = new java.util.HashSet<>();
    for (JsonElement element : ModifierJsonLoader.readGenerated(path).getAsJsonArray("values")) {
      if (element.isJsonObject()) {
        assertFalse(element.getAsJsonObject().get("required").getAsBoolean());
        values.add(element.getAsJsonObject().get("id").getAsString());
      }
    }
    return values;
  }

  private static List<String> traitNames(JsonArray traits) {
    List<String> names = new ArrayList<>();
    for (JsonElement element : traits) {
      names.add(element.getAsJsonObject().get("name").getAsString());
    }
    return names;
  }

  private static <T> List<T> modules(ComposableModifier modifier, Class<T> type) throws Exception {
    Field field = ComposableModifier.class.getDeclaredField("modules");
    field.setAccessible(true);
    List<T> found = new ArrayList<>();
    for (Object entry : (List<?>) field.get(modifier)) {
      Object module = ((WithHooks<?>) entry).module();
      if (type.isInstance(module)) {
        found.add(type.cast(module));
      }
    }
    return found;
  }

  private static boolean blocked(List<BlockDamageSourceModule> blockers, IToolStackView tool, ModifierEntry entry, DamageSource source) {
    for (BlockDamageSourceModule blocker : blockers) {
      if (blocker.isDamageBlocked(tool, entry, null, EquipmentSlot.FEET, source, 4)) {
        return true;
      }
    }
    return false;
  }

  /** A tool view that fails the test if the damage block path reads the tool; rugged's tool condition is "any". */
  private static IToolStackView unusedTool() {
    return (IToolStackView) Proxy.newProxyInstance(IToolStackView.class.getClassLoader(), new Class<?>[]{IToolStackView.class},
      (proxy, method, arguments) -> {
        if (method.getName().equals("toString")) {
          return "unused tool";
        }
        throw new AssertionError("rugged damage block unexpectedly read the tool: " + method);
      });
  }

  /** Builds a damage source whose type holder is bound to the given tags, like a loaded damage type registry entry. */
  @SafeVarargs
  private static DamageSource source(String id, TagKey<DamageType>... tags) throws Exception {
    HolderOwner<DamageType> owner = new HolderOwner<>() {};
    Holder.Reference<DamageType> holder = Holder.Reference.createStandAlone(owner, ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.parse(id)));
    Method bindValue = Holder.Reference.class.getDeclaredMethod("bindValue", Object.class);
    bindValue.setAccessible(true);
    bindValue.invoke(holder, new DamageType(id.replace(':', '.'), 0.1f));
    Method bindTags = Holder.Reference.class.getDeclaredMethod("bindTags", Collection.class);
    bindTags.setAccessible(true);
    bindTags.invoke(holder, List.of(tags));
    return new DamageSource(holder);
  }
}
