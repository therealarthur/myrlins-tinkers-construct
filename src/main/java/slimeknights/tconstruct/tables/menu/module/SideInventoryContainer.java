package slimeknights.tconstruct.tables.menu.module;

import lombok.Getter;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerCopySlot;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import slimeknights.mantle.inventory.EmptyItemHandler;
import slimeknights.mantle.inventory.BaseContainerMenu;
import slimeknights.mantle.inventory.SmartItemHandlerSlot;
import slimeknights.tconstruct.smeltery.block.entity.controller.HeatingStructureBlockEntity;
import slimeknights.tconstruct.tables.block.entity.chest.AbstractChestBlockEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SideInventoryContainer<TILE extends BlockEntity> extends BaseContainerMenu<TILE> {

  @Getter
  private final int columns;
  @Getter
  private final int slotCount;
  protected final IItemHandler itemHandler;

  public SideInventoryContainer(MenuType<?> containerType, int windowId, Inventory inv, @Nullable TILE tile, int x, int y, int columns) {
    this(containerType, windowId, inv, tile, null, x, y, columns);
  }

  public SideInventoryContainer(MenuType<?> containerType, int windowId, Inventory inv, @Nullable TILE tile, @Nullable Direction inventoryDirection, int x, int y, int columns) {
    super(containerType, windowId, inv, tile);

    // must have a TE
    if (tile == null) {
      this.itemHandler = EmptyItemHandler.INSTANCE;
    } else if (tile instanceof HeatingStructureBlockEntity structure) {
      this.itemHandler = structure.getMeltingInventory();
    } else if (tile instanceof AbstractChestBlockEntity chest) {
      this.itemHandler = chest.getItemHandler();
    } else {
      var resourceHandler = tile.getLevel() == null ? null : tile.getLevel().getCapability(Capabilities.Item.BLOCK, tile.getBlockPos(), inventoryDirection);
      this.itemHandler = resourceHandler == null ? EmptyItemHandler.INSTANCE : new TransferItemHandler(resourceHandler);
    }

    // slot properties
    IItemHandler handler = itemHandler;
    this.slotCount = handler.getSlots();
    this.columns = Math.max(1, columns);
    int rows = this.slotCount / this.columns;
    if (this.slotCount % this.columns != 0) {
      rows++;
    }

    // add slots
    int index = 0;
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < this.columns; c++) {
        if (index >= this.slotCount) {
          break;
        }

        this.addSlot(this.createSlot(handler, index, x + c * 18, y + r * 18));
        index++;
      }
    }
  }

  /**
   * Creates a slot for this inventory
   * @param itemHandler  Item handler
   * @param index        Slot index
   * @param x            Slot X position
   * @param y            Slot Y position
   * @return  Inventory slot
   */
  protected Slot createSlot(IItemHandler itemHandler, int index, int x, int y) {
    // Transfer views return detached stacks. Menu refill code mutates getItem() then
    // calls setChanged(), so those slots need NeoForge's copy-aware writeback.
    return itemHandler instanceof TransferItemHandler transfer
      ? new TransferItemHandlerSlot(transfer, index, x, y)
      : new SmartItemHandlerSlot(itemHandler, index, x, y);
  }

  /** Keeps legacy menu operations within the transfer handler's actual accepted amounts. */
  private static class TransferItemHandlerSlot extends ItemHandlerCopySlot {
    private final TransferItemHandler handler;
    private final int handlerSlot;

    private TransferItemHandlerSlot(TransferItemHandler handler, int index, int x, int y) {
      super(new SmartItemHandlerSlot(handler, index, x, y));
      this.handler = handler;
      this.handlerSlot = index;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
      if (stack.isEmpty() || !handler.isItemValid(handlerSlot, stack)) {
        return false;
      }
      ItemStack current = handler.getStackInSlot(handlerSlot);
      int existing = ItemStack.isSameItemSameComponents(current, stack) ? current.getCount() : 0;
      return handler.getInsertLimit(handlerSlot, stack) > existing;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
      return handler.getInsertLimit(handlerSlot, stack);
    }

    @Override
    public boolean mayPickup(Player player) {
      ItemStack current = handler.getStackInSlot(handlerSlot);
      // Legacy quick-move assumes any decrement of the displayed stack can be committed.
      // Handlers that only allow extracting part of it cannot safely satisfy that contract.
      return current.isEmpty() || handler.extractItem(handlerSlot, current.getCount(), true).getCount() == current.getCount();
    }

    @Override
    public ItemStack remove(int amount) {
      ItemStack extracted = handler.extractItem(handlerSlot, amount, false);
      clearCachedReturnStack();
      return extracted;
    }
  }
  /** Modifiable bridge for NeoForge transfer handlers, needed because SlotItemHandler#set requires IItemHandlerModifiable. */
  private static class TransferItemHandler implements IItemHandlerModifiable {
    private final ResourceHandler<ItemResource> handler;
    private final IItemHandler view;

    private TransferItemHandler(ResourceHandler<ItemResource> handler) {
      this.handler = handler;
      this.view = IItemHandler.of(handler);
    }

    @Override
    public int getSlots() {
      return view.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
      return view.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
      return view.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
      return view.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
      return view.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
      return view.isItemValid(slot, stack);
    }

    /** Simulates the largest resulting stack, including removal for an ordinary item swap. */
    private int getInsertLimit(int slot, ItemStack stack) {
      if (stack.isEmpty()) {
        return 0;
      }
      ItemResource incoming = ItemResource.of(stack);
      ItemResource current = handler.getResource(slot);
      int currentAmount = handler.getAmountAsInt(slot);
      boolean sameResource = incoming.equals(current);
      int capacity = Math.min(stack.getMaxStackSize(), handler.getCapacityAsInt(slot, incoming));
      if (!isItemValid(slot, stack)) {
        return sameResource ? Math.min(currentAmount, capacity) : 0;
      }
      try (Transaction transaction = Transaction.open(null)) {
        if (sameResource) {
          int room = Math.max(0, capacity - currentAmount);
          int inserted = room == 0 ? 0 : handler.insert(slot, incoming, room, transaction);
          return Math.min(capacity, currentAmount + inserted);
        }
        // Menu swaps replace the old item. Test extraction and insertion in one aborted
        // transaction so ordinary chest swaps work without assuming either will succeed.
        if (!current.isEmpty() && currentAmount > 0
          && handler.extract(slot, current, currentAmount, transaction) != currentAmount) {
          return 0;
        }
        return capacity <= 0 ? 0 : handler.insert(slot, incoming, capacity, transaction);
      }
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
      try (Transaction transaction = Transaction.open(null)) {
        ItemResource current = handler.getResource(slot);
        int currentAmount = handler.getAmountAsInt(slot);
        ItemResource replacement = ItemResource.of(stack);
        if (current.equals(replacement)) {
          int change = stack.getCount() - currentAmount;
          // Refilling a stack must not extract and reinsert its existing contents.
          if (change > 0 && handler.insert(slot, replacement, change, transaction) != change) {
            return;
          }
          if (change < 0 && handler.extract(slot, current, -change, transaction) != -change) {
            return;
          }
          transaction.commit();
          return;
        }
        if (!current.isEmpty() && currentAmount > 0) {
          if (handler.extract(slot, current, currentAmount, transaction) != currentAmount) {
            return;
          }
        }
        if (!stack.isEmpty()) {
          int inserted = handler.insert(slot, replacement, stack.getCount(), transaction);
          if (inserted != stack.getCount()) {
            return;
          }
        }
        transaction.commit();
      }
    }
  }
}
