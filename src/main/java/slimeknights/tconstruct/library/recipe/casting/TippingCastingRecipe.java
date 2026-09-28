package slimeknights.tconstruct.library.recipe.casting;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.common.IngredientLoadable;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.helper.LoadableRecipeSerializer;
import slimeknights.mantle.recipe.helper.TypeAwareRecipeSerializer;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.modifiers.adding.IDisplayModifierRecipe;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.item.IModifiableDisplay;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import java.util.List;

/** Casting recipe applying a potion to a tool */
public class TippingCastingRecipe extends PotionCastingRecipe {
  protected static final LoadableField<Ingredient, PotionCastingRecipe> TOOL_FIELD = IngredientLoadable.DISALLOW_EMPTY.requiredField("tools", r -> r.bottle);
  public static final RecordLoadable<TippingCastingRecipe> LOADER = RecordLoadable.create(
    LoadableRecipeSerializer.TYPED_SERIALIZER.requiredField(), ContextKey.ID.requiredField(), LoadableRecipeSerializer.RECIPE_GROUP,
    TOOL_FIELD, FLUID_FIELD, COOLING_TIME_FIELD,
    ModifierId.PARSER.requiredField("modifier", r -> r.modifier),
    TippingCastingRecipe::new);

  private final ModifierId modifier;
  public TippingCastingRecipe(TypeAwareRecipeSerializer<?> serializer, Identifier id, String group, Ingredient tool, FluidIngredient fluid, int coolingTime, ModifierId modifier) {
    super(serializer, id, group, tool, fluid, Items.AIR, coolingTime);
    this.modifier = modifier;
  }

  @Override
  public boolean matches(ICastingContainer inv, Level level) {
    // must have the modifier to cast
    ItemStack stack = inv.getStack();
    if (super.matches(inv, level) && ToolStack.from(stack).getModifierLevel(modifier) > 0) {
      // must also have a specific potion, and it cannot match what is already on the stack
      String potion = getPotionId(getPotionContents(inv));
      return !potion.isEmpty() && !ModifierUtil.getPersistentString(stack, modifier.getId()).equals(potion);
    }
    return false;
  }

  @Override
  public ItemStack assemble(ICastingContainer inv) {
    ItemStack result = inv.getStack().copy();
    String potion = getPotionId(getPotionContents(inv));
    if (!potion.isEmpty()) {
      ToolStack tool = ToolStack.from(result);
      tool.getPersistentData().putString(modifier.getId(), potion);
      result = tool.copyStack(result);
    }
    return result;
  }


  /* JEI */

  @Override
  public List<DisplayCastingRecipe> getRecipes(RegistryAccess access) {
    refreshDisplayCache();
    if (displayRecipes == null) {
      // create a list of tools with the modifier
      List<ItemStack> tools = MaterialRecipeCache.getDisplayItems(bottle).stream()
        .map(stack -> IDisplayModifierRecipe.withModifiers(IModifiableDisplay.getDisplayStack(stack), List.of(new ModifierEntry(modifier, 1))))
        .toList();
      displayRecipes = BuiltInRegistries.POTION.stream()
        .filter(potion -> potion != Potions.WATER.value())
        .map(potion -> {
          // add the potion to the tool list
          String id = Loadables.POTION.getString(potion);
          List<ItemStack> results = tools.stream().map(stack -> {
            ToolStack tool = ToolStack.copyFrom(stack);
            tool.getPersistentData().putString(modifier.getId(), id);
            return tool.copyStack(stack);
          }).toList();
          // add the potion to the fluid
          PotionContents contents = potionContents(potion);
          return new DisplayCastingRecipe(getId(), getType(), tools, fluid.getFluids().stream()
            .map(fluid -> withPotion(fluid, contents))
            .toList(),
            results, coolingTime, true);
        }).toList();
    }
    return displayRecipes;
  }
}
