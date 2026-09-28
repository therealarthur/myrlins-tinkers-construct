package slimeknights.tconstruct.tables.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import slimeknights.mantle.util.RegistryHelper;
import slimeknights.tconstruct.library.recipe.tinkerstation.IDisplayToolTinkering;
import slimeknights.tconstruct.library.tools.item.IModifiableDisplay;
import slimeknights.tconstruct.library.tools.nbt.StatsNBT;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.loadable.common.IngredientLoadable;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.recipe.RecipeResult;
import slimeknights.tconstruct.library.recipe.modifiers.adding.IncrementalModifierRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.IMutableTinkerStationContainer;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationRecipe;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.nbt.LazyToolStack;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tables.TinkerTables;

@RequiredArgsConstructor
public class TinkerStationDamagingRecipe implements ITinkerStationRecipe, IDisplayToolTinkering {
  public static final RecordLoadable<TinkerStationDamagingRecipe> LOADER = RecordLoadable.create(
    ContextKey.ID.requiredField(),
    IngredientLoadable.DISALLOW_EMPTY.requiredField("ingredient", r -> r.ingredient),
    IntLoadable.FROM_ONE.requiredField("damage_amount", r -> r.damageAmount),
    TinkerStationDamagingRecipe::new);
  private static final RecipeResult<LazyToolStack> BROKEN = RecipeResult.failure(TConstruct.makeTranslationKey("recipe", "damaging.broken"));

  private static final Component TITLE = TConstruct.makeTranslation("recipe", "tool_damaging");
  private static final Component TOOLTIP = TConstruct.makeTranslation("recipe", "tool_damaging.tooltip");

  @Getter
  private final Identifier id;
  private final Ingredient ingredient;
  private final int damageAmount;

  @Override
  public boolean matches(ITinkerStationContainer inv, Level world) {
    if (!inv.getTinkerableStack().is(TinkerTags.Items.DURABILITY)) {
      return false;
    }
    // must find at least one input, but multiple is fine, as is empty slots
    return IncrementalModifierRecipe.containsOnlyIngredient(inv, ingredient);
  }

  @Override
  public RecipeResult<LazyToolStack> getValidatedResult(ITinkerStationContainer inv, RegistryAccess access) {
    ToolStack tool = inv.getTinkerable();
    if (tool.isBroken()) {
      return BROKEN;
    }
    // simply damage the tool directly
    tool = tool.copy();
    int maxDamage = IncrementalModifierRecipe.getAvailableAmount(inv, ingredient, damageAmount);
    ItemStack tinkerable = inv.getTinkerableStack();
    ToolDamageUtil.directDamage(tool, maxDamage, null, tinkerable);
    return LazyToolStack.successCopy(tool, 1, tinkerable);
  }

  @Override
  public int shrinkToolSlotBy() {
    return 1;
  }

  @Override
  public void updateInputs(LazyToolStack result, IMutableTinkerStationContainer inv, boolean isServer) {
    // how much did we actually consume?
    int damageTaken = result.getTool().getDamage() - inv.getTinkerable().getDamage();
    IncrementalModifierRecipe.updateInputs(inv, ingredient, damageTaken, damageAmount, ItemStack.EMPTY);
  }

  @Override
  public RecipeSerializer<? extends Recipe<ITinkerStationContainer>> getSerializer() {
    return TinkerTables.tinkerStationDamagingSerializer.get();
  }

  /* JEI */

  /** Tools for display in JEI */


  @Override
  public Identifier getRecipeId() {
    return getId();
  }

  @Override
  public Component getTitle() {
    return TITLE;
  }

  @Override
  public Component getTooltip() {
    return TOOLTIP;
  }

  @Override
  public boolean isToolCatalyst() {
    return true;
  }

  @Override
  public Component getVariant() {
    return TConstruct.makeTranslation("recipe", "modifier.amount", damageAmount);
  }

  @Override
  public int getMaxToolSize() {
    return 1;
  }

  @Override
  public int getInputCount() {
    return 1;
  }

  @Override
  public List<ItemStack> getDisplayItems(int slot) {
    return slot == 0 ? MaterialRecipeCache.getDisplayItems(ingredient) : List.of();
  }

  @Override
  public List<ItemStack> getToolWithoutModifier() {
      // set durability on each tool to 1000, covers most instances
      CompoundTag stats = StatsNBT.builder().set(ToolStats.DURABILITY, 1000).build().serializeToNBT();
      List<ItemStack> toolWithoutModifier = RegistryHelper.getTagValueStream(BuiltInRegistries.ITEM, TinkerTags.Items.DURABILITY)
        .map(item -> {
          if (item instanceof IModifiableDisplay modifiable) {
            ItemStack stack = modifiable.getRenderTool().copy();
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            tag.put("tic_stats", stats);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            return stack;
          }
          return ItemStack.EMPTY;
        })
        .filter(stack -> !stack.isEmpty())
        .toList();
    return toolWithoutModifier;
  }

  @Override
  public List<ItemStack> getToolWithModifier() {
      List<ItemStack> toolWithModifier = getToolWithoutModifier().stream()
        .map(stack -> {
          stack = stack.copy();
          CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
          tag.putInt("Damage", damageAmount);
          stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
          return stack;
        }).toList();
    return toolWithModifier;
  }

  @Override
  public boolean isTool(ItemStack check) {
    return check.is(TinkerTags.Items.DURABILITY);
  }

  @Override
  public RecipeResult<ItemStack> onFocused(ItemStack focus) {
    ToolStack tool = ToolStack.copyFrom(focus);
    ToolDamageUtil.directDamage(tool, damageAmount, null, focus);
    return RecipeResult.success(tool.copyStack(focus));
  }
}
