package slimeknights.tconstruct.smeltery.block.entity;

import static slimeknights.tconstruct.library.fluid.FluidActions.EXECUTE;
import static slimeknights.tconstruct.library.fluid.FluidActions.SIMULATE;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.tconstruct.library.fluid.FluidStackNbt;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.EmptyFluidHandler;
import slimeknights.mantle.block.entity.MantleBlockEntity;
import slimeknights.mantle.fluid.FluidTransferHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.component.SmelteryInputOutputBlockEntity.SmelteryFluidIO;
import slimeknights.tconstruct.smeltery.network.FaucetActivationPacket;

import static slimeknights.tconstruct.smeltery.block.FaucetBlock.FACING;

public class FaucetBlockEntity extends MantleBlockEntity {
  /** amount of MB to extract from the input at a time */
  public static final int PACKET_SIZE = FluidValues.INGOT;
  /** Transfer rate of the faucet */
  public static final int MB_PER_TICK = FluidValues.NUGGET;

  public static final BlockEntityTicker<FaucetBlockEntity> SERVER_TICKER = (level, pos, world, self) -> self.tick();

  private static final String TAG_DRAINED = "drained";
  private static final String TAG_RENDER_FLUID = "render_fluid";
  private static final String TAG_STOP = "stop";
  private static final String TAG_STATE = "state";
  private static final String TAG_LAST_REDSTONE = "lastRedstone";

  /** If true, faucet is currently pouring */
  private FaucetState faucetState = FaucetState.OFF;
  /** If true, redstone told this faucet to stop, so stop when ready */
  private boolean stopPouring = false;
  /** Current fluid in the faucet */
  private FluidStack drained = FluidStack.EMPTY;
  /** Fluid for rendering, used to reduce the number of packets. There is a brief moment where {@link this#drained} is empty but we should be rendering something */
  @Getter
  private FluidStack renderFluid = FluidStack.EMPTY;
  /** Used for pulse detection */
  private boolean lastRedstoneState = false;
  /** Defers redstone state restoration until neighbor signals are stable after load. */
  private boolean checkRedstoneOnNextTick = false;
  /** Restores saved in-flight fluid after load before resuming redstone automation. */
  private boolean restoreDrainedOnNextTick = false;


  public FaucetBlockEntity(BlockPos pos, BlockState state) {
    this(TinkerSmeltery.faucet.get(), pos, state);
  }

  @SuppressWarnings("WeakerAccess")
  protected FaucetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
  }


  /* Fluid handler */

  /**
   * Finds the fluid handler on the given side
   * @param side  Side to check
   * @return  Fluid handler
   */
  private IFluidHandler findFluidHandler(Direction side) {
    assert level != null;
    return findFluidHandler(level, worldPosition, side);
  }

  /** Looks up the fluid handler on {@code side} of {@code origin}, including Tinkers blocks that are not capability-registered. */
  public static IFluidHandler findFluidHandler(net.minecraft.world.level.Level level, BlockPos origin, Direction side) {
    BlockPos target = origin.relative(side);
    Direction targetSide = side.getOpposite();
    var blockEntity = level.getBlockEntity(target);
    if (blockEntity instanceof CastingBlockEntity casting) {
      return casting.getFluidHandler(targetSide);
    }
    if (blockEntity instanceof CastingTankBlockEntity castingTank) {
      return castingTank.getFluidHandler(targetSide);
    }
    if (blockEntity instanceof ChannelBlockEntity channel) {
      return channel.getFluidHandler(targetSide);
    }
    if (blockEntity instanceof ProxyTankBlockEntity proxyTank) {
      return proxyTank.getItemTank();
    }
    if (blockEntity instanceof SmelteryFluidIO fluidIO) {
      return fluidIO.getHandler();
    }
    if (blockEntity instanceof ITankBlockEntity tank) {
      return tank.getTank();
    }
    IFluidHandler handler = FluidTransferHelper.getFluidHandler(level, target, targetSide);
    return handler == null ? EmptyFluidHandler.INSTANCE : handler;
  }

  /**
   * Gets the input fluid handler
   * @return  Input fluid handler
   */
  private IFluidHandler getInputHandler() {
    return findFluidHandler(getBlockState().getValue(FACING).getOpposite());
  }

  /**
   * Gets the output fluid handler
   * @return  Output fluid handler
   */
  private IFluidHandler getOutputHandler() {
    return findFluidHandler(Direction.DOWN);
  }

  /**
   * Called when a neighbor changes to invalidate the cached fluid handler
   * @param neighbor  Neighbor position that changed
   */
  public void neighborChanged(BlockPos neighbor) {
    if (worldPosition.equals(neighbor.above()) || worldPosition.equals(neighbor.relative(getBlockState().getValue(FACING)))) {
      reset();
    }
  }


  /* Data */

  /**
   * Gets whether the faucet is pouring
   * @return True if pouring
   */
  public boolean isPouring() {
    return faucetState != FaucetState.OFF;
  }

  /* Activation */

  /**
   * Toggles pouring state and initiates transfer if appropriate. Called on right click and from redstone
   */
  public void activate() {
    // don't run on client
    if (level == null || level.isClientSide()) {
      return;
    }
    // already pouring? we want to start
    switch (faucetState) {
      // off activates the faucet
      case OFF -> {
        stopPouring = false;
        doTransfer(true);
      }
      // powered deactivates the faucet, sync to client
      case POWERED -> {
        faucetState = FaucetState.OFF;
        syncToClient(FluidStack.EMPTY, false);
      }
      // pouring means we stop pouring as soon as possible
      case POURING -> stopPouring = true;
    }
  }

  /** Starts or keeps pouring from redstone without toggling a powered faucet back off. */
  public void activateFromRedstone() {
    if (level == null || level.isClientSide()) {
      return;
    }
    stopPouring = false;
    if (faucetState == FaucetState.OFF || faucetState == FaucetState.POWERED) {
      doTransfer(true);
    }
  }

  /**
   * Flips hasSignal and schedules a tick if appropriate.
   * @param hasSignal  New signal state
   */
  public void handleRedstone(boolean hasSignal) {
    if (hasSignal != lastRedstoneState) {
      lastRedstoneState = hasSignal;
      if (hasSignal) {
        activateFromRedstone();
      } else if (faucetState == FaucetState.POWERED) {
        faucetState = FaucetState.OFF;
        syncToClient(FluidStack.EMPTY, false);
      }
    }
  }


  /* Pouring */

  /** Handles server ticks */
  private void tick() {
    if (restoreDrainedOnNextTick) {
      if (!restoreDrainedToInput()) {
        return;
      }
    }
    if (checkRedstoneOnNextTick) {
      checkRedstoneOnNextTick = false;
      lastRedstoneState = false;
      handleRedstone(level != null && level.hasNeighborSignal(worldPosition));
    }
    // nothing to do if not pouring
    if (faucetState == FaucetState.OFF) {
      return;
      // if powered and we can transfer, schedule transfer for next tick
    } else if (faucetState == FaucetState.POWERED && doTransfer(false)) {
      faucetState = FaucetState.POURING;
      return;
    }

    // continue current stack
    if (!drained.isEmpty()) {
      pour();
      // stop if told to stop once done
    } else if (stopPouring) {
      reset();
      // otherwise keep going
    } else {
      doTransfer(true);
    }
  }

  /**
   * Initiate fluid transfer
   */
  private boolean doTransfer(boolean execute) {
    // still got content left
    IFluidHandler input = getInputHandler();
    IFluidHandler output = getOutputHandler();
    if (input != EmptyFluidHandler.INSTANCE && output != EmptyFluidHandler.INSTANCE) {
      // can we drain?
      FluidStack drained = input.drain(PACKET_SIZE, SIMULATE);
      if (!drained.isEmpty()) {
        // can we fill
        int filled = output.fill(drained, SIMULATE);
        if (filled > 0) {
          // ensure we can actually fill in our min increment, deals with handlers like copper cans
          // can skip this step if we already received a small enough number
          drained.setAmount(MB_PER_TICK); // done using this fluid stack's original size, so save some memory and reuse
          int minFilled = filled <= MB_PER_TICK ? filled : output.fill(drained, SIMULATE);
          if (filled <= MB_PER_TICK || minFilled > 0) {
            // execute if requested
            if (execute) {
              // drain the liquid and transfer it, buffer the amount for delay
              this.drained = FluidStackNbt.registeredCopy(input.drain(filled, EXECUTE));

              // sync to clients if we have changes
              if (faucetState == FaucetState.OFF || !FluidStack.isSameFluidSameComponents(renderFluid, drained)) {
                syncToClient(this.drained, true);
              }
              faucetState = FaucetState.POURING;
              // pour after initial packet, in case we end up resetting later
              pour();
            }
            return true;
          }
        }
      }

      // if powered, keep faucet running
      if (lastRedstoneState) {
        // sync if either we were not pouring before (particle effects), or if the client thinks we have fluid
        if (execute && (faucetState == FaucetState.OFF || !FluidStack.isSameFluidSameComponents(renderFluid, FluidStack.EMPTY))) {
          syncToClient(FluidStack.EMPTY, true);
        }
        faucetState = FaucetState.POWERED;
        return false;
      }
    }
    // If redstone is holding the faucet on, stay in a waiting state and retry on
    // later ticks. This covers world-load ordering where drains/ducts may not
    // have reconnected to the smeltery master yet.
    if (lastRedstoneState) {
      if (execute && (faucetState == FaucetState.OFF || !FluidStack.isSameFluidSameComponents(renderFluid, FluidStack.EMPTY))) {
        syncToClient(FluidStack.EMPTY, true);
      }
      faucetState = FaucetState.POWERED;
    } else if (execute) {
      reset();
    }
    return false;
  }

  /**
   * Takes the liquid inside and executes one pouring step.
   */
  private void pour() {
    if (drained.isEmpty()) {
      return;
    }

    // ensure we have an output
    IFluidHandler output = getOutputHandler();
    if (output != EmptyFluidHandler.INSTANCE) {
      FluidStack fillStack = drained.copy();
      fillStack.setAmount(Math.min(drained.getAmount(), MB_PER_TICK));

      // can we fill?
      int filled = output.fill(fillStack, SIMULATE);
      if (filled > 0) {
        // update client if they do not think we have fluid
        if (!FluidStack.isSameFluidSameComponents(renderFluid, drained)) {
          syncToClient(drained, true);
        }

        // transfer it
        fillStack.setAmount(filled);
        int execFilled = output.fill(fillStack, EXECUTE);
        if (execFilled > 0) {
          this.drained.shrink(execFilled);
        } else {
          pausePouring();
        }
      } else {
        pausePouring();
      }
    }
    else {
      pausePouring();
    }
  }

  /** Returns saved faucet buffer to the input after a world reload before resuming automation. */
  private boolean restoreDrainedToInput() {
    restoreDrainedOnNextTick = false;
    if (drained.isEmpty()) {
      return true;
    }
    IFluidHandler input = getInputHandler();
    if (input == EmptyFluidHandler.INSTANCE) {
      restoreDrainedOnNextTick = true;
      faucetState = FaucetState.POWERED;
      return false;
    }
    int filled = input.fill(drained.copy(), SIMULATE);
    if (filled < drained.getAmount()) {
      restoreDrainedOnNextTick = true;
      faucetState = FaucetState.POWERED;
      return false;
    }
    input.fill(drained.copy(), EXECUTE);
    drained = FluidStack.EMPTY;
    stopPouring = false;
    faucetState = FaucetState.OFF;
    syncToClient(FluidStack.EMPTY, false);
    return true;
  }

  /**
   * Stops pouring without discarding liquid already drained into the faucet buffer.
   * This handles cases where the output changes or refuses the fluid after the faucet
   * already removed a packet from the source tank.
   */
  private void pausePouring() {
    stopPouring = true;
    faucetState = FaucetState.POURING;
    syncToClient(drained, true);
  }

  /**
   * Resets TE to default state.
   */
  private void reset() {
    stopPouring = false;
    drained = FluidStack.EMPTY;
    if (faucetState != FaucetState.OFF || !FluidStack.isSameFluidSameComponents(renderFluid, drained)) {
      faucetState = FaucetState.OFF;
      syncToClient(FluidStack.EMPTY, false);
    }
  }

  public AABB getRenderBoundingBox() {
    return new AABB(worldPosition.getX(), worldPosition.getY() - 1, worldPosition.getZ(), worldPosition.getX() + 1, worldPosition.getY() + 1, worldPosition.getZ() + 1);
  }

  @Override
  public void onLoad() {
    super.onLoad();
    if (level == null || level.isClientSide()) {
      return;
    }
    lastRedstoneState = false;
    restoreDrainedOnNextTick = !drained.isEmpty();
    checkRedstoneOnNextTick = true;
  }


  /* NBT and networking */

  /**
   * Sends an update to the client with the most recent
   * @param fluid       New fluid
   * @param isPouring   New isPouring status
   */
  private void syncToClient(FluidStack fluid, boolean isPouring) {
    renderFluid = FluidStackNbt.registeredCopy(fluid);
    if (level instanceof ServerLevel) {
      TinkerNetwork.getInstance().sendToClientsAround(new FaucetActivationPacket(worldPosition, fluid, isPouring), (ServerLevel) level, getBlockPos());
    }
  }

  /**
   * Sets draining fluid to specified stack.
   * @param fluid new FluidStack
   */
  public void onActivationPacket(FluidStack fluid, boolean isPouring) {
    // pouring and powered are interchangable on the client
    this.faucetState = isPouring ? FaucetState.POURING : FaucetState.OFF;
    this.renderFluid = FluidStackNbt.registeredCopy(fluid);
  }

  @Override
  protected boolean shouldSyncOnUpdate() {
    return true;
  }

  @Override
  protected void saveSynced(CompoundTag compound) {
    super.saveSynced(compound);
    compound.putByte(TAG_STATE, (byte)faucetState.ordinal());
    if (!renderFluid.isEmpty()) {
      compound.put(TAG_RENDER_FLUID, FluidStackNbt.write(renderFluid));
    }
  }

  @Override
  public void saveAdditional(ValueOutput output) {
    super.saveAdditional(output);
    output.putByte(TAG_STATE, (byte)faucetState.ordinal());
    output.putBoolean(TAG_STOP, stopPouring);
    output.putBoolean(TAG_LAST_REDSTONE, lastRedstoneState);
    if (!drained.isEmpty()) {
      output.store(TAG_DRAINED, FluidStack.OPTIONAL_CODEC, drained);
    }
    if (!renderFluid.isEmpty()) {
      output.store(TAG_RENDER_FLUID, FluidStack.OPTIONAL_CODEC, renderFluid);
    }
  }

  @Override
  protected void loadAdditional(ValueInput input) {
    super.loadAdditional(input);
    faucetState = FaucetState.fromIndex(input.getByteOr(TAG_STATE, (byte)0));
    stopPouring = input.getBooleanOr(TAG_STOP, false);
    lastRedstoneState = input.getBooleanOr(TAG_LAST_REDSTONE, false);
    drained = FluidStackNbt.registeredCopy(input.read(TAG_DRAINED, FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY));
    renderFluid = FluidStackNbt.registeredCopy(input.read(TAG_RENDER_FLUID, FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY));
  }

  private enum FaucetState {
    OFF,
    POURING,
    POWERED;

    /** Gets the state for the given index */
    public static FaucetState fromIndex(int index) {
      switch (index) {
        case 1: return POURING;
        case 2: return POWERED;
      }
      return OFF;
    }
  }
}

