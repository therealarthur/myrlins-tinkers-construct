package aebm.continuumtests;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.tools.modifiers.loot.BonusFormula;

import static org.junit.jupiter.api.Assertions.*;

/** Compare the production modifier formulas against actual target vanilla implementations. */
final class BonusFormulaParityTest {
  @Test
  void oreDropsMatchesVanillaIncludingZeroAndNegativeLevel() throws Exception {
    Object vanilla = vanilla("OreDrops", new Class<?>[0]);
    compare(vanilla, new BonusFormula.OreDrops(), new int[]{-3, 0, 1, 2, 3, 5});
  }

  @Test
  void otherFormulaAdaptersKeepVanillaResultsAndRandomConsumption() throws Exception {
    for (int multiplier : new int[]{0, 1, 3}) {
      compare(vanilla("UniformBonusCount", new Class<?>[]{int.class}, multiplier),
        new BonusFormula.UniformBonusCount(multiplier), new int[]{0, 1, 3, 5});
    }
    for (float probability : new float[]{0, 0.25f, 0.65f, 1}) {
      compare(vanilla("BinomialWithBonusCount", new Class<?>[]{int.class, float.class}, 2, probability),
        new BonusFormula.BinomialWithBonusCount(2, probability), new int[]{0, 1, 3, 5});
    }
  }

  @Test
  void formulaCodecsRoundtripAndRejectUnknownTypes() {
    for (BonusFormula formula : new BonusFormula[]{new BonusFormula.OreDrops(),
      new BonusFormula.UniformBonusCount(3), new BonusFormula.BinomialWithBonusCount(2, 0.65f)}) {
      var encoded = BonusFormula.CODEC.encodeStart(JsonOps.INSTANCE, formula).getOrThrow();
      var decoded = BonusFormula.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
      assertEquals(formula.getType(), decoded.getType());
      for (int seed = 0; seed < 32; seed++) {
        assertEquals(formula.calculateNewCount(RandomSource.create(seed), 4, 3),
          decoded.calculateNewCount(RandomSource.create(seed), 4, 3));
      }
    }
    var unknown = BonusFormula.CODEC.parse(JsonOps.INSTANCE,
      JsonParser.parseString("{\"type\":\"minecraft:typo_ore_drops\"}"));
    assertTrue(unknown.error().isPresent(), "unknown formulas must not silently change loot to ore drops");
    assertTrue(unknown.result().isEmpty());
  }

  private static Object vanilla(String name, Class<?>[] parameterTypes, Object... arguments) throws Exception {
    Class<?> type = Class.forName("net.minecraft.world.level.storage.loot.functions.ApplyBonusCount$" + name);
    Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
    constructor.setAccessible(true);
    return constructor.newInstance(arguments);
  }

  private static void compare(Object vanilla, BonusFormula modifier, int[] levels) throws Exception {
    Method calculate = vanilla.getClass().getDeclaredMethod("calculateNewCount", RandomSource.class, int.class, int.class);
    calculate.setAccessible(true);
    for (int base : new int[]{0, 1, 2, 8}) {
      for (int level : levels) {
        for (int seed = 0; seed < 64; seed++) {
          RandomSource vanillaRandom = RandomSource.create(seed);
          RandomSource modifierRandom = RandomSource.create(seed);
          int expected = (int) calculate.invoke(vanilla, vanillaRandom, base, level);
          assertEquals(expected, modifier.calculateNewCount(modifierRandom, base, level),
            () -> "formula=" + modifier.getType() + " base=" + base + " level=" + level);
          assertEquals(vanillaRandom.nextLong(), modifierRandom.nextLong(), "random sequence must remain identical");
        }
      }
    }
  }
}
