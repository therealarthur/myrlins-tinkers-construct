package slimeknights.tconstruct.library.recipe.ingredient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.InstrumentComponent;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.recipe.ClientRecipeCache;

/** Matches the instrument component of a horn, preserving the original material variants. */
public record InstrumentIngredient(Item item, @Nullable ResourceKey<Instrument> instrument,
                                   @Nullable TagKey<Instrument> ignore) implements ICustomIngredient {
  public static final Identifier ID = TConstruct.getResource("instrument");
  public static final IngredientType<InstrumentIngredient> TYPE = LegacyIngredientType.of(InstrumentIngredient::parse, InstrumentIngredient::toJson);

  public InstrumentIngredient {
    if ((instrument == null) == (ignore == null)) {
      throw new IllegalArgumentException("Instrument ingredient needs exactly one instrument or ignored tag");
    }
  }

  public static InstrumentIngredient of(ItemLike item, ResourceKey<Instrument> instrument) {
    return new InstrumentIngredient(item.asItem(), instrument, null);
  }

  public static InstrumentIngredient of(ItemLike item, TagKey<Instrument> ignore) {
    return new InstrumentIngredient(item.asItem(), null, ignore);
  }

  @Override
  public boolean test(@Nullable ItemStack stack) {
    if (stack == null || !stack.is(item)) return false;
    InstrumentComponent component = stack.get(DataComponents.INSTRUMENT);
    if (component == null) return instrument == null;
    return instrument != null ? component.instrument().is(instrument) : !component.instrument().is(ignore);
  }

  @Override public boolean isSimple() { return false; }
  @Override public IngredientType<?> getType() { return TYPE; }
  @Override public Stream<Holder<Item>> items() { return Stream.of(item.builtInRegistryHolder()); }

  /** Resolves display variants from the same received registries as the recipes. */
  public List<ItemStack> getDisplayStacks(HolderLookup.Provider registries) {
    return registries.lookup(Registries.INSTRUMENT).map(lookup -> {
      Stream<Holder.Reference<Instrument>> values = instrument != null
        ? lookup.get(instrument).stream() : lookup.listElements().filter(holder -> !holder.is(ignore));
      return values.map(holder -> {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.INSTRUMENT, new InstrumentComponent(holder));
        return stack;
      }).filter(this::test).toList();
    }).orElseGet(List::of);
  }

  @Override
  public SlotDisplay display() {
    return new SlotDisplay.Composite(getDisplayStacks(ClientRecipeCache.getSnapshot().registryAccess()).stream()
      .map(stack -> (SlotDisplay) new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(stack))).toList());
  }

  public JsonElement toJson() {
    JsonObject json = new JsonObject();
    json.addProperty("neoforge:ingredient_type", ID.toString());
    json.addProperty("item", Loadables.ITEM.getString(item));
    if (instrument != null) json.addProperty("instrument", instrument.identifier().toString());
    if (ignore != null) json.addProperty("ignore", ignore.location().toString());
    return json;
  }

  private static InstrumentIngredient parse(JsonObject json) {
    if (json.has("instrument") == json.has("ignore")) {
      throw new JsonSyntaxException("Instrument ingredient needs exactly one instrument or ignored tag");
    }
    Item item = Loadables.ITEM.getIfPresent(json, "item");
    return json.has("instrument")
      ? of(item, ResourceKey.create(Registries.INSTRUMENT, Identifier.parse(json.get("instrument").getAsString())))
      : of(item, TagKey.create(Registries.INSTRUMENT, Identifier.parse(json.get("ignore").getAsString())));
  }
}
