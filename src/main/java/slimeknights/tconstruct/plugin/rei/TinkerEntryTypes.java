package slimeknights.tconstruct.plugin.rei;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Function;
import java.util.stream.Stream;
import me.shedaniel.rei.api.client.entry.renderer.EntryRenderer;
import me.shedaniel.rei.api.common.entry.EntrySerializer;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.comparison.ComparisonContext;
import me.shedaniel.rei.api.common.entry.type.EntryDefinition;
import me.shedaniel.rei.api.common.entry.type.EntryType;
import me.shedaniel.rei.api.common.entry.type.EntryTypeRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.*;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.SlotType.SlotCount;

/** Real non-item entries: amounts and component identities survive display/bookmark serialization. */
final class TinkerEntryTypes {
  private TinkerEntryTypes() {}
  static final EntryType<MaterialValue> MATERIAL = EntryType.deferred(TConstruct.getResource("material"));
  static final EntryType<ModifierValue> MODIFIER = EntryType.deferred(TConstruct.getResource("modifier"));
  static final EntryType<PatternValue> PATTERN = EntryType.deferred(TConstruct.getResource("pattern"));
  static final EntryType<SlotValue> SLOT = EntryType.deferred(TConstruct.getResource("slot"));
  static final EntryType<EntityValue> ENTITY = EntryType.deferred(TConstruct.getResource("entity"));

  private static final Codec<MaterialVariantId> MATERIAL_ID = Codec.STRING.comapFlatMap(value -> {
    var parsed = MaterialVariantId.tryParse(value);
    return parsed == null ? DataResult.error(() -> "Invalid material variant: " + value) : DataResult.success(parsed);
  }, MaterialVariantId::toString);
  private static final Codec<MaterialValue> MATERIAL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
    MATERIAL_ID.fieldOf("material").forGetter(MaterialValue::material),
    Codec.intRange(1, Integer.MAX_VALUE).fieldOf("amount").forGetter(MaterialValue::amount)
  ).apply(instance, MaterialValue::new));
  private static final Codec<ModifierValue> MODIFIER_CODEC = CompoundTag.CODEC.comapFlatMap(tag -> {
    ModifierEntry entry = ModifierEntry.readFromNBT(tag);
    return entry == null ? DataResult.error(() -> "Invalid modifier entry") : DataResult.success(new ModifierValue(entry));
  }, value -> value.modifier().serializeToNBT());
  private static final Codec<PatternValue> PATTERN_CODEC = Identifier.CODEC.xmap(id -> new PatternValue(new Pattern(id)), value -> value.pattern().getId());
  private static final Codec<SlotType> SLOT_TYPE_CODEC = Codec.STRING.comapFlatMap(name ->
    SlotType.isValidName(name) ? DataResult.success(SlotType.getOrCreate(name)) : DataResult.error(() -> "Invalid slot type: " + name), SlotType::getName);
  private static final Codec<SlotValue> SLOT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
    SLOT_TYPE_CODEC.fieldOf("type").forGetter(value -> value.slots().type()),
    Codec.intRange(1, Integer.MAX_VALUE).fieldOf("count").forGetter(value -> value.slots().count())
  ).apply(instance, (type, count) -> new SlotValue(new SlotCount(type, count))));
  private static final Codec<EntityValue> ENTITY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
    BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(EntityValue::entity),
    Codec.BOOL.optionalFieldOf("baby", false).forGetter(EntityValue::baby)
  ).apply(instance, EntityValue::new));

  static void register(EntryTypeRegistry registry) {
    registry.register(MATERIAL, new Definition<>(MaterialValue.class, MATERIAL, MATERIAL_CODEC,
      value -> value.material().getId(), value -> value.material().toString() + "/" + value.amount(),
      value -> value.material().getId().toString(), value -> new MaterialValue(value.material(), 1)));
    registry.register(MODIFIER, new Definition<>(ModifierValue.class, MODIFIER, MODIFIER_CODEC,
      value -> value.modifier().getId().getId(), value -> value.modifier().serializeToNBT().toString(),
      value -> value.modifier().getId().toString(), value -> new ModifierValue(new ModifierEntry(value.modifier().getId(), 1))));
    registry.register(PATTERN, new Definition<>(PatternValue.class, PATTERN, PATTERN_CODEC,
      value -> value.pattern().getId(), value -> value.pattern().toString(), value -> value.pattern().toString(), Function.identity()));
    registry.register(SLOT, new Definition<>(SlotValue.class, SLOT, SLOT_CODEC,
      value -> TConstruct.getResource("slot/" + value.slots().type().getName()), value -> value.slots().toString(),
      value -> value.slots().type().getName(), value -> new SlotValue(new SlotCount(value.slots().type(), 1))));
    registry.register(ENTITY, new Definition<>(EntityValue.class, ENTITY, ENTITY_CODEC,
      value -> BuiltInRegistries.ENTITY_TYPE.getKey(value.entity()), value -> BuiltInRegistries.ENTITY_TYPE.getKey(value.entity()) + "/" + value.baby(),
      value -> BuiltInRegistries.ENTITY_TYPE.getKey(value.entity()).toString(), value -> new EntityValue(value.entity(), false)));
  }

  static EntryStack<?> entry(Value value) {
    return switch (value) {
      case MaterialValue material -> EntryStack.of(MATERIAL, material);
      case ModifierValue modifier -> EntryStack.of(MODIFIER, modifier);
      case PatternValue pattern -> EntryStack.of(PATTERN, pattern);
      case SlotValue slot -> EntryStack.of(SLOT, slot);
      case EntityValue entity -> EntryStack.of(ENTITY, entity);
      default -> throw new IllegalArgumentException("Item/fluid values use their native entry types");
    };
  }

  static boolean isOwned(EntryStack<?> entry) {
    var type = entry.getType();
    return type.equals(MATERIAL) || type.equals(MODIFIER) || type.equals(PATTERN) || type.equals(SLOT) || type.equals(ENTITY);
  }

  static Component name(Value value) {
    return switch (value) {
      case MaterialValue material -> Component.translatableWithFallback("rei.tconstruct.material_amount", "%s × %s", material.amount(),
        Component.translatable("material." + material.material().getId().getNamespace() + "." + material.material().getId().getPath()));
      case ModifierValue modifier -> modifier.modifier().getDisplayName();
      case PatternValue pattern -> pattern.pattern().getDisplayName();
      case SlotValue slot -> slot.slots().type().format(slot.slots().count());
      case EntityValue entity -> entity.baby()
        ? Component.translatableWithFallback("rei.tconstruct.baby_entity", "Baby %s", entity.entity().getDescription()) : entity.entity().getDescription();
      default -> Component.empty();
    };
  }

  private record Definition<T extends Value>(Class<T> valueClass, EntryType<T> type, Codec<T> codec,
                                               Function<T,Identifier> identifier, Function<T,String> exact,
                                               Function<T,String> fuzzy, Function<T,T> normalizer) implements EntryDefinition<T> {
    @Override public Class<T> getValueType() { return valueClass; }
    @Override public EntryType<T> getType() { return type; }
    @Override public EntryRenderer<T> getRenderer() { return TinkerEntryRenderers.renderer(); }
    @Override public Identifier getIdentifier(EntryStack<T> stack, T value) { return identifier.apply(value); }
    @Override public boolean isEmpty(EntryStack<T> stack, T value) { return value == null; }
    @Override public T copy(EntryStack<T> stack, T value) { return value; }
    @Override public T normalize(EntryStack<T> stack, T value) { return normalizer.apply(value); }
    @Override public T wildcard(EntryStack<T> stack, T value) { return normalizer.apply(value); }
    @Override public long hash(EntryStack<T> stack, T value, ComparisonContext context) { return (context.isExact() ? exact : fuzzy).apply(value).hashCode(); }
    @Override public boolean equals(T first, T second, ComparisonContext context) {
      Function<T,String> identity = context.isExact() ? exact : fuzzy;
      return identity.apply(first).equals(identity.apply(second));
    }
    @Override public EntrySerializer<T> getSerializer() {
      return new EntrySerializer<>() {
        @Override public Codec<T> codec() { return Definition.this.codec; }
        @Override public StreamCodec<RegistryFriendlyByteBuf,T> streamCodec() { return ByteBufCodecs.fromCodecWithRegistries(Definition.this.codec); }
      };
    }
    @Override public Component asFormattedText(EntryStack<T> stack, T value) { return name(value); }
    @Override public Stream<? extends TagKey<?>> getTagsFor(EntryStack<T> stack, T value) { return Stream.empty(); }
  }
}
