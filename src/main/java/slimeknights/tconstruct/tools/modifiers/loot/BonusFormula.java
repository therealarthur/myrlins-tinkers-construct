package slimeknights.tconstruct.tools.modifiers.loot;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import slimeknights.mantle.util.JsonHelper;

import java.util.Map;

/** Vanilla bonus formulas used by modifier-driven loot functions on the current codec API. */
public interface BonusFormula {
    int calculateNewCount(RandomSource random, int base, int level);
    Identifier getType();
    void serializeParams(JsonObject json, JsonSerializationContext context);

    interface FormulaDeserializer {
        BonusFormula deserialize(JsonObject json, JsonDeserializationContext context);
    }

    Map<Identifier, FormulaDeserializer> FORMULAS = ImmutableMap.of(
        Identifier.fromNamespaceAndPath("minecraft", "binomial_with_bonus_count"), (json, context) -> {
            int extra = GsonHelper.getAsInt(json, "extra");
            float probability = GsonHelper.getAsFloat(json, "probability");
            return new BinomialWithBonusCount(extra, probability);
        },
        Identifier.fromNamespaceAndPath("minecraft", "ore_drops"), (json, context) -> new OreDrops(),
        Identifier.fromNamespaceAndPath("minecraft", "uniform_bonus_count"), (json, context) -> {
            int bonusMultiplier = GsonHelper.getAsInt(json, "bonusMultiplier");
            return new UniformBonusCount(bonusMultiplier);
        }
    );

    Map<Identifier, MapCodec<? extends BonusFormula>> FORMULA_CODECS = ImmutableMap.of(
        Identifier.withDefaultNamespace("binomial_with_bonus_count"), BinomialWithBonusCount.MAP_CODEC,
        Identifier.withDefaultNamespace("ore_drops"), OreDrops.MAP_CODEC,
        Identifier.withDefaultNamespace("uniform_bonus_count"), UniformBonusCount.MAP_CODEC
    );
    Codec<Identifier> FORMULA_TYPE_CODEC = Identifier.CODEC.validate(id -> FORMULA_CODECS.containsKey(id)
        ? DataResult.success(id) : DataResult.error(() -> "Unknown bonus formula: " + id));
    Codec<BonusFormula> CODEC = FORMULA_TYPE_CODEC.dispatch(BonusFormula::getType, FORMULA_CODECS::get);

    class BinomialWithBonusCount implements BonusFormula {
        public static final com.mojang.serialization.MapCodec<BinomialWithBonusCount> MAP_CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(inst -> inst.group(
            com.mojang.serialization.Codec.INT.fieldOf("extra").forGetter(c -> c.extra),
            com.mojang.serialization.Codec.FLOAT.fieldOf("probability").forGetter(c -> c.probability)
        ).apply(inst, BinomialWithBonusCount::new));

        private final int extra;
        private final float probability;
        public BinomialWithBonusCount(int extra, float probability) {
            this.extra = extra;
            this.probability = probability;
        }
        @Override
        public int calculateNewCount(RandomSource random, int base, int level) {
            int count = base;
            for (int i = 0; i < level + extra; i++) {
                if (random.nextFloat() < probability) {
                    count++;
                }
            }
            return count;
        }
        @Override
        public Identifier getType() { return Identifier.fromNamespaceAndPath("minecraft", "binomial_with_bonus_count"); }
        @Override
        public void serializeParams(JsonObject json, JsonSerializationContext context) {
            json.addProperty("extra", extra);
            json.addProperty("probability", probability);
        }
    }

    class OreDrops implements BonusFormula {
        public static final com.mojang.serialization.MapCodec<OreDrops> MAP_CODEC = com.mojang.serialization.MapCodec.unit(new OreDrops());

        @Override
        public int calculateNewCount(RandomSource random, int base, int level) {
            if (level > 0) {
                int bonus = Math.max(random.nextInt(level + 2) - 1, 0);
                return base * (bonus + 1);
            }
            return base;
        }
        @Override
        public Identifier getType() { return Identifier.fromNamespaceAndPath("minecraft", "ore_drops"); }
        @Override
        public void serializeParams(JsonObject json, JsonSerializationContext context) {}
    }

    class UniformBonusCount implements BonusFormula {
        public static final com.mojang.serialization.MapCodec<UniformBonusCount> MAP_CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(inst -> inst.group(
            com.mojang.serialization.Codec.INT.fieldOf("bonusMultiplier").forGetter(c -> c.bonusMultiplier)
        ).apply(inst, UniformBonusCount::new));

        private final int bonusMultiplier;
        public UniformBonusCount(int bonusMultiplier) { this.bonusMultiplier = bonusMultiplier; }
        @Override
        public int calculateNewCount(RandomSource random, int base, int level) {
            return base + random.nextInt(level * bonusMultiplier + 1);
        }
        @Override
        public Identifier getType() { return Identifier.fromNamespaceAndPath("minecraft", "uniform_bonus_count"); }
        @Override
        public void serializeParams(JsonObject json, JsonSerializationContext context) {
            json.addProperty("bonusMultiplier", bonusMultiplier);
        }
    }
}
