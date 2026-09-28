package slimeknights.tconstruct.smeltery.block.entity.component;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.model.data.ModelData;
import slimeknights.mantle.util.RetexturedHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.model.ModelProperties;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.component.SmelteryInputOutputBlockEntity.SmelteryFluidIO;
import slimeknights.tconstruct.smeltery.block.entity.inventory.DuctItemHandler;
import slimeknights.tconstruct.smeltery.block.entity.inventory.DuctTankWrapper;
import slimeknights.tconstruct.smeltery.menu.SingleItemContainerMenu;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Filtered drain tile entity
 */
public class DuctBlockEntity extends SmelteryFluidIO implements MenuProvider {
  private static final String TAG_ITEM = "item";
  private static final Component TITLE = TConstruct.makeTranslation("gui", "duct");

  @Getter
  private final DuctItemHandler itemHandler = new DuctItemHandler(this);

  public DuctBlockEntity(BlockPos pos, BlockState state) {
    this(TinkerSmeltery.duct.get(), pos, state);
  }

  protected DuctBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
  }


  /* Container */

  @Override
  public Component getDisplayName() {
    return TITLE;
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player playerEntity) {
    return new SingleItemContainerMenu(id, inventory, this);
  }


  /* Capability */


  @Override
  protected IFluidHandler makeWrapper(IFluidHandler handler) {
    return new DuctTankWrapper(handler, itemHandler);
  }

  /** Called when the filter item changes or loads from disk. */
  public void onFilterChanged() {
    clearHandler();
    setChangedFast();
    if (level != null) {
      updateFluid();
    }
  }

  @Nonnull
  @Override
  public ModelData getModelData() {
    return RetexturedHelper.getModelDataBuilder(getTexture()).with(ModelProperties.FLUID_STACK, itemHandler.getFluid().copy()).build();
  }

  /** Updates the fluid in model data */
  public void updateFluid() {
    requestModelDataUpdate();
    assert level != null;
    BlockState state = getBlockState();
    level.sendBlockUpdated(worldPosition, state, state, 48);
  }


  /* NBT */

  @Override
  protected boolean shouldSyncOnUpdate() {
    return true;
  }

  public void load(CompoundTag tags) {
    super.load(tags);
    tags.getCompound(TAG_ITEM).ifPresent(tag -> {
      itemHandler.readFromNBT(tag);
      itemHandler.refreshFluid();
    });
  }

  public void handleUpdateTag(CompoundTag tag) {
    super.load(tag);
    tag.getCompound(TAG_ITEM).ifPresent(item -> {
      itemHandler.readFromNBT(item);
      itemHandler.refreshFluid();
    });
    if (level != null && level.isClientSide()) {
      updateFluid();
    }
  }

  @Override
  protected void loadAdditional(ValueInput input) {
    super.loadAdditional(input);
    input.read(TAG_ITEM, CompoundTag.CODEC).ifPresent(tag -> {
      itemHandler.readFromNBT(tag);
      itemHandler.refreshFluid();
    });
  }

  @Override
  public void saveSynced(CompoundTag tags) {
    super.saveSynced(tags);
    tags.put(TAG_ITEM, itemHandler.writeToNBT());
  }

  @Override
  public void saveAdditional(ValueOutput output) {
    super.saveAdditional(output);
    output.store(TAG_ITEM, CompoundTag.CODEC, itemHandler.writeToNBT());
  }
}
