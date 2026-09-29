package slimeknights.tconstruct.library.client.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
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
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe;
import slimeknights.tconstruct.library.recipe.casting.IDisplayableCastingRecipe;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuel;
import slimeknights.tconstruct.library.recipe.melting.IMeltingContainer.OreRateType;
import slimeknights.tconstruct.library.recipe.melting.MeltingRecipe;
import slimeknights.tconstruct.library.tools.layout.LayoutSlot;

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
    // Some producers write into the focus stack (the damaging recipe's onFocused syncs the damaged tool back into it).
    // The focus is the viewer's own entry, so work on a copy and never change what the viewer lists.
    focus = focus.copy();
    if (focus.isEmpty() ? !recipe.showUnfocused() : recipe.isFiltered() && !recipe.isVisibleFromItem(focus, focusOutput)) return List.of();
    List<ItemStack> before = recipe.getToolWithoutModifier(focus, focusOutput);
    List<ItemStack> after = recipe.getToolWithModifier(focus, focusOutput);
    if (!focus.isEmpty() && !focusOutput && recipe.isTool(focus)) {
      // its own copy, so the "before" tool below keeps the focus state even if onFocused writes into its argument
      var focused = recipe.onFocused(focus.copy());
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
      // Station input index of every item input after the tool, so layouts can place each slot like official JEI.
      List<Integer> stationSlots = new ArrayList<>();
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
          stationSlots.add(slot);
          addReturn(outputs, refunds, variant);
          addReturn(outputs, containers, variant);
        } else if (!refunds.isEmpty() || !containers.isEmpty()) {
          independent.add(new SlotAlternatives(alternatives, refunds, containers, slot));
        } else {
          inputs.add(items(alternatives));
          stationSlots.add(slot);
        }
      }
      CompoundTag layout = new CompoundTag();
      layout.putBoolean(RecipeLayout.TOOL_INPUT, !recipe.isToolCatalyst());
      expandReturns(result, source, inputs, outputs, catalysts, notes, independent, 0, stationSlots, layout);
    }
    return result;
  }

  /** @param station  station input index of these alternatives, for layouts */
  private record SlotAlternatives(List<ItemStack> inputs, List<ItemStack> refunds, List<ItemStack> containers, int station) {
    /** Original shape; the station index is unknown. */
    SlotAlternatives(List<ItemStack> inputs, List<ItemStack> refunds, List<ItemStack> containers) {
      this(inputs, refunds, containers, -1);
    }
  }

  private static void addReturn(List<List<Value>> outputs, List<ItemStack> values, int index) {
    if (index < values.size() && !values.get(index).isEmpty()) outputs.add(items(List.of(values.get(index))));
  }

  private static void expandReturns(List<RecipeDisplayData> result, Identifier source, List<List<Value>> inputs, List<List<Value>> outputs,
                                    List<List<Value>> catalysts, List<Component> notes, List<SlotAlternatives> slots, int slot) {
    expandReturns(result, source, inputs, outputs, catalysts, notes, slots, slot, List.of(), new CompoundTag());
  }

  /** Expands independent refund slots, recording the station index of every item input in the layout facts. */
  private static void expandReturns(List<RecipeDisplayData> result, Identifier source, List<List<Value>> inputs, List<List<Value>> outputs,
                                    List<List<Value>> catalysts, List<Component> notes, List<SlotAlternatives> slots, int slot,
                                    List<Integer> stationSlots, CompoundTag layout) {
    if (slot == slots.size()) {
      CompoundTag facts = layout.copy();
      facts.put(RecipeLayout.STATION_SLOTS, new IntArrayTag(stationSlots.stream().mapToInt(Integer::intValue).toArray()));
      result.addAll(one("tool_modification", source, inputs, outputs, catalysts, notes, List.of(), facts, List.of()));
      return;
    }
    SlotAlternatives alternatives = slots.get(slot);
    for (int i = 0; i < alternatives.inputs.size(); i++) {
      var nextInputs = new ArrayList<>(inputs);
      var nextOutputs = new ArrayList<>(outputs);
      var nextSlots = new ArrayList<>(stationSlots);
      nextInputs.add(items(List.of(alternatives.inputs.get(i))));
      nextSlots.add(alternatives.station);
      addReturn(nextOutputs, alternatives.refunds, i);
      addReturn(nextOutputs, alternatives.containers, i);
      expandReturns(result, source, nextInputs, nextOutputs, catalysts, notes, slots, slot + 1, nextSlots, layout);
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
    CompoundTag layout = new CompoundTag();
    layout.putBoolean(RecipeLayout.BASIN, recipe.getType() == TinkerRecipeTypes.MOLDING_BASIN.get());
    layout.putInt(RecipeLayout.CAST, !hasPattern ? RecipeLayout.ROLE_NONE : recipe.isPatternConsumed() ? RecipeLayout.ROLE_CONSUMED : RecipeLayout.ROLE_KEPT);
    return one("molding", source, inputs, List.of(items(List.of(output))), catalysts, List.of(), List.of(), layout, List.of());
  }

  private static List<RecipeDisplayData> entityMelting(Identifier source, EntityMeltingRecipe recipe) {
    CompoundTag layout = new CompoundTag();
    layout.putInt(RecipeLayout.DAMAGE, recipe.getDamage());
    return one("entity_melting", source, List.of(entities(recipe.getIngredient().getTypes().stream().toList(), false)),
      List.of(List.of(new FluidValue(recipe.getOutput()))), List.of(),
      List.of(Component.translatable("jei.tconstruct.entity_melting.per_hearts", recipe.getDamage() / 2f)),
      items(recipe.getIngredient().getEggs()), layout, List.of());
  }

  public static List<RecipeDisplayData> defaultEntityMelting(List<EntityMeltingRecipe> recipes) {
    List<EntityType<?>> entities = new ArrayList<>();
    for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
      if (type.builtInRegistryHolder().is(TinkerTags.EntityTypes.MELTING_HIDE)
          || type.getCategory() == MobCategory.MISC && !type.builtInRegistryHolder().is(TinkerTags.EntityTypes.MELTING_SHOW)) continue;
      if (recipes.stream().noneMatch(recipe -> recipe.matches(type))) entities.add(type);
    }
    // Official DefaultEntityMeltingRecipe deals 2 damage (one heart) per output.
    CompoundTag layout = new CompoundTag();
    layout.putInt(RecipeLayout.DAMAGE, 2);
    return one("entity_melting", TConstruct.getResource("__default_entity_melting"), List.of(entities(entities, false)),
      List.of(List.of(new FluidValue(EntityMeltingModule.getDefaultFluid()))), List.of(),
      List.of(Component.translatable("jei.tconstruct.entity_melting.per_heart")), List.of(), layout, List.of());
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
    // Official shows the leftover as its own output slot beside the value.
    List<List<Value>> outputs = new ArrayList<>(List.of(List.of(new MaterialValue(recipe.getMaterial().getVariant(), recipe.getValue()))));
    boolean leftover = recipe.hasLeftover() && !recipe.getLeftover().isEmpty();
    if (leftover) outputs.add(items(List.of(recipe.getLeftover())));
    CompoundTag layout = new CompoundTag();
    layout.putInt(RecipeLayout.VALUE, recipe.getValue());
    layout.putInt(RecipeLayout.NEEDED, recipe.getNeeded());
    layout.putBoolean(RecipeLayout.CRAFTABLE, recipe.getMaterial().get().isCraftable());
    layout.putBoolean(RecipeLayout.LEFTOVER, leftover);
    return one("materials", source, List.of(items(recipe.getDisplayItems())), outputs, List.of(), notes, List.of(), layout, List.of());
  }

  private static List<RecipeDisplayData> materialFluid(Identifier source, MaterialFluidRecipe recipe) {
    if (!recipe.isVisible()) return List.of();
    List<List<Value>> inputs = new ArrayList<>();
    inputs.add(recipe.getFluids().stream().<Value>map(FluidValue::new).toList());
    if (recipe.getInput() != null) inputs.add(List.of(new MaterialValue(recipe.getInput().getVariant(), 1)));
    CompoundTag layout = new CompoundTag();
    layout.putBoolean(RecipeLayout.COMPOSITE, recipe.getInput() != null);
    layout.putBoolean(RecipeLayout.CRAFTABLE, recipe.getOutput().get().isCraftable());
    return one("materials", source, inputs, List.of(List.of(new MaterialValue(recipe.getOutput().getVariant(), 1))), List.of(),
      List.of(text("material_unit", "Fluid amount is for one material unit.")), List.of(), layout, List.of());
  }

  private static List<RecipeDisplayData> building(Identifier source, ToolBuildingRecipe recipe) {
    List<List<Value>> inputs = new ArrayList<>();
    for (var part : recipe.getToolParts()) {
      inputs.add(items(MaterialRecipeCache.getAllVariants().stream().filter(mat -> part.canUseMaterial(mat.getMaterialId())).map(part::withMaterial).toList()));
    }
    recipe.getExtraRequirements().forEach(ingredient -> inputs.add(items(MaterialRecipeCache.getDisplayItems(ingredient))));
    // Official positions every input at its station layout slot, offset by the recipe's display offsets.
    CompoundTag layout = new CompoundTag();
    List<LayoutSlot> slots = recipe.getLayoutSlots();
    int[] positions = new int[slots.size() * 2];
    for (int i = 0; i < slots.size(); i++) {
      positions[i * 2] = slots.get(i).getX() + ToolBuildingRecipe.X_OFFSET;
      positions[i * 2 + 1] = slots.get(i).getY() + ToolBuildingRecipe.Y_OFFSET;
    }
    layout.put(RecipeLayout.LAYOUT_SLOTS, new IntArrayTag(positions));
    layout.putBoolean(RecipeLayout.ANVIL, recipe.requiresAnvil());
    layout.putInt(RecipeLayout.PART_COUNT, recipe.getToolParts().size());
    return one("tool_recipes", source, inputs, List.of(items(recipe.getDisplayOutput())), List.of(),
      List.of(text("tool_materials", "Tool materials follow the chosen parts; material traits can change the result count."),
        text("tool_station", recipe.requiresAnvil() ? "Requires a Tinker's Anvil." : "Craft in a Tinker Station or Tinker's Anvil.")), List.of(),
      layout, List.of());
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
    int requirementsNote = notes.size();
    if (requirements != null) notes.add(requirements);
    if (recipe.getSlots() == null) notes.add(Component.translatable("jei.tconstruct.modifiers.free"));
    CompoundTag layout = new CompoundTag();
    layout.putBoolean(RecipeLayout.VARIANT, recipe.getVariant() != null);
    layout.putInt(RecipeLayout.LEVEL_MIN, recipe.getLevel().min());
    layout.putInt(RecipeLayout.LEVEL_MAX, recipe.getLevel().max());
    if (requirements != null) layout.putInt(RecipeLayout.REQUIREMENTS, requirementsNote);
    // Modifier inputs are station slots 0 to n-1 in order; the slot cost entry is not an item and is excluded.
    int itemInputs = recipe.getInputCount(context);
    int[] stationSlots = new int[itemInputs];
    for (int slot = 0; slot < itemInputs; slot++) stationSlots[slot] = slot;
    layout.put(RecipeLayout.STATION_SLOTS, new IntArrayTag(stationSlots));
    layout.putBoolean(RecipeLayout.INCREMENTAL, recipe.isIncremental());
    layout.putBoolean(RecipeLayout.FREE, recipe.getSlots() == null);
    // Official draws the modified tool as a render-only result; it is not a lookup output.
    List<List<Value>> renderOnly = new ArrayList<>();
    List<Value> withModifier = items(recipe.getToolWithModifier(context));
    if (!withModifier.isEmpty()) renderOnly.add(withModifier);
    return one("modifiers", source, inputs, outputs, List.of(items(recipe.getToolWithoutModifier(context))), notes, List.of(), layout, renderOnly);
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
      CompoundTag layout = new CompoundTag();
      layout.putBoolean(RecipeLayout.TOOL_INPUT, recipe.isToolInput());
      layout.putBoolean(RecipeLayout.MODIFIER_OUTPUT, recipe.isModifierOutput());
      layout.putInt(RecipeLayout.ITEM_SLOTS, recipe.getInputCount());
      result.addAll(one("worktable", source, recipeInputs, outputs, catalysts,
        List.of(recipe.getTitle(), recipe.getDescription(null)), List.of(), layout, List.of()));
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
          // Official shows the material name as an input slot, which also drives material focus. It is appended last so
          // the material item stays the first input and the pattern item keeps its position.
          // Use the material the station really reads from this input item (the same lookup assembly used above), not the
          // page's own material: a base-material page can list variant items, such as crimson stems on a wood page.
          var runtimeMaterial = value == null ? null : value.getMaterial();
          boolean namedMaterial = runtimeMaterial != null && !runtimeMaterial.isUnknown() && !IMaterial.UNKNOWN_ID.matchesVariant(runtimeMaterial.getVariant());
          if (namedMaterial) {
            inputs.add(List.of(new MaterialValue(runtimeMaterial.getVariant(), Math.max(1, display.getCost()))));
          }
          CompoundTag layout = new CompoundTag();
          layout.putInt(RecipeLayout.COST, display.getCost());
          layout.putBoolean(RecipeLayout.REUSABLE, pattern.is(TinkerTags.Items.REUSABLE_PATTERNS));
          layout.putBoolean(RecipeLayout.MATERIAL_ITEM, !material.isEmpty());
          layout.putBoolean(RecipeLayout.MATERIAL_NAME, namedMaterial);
          result.addAll(one("part_builder", source, inputs, outputs, catalysts, notes, List.of(), layout, List.of()));
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

  /** As {@link #one(String, Identifier, List, List, List, List, List)}, with layout facts and render-only entries. */
  private static List<RecipeDisplayData> one(String category, Identifier source, List<List<Value>> inputs, List<List<Value>> outputs,
                                            List<List<Value>> catalysts, List<Component> notes, List<Value> lookupInputs,
                                            CompoundTag layout, List<List<Value>> renderOnly) {
    // A missing compatibility tag must not become a recipe with a free input.
    if (Stream.of(inputs, outputs, catalysts).flatMap(List::stream).anyMatch(List::isEmpty)) return List.of();
    // Render-only groups are decoration; an empty one is dropped instead of hiding the recipe.
    List<List<Value>> decoration = renderOnly.stream().filter(values -> !values.isEmpty()).toList();
    return List.of(new RecipeDisplayData(TConstruct.getResource(category), source, inputs, outputs, catalysts, notes, lookupInputs, layout, decoration));
  }


  /* Smeltery categories. These used to be built inside the REI adapter; they live here so the facts are viewer-neutral and testable. */

  /**
   * Maps a casting recipe to one display per paired cast/output variant.
   * @param basin  true for the casting basin category, false for the casting table
   */
  public static List<RecipeDisplayData> casting(Identifier source, IDisplayableCastingRecipe recipe, boolean basin) {
    List<ItemStack> outputs = recipe.getOutputs();
    List<ItemStack> casts = recipe.getCastItems();
    if (outputs.isEmpty() || recipe.getFluids().isEmpty() || recipe.hasCast() && casts.isEmpty()) return List.of();
    // These are paired variants, not independent alternatives. Split them instead of showing impossible combinations.
    int variants = outputs.size() > 1 && casts.size() == outputs.size() ? outputs.size() : 1;
    List<RecipeDisplayData> result = new ArrayList<>();
    for (int i = 0; i < variants; i++) {
      List<List<Value>> inputs = new ArrayList<>();
      List<List<Value>> catalysts = new ArrayList<>();
      inputs.add(fluids(recipe.getFluids()));
      if (recipe.hasCast()) {
        List<Value> cast = items(variants > 1 ? List.of(casts.get(i)) : casts);
        (recipe.isConsumed() ? inputs : catalysts).add(cast);
      }
      List<Component> notes = new ArrayList<>();
      notes.add(Component.translatable("jei.tconstruct.casting.time", recipe.getCoolingTime() / 20));
      if (recipe.hasCast()) notes.add(Component.translatable(recipe.isConsumed() ? "jei.tconstruct.casting.cast_consumed" : "jei.tconstruct.casting.cast_kept"));
      CompoundTag layout = new CompoundTag();
      layout.putInt(RecipeLayout.COOLING, recipe.getCoolingTime());
      layout.putInt(RecipeLayout.CAST, !recipe.hasCast() ? RecipeLayout.ROLE_NONE : recipe.isConsumed() ? RecipeLayout.ROLE_CONSUMED : RecipeLayout.ROLE_KEPT);
      layout.putBoolean(RecipeLayout.BASIN, basin);
      result.addAll(one(basin ? "casting_basin" : "casting_table", source, inputs,
        List.of(items(variants > 1 ? List.of(outputs.get(i)) : outputs)), catalysts, notes, List.of(), layout, List.of()));
    }
    return result;
  }

  /**
   * Maps a melting recipe to its melting display and its foundry display. Official shows one melting page whose output
   * tooltip gives the melter and smeltery amounts separately; both boosted amounts are kept as layout facts.
   */
  public static List<RecipeDisplayData> melting(Identifier source, MeltingRecipe recipe) {
    List<Value> input = items(MaterialRecipeCache.getDisplayItems(recipe.getInput()));
    FluidStack output = recipe.getOutput();
    if (input.isEmpty() || output.isEmpty()) return List.of();
    OreRateType ore = recipe.getOreType();
    boolean boosted = ore == OreRateType.METAL || ore == OreRateType.GEM;
    int melter = boosted ? Config.COMMON.melterOreRate.applyOreBoost(ore, output.getAmount()) : output.getAmount();
    int smeltery = boosted ? Config.COMMON.smelteryOreRate.applyOreBoost(ore, output.getAmount()) : output.getAmount();
    List<Component> notes = new ArrayList<>(List.of(
      Component.translatable("jei.tconstruct.temperature", recipe.getTemperature()),
      Component.translatable("jei.tconstruct.melting.time", recipe.getTime() / 4)));
    if (boosted) notes.add(Component.translatable("jei.tconstruct.melting.ore"));
    CompoundTag layout = new CompoundTag();
    layout.putInt(RecipeLayout.TEMPERATURE, recipe.getTemperature());
    layout.putInt(RecipeLayout.TIME, recipe.getTime());
    layout.putInt(RecipeLayout.ORE, ore == OreRateType.METAL ? RecipeLayout.ORE_METAL : ore == OreRateType.GEM ? RecipeLayout.ORE_GEM : RecipeLayout.ORE_NONE);
    layout.putInt(RecipeLayout.MELTER_AMOUNT, melter);
    layout.putInt(RecipeLayout.SMELTERY_AMOUNT, smeltery);
    List<RecipeDisplayData> result = new ArrayList<>(one("melting", source, List.of(input), List.of(List.of(new FluidValue(output))), List.of(),
      notes, List.of(), layout, List.of()));
    // The foundry adds byproducts and does not apply the melter/smeltery ore boost.
    CompoundTag foundry = new CompoundTag();
    foundry.putInt(RecipeLayout.TEMPERATURE, recipe.getTemperature());
    foundry.putInt(RecipeLayout.TIME, recipe.getTime());
    // official draws the ore marker on both melting categories
    foundry.putInt(RecipeLayout.ORE, layout.getIntOr(RecipeLayout.ORE, RecipeLayout.ORE_NONE));
    result.addAll(one("foundry", source, List.of(input), recipe.getOutputWithByproducts().stream().map(RecipeDisplayMapper::fluids).toList(), List.of(),
      notes.subList(0, 2), List.of(), foundry, List.of()));
    return result;
  }

  /** Maps an alloy recipe. Inputs and catalysts each keep recipe order; the mask records how they interleave. */
  public static List<RecipeDisplayData> alloy(Identifier source, AlloyRecipe recipe) {
    if (recipe.getOutput().isEmpty()) return List.of();
    List<List<Value>> inputs = new ArrayList<>();
    List<List<Value>> catalysts = new ArrayList<>();
    int mask = 0;
    List<AlloyRecipe.AlloyIngredient> ingredients = recipe.getInputs();
    for (int i = 0; i < ingredients.size(); i++) {
      AlloyRecipe.AlloyIngredient ingredient = ingredients.get(i);
      (ingredient.catalyst() ? catalysts : inputs).add(fluids(ingredient.fluid().getFluids()));
      if (ingredient.catalyst() && i < 31) mask |= 1 << i;
    }
    CompoundTag layout = new CompoundTag();
    layout.putInt(RecipeLayout.TEMPERATURE, recipe.getTemperature());
    layout.putInt(RecipeLayout.CATALYST_MASK, mask);
    return one("alloy", source, inputs, List.of(List.of(new FluidValue(recipe.getOutput()))), catalysts,
      List.of(Component.translatable("jei.tconstruct.temperature", recipe.getTemperature())), List.of(), layout, List.of());
  }

  /** Maps a liquid fuel. Returns nothing for the solid fuel recipe, which uses {@link #solidFuel}. */
  public static List<RecipeDisplayData> liquidFuel(Identifier source, MeltingFuel fuel) {
    if (fuel.getInput() == FluidIngredient.EMPTY || fuel.getInputs().isEmpty()) return List.of();
    CompoundTag layout = fuelLayout(fuel, fuel.getDuration(), false);
    return one("fuel", source, List.of(fluids(fuel.getInputs())), List.of(), List.of(), fuelNotes(fuel, fuel.getDuration()), List.of(), layout, List.of());
  }

  /**
   * Maps the solid fuel page. With an empty stack this is the unfocused page cycling the example fuels, like official;
   * with a stack it is the focused page for that item, including its crafting remainder.
   * @param heater    heater item shown as the required catalyst, since only the heater burns solid fuel
   * @param examples  example fuels for the unfocused page
   * @param stack     focused fuel, or empty for the examples page
   */
  public static List<RecipeDisplayData> solidFuel(Identifier source, MeltingFuel fuel, ItemStack heater, List<ItemStack> examples, ItemStack stack, Level level) {
    if (fuel.getInput() != FluidIngredient.EMPTY || level == null) return List.of();
    if (stack.isEmpty()) {
      List<ItemStack> burnable = examples.stream().filter(example -> solidFuelDuration(example, level) > 0).map(example -> example.copyWithCount(1)).toList();
      return one("fuel", source, List.of(items(burnable)), List.of(), heater.isEmpty() ? List.of() : List.of(items(List.of(heater))),
        List.of(Component.translatable("jei.tconstruct.temperature", fuel.getTemperature()),
          Component.translatable("jei.tconstruct.melting.multiplier", fuel.getRate() / 10f)),
        List.of(), fuelLayout(fuel, 0, true), List.of());
    }
    int duration = solidFuelDuration(stack, level);
    if (duration <= 0) return List.of();
    ItemStack single = stack.copyWithCount(1);
    var remainder = single.getCraftingRemainder();
    ItemStack container = remainder == null ? ItemStack.EMPTY : remainder.create();
    List<List<Value>> outputs = container.isEmpty() ? List.of() : List.of(items(List.of(container)));
    return one("fuel", source, List.of(items(List.of(single))), outputs, heater.isEmpty() ? List.of() : List.of(items(List.of(heater))),
      fuelNotes(fuel, duration), List.of(), fuelLayout(fuel, duration, true), List.of());
  }

  /** Solid fuel duration in heater units, matching SolidFuelModule's item override, event result and integer division. */
  public static int solidFuelDuration(ItemStack stack, Level level) {
    if (stack.isEmpty() || level == null) return 0;
    return stack.getBurnTime(TinkerRecipeTypes.FUEL.get(), level.fuelValues()) / 4;
  }

  /**
   * Liquid fuels usable at the given temperature, ordered from coolest to hottest like official MeltingFuelHandler.
   * @param fuels        all fuel recipes from the current snapshot
   * @param temperature  recipe temperature
   */
  public static List<FluidStack> usableFuels(Collection<MeltingFuel> fuels, int temperature) {
    return fuels.stream()
      .filter(fuel -> fuel.getInput() != FluidIngredient.EMPTY && fuel.getTemperature() >= temperature)
      .sorted(Comparator.comparingInt(MeltingFuel::getTemperature))
      .flatMap(fuel -> fuel.getInputs().stream())
      .filter(stack -> !stack.isEmpty())
      .toList();
  }

  private static CompoundTag fuelLayout(MeltingFuel fuel, int duration, boolean solid) {
    CompoundTag layout = new CompoundTag();
    layout.putInt(RecipeLayout.TEMPERATURE, fuel.getTemperature());
    layout.putInt(RecipeLayout.RATE, fuel.getRate());
    layout.putInt(RecipeLayout.DURATION, duration);
    layout.putBoolean(RecipeLayout.SOLID, solid);
    return layout;
  }

  private static List<Component> fuelNotes(MeltingFuel fuel, int duration) {
    return List.of(
      Component.translatable("jei.tconstruct.temperature", fuel.getTemperature()),
      text("fuel_duration", "Burn time at minimum fuel use: %s s", duration / 5f),
      Component.translatable("jei.tconstruct.melting.multiplier", fuel.getRate() / 10f),
      text("fuel_structure_cost", "Larger structures may consume fuel faster"));
  }

  /** Fluid alternatives for one slot, dropping empty stacks. */
  public static List<Value> fluids(List<FluidStack> fluids) {
    return fluids.stream().filter(stack -> !stack.isEmpty()).<Value>map(FluidValue::new).toList();
  }
}
