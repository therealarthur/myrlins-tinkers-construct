package slimeknights.tconstruct.library.client.recipe;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;
import slimeknights.tconstruct.library.tools.SlotType.SlotCount;

/** Viewer-neutral recipe facts. Each inner list contains alternatives for one slot. */
public record RecipeDisplayData(Identifier category, Identifier source,
                               List<List<Value>> inputs, List<List<Value>> outputs,
                               List<List<Value>> catalysts, List<Component> notes,
                               List<Value> lookupInputs) {
  public RecipeDisplayData {
    inputs = inputs.stream().map(List::copyOf).toList();
    outputs = outputs.stream().map(List::copyOf).toList();
    catalysts = catalysts.stream().map(List::copyOf).toList();
    notes = List.copyOf(notes);
    lookupInputs = List.copyOf(lookupInputs);
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
