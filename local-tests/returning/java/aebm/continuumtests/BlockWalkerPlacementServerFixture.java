package aebm.continuumtests;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import slimeknights.mantle.data.predicate.block.BlockPropertiesPredicate;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.modules.armor.ReplaceBlockWalkerModule;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;

/**
 * Real world/event-bus regression for the block-walker placement veto.
 * Run only in a disposable, already loaded, empty area chosen by the coordinator
 * with execute positioned. Every touched block is restored before this command returns.
 * Water/ice scheduled ticks may remain, but their blocks have been restored to air.
 */
public final class BlockWalkerPlacementServerFixture {
  private BlockWalkerPlacementServerFixture() {}

  public static void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("aebmblockwalkertest")
      .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
      .executes(context -> new Suite(context.getSource()).run()));
  }

  private static final class Suite {
    private final CommandSourceStack source;
    private final ServerLevel level;
    private final BlockPos origin;
    private final Map<BlockPos,BlockState> originals = new LinkedHashMap<>();
    private final List<BlockPos> bases = new ArrayList<>();
    private int passed;
    private int failed;

    private Suite(CommandSourceStack source) {
      this.source = source;
      this.level = source.getLevel();
      this.origin = BlockPos.containing(source.getPosition());
    }

    private int run() {
      // Refuse before writing any block. No chunk tickets, far coordinates, or chunk generation.
      try {
        for (int index = 0; index < 5; index++) {
          BlockPos base = origin.offset(index * 2, 0, 0);
          bases.add(base);
          for (BlockPos position : List.of(base.below(), base, base.above(), base.above(2))) {
            require(!level.isOutsideBuildHeight(position), "fixture position outside build height: " + position);
            require(level.hasChunkAt(position), "fixture requires an already loaded chunk: " + position);
            require(level.isEmptyBlock(position), "fixture refuses nonempty position: " + position);
            require(level.getBlockEntity(position) == null, "fixture refuses block entity: " + position);
          }
          require(!level.getBlockTicks().hasScheduledTick(base, Blocks.FROSTED_ICE),
            "fixture requires no pre-existing frosted-ice tick: " + base);
          originals.put(base.below(), level.getBlockState(base.below()));
          originals.put(base, level.getBlockState(base));
          originals.put(base.above(), level.getBlockState(base.above()));
        }
        // Neighbor notifications must not cause this fixture to request an unloaded chunk.
        for (BlockPos position : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(9, 2, 1))) {
          require(level.hasChunkAt(position), "fixture neighbor chunk must already be loaded: " + position);
        }
      } catch (Throwable failure) {
        source.sendFailure(Component.literal("AEBM_BLOCK_WALKER_REFUSED " + failure.getMessage()));
        return 0;
      }

      try {
        test("canceled_placement_preserves_water_and_no_ice_tick", () -> exercise(0, true, false, false, false));
        test("allowed_placement_freezes_and_schedules_tick", () -> exercise(1, false, false, false, false));
        test("nonmatching_block_is_not_overwritten", () -> exercise(2, false, true, false, false));
        test("occupied_feet_block_preserves_both_blocks", () -> exercise(3, false, false, true, false));
        test("modifier_level_mismatch_does_not_post_or_place", () -> exercise(4, false, false, false, true));
      } finally {
        // Restore only our initially empty positions, including when an assertion fails.
        for (Map.Entry<BlockPos,BlockState> saved : originals.entrySet()) {
          level.setBlockAndUpdate(saved.getKey(), saved.getValue());
        }
      }
      source.sendSuccess(() -> Component.literal("AEBM_BLOCK_WALKER_SUMMARY passed=" + passed + " failed=" + failed), false);
      return failed == 0 ? 1 : 0;
    }

    private void exercise(int index, boolean cancel, boolean wrongBlock, boolean obstructedAbove, boolean wrongLevel) {
      BlockPos base = bases.get(index);
      BlockState initial = (wrongBlock ? Blocks.STONE : Blocks.WATER).defaultBlockState();
      // A distinct support state makes the Direction.UP/placed-against assertion meaningful.
      require(level.setBlockAndUpdate(base.below(), Blocks.STONE.defaultBlockState()), "fixture must place support");
      require(level.setBlockAndUpdate(base, initial), "fixture must place initial block");
      if (obstructedAbove) {
        require(level.setBlockAndUpdate(base.above(), Blocks.STONE.defaultBlockState()), "fixture must place obstruction");
      }

      // Neither actor nor tool is added to the player list/world or any real player's inventory.
      FakePlayer actor = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "AEBMWalkerTest"));
      actor.setPos(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
      ItemStack stack = ToolBuildHandler.createSingleMaterial(TinkerTools.pickaxe.get(), MaterialVariant.of(MaterialIds.iron, ""));
      require(!stack.isEmpty(), "loaded tool/material data must create fixture tool");
      ToolStack tool = ToolStack.from(stack);
      require(!tool.isBroken(), "fixture tool must be usable");
      ModifierEntry modifier = new ModifierEntry(ModifierIds.frostWalker, 1);
      var builder = ReplaceBlockWalkerModule.builder();
      var water = BlockPropertiesPredicate.block(Blocks.WATER).matches(LiquidBlock.LEVEL, 0).build();
      if (wrongLevel) {
        builder.replaceLevelRange(water, Blocks.FROSTED_ICE.defaultBlockState(), 2, 3);
      } else {
        builder.replaceAlways(water, Blocks.FROSTED_ICE.defaultBlockState());
      }
      ReplaceBlockWalkerModule module = builder.amount(2, 1);
      PlacementObserver observer = new PlacementObserver(actor, level, base, cancel);
      NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, BlockEvent.EntityPlaceEvent.class, observer);
      try {
        boolean stopWalking = module.walkOn(tool, modifier, actor, level, base.above(), new BlockPos.MutableBlockPos(), null);
        require(!stopWalking, "usable tool must retain original continue-walking result");
        if (wrongBlock || obstructedAbove || wrongLevel) {
          require(observer.events == 0, "ineligible replacement must not post placement event");
          require(level.getBlockState(base).equals(initial), "ineligible target must not be overwritten");
          require(!level.getBlockTicks().hasScheduledTick(base, Blocks.FROSTED_ICE), "ineligible target must not schedule ice tick");
          if (obstructedAbove) {
            require(level.getBlockState(base.above()).is(Blocks.STONE), "feet obstruction must not be overwritten");
          }
        } else {
          require(observer.events == 1, "one eligible replacement must post exactly one placement event");
          require(observer.errors.isEmpty(), "event contract: " + observer.errors);
          require(level.getBlockState(base).is(cancel ? Blocks.WATER : Blocks.FROSTED_ICE),
            "canceled replacement must preserve water; allowed replacement must freeze it");
          require(level.getBlockTicks().hasScheduledTick(base, Blocks.FROSTED_ICE) == !cancel,
            "only an allowed replacement may schedule the ice tick");
        }
      } finally {
        NeoForge.EVENT_BUS.unregister(observer);
      }
    }

    private void test(String name, Runnable body) {
      try {
        body.run();
        passed++;
        source.sendSuccess(() -> Component.literal("AEBM_BLOCK_WALKER_PASS " + name), false);
      } catch (Throwable failure) {
        failed++;
        source.sendFailure(Component.literal("AEBM_BLOCK_WALKER_FAIL " + name + " " + failure));
      }
    }
  }

  private static final class PlacementObserver implements Consumer<BlockEvent.EntityPlaceEvent> {
    private final FakePlayer actor;
    private final ServerLevel level;
    private final BlockPos position;
    private final boolean cancel;
    private final List<String> errors = new ArrayList<>();
    private int events;

    private PlacementObserver(FakePlayer actor, ServerLevel level, BlockPos position, boolean cancel) {
      this.actor = actor;
      this.level = level;
      this.position = position;
      this.cancel = cancel;
    }

    @Override
    public void accept(BlockEvent.EntityPlaceEvent event) {
      // Ignore other entities and positions; cancellation never applies outside this fixture.
      if (event.getEntity() != actor || !event.getPos().equals(position)) {
        return;
      }
      events++;
      if (event.getLevel() != level) errors.add("wrong level");
      if (!event.getBlockSnapshot().getPos().equals(position)) errors.add("wrong snapshot position");
      if (!event.getBlockSnapshot().getState().is(Blocks.WATER)) errors.add("snapshot must contain original water");
      if (!level.getBlockState(position).is(Blocks.WATER)) errors.add("event must run before block mutation");
      if (!event.getPlacedAgainst().is(Blocks.STONE)
          || !event.getPlacedAgainst().equals(level.getBlockState(position.below()))) errors.add("placement face must be UP");
      if (cancel) event.setCanceled(true);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
