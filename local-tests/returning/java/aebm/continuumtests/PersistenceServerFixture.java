package aebm.continuumtests;

import com.mojang.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.fluid.FluidActions;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.SmelteryBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.module.MeltingModuleInventory;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.tools.entity.ThrownTool;

/** In-memory binary persistence checks using loaded recipes and real tool/casting implementations. */
public final class PersistenceServerFixture {
  private static final Identifier PROBE = Identifier.fromNamespaceAndPath("aebmcontinuumtests", "persistence_probe");

  private PersistenceServerFixture() {}

  public static void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("aebmpersistencetest")
      .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
      .executes(context -> new Suite(context.getSource()).run()));
  }

  private static final class Suite {
    private final CommandSourceStack source;
    private final ServerLevel level;
    private final BlockPos position;
    private int passed;
    private int failed;

    private Suite(CommandSourceStack source) {
      this.source = source;
      this.level = source.getLevel();
      // Instances are never installed into chunks; any normal casting state-update attempt is out of bounds.
      this.position = unloadedColumn(level, BlockPos.containing(source.getPosition().x, level.getMaxY() + 32, source.getPosition().z));
    }

    /**
     * Casting notifications (sendBlockUpdated) reach ChunkHolder.blockChanged, which indexes the chunk
     * section array without a height check when the column's chunk is ticking. Above build height that
     * throws ArrayIndexOutOfBoundsException, a vanilla environment artifact that real casting tables inside
     * build height never hit. The first pack server's console runs used an unloaded spawn column; a player-issued run or
     * a forceloaded area does not. Step east in whole chunks until the column is not loaded, so the
     * notifications stay no-ops as intended. This only inspects chunk state; it never loads a chunk.
     */
    private static BlockPos unloadedColumn(ServerLevel level, BlockPos start) {
      // Negative control for the harness: keep the command's own column even when it is loaded.
      if (Boolean.getBoolean("aebm.fixture.persistence.sourceColumn")) {
        return start;
      }
      BlockPos candidate = start;
      for (int step = 0; step < 256 && level.hasChunkAt(candidate); step++) {
        candidate = candidate.east(16);
      }
      return candidate;
    }

    private int run() {
      test("tool_itemstack_binary_roundtrip", () -> {
        ToolStack original = tool(false);
        ItemStack stack = original.createStack();
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Returning persistence fixture"));
        ItemStack restored = roundtripItem(stack);
        require(ItemStack.matches(stack, restored), "item count/components must roundtrip exactly");
        assertTool(original, ToolStack.from(restored));
      });
      test("tool_rebuild_after_binary_roundtrip", () -> {
        ToolStack original = tool(false);
        ToolStack restored = ToolStack.from(roundtripItem(original.createStack()));
        restored.rebuildStats();
        assertTool(original, restored);
      });
      test("broken_tool_binary_roundtrip", () -> {
        ToolStack original = tool(true);
        require(original.isBroken(), "fixture must start broken");
        ToolStack restored = ToolStack.from(roundtripItem(original.createStack()));
        assertTool(original, restored);
        restored.rebuildStats();
        assertTool(original, restored);
      });
      test("partial_casting_table_fill_reload", () -> {
        CastingBlockEntity table = casting(false, new ItemStack(TinkerSmeltery.ingotCast.get()));
        require(table.getTank().fill(iron(30), FluidActions.EXECUTE) == 30, "partial fill must accept 30 mB");
        require(table.getTank().getCapacity() == 90, "loaded iron ingot recipe must require 90 mB");
        require(table.getCoolingTime() < 0 && table.getTimer() == 0, "partial fill must not cool");
        CompoundTag saved = saveCasting(table);
        // Cover loadAdditional when a level is already available, as well as the deferred path below.
        CastingBlockEntity restored = loadCasting(false, saved, true);
        assertCastingSnapshot(table, restored, saved);
        FluidStack incompatible = new FluidStack(TinkerFluids.moltenCopper.get(), 1);
        require(restored.getTank().fill(incompatible, FluidActions.EXECUTE) == 0, "restored filter must reject another metal");
        require(restored.getTank().getFluid().getAmount() == 30, "rejected fill must preserve fluid");
        require(restored.getTank().fill(iron(60), FluidActions.EXECUTE) == 60, "remaining 60 mB must complete fill");
        finishAndCheck(restored, false, TinkerSmeltery.ingotCast.get(), Items.IRON_INGOT);
      });
      test("reusable_cast_mid_cooling_reload", () ->
        midCooling(false, new ItemStack(TinkerSmeltery.ingotCast.get()), 90, TinkerSmeltery.ingotCast.get(), Items.IRON_INGOT));
      test("consumed_cast_mid_cooling_reload", () ->
        midCooling(false, new ItemStack(TinkerSmeltery.ingotCast.getSand()), 90, null, Items.IRON_INGOT));
      test("casting_basin_mid_cooling_reload", () ->
        midCooling(true, ItemStack.EMPTY, 810, null, Items.IRON_BLOCK));
      // Acceptance rows from the parity ledger (fixes stream, 2026-09-29): a smeltery larger than 255
      // item slots, and breaking an empty smeltery controller. Official 3.12.1 stored the slot index as a
      // byte; Continuum stores ints and reads legacy bytes as unsigned.
      test("smeltery_slots_above_255_and_tank_reload", this::smelteryAbove255);
      test("empty_smeltery_break_drops_only_controller", this::emptySmelteryBreak);
      source.sendSuccess(() -> Component.literal("AEBM_PERSISTENCE_SUMMARY passed=" + passed + " failed=" + failed), false);
      return failed == 0 ? 1 : 0;
    }

    private ToolStack tool(boolean broken) {
      ItemStack stack = ToolBuildHandler.createSingleMaterial(TinkerTools.javelin.get(), MaterialVariant.of(MaterialIds.iron, ""));
      require(!stack.isEmpty(), "loaded materials must build an iron javelin");
      ToolStack tool = ToolStack.copyFrom(stack);
      tool.addModifier(ModifierIds.returning, 4);
      tool.addModifier(ModifierIds.sharpness, 1);
      tool.getPersistentData().putInt(PROBE, 173);
      int durability = tool.getStats().getInt(ToolStats.DURABILITY);
      require(durability > 17, "fixture must have sufficient durability");
      tool.setDamage(broken ? durability : 17);
      require(tool.getMaterials().size() > 0 && tool.getUpgrades().getLevel(ModifierIds.returning) == 4,
        "fixture must contain actual materials and Returning upgrade");
      require(tool.getVolatileData().getInt(ThrownTool.LOYALTY) == 4, "Returning must build volatile loyalty");
      return tool;
    }

    private void assertTool(ToolStack expected, ToolStack actual) {
      require(expected.getMaterials().serializeToNBT().equals(actual.getMaterials().serializeToNBT()), "material order/variants must persist");
      require(expected.getUpgrades().serializeToNBT().equals(actual.getUpgrades().serializeToNBT()), "installed upgrade levels must persist");
      require(expected.getModifiers().serializeToNBT().equals(actual.getModifiers().serializeToNBT()), "traits and combined modifiers must persist");
      require(expected.getDamage() == actual.getDamage() && expected.isBroken() == actual.isBroken(), "damage/broken state must persist");
      require(expected.getStats().equals(actual.getStats()), "all stored tool stats must persist");
      require(expected.getPersistentData().getCopy().equals(actual.getPersistentData().getCopy()), "persistent modifier data/slots must persist");
      require(actual.getPersistentData().getInt(PROBE) == 173, "persistent probe must survive");
      require(actual.getVolatileData().getInt(ThrownTool.LOYALTY) == 4, "derived Returning loyalty must survive");
    }

    private ItemStack roundtripItem(ItemStack original) throws IOException {
      ProblemReporter.Collector problems = new ProblemReporter.Collector();
      TagValueOutput output = TagValueOutput.createWithContext(problems, level.registryAccess());
      output.store("tool", ItemStack.CODEC, original);
      require(problems.isEmpty(), "item encoding: " + problems.getReport());
      CompoundTag saved = binaryRoundtrip(output.buildResult());
      ItemStack restored = TagValueInput.create(problems, level.registryAccess(), saved)
        .read("tool", ItemStack.CODEC).orElseThrow(() -> new AssertionError("item codec produced no tool"));
      require(problems.isEmpty(), "item decoding: " + problems.getReport());
      return restored;
    }

    private CastingBlockEntity casting(boolean basin, ItemStack input) {
      CastingBlockEntity casting = newCasting(basin);
      if (!input.isEmpty()) {
        casting.setItem(CastingBlockEntity.INPUT, input);
      }
      casting.setLevel(level);
      return casting;
    }

    private CastingBlockEntity newCasting(boolean basin) {
      BlockState state = basin ? TinkerSmeltery.searedBasin.get().defaultBlockState() : TinkerSmeltery.searedTable.get().defaultBlockState();
      return basin ? new CastingBlockEntity.Basin(position, state) : new CastingBlockEntity.Table(position, state);
    }

    private void tick(CastingBlockEntity casting) {
      CastingBlockEntity.SERVER_TICKER.tick(level, position, casting.getBlockState(), casting);
    }

    private void midCooling(boolean basin, ItemStack input, int fluidAmount, Item retainedCast, Item expectedOutput) throws IOException {
      CastingBlockEntity casting = casting(basin, input);
      require(casting.getTank().fill(iron(fluidAmount), FluidActions.EXECUTE) == fluidAmount, "recipe must accept exact iron amount");
      require(casting.getTank().getCapacity() == fluidAmount, "recipe capacity must match accepted amount");
      require(casting.getCoolingTime() > 17, "recipe must have a measurable cooling period");
      for (int i = 0; i < 17; i++) {
        tick(casting);
      }
      require(casting.getTimer() == 17 && casting.getItem(CastingBlockEntity.OUTPUT).isEmpty(), "cast must be mid-cooling before save");
      CompoundTag saved = saveCasting(casting);
      CastingBlockEntity restored = loadCasting(basin, saved, false);
      assertCastingSnapshot(casting, restored, saved);
      finishAndCheck(restored, basin, retainedCast, expectedOutput);
    }

    private CompoundTag saveCasting(CastingBlockEntity casting) throws IOException {
      ProblemReporter.Collector problems = new ProblemReporter.Collector();
      TagValueOutput output = TagValueOutput.createWithContext(problems, level.registryAccess());
      casting.saveWithoutMetadata(output);
      require(problems.isEmpty(), "casting encoding: " + problems.getReport());
      return binaryRoundtrip(output.buildResult());
    }

    private CastingBlockEntity loadCasting(boolean basin, CompoundTag saved, boolean levelFirst) {
      ProblemReporter.Collector problems = new ProblemReporter.Collector();
      CastingBlockEntity restored = newCasting(basin);
      if (levelFirst) {
        restored.setLevel(level);
      }
      restored.loadWithComponents(TagValueInput.create(problems, level.registryAccess(), saved));
      require(problems.isEmpty(), "casting decoding: " + problems.getReport());
      if (!levelFirst) {
        restored.setLevel(level);
      }
      return restored;
    }

    private void assertCastingSnapshot(CastingBlockEntity expected, CastingBlockEntity actual, CompoundTag saved) throws IOException {
      require(expected.getTimer() == actual.getTimer(), "cooling progress must persist");
      require(expected.getCoolingTime() == actual.getCoolingTime(), "cooling duration must resolve from saved recipe");
      require(expected.getTank().getCapacity() == actual.getTank().getCapacity(), "tank capacity must persist");
      require(FluidStack.isSameFluidSameComponents(expected.getTank().getFluid(), actual.getTank().getFluid())
        && expected.getTank().getFluid().getAmount() == actual.getTank().getFluid().getAmount(), "fluid identity/components/amount must persist");
      require(ItemStack.matches(expected.getItem(CastingBlockEntity.INPUT), actual.getItem(CastingBlockEntity.INPUT)), "cast input must persist");
      require(ItemStack.matches(expected.getItem(CastingBlockEntity.OUTPUT), actual.getItem(CastingBlockEntity.OUTPUT)), "output must persist");
      String recipe = saved.getStringOr("recipe", "");
      require(!recipe.isEmpty() && recipe.equals(saveCasting(actual).getStringOr("recipe", "")), "selected recipe ID must persist");
      require(!actual.getRecipeOutput().isEmpty(), "restored recipe must resolve a real output");
    }

    private void finishAndCheck(CastingBlockEntity casting, boolean basin, Item retainedCast, Item expectedOutput) throws IOException {
      int remaining = casting.getCoolingTime() - casting.getTimer();
      require(remaining > 0 && remaining < 2000, "fixture requires bounded remaining cooling time");
      for (int i = 1; i < remaining; i++) {
        tick(casting);
      }
      require(casting.getItem(CastingBlockEntity.OUTPUT).isEmpty(), "output must not appear before remaining cooling completes");
      tick(casting);
      require(casting.getItem(CastingBlockEntity.OUTPUT).is(expectedOutput)
        && casting.getItem(CastingBlockEntity.OUTPUT).getCount() == 1, "completion must produce exactly one expected item");
      require(casting.getTank().isEmpty() && casting.getTimer() == 0, "completion must consume fluid and reset timer");
      ItemStack input = casting.getItem(CastingBlockEntity.INPUT);
      require(retainedCast == null ? input.isEmpty() : input.is(retainedCast) && input.getCount() == 1,
        "cast consumption/reuse must match recipe");
      CompoundTag finished = saveCasting(casting);
      require(finished.getStringOr("recipe", "").isEmpty(), "finished cast must not retain an active recipe");
      CastingBlockEntity restored = loadCasting(basin, finished, false);
      require(ItemStack.matches(casting.getItem(CastingBlockEntity.INPUT), restored.getItem(CastingBlockEntity.INPUT)), "finished input must persist");
      require(ItemStack.matches(casting.getItem(CastingBlockEntity.OUTPUT), restored.getItem(CastingBlockEntity.OUTPUT)), "finished output must persist");
      require(restored.getTank().isEmpty(), "finished reload must not restore consumed fluid");
      tick(restored);
      require(restored.getItem(CastingBlockEntity.OUTPUT).is(expectedOutput)
        && restored.getItem(CastingBlockEntity.OUTPUT).getCount() == 1 && restored.getTank().isEmpty(),
        "post-reload tick must retain one output without recasting");
    }

    /** Detached smeltery controller with real structure metadata; never placed into a chunk. */
    private SmelteryBlockEntity smeltery(boolean withStructure) {
      SmelteryBlockEntity controller = new SmelteryBlockEntity(position, TinkerSmeltery.smelteryController.get().defaultBlockState());
      controller.setLevel(level);
      if (withStructure) {
        // Smeltery structures have a floor and no ceiling: 7 x 8 x 7 = 392 inner item slots.
        controller.setStructureSize(position, position.offset(8, 8, 8), List.of());
      }
      return controller;
    }

    private void smelteryAbove255() throws IOException {
      SmelteryBlockEntity original = smeltery(true);
      MeltingModuleInventory inventory = original.getMeltingInventory();
      int size = inventory.getSlots();
      require(size == 392, "7 x 8 x 7 structure must allocate 392 slots, got " + size);
      int[] slots = {0, 255, 256, 300, size - 1};
      Item[] items = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.RAW_IRON, Items.IRON_NUGGET};
      for (int i = 0; i < slots.length; i++) {
        inventory.setStackInSlot(slots[i], new ItemStack(items[i]));
      }
      require(original.getTank().fill(iron(1000), FluidActions.EXECUTE) == 1000, "tank must accept 1000 mB iron");
      require(original.getTank().fill(new FluidStack(TinkerFluids.moltenCopper.get(), 250), FluidActions.EXECUTE) == 250, "tank must accept 250 mB copper");
      CompoundTag saved = saveBlockEntity(original);
      SmelteryBlockEntity restored = loadSmeltery(saved);
      MeltingModuleInventory reloaded = restored.getMeltingInventory();
      require(reloaded.getSlots() == size, "slot count above 255 must survive reload, got " + reloaded.getSlots());
      for (int slot = 0; slot < size; slot++) {
        require(ItemStack.matches(inventory.getStackInSlot(slot), reloaded.getStackInSlot(slot)), "slot " + slot + " must survive reload");
      }
      require(restored.getTank().getContained() == 1250 && restored.getTank().getFluids().size() == 2, "tank amounts must survive reload");
      require(restored.getTank().getFluidInTank(0).getAmount() == 1000 && restored.getTank().getFluidInTank(1).getAmount() == 250,
        "per-fluid amounts and order must survive reload");
      // Legacy data: official 3.12.1 wrote the slot index and size as bytes; 200 was written as (byte) -56.
      CompoundTag legacy = saved.copy();
      CompoundTag inventoryTag = legacy.getCompoundOrEmpty("inventory");
      ListTag list = inventoryTag.getListOrEmpty("items");
      for (int i = 0; i < list.size(); i++) {
        CompoundTag entry = list.getCompoundOrEmpty(i);
        if (entry.getIntOr("slot", -1) == 0) {
          entry.putByte("slot", (byte) 200);
        }
      }
      SmelteryBlockEntity fromLegacy = loadSmeltery(legacy);
      require(fromLegacy.getMeltingInventory().getStackInSlot(200).is(Items.IRON_INGOT), "legacy byte slot 200 must read as unsigned 200");
    }

    private void emptySmelteryBreak() {
      SmelteryBlockEntity controller = smeltery(false);
      require(controller.getTank().getContained() == 0, "fixture controller starts empty");
      List<ItemStack> spawned = new ArrayList<>();
      Consumer<EntityJoinLevelEvent> capture = event -> {
        if (event.getLevel() == level && event.getEntity() instanceof ItemEntity item) {
          spawned.add(item.getItem().copy());
          event.setCanceled(true);
        }
      };
      NeoForge.EVENT_BUS.addListener(capture);
      try {
        // 26.1 runs this before a block entity is removed; containers drop contents here.
        controller.preRemoveSideEffects(position, controller.getBlockState());
      } finally {
        NeoForge.EVENT_BUS.unregister(capture);
      }
      require(spawned.isEmpty(), "removing an empty controller must spawn nothing: " + spawned);
      List<ItemStack> drops = controller.getBlockState().getDrops(new LootParams.Builder(level)
        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(position))
        .withParameter(LootContextParams.TOOL, new ItemStack(Items.IRON_PICKAXE))
        .withOptionalParameter(LootContextParams.BLOCK_ENTITY, controller));
      require(drops.size() == 1 && drops.getFirst().getCount() == 1
        && ItemStack.isSameItemSameComponents(drops.getFirst(), new ItemStack(TinkerSmeltery.smelteryController.get())),
        "breaking an empty controller must drop exactly one plain controller: " + drops);
    }

    private CompoundTag saveBlockEntity(net.minecraft.world.level.block.entity.BlockEntity blockEntity) throws IOException {
      ProblemReporter.Collector problems = new ProblemReporter.Collector();
      TagValueOutput output = TagValueOutput.createWithContext(problems, level.registryAccess());
      blockEntity.saveWithoutMetadata(output);
      require(problems.isEmpty(), "block entity encoding: " + problems.getReport());
      return binaryRoundtrip(output.buildResult());
    }

    private SmelteryBlockEntity loadSmeltery(CompoundTag saved) {
      ProblemReporter.Collector problems = new ProblemReporter.Collector();
      SmelteryBlockEntity restored = new SmelteryBlockEntity(position, TinkerSmeltery.smelteryController.get().defaultBlockState());
      restored.loadWithComponents(TagValueInput.create(problems, level.registryAccess(), saved));
      require(problems.isEmpty(), "smeltery decoding: " + problems.getReport());
      restored.setLevel(level);
      return restored;
    }

    private void test(String name, CheckedRunnable test) {
      try {
        test.run();
        passed++;
        source.sendSuccess(() -> Component.literal("AEBM_PERSISTENCE_PASS " + name), false);
      } catch (Exception | AssertionError failure) {
        failed++;
        Throwable cause = failure;
        while (cause.getCause() != null) {
          cause = cause.getCause();
        }
        source.sendFailure(Component.literal("AEBM_PERSISTENCE_FAIL " + name + " " + cause));
        // Full stack for diagnosis; the FAIL line above stays the single machine-readable record.
        LogUtils.getLogger().error("AEBM_PERSISTENCE_TRACE {}", name, failure);
      }
    }
  }

  private static FluidStack iron(int amount) {
    return new FluidStack(TinkerFluids.moltenIron.get(), amount);
  }

  private static CompoundTag binaryRoundtrip(CompoundTag tag) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      NbtIo.write(tag, output);
    }
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return NbtIo.read(input, NbtAccounter.unlimitedHeap());
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  @FunctionalInterface
  private interface CheckedRunnable {
    void run() throws Exception;
  }
}
