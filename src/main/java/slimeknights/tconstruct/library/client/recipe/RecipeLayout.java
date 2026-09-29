package slimeknights.tconstruct.library.client.recipe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Keys and typed accessors for {@link RecipeDisplayData#layout()} facts.
 * <p>
 * Official JEI categories read these numbers straight from recipe objects while drawing. The
 * viewer-neutral display keeps only facts, so each official category element that depends on a
 * recipe number gets a key here. Values are plain NBT so displays stay serializable for bookmarks.
 * No REI or JEI classes are referenced.
 */
public final class RecipeLayout {
  private RecipeLayout() {}

  /* Casting and molding */
  /** Casting cooling time in ticks. */
  public static final String COOLING = "cooling";
  /** Cast or pattern role: {@link #ROLE_NONE}, {@link #ROLE_CONSUMED} or {@link #ROLE_KEPT}. */
  public static final String CAST = "cast";
  /** True for casting basin and basin molding displays. */
  public static final String BASIN = "basin";
  public static final int ROLE_NONE = 0;
  public static final int ROLE_CONSUMED = 1;
  public static final int ROLE_KEPT = 2;

  /* Melting, foundry, alloy and fuel */
  /** Recipe temperature in degrees. */
  public static final String TEMPERATURE = "temperature";
  /** Melting time in recipe units; official shows {@code time / 4} seconds. */
  public static final String TIME = "time";
  /** Ore rate type: {@link #ORE_NONE}, {@link #ORE_METAL} or {@link #ORE_GEM}. */
  public static final String ORE = "ore";
  public static final int ORE_NONE = 0;
  public static final int ORE_METAL = 1;
  public static final int ORE_GEM = 2;
  /** Output amount in the melter after the configured ore boost. */
  public static final String MELTER_AMOUNT = "melter_amount";
  /** Output amount in the smeltery after the configured ore boost. */
  public static final String SMELTERY_AMOUNT = "smeltery_amount";
  /** Alloy: bit {@code i} is set when the i-th recipe ingredient is a catalyst. Inputs and catalysts keep recipe order. */
  public static final String CATALYST_MASK = "catalyst_mask";
  /** Fuel: duration in recipe ticks for one consumed amount. */
  public static final String DURATION = "duration";
  /** Fuel: speed rate, official shows {@code rate / 10}. */
  public static final String RATE = "rate";
  /** Fuel: true for the solid fuel page. */
  public static final String SOLID = "solid";

  /* Entity melting */
  /** Damage dealt per output, in half hearts. */
  public static final String DAMAGE = "damage";

  /* Part builder */
  /** Material cost of the pattern in material units. */
  public static final String COST = "cost";
  /** True when the displayed pattern item is reusable (a catalyst, not consumed). */
  public static final String REUSABLE = "reusable";
  /** True when the first input is the material item (false for patterns with no material cost). */
  public static final String MATERIAL_ITEM = "material_item";
  /** True when the last input is the material name entry, drawn as the title row. */
  public static final String MATERIAL_NAME = "material_name";

  /* Modifiers */
  /** Minimum and maximum modifier level for the level text; absent when the recipe shows a variant instead. */
  public static final String LEVEL_MIN = "level_min";
  public static final String LEVEL_MAX = "level_max";
  /** True when the first note is the recipe's variant text rather than a level range. */
  public static final String VARIANT = "variant";
  /** Index of the requirements note, or absent when the modifier has no requirements. */
  public static final String REQUIREMENTS = "requirements";

  /* Materials */
  /** Material value for item recipes, in units. */
  public static final String VALUE = "value";
  /** Items needed for the value. */
  public static final String NEEDED = "needed";
  /** True when the material can be used in the part builder. */
  public static final String CRAFTABLE = "craftable";
  /** True when the display has a leftover output after its main material output. */
  public static final String LEFTOVER = "leftover";
  /** True when a composite base material is the last input. */
  public static final String COMPOSITE = "composite";

  /* Tinker station and worktable */
  /** Station input index (0 to 4) for each item input, in input order. Tool inputs are excluded. */
  public static final String STATION_SLOTS = "station_slots";
  /** True when the tool is consumed or replaced (tinkering or worktable input) rather than a catalyst. */
  public static final String TOOL_INPUT = "tool_input";
  /** Modifier recipe flags. */
  public static final String INCREMENTAL = "incremental";
  public static final String FREE = "free";
  /** Worktable: true when the modifier list is an output rather than a selection. */
  public static final String MODIFIER_OUTPUT = "modifier_output";
  /** Worktable: number of item input slots shown (official shows 2). */
  public static final String ITEM_SLOTS = "item_slots";
  /** Tool building: flattened x, y pairs from the station slot layout, already offset for the display. */
  public static final String LAYOUT_SLOTS = "layout_slots";
  /** Tool building: true when the tool needs a Tinker's Anvil. */
  public static final String ANVIL = "anvil";
  /** Tool building: number of tool part inputs before extra requirements. */
  public static final String PART_COUNT = "part_count";

  /** Reads an int, or the fallback when absent. */
  public static int getInt(CompoundTag tag, String key, int fallback) {
    return tag.contains(key) ? tag.getIntOr(key, fallback) : fallback;
  }

  /** Reads a boolean, false when absent. Reads any numeric tag, since codec round trips may widen a byte. */
  public static boolean getBoolean(CompoundTag tag, String key) {
    return tag.getIntOr(key, 0) != 0;
  }

  /** Reads an int array, empty when absent or of another type. */
  public static int[] getIntArray(CompoundTag tag, String key) {
    Tag value = tag.get(key);
    if (value instanceof net.minecraft.nbt.IntArrayTag array) {
      return array.getAsIntArray();
    }
    // a JSON round trip can turn an int array into a list of numbers
    if (value instanceof net.minecraft.nbt.ListTag list) {
      int[] result = new int[list.size()];
      for (int i = 0; i < result.length; i++) {
        result[i] = list.get(i).asInt().orElse(0);
      }
      return result;
    }
    return new int[0];
  }
}
