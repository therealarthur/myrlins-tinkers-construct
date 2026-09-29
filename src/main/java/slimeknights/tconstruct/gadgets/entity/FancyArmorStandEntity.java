package slimeknights.tconstruct.gadgets.entity;

import lombok.Getter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import slimeknights.tconstruct.gadgets.TinkerGadgets;

/** Extension of {@link ArmorStand} with extra features */
public class FancyArmorStandEntity extends ArmorStand {
  private static final String TAG_VARIANT = "Variant";
  private static final String TAG_LEFT = "LeftHanded";
  private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(FancyArmorStandEntity.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Boolean> LEFT_HANDED = SynchedEntityData.defineId(FancyArmorStandEntity.class, EntityDataSerializers.BOOLEAN);

  public FancyArmorStandEntity(EntityType<? extends FancyArmorStandEntity> type, Level level) {
    super(type, level);
  }


  /* Data */

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(VARIANT, 0);
    builder.define(LEFT_HANDED, false);
  }

  @Override
  protected void addAdditionalSaveData(ValueOutput output) {
    super.addAdditionalSaveData(output);
    output.putInt(TAG_VARIANT, this.getStandId());
    output.putBoolean(TAG_LEFT, isLeft());
  }

  @Override
  protected void readAdditionalSaveData(ValueInput input) {
    super.readAdditionalSaveData(input);
    this.entityData.set(VARIANT, input.getIntOr(TAG_VARIANT, 0));
    this.entityData.set(LEFT_HANDED, input.getBooleanOr(TAG_LEFT, false));
  }


  /* Extra behavior */

  @Override
  public boolean fireImmune() {
    return true;
  }

  /** Checks if this stand should not be rendered */
  public boolean isClearHidden() {
    if (getStandId() == StandType.CLEAR.id) {
      for (EquipmentSlot slot : EquipmentSlot.values()) {
        if (!getItemBySlot(slot).isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public boolean isInvisibleTo(Player player) {
    return isClearHidden() || super.isInvisibleTo(player);
  }


  /* Hand */

  private boolean isLeft() {
    return this.entityData.get(LEFT_HANDED);
  }

  public void setLeft(boolean left) {
    this.entityData.set(LEFT_HANDED, left);
  }

  @Override
  public HumanoidArm getMainArm() {
    return isLeft() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
  }


  /* Variant definition */

  protected int getStandId() {
    return this.entityData.get(VARIANT);
  }

  public StandType getStandType() {
    return StandType.byId(this.getStandId());
  }

  public void setStandType(StandType type) {
    this.entityData.set(VARIANT, type.getId());
  }

  /** Makes this armor stand small */
  void setSmall() {
    this.entityData.set(DATA_CLIENT_FLAGS, (byte)(this.entityData.get(DATA_CLIENT_FLAGS) | 1));
    this.refreshDimensions();
  }


  /* Variant items */

  public Item getStandItem() {
    return TinkerGadgets.armorStand.get(getStandType());
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(getStandItem());
  }

  @Override
  protected Component getTypeName() {
    return Component.translatable(getStandItem().getDescriptionId());
  }

  /** Replaces the vanilla stand drop while retaining the parent equipment-drop path. */
  @Override
  protected void brokenByPlayer(ServerLevel level, DamageSource source) {
    ItemStack stack = new ItemStack(getStandItem());
    if (this.hasCustomName()) {
      stack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
    }
    Block.popResource(this.level(), this.blockPosition(), stack);
    this.brokenByAnything(level, source);
  }


  /** List of all stand types */
  public enum StandType {
    BAMBOO {
      @Override
      public void onPlace(FancyArmorStandEntity entity) {
        super.onPlace(entity);
        entity.setSmall();
      }
    },
    BONE {
      @Override
      public void onPlace(FancyArmorStandEntity entity) {
        super.onPlace(entity);
        entity.setShowArms(true);
      }
    },
    NECROTIC_BONE {
      @Override
      public void onPlace(FancyArmorStandEntity entity) {
        super.onPlace(entity);
        entity.setShowArms(true);
      }
    },
    CLEAR;

    private static final StandType[] VALUES = values();
    @Getter
    private final int id = ordinal();

    public void onPlace(FancyArmorStandEntity entity) {
      entity.setStandType(this);
    }

    public boolean isFullbright() {
      return this == NECROTIC_BONE;
    }

    public static StandType byId(int id) {
      if (id < 0 || id >= VALUES.length) {
        id = 0;
      }
      return VALUES[id];
    }
  }
}
