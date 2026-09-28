package aebm.continuumtests;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.block.entity.table.CraftingStationBlockEntity;
import slimeknights.tconstruct.tables.menu.CraftingStationContainerMenu;

/** Real recipe/menu checks after server data is loaded, with no world blocks or connected client. */
public final class CraftingServerFixture {
  private CraftingServerFixture() {}

  public static void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("aebmcraftingtest")
      .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
      .executes(context -> new Suite(context.getSource()).run()));
  }

  private static final class Suite {
    private final CommandSourceStack source;
    private final ServerLevel level;
    private int passed;
    private int failed;

    private Suite(CommandSourceStack source) {
      this.source = source;
      this.level = source.getLevel();
    }

    private int run() {
      test("all_full_preserves_grid_and_output", () -> {
        Fixture fixture = logs(true);
        require(fixture.menu.quickMoveStack(fixture.player, 9).isEmpty(), "full inventory must stop shift-crafting");
        require(fixture.station.getItem(0).getCount() == 2, "full inventory must preserve both logs");
        require(fixture.player.dropped.isEmpty(), "full inventory must not drop crafted items");
        require(countPlayer(fixture.player, Items.OAK_PLANKS) == 0, "full inventory must not deliver phantom output");
        require(fixture.station.getResultForPlayer(fixture.player).getCount() == 4, "recipe must still be available");
        require(fixture.menu.quickMoveStack(fixture.player, 9).isEmpty(), "repeated full-inventory click must stop");
        require(fixture.station.getItem(0).getCount() == 2, "repeated full-inventory click must preserve inputs");
      });
      test("repeated_craft_consumes_once_per_output", () -> {
        Fixture fixture = logs(false);
        require(fixture.menu.quickMoveStack(fixture.player, 9).getCount() == 4, "first craft must report four planks");
        require(fixture.station.getItem(0).getCount() == 1, "first craft consumes one log");
        require(countPlayer(fixture.player, Items.OAK_PLANKS) == 4, "first craft delivers four planks");
        require(fixture.menu.quickMoveStack(fixture.player, 9).getCount() == 4, "second craft must report four planks");
        require(fixture.station.isEmpty(), "second craft consumes last log");
        require(countPlayer(fixture.player, Items.OAK_PLANKS) == 8, "two logs produce exactly eight planks");
        require(fixture.menu.quickMoveStack(fixture.player, 9).isEmpty(), "empty recipe must stop shift-crafting");
        require(fixture.player.dropped.isEmpty(), "available inventory must avoid drops");
      });
      test("partial_capacity_conserves_remainder", () -> {
        Fixture fixture = logs(true);
        fixture.player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 63));
        require(fixture.menu.quickMoveStack(fixture.player, 9).getCount() == 4, "partial craft must report full recipe result");
        require(fixture.station.getItem(0).getCount() == 1, "partial insertion consumes exactly one log");
        require(countPlayer(fixture.player, Items.OAK_PLANKS) == 64, "only one plank fits inventory");
        require(countDropped(fixture.player, Items.OAK_PLANKS) == 3, "remaining three planks must be handed to drop path");
        require(fixture.menu.quickMoveStack(fixture.player, 9).isEmpty(), "next full-inventory attempt must stop");
        require(fixture.station.getItem(0).getCount() == 1, "next full-inventory attempt preserves remaining log");
        require(countDropped(fixture.player, Items.OAK_PLANKS) == 3, "failed retry must not produce extra drops");
      });
      test("recipe_bucket_remainders_preserved", () -> {
        RecordingPlayer player = player();
        CraftingStationBlockEntity station = station();
        for (int slot = 0; slot < 3; slot++) {
          station.setItem(slot, new ItemStack(Items.MILK_BUCKET));
          station.setItem(slot + 6, new ItemStack(Items.WHEAT));
        }
        station.setItem(3, new ItemStack(Items.SUGAR));
        station.setItem(4, new ItemStack(Items.EGG));
        station.setItem(5, new ItemStack(Items.SUGAR));
        CraftingStationContainerMenu menu = menu(player, station);
        require(station.getResultForPlayer(player).is(Items.CAKE), "fixture must find vanilla cake recipe");
        require(menu.quickMoveStack(player, 9).is(Items.CAKE), "cake craft must succeed");
        require(countPlayer(player, Items.CAKE) == 1, "one cake delivered");
        for (int slot = 0; slot < 3; slot++) {
          require(station.getItem(slot).is(Items.BUCKET) && station.getItem(slot).getCount() == 1,
            "each milk bucket must leave one bucket in grid");
        }
        for (int slot = 3; slot < 9; slot++) {
          require(station.getItem(slot).isEmpty(), "cake ingredients must be consumed");
        }
        require(player.dropped.isEmpty(), "bucket remainders fit original grid slots");
      });
      source.sendSuccess(() -> Component.literal("AEBM_CRAFTING_SUMMARY passed=" + passed + " failed=" + failed), false);
      return failed == 0 ? 1 : 0;
    }

    private Fixture logs(boolean full) {
      RecordingPlayer player = player();
      if (full) {
        for (int slot = 0; slot < 36; slot++) {
          player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        }
      }
      CraftingStationBlockEntity station = station();
      station.setItem(0, new ItemStack(Items.OAK_LOG, 2));
      CraftingStationContainerMenu menu = menu(player, station);
      ItemStack result = station.getResultForPlayer(player);
      require(result.is(Items.OAK_PLANKS) && result.getCount() == 4, "fixture must find four-plank recipe");
      return new Fixture(player, station, menu);
    }

    private RecordingPlayer player() {
      RecordingPlayer player = new RecordingPlayer(level);
      player.setPos(0, level.getMinY() + 96, 0);
      return player;
    }

    private CraftingStationBlockEntity station() {
      CraftingStationBlockEntity station = new CraftingStationBlockEntity(
        new BlockPos(0, level.getMinY() + 96, 0), TinkerTables.craftingStation.get().defaultBlockState());
      station.setLevel(level);
      return station;
    }

    private CraftingStationContainerMenu menu(RecordingPlayer player, CraftingStationBlockEntity station) {
      CraftingStationContainerMenu menu = new CraftingStationContainerMenu(1, player.getInventory(), station) {
        @Override
        protected void addChestSideInventory() {
          // Isolate recipe/output ordering from unrelated blocks in the test world's vicinity.
        }
      };
      player.containerMenu = menu;
      return menu;
    }

    private void test(String name, Runnable test) {
      try {
        test.run();
        passed++;
        source.sendSuccess(() -> Component.literal("AEBM_CRAFTING_PASS " + name), false);
      } catch (Exception | AssertionError failure) {
        failed++;
        Throwable cause = failure;
        while (cause.getCause() != null) {
          cause = cause.getCause();
        }
        source.sendFailure(Component.literal("AEBM_CRAFTING_FAIL " + name + " " + cause));
      }
    }
  }

  private static final class RecordingPlayer extends FakePlayer {
    private final List<ItemStack> dropped = new ArrayList<>();

    private RecordingPlayer(ServerLevel level) {
      super(level, new GameProfile(UUID.randomUUID(), "CraftingFixture"));
    }

    @Override
    public ItemEntity drop(ItemStack stack, boolean randomDirection) {
      dropped.add(stack.copy());
      return null;
    }
  }

  private static int countPlayer(RecordingPlayer player, Item item) {
    int result = 0;
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
      ItemStack stack = player.getInventory().getItem(slot);
      if (stack.is(item)) {
        result += stack.getCount();
      }
    }
    return result;
  }

  private static int countDropped(RecordingPlayer player, Item item) {
    return player.dropped.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  private record Fixture(RecordingPlayer player, CraftingStationBlockEntity station, CraftingStationContainerMenu menu) {}
}
