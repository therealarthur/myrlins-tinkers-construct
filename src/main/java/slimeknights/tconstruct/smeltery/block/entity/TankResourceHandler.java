package slimeknights.tconstruct.smeltery.block.entity;

import static slimeknights.tconstruct.library.fluid.FluidActions.EXECUTE;

import java.util.function.IntSupplier;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.tconstruct.library.fluid.FluidTankBase;
import slimeknights.tconstruct.smeltery.block.entity.tank.CastingFluidHandler;
import slimeknights.tconstruct.smeltery.block.entity.tank.SmelteryTank;

/** Bridges Tinkers' legacy IFluidHandler tanks to NeoForge's ResourceHandler fluid capability. */
public class TankResourceHandler implements ResourceHandler<FluidResource> {
  private final IFluidHandler tank;
  private final Journal journal = new Journal();

  public TankResourceHandler(IFluidHandler tank) {
    this.tank = tank;
  }

  @Override
  public int size() {
    return tank.getTanks();
  }

  @Override
  public FluidResource getResource(int index) {
    FluidStack stack = tank.getFluidInTank(index);
    return stack.isEmpty() ? FluidResource.EMPTY : FluidResource.of(stack);
  }

  @Override
  public long getAmountAsLong(int index) {
    return tank.getFluidInTank(index).getAmount();
  }

  @Override
  public long getCapacityAsLong(int index, FluidResource resource) {
    return tank.getTankCapacity(index);
  }

  @Override
  public boolean isValid(int index, FluidResource resource) {
    return !resource.isEmpty() && tank.isFluidValid(index, resource.toStack(1));
  }

  @Override
  public int insert(int index, FluidResource resource, int maxAmount, TransactionContext transaction) {
    if (resource.isEmpty() || maxAmount <= 0) {
      return 0;
    }
    journal.updateSnapshots(transaction);
    return transfer(() -> tank.fill(resource.toStack(maxAmount), EXECUTE));
  }

  @Override
  public int extract(int index, FluidResource resource, int maxAmount, TransactionContext transaction) {
    if (resource.isEmpty() || maxAmount <= 0) {
      return 0;
    }
    journal.updateSnapshots(transaction);
    return transfer(() -> tank.drain(resource.toStack(maxAmount), EXECUTE).getAmount());
  }

  /** Runs a tank mutation without publishing it until the transaction commits. */
  private int transfer(IntSupplier action) {
    FluidTankBase.beginDeferredSync();
    try {
      return action.getAsInt();
    } finally {
      FluidTankBase.endDeferredSync();
    }
  }

  private class Journal extends SnapshotJournal<Object> {
    @Override
    protected Object createSnapshot() {
      if (tank instanceof CastingFluidHandler casting) {
        return casting.createSnapshot();
      }
      if (tank instanceof SmelteryTank<?> smeltery) {
        return smeltery.createSnapshot();
      }
      FluidStack[] fluids = new FluidStack[tank.getTanks()];
      for (int i = 0; i < fluids.length; i++) {
        fluids[i] = tank.getFluidInTank(i).copy();
      }
      return fluids;
    }

    @Override
    protected void revertToSnapshot(Object snapshot) {
      if (tank instanceof CastingFluidHandler casting && snapshot instanceof CastingFluidHandler.State state) {
        casting.restoreSnapshot(state);
        return;
      }
      if (tank instanceof SmelteryTank<?> smeltery && snapshot instanceof SmelteryTank.State state) {
        smeltery.restoreSnapshot(state);
        return;
      }
      FluidStack[] fluids = (FluidStack[])snapshot;
      // drain/fill here runs while the transaction is closing and fires tank callbacks.
      // Those callbacks cannot ask about the open transaction, and a throw leaves the tank empty.
      if (tank instanceof FluidTank fluidTank && fluids.length == 1) {
        FluidStack restored = fluids[0];
        fluidTank.setFluid(restored.isEmpty() ? FluidStack.EMPTY : restored.copy());
        return;
      }
      for (int i = 0; i < fluids.length; i++) {
        tank.drain(Integer.MAX_VALUE, EXECUTE);
        if (!fluids[i].isEmpty()) {
          tank.fill(fluids[i].copy(), EXECUTE);
        }
      }
    }

    @Override
    protected void onRootCommit(Object originalState) {
      if (tank instanceof FluidTankBase<?> base) {
        base.onContentsChanged();
      }
    }
  }
}
