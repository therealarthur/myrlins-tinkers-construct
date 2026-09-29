package aebm.continuumtests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.recipe.ingredient.SizedIngredient;
import slimeknights.tconstruct.library.json.predicate.material.MaterialPredicate;
import slimeknights.tconstruct.library.json.predicate.material.MaterialStatTypePredicate;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipe;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.tinkerstation.IDisplayToolTinkering;
import slimeknights.tconstruct.library.recipe.tinkerstation.IMutableTinkerStationContainer;
import slimeknights.tconstruct.library.recipe.tinkerstation.building.MaterialValueSwappingRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.repairing.ModifierRepairTinkerStationRecipe;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tables.recipe.TinkerStationDamagingRecipe;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.tools.stats.SlimeStats;
import slimeknights.tconstruct.tools.stats.StatlessMaterialStats;

/** Compares viewer data with loaded materials and production station transactions without world edits. */
@EventBusSubscriber(modid = "aebmcontinuumtests")
public final class ToolTinkeringServerFixture {
  private ToolTinkeringServerFixture() {}

  @SubscribeEvent
  public static void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("aebmtooltinkeringtest")
      .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
      .executes(context -> new Suite(context.getSource()).run()));
  }

  private static final class Suite {
    private final CommandSourceStack source;
    private int passed;
    private int failed;
    Suite(CommandSourceStack source) { this.source = source; }

    int run() {
      test("six_loaded_costs_and_indexed_holes", () -> {
        checkCost("goggles_cuirass", TinkerTools.travelersGear.get(ArmorType.HELMET), 3, 1);
        checkCost("vest_cuirass", TinkerTools.travelersGear.get(ArmorType.CHESTPLATE), 6, 1);
        checkCost("pants_cuirass", TinkerTools.travelersGear.get(ArmorType.LEGGINGS), 4, 1);
        checkCost("boots_cuirass", TinkerTools.travelersGear.get(ArmorType.BOOTS), 2, 1);
        checkCost("boots_cuirass", TinkerTools.travelersShield.get(), 2, 1);
        checkCost("shield_wood", TinkerTools.travelersShield.get(), 2, 0);
      });
      test("stat_filtered_pairs_reject_unfiltered_output_control", () -> {
        // ANY deliberately includes recipes for incompatible stats. The upstream output loop used
        // this unfiltered collection even when it filtered the material inputs for each tool.
        var recipe = new ValueRecipe(TinkerTools.travelersGear.get(ArmorType.HELMET), MaterialPredicate.ANY, 3, List.of());
        var display = only(recipe);
        long incompatible = MaterialRecipeCache.getAllRecipes().stream()
          .filter(r -> !StatlessMaterialStats.CUIRASS.getIdentifier().canUseMaterial(r.getMaterial().getId()))
          .flatMap(r -> r.getDisplayItems().stream()).count();
        require(incompatible > 0, "negative control needs incompatible material recipes");
        checkPairs(recipe, display, 1, ItemStack.EMPTY, false);
        for (ItemStack output : display.getToolWithModifier()) {
          require(StatlessMaterialStats.CUIRASS.getIdentifier().canUseMaterial(ToolStack.from(output).getMaterial(1).getId()), "unfiltered output material escaped");
        }
      });
      test("indexed_material_and_three_sized_extras", () -> {
        var recipe = new ValueRecipe(TinkerTools.travelersGear.get(ArmorType.HELMET), cuirass(), 3,
          List.of(SizedIngredient.fromItems(2, Items.IRON_INGOT), SizedIngredient.fromItems(3, Items.GOLD_INGOT), SizedIngredient.fromItems(Items.DIAMOND)));
        var display = only(recipe);
        require(display.getInputCount() == 4, "slot span must include every extra after indexed material");
        require(display.getDisplayItems(0).getFirst().is(Items.IRON_INGOT) && display.getDisplayItems(0).getFirst().getCount() == 2, "first sized extra lost");
        require(display.getDisplayItems(2).getFirst().is(Items.GOLD_INGOT) && display.getDisplayItems(2).getFirst().getCount() == 3, "second sized extra lost");
        require(display.getDisplayItems(3).getFirst().is(Items.DIAMOND), "last extra lost");
        require(display.matchesItem(stack -> stack.is(Items.DIAMOND), false), "input search must include last extra");
        checkPairs(recipe, display, 1, ItemStack.EMPTY, false);
      });
      test("empty_required_tag_omits_recipe", () -> {
        var missing = SizedIngredient.fromTag(TagKey.create(Registries.ITEM, id("absent_required_input")), 2);
        var recipe = new ValueRecipe(TinkerTools.travelersGear.get(ArmorType.HELMET), cuirass(), 3, List.of(missing));
        require(recipe.getRecipes(source.getLevel().registryAccess()).isEmpty(), "unresolved required tag must not display a free swap");
      });
      test("focus_pairs_components_and_noop_filter", () -> {
        var recipe = recipe("goggles_cuirass");
        var display = only(recipe);
        MaterialVariantId whiteWool = MaterialVariantId.create(MaterialIds.wool, "white");
        ToolStack tool = tool(TinkerTools.travelersGear.get(ArmorType.HELMET), MaterialIds.copper, whiteWool);
        tool.addModifier(ModifierIds.reinforced, 1);
        tool.setDamage(17);
        ItemStack focus = tool.createStack();
        focus.set(DataComponents.CUSTOM_NAME, Component.literal("Material focus probe"));
        ItemStack original = focus.copy();
        require(display.getDisplayItems(1).stream().anyMatch(stack -> stack.is(Items.WHITE_WOOL)), "negative control needs the unchanged white-wool alternative");
        checkPairs(recipe, display, 1, focus, false);
        require(display.getDisplayItems(1, focus, false).stream().noneMatch(stack -> stack.is(Items.WHITE_WOOL)), "focused inputs must exclude the unchanged material");
        require(!display.getToolWithModifier(focus, false).isEmpty(), "focus must exercise replacements");
        for (ItemStack output : display.getToolWithModifier(focus, false)) {
          ToolStack changed = ToolStack.from(output);
          require(!changed.getMaterial(1).sameVariant(whiteWool), "same-material no-op was displayed");
          require(changed.getMaterial(0).sameVariant(MaterialIds.copper), "unrelated material changed");
          require(changed.getDamage() == 17 && changed.getUpgrades().getLevel(ModifierIds.reinforced) == 1, "focus tool state lost");
          require(focus.get(DataComponents.CUSTOM_NAME).equals(output.get(DataComponents.CUSTOM_NAME)), "focus component lost");
        }
        checkPairs(recipe, display, 1, focus, true);
        require(!display.getDisplayItems(1, focus, true).isEmpty(), "output focus must find wool inputs");
        require(ItemStack.matches(focus, original), "display mutated focus");
      });
      test("log_refund_matches_station_transaction", () -> {
        var recipe = recipe("shield_wood");
        var display = only(recipe);
        int index = find(display.getDisplayItems(0), Items.OAK_LOG);
        ItemStack input = display.getDisplayItems(0).get(index);
        require(input.getCount() == 1, "two material units require one log");
        ItemStack refund = display.getDisplayRemainders(0).get(index);
        require(refund.is(Items.OAK_PLANKS) && refund.getCount() == 2, "display must return two unspent planks");
        var inv = new Inventory(tool(TinkerTools.travelersShield.get(), MaterialIds.bone, MaterialIds.leather).createStack(), input.copy());
        require(recipe.matches(inv, source.getLevel()), "displayed log must match station");
        var result = recipe.getValidatedResult(inv, source.getLevel().registryAccess());
        require(result.isSuccess(), "displayed log must validate");
        require(result.getResult().getTool().getMaterial(0).sameVariant(ToolStack.from(display.getToolWithModifier().get(index)).getMaterial(0).getVariant()), "display and station output material differ");
        recipe.updateInputs(result.getResult(), inv, true);
        require(inv.getInput(0).isEmpty(), "station must consume displayed log quantity");
        require(inv.given.size() == 1 && ItemStack.matches(refund, inv.given.getFirst()), "station refund differs from display");
      });
      test("honey_container_matches_station_transaction", () -> {
        var recipe = new ValueRecipe(TinkerTools.slimesuit.get(ArmorType.HELMET), new MaterialStatTypePredicate(SlimeStats.ID), 2, List.of());
        var display = only(recipe);
        int index = find(display.getDisplayItems(1), Items.HONEY_BOTTLE);
        ItemStack input = display.getDisplayItems(1).get(index);
        ItemStack container = display.getDisplayContainers(1).get(index);
        require(input.getCount() == 2 && container.is(Items.GLASS_BOTTLE) && container.getCount() == 2, "container count must follow consumed honey bottles");
        require(display.getDisplayRemainders(1).get(index).isEmpty(), "exact-value honey must have empty material-refund placeholder");
        var inv = new Inventory(tool(TinkerTools.slimesuit.get(ArmorType.HELMET), MaterialIds.bone, MaterialIds.venom).createStack(), input.copy());
        require(recipe.matches(inv, source.getLevel()), "displayed honey must match station");
        var result = recipe.getValidatedResult(inv, source.getLevel().registryAccess());
        require(result.isSuccess(), "displayed honey must validate");
        recipe.updateInputs(result.getResult(), inv, true);
        require(ItemStack.matches(container, inv.getInput(0)) && inv.given.isEmpty(), "actual container differs from display");
      });
      test("damage_focus_matches_station", () -> {
        var recipe = new TinkerStationDamagingRecipe(id("damage"), Ingredient.of(Items.FLINT), 15);
        ItemStack focus = tool(TinkerTools.travelersGear.get(ArmorType.HELMET), MaterialIds.copper, MaterialIds.leather).createStack();
        focus.set(DataComponents.CUSTOM_NAME, Component.literal("Damage focus probe"));
        ItemStack before = focus.copy();
        var inv = new Inventory(focus, new ItemStack(Items.FLINT));
        require(recipe.matches(inv, source.getLevel()), "damage fixture must really match");
        var station = recipe.getValidatedResult(inv, source.getLevel().registryAccess());
        var displayed = recipe.onFocused(focus);
        require(station.isSuccess() && displayed.isSuccess(), "damage results must succeed");
        require(ItemStack.matches(station.getResult().getStack(), displayed.getResult()), "focused damage differs from station");
        require(ItemStack.matches(focus, before), "damage display mutated focus");
      });
      test("modifier_repair_focus_matches_station", () -> {
        var recipe = new ModifierRepairTinkerStationRecipe(id("repair"), ModifierIds.tasty, Ingredient.of(Items.APPLE), 25);
        ToolStack tool = tool(TinkerTools.travelersGear.get(ArmorType.HELMET), MaterialIds.copper, MaterialIds.leather);
        tool.addModifier(ModifierIds.tasty, 2);
        tool.setDamage(80);
        ItemStack focus = tool.createStack();
        focus.set(DataComponents.CUSTOM_NAME, Component.literal("Repair focus probe"));
        ItemStack before = focus.copy();
        var inv = new Inventory(focus, new ItemStack(Items.APPLE));
        require(recipe.matches(inv, source.getLevel()), "repair fixture must really match");
        var station = recipe.getValidatedResult(inv, source.getLevel().registryAccess());
        var display = recipe.getRecipes(source.getLevel().registryAccess()).getFirst();
        var displayed = display.onFocused(focus);
        require(station.isSuccess() && displayed.isSuccess(), "repair results must succeed");
        require(ItemStack.matches(station.getResult().getStack(), displayed.getResult()), "focused repair differs from station");
        require(ToolStack.from(displayed.getResult()).getDamage() < 80, "repair comparison must exercise actual damage change");
        require(ItemStack.matches(focus, before), "repair display mutated focus");
      });
      // parity/rei: a modifier focus lists exactly the tools its recipes accept
      test("modifier_focus_lists_only_accepting_tools", this::modifierFocusTools);
      source.sendSuccess(() -> Component.literal("AEBM_TOOL_TINKERING_SUMMARY passed=" + passed + " failed=" + failed), false);
      return failed == 0 ? 1 : 0;
    }

    /**
     * For real modifier recipes, the viewer's modifier focus returns only that modifier's pages, and each page's tool list
     * is exactly the modifiable tools the recipe's tool requirement accepts.
     */
    private void modifierFocusTools() {
      var level = source.getLevel();
      var recipes = source.getServer().getRecipeManager().recipeMap();
      java.lang.reflect.Field requirementField;
      try {
        requirementField = slimeknights.tconstruct.library.recipe.modifiers.adding.AbstractModifierRecipe.class.getDeclaredField("toolRequirement");
        requirementField.setAccessible(true);
      } catch (ReflectiveOperationException exception) {
        throw new AssertionError("tool requirement field changed", exception);
      }
      List<slimeknights.tconstruct.library.client.recipe.RecipeDisplayData> all = new ArrayList<>();
      java.util.Map<Identifier, Ingredient> requirements = new java.util.HashMap<>();
      for (var holder : recipes.byType(slimeknights.tconstruct.library.recipe.TinkerRecipeTypes.TINKER_STATION.get())) {
        if (holder.value() instanceof slimeknights.tconstruct.library.recipe.modifiers.adding.AbstractModifierRecipe modifierRecipe) {
          try {
            requirements.put(holder.id().identifier(), (Ingredient) requirementField.get(modifierRecipe));
          } catch (IllegalAccessException exception) {
            throw new AssertionError(exception);
          }
        }
        all.addAll(slimeknights.tconstruct.library.client.recipe.RecipeDisplayMapper.map(holder, level.registryAccess(), level));
      }
      int checked = 0;
      for (var display : all) {
        if (!display.category().getPath().equals("modifiers") || display.catalysts().isEmpty()) continue;
        Ingredient requirement = requirements.get(display.source());
        if (requirement == null) continue; // multi-recipes and data-driven displays without a single requirement
        var focus = display.outputs().getFirst().getFirst();
        var pages = slimeknights.tconstruct.library.client.recipe.RecipeFocus.recipesFor(all, focus);
        require(pages.contains(display), "the focus must include the recipe's own page");
        var modifier = ((slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.ModifierValue) focus).modifier().getId();
        for (var page : pages) {
          require(page.outputs().stream().flatMap(List::stream).anyMatch(value ->
            value instanceof slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.ModifierValue other && other.modifier().getId().equals(modifier)),
            "a " + modifier + " focus opened a page for another modifier: " + page.source());
        }
        java.util.Set<Item> shown = new java.util.HashSet<>();
        for (var value : display.catalysts().getFirst()) {
          ItemStack tool = ((slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.ItemValue) value).stack();
          require(requirement.test(new ItemStack(tool.getItem())), display.source() + " lists " + tool.getItem() + " which its requirement rejects");
          shown.add(tool.getItem());
        }
        for (var holder : net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(slimeknights.tconstruct.common.TinkerTags.Items.MODIFIABLE)) {
          if (requirement.test(new ItemStack(holder.value()))) {
            require(shown.contains(holder.value()), display.source() + " omits accepted tool " + holder.getRegisteredName());
          }
        }
        if (++checked >= 40) break;
      }
      require(checked > 0, "fixture needs modifier recipe pages with tools");
    }

    private void checkCost(String name, Item target, int cost, int index) {
      var recipe = recipe(name);
      var display = recipe.getRecipes(source.getLevel().registryAccess()).stream()
        .filter(d -> d.getToolWithoutModifier().stream().anyMatch(stack -> stack.is(target))).findFirst().orElseThrow();
      require(recipe.getCost() == cost && display.getRecipeId().equals(recipe.getId()), "loaded recipe identity/cost changed");
      require(display.getInputCount() == index + 1, "indexed input is beyond declared slot span");
      if (index == 1) require(display.getDisplayItems(0).isEmpty(), "intentional slot hole must stay empty");
      Item probe = index == 0 ? Items.OAK_PLANKS : Items.LEATHER;
      require(display.getDisplayItems(index).get(find(display.getDisplayItems(index), probe)).getCount() == cost, "displayed quantity differs from loaded cost");
      require(display.matchesItem(stack -> stack.is(probe), false), "indexed material input not searchable");
      checkPairs(recipe, display, index, ItemStack.EMPTY, false);
    }

    private void checkPairs(MaterialValueSwappingRecipe recipe, IDisplayToolTinkering display, int slot, ItemStack focus, boolean output) {
      var inputs = display.getDisplayItems(slot, focus, output);
      var outputs = display.getToolWithModifier(focus, output);
      var refunds = display.getDisplayRemainders(slot, focus, output);
      var containers = display.getDisplayContainers(slot, focus, output);
      require(!inputs.isEmpty() && inputs.size() == outputs.size() && inputs.size() == refunds.size() && inputs.size() == containers.size(), "correlated arrays have different lengths");
      require(Arrays.equals(display.linkToOutput(), new int[] {slot}), "material slot not linked to output");
      for (int i = 0; i < inputs.size(); i++) {
        MaterialRecipe material = MaterialRecipeCache.findRecipe(inputs.get(i));
        require(material != MaterialRecipe.EMPTY, "displayed input has no loaded material recipe");
        require(inputs.get(i).getCount() == material.getItemsUsed(recipe.getCost()), "displayed cost is wrong");
        require(ToolStack.from(outputs.get(i)).getMaterial(slot).sameVariant(material.getMaterial().getVariant()), "input/output alternatives became unpaired");
        require(ItemStack.matches(refunds.get(i), material.getLeftover(recipe.getCost())), "material refund became unpaired");
      }
    }

    private IDisplayToolTinkering only(MaterialValueSwappingRecipe recipe) {
      var displays = recipe.getRecipes(source.getLevel().registryAccess());
      require(displays.size() == 1, "expected one tool/index display, got " + displays.size());
      return displays.getFirst();
    }

    private MaterialValueSwappingRecipe recipe(String name) {
      var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("tconstruct", "tools/armor/travelers/" + name));
      return (MaterialValueSwappingRecipe)source.getServer().getRecipeManager().byKey(key).orElseThrow().value();
    }

    private void test(String name, Runnable test) {
      try {
        test.run(); passed++;
        source.sendSuccess(() -> Component.literal("AEBM_TOOL_TINKERING_PASS " + name), false);
      } catch (Exception | AssertionError failure) {
        failed++;
        source.sendFailure(Component.literal("AEBM_TOOL_TINKERING_FAIL " + name + " " + failure));
      }
    }
  }

  /** Holds real stacks only; all matching, validation, consumption and container logic is production code. */
  private static final class Inventory implements IMutableTinkerStationContainer {
    private final ItemStack tool;
    private final List<ItemStack> inputs = new ArrayList<>();
    private final List<ItemStack> given = new ArrayList<>();
    Inventory(ItemStack tool, ItemStack... inputs) {
      this.tool = tool;
      this.inputs.addAll(Arrays.asList(inputs));
      while (this.inputs.size() < 5) this.inputs.add(ItemStack.EMPTY);
    }
    @Override public ItemStack getTinkerableStack() { return tool; }
    @Override public ItemStack getInput(int index) { return inputs.get(index); }
    @Override public int getInputCount() { return inputs.size(); }
    @Override public MaterialRecipe getInputMaterial(int index) { return MaterialRecipeCache.findRecipe(getInput(index)); }
    @Override public void setInput(int index, ItemStack stack) { inputs.set(index, stack); }
    @Override public void giveItem(ItemStack stack) { given.add(stack.copy()); }
  }

  private static final class ValueRecipe extends MaterialValueSwappingRecipe {
    ValueRecipe(Item item, IJsonPredicate<MaterialVariantId> material, int cost, List<SizedIngredient> extras) {
      super(id("value"), Ingredient.of(item), 16, material, cost, new int[] {1}, extras);
    }
  }

  private static IJsonPredicate<MaterialVariantId> cuirass() { return new MaterialStatTypePredicate(StatlessMaterialStats.CUIRASS.getIdentifier()); }
  private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("aebmcontinuumtests", "tool_tinkering/" + path); }
  private static ToolStack tool(Item item, MaterialVariantId... materials) {
    return ToolStack.createTool(item, IModifiable.getToolDefinition(item), new MaterialNBT(Arrays.stream(materials).map(MaterialVariant::of).toList()));
  }
  private static int find(List<ItemStack> stacks, Item item) {
    for (int i = 0; i < stacks.size(); i++) if (stacks.get(i).is(item)) return i;
    throw new AssertionError("No displayed input for " + item);
  }
  private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
