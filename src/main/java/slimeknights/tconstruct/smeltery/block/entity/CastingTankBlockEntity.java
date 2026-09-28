package slimeknights.tconstruct.smeltery.block.entity;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import slimeknights.tconstruct.library.fluid.FluidStackNbt;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import slimeknights.mantle.fluid.FluidTransferHelper;
import slimeknights.mantle.fluid.transfer.FluidContainerTransferManager;
import slimeknights.mantle.fluid.transfer.IFluidContainerTransfer;
import slimeknights.mantle.fluid.transfer.IFluidContainerTransfer.TransferResult;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.Sounds;
import slimeknights.tconstruct.library.client.model.ModelProperties;
import slimeknights.tconstruct.library.fluid.FluidTankAnimated;
import slimeknights.tconstruct.library.utils.NBTTags;
import slimeknights.tconstruct.shared.block.entity.TableBlockEntity;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.CastingTankBlock;
import slimeknights.tconstruct.smeltery.block.entity.component.TankBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.component.TankBlockEntity.ITankBlock;
import slimeknights.tconstruct.smeltery.item.TankItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CastingTankBlockEntity extends TableBlockEntity implements ITankBlockEntity.ITankInventoryBlockEntity, WorldlyContainer {
  /** Max capacity for the tank */
  public static final int DEFAULT_CAPACITY = FluidType.BUCKET_VOLUME * 4;
  // slots
  public static final int INPUT = 0;
  public static final int OUTPUT = 1;
  private static final Component NAME = TConstruct.makeTranslation("gui", "casting");

  /** Internal fluid tank instance */
  @Getter
  protected final FluidTankAnimated tank;
  /** Last redstone state of the block */
  private boolean lastRedstone = false;
  /** Last comparator strength to reduce block updates */
  @Getter @Setter
  private int lastStrength = -1;

  /**
   * Gets the capacity for the given block
   * @param block  block
   * @return  Capacity
   */
  public static int getCapacity(Block block) {
    return DEFAULT_CAPACITY;
  }

  /**
   * Gets the capacity for the given item
   * @param item  item
   * @return  Capacity
   */
  public static int getCapacity(Item item) {
    return DEFAULT_CAPACITY;
  }

  public CastingTankBlockEntity(BlockPos pos, BlockState state) {
    this(pos, state, state.getBlock() instanceof ITankBlock tankBlock
                     ? tankBlock
                     : TinkerSmeltery.searedCastingTank.get());
  }

  /** Main constructor */
  public CastingTankBlockEntity(BlockPos pos, BlockState state, ITankBlock block) {
    this(TinkerSmeltery.castingTank.get(), pos, state, block);
  }

  /** Extendable constructor */
  @SuppressWarnings("WeakerAccess")
  protected CastingTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, ITankBlock block) {
    super(type, pos, state, NAME, 2, 1);
    tank = new FluidTankAnimated(block.getCapacity(), this);
    itemHandler = new SidedInvWrapper(this, Direction.DOWN);
  }

  /**
   * Called from {@link slimeknights.tconstruct.smeltery.block.CastingTankBlock#use(BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)}
   * @param player Player activating the block.
   */
  public void interact(Player player, InteractionHand hand, boolean clickedTank) {
    // skip client side
    if (level == null || level.isClientSide()) {
      return;
    }

    // clicked tank? move fluid
    if (clickedTank) {
      if (!FluidTransferHelper.interactWithContainer(level, worldPosition, tank, player, hand).didTransfer()) {
        FluidTransferHelper.interactWithFilledBucket(level, worldPosition, tank, player, hand, getBlockState().getValue(CastingTankBlock.FACING));
      }
    } else {
      // did not click tank? swap items around
      ItemStack input = getItem(INPUT);
      ItemStack output = getItem(OUTPUT);
      ItemStack held = player.getItemInHand(hand);
      // if there is an item in the output slot, take it
      if (!output.isEmpty()) {
        setItem(OUTPUT, ItemStack.EMPTY);
        player.getInventory().placeItemBackInInventory(output);
        // next try to take the item from the input slot
      } else if (!input.isEmpty()) {
        setItem(INPUT, ItemStack.EMPTY);
        player.getInventory().placeItemBackInInventory(input);
        // if no item in the tank, try to place a held item in the input
      } else if (!held.isEmpty() && canPlaceItem(INPUT, held)) {
        setItem(INPUT, held.split(1));
      }
    }
  }

  @Override
  public void setItem(int slot, ItemStack newStack) {
    ItemStack oldStack = getItem(INPUT);
    super.setItem(slot, newStack);
    // if we are setting the input item to something different, try to process it
    if (slot == INPUT && !newStack.isEmpty() && !ItemStack.matches(oldStack, newStack)) {
      tryToProcessItem();
    }
  }

  private void setInputItem(ItemStack stack) {
    super.setItem(INPUT, stack);
  }

  @Override
  public boolean canPlaceItem(int pIndex, ItemStack pStack) {
    if (pIndex == INPUT) {
      return getItem(INPUT).isEmpty() && getItem(OUTPUT).isEmpty() && !pStack.isEmpty() && (
        // check the various options for some sort of fluid-containing stack
        FluidContainerTransferManager.INSTANCE.mayHaveTransfer(pStack)
      );
    }
    return false;
  }

  /** Called on block update to update the redstone state */
  public void handleRedstone(boolean hasSignal) {
    if (lastRedstone != hasSignal) {
      if (hasSignal) {
        if (level != null){
          level.scheduleTick(worldPosition, this.getBlockState().getBlock(), 2);
        }
      }
      lastRedstone = hasSignal;
    }
  }

  /** Called after a redstone tick to swap input and output */
  public void swap() {
    ItemStack output = getItem(OUTPUT);
    setItem(OUTPUT, getItem(INPUT));
    setItem(INPUT, output);
    if (level != null) {
      level.playSound(null, getBlockPos(), Sounds.CASTING_CLICKS.getSound(), SoundSource.BLOCKS, 1.0f, 1.0f);
    }
  }

  /**
   * Tries to empty or fill an item in the input spot. If either happens, the resulting item is placed in the output slot.
   */
  protected void tryToProcessItem() {
    ItemStack input = getItem(INPUT);
    if (input.isEmpty() || !getItem(OUTPUT).isEmpty()) {
      return;
    }

    // need to take the item out of the input slot to prevent a nested call from getting too far if we do modify the tank
    setInputItem(ItemStack.EMPTY);
    TransferResult result = FluidTransferHelper.interactWithStack(tank, input, IFluidContainerTransfer.TransferDirection.AUTO);

    // if no transfer happened
    if (result == null) {
      // put the input item back in the input slot without trying to process, since we know that won't work
      setInputItem(input);
    } else {
      // otherwise, the item got processed
      setItem(OUTPUT, result.stack());

      // play appropriate sound
      if (level != null) {
        level.playSound(null, getBlockPos(), result.getSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
      }
    }
  }

  @Override
  @Nonnull
  public int[] getSlotsForFace(Direction side) {
    return new int[]{INPUT, OUTPUT};
  }

  @Override
  public boolean canPlaceItemThroughFace(int index, ItemStack itemStackIn, @Nullable Direction direction) {
    return index == INPUT && !isStackInSlot(OUTPUT);
  }

  @Override
  public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
    return index == OUTPUT;
  }

  /*
   * Tank methods
   */

  public IFluidHandler getFluidHandler(@Nullable Direction facing) {
    return tank;
  }

  @Nonnull
  @Override
  public ModelData getModelData() {
    return ModelData.builder()
      .with(ModelProperties.FLUID_STACK, tank.getFluid().copy())
      .with(ModelProperties.TANK_CAPACITY, tank.getCapacity()).build();
  }

  @Override
  public void onTankContentsChanged() {
    ITankInventoryBlockEntity.super.onTankContentsChanged();
    tryToProcessItem();
    if (this.level != null) {
      TankBlockEntity.updateLight(this, tank);
      this.requestModelDataUpdate();
      if (!this.level.isClientSide()) {
        BlockState state = getBlockState();
        this.level.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_CLIENTS);
      }
    }
  }


  @Override
  public void onLoad() {
    super.onLoad();
    refreshTankRender();
  }

  /** Refreshes tank rendering and synced light after loading or packet updates. */
  private void refreshTankRender() {
    if (level != null) {
      TankBlockEntity.updateLight(this, tank);
      requestModelDataUpdate();
      if (!level.isClientSide()) {
        BlockState state = getBlockState();
        level.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_CLIENTS);
      }
    }
  }
  /*
   * NBT
   */
  private static final String TAG_REDSTONE = "redstone";

  /**
   * Sets the tag on the stack based on the contained tank.
   * The component is written even when the tank is empty, as the value the block was placed with otherwise survives
   * in the stored component patch and a drained tank drops still holding its original fluid.
   */
  @Override
  protected void collectImplicitComponents(DataComponentMap.Builder components) {
    super.collectImplicitComponents(components);
    components.set(DataComponents.CUSTOM_DATA, TankBlockEntity.writeTankData(tank.getFluid()));
  }

  /** Fills the tank from a placed item, which also keeps the fluid out of the stored component patch. */
  @Override
  protected void applyImplicitComponents(DataComponentGetter components) {
    super.applyImplicitComponents(components);
    CustomData data = components.get(DataComponents.CUSTOM_DATA);
    if (data != null) {
      updateTank(data.copyTag().getCompound(NBTTags.TANK).orElseGet(CompoundTag::new));
    }
  }

  public void setTankTag(ItemStack stack) {
    TankItem.setTank(stack, tank);
  }

  /**
   * Updates the tank from an NBT tag, used in the block
   * @param nbt  tank NBT
   */
  public void updateTank(CompoundTag nbt) {
    if (nbt.isEmpty()) {
      tank.setFluid(FluidStack.EMPTY);
    } else {
      FluidStack fluid = FluidStackNbt.read(nbt);
      if (fluid.isEmpty()) {
        fluid = nbt.getCompound("fluid")
          .map(FluidStackNbt::read)
          .orElseGet(() -> nbt.getCompound("Fluid")
            .map(FluidStackNbt::read)
            .orElse(FluidStack.EMPTY));
      }
      tank.setFluid(fluid);
    }
    onTankContentsChanged();
  }

  public void handleUpdateTag(CompoundTag tag) {
    tag.getCompound(NBTTags.TANK).ifPresent(this::updateTank);
  }

  @Override
  protected void loadAdditional(ValueInput input) {
    super.loadAdditional(input);
    tank.setCapacity(getCapacity(getBlockState().getBlock()));
    input.child(NBTTags.TANK).flatMap(child -> child.read("fluid", FluidStack.OPTIONAL_CODEC)).ifPresent(tank::setFluid);
    lastRedstone = input.getBooleanOr(TAG_REDSTONE, false);
    refreshTankRender();
  }

  @Override
  public void saveAdditional(ValueOutput output) {
    super.saveAdditional(output);
    output.putBoolean(TAG_REDSTONE, lastRedstone);
    if (!tank.isEmpty()) {
      output.child(NBTTags.TANK).store("fluid", FluidStack.OPTIONAL_CODEC, tank.getFluid());
    }
  }
  @Override
  public void saveSynced(CompoundTag tag) {
    super.saveSynced(tag);
    // want tank on the client on world load
    if (!tank.isEmpty()) {
      CompoundTag tankTag = new CompoundTag();
      tankTag.put("fluid", FluidStackNbt.write(tank.getFluid()));
      tag.put(NBTTags.TANK, tankTag);
    }
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
    return null;
  }
}
