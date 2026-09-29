package aebm.continuumtests;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.recipe.IMultiRecipe;
import slimeknights.mantle.recipe.helper.IngredientHelper;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.recipe.helper.TypeAwareRecipeSerializer;
import slimeknights.mantle.recipe.ingredient.EmptyIngredient;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.common.recipe.TinkerRecipeCacheRebuilder;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.casting.ICastingContainer;
import slimeknights.tconstruct.library.recipe.casting.IDisplayableCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.AbstractMaterialCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.CompositeCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingLookup;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialFluidRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.ToolCastingRecipe;
import slimeknights.tconstruct.library.recipe.ingredient.MaterialIngredient;
import slimeknights.tconstruct.library.recipe.ingredient.LegacyIngredientType;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.melting.IMeltingContainer;
import slimeknights.tconstruct.library.recipe.melting.MaterialMeltingRecipe;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

/** Checks actual loaded recipe displays without loading either viewer or changing world state. */
@EventBusSubscriber(modid = "aebmcontinuumtests")
public final class RecipeViewerServerFixture {
  private RecipeViewerServerFixture() {}

  @SubscribeEvent
  public static void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("aebmrecipeviewertest")
      .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
      .executes(context -> new Suite(context.getSource()).run()));
  }

  private static final class Suite {
    private final CommandSourceStack source;
    private final ServerLevel level;
    private final RecipeMap recipes;
    private int passed;
    private int failed;

    private Suite(CommandSourceStack source) {
      this.source = source;
      this.level = source.getLevel();
      this.recipes = source.getServer().getRecipeManager().recipeMap();
    }

    private int run() {
      test("material_ingredient_preserves_material_and_components", () -> {
        require(MaterialRegistry.isFullyLoaded(), "fixture needs loaded material data");
        var part = TinkerToolParts.pickHead.get();
        Component name = Component.literal("Material ingredient probe");
        var nested = DataComponentIngredient.of(DataComponents.CUSTOM_NAME, name, part);
        var ingredient = MaterialIngredient.of(nested, MaterialIds.iron).toVanilla();
        var displayed = MaterialRecipeCache.getDisplayItems(ingredient);
        require(!displayed.isEmpty(), "iron pick head must have a display");
        for (ItemStack stack : displayed) {
          require(IMaterialItem.getMaterialFromStack(stack).equals(MaterialIds.iron), "requested material was lost");
          require(name.equals(stack.get(DataComponents.CUSTOM_NAME)), "nested component was lost");
          require(ingredient.test(stack), "material display must satisfy the real ingredient");
        }
        require(!ingredient.test(new ItemStack(part)), "plain part must fail the material/component ingredient");
        var anyMaterial = MaterialIngredient.of(part).toVanilla();
        var alternatives = MaterialRecipeCache.getDisplayItems(anyMaterial);
        require(!alternatives.isEmpty(), "ANY material ingredient must retain valid material alternatives");
        for (ItemStack stack : alternatives) {
          var material = IMaterialItem.getMaterialFromStack(stack);
          require(!material.equals(IMaterial.UNKNOWN_ID), "unsupported material must not become an unknown-material display");
          require(part.canUseMaterial(material.getMaterialId()), "displayed material must be usable by the part");
          require(anyMaterial.test(stack), "ANY material display must satisfy its actual ingredient");
        }
      });
      test("material_melting_display_matches_runtime", this::materialMelting);
      test("material_casting_amount_and_cooling", () -> casting(MaterialCastingRecipe.class));
      test("composite_casting_amount_cooling_and_fluid_filter", () -> casting(CompositeCastingRecipe.class));
      test("tool_casting_amount_and_cooling", () -> casting(ToolCastingRecipe.class));
      test("tool_casting_retains_material_casts", this::toolMaterialCasts);
      test("missing_required_cast_differs_from_no_cast", () -> {
        var id = Identifier.fromNamespaceAndPath("aebmcontinuumtests", "missing_cast");
        var missing = LegacyIngredientType.ofTag(TagKey.create(Registries.ITEM, id));
        TypeAwareRecipeSerializer<ItemCastingRecipe> serializer = new TypeAwareRecipeSerializer<>() {
          @Override public RecipeType<?> getType() { return TinkerRecipeTypes.CASTING_TABLE.get(); }
          @Override public RecipeSerializer<ItemCastingRecipe> getSerializer() { return TinkerSmeltery.tableRecipeSerializer.get(); }
        };
        var required = new ItemCastingRecipe(serializer, id, "", missing, FluidIngredient.EMPTY,
          ItemOutput.fromItem(Items.IRON_INGOT), 20, false, false);
        var absent = new ItemCastingRecipe(serializer, id, "", EmptyIngredient.VANILLA, FluidIngredient.EMPTY,
          ItemOutput.fromItem(Items.IRON_INGOT), 20, false, false);
        require(required.getCastItems().isEmpty(), "fixture tag must have no alternatives");
        require(required.hasCast(), "missing required tag must not advertise a free cast slot");
        require(!IngredientHelper.test(required.getCast(), ItemStack.EMPTY), "runtime must reject absent required cast");
        require(!absent.hasCast(), "intentionally absent cast must remain absent");
        require(IngredientHelper.test(absent.getCast(), ItemStack.EMPTY), "runtime must accept intentionally absent cast");
      });
      // parity/rei: layout facts, focus sets, workstations and transfer conservation on real data and real menus
      test("rei_layout_casting_facts_match_recipes", this::castingFacts);
      test("rei_layout_melting_one_display_with_controller_amounts", this::meltingFacts);
      test("rei_material_focus_is_an_exact_partition", this::materialFocus);
      test("rei_tool_workstations_follow_traits", this::toolWorkstations);
      test("rei_transfer_crafting_station_real_menu_conserves", this::craftingTransfer);
      test("rei_transfer_tinker_station_real_menu_conserves", this::stationTransfer);
      test("rei_hidden_filled_containers_keep_empty_containers", () -> {
        var hidden = slimeknights.tconstruct.library.client.recipe.RecipeViewerHiding.filledContainers();
        require(!hidden.isEmpty(), "loaded fluids must produce filled container entries to hide");
        for (ItemStack stack : hidden) {
          require(!ItemStack.matches(stack, new ItemStack(stack.getItem())), "an empty container " + stack.getItem() + " would be hidden");
          require(stack.is(TinkerSmeltery.copperCan.get()) || stack.getItem() instanceof slimeknights.tconstruct.smeltery.item.TankItem,
            "only fluid containers are hidden, got " + stack.getItem());
        }
      });
      source.sendSuccess(() -> Component.literal("AEBM_VIEWER_SUMMARY passed=" + passed + " failed=" + failed), false);
      return failed == 0 ? 1 : 0;
    }

    /* parity/rei */

    @SuppressWarnings({"rawtypes", "unchecked"})
    private java.util.Collection<net.minecraft.world.item.crafting.RecipeHolder<?>> holders(RecipeType<?> type) {
      return (java.util.Collection) recipes.byType((RecipeType) type);
    }

    /** Cooling, cast role and fluid amounts in the casting layout facts equal the recipe's own values. */
    private void castingFacts() {
      int consumed = 0;
      int kept = 0;
      for (RecipeType<?> type : List.of(TinkerRecipeTypes.CASTING_TABLE.get(), TinkerRecipeTypes.CASTING_BASIN.get())) {
        boolean basin = type == TinkerRecipeTypes.CASTING_BASIN.get();
        for (var holder : holders(type)) {
          for (var display : slimeknights.mantle.recipe.helper.RecipeHelper.getJEIRecipes(level.registryAccess(), Stream.of(holder), IDisplayableCastingRecipe.class)) {
            for (var data : slimeknights.tconstruct.library.client.recipe.RecipeDisplayMapper.casting(holder.id().identifier(), display, basin)) {
              var layout = data.layout();
              require(slimeknights.tconstruct.library.client.recipe.RecipeLayout.getInt(layout, "cooling", -1) == display.getCoolingTime(),
                holder.id().identifier() + " cooling differs from the recipe");
              int role = slimeknights.tconstruct.library.client.recipe.RecipeLayout.getInt(layout, "cast", -1);
              int expected = !display.hasCast() ? 0 : display.isConsumed() ? 1 : 2;
              require(role == expected, holder.id().identifier() + " cast role " + role + " expected " + expected);
              require(data.category().getPath().equals(basin ? "casting_basin" : "casting_table"), "casting category follows the recipe type");
              if (role == 1) consumed++;
              if (role == 2) kept++;
            }
          }
        }
      }
      require(consumed > 0 && kept > 0, "fixture needs consumed and reusable casts, got " + consumed + " and " + kept);
    }

    /** Each melting recipe gives one melting display whose melter and smeltery amounts use the configured ore rates. */
    private void meltingFacts() {
      int ores = 0;
      for (var holder : holders(TinkerRecipeTypes.MELTING.get())) {
        if (!(holder.value() instanceof slimeknights.tconstruct.library.recipe.melting.MeltingRecipe recipe)) continue;
        var displays = slimeknights.tconstruct.library.client.recipe.RecipeDisplayMapper.melting(holder.id().identifier(), recipe);
        if (displays.isEmpty()) continue; // absent compatibility inputs
        var melting = displays.stream().filter(display -> display.category().getPath().equals("melting")).toList();
        require(melting.size() == 1, holder.id().identifier() + " must have exactly one melting display");
        var layout = melting.getFirst().layout();
        var ore = recipe.getOreType();
        int amount = recipe.getOutput().getAmount();
        boolean boosted = ore == slimeknights.tconstruct.library.recipe.melting.IMeltingContainer.OreRateType.METAL
          || ore == slimeknights.tconstruct.library.recipe.melting.IMeltingContainer.OreRateType.GEM;
        int melter = boosted ? slimeknights.tconstruct.common.config.Config.COMMON.melterOreRate.applyOreBoost(ore, amount) : amount;
        int smeltery = boosted ? slimeknights.tconstruct.common.config.Config.COMMON.smelteryOreRate.applyOreBoost(ore, amount) : amount;
        require(slimeknights.tconstruct.library.client.recipe.RecipeLayout.getInt(layout, "melter_amount", -1) == melter, holder.id().identifier() + " melter amount");
        require(slimeknights.tconstruct.library.client.recipe.RecipeLayout.getInt(layout, "smeltery_amount", -1) == smeltery, holder.id().identifier() + " smeltery amount");
        require(slimeknights.tconstruct.library.client.recipe.RecipeLayout.getInt(layout, "temperature", -1) == recipe.getTemperature(), "temperature fact");
        if (boosted) ores++;
      }
      require(ores > 0, "fixture needs ore melting recipes");
    }

    /** A material focus opens exactly the displays that produce or use that variant, nothing more and nothing less. */
    private void materialFocus() {
      List<slimeknights.tconstruct.library.client.recipe.RecipeDisplayData> all = new java.util.ArrayList<>();
      for (RecipeType<?> type : List.of(TinkerRecipeTypes.PART_BUILDER.get(), TinkerRecipeTypes.MATERIAL.get(), TinkerRecipeTypes.DATA.get())) {
        for (var holder : holders(type)) {
          all.addAll(slimeknights.tconstruct.library.client.recipe.RecipeDisplayMapper.map(holder, level.registryAccess(), level));
        }
      }
      // the variant with the most part builder pages exercises the partition best
      java.util.Map<slimeknights.tconstruct.library.materials.definition.MaterialVariantId, Integer> counts = new java.util.HashMap<>();
      for (var display : all) {
        if (!display.category().getPath().equals("part_builder")) continue;
        display.inputs().stream().flatMap(List::stream)
          .filter(slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.MaterialValue.class::isInstance)
          .map(value -> ((slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.MaterialValue) value).material())
          .forEach(material -> counts.merge(material, 1, Integer::sum));
      }
      require(!counts.isEmpty(), "part builder displays must carry their material for focus");
      var variant = counts.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).orElseThrow().getKey();
      var focus = new slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.MaterialValue(variant, 7);
      var recipesFor = slimeknights.tconstruct.library.client.recipe.RecipeFocus.recipesFor(all, focus);
      var usesOf = slimeknights.tconstruct.library.client.recipe.RecipeFocus.usesOf(all, focus);
      require(!usesOf.isEmpty(), "material " + variant + " must have uses");
      for (var display : all) {
        boolean produces = display.outputs().stream().flatMap(List::stream).anyMatch(value ->
          value instanceof slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.MaterialValue material && material.material().equals(variant));
        boolean uses = java.util.stream.Stream.concat(display.inputs().stream(), display.catalysts().stream()).flatMap(List::stream).anyMatch(value ->
          value instanceof slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.MaterialValue material && material.material().equals(variant));
        require(recipesFor.contains(display) == produces, "recipes-for set is not exact for " + display.source());
        require(usesOf.contains(display) == uses, "uses-of set is not exact for " + display.source());
        if (uses && display.category().getPath().equals("part_builder")) {
          // cross-check against the material recipes: the page's material item really is this variant
          ItemStack item = ((slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.ItemValue) display.inputs().getFirst().getFirst()).stack();
          var owner = MaterialRecipeCache.getAllRecipes().stream().filter(recipe -> IngredientHelper.test(recipe.getIngredient(), item)).findFirst();
          if (owner.isPresent()) {
            require(owner.get().getMaterial().getVariant().equals(variant), display.source() + " material item belongs to another material");
          }
        }
      }
    }

    /** Workstation stations equal the official rule applied to each tool's own traits. */
    private void toolWorkstations() {
      var stations = slimeknights.tconstruct.library.client.recipe.RecipeWorkstations.all();
      int tools = 0;
      for (var holder : net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(slimeknights.tconstruct.common.TinkerTags.Items.MODIFIABLE)) {
        if (!(holder.value() instanceof slimeknights.tconstruct.library.tools.item.IModifiableDisplay modifiable)) continue;
        tools++;
        var traits = slimeknights.tconstruct.library.tools.definition.module.build.ToolTraitHook.getTraits(modifiable.getToolDefinition(),
          slimeknights.tconstruct.library.tools.nbt.MaterialNBT.EMPTY).getModifiers();
        java.util.function.Predicate<net.minecraft.tags.TagKey<slimeknights.tconstruct.library.modifiers.Modifier>> has = tag ->
          traits.stream().anyMatch(entry -> slimeknights.tconstruct.library.modifiers.ModifierManager.isInTag(entry.getId(), tag));
        var actual = stations.getOrDefault(holder.value(), java.util.EnumSet.noneOf(slimeknights.tconstruct.library.client.recipe.RecipeWorkstations.Station.class));
        boolean melting = has.test(slimeknights.tconstruct.common.TinkerTags.Modifiers.MELTING);
        boolean melee = holder.is(slimeknights.tconstruct.common.TinkerTags.Items.MELEE);
        require(actual.contains(slimeknights.tconstruct.library.client.recipe.RecipeWorkstations.Station.MELTING) == melting, holder.getRegisteredName() + " melting station");
        require(actual.contains(slimeknights.tconstruct.library.client.recipe.RecipeWorkstations.Station.ENTITY_MELTING) == (melting && melee), holder.getRegisteredName() + " entity melting station");
        require(actual.contains(slimeknights.tconstruct.library.client.recipe.RecipeWorkstations.Station.SEVERING) == has.test(slimeknights.tconstruct.common.TinkerTags.Modifiers.SEVERING), holder.getRegisteredName() + " severing station");
        require(actual.contains(slimeknights.tconstruct.library.client.recipe.RecipeWorkstations.Station.CRAFTING) == has.test(slimeknights.tconstruct.common.TinkerTags.Modifiers.CRAFTING), holder.getRegisteredName() + " crafting station");
        require(actual.contains(slimeknights.tconstruct.library.client.recipe.RecipeWorkstations.Station.SMELTING) == has.test(slimeknights.tconstruct.common.TinkerTags.Modifiers.SMELTING), holder.getRegisteredName() + " smelting station");
      }
      require(tools > 0, "fixture needs loaded modifiable tools");
      require(!stations.containsKey(slimeknights.tconstruct.tools.TinkerTools.pickaxe.get())
        || !stations.get(slimeknights.tconstruct.tools.TinkerTools.pickaxe.get()).contains(slimeknights.tconstruct.library.client.recipe.RecipeWorkstations.Station.MELTING),
        "a plain pickaxe is no melting workstation");
    }

    /** A wooden pickaxe transfer through real crafting station clicks keeps every item. */
    private void craftingTransfer() {
      var player = new net.neoforged.neoforge.common.util.FakePlayer(level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ReiTransferFixture"));
      player.setPos(0, level.getMinY() + 96, 0);
      var station = new slimeknights.tconstruct.tables.block.entity.table.CraftingStationBlockEntity(
        new net.minecraft.core.BlockPos(0, level.getMinY() + 96, 0), slimeknights.tconstruct.tables.TinkerTables.craftingStation.get().defaultBlockState());
      station.setLevel(level);
      station.setItem(8, new ItemStack(Items.COBBLESTONE, 2));
      var menu = new slimeknights.tconstruct.tables.menu.CraftingStationContainerMenu(1, player.getInventory(), station) {
        @Override
        protected void addChestSideInventory() {
          // no adjacent blocks: storage behavior is covered by TransferConservationTest on the real slot class
        }
      };
      player.containerMenu = menu;
      player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 5));
      player.getInventory().setItem(1, new ItemStack(Items.STICK, 3));
      List<List<ItemStack>> grid = new java.util.ArrayList<>();
      for (int i = 0; i < 9; i++) grid.add(List.of());
      for (int i : new int[] {0, 1, 2}) grid.set(i, List.of(new ItemStack(Items.OAK_PLANKS)));
      for (int i : new int[] {4, 7}) grid.set(i, List.of(new ItemStack(Items.STICK)));
      var request = slimeknights.tconstruct.library.client.recipe.transfer.MenuTransferSlots.craftingStation(menu, player, grid, false);
      var before = menuCensus(menu);
      var plan = slimeknights.tconstruct.library.client.recipe.transfer.MenuTransferSlots.plan(menu, player, request);
      require(plan instanceof slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner.Ready, "planned transfer, got " + plan);
      var outcome = slimeknights.tconstruct.library.client.recipe.transfer.TransferExecutor.execute(menu, player,
        ((slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner.Ready) plan).clicks(), request.dump(),
        (slot, button) -> menu.clicked(slot, button, net.minecraft.world.inventory.ContainerInput.PICKUP, player));
      require(outcome.complete(), "server menu followed every planned click");
      for (int i : new int[] {0, 1, 2}) require(station.getItem(i).is(Items.OAK_PLANKS) && station.getItem(i).getCount() == 1, "plank in grid " + i);
      for (int i : new int[] {4, 7}) require(station.getItem(i).is(Items.STICK) && station.getItem(i).getCount() == 1, "stick in grid " + i);
      require(station.getItem(8).isEmpty(), "cobblestone cleared from the grid");
      require(menu.getCarried().isEmpty(), "cursor empty");
      require(slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner.sameCensus(before, menuCensus(menu)), "item totals conserved");
      require(station.getResultForPlayer(player).is(Items.WOODEN_PICKAXE), "the transferred grid crafts a wooden pickaxe");
    }

    /** A real modifier recipe's inputs move into the real tinker station input slots without loss. */
    private void stationTransfer() {
      var player = new net.neoforged.neoforge.common.util.FakePlayer(level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ReiStationFixture"));
      player.setPos(0, level.getMinY() + 96, 0);
      var tile = new slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity(
        new net.minecraft.core.BlockPos(0, level.getMinY() + 96, 0), slimeknights.tconstruct.tables.TinkerTables.tinkerStation.get().defaultBlockState());
      tile.setLevel(level);
      var menu = new slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu(1, player.getInventory(), tile);
      player.containerMenu = menu;
      int inputs = menu.getInputSlots().size();
      // first real modifier page with item inputs that fit the default layout
      List<List<ItemStack>> station = null;
      for (var holder : holders(TinkerRecipeTypes.TINKER_STATION.get())) {
        for (var data : slimeknights.tconstruct.library.client.recipe.RecipeDisplayMapper.map(holder, level.registryAccess(), level)) {
          if (!data.category().getPath().equals("modifiers")) continue;
          int[] slots = slimeknights.tconstruct.library.client.recipe.RecipeLayout.getIntArray(data.layout(), "station_slots");
          if (slots.length == 0 || slots.length > inputs) continue;
          List<List<ItemStack>> candidate = new java.util.ArrayList<>();
          for (int i = 0; i < slots.length; i++) {
            while (candidate.size() <= slots[i]) candidate.add(List.of());
            ItemStack first = ((slimeknights.tconstruct.library.client.recipe.RecipeDisplayData.ItemValue) data.inputs().get(i).getFirst()).stack();
            candidate.set(slots[i], List.of(first.copy()));
          }
          if (slimeknights.tconstruct.library.client.recipe.transfer.MenuTransferSlots.tinkerStationProblem(menu, candidate) == null) {
            station = candidate;
            break;
          }
        }
        if (station != null) break;
      }
      require(station != null, "fixture needs a modifier recipe that fits the tinker station");
      int slot = 0;
      for (List<ItemStack> alternatives : station) {
        if (!alternatives.isEmpty()) player.getInventory().setItem(slot++, alternatives.getFirst().copyWithCount(alternatives.getFirst().getCount() + 1));
      }
      var request = slimeknights.tconstruct.library.client.recipe.transfer.MenuTransferSlots.tinkerStation(menu, player, station, false);
      require(request != null, "station request");
      var before = menuCensus(menu);
      var plan = slimeknights.tconstruct.library.client.recipe.transfer.MenuTransferSlots.plan(menu, player, request);
      require(plan instanceof slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner.Ready, "planned station transfer, got " + plan);
      var outcome = slimeknights.tconstruct.library.client.recipe.transfer.TransferExecutor.execute(menu, player,
        ((slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner.Ready) plan).clicks(), request.dump(),
        (index, button) -> menu.clicked(index, button, net.minecraft.world.inventory.ContainerInput.PICKUP, player));
      require(outcome.complete(), "server station followed every planned click");
      for (int i = 0; i < station.size(); i++) {
        if (station.get(i).isEmpty()) continue;
        ItemStack expected = station.get(i).getFirst();
        ItemStack actual = menu.getInputSlots().get(i).getItem();
        require(ItemStack.isSameItemSameComponents(actual, expected) && actual.getCount() == expected.getCount(), "station input " + i + " holds " + actual);
      }
      require(menu.getCarried().isEmpty(), "cursor empty");
      require(slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner.sameCensus(before, menuCensus(menu)), "item totals conserved");
    }

    private List<ItemStack> menuCensus(net.minecraft.world.inventory.AbstractContainerMenu menu) {
      List<ItemStack> stacks = new java.util.ArrayList<>();
      // the result slot is a preview, not an item the player owns
      for (var slot : menu.slots) {
        if (!(slot instanceof net.minecraft.world.inventory.ResultSlot) && !slot.getClass().getSimpleName().contains("Result")) stacks.add(slot.getItem().copy());
      }
      stacks.add(menu.getCarried().copy());
      return slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner.census(stacks);
    }

    private void materialMelting() {
      int checked = 0;
      for (var original : TinkerRecipeCacheRebuilder.getRecipes(recipes, TinkerRecipeTypes.MELTING.get(), MaterialMeltingRecipe.class)) {
        for (var display : original.getRecipes(level.registryAccess())) {
          var items = MaterialRecipeCache.getDisplayItems(display.getInput());
          require(!items.isEmpty(), original.getId() + " material melting input disappeared");
          for (ItemStack stack : items) {
            require(display.getInput().test(stack), "displayed material melting input fails its ingredient");
            IMeltingContainer input = new IMeltingContainer() {
              @Override public ItemStack getStack() { return stack; }
              @Override public IOreRate getOreRate() { return (type, amount) -> amount; }
            };
            require(original.matches(input, level), "displayed part fails original material melting recipe");
            FluidStack actual = original.getOutput(input);
            require(FluidStack.isSameFluidSameComponents(actual, display.getOutput())
              && actual.getAmount() == display.getOutput().getAmount(), "displayed melting output differs from runtime");
            require(original.getTime(input) == display.getTime(), "displayed melting time differs from runtime");
            checked++;
          }
        }
        if (checked > 0) break;
      }
      require(checked > 0, "fixture found no visible material melting recipe");
    }

    private void toolMaterialCasts() {
      int checked = 0;
      for (var original : TinkerRecipeCacheRebuilder.getRecipes(recipes, TinkerRecipeTypes.CASTING_TABLE.get(), ToolCastingRecipe.class)) {
        for (var display : original.getRecipes(level.registryAccess())) {
          for (ItemStack cast : display.getCastItems()) {
            if (cast.getItem() instanceof IMaterialItem) {
              require(!IMaterialItem.getMaterialFromStack(cast).equals(IMaterial.UNKNOWN_ID), "tool cast lost its material");
              require(IngredientHelper.test(original.getCast(), cast), "material-bearing tool cast fails real cast ingredient");
              checked++;
            }
          }
        }
      }
      require(checked > 0, "fixture needs at least one material-bearing tool cast");
    }

    @SuppressWarnings("unchecked")
    private void casting(Class<? extends AbstractMaterialCastingRecipe> family) {
      var originals = Stream.concat(
        TinkerRecipeCacheRebuilder.getRecipes(recipes, TinkerRecipeTypes.CASTING_TABLE.get(), family).stream(),
        TinkerRecipeCacheRebuilder.getRecipes(recipes, TinkerRecipeTypes.CASTING_BASIN.get(), family).stream())
        .filter(recipe -> recipe.getClass() == family).toList();
      int checked = 0;
      boolean multipleUnits = false;
      for (var original : originals) {
        var displays = ((IMultiRecipe<IDisplayableCastingRecipe>)original).getRecipes(level.registryAccess());
        for (var display : displays) {
          int maximumCooling = 0;
          List<ItemStack> casts = display.hasCast() ? display.getCastItems() : List.of(ItemStack.EMPTY);
          for (FluidStack fluid : display.getFluids()) {
            boolean accepted = false;
            for (ItemStack cast : casts) {
              var input = new CastingInput(cast, fluid);
              if (!original.matches(input, level)) continue;
              accepted = true;
              require(original.getFluidAmount(input) == fluid.getAmount(), original.getId() + " fluid amount differs from runtime");
              maximumCooling = Math.max(maximumCooling, original.getCoolingTime(input));
              MaterialFluidRecipe unit = family == CompositeCastingRecipe.class
                ? MaterialCastingLookup.getCompositeFluid(fluid.getFluid(), IMaterialItem.getMaterialFromStack(cast))
                : MaterialCastingLookup.getCastingFluid(fluid.getFluid());
              if (unit != MaterialFluidRecipe.EMPTY && fluid.getAmount() > unit.getFluidAmount(fluid.getFluid())) multipleUnits = true;
              checked++;
            }
            // Tool part-swap display markers intentionally use a render-only material.
            if (family != ToolCastingRecipe.class) require(accepted, original.getId() + " displays a rejected fluid/cast combination");
          }
          if (maximumCooling > 0) {
            require(display.getCoolingTime() == maximumCooling,
              original.getId() + " display cooling=" + display.getCoolingTime() + " runtime maximum=" + maximumCooling);
          }
        }
        if (multipleUnits) break;
      }
      require(checked > 0, "fixture found no accepted " + family.getSimpleName() + " displays");
      require(multipleUnits, "fixture needs an item cost above one to detect double-scaled cooling for " + family.getSimpleName());
    }

    private void test(String name, Runnable test) {
      try {
        test.run(); passed++;
        source.sendSuccess(() -> Component.literal("AEBM_VIEWER_PASS " + name), false);
      } catch (Exception | AssertionError failure) {
        failed++;
        source.sendFailure(Component.literal("AEBM_VIEWER_FAIL " + name + " " + failure));
      }
    }
  }

  private record CastingInput(ItemStack stack, FluidStack fluid) implements ICastingContainer {
    @Override public ItemStack getStack() { return stack; }
    @Override public Fluid getFluid() { return fluid.getFluid(); }
    @Override public FluidStack getFluidStack() { return fluid; }
  }

  private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
