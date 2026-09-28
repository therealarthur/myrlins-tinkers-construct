package slimeknights.tconstruct.plugin.rei;

import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** A complete, bookmarkable display. No server-side REI recipe or transfer implementation is required. */
public record SmelteryDisplay(Identifier category, Identifier source, Identifier displayId,
                              List<EntryIngredient> inputs, List<EntryIngredient> outputs,
                              List<EntryIngredient> catalysts, List<Component> notes,
                              List<EntryIngredient> lookupInputs) implements Display {
  public static final MapCodec<SmelteryDisplay> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
    Identifier.CODEC.fieldOf("category").forGetter(SmelteryDisplay::category),
    Identifier.CODEC.fieldOf("source").forGetter(SmelteryDisplay::source),
    Identifier.CODEC.fieldOf("display_id").forGetter(SmelteryDisplay::displayId),
    EntryIngredient.codec().listOf().fieldOf("inputs").forGetter(SmelteryDisplay::inputs),
    EntryIngredient.codec().listOf().fieldOf("outputs").forGetter(SmelteryDisplay::outputs),
    EntryIngredient.codec().listOf().fieldOf("catalysts").forGetter(SmelteryDisplay::catalysts),
    ComponentSerialization.CODEC.listOf().fieldOf("notes").forGetter(SmelteryDisplay::notes),
    EntryIngredient.codec().listOf().optionalFieldOf("lookup_inputs", List.of()).forGetter(SmelteryDisplay::lookupInputs)
  ).apply(instance, SmelteryDisplay::new));

  public static final DisplaySerializer<SmelteryDisplay> SERIALIZER = DisplaySerializer.of(
    CODEC, ByteBufCodecs.fromCodecWithRegistries(CODEC.codec()));

  public SmelteryDisplay {
    inputs = List.copyOf(inputs);
    outputs = List.copyOf(outputs);
    catalysts = List.copyOf(catalysts);
    notes = List.copyOf(notes);
    lookupInputs = List.copyOf(lookupInputs);
  }

  /** Uses the canonical holder ID plus content identity, so material expansion order cannot change bookmarks. */
  public static SmelteryDisplay create(RegistryAccess access, Identifier category, Identifier source,
                                       List<EntryIngredient> inputs, List<EntryIngredient> outputs,
                                       List<EntryIngredient> catalysts, List<Component> notes) {
    return create(access, category, source, inputs, outputs, catalysts, notes, List.of());
  }

  public static SmelteryDisplay create(RegistryAccess access, Identifier category, Identifier source,
                                       List<EntryIngredient> inputs, List<EntryIngredient> outputs,
                                       List<EntryIngredient> catalysts, List<Component> notes, List<EntryIngredient> lookupInputs) {
    SmelteryDisplay provisional = new SmelteryDisplay(category, source, source, inputs, outputs, catalysts, notes, lookupInputs);
    JsonElement encoded = CODEC.codec().encodeStart(access.createSerializationContext(JsonOps.INSTANCE), provisional).getOrThrow();
    String hash = Hashing.sha256().hashString(canonicalize(encoded).toString(), StandardCharsets.UTF_8).toString();
    Identifier id = Identifier.fromNamespaceAndPath(source.getNamespace(), source.getPath() + "/rei/" + category.getPath() + "/" + hash);
    return new SmelteryDisplay(category, source, id, inputs, outputs, catalysts, notes, lookupInputs);
  }

  private static JsonElement canonicalize(JsonElement element) {
    if (element.isJsonObject()) {
      JsonObject sorted = new JsonObject();
      element.getAsJsonObject().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
        .forEach(entry -> sorted.add(entry.getKey(), canonicalize(entry.getValue())));
      return sorted;
    }
    if (element.isJsonArray()) {
      JsonArray array = new JsonArray();
      element.getAsJsonArray().forEach(value -> array.add(canonicalize(value)));
      return array;
    }
    return element;
  }

  @Override public List<EntryIngredient> getInputEntries() { return inputs; }
  @Override public List<EntryIngredient> getOutputEntries() { return outputs; }
  @Override public List<EntryIngredient> getRequiredEntries() { return Stream.of(inputs, catalysts, lookupInputs).flatMap(List::stream).toList(); }
  @Override public CategoryIdentifier<SmelteryDisplay> getCategoryIdentifier() { return CategoryIdentifier.of(category); }
  @Override public Optional<Identifier> getDisplayLocation() { return Optional.of(source); }
  @Override public Collection<Identifier> provideInternalDisplayIds() { return List.of(displayId); }
  @Override public DisplaySerializer<SmelteryDisplay> getSerializer() { return SERIALIZER; }
}
