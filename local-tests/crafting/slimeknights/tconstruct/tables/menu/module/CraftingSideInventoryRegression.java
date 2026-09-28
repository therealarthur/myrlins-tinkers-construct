package slimeknights.tconstruct.tables.menu.module;

import java.lang.reflect.Constructor;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerCopySlot;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.mantle.inventory.EmptyItemHandler;
import slimeknights.mantle.inventory.MultiModuleContainerMenu;
import slimeknights.mantle.inventory.SmartItemHandlerSlot;

/**
 * Exercises the actual side inventory factory, Core refill/WrapperSlot path, and
 * NeoForge transfer implementation. No world, graphical client, or mock menu logic.
 * The private bridge is accessed reflectively to avoid making production API for tests.
 */
public final class CraftingSideInventoryRegression {
  private static int assertions;

  public static void main(String[] args) throws Exception {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();

    demonstratesReleasedSlotFailure();
    vanillaContainerCapability();
    matchingStackRefill();
    partialRefill();
    fullAndDifferentStacks();
    emptySlotMove();
    componentIdentity();
    slotLimits();
    repeatedReadsAndWrites();
    extractionDoesNotRestoreCachedItems();
    insertionOnlyStorage();
    insertionDeniedStorage();
    limitedInsertionStorage();
    limitedExtractionStorage();
    differentItemSwaps();
    rejectedSwapsRollback();
    nativeSlotSelection();

    System.out.println("CRAFTING_SIDE_INVENTORY_REGRESSION PASS assertions=" + assertions);
  }

  private static void demonstratesReleasedSlotFailure() throws Exception {
    ItemStacksResourceHandler storage = storage(16);
    IItemHandler bridge = bridge(storage);
    SideHarness side = new SideHarness();
    side.include(new SmartItemHandlerSlot(bridge, 0, 0, 0));
    ParentHarness menu = new ParentHarness(side);
    ItemStack output = cobble(8);
    check(menu.refill(output), "released slot reports successful refill");
    count(output, 0, "released slot consumes source");
    amount(storage, 16, "released slot does not write target copy back (negative control)");
  }

  private static void matchingStackRefill() throws Exception {
    Fixture fixture = fixture(storage(16));
    check(fixture.slot instanceof ItemHandlerCopySlot, "transfer bridge selects copy-aware slot");
    ItemStack output = cobble(8);
    check(fixture.menu.refill(output), "matching refill transfers");
    count(output, 0, "matching refill consumes exactly output");
    amount(fixture.storage, 24, "matching refill stores output");
    conserved(fixture, output, 24, "matching refill conservation");
  }

  private static void vanillaContainerCapability() throws Exception {
    SimpleContainer inventory = new SimpleContainer(1);
    inventory.setItem(0, cobble(16));
    SideHarness side = new SideHarness();
    Slot slot = side.factory(bridge(VanillaContainerWrapper.of(inventory)));
    side.include(slot);
    ParentHarness menu = new ParentHarness(side);
    ItemStack output = cobble(8);
    check(menu.refill(output), "vanilla container capability accepts refill");
    count(output, 0, "vanilla container consumes exact output");
    count(inventory.getItem(0), 24, "vanilla container backing inventory receives refill");
    check(slot.mayPickup(null), "ordinary vanilla storage allows pickup");
    ItemStack dirt = new ItemStack(Items.DIRT, 12);
    check(slot.mayPlace(dirt), "vanilla container capability allows ordinary item swap");
    slot.set(dirt);
    slot.setChanged();
    check(inventory.getItem(0).is(Items.DIRT), "vanilla container swap changes item");
    count(inventory.getItem(0), 12, "vanilla container swap keeps exact count");
  }

  private static void partialRefill() throws Exception {
    Fixture fixture = fixture(storage(63));
    ItemStack output = cobble(8);
    check(fixture.menu.refill(output), "partial refill transfers");
    amount(fixture.storage, 64, "partial refill caps at item maximum");
    count(output, 7, "partial refill leaves remainder for player inventory");
    conserved(fixture, output, 71, "partial refill conservation");
  }

  private static void fullAndDifferentStacks() throws Exception {
    Fixture fixture = fixture(storage(64));
    ItemStack output = cobble(8);
    check(!fixture.menu.refill(output), "full target declines refill");
    conserved(fixture, output, 72, "full target conservation");
    count(output, 8, "full target leaves source");
    fixture.storage.set(0, ItemResource.of(new ItemStack(Items.DIRT)), 16);
    check(!fixture.menu.refill(output), "different item declines refill");
    count(output, 8, "different target leaves source");
    amount(fixture.storage, 16, "different target stays unchanged");
    check(fixture.storage.getResource(0).matches(new ItemStack(Items.DIRT)), "different target identity retained");
  }

  private static void emptySlotMove() throws Exception {
    Fixture fixture = fixture(storage(0));
    ItemStack output = cobble(8);
    check(!fixture.menu.refill(output), "refill skips empty target");
    check(fixture.menu.move(output), "move fills empty target");
    amount(fixture.storage, 8, "empty target stores output");
    count(output, 0, "empty target consumes output");
    conserved(fixture, output, 8, "empty target conservation");
  }

  private static void componentIdentity() throws Exception {
    ItemStack named = cobble(16);
    named.set(DataComponents.CUSTOM_NAME, Component.literal("regression cobblestone"));
    ItemStacksResourceHandler storage = storage(0);
    storage.set(0, ItemResource.of(named), named.getCount());
    Fixture fixture = fixture(storage);
    ItemStack ordinary = cobble(8);
    check(!fixture.menu.refill(ordinary), "different components cannot merge");
    count(ordinary, 8, "different components leave source");
    amount(storage, 16, "different components leave target");
    ItemStack matching = named.copyWithCount(8);
    check(fixture.menu.refill(matching), "same components merge");
    amount(storage, 24, "same components store all output");
    check(storage.getResource(0).matches(named), "custom component retained");
    count(matching, 0, "same components consume output");
  }

  private static void slotLimits() throws Exception {
    ItemStacksResourceHandler limited = new ItemStacksResourceHandler(1) {
      @Override
      protected int getCapacity(int index, ItemResource resource) {
        return resource.isEmpty() ? 16 : Math.min(16, resource.getMaxStackSize());
      }
    };
    limited.set(0, ItemResource.of(cobble(1)), 15);
    Fixture fixture = fixture(limited);
    check(fixture.slot.getMaxStackSize(cobble(8)) == 16, "copy slot preserves Smart slot limit");
    ItemStack output = cobble(8);
    check(fixture.menu.refill(output), "limited target accepts one");
    amount(limited, 16, "limited target remains within capacity");
    count(output, 7, "limited target leaves seven");
    conserved(fixture, output, 23, "limited target conservation");
  }

  private static void repeatedReadsAndWrites() throws Exception {
    Fixture fixture = fixture(storage(16));
    ItemStack firstRead = fixture.slot.getItem();
    ItemStack lastRead = fixture.slot.getItem();
    check(firstRead != lastRead, "bridge returns separate stack copies");
    lastRead.grow(2);
    fixture.slot.setChanged();
    amount(fixture.storage, 18, "latest read copy is persisted");
    fixture.slot.setChanged();
    amount(fixture.storage, 18, "repeat dirty notification does not duplicate");
    check(fixture.menu.refill(cobble(3)), "second operation refreshes copy");
    amount(fixture.storage, 21, "second operation uses current contents");
    fixture.slot.set(cobble(9));
    fixture.slot.setChanged();
    amount(fixture.storage, 9, "explicit set followed by dirty notification stays exact");
    // A normal menu read refreshes the cached value after another consumer changes storage.
    fixture.storage.set(0, ItemResource.of(cobble(1)), 12);
    count(fixture.slot.getItem(), 12, "next read observes external storage change");
    fixture.slot.setChanged();
    amount(fixture.storage, 12, "refreshed copy preserves external change");
  }

  private static void extractionDoesNotRestoreCachedItems() throws Exception {
    Fixture fixture = fixture(storage(16));
    fixture.slot.getItem();
    count(fixture.slot.remove(5), 5, "partial extraction returns requested items");
    amount(fixture.storage, 11, "partial extraction updates target");
    fixture.slot.setChanged();
    amount(fixture.storage, 11, "dirty notification after removal does not restore stale stack");
    count(fixture.slot.remove(64), 11, "oversized extraction only returns existing items");
    amount(fixture.storage, 0, "oversized extraction empties target");
    fixture.slot.setChanged();
    amount(fixture.storage, 0, "dirty notification after emptying does not restore items");
    check(fixture.slot.mayPickup(null), "empty slot retains Smart mayPickup behavior");
  }

  private static void nativeSlotSelection() {
    SideHarness side = new SideHarness();
    Slot slot = side.factory(EmptyItemHandler.INSTANCE);
    check(slot.getClass() == SmartItemHandlerSlot.class, "native legacy handler keeps existing slot type");
  }

  private static void insertionOnlyStorage() throws Exception {
    ItemStacksResourceHandler storage = restricted(0, Integer.MAX_VALUE, false);
    Fixture fixture = fixture(storage);
    check(!fixture.slot.mayPickup(null), "insertion-only storage rejects pickup");
    check(fixture.slot.mayPlace(cobble(8)), "insertion-only storage permits matching refill");
    ItemStack output = cobble(8);
    check(fixture.menu.refill(output), "insertion-only refill transfers");
    amount(storage, 24, "insertion-only refill adds only delta");
    count(output, 0, "insertion-only refill consumes correct source");
    conserved(fixture, output, 24, "insertion-only refill cannot duplicate original contents");
    fixture.slot.set(ItemStack.EMPTY);
    amount(storage, 24, "refused direct extraction cannot commit a partial change");
  }

  private static void insertionDeniedStorage() throws Exception {
    ItemStacksResourceHandler storage = restricted(Integer.MAX_VALUE, 0, false);
    Fixture fixture = fixture(storage);
    ItemStack output = cobble(8);
    check(!fixture.slot.mayPlace(output), "insert-denied storage rejects placement");
    check(fixture.slot.getMaxStackSize(output) == 16, "insert-denied limit includes existing stack only");
    check(!fixture.menu.refill(output), "insert-denied refill declines");
    amount(storage, 16, "insert-denied refill preserves destination");
    count(output, 8, "insert-denied refill preserves output");
    storage.set(0, ItemResource.EMPTY, 0);
    check(!fixture.slot.mayPlace(output), "empty insert-denied storage rejects placement");
    check(!fixture.menu.move(output), "empty insert-denied storage cannot report zero-size success");
    count(output, 8, "empty insert-denied storage preserves output");
    amount(storage, 0, "empty insert-denied storage stays empty");
  }

  private static void limitedInsertionStorage() throws Exception {
    ItemStacksResourceHandler storage = restricted(Integer.MAX_VALUE, 2, false);
    Fixture fixture = fixture(storage);
    ItemStack output = cobble(8);
    check(fixture.slot.getMaxStackSize(output) == 18, "insertion limit adds accepted delta to current count");
    check(fixture.menu.refill(output), "bounded insertion accepts partial output");
    amount(storage, 18, "bounded insertion stores exactly accepted amount");
    count(output, 6, "bounded insertion retains unaccepted output");
    conserved(fixture, output, 24, "bounded insertion conservation");
    storage.set(0, ItemResource.EMPTY, 0);
    output = cobble(8);
    check(fixture.menu.move(output), "bounded insertion accepts into empty target");
    amount(storage, 2, "empty bounded target stores exactly accepted amount");
    count(output, 6, "empty bounded target retains remainder");
  }

  private static void limitedExtractionStorage() throws Exception {
    ItemStacksResourceHandler storage = restricted(4, Integer.MAX_VALUE, false);
    Fixture fixture = fixture(storage);
    check(!fixture.slot.mayPickup(null), "limited extraction rejects unsafe legacy whole-stack quick-move");
    ItemStack output = cobble(8);
    check(fixture.menu.refill(output), "limited extraction does not obstruct insertion");
    amount(storage, 24, "refill does not duplicate unextracted contents");
    fixture.slot.getItem();
    ItemStack extracted = fixture.slot.remove(7);
    count(extracted, 4, "direct removal returns actually extracted count");
    amount(storage, 20, "direct removal decrements only extracted count");
    fixture.slot.setChanged();
    amount(storage, 20, "dirty notification after bounded extraction cannot restore cache");
    fixture.slot.set(new ItemStack(Items.DIRT, 8));
    amount(storage, 20, "incomplete swap extraction is rolled back");
    check(storage.getResource(0).matches(cobble(1)), "failed swap retains original resource");
  }

  private static void differentItemSwaps() throws Exception {
    Fixture fixture = fixture(storage(16));
    ItemStack dirt = new ItemStack(Items.DIRT, 24);
    check(fixture.slot.mayPlace(dirt), "ordinary different-item swap remains permitted");
    check(fixture.slot.getMaxStackSize(dirt) == 64, "swap capacity simulates removal before insertion");
    amount(fixture.storage, 16, "swap simulation preserves current contents");
    check(fixture.storage.getResource(0).matches(cobble(1)), "swap simulation rolls back original item");
    fixture.slot.set(dirt);
    fixture.slot.setChanged();
    amount(fixture.storage, 24, "swap sets exact replacement count");
    check(fixture.storage.getResource(0).matches(dirt), "swap installs different item");
  }

  private static void rejectedSwapsRollback() throws Exception {
    ItemStacksResourceHandler storage = restricted(0, Integer.MAX_VALUE, false);
    Fixture fixture = fixture(storage);
    ItemStack dirt = new ItemStack(Items.DIRT, 24);
    check(!fixture.slot.mayPlace(dirt), "extract-denied storage rejects item swap");
    check(fixture.slot.getMaxStackSize(dirt) == 0, "extract-denied swap capacity is zero");
    fixture.slot.set(dirt);
    amount(storage, 16, "extract-denied direct swap preserves count");
    check(storage.getResource(0).matches(cobble(1)), "extract-denied direct swap preserves item");
    storage = restricted(Integer.MAX_VALUE, Integer.MAX_VALUE, true);
    fixture = fixture(storage);
    check(!fixture.slot.mayPlace(dirt), "insert-denied replacement rejects swap");
    check(fixture.slot.getMaxStackSize(dirt) == 0, "insert-denied replacement capacity is zero");
    fixture.slot.set(dirt);
    amount(storage, 16, "failed replacement restores extracted count");
    check(storage.getResource(0).matches(cobble(1)), "failed replacement restores extracted resource");
  }

  private static ItemStacksResourceHandler restricted(int extractionLimit, int insertionLimit, boolean denyDirt) {
    ItemStacksResourceHandler storage = new ItemStacksResourceHandler(1) {
      @Override
      public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return super.extract(index, resource, Math.min(amount, extractionLimit), transaction);
      }

      @Override
      public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return denyDirt && resource.matches(new ItemStack(Items.DIRT))
          ? 0 : super.insert(index, resource, Math.min(amount, insertionLimit), transaction);
      }
    };
    storage.set(0, ItemResource.of(cobble(1)), 16);
    return storage;
  }

  private static Fixture fixture(ItemStacksResourceHandler storage) throws Exception {
    SideHarness side = new SideHarness();
    Slot slot = side.factory(bridge(storage));
    side.include(slot);
    return new Fixture(storage, slot, new ParentHarness(side));
  }

  private static IItemHandler bridge(ResourceHandler<ItemResource> storage) throws Exception {
    Class<?> type = Class.forName(SideInventoryContainer.class.getName() + "$TransferItemHandler");
    Constructor<?> constructor = type.getDeclaredConstructor(ResourceHandler.class);
    constructor.setAccessible(true);
    return (IItemHandler) constructor.newInstance(storage);
  }

  private static ItemStacksResourceHandler storage(int count) {
    ItemStacksResourceHandler storage = new ItemStacksResourceHandler(1);
    if (count > 0) {
      storage.set(0, ItemResource.of(cobble(1)), count);
    }
    return storage;
  }

  private static ItemStack cobble(int count) {
    return new ItemStack(Items.COBBLESTONE, count);
  }

  private static void count(ItemStack stack, int expected, String message) {
    check(stack.getCount() == expected, message + ": expected " + expected + ", got " + stack.getCount());
  }

  private static void amount(ItemStacksResourceHandler storage, int expected, String message) {
    check(storage.getAmountAsInt(0) == expected, message + ": expected " + expected + ", got " + storage.getAmountAsInt(0));
  }

  private static void conserved(Fixture fixture, ItemStack output, int expected, String message) {
    check(fixture.storage.getAmountAsInt(0) + output.getCount() == expected, message);
  }

  private static void check(boolean condition, String message) {
    assertions++;
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  private record Fixture(ItemStacksResourceHandler storage, Slot slot, ParentHarness menu) {}

  private static final class SideHarness extends SideInventoryContainer<BlockEntity> {
    private SideHarness() {
      super(null, 0, null, null, 0, 0, 1);
    }

    private Slot factory(IItemHandler handler) {
      return createSlot(handler, 0, 0, 0);
    }

    private void include(Slot slot) {
      addSlot(slot);
    }
  }

  private static final class ParentHarness extends MultiModuleContainerMenu<BlockEntity> {
    private final SideHarness side;

    private ParentHarness(SideHarness side) {
      super(null, 1, null, null);
      this.side = side;
      addSubContainer(side, false);
    }

    private boolean refill(ItemStack stack) {
      return !refillContainer(stack, side);
    }

    private boolean move(ItemStack stack) {
      return !moveToContainer(stack, side);
    }
  }
}
