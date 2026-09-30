package slimeknights.tconstruct.tables.menu;

import lombok.Getter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import slimeknights.mantle.util.sync.LambdaDataSlot;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.block.entity.inventory.LazyResultContainer;
import slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity;
import slimeknights.tconstruct.tables.menu.slot.LazyResultSlot;

import javax.annotation.Nullable;

public class PartBuilderContainerMenu extends TabbedContainerMenu<PartBuilderBlockEntity> {
  // slots
  @Getter
  private final Slot patternSlot;
  @Getter
  private final Slot inputSlot;
  @Getter
  private final LazyResultSlot outputSlot;

  public PartBuilderContainerMenu(int windowIdIn, Inventory playerInventoryIn, @Nullable PartBuilderBlockEntity partBuilderTileEntity) {
    super(TinkerTables.partBuilderContainer.get(), windowIdIn, playerInventoryIn, partBuilderTileEntity);

    // unfortunately, nothing works with no tile
    if (tile != null) {
      // slots
      this.addSlot(this.outputSlot = new LazyResultSlot(tile.getCraftingResult(), 148, 42));
      // inputs
      this.addSlot(this.patternSlot = new PatternSlot(tile, 8, 43));
      this.addSlot(this.inputSlot = new MaterialSlot(tile, PartBuilderBlockEntity.MATERIAL_SLOT, 29, 43));

      // other inventories
      this.addChestSideInventory();
      this.addInventorySlots();

      // listen for the button to change in the tile
      this.addDataSlot(new LambdaDataSlot(-1, tile::getSelectedIndex, i -> {
        tile.selectRecipe(i);
        this.updateScreen();
      }));
      // update for the first time
      tile.syncButtons(playerInventoryIn.player);
      this.updateScreen();
    } else {
      this.patternSlot = null;
      this.inputSlot = null;
      this.outputSlot = null;
    }
  }

  public PartBuilderContainerMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
    this(id, inv, getTileEntityFromBuf(buf, PartBuilderBlockEntity.class));
  }

  @Override
  protected int getInventoryYOffset() {
    return 102;
  }

  @Override
  public void slotsChanged(Container inventoryIn) {}

  /**
   * Called when a pattern button is pressed
   */
  @Override
  public boolean clickMenuButton(Player playerIn, int id) {
    // no letting ghosts choose patterns
    if (playerIn.isSpectator()) {
      return false;
    }
    if (id >= 0 && tile != null) {
      tile.selectRecipe(id);
    }
    return true;
  }

  @Override
  public boolean canTakeItemForPickAll(ItemStack stack, Slot slotIn) {
    return slotIn != this.outputSlot && super.canTakeItemForPickAll(stack, slotIn);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (this.outputSlot != null && index == this.outputSlot.index) {
      return this.quickMoveResult(player);
    }
    if (index < 0 || index >= this.slots.size()) {
      return ItemStack.EMPTY;
    }
    Slot slot = this.slots.get(index);
    int countBefore = slot.hasItem() ? slot.getItem().getCount() : 0;
    ItemStack moved = super.quickMoveStack(player, index);
    // The click handler repeats while the source slot still holds the same item.
    // Stop when this transfer did not actually remove any of that stack.
    if (!moved.isEmpty() && slot.hasItem() && slot.getItem().getCount() >= countBefore && ItemStack.isSameItem(slot.getItem(), moved)) {
      return ItemStack.EMPTY;
    }
    return moved;
  }

  /**
   * Shift-click every part the inputs can pay for.
   * The click handler repeats this method while it returns the same item. The client copy of the
   * recipe still matches after the material is gone, so that repeat painted a ghost stack and the
   * server correction snapped it back. Craft here and return empty so the repeat runs once.
   */
  private ItemStack quickMoveResult(Player player) {
    if (this.tile == null || !this.outputSlot.hasItem()) {
      return ItemStack.EMPTY;
    }
    if (player.level().isClientSide()) {
      return ItemStack.EMPTY;
    }

    for (int guard = 0; guard < 64 && this.outputSlot.hasItem(); guard++) {
      ItemStack result = this.outputSlot.getItem().copy();
      ItemStack materialBefore = this.tile.getItem(PartBuilderBlockEntity.MATERIAL_SLOT).copy();
      ItemStack patternBefore = this.tile.getItem(PartBuilderBlockEntity.PATTERN_SLOT).copy();
      this.tile.onCraft(player, result, result.getCount());
      boolean consumed = !ItemStack.matches(materialBefore, this.tile.getItem(PartBuilderBlockEntity.MATERIAL_SLOT))
                         || !ItemStack.matches(patternBefore, this.tile.getItem(PartBuilderBlockEntity.PATTERN_SLOT));
      if (!consumed) {
        break;
      }

      int countBeforeMove = result.getCount();
      if (!this.subContainers.isEmpty()) {
        this.refillAnyContainer(result, this.subContainers);
      }
      this.moveToPlayerInventory(result);
      if (!result.isEmpty() && !this.subContainers.isEmpty()) {
        this.moveToAnyContainer(result, this.subContainers);
      }
      boolean inserted = result.getCount() != countBeforeMove;
      if (!result.isEmpty()) {
        player.drop(result, false);
      }
      this.tile.getCraftingResult().clearContent();
      if (!inserted) {
        break;
      }
    }
    this.tile.getCraftingResult().clearContent();
    return ItemStack.EMPTY;
  }

  /** Slot to update recipe on change */
  private static class PartBuilderSlot extends Slot {
    private final LazyResultContainer craftResult;
    public PartBuilderSlot(PartBuilderBlockEntity tile, int index, int xPosition, int yPosition) {
      super(tile, index, xPosition, yPosition);
      craftResult = tile.getCraftingResult();
    }

    @Override
    public void setChanged() {
      craftResult.clearContent();
      super.setChanged();
    }
  }

  /** Slot for the material, which wants to force a screen update */
  private class MaterialSlot extends PartBuilderSlot {
    public MaterialSlot(PartBuilderBlockEntity tile, int index, int xPosition, int yPosition) {
      super(tile, index, xPosition, yPosition);
    }

    @Override
    public void setChanged() {
      super.setChanged();
      updateScreen(); // no other good way to detect stack size decreasing, e.g. on right click
    }
  }

  /**
   * Slot for the pattern, updates buttons on change
   */
  private static class PatternSlot extends PartBuilderSlot {
    private PatternSlot(PartBuilderBlockEntity tile, int x, int y) {
      super(tile, PartBuilderBlockEntity.PATTERN_SLOT, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
      return stack.is(TinkerTags.Items.PATTERNS);
    }
  }
}
