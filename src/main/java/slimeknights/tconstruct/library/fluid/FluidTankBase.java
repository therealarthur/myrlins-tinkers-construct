package slimeknights.tconstruct.library.fluid;

import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import slimeknights.mantle.block.entity.MantleBlockEntity;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.smeltery.network.FluidUpdatePacket;

public class FluidTankBase<T extends MantleBlockEntity> extends FluidTank {

  /** While positive, content callbacks wait until the surrounding fluid transaction commits. */
  private static final ThreadLocal<Integer> DEFERRED_SYNC = ThreadLocal.withInitial(() -> 0);

  protected T parent;

  /** Skips client sync until {@link #endDeferredSync()} and a later {@link #onContentsChanged()} after commit. */
  public static void beginDeferredSync() {
    DEFERRED_SYNC.set(DEFERRED_SYNC.get() + 1);
  }

  /** Ends one {@link #beginDeferredSync()} scope. */
  public static void endDeferredSync() {
    int depth = DEFERRED_SYNC.get() - 1;
    if (depth <= 0) {
      DEFERRED_SYNC.remove();
    } else {
      DEFERRED_SYNC.set(depth);
    }
  }

  public FluidTankBase(int capacity, T parent) {
    super(capacity);
    this.parent = parent;
  }

  @Override
  public void setFluid(FluidStack stack) {
    super.setFluid(FluidStackNbt.registeredCopy(stack));
  }

  // override to fix bug with onContentsChanged during fill
  @Override
  public int fill(FluidStack resource, FluidAction action) {
    if (resource.isEmpty() || !isFluidValid(resource)) {
      return 0;
    }
    if (action.simulate()) {
      if (fluid.isEmpty()) {
        return Math.min(capacity, resource.getAmount());
      }
      if (!FluidStack.isSameFluidSameComponents(fluid, resource)) {
        return 0;
      }
      return Math.min(capacity - fluid.getAmount(), resource.getAmount());
    }
    if (fluid.isEmpty()) {
      // FIX: the Forge implementation returns fluid.getAmount() here, which may be wrong if the fluid gets changed during onContentsChanged()
      // we instead use a local variable for the amount filled to guarantee its accurate
      int filled = Math.min(capacity, resource.getAmount());
      fluid = FluidStackNbt.registeredCopy(resource).copyWithAmount(filled);
      onContentsChanged();
      return filled;
    }
    if (!FluidStack.isSameFluidSameComponents(fluid, resource)) {
      return 0;
    }
    int filled = capacity - fluid.getAmount();

    if (resource.getAmount() < filled) {
      fluid.grow(resource.getAmount());
      filled = resource.getAmount();
    } else {
      fluid.setAmount(capacity);
    }
    if (filled > 0) {
      onContentsChanged();
    }
    return filled;
  }

  @Override
  public FluidStack drain(int maxDrain, FluidAction action) {
    FluidStack drained = super.drain(maxDrain, action);
    // A zero-amount stack still carries its fluid type. Dropping or copying that stack
    // can revive lava that was already burned, so an emptied tank must hold the empty singleton.
    if (action.execute() && this.fluid != FluidStack.EMPTY && this.fluid.getAmount() <= 0) {
      this.fluid = FluidStack.EMPTY;
    }
    return drained;
  }

  @Override
  public void onContentsChanged() {
    // A simulated insert still calls this. Syncing here publishes fluid the transaction is about to roll back.
    if (DEFERRED_SYNC.get() > 0) {
      return;
    }
    if (parent instanceof IFluidTankUpdater updater) {
      updater.onTankContentsChanged();
    }

    parent.setChangedFast();
    Level level = parent.getLevel();
    if (level != null && !level.isClientSide()) {
      TinkerNetwork.getInstance().sendToClientsAround(new FluidUpdatePacket(parent.getBlockPos(), this.getFluid()), level, parent.getBlockPos());
    }
  }
}
