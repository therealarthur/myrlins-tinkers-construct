package aebm.continuumtests;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.item.Instruments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.InstrumentComponent;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.recipe.ingredient.InstrumentIngredient;

import static org.junit.jupiter.api.Assertions.*;

final class InstrumentIngredientTest {
  @BeforeAll static void components() { ComponentTestSetup.initialize(); }

  @Test
  void allEightShippedVariantRecipesMatchOnlyTheirInstrumentAndPreserveDisplayComponents() throws Exception {
    var lookup = VanillaRegistries.createLookup();
    var instruments = List.of(Instruments.PONDER_GOAT_HORN, Instruments.SING_GOAT_HORN,
      Instruments.SEEK_GOAT_HORN, Instruments.FEEL_GOAT_HORN, Instruments.ADMIRE_GOAT_HORN,
      Instruments.CALL_GOAT_HORN, Instruments.YEARN_GOAT_HORN, Instruments.DREAM_GOAT_HORN);
    var ops = lookup.createSerializationContext(JsonOps.INSTANCE);
    for (var instrument : instruments) {
      String resource = "/data/tconstruct/recipe/tools/materials/horn/" + instrument.identifier().getPath() + ".json";
      try (var stream = getClass().getResourceAsStream(resource)) {
        assertNotNull(stream, resource);
        var recipe = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        Ingredient ingredient = Ingredient.CODEC.parse(ops, recipe.get("ingredient")).getOrThrow();
        var roundtrip = Ingredient.CODEC.parse(ops, Ingredient.CODEC.encodeStart(ops, ingredient).getOrThrow()).getOrThrow();
        assertEquals("tconstruct:horn#" + instrument.identifier().toString().replace(':', '.'), recipe.get("material").getAsString());
        assertEquals(4, recipe.get("value").getAsInt());
        assertEquals(1, recipe.get("needed").getAsInt());
        for (var candidate : instruments) {
          ItemStack horn = new ItemStack(Items.GOAT_HORN);
          horn.set(DataComponents.INSTRUMENT, new InstrumentComponent(lookup.lookupOrThrow(Registries.INSTRUMENT).getOrThrow(candidate)));
          assertEquals(instrument.equals(candidate), ingredient.test(horn));
          assertEquals(instrument.equals(candidate), roundtrip.test(horn));
        }
        var display = InstrumentIngredient.of(Items.GOAT_HORN, instrument).getDisplayStacks(lookup);
        assertEquals(1, display.size());
        assertTrue(ingredient.test(display.getFirst()));
        assertFalse(ingredient.test(new ItemStack(Items.STICK)));
      }
    }
    assertNull(getClass().getResource("/data/tconstruct/recipe/tools/materials/horn.json"), "generic recipe must not compete with the variants");
  }

  @Test
  void fallbackHonorsActualHolderTagsAndAcceptsOnlyUnmappedHorns() throws Exception {
    var lookup = VanillaRegistries.createLookup();
    // Ponder equals the item default by registry key, so setting it discards the patch and
    // retains ComponentTestSetup's separate holder. Use nondefault holders to isolate tags.
    var mapped = lookup.lookupOrThrow(Registries.INSTRUMENT).getOrThrow(Instruments.SING_GOAT_HORN);
    var other = lookup.lookupOrThrow(Registries.INSTRUMENT).getOrThrow(Instruments.SEEK_GOAT_HORN);
    Method bindTags = Holder.Reference.class.getDeclaredMethod("bindTags", Collection.class);
    bindTags.setAccessible(true);
    var tagField = Holder.Reference.class.getDeclaredField("tags");
    tagField.setAccessible(true);
    // Datagen holders may have no bound tags yet; preserve that state as well.
    var previousMapped = tagField.get(mapped);
    var previousOther = tagField.get(other);
    try {
      bindTags.invoke(mapped, List.of(TinkerTags.Instruments.VARIANT_HORNS));
      bindTags.invoke(other, List.of());
      var ingredient = InstrumentIngredient.of(Items.GOAT_HORN, TinkerTags.Instruments.VARIANT_HORNS);
      ItemStack horn = new ItemStack(Items.GOAT_HORN);
      horn.set(DataComponents.INSTRUMENT, new InstrumentComponent(mapped));
      assertSame(mapped, horn.get(DataComponents.INSTRUMENT).instrument());
      assertFalse(ingredient.test(horn));
      bindTags.invoke(mapped, List.of());
      assertTrue(ingredient.test(horn), "fallback follows holder tags, not the instrument key");
      bindTags.invoke(mapped, List.of(TinkerTags.Instruments.VARIANT_HORNS));
      assertFalse(ingredient.test(horn));
      horn.set(DataComponents.INSTRUMENT, new InstrumentComponent(other));
      assertSame(other, horn.get(DataComponents.INSTRUMENT).instrument());
      assertTrue(ingredient.test(horn));
      horn.remove(DataComponents.INSTRUMENT);
      assertTrue(ingredient.test(horn));
      assertFalse(InstrumentIngredient.of(Items.GOAT_HORN, Instruments.PONDER_GOAT_HORN).test(horn));
      assertFalse(ingredient.test(ItemStack.EMPTY));
    } finally {
      tagField.set(mapped, previousMapped);
      tagField.set(other, previousOther);
    }
  }
}
