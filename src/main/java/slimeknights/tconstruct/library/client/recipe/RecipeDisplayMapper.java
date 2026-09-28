package slimeknights.tconstruct.library.client.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;
import slimeknights.mantle.recipe.helper.IngredientHelper;
import slimeknights.mantle.recipe.helper.RecipeHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.*;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingLookup;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialFluidRecipe;
import slimeknights.tconstruct.library.recipe.entitymelting.EntityMeltingRecipe;
import slimeknights.tconstruct.library.recipe.material.IMaterialValue;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipe;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.modifiers.adding.IDisplayModifierRecipe;
import slimeknights.tconstruct.library.recipe.modifiers.severing.AgeableSeveringRecipe;
import slimeknights.tconstruct.library.recipe.modifiers.severing.SeveringRecipe;
import slimeknights.tconstruct.library.recipe.molding.IMoldingContainer;
import slimeknights.tconstruct.library.recipe.molding.MoldingRecipe;
import slimeknights.tconstruct.library.recipe.partbuilder.IDisplayPartBuilderRecipe;
import slimeknights.tconstruct.library.recipe.partbuilder.IPartBuilderContainer;
import slimeknights.tconstruct.library.recipe.partbuilder.IPartBuilderRecipe;
import slimeknights.tconstruct.library.recipe.partbuilder.recycle.PartBuilderRecycle;
import slimeknights.tconstruct.library.recipe.tinkerstation.building.ToolBuildingRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.IDisplayToolTinkering;
import slimeknights.tconstruct.library.recipe.worktable.IModifierWorktableRecipe;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.smeltery.block.entity.module.EntityMeltingModule;
import slimeknights.tconstruct.tables.recipe.PartBuilderToolRecycle;

/** Maps loaded recipe objects to display facts, without loading either optional viewer or a client renderer. */
public final class RecipeDisplayMapper {
  private RecipeDisplayMapper() {}

  public static List<RecipeDisplayData> map(RecipeHolder<?> holder, RegistryAccess access, Level level) {
    Identifier source = holder.id().identifier();
    Object recipe = holder.value();
    if (recipe instanceof IPartBuilderRecipe part) return parts(source, holder, part, access, level);
    if (recipe instanceof MoldingRecipe molding) return molding(source, molding);
    if (recipe instanceof EntityMeltingRecipe melting) return entityMelting(source, melting);
    if (recipe instanceof SeveringRecipe severing) return severing(source, severing);
    if (recipe instanceof ToolBuildingRecipe building) return building(source, building);
    if (recipe instanceof IModifierWorktableRecipe worktable) return worktable(source, worktable);
    if (recipe instanceof MaterialRecipe material) return material(source, material);
    if (recipe instanceof MaterialFluidRecipe material) return materialFluid(source, material);
    List<RecipeDisplayData> result = new ArrayList<>();
    for (var modifier : RecipeHelper.getJEIRecipes(access, Stream.of(holder), IDisplayModifierRecipe.class)) {
      result.addAll(modifier(source, modifier, level));
    }
    for (var tinkering : RecipeHelper.getJEIRecipes(access, Stream.of(holder), IDisplayToolTinkering.class)) {
      result.addAll(tinkering(source, tinkering, ItemStack.EMPTY, false));
    }
    return result;
  }

  /** Keeps linked tool/material/refund alternatives together instead of cycling impossible combinations. */
  public static List<RecipeDisplayData> tinkering(Identifier source, IDisplayToolTinkering recipe, ItemStack focus, boolean focusOutput) {
    if (focus.isEmpty() ? !recipe.showUnfocused() : recipe.isFiltered() && !recipe.isVisibleFromItem(focus, focusOutput)) return List.of();
    List<ItemStack> before = recipe.getToolWithoutModifier(focus, focusOutput);
    List<ItemStack> after = recipe.getToolWithModifier(focus, focusOutput);
    if (!focus.isEmpty() && !focusOutput && recipe.isTool(focus)) {
      var focused = recipe.onFocused(focus);
      if (focused.hasError()) return List.of();
      if (focused.isSuccess()) {
        int count = Math.min(focus.getCount(), recipe.getMaxToolSize(focus));
        before = List.of(focus.copyWithCount(count));
        after = List.of(focused.getResult().copyWithCount(count));
      }
    }
    if (before.isEmpty() || after.isEmpty()) return List.of();
    int[] links = recipe.linkToOutput();
    int variants = after.size();
    List<RecipeDisplayData> result = new ArrayList<>();
    for (int variant = 0; variant < variants; variant++) {
      List<List<Value>> inputs = new ArrayList<>();
      List<List<Value>> outputs = new ArrayList<>();
      List<List<Value>> catalysts = new ArrayList<>();
      (recipe.isToolCatalyst() ? catalysts : inputs).add(items(before.size() == variants ? List.of(before.get(variant)) : before));
      (recipe.isToolCatalyst() ? catalysts : outputs).add(items(List.of(after.get(variant))));
      List<Component> notes = new ArrayList<>(List.of(recipe.getTitle(), recipe.getTooltip()));
      if (recipe.getVariant() != null) notes.add(recipe.getVariant());
      if (recipe.isToolCatalyst()) notes.add(text("tool_modified", "The tool is modified; before and after are shown below."));
      List<SlotAlternatives> independent = new ArrayList<>();
      for (int slot = 0; slot < recipe.getInputCount(); slot++) {
        List<ItemStack> alternatives = recipe.getDisplayItems(slot, focus, focusOutput);
        if (alternatives.isEmpty()) continue; // Station slot indices may intentionally have holes.
        List<ItemStack> refunds = recipe.getDisplayRemainders(slot, focus, focusOutput);
        List<ItemStack> containers = recipe.getDisplayContainers(slot, focus, focusOutput);
        final int index = slot;
        boolean linked = java.util.Arrays.stream(links).anyMatch(link -> link == index);
        if (linked && alternatives.size() != variants) throw new IllegalArgumentException("Mismatched linked alternatives for " + source);
        if (linked) {
          inputs.add(items(List.of(alternatives.get(variant))));
          addReturn(outputs, refunds, variant);
          addReturn(outputs, containers, variant);
        } else if (!refunds.isEmpty() || !containers.isEmpty()) {
          independent.add(new SlotAlternatives(alternatives, refunds, containers));
        } else inputs.add(items(alternatives));
      }
      expandReturns(result, source, inputs, outputs, catalysts, notes, independent, 0);
    }
    return result;
  }

  private record SlotAlternatives(List<ItemStack> inputs, List<ItemStack> refunds, List<ItemStack> containers) {}

  private static void addReturn(List<List<Value>> outputs, List<ItemStack> values, int index) {
    if (index < values.size() && !values.get(index).isEmpty()) outputs.add(items(List.of(values.get(index))));
  }

  private static void expandReturns(List<RecipeDisplayData> result, Identifier source, List<List<Value>> inputs, List<List<Value>> outputs,
                                    List<List<Value>> catalysts, List<Component> notes, List<SlotAlternatives> slots, int slot) {
    if (slot == slots.size()) {
      result.addAll(one("tool_modification", source, inputs, outputs, catalysts, notes, List.of()));
      return;
    }
    SlotAlternatives alternatives = slots.get(slot);
    for (int i = 0; i < alternatives.inputs.size(); i++) {
      var nextInputs = new ArrayList<>(inputs);
      var nextOutputs = new ArrayList<>(outputs);
      nextInputs.add(items(List.of(alternatives.inputs.get(i))));
      addReturn(nextOutputs, alternatives.refunds, i);
      addReturn(nextOutputs, alternatives.containers, i);
      expandReturns(result, source, nextInputs, nextOutputs, catalysts, notes, slots, slot + 1);
    }
  }

  private static List<RecipeDisplayData> molding(Identifier source, MoldingRecipe recipe) {
    var material = MaterialRecipeCache.getDisplayItems(recipe.getMaterial());
    var patterns = MaterialRecipeCache.getDisplayItems(recipe.getPattern());
    boolean hasPattern = !IngredientHelper.test(recipe.getPattern(), ItemStack.EMPTY);
    if (material.isEmpty() || hasPattern && patterns.isEmpty()) return List.of();
    ItemStack output = recipe.assemble(new IMoldingContainer() {
      @Override public ItemStack getMaterial() { return material.getFirst(); }
      @Override public ItemStack getPattern() { return patterns.isEmpty() ? ItemStack.EMPTY : patterns.getFirst(); }
      @Override public int getContainerSize() { return 2; }
    });
    List<List<Value>> inputs = new ArrayList<>(List.of(items(material)));
    List<List<Value>> catalysts = new ArrayList<>();
    if (hasPattern) (recipe.isPatternConsumed() ? inputs : catalysts).add(items(patterns));
    return one("molding", source, inputs, List.of(items(List.of(output))), catalysts, List.of(), List.of());
  }

  private static List<RecipeDisplayData> entityMelting(Identifier source, EntityMeltingRecipe recipe) {
    return one("entity_melting", source, List.of(entities(recipe.getIngredient().getTypes().stream().toList(), false)),
      List.of(List.of(new FluidValue(recipe.getOutput()))), List.of(),
      List.of(Component.translatable("jei.tconstruct.entity_melting.per_hearts", recipe.getDamage() / 2f)),
      items(recipe.getIngredient().getEggs()));
  }

  public static List<RecipeDisplayData> defaultEntityMelting(List<EntityMeltingRecipe> recipes) {
    List<EntityType<?>> entities = new ArrayList<>();
    for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
      if (type.builtInRegistryHolder().is(TinkerTags.EntityTypes.MELTING_HIDE)
          || type.getCategory() == MobCategory.MISC && !type.builtInRegistryHolder().is(TinkerTags.EntityTypes.MELTING_SHOW)) continue;
      if (recipes.stream().noneMatch(recipe -> recipe.matches(type))) entities.add(type);
    }
    return one("entity_melting", TConstruct.getResource("__default_entity_melting"), List.of(entities(entities, false)),
      List.of(List.of(new FluidValue(EntityMeltingModule.getDefaultFluid()))), List.of(),
      List.of(Component.translatable("jei.tconstruct.entity_melting.per_heart")), List.of());
  }

  private static List<RecipeDisplayData> severing(Identifier source, SeveringRecipe recipe) {
    List<Component> notes = List.of(
      text("severing_chance", "Chance per Severing level: %s%%", recipe.getChance(1, 0) * 100f),
      text("severing_looting", "Added per Looting and Severing level: %s%%", (recipe.getChance(1, 1) - recipe.getChance(1, 0)) * 100f));
    List<RecipeDisplayData> result = new ArrayList<>(one("severing", source,
      List.of(entities(recipe.getIngredient().getTypes().stream().toList(), false)), List.of(items(List.of(recipe.getOutput()))), List.of(), notes,
      items(recipe.getIngredient().getEggs())));
    if (recipe instanceof AgeableSeveringRecipe ageable && !ageable.getChildDisplayOutput().isEmpty()) {
      result.addAll(one("severing", source, List.of(entities(recipe.getIngredient().getTypes().stream().toList(), true)),
        List.of(items(List.of(ageable.getChildDisplayOutput()))), List.of(), notes, items(recipe.getIngredient().getEggs())));
    }
    return result;
  }

  private static List<RecipeDisplayData> material(Identifier source, MaterialRecipe recipe) {
    if (recipe.getMaterial().isUnknown() || recipe.getMaterial().get().isHidden()) return List.of();
    List<Component> notes = new ArrayList<>();
    if (recipe.hasLeftover()) notes.add(text("material_change", "Unused material value returns change: %s", recipe.getLeftover().getHoverName()));
    return one("materials", source, List.of(items(recipe.getDisplayItems())),
      List.of(List.of(new MaterialValue(recipe.getMaterial().getVariant(), recipe.getValue()))), List.of(), notes, List.of());
  }

  private static List<RecipeDisplayData> materialFluid(Identifier source, MaterialFluidRecipe recipe) {
    if (!recipe.isVisible()) return List.of();
    List<List<Value>> inputs = new ArrayList<>();
    inputs.add(recipe.getFluids().stream().<Value>map(FluidValue::new).toList());
    if (recipe.getInput() != null) inputs.add(List.of(new MaterialValue(recipe.getInput().getVariant(), 1)));
    return one("materials", source, inputs, List.of(List.of(new MaterialValue(recipe.getOutput().getVariant(), 1))), List.of(),
      List.of(text("material_unit", "Fluid amount is for one material unit.")), List.of());
  }

  private static List<RecipeDisplayData> building(Identifier source, ToolBuildingRecipe recipe) {
    List<List<Value>> inputs = new ArrayList<>();
    for (var part : recipe.getToolParts()) {
      inputs.add(items(MaterialRecipeCache.getAllVariants().stream().filter(mat -> part.canUseMaterial(mat.getMaterialId())).map(part::withMaterial).toList()));
    }
    recipe.getExtraRequirements().forEach(ingredient -> inputs.add(items(MaterialRecipeCache.getDisplayItems(ingredient))));
    return one("tool_recipes", source, inputs, List.of(items(recipe.getDisplayOutput())), List.of(),
      List.of(text("tool_materials", "Tool materials follow the chosen parts; material traits can change the result count."),
        text("tool_station", recipe.requiresAnvil() ? "Requires a Tinker's Anvil." : "Craft in a Tinker Station or Tinker's Anvil.")), List.of());
  }

  private static List<RecipeDisplayData> modifier(Identifier source, IDisplayModifierRecipe recipe, Level level) {
    var context = SlotDisplayContext.fromLevel(level);
    List<List<Value>> inputs = new ArrayList<>();
    for (int slot = 0; slot < recipe.getInputCount(context); slot++) inputs.add(items(recipe.getDisplayItems(slot, context)));
    if (recipe.getSlots() != null) inputs.add(List.of(new SlotValue(recipe.getSlots())));
    List<List<Value>> outputs = new ArrayList<>(List.of(List.of(new ModifierValue(recipe.getDisplayResult()))));
    recipe.getResultSlots().forEach(slots -> outputs.add(List.of(new SlotValue(slots))));
    List<Component> notes = new ArrayList<>();
    if (recipe.getVariant() != null) notes.add(recipe.getVariant());
    else notes.add(Component.translatable("jei.tconstruct.modifiers.level.range", recipe.getLevel().min(), recipe.getLevel().max()));
    if (recipe.isIncremental()) notes.add(Component.translatable("jei.tconstruct.modifiers.incremental"));
    Component requirements = recipe.getDisplayResult().getHook(ModifierHooks.REQUIREMENTS).requirementsError(recipe.getDisplayResult());
    if (requirements != null) notes.add(requirements);
    if (recipe.getSlots() == null) notes.add(Component.translatable("jei.tconstruct.modifiers.free"));
    return one("modifiers", source, inputs, outputs, List.of(items(recipe.getToolWithoutModifier(context))), notes, List.of());
  }

  private static List<RecipeDisplayData> worktable(Identifier source, IModifierWorktableRecipe recipe) {
    List<List<Value>> inputs = new ArrayList<>();
    for (int slot = 0; slot < recipe.getInputCount(); slot++) inputs.add(items(recipe.getDisplayItems(slot)));
    var tools = recipe.getInputTools();
    var modifiers = recipe.getModifierOptions(null);
    if (modifiers.isEmpty()) return List.of();
    int variants = recipe.linkToolsModifiers() && tools.size() == modifiers.size() ? tools.size() : 1;
    List<RecipeDisplayData> result = new ArrayList<>();
    for (int i = 0; i < variants; i++) {
      var recipeInputs = new ArrayList<>(inputs);
      var catalysts = new ArrayList<List<Value>>();
      var outputs = new ArrayList<List<Value>>();
      (recipe.isToolInput() ? recipeInputs : catalysts).add(items(variants > 1 ? List.of(tools.get(i)) : tools));
      var modifierValues = (variants > 1 ? List.of(modifiers.get(i)) : modifiers).stream().<Value>map(ModifierValue::new).toList();
      if (!modifierValues.isEmpty()) (recipe.isModifierOutput() ? outputs : catalysts).add(modifierValues);
      result.addAll(one("worktable", source, recipeInputs, outputs, catalysts,
        List.of(recipe.getTitle(), recipe.getDescription(null)), List.of()));
    }
    return result;
  }

  private static List<RecipeDisplayData> parts(Identifier source, RecipeHolder<?> holder, IPartBuilderRecipe original, RegistryAccess access, Level level) {
    List<RecipeDisplayData> result = new ArrayList<>();
    for (IDisplayPartBuilderRecipe display : RecipeHelper.getJEIRecipes(access, Stream.of(holder), IDisplayPartBuilderRecipe.class)) {
      List<ItemStack> materials = display.getMaterialItems();
      if (materials.isEmpty() && display.getCost() == 0) materials = List.of(ItemStack.EMPTY);
      for (ItemStack material : materials) {
        IMaterialValue value = materialValue(material);
        for (ItemStack pattern : display.getPatternItems()) {
          PartInput probe = new PartInput(material, pattern, value);
          int used = original.getItemsUsed(probe);
          PartInput input = new PartInput(material.isEmpty() ? ItemStack.EMPTY : material.copyWithCount(used), pattern, value);
          if (!original.partialMatch(input) || !original.matches(input, level)) continue;
          ItemStack output = original.assemble(input, display.getPattern());
          if (output.isEmpty()) continue;
          List<List<Value>> inputs = new ArrayList<>();
          List<List<Value>> catalysts = new ArrayList<>(List.of(List.of(new PatternValue(display.getPattern()))));
          if (!material.isEmpty()) inputs.add(items(List.of(input.stack())));
          (pattern.is(TinkerTags.Items.REUSABLE_PATTERNS) ? catalysts : inputs).add(items(List.of(pattern.copyWithCount(1))));
          List<List<Value>> outputs = new ArrayList<>(List.of(items(List.of(output))));
          addContainer(outputs, input.stack(), used);
          if (!pattern.is(TinkerTags.Items.REUSABLE_PATTERNS)) addContainer(outputs, pattern, 1);
          List<Component> notes = new ArrayList<>();
          if (display.getCost() > 0) notes.add(Component.translatable("jei.tconstruct.part_builder.cost", display.getCost()));
          if (original instanceof PartBuilderRecycle recycling) {
            List<ItemStack> leftovers = recycling.getDisplayLeftovers(input, display.getPattern());
            if (!leftovers.isEmpty()) {
              outputs.add(items(leftovers));
              notes.add(text("random_recycling_change", "Also returns one of the shown extra results at random."));
            }
            notes.add(text("recycling_condition", "Undamaged input shown; damage reduces the recovered amount."));
          } else if (original instanceof PartBuilderToolRecycle recycling) {
            List<ItemStack> leftovers = recycling.getDisplayLeftovers(input, display.getPattern());
            if (!leftovers.isEmpty()) outputs.add(items(leftovers));
            notes.add(text("tool_recycling_change", "Undamaged example: also returns one randomly chosen extra part. Damage reduces that chance."));
          } else {
            ItemStack leftover = original.getLeftover(input, display.getPattern());
            if (!leftover.isEmpty()) outputs.add(items(List.of(leftover)));
          }
          result.addAll(one("part_builder", source, inputs, outputs, catalysts, notes, List.of()));
        }
      }
    }
    return result;
  }

  private static void addContainer(List<List<Value>> outputs, ItemStack input, int count) {
    if (!input.isEmpty() && count > 0) {
      var remainder = input.getCraftingRemainder();
      if (remainder != null) {
        ItemStack stack = remainder.create();
        if (!stack.isEmpty()) outputs.add(items(List.of(stack.copyWithCount(stack.getCount() * count))));
      }
    }
  }

  private static IMaterialValue materialValue(ItemStack stack) {
    if (stack.isEmpty()) return null;
    if (stack.is(TinkerTags.Items.TOOL_PARTS)) {
      var material = IMaterialItem.getMaterialFromStack(stack);
      int cost = MaterialCastingLookup.getItemCost(stack.getItem());
      return cost == 0 || IMaterial.UNKNOWN_ID.matchesVariant(material) ? null
        : new slimeknights.tconstruct.library.recipe.material.MaterialValue(material, cost);
    }
    return MaterialRecipeCache.getAllRecipes().stream().filter(recipe -> IngredientHelper.test(recipe.getIngredient(), stack)).findFirst().orElse(null);
  }

  private record PartInput(ItemStack stack, ItemStack pattern, IMaterialValue material) implements IPartBuilderContainer {
    @Override public ItemStack getStack() { return stack; }
    @Override public ItemStack getPatternStack() { return pattern; }
    @Override public IMaterialValue getMaterial() { return material; }
  }

  public static List<Value> items(List<ItemStack> items) {
    return items.stream().filter(stack -> !stack.isEmpty()).<Value>map(ItemValue::new).toList();
  }

  private static List<Value> entities(List<EntityType<?>> types, boolean baby) {
    return types.stream().<Value>map(type -> new EntityValue(type, baby)).toList();
  }

  private static Component text(String key, String fallback, Object... args) {
    return Component.translatableWithFallback("rei.tconstruct." + key, fallback, args);
  }

  private static List<RecipeDisplayData> one(String category, Identifier source, List<List<Value>> inputs, List<List<Value>> outputs,
                                            List<List<Value>> catalysts, List<Component> notes, List<Value> lookupInputs) {
    if (Stream.of(inputs, outputs, catalysts).flatMap(List::stream).anyMatch(List::isEmpty)) return List.of();
    return List.of(new RecipeDisplayData(TConstruct.getResource(category), source, inputs, outputs, catalysts, notes, lookupInputs));
  }
}
