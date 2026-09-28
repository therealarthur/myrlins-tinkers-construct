package slimeknights.tconstruct.library.recipe.worktable;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.loadable.common.IngredientLoadable;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.recipe.ingredient.SizedIngredient;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.recipe.ITinkerableContainer;
import slimeknights.tconstruct.library.recipe.modifiers.ModifierRecipeLookup;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.modifiers.adding.ModifierRecipe;
import slimeknights.tconstruct.library.tools.item.IModifiableDisplay;
import slimeknights.tconstruct.library.tools.nbt.LazyToolStack;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Default implementation of modifier worktable recipes, taking a list of inputs
 */
@RequiredArgsConstructor
public abstract class AbstractWorktableRecipe implements IModifierWorktableRecipe {
  public static final Ingredient DEFAULT_TOOLS = slimeknights.tconstruct.library.recipe.ingredient.LegacyIngredientType.ofTag(TinkerTags.Items.MODIFIABLE);
  protected static final LoadableField<Ingredient,AbstractWorktableRecipe> TOOL_FIELD = IngredientLoadable.DISALLOW_EMPTY.defaultField("tools", DEFAULT_TOOLS, true, r -> r.toolRequirement);
  protected static final LoadableField<List<SizedIngredient>,AbstractWorktableRecipe> INPUTS_FIELD = SizedIngredient.LOADABLE.list(1).requiredField("inputs", r -> r.inputs);

  @Getter
  private final Identifier id;
  protected final Ingredient toolRequirement;
  protected final List<SizedIngredient> inputs;

  /* JEI */
  @Nullable
  protected List<ItemStack> tools;
  private long displayRevision = -1;

  public AbstractWorktableRecipe(Identifier id, List<SizedIngredient> inputs) {
    this(id, slimeknights.tconstruct.library.recipe.ingredient.LegacyIngredientType.ofTag(TinkerTags.Items.MODIFIABLE), inputs);
  }

  @Override
  public boolean matches(ITinkerableContainer inv, Level world) {
    if (!toolRequirement.test(inv.getTinkerableStack())) {
      return false;
    }
    return ModifierRecipe.checkMatch(inv, inputs);
  }

  @Override
  public List<ModifierEntry> getModifierOptions(@Nullable ITinkerableContainer inv) {
    if (inv == null) {
      return ModifierRecipeLookup.getRecipeModifierList();
    }
    return inv.getTinkerable().getUpgrades().getModifiers();
  }

  @Override
  public void updateInputs(LazyToolStack result, ITinkerableContainer.Mutable inv, ModifierEntry selected, boolean isServer) {
    ModifierRecipe.updateInputs(inv, inputs);
  }


  /* JEI */

  /** Gets a list of tools to display */
  @Override
  public List<ItemStack> getInputTools() {
    if (displayRevision != MaterialRecipeCache.getDisplayRevision()) {
      displayRevision = MaterialRecipeCache.getDisplayRevision();
      tools = null;
    }
    if (tools == null) {
      try {
        tools = MaterialRecipeCache.getDisplayItems(toolRequirement).stream()
          .map(stack -> stack.getComponentsPatch().isEmpty() ? IModifiableDisplay.getDisplayStack(stack.getItem()) : stack).toList();
      } catch (UnsupportedOperationException exception) {
        // JEI may construct layouts before tag holders are resolved. Do not cache a partial list;
        // the normal lookup will run again once tags are available.
        return Collections.emptyList();
      }
    }
    return tools;
  }

  @Override
  public List<ItemStack> getDisplayItems(int slot) {
    if (slot < 0 || slot >= inputs.size()) {
      return Collections.emptyList();
    }
    SizedIngredient input = inputs.get(slot);
    return MaterialRecipeCache.getDisplayItems(input.getIngredient()).stream()
      .map(stack -> stack.copyWithCount(input.getAmountNeeded())).toList();
  }

  @Override
  public int getInputCount() {
    return inputs.size();
  }
}
