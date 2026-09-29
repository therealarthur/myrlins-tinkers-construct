package slimeknights.tconstruct.library.client.recipe.transfer;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner.Request;
import slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner.Requirement;
import slimeknights.tconstruct.tables.menu.CraftingStationContainerMenu;
import slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu;
import slimeknights.tconstruct.tools.menu.ReadOnlySlot;
import slimeknights.tconstruct.tools.menu.ToolContainerMenu;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Slot roles for the three menus that official 3.12.1 JEI transfers into ({@code plugin/jei/transfer/*}), built from the
 * menus' own public layout instead of fixed offsets:
 * <ul>
 *   <li>crafting station: grid slots 0 to 8 (result 9), items from the player's inventory, then from adjacent storage slots
 *   that hold items and allow modification;</li>
 *   <li>tinker station and both anvils: the menu's station input slots; the tool slot is left alone, as official does;</li>
 *   <li>tool inventory crafting: the 3 by 3 or 2 by 2 grid after the result slot, items from the tool's own inventory, the
 *   offhand and the player's inventory, never the read-only slot holding the tool.</li>
 * </ul>
 * Cleared items and leftovers only ever go to the player's inventory slots, whose rules do not depend on their contents.
 */
public final class MenuTransferSlots {
  private MenuTransferSlots() {}

  /** Number of player inventory slots added at the end of Tinkers' table menus. */
  private static final int PLAYER_SLOTS = 36;

  /**
   * Crafting station request.
   * @param grid  alternatives for each grid cell, index {@code x + 3 * y}; empty lists stay empty
   */
  public static Request craftingStation(CraftingStationContainerMenu menu, @Nullable Player player, List<List<ItemStack>> grid, boolean max) {
    int total = menu.slots.size();
    int playerStart = total - PLAYER_SLOTS;
    List<Requirement> requirements = new ArrayList<>();
    List<Integer> clear = new ArrayList<>();
    for (int i = 0; i < 9 && i < playerStart; i++) {
      clear.add(i);
      if (i < grid.size() && !grid.get(i).isEmpty()) requirements.add(new Requirement(i, grid.get(i)));
    }
    List<Integer> playerSlots = range(playerStart, total);
    List<Integer> sources = new ArrayList<>(playerSlots);
    // adjacent storage, only slots with items that this player may take from, like official JEI
    for (int i = 10; i < playerStart; i++) {
      Slot slot = menu.getSlot(i);
      if (slot.hasItem() && slot.mayPickup(player) && slot.allowModification(player)) sources.add(i);
    }
    return new Request(requirements, clear, sources, playerSlots, max, TransferPlanner.DEFAULT_CLICK_BUDGET);
  }

  /**
   * Tinker station or anvil request.
   * @param station  alternatives for each station input index; indices past the station's inputs make the request too large
   * @return null when the recipe needs more station inputs than this station has
   */
  @Nullable
  public static Request tinkerStation(TinkerStationContainerMenu menu, @Nullable Player player, List<List<ItemStack>> station, boolean max) {
    List<Slot> inputs = menu.getInputSlots();
    List<Requirement> requirements = new ArrayList<>();
    for (int i = 0; i < station.size(); i++) {
      if (station.get(i).isEmpty()) continue;
      if (i >= inputs.size()) return null;
      requirements.add(new Requirement(inputs.get(i).index, station.get(i)));
    }
    List<Integer> clear = inputs.stream().map(slot -> slot.index).toList();
    int total = menu.slots.size();
    List<Integer> playerSlots = range(Math.max(0, total - PLAYER_SLOTS), total);
    return new Request(requirements, clear, playerSlots, playerSlots, max, TransferPlanner.DEFAULT_CLICK_BUDGET);
  }

  /**
   * Checks whether the station can take the recipe as laid out right now.
   * @return null when it can, {@code "too_large"} when the recipe needs more inputs than the station has, or
   *         {@code "select_layout"} when a needed input slot is hidden by the selected station layout
   */
  @Nullable
  public static String tinkerStationProblem(TinkerStationContainerMenu menu, List<List<ItemStack>> station) {
    List<Slot> inputs = menu.getInputSlots();
    for (int i = 0; i < station.size(); i++) {
      if (station.get(i).isEmpty()) continue;
      if (i >= inputs.size()) return "too_large";
      if (inputs.get(i) instanceof slimeknights.tconstruct.tables.menu.slot.TinkerStationSlot slot && slot.isDormant()) return "select_layout";
    }
    return null;
  }

  /**
   * Tool inventory crafting request.
   * @param grid  alternatives per grid cell in the menu's own grid width (3 for the full grid, 2 for the inventory grid)
   * @return null when the tool has no crafting grid
   */
  @Nullable
  public static Request toolInventory(ToolContainerMenu menu, @Nullable Player player, List<List<ItemStack>> grid, boolean max) {
    int gridSlots = menu.getToolInventoryStart() - 1;
    if (gridSlots <= 0) return null;
    List<Requirement> requirements = new ArrayList<>();
    List<Integer> clear = new ArrayList<>();
    for (int i = 0; i < gridSlots; i++) {
      clear.add(1 + i);
      if (i < grid.size() && !grid.get(i).isEmpty()) requirements.add(new Requirement(1 + i, grid.get(i)));
    }
    int total = menu.slots.size();
    List<Integer> sources = new ArrayList<>();
    for (int i = menu.getToolInventoryStart(); i < total; i++) {
      if (!(menu.getSlot(i) instanceof ReadOnlySlot)) sources.add(i);
    }
    List<Integer> playerSlots = new ArrayList<>();
    for (int i = menu.getPlayerInventoryStart(); i < total; i++) {
      if (!(menu.getSlot(i) instanceof ReadOnlySlot)) playerSlots.add(i);
    }
    return new Request(requirements, clear, sources, playerSlots, max, TransferPlanner.DEFAULT_CLICK_BUDGET);
  }

  /** Grid width of a tool inventory's crafting area. */
  public static int toolGridWidth(ToolContainerMenu menu) {
    int gridSlots = menu.getToolInventoryStart() - 1;
    return gridSlots >= 9 ? 3 : gridSlots >= 4 ? 2 : 0;
  }

  /** Slot numbers from start (inclusive) to end (exclusive). */
  static List<Integer> range(int start, int end) {
    List<Integer> list = new ArrayList<>();
    for (int i = start; i < end; i++) list.add(i);
    return list;
  }

  /** Plans a request for any menu, using the menu's own slots and cursor. */
  public static TransferPlanner.Plan plan(AbstractContainerMenu menu, @Nullable Player player, Request request) {
    return TransferPlanner.plan(menu::getSlot, player, menu.getCarried(), request);
  }
}
