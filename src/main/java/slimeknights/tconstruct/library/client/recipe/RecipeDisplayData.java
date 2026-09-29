package slimeknights.tconstruct.library.client.recipe;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;
import slimeknights.tconstruct.library.tools.SlotType.SlotCount;

/**
 * Viewer-neutral recipe facts. Each inner list contains alternatives for one slot.
 * <p>
 * {@code layout} carries the numbers and flags a category layout needs (cooling time, temperature,
 * station slot positions and similar), keyed by {@link RecipeLayout}. {@code renderOnly} holds entries
 * that official JEI draws but does not index for lookup, such as the modified tool on a modifier page.
 * Both default to empty, so displays created before layouts existed keep their shape.
 */
public record RecipeDisplayData(Identifier category, Identifier source,
                               List<List<Value>> inputs, List<List<Value>> outputs,
                               List<List<Value>> catalysts, List<Component> notes,
                               List<Value> lookupInputs, CompoundTag layout,
                               List<List<Value>> renderOnly) {
  public RecipeDisplayData {
    inputs = inputs.stream().map(List::copyOf).toList();
    outputs = outputs.stream().map(List::copyOf).toList();
    catalysts = catalysts.stream().map(List::copyOf).toList();
    notes = List.copyOf(notes);
    lookupInputs = List.copyOf(lookupInputs);
    layout = layout == null ? new CompoundTag() : layout.copy();
    renderOnly = renderOnly == null ? List.of() : renderOnly.stream().map(List::copyOf).toList();
  }

  /** Original shape without layout facts; kept for existing callers. */
  public RecipeDisplayData(Identifier category, Identifier source,
                           List<List<Value>> inputs, List<List<Value>> outputs,
                           List<List<Value>> catalysts, List<Component> notes,
                           List<Value> lookupInputs) {
    this(category, source, inputs, outputs, catalysts, notes, lookupInputs, new CompoundTag(), List.of());
  }

  /** Layout facts are copied so callers cannot change a cached display. */
  @Override
  public CompoundTag layout() {
    return layout.copy();
  }

  /** Returns a copy of this display with the given layout facts. */
  public RecipeDisplayData withLayout(CompoundTag layout) {
    return new RecipeDisplayData(category, source, inputs, outputs, catalysts, notes, lookupInputs, layout, renderOnly);
  }

  /** Returns a copy of this display with the given render-only entries. */
  public RecipeDisplayData withRenderOnly(List<List<Value>> renderOnly) {
    return new RecipeDisplayData(category, source, inputs, outputs, catalysts, notes, lookupInputs, layout, renderOnly);
  }

  public sealed interface Value permits ItemValue, FluidValue, MaterialValue, ModifierValue, PatternValue, SlotValue, EntityValue {}
  public record ItemValue(ItemStack stack) implements Value {
    public ItemValue { stack = stack.copy(); }
  }
  public record FluidValue(FluidStack stack) implements Value {
    public FluidValue { stack = stack.copy(); }
  }
  public record MaterialValue(MaterialVariantId material, int amount) implements Value {}
  public record ModifierValue(ModifierEntry modifier) implements Value {}
  public record PatternValue(Pattern pattern) implements Value {}
  public record SlotValue(SlotCount slots) implements Value {}
  public record EntityValue(EntityType<?> entity, boolean baby) implements Value {}
}
