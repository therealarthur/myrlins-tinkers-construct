package aebm.continuumtests;

import com.mojang.authlib.GameProfile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import slimeknights.mantle.recipe.ingredient.SizedIngredient;
import slimeknights.tconstruct.library.json.predicate.material.MaterialStatTypePredicate;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.tinkerstation.IMutableTinkerStationContainer;
import slimeknights.tconstruct.library.recipe.tinkerstation.building.MaterialValueSwappingRecipe;
import slimeknights.tconstruct.library.tools.context.EquipmentContext;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.block.entity.inventory.TinkerStationContainerWrapper;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.library.tools.item.armor.ModifiableArmorItem;
import slimeknights.tconstruct.tools.stats.SlimeStats;
import slimeknights.tconstruct.tools.stats.StatlessMaterialStats;

/** Tests loaded data and actual tools/station transactions; no blocks or entities are installed in the world. */
@EventBusSubscriber(modid = "aebmcontinuumtests")
public final class ArmorParityServerFixture {
  private static final Identifier PROBE = Identifier.fromNamespaceAndPath("aebmcontinuumtests", "armor_probe");
  private ArmorParityServerFixture() {}

  @SubscribeEvent
  public static void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("aebmarmortest")
      .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
      .executes(context -> new Suite(context.getSource()).run()));
  }

  private static final class Suite {
    private final CommandSourceStack source;
    private final ServerLevel level;
    private final FakePlayer player;
    private int passed;
    private int failed;

    private Suite(CommandSourceStack source) {
      this.source = source;
      this.level = source.getLevel();
      this.player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ArmorFixture"));
      player.setPos(source.getPosition().x, level.getMaxY() + 32, source.getPosition().z);
    }

    private int run() {
      test("loaded_materials_and_traits", () -> {
        for (MaterialId material : List.of(MaterialIds.skyslimeskin, MaterialIds.enderslimeskin)) {
          require(MaterialRegistry.getInstance().getMaterialStats(material, StatlessMaterialStats.CUIRASS.getIdentifier()).isPresent(), "skin cuirass stats missing");
          require(MaterialRegistry.getInstance().getMaterialStats(material, StatlessMaterialStats.MAILLE.getIdentifier()).isPresent(), "skin maille stats missing");
        }
        SlimeStats stats = MaterialRegistry.getInstance().<SlimeStats>getMaterialStats(MaterialIds.venom, SlimeStats.ID).orElseThrow();
        require(stats.durability() == 225 && stats.overslime() == 0, "venom stats must match released material");
        require(tool(TinkerTools.travelersGear.get(ArmorType.HELMET), MaterialIds.copper, MaterialIds.skyslimeskin).getModifiers().getLevel(ModifierIds.airborn) == 1, "sky skin must grant airborn");
        require(tool(TinkerTools.travelersGear.get(ArmorType.HELMET), MaterialIds.copper, MaterialIds.enderslimeskin).getModifiers().getLevel(ModifierIds.enderclearance) == 1, "ender skin must grant enderclearance");
        require(tool(TinkerTools.slimesuit.get(ArmorType.HELMET), MaterialIds.bone, MaterialIds.venom).getModifiers().getLevel(ModifierIds.magicProtection) == 1, "venom slime trait missing");
      });
      test("cuirass_stats_and_venom_values", () -> {
        for (MaterialId material : List.of(MaterialIds.wool, MaterialIds.vine, MaterialIds.weepingVine, MaterialIds.twistingVine)) {
          require(MaterialRegistry.getInstance().getMaterialStats(material, StatlessMaterialStats.CUIRASS.getIdentifier()).isPresent(), "restored cuirass material missing: " + material);
        }
        var eye = MaterialRecipeCache.findRecipe(new ItemStack(Items.SPIDER_EYE));
        var fermented = MaterialRecipeCache.findRecipe(new ItemStack(Items.FERMENTED_SPIDER_EYE));
        require(eye.getMaterial().sameVariant(MaterialIds.venom) && eye.getItemsUsed(2) == 2, "spider eye must supply one venom value");
        require(fermented.getMaterial().sameVariant(MaterialIds.venom) && fermented.getItemsUsed(2) == 1, "fermented eye must supply two venom values");
      });
      test("trim_and_rebalance_keep_material_positions", () -> {
        checkHooks(ArmorType.HELMET, MaterialIds.bone, ModifierIds.skeletonDisguise);
        checkHooks(ArmorType.CHESTPLATE, MaterialIds.blazingBone, ModifierIds.conductive);
        checkHooks(ArmorType.LEGGINGS, MaterialIds.shulker, ModifierIds.shulkerBox);
        checkHooks(ArmorType.BOOTS, MaterialIds.leather, ModifierIds.rugged);
        ToolStack chest = tool(TinkerTools.slimesuit.get(ArmorType.CHESTPLATE), MaterialIds.blazingBone, MaterialIds.venom);
        require(chest.getModifiers().getLevel(ModifierIds.conductive) == 2, "chest must grant the ribcage trait twice");
      });
      test("short_legacy_skulls_remap_once", () -> {
        List<MaterialId> old = List.of(MaterialIds.glass, MaterialIds.venombone, MaterialIds.blazingBone);
        List<MaterialId> expected = List.of(MaterialIds.gunpowder, MaterialIds.ice, MaterialIds.blaze);
        for (int i = 0; i < old.size(); i++) {
          ToolStack skull = tool(TinkerTools.slimesuit.get(ArmorType.HELMET), old.get(i));
          skull.ensureHasData();
          require(skull.getMaterials().size() == 2 && skull.getMaterial(0).sameVariant(expected.get(i)) && skull.getMaterial(1).sameVariant(MaterialIds.blood), "short skull must remap/fill without index reversal");
          var snapshot = skull.getMaterials().serializeToNBT();
          skull.ensureHasData();
          require(snapshot.equals(skull.getMaterials().serializeToNBT()), "remap must be idempotent");
        }
      });
      test("complete_legacy_skulls_roundtrip", () -> {
        for (MaterialId skull : List.of(MaterialIds.venombone, MaterialIds.blazingBone)) {
          preserve(tool(TinkerTools.slimesuit.get(ArmorType.HELMET), skull, MaterialIds.earthslime));
        }
      });
      test("old_and_new_skin_roundtrip", () -> {
        for (MaterialVariantId skin : List.of(MaterialIds.skySlimeskin, MaterialIds.enderSlimeskin, MaterialIds.skyslimeskin, MaterialIds.enderslimeskin)) {
          preserve(tool(TinkerTools.travelersGear.get(ArmorType.HELMET), MaterialIds.copper, skin));
        }
      });
      test("six_loaded_travelers_targets", () -> {
        swap("goggles_cuirass", TinkerTools.travelersGear.get(ArmorType.HELMET), 3, 1);
        swap("vest_cuirass", TinkerTools.travelersGear.get(ArmorType.CHESTPLATE), 6, 1);
        swap("pants_cuirass", TinkerTools.travelersGear.get(ArmorType.LEGGINGS), 4, 1);
        swap("boots_cuirass", TinkerTools.travelersGear.get(ArmorType.BOOTS), 2, 1);
        swap("boots_cuirass", TinkerTools.travelersShield.get(), 2, 1);
        swap("shield_wood", TinkerTools.travelersShield.get(), 2, 0);
      });
      test("shield_log_leftover", () -> {
        var logs = MaterialRecipeCache.findRecipe(new ItemStack(Items.OAK_LOG));
        require(logs.getLeftover(1).getCount() == 3 && logs.getLeftover(3).getCount() == 1,
          "material refunds must return unspent value, not spent value");
        require(logs.getLeftover(4).isEmpty() && logs.getLeftover(5).getCount() == 3,
          "refund calculation must wrap across complete material items");
        var recipe = recipe("shield_wood");
        var inv = station(tool(TinkerTools.travelersShield.get(), MaterialIds.bone, MaterialIds.leather), new ItemStack(Items.OAK_LOG));
        require(recipe.matches(inv, level), "log must supply the two-unit core cost");
        var result = recipe.getValidatedResult(inv, level.registryAccess());
        require(result.isSuccess(), "log swap must produce an output");
        int before = inventoryCount(Items.OAK_PLANKS);
        recipe.updateInputs(result.getResult(), inv, true);
        require(inv.getInput(0).isEmpty(), "one log must be consumed");
        require(inventoryCount(Items.OAK_PLANKS) - before == 2, "two unspent material units must be returned as planks");
      });
      test("extra_material_requirement_conservation", () -> {
        // Both recipes execute the production base loop and production shrink implementation.
        // Only the negative control restores the upstream false return after successful consumption.
        for (boolean upstreamFalse : new boolean[] {false, true}) {
          var recipe = new LeatherExtraRecipe(upstreamFalse);
          var inv = station(tool(TinkerTools.travelersGear.get(ArmorType.HELMET), MaterialIds.copper, MaterialIds.wool), new ItemStack(Items.LEATHER, 5), new ItemStack(Items.LEATHER, 4));
          require(recipe.matches(inv, level), "extra-requirement recipe must really match");
          var result = recipe.getValidatedResult(inv, level.registryAccess());
          require(result.isSuccess(), "extra-requirement result must validate");
          recipe.updateInputs(result.getResult(), inv, true);
          int remaining = inv.getInput(0).getCount() + inv.getInput(1).getCount();
          require(remaining == (upstreamFalse ? 2 : 5), "selected material costs 3 plus one extra; false-return control must expose seven consumed");
        }
      });
      test("rugged_and_airborn_actual_hooks", () -> {
        ToolStack boots = tool(TinkerTools.slimesuit.get(ArmorType.BOOTS), MaterialIds.leather, MaterialIds.venom);
        require(boots.getVolatileData().getBoolean(ModifiableArmorItem.SNOW_BOOTS), "rugged retains powder-snow flag");
        var context = new EquipmentContext(player);
        for (int level = 1; level <= 2; level++) {
          var rugged = new ModifierEntry(ModifierIds.rugged, level);
          var hook = rugged.getHook(ModifierHooks.DAMAGE_BLOCK);
          require(hook.isDamageBlocked(boots, rugged, context, EquipmentSlot.FEET, this.level.damageSources().cactus(), 1), "rugged must block terrain");
          require(hook.isDamageBlocked(boots, rugged, context, EquipmentSlot.FEET, this.level.damageSources().thorns(player), 1) == (level == 2), "rugged attacks require level two");
        }
        ToolStack helmet = tool(TinkerTools.travelersGear.get(ArmorType.HELMET), MaterialIds.copper, MaterialIds.skyslimeskin);
        var airborn = new ModifierEntry(ModifierIds.airborn, 1);
        var hook = airborn.getHook(ModifierHooks.PROTECTION);
        player.setOnGround(true);
        require(hook.getProtectionModifier(helmet, airborn, context, EquipmentSlot.HEAD, this.level.damageSources().playerAttack(player), 0) == 0, "grounded attacker must not activate airborn");
        player.setOnGround(false);
        require(hook.getProtectionModifier(helmet, airborn, context, EquipmentSlot.HEAD, this.level.damageSources().playerAttack(player), 0) == 2.5f, "airborne attacker must grant original protection");
      });
      source.sendSuccess(() -> Component.literal("AEBM_ARMOR_SUMMARY passed=" + passed + " failed=" + failed), false);
      return failed == 0 ? 1 : 0;
    }

    private void checkHooks(ArmorType armor, MaterialId part, ModifierId trim) {
      ToolStack tool = tool(TinkerTools.slimesuit.get(armor), part, MaterialIds.venom);
      ModifierNBT.Builder builder = ModifierNBT.builder();
      tool.getDefinition().getHook(ToolHooks.TRIM_TRAIT).addTraits(tool.getDefinition(), tool.getMaterials(), builder);
      require(builder.build().getLevel(trim) == 1, "trim must use outer part: " + armor);
      builder = ModifierNBT.builder();
      tool.getDefinition().getHook(ToolHooks.REBALANCED_TRAIT).addTraits(tool.getDefinition(), tool.getMaterials(), builder);
      require(builder.build().getLevel(ModifierIds.magicProtection) == 1, "rebalance must use slime: " + armor);
      require(tool.getMaterial(0).sameVariant(part) && tool.getMaterial(1).sameVariant(MaterialIds.venom), "stored slot order changed");
    }

    private void preserve(ToolStack original) throws Exception {
      original.addModifier(ModifierIds.reinforced, 1);
      original.setDamage(17);
      original.getPersistentData().putInt(PROBE, 173);
      ItemStack stack = original.createStack();
      ProblemReporter.Collector problems = new ProblemReporter.Collector();
      TagValueOutput output = TagValueOutput.createWithContext(problems, level.registryAccess());
      output.store("tool", ItemStack.CODEC, stack);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream data = new DataOutputStream(bytes)) { NbtIo.write(output.buildResult(), data); }
      CompoundTag saved;
      try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { saved = NbtIo.read(data, NbtAccounter.unlimitedHeap()); }
      ItemStack loaded = TagValueInput.create(problems, level.registryAccess(), saved).read("tool", ItemStack.CODEC).orElseThrow();
      require(problems.isEmpty() && ItemStack.matches(stack, loaded), "item codec must preserve complete saved components");
      ToolStack restored = ToolStack.from(loaded);
      restored.ensureHasData();
      restored.rebuildStats();
      require(original.getMaterials().serializeToNBT().equals(restored.getMaterials().serializeToNBT()), "complete old material array must be preserved");
      require(restored.getUpgrades().getLevel(ModifierIds.reinforced) == 1 && restored.getDamage() == 17 && restored.getPersistentData().getInt(PROBE) == 173, "save/rebuild must preserve upgrade, damage and persistent data");
      require(restored.getStats().getInt(ToolStats.DURABILITY) > 17, "preserved tool must still have valid durability");
    }

    private MaterialValueSwappingRecipe recipe(String name) {
      var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("tconstruct", "tools/armor/travelers/" + name));
      var value = level.getServer().getRecipeManager().byKey(key).orElseThrow().value();
      require(value instanceof MaterialValueSwappingRecipe, "loaded swap serializer missing: " + name);
      return (MaterialValueSwappingRecipe)value;
    }

    private TinkerStationContainerWrapper station(ToolStack tool, ItemStack... inputs) {
      var station = new TinkerStationBlockEntity(BlockPos.containing(player.position()), TinkerTables.tinkerStation.get().defaultBlockState(), 5);
      station.setLevel(level);
      station.setItem(TinkerStationBlockEntity.TINKER_SLOT, tool.createStack());
      for (int i = 0; i < inputs.length; i++) station.setItem(TinkerStationBlockEntity.INPUT_SLOT + i, inputs[i]);
      var wrapper = new TinkerStationContainerWrapper(station);
      wrapper.setPlayer(player);
      return wrapper;
    }

    private void swap(String name, Item item, int cost, int index) {
      var recipe = recipe(name);
      ToolStack original = tool(item, item == TinkerTools.travelersShield.get() ? MaterialIds.bone : MaterialIds.copper, MaterialIds.wool);
      Item ingredient = index == 0 ? Items.OAK_PLANKS : Items.LEATHER;
      var insufficient = station(original, new ItemStack(ingredient, cost - 1));
      require(!recipe.matches(insufficient, level), "too-small stack must not match: " + name);
      var inv = station(original, new ItemStack(ingredient, cost + 2));
      require(recipe.matches(inv, level), "loaded recipe must match: " + name);
      var result = recipe.getValidatedResult(inv, level.registryAccess());
      require(result.isSuccess(), "loaded recipe must validate: " + name);
      var material = MaterialRecipeCache.findRecipe(new ItemStack(ingredient)).getMaterial().getVariant();
      require(result.getResult().getTool().getMaterial(index).sameVariant(material), "wrong material index changed: " + name);
      require(result.getResult().getTool().getMaterial(1 - index).sameVariant(original.getMaterial(1 - index).getVariant()), "untouched material changed");
      recipe.updateInputs(result.getResult(), inv, true);
      require(inv.getInput(0).getCount() == 2, "must consume exact original cost: " + name);
      require(inv.getTinkerable().getMaterials().serializeToNBT().equals(original.getMaterials().serializeToNBT()), "validation must leave original tool intact");
    }

    private int inventoryCount(Item item) {
      int count = 0;
      for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
        var stack = player.getInventory().getItem(i);
        if (stack.is(item)) count += stack.getCount();
      }
      return count;
    }

    private void test(String name, CheckedRunnable test) {
      try {
        test.run(); passed++;
        source.sendSuccess(() -> Component.literal("AEBM_ARMOR_PASS " + name), false);
      } catch (Exception | AssertionError failure) {
        failed++;
        source.sendFailure(Component.literal("AEBM_ARMOR_FAIL " + name + " " + failure));
      }
    }
  }

  /** Actual recipe with an extra requirement that is also a legal replacement material. */
  private static final class LeatherExtraRecipe extends MaterialValueSwappingRecipe {
    private final boolean upstreamFalse;
    private LeatherExtraRecipe(boolean upstreamFalse) {
      super(Identifier.fromNamespaceAndPath("aebmcontinuumtests", "leather_extra"), Ingredient.of(TinkerTools.travelersGear.get(ArmorType.HELMET)), 16,
        new MaterialStatTypePredicate(StatlessMaterialStats.CUIRASS.getIdentifier()), 3, new int[] {1}, List.of(SizedIngredient.fromItems(Items.LEATHER)));
      this.upstreamFalse = upstreamFalse;
    }
    @Override
    protected boolean shrinkPart(IMutableTinkerStationContainer inv, int index, ItemStack stack) {
      boolean consumed = super.shrinkPart(inv, index, stack);
      return !upstreamFalse && consumed;
    }
  }

  private static ToolStack tool(Item item, MaterialVariantId... materials) {
    var variants = java.util.Arrays.stream(materials).map(MaterialVariant::of).toList();
    return ToolStack.createTool(item, IModifiable.getToolDefinition(item), new MaterialNBT(variants));
  }
  private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
  @FunctionalInterface private interface CheckedRunnable { void run() throws Exception; }
}
