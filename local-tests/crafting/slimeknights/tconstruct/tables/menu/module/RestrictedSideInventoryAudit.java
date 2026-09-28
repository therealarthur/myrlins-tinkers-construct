package slimeknights.tconstruct.tables.menu.module;

import java.lang.reflect.Constructor;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.mantle.inventory.MultiModuleContainerMenu;

/** Negative controls: a successful run confirms known restricted-handler defects, not correctness. */
public final class RestrictedSideInventoryAudit {
  public static void main(String[] args) throws Exception {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();

    var deniedExtraction = storage(0, false);
    bridge(deniedExtraction).setStackInSlot(0, cobble(24));
    expect(deniedExtraction.getAmountAsInt(0), 40, "denied extraction plus full reinsertion duplicates original 16");

    var partialExtraction = storage(4, false);
    bridge(partialExtraction).setStackInSlot(0, cobble(24));
    expect(partialExtraction.getAmountAsInt(0), 36, "partial extraction plus full reinsertion duplicates residual 12");

    var deniedInsertion = storage(Integer.MAX_VALUE, true);
    Side side = new Side();
    side.include(bridge(deniedInsertion));
    Parent parent = new Parent(side);
    ItemStack output = cobble(8);
    expect(parent.refill(output) ? 1 : 0, 1, "refill reports movement despite rejected setter");
    expect(output.getCount(), 0, "rejected setter still consumes source");
    expect(deniedInsertion.getAmountAsInt(0), 16, "rejected insertion transaction restores unchanged storage");
    System.out.println("RESTRICTED_SIDE_INVENTORY_AUDIT EXPECTED_DEFECTS_CONFIRMED controls=3");
  }

  private static ItemStacksResourceHandler storage(int extractionLimit, boolean denyInsertion) {
    var handler = new ItemStacksResourceHandler(1) {
      @Override
      public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return super.extract(index, resource, Math.min(amount, extractionLimit), transaction);
      }

      @Override
      public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return denyInsertion ? 0 : super.insert(index, resource, amount, transaction);
      }
    };
    handler.set(0, ItemResource.of(cobble(1)), 16);
    return handler;
  }

  private static IItemHandlerModifiable bridge(ResourceHandler<ItemResource> storage) throws Exception {
    Class<?> type = Class.forName(SideInventoryContainer.class.getName() + "$TransferItemHandler");
    Constructor<?> constructor = type.getDeclaredConstructor(ResourceHandler.class);
    constructor.setAccessible(true);
    return (IItemHandlerModifiable) constructor.newInstance(storage);
  }

  private static ItemStack cobble(int count) {
    return new ItemStack(Items.COBBLESTONE, count);
  }

  private static void expect(int actual, int expected, String message) {
    if (actual != expected) {
      throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
  }

  private static final class Side extends SideInventoryContainer<BlockEntity> {
    private Side() {
      super(null, 0, null, null, 0, 0, 1);
    }

    private void include(IItemHandler handler) {
      Slot slot = createSlot(handler, 0, 0, 0);
      addSlot(slot);
    }
  }

  private static final class Parent extends MultiModuleContainerMenu<BlockEntity> {
    private final Side side;

    private Parent(Side side) {
      super(null, 1, null, null);
      this.side = side;
      addSubContainer(side, false);
    }

    private boolean refill(ItemStack output) {
      return !refillContainer(output, side);
    }
  }
}
