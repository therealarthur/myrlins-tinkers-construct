package aebm.continuumtests;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.client.recipe.transfer.ClickModel;
import slimeknights.tconstruct.library.client.recipe.transfer.TransferExecutor;
import slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner;
import slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner.Blocked;
import slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner.Click;
import slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner.Missing;
import slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner.Plan;
import slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner.Ready;
import slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner.Request;
import slimeknights.tconstruct.library.client.recipe.transfer.TransferPlanner.Requirement;
import slimeknights.tconstruct.tables.menu.module.SideInventoryContainer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Conservation of the REI "+" transfer, modeled on {@code SideInventoryTest}.
 * <p>
 * Plans are replayed on real slot objects: plain container slots for the grid and the player inventory, and the real
 * side-inventory transfer slot over NeoForge transactional storage (the class behind the #29 shift-click loss).
 * {@link RealClicks} performs each click with the same Slot calls as vanilla {@code AbstractContainerMenu.doClick}'s
 * PICKUP branch; no world, player or graphical client is created. Every test checks exact item counts before and after.
 */
final class TransferConservationTest {
  private static final int GRID = 0;
  private static final int INVENTORY = 9;
  private static final int SIDE = 45;
  private int assertions;

  @BeforeAll
  static void components() {
    ComponentTestSetup.initialize();
  }

  @Test
  void matchingStacksFillEachTargetAndReturnTheRestToTheirSource() throws Exception {
    Rig rig = new Rig(0);
    rig.inventory.setItem(0, new ItemStack(Items.OAK_PLANKS, 10));
    Ready ready = ready(rig.plan(planks(0, 1, 3, 4), false));
    assertEquals(1, ready.crafts());
    List<ItemStack> before = rig.census();
    rig.apply(ready.clicks());
    for (int slot : List.of(0, 1, 3, 4)) count(rig.grid.getItem(slot), Items.OAK_PLANKS, 1, "grid slot " + slot);
    count(rig.inventory.getItem(0), Items.OAK_PLANKS, 6, "remainder returns to its source slot");
    conserved(before, rig.census(), "matching transfer");
    check(rig.carried.isEmpty(), "cursor is empty after the transfer");
  }

  @Test
  void partialInventoryReportsExactlyTheUnsatisfiedTargetsAndMovesNothing() throws Exception {
    Rig rig = new Rig(0);
    rig.inventory.setItem(5, new ItemStack(Items.OAK_PLANKS, 3));
    List<ItemStack> before = rig.census();
    Plan plan = rig.plan(planks(0, 1, 3, 4), false);
    check(plan instanceof Missing, "three planks cannot fill four slots");
    assertEquals(List.of(3), ((Missing) plan).requirements(), "the last requirement is the one left without planks");
    conserved(before, rig.census(), "planning never moves items");
    count(rig.inventory.getItem(5), Items.OAK_PLANKS, 3, "source untouched");
  }

  @Test
  void fullInventoryBlocksClearingInsteadOfDroppingGridItems() throws Exception {
    Rig rig = new Rig(0);
    rig.grid.setItem(4, new ItemStack(Items.COBBLESTONE, 5));
    for (int i = 0; i < 36; i++) rig.inventory.setItem(i, new ItemStack(Items.DIRT, 64));
    rig.inventory.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
    Plan plan = rig.plan(planks(0), false);
    check(plan instanceof Blocked blocked && blocked.reason().equals("no_room"), "a full inventory cannot receive the cleared grid");
    count(rig.grid.getItem(4), Items.COBBLESTONE, 5, "grid keeps its item when clearing is impossible");
  }

  @Test
  void emptyRequirementsAndEmptyInventory() throws Exception {
    Rig rig = new Rig(0);
    Plan none = rig.plan(new ArrayList<>(List.of(new Requirement(GRID + 2, List.of()))), false);
    check(none instanceof Ready ready && ready.clicks().isEmpty(), "a recipe with no item inputs needs no clicks");
    Plan missing = rig.plan(planks(0), false);
    check(missing instanceof Missing, "an empty inventory has nothing to move");
  }

  @Test
  void componentDistinctStacksDoNotSubstituteForEachOther() throws Exception {
    Rig rig = new Rig(0);
    ItemStack named = new ItemStack(Items.OAK_PLANKS, 4);
    named.set(DataComponents.CUSTOM_NAME, Component.literal("heirloom planks"));
    // the container keeps the stack instance it is given, so hand it a copy and keep the original as the reference
    rig.inventory.setItem(0, named.copy());
    check(rig.plan(planks(0), false) instanceof Missing, "named planks must not satisfy plain planks");
    ItemStack wanted = named.copyWithCount(1);
    Ready ready = ready(rig.plan(List.of(new Requirement(GRID, List.of(wanted))), false));
    List<ItemStack> before = rig.census();
    rig.apply(ready.clicks());
    check(ItemStack.isSameItemSameComponents(rig.grid.getItem(0), named) && rig.grid.getItem(0).getCount() == 1, "named plank moved with its name");
    count(rig.inventory.getItem(0), Items.OAK_PLANKS, 3, "named remainder stays");
    conserved(before, rig.census(), "component transfer");
  }

  @Test
  void adjacentInventoryIsUsedWhenPresentAndConservedExactly() throws Exception {
    Rig rig = new Rig(1);
    rig.side.set(0, ItemResource.of(new ItemStack(Items.OAK_PLANKS)), 7);
    Ready ready = ready(rig.plan(planks(0, 1), false));
    List<ItemStack> before = rig.census();
    rig.apply(ready.clicks());
    count(rig.grid.getItem(0), Items.OAK_PLANKS, 1, "first slot from storage");
    count(rig.grid.getItem(1), Items.OAK_PLANKS, 1, "second slot from storage");
    check(rig.side.getAmountAsInt(0) == 5, "storage keeps exactly the rest, got " + rig.side.getAmountAsInt(0));
    conserved(before, rig.census(), "adjacent transfer");

    Rig absent = new Rig(0);
    check(absent.plan(planks(0, 1), false) instanceof Missing, "without adjacent storage the same inventory is missing planks");
  }

  @Test
  void refusedPutBackGoesToThePlayerInventory() throws Exception {
    Rig rig = new Rig(1, true);
    rig.side.set(0, ItemResource.of(new ItemStack(Items.OAK_PLANKS)), 7);
    Ready ready = ready(rig.plan(planks(0), false));
    List<ItemStack> before = rig.census();
    rig.apply(ready.clicks());
    count(rig.grid.getItem(0), Items.OAK_PLANKS, 1, "grid filled");
    check(rig.side.getAmountAsInt(0) == 0, "storage that refuses insertion cannot take the rest back");
    count(rig.inventory.getItem(0), Items.OAK_PLANKS, 6, "the rest lands in the player inventory");
    conserved(before, rig.census(), "refused put-back");
  }

  @Test
  void storageThatCannotReleaseAWholeStackIsNeverASource() throws Exception {
    Rig rig = new Rig(1, false, 4);
    rig.side.set(0, ItemResource.of(new ItemStack(Items.OAK_PLANKS)), 16);
    List<ItemStack> before = rig.census();
    check(rig.plan(planks(0), false) instanceof Missing, "a slot that only releases part of its stack is skipped, like its pickup rule");
    conserved(before, rig.census(), "skipped storage untouched");
    check(rig.side.getAmountAsInt(0) == 16, "storage still holds all sixteen");
  }

  @Test
  void maxTransferSplitsEvenlyAndConserves() throws Exception {
    Rig rig = new Rig(0);
    rig.inventory.setItem(3, new ItemStack(Items.OAK_PLANKS, 64));
    rig.inventory.setItem(7, new ItemStack(Items.OAK_PLANKS, 64));
    Ready ready = ready(rig.plan(planks(0, 1, 3, 4), true));
    assertEquals(32, ready.crafts(), "128 planks over four slots");
    check(ready.clicks().size() <= TransferPlanner.DEFAULT_CLICK_BUDGET, "within the click budget");
    List<ItemStack> before = rig.census();
    rig.apply(ready.clicks());
    for (int slot : List.of(0, 1, 3, 4)) count(rig.grid.getItem(slot), Items.OAK_PLANKS, 32, "max slot " + slot);
    conserved(before, rig.census(), "max transfer");
  }

  @Test
  void existingGridItemsAreClearedThenReused() throws Exception {
    Rig rig = new Rig(0);
    rig.grid.setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
    rig.grid.setItem(8, new ItemStack(Items.COBBLESTONE, 3));
    Ready ready = ready(rig.plan(planks(0, 1), false));
    List<ItemStack> before = rig.census();
    rig.apply(ready.clicks());
    count(rig.grid.getItem(0), Items.OAK_PLANKS, 1, "slot 0 refilled with one");
    count(rig.grid.getItem(1), Items.OAK_PLANKS, 1, "slot 1 filled from the cleared planks");
    check(rig.grid.getItem(8).isEmpty(), "unrelated grid item cleared");
    int planks = 0;
    int cobble = 0;
    for (int i = 0; i < 36; i++) {
      if (rig.inventory.getItem(i).is(Items.OAK_PLANKS)) planks += rig.inventory.getItem(i).getCount();
      if (rig.inventory.getItem(i).is(Items.COBBLESTONE)) cobble += rig.inventory.getItem(i).getCount();
    }
    check(planks == 0 && cobble == 3, "inventory holds exactly the cleared leftovers: planks " + planks + ", cobblestone " + cobble);
    conserved(before, rig.census(), "clear and reuse");
  }

  @Test
  void targetsThatRejectAnItemAreReportedMissing() throws Exception {
    Rig rig = new Rig(0);
    rig.inventory.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
    rig.rejectGrid = true;
    check(rig.plan(planks(0), false) instanceof Missing, "a slot that refuses the item cannot be planned");
  }

  @Test
  void executorStopsOnTheFirstClickThatDiffersFromThePlan() throws Exception {
    Rig rig = new Rig(0);
    rig.inventory.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
    Ready ready = ready(rig.plan(planks(0, 1), false));
    List<Integer> sent = new ArrayList<>();
    // a sink that drops every click: the menu never changes, so the first click already differs from the model
    var outcome = TransferExecutor.execute(rig.menu(), null, ready.clicks(), List.of(), (slot, button) -> sent.add(slot));
    check(!outcome.complete() && outcome.divergedAt() == 0, "execution stops at the first differing click");
    assertEquals(1, sent.size(), "no further planned click is sent");
    count(rig.inventory.getItem(0), Items.OAK_PLANKS, 4, "nothing moved");
  }

  @Test
  void executorAppliesEveryClickWhenTheMenuFollowsThePlan() throws Exception {
    Rig rig = new Rig(0);
    rig.inventory.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
    Ready ready = ready(rig.plan(planks(0, 1), false));
    List<ItemStack> before = rig.census();
    TestMenu menu = rig.menu();
    var outcome = TransferExecutor.execute(menu, null, ready.clicks(), List.of(), (slot, button) -> menu.clickReal(slot, button));
    check(outcome.complete(), "every click matched");
    count(rig.grid.getItem(0), Items.OAK_PLANKS, 1, "executed slot 0");
    count(rig.grid.getItem(1), Items.OAK_PLANKS, 1, "executed slot 1");
    conserved(before, rig.census(), "executor");
  }

  /* Helpers */

  private static List<Requirement> planks(int... gridSlots) {
    List<Requirement> list = new ArrayList<>();
    for (int slot : gridSlots) list.add(new Requirement(GRID + slot, List.of(new ItemStack(Items.OAK_PLANKS))));
    return list;
  }

  private Ready ready(Plan plan) {
    check(plan instanceof Ready, "expected a ready plan, got " + plan);
    return (Ready) plan;
  }

  private void count(ItemStack stack, net.minecraft.world.item.Item item, int expected, String message) {
    check(stack.is(item) && stack.getCount() == expected, message + ": expected " + expected + " of " + item + ", got " + stack);
  }

  private void conserved(List<ItemStack> before, List<ItemStack> after, String message) {
    check(TransferPlanner.sameCensus(before, after), message + ": item totals changed from " + before + " to " + after);
  }

  private void check(boolean condition, String message) {
    assertions++;
    assertTrue(condition, message);
  }

  /** Grid (0 to 8), player inventory (9 to 44) and optional side storage slots (45 on), all real slot classes. */
  private static final class Rig {
    final SimpleContainer grid = new SimpleContainer(9);
    final SimpleContainer inventory = new SimpleContainer(36);
    final ItemStacksResourceHandler side;
    final List<Slot> slots = new ArrayList<>();
    ItemStack carried = ItemStack.EMPTY;
    boolean rejectGrid;

    Rig(int sideSlots) throws Exception {
      this(sideSlots, false);
    }

    Rig(int sideSlots, boolean denyInsert) throws Exception {
      this(sideSlots, denyInsert, Integer.MAX_VALUE);
    }

    Rig(int sideSlots, boolean denyInsert, int extractionLimit) throws Exception {
      for (int i = 0; i < 9; i++) {
        slots.add(new Slot(grid, i, 0, 0) {
          @Override
          public boolean mayPlace(ItemStack stack) {
            return !rejectGrid;
          }
        });
      }
      for (int i = 0; i < 36; i++) slots.add(new Slot(inventory, i, 0, 0));
      side = new ItemStacksResourceHandler(Math.max(1, sideSlots)) {
        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
          return denyInsert ? 0 : super.insert(index, resource, amount, transaction);
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
          return super.extract(index, resource, Math.min(amount, extractionLimit), transaction);
        }
      };
      if (sideSlots > 0) {
        IItemHandler bridge = bridge(side);
        SideHarness harness = new SideHarness();
        for (int i = 0; i < sideSlots; i++) slots.add(harness.make(bridge, i));
      }
    }

    Plan plan(List<Requirement> requirements, boolean max) {
      List<Integer> clear = MenuRange.of(GRID, 9);
      List<Integer> inventorySlots = MenuRange.of(INVENTORY, 36);
      List<Integer> sources = new ArrayList<>(inventorySlots);
      for (int i = SIDE; i < slots.size(); i++) sources.add(i);
      return TransferPlanner.plan(slots::get, null, carried, new Request(requirements, clear, sources, inventorySlots, max, TransferPlanner.DEFAULT_CLICK_BUDGET));
    }

    void apply(List<Click> clicks) {
      for (Click click : clicks) carried = RealClicks.click(slots, carried, click.slot(), click.button());
    }

    List<ItemStack> census() {
      List<ItemStack> stacks = new ArrayList<>();
      for (Slot slot : slots) stacks.add(slot.getItem().copy());
      stacks.add(carried.copy());
      return TransferPlanner.census(stacks);
    }

    TestMenu menu() {
      return new TestMenu(this);
    }
  }

  /** Minimal menu over the rig's real slots for the executor; clicks use the same real slot calls. */
  private static final class TestMenu extends net.minecraft.world.inventory.AbstractContainerMenu {
    private final Rig rig;

    TestMenu(Rig rig) {
      super(null, 0);
      this.rig = rig;
      for (Slot slot : rig.slots) addSlot(slot);
    }

    void clickReal(int slot, int button) {
      rig.carried = RealClicks.click(rig.slots, rig.carried, slot, button);
      setCarried(rig.carried);
    }

    @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
  }

  /** The PICKUP branch of vanilla AbstractContainerMenu.doClick, using the real slot methods with no player. */
  static final class RealClicks {
    static ItemStack click(List<Slot> slots, ItemStack carried, int index, int button) {
      Slot slot = slots.get(index);
      ItemStack clicked = slot.getItem();
      boolean primary = button == 0;
      if (clicked.isEmpty()) {
        if (!carried.isEmpty()) {
          carried = slot.safeInsert(carried, primary ? carried.getCount() : 1);
        }
      } else if (slot.mayPickup(null)) {
        if (carried.isEmpty()) {
          int amount = primary ? clicked.getCount() : (clicked.getCount() + 1) / 2;
          Optional<ItemStack> taken = slot.tryRemove(amount, Integer.MAX_VALUE, null);
          if (taken.isPresent()) {
            carried = taken.get();
            slot.onTake(null, carried);
          }
        } else if (slot.mayPlace(carried)) {
          if (ItemStack.isSameItemSameComponents(clicked, carried)) {
            carried = slot.safeInsert(carried, primary ? carried.getCount() : 1);
          } else if (carried.getCount() <= slot.getMaxStackSize(carried)) {
            ItemStack previous = clicked;
            slot.setByPlayer(carried);
            carried = previous;
          }
        } else if (ItemStack.isSameItemSameComponents(clicked, carried)) {
          ItemStack held = carried;
          slot.tryRemove(clicked.getCount(), held.getMaxStackSize() - held.getCount(), null).ifPresent(taken -> {
            held.grow(taken.getCount());
            slot.onTake(null, taken);
          });
        }
      }
      slot.setChanged();
      return carried;
    }
  }

  private static final class MenuRange {
    static List<Integer> of(int start, int count) {
      List<Integer> list = new ArrayList<>();
      for (int i = 0; i < count; i++) list.add(start + i);
      return list;
    }
  }

  /** Exposes the real side inventory slot factory, as CraftingSideInventoryRegression does. */
  private static final class SideHarness extends SideInventoryContainer<BlockEntity> {
    SideHarness() {
      super(null, 0, null, null, 0, 0, 1);
    }

    Slot make(IItemHandler handler, int index) {
      return createSlot(handler, index, 0, 0);
    }
  }

  private static IItemHandler bridge(ResourceHandler<ItemResource> storage) throws Exception {
    Class<?> type = Class.forName(SideInventoryContainer.class.getName() + "$TransferItemHandler");
    Constructor<?> constructor = type.getDeclaredConstructor(ResourceHandler.class);
    constructor.setAccessible(true);
    return (IItemHandler) constructor.newInstance(storage);
  }
}
