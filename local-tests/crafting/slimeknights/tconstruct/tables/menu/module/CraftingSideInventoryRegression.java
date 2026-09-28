package slimeknights.tconstruct.tables.menu.module;

import java.lang.reflect.Constructor;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerCopySlot;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
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
    matchingStackRefill();
    partialRefill();
    fullAndDifferentStacks();
    emptySlotMove();
    componentIdentity();
    slotLimits();
    repeatedReadsAndWrites();
    extractionDoesNotRestoreCachedItems();
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
