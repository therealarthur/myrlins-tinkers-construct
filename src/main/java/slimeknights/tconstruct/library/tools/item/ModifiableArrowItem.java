package slimeknights.tconstruct.library.tools.item;

import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.common.Sounds;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.hook.build.ConditionalStatModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.InventoryTickModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.SlotStackModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.build.RarityModule;
import slimeknights.tconstruct.library.tools.IndestructibleItemEntity;
import slimeknights.tconstruct.library.tools.capability.ToolCapabilityProvider;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.module.display.ToolNameHook;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.helper.TooltipUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.shared.TinkerCommons;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.tools.entity.ModifiableArrow;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/** Modifiable item that is usable as arrows in a bow */
public class ModifiableArrowItem extends ArrowItem implements IModifiableDisplay {
  /** Tool definition for the given tool */
  @Getter
  private final ToolDefinition toolDefinition;
  /** Cached tool for rendering on UIs */
  private ItemStack toolForRendering;

  public ModifiableArrowItem(Properties props, ToolDefinition toolDefinition) {
    super(slimeknights.mantle.registration.deferred.ItemDeferredRegister.setIdFromCurrentKey(TooltipUtil.hideVanillaEnchantments(props)));
    this.toolDefinition = toolDefinition;
  }


  /* Arrowing */
  @Override
  public AbstractArrow createArrow(Level level, ItemStack stack, LivingEntity shooter, @org.jspecify.annotations.Nullable ItemStack firedFromWeapon) {
    ModifiableArrow arrow = new ModifiableArrow(level, shooter);
    arrow.onCreate(stack, shooter);
    return arrow;
  }

  /** @deprecated kept for old addon callers; Minecraft 26.1 calls the weapon-aware overload above. */
  @Deprecated(forRemoval = true)
  public AbstractArrow createArrow(Level level, ItemStack stack, LivingEntity shooter) {
    return createArrow(level, stack, shooter, null);
  }

  public boolean isInfinite(ItemStack stack, ItemStack bow, Player player) {
    return false;
  }


  /* Shurikening */
  public InteractionResult use(Level level, Player player, InteractionHand hand) {
    ItemStack stack = player.getItemInHand(hand);
    // only throw arrows if they have the throwable tool action. Useful for the other style of projectile in addons, or a really weird arrow modifier.
    if (stack.is(TinkerTags.Items.THROWN_AMMO)) {
      level.playSound(null, player.getX(), player.getY(), player.getZ(), Sounds.SHURIKEN_THROW.getSound(), SoundSource.NEUTRAL, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
      player.getCooldowns().addCooldown(stack, 10);
      if (!level.isClientSide()) {
        ModifiableArrow arrow = new ModifiableArrow(level, player);
        IToolStackView tool = arrow.onCreate(stack, player);
        float velocity = ConditionalStatModifierHook.getModifiedStat(tool, player, ToolStats.VELOCITY);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity, 1.0F);
        level.addFreshEntity(arrow);
      }

      player.awardStat(Stats.ITEM_USED.get(this));
      if (!player.getAbilities().instabuild) {
        stack.shrink(1);
      }

      return InteractionResult.SUCCESS;
    }
    return InteractionResult.PASS;
  }


  /* Loading */

  @Nullable
  public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
    return new ToolCapabilityProvider(stack);
  }
  public void verifyTagAfterLoad(CompoundTag nbt) {
    ToolStack.verifyTag(this, nbt, getToolDefinition());
  }
  @Override
  public void onCraftedBy(ItemStack stack, Player playerIn) {
    ToolStack.ensureInitialized(stack, getToolDefinition());
  }


  /* Display */
  public boolean isFoil(ItemStack stack) {
    // we use enchantments to handle some modifiers, so don't glow from them
    // however, if a modifier wants to glow let them
    return ModifierUtil.checkVolatileFlag(stack, SHINY);
  }
  public Rarity getRarity(ItemStack stack) {
    return RarityModule.getRarity(stack);
  }


  /* Indestructible items */
  public boolean hasCustomEntity(ItemStack stack) {
    return IndestructibleItemEntity.hasCustomEntity(stack);
  }

  @Nullable
  public Entity createEntity(Level world, Entity original, ItemStack stack) {
    return IndestructibleItemEntity.createFrom(world, original, stack);
  }


  /* Modifier interactions */
  @Override
  public void inventoryTick(ItemStack stack, ServerLevel worldIn, Entity entityIn, @Nullable EquipmentSlot slot) {
    // arthur.8: 26.1 replacement for verifyTagAfterLoad, rebuilds stale derived stats once per loaded stack
    slimeknights.tconstruct.tools.logic.ToolLoadVerification.verifyOnce(stack);
    if (entityIn instanceof ServerPlayer player) {
      TinkerCommons.TOOL_INVENTORY_CHANGED_TRIGGER.trigger(player, stack);
    }
    InventoryTickModifierHook.heldInventoryTick(stack, worldIn, entityIn, slot == null ? -1 : slot.ordinal(), slot != null && entityIn instanceof LivingEntity living && living.getItemBySlot(slot) == stack);
  }
  public boolean overrideStackedOnOther(ItemStack held, Slot slot, ClickAction action, Player player) {
    return SlotStackModifierHook.overrideStackedOnOther(held, slot, action, player);
  }
  public boolean overrideOtherStackedOnMe(ItemStack slotStack, ItemStack held, Slot slot, ClickAction action, Player player, SlotAccess access) {
    return SlotStackModifierHook.overrideOtherStackedOnMe(slotStack, held, slot, action, player, access);
  }


  /* Tooltips */
  public Component getName(ItemStack stack) {
    RarityModule.applyToStack(stack);
    return ToolNameHook.getName(getToolDefinition(), stack);
  }
  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
    List<Component> lines = new java.util.ArrayList<>();
    TooltipUtil.addInformation(this, stack, context.level(), lines, SafeClientAccess.getTooltipKey(), flag);
    lines.forEach(tooltip);
  }
  public int getDefaultTooltipHideFlags(ItemStack stack) {
    return TooltipUtil.getModifierHideFlags(getToolDefinition());
  }
  public List<Component> getStatInformation(IToolStackView tool, @Nullable Player player, List<Component> tooltips, TooltipKey key, TooltipFlag tooltipFlag) {
    return TooltipUtil.getAmmoStats(tool, player, tooltips, key, tooltipFlag);
  }

  /* Display items */
  public ItemStack getRenderTool() {
    if (toolForRendering == null) {
      toolForRendering = ToolBuildHandler.buildToolForRendering(this, this.getToolDefinition());
    }
    return toolForRendering;
  }
}
