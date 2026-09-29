package slimeknights.tconstruct.library.tools.item.ranged;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.EquipmentSlot.Type;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.item.ModifiableItemClientExtension;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.behavior.AttributesModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.behavior.EnchantmentModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.display.DurabilityDisplayModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.EntityInteractionModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.InventoryTickModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.SlotStackModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.UsingToolModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.build.RarityModule;
import slimeknights.tconstruct.library.tools.IndestructibleItemEntity;
import slimeknights.tconstruct.library.tools.capability.ToolCapabilityProvider;
import slimeknights.tconstruct.library.tools.capability.inventory.ToolInventoryCapability;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.module.display.ToolNameHook;
import slimeknights.tconstruct.library.tools.definition.module.mining.IsEffectiveToolHook;
import slimeknights.tconstruct.library.tools.definition.module.mining.MiningSpeedToolHook;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.helper.ToolHarvestLogic;
import slimeknights.tconstruct.library.tools.helper.TooltipUtil;
import slimeknights.tconstruct.library.tools.item.IModifiableDisplay;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.shared.TinkerCommons;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.TinkerToolActions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook.KEY_DRAWTIME;

/** Base class for any items that launch projectiles */
public abstract class ModifiableLauncherItem extends ProjectileWeaponItem implements IModifiableDisplay {
  /** Persistent data key for the ammo being used on drawing back the bow. */
  public static final Identifier KEY_DRAWBACK_AMMO = TConstruct.getResource("drawback_ammo");

  /** Tool definition for the given tool */
  @Getter
  private final ToolDefinition toolDefinition;

  /** Cached tool for rendering on UIs */
  private ItemStack toolForRendering;

  public ModifiableLauncherItem(Properties properties, ToolDefinition toolDefinition) {
    super(slimeknights.mantle.registration.deferred.ItemDeferredRegister.setIdFromCurrentKey(TooltipUtil.hideVanillaEnchantments(properties)));
    this.toolDefinition = toolDefinition;
  }


  public void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float velocity, float inaccuracy, float angle, @org.jspecify.annotations.Nullable LivingEntity target) {
    projectile.shootFromRotation(shooter, shooter.getXRot(), shooter.getYRot() + angle, 0, velocity, inaccuracy);
  }

  /* Basic properties */
  public int getMaxStackSize(ItemStack stack) {
    return 1;
  }
  public boolean isNotReplaceableByPickAction(ItemStack stack, Player player, int inventorySlot) {
    return true;
  }


  /* Enchanting */
  public boolean isEnchantable(ItemStack stack) {
    return false;
  }
  public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
    return false;
  }
  public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
    return false;
  }
  public int getEnchantmentValue() {
    return 0;
  }
  @Override
  public int getEnchantmentLevel(ItemInstance stack, Holder<Enchantment> enchantment) {
    return EnchantmentModifierHook.getEnchantmentLevel(stack, enchantment);
  }
  @Override
  public ItemEnchantments getAllEnchantments(ItemStack stack, RegistryLookup<Enchantment> lookup) {
    return EnchantmentModifierHook.getAllEnchantments(stack, lookup);
  }
  public int getEnchantmentLevel(ItemStack stack, Enchantment enchantment) {
    return EnchantmentModifierHook.getEnchantmentLevel(stack, enchantment);
  }
  public Map<Enchantment,Integer> getAllEnchantments(ItemStack stack) {
    return EnchantmentModifierHook.getAllEnchantments(stack);
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

  public void onDestroyed(ItemEntity entity) {
    ToolInventoryCapability.onDestroyed(entity);
  }


  /* Damage/Durability */
  public boolean isRepairable(ItemStack stack) {
    // handle in the tinker station
    return false;
  }
  public boolean canBeDepleted() {
    return true;
  }
  public int getMaxDamage(ItemStack stack) {
    return ToolDamageUtil.getFakeMaxDamage(stack);
  }
  public int getDamage(ItemStack stack) {
    if (!canBeDepleted()) {
      return 0;
    }
    return ToolStack.from(stack).getDamage();
  }
  public void setDamage(ItemStack stack, int damage) {
    if (canBeDepleted()) {
      ToolStack tool = ToolStack.from(stack);
      tool.setDamage(damage);
      tool.updateStack(stack, false);
    }
  }
  public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T damager, Consumer<Item> onBroken) {
    if (stack.isDamageableItem() && ToolDamageUtil.damage(ToolStack.from(stack), amount, damager, stack)) {
      onBroken.accept(stack.getItem());
    }
    return 0;
  }


  /* Durability display */
  public boolean isBarVisible(ItemStack pStack) {
    return DurabilityDisplayModifierHook.showDurabilityBar(pStack);
  }
  public int getBarColor(ItemStack pStack) {
    return DurabilityDisplayModifierHook.getDurabilityRGB(pStack);
  }
  public int getBarWidth(ItemStack pStack) {
    return DurabilityDisplayModifierHook.getDurabilityWidth(pStack);
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


  /* Attacking */
  public boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) {
    return EntityInteractionModifierHook.leftClickEntity(stack, player, target);
  }
  public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
    return ModifierUtil.canPerformAction(ToolStack.from(stack), itemAbility);
  }
  @Override
  public boolean canPerformAction(ItemInstance stack, ItemAbility itemAbility) {
    return stack instanceof ItemStack itemStack && canPerformAction(itemStack, itemAbility);
  }
  public Multimap<Attribute,AttributeModifier> getAttributeModifiers(IToolStackView tool, EquipmentSlot slot) {
    return AttributesModifierHook.getHeldAttributeModifiers(tool, slot);
  }
  @Override
  public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
    if (!stack.has(DataComponents.CUSTOM_DATA)) {
      return ItemAttributeModifiers.EMPTY;
    }
    ToolStack tool = ToolStack.from(stack);
    ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
    for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
      EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
      getAttributeModifiers(tool, slot).forEach((attribute, modifier) -> builder.add(BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute), modifier, group));
    }
    return builder.build();
  }
  public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
    if (!stack.has(DataComponents.CUSTOM_DATA) || slot.getType() != Type.HAND) {
      return ImmutableMultimap.of();
    }
    return getAttributeModifiers(ToolStack.from(stack), slot);
  }
  public boolean canDisableShield(ItemStack stack, ItemStack shield, LivingEntity entity, LivingEntity attacker) {
    return canPerformAction(stack, TinkerToolActions.SHIELD_DISABLE);
  }


  /* Arrow logic */
  @Override
  public int getUseDuration(ItemStack pStack, LivingEntity user) {
    return 72000;
  }
  public abstract ItemUseAnimation getUseAnimation(ItemStack pStack);
  public ItemStack finishUsingItem(ItemStack stack, Level pLevel, LivingEntity living) {
    ToolStack tool = ToolStack.from(stack);
    int duration = getUseDuration(stack, living);
    for (ModifierEntry entry : tool.getModifiers()) {
      entry.getHook(ModifierHooks.TOOL_USING).beforeReleaseUsing(tool, entry, living, duration, 0, ModifierEntry.EMPTY);
    }
    return stack;
  }
  public void onStopUsing(ItemStack stack, LivingEntity entity, int timeLeft) {
    ToolStack tool = ToolStack.from(stack);
    onStopUsing(tool, entity, timeLeft);
    tool.updateStack(stack, false);
  }

  /** Same as {@link #onStopUsing(ItemStack, LivingEntity, int)} but uses a tool. */
  protected void onStopUsing(IToolStackView tool, LivingEntity entity, int timeLeft) {
    UsingToolModifierHook.afterStopUsing(tool, entity, timeLeft);
    ModDataNBT data = tool.getPersistentData();
    data.remove(KEY_DRAWTIME);
    data.remove(KEY_DRAWBACK_AMMO);
  }
  public void onUseTick(Level level, LivingEntity living, ItemStack bow, int chargeRemaining) {
    // play the sound at the end of loading as an indicator its loaded, texture is another indicator
    int duration = getUseDuration(bow, living);
    if (!level.isClientSide()) {
      if (duration - chargeRemaining == ModifierUtil.getPersistentInt(bow, KEY_DRAWTIME, -1)) {
        level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.PLAYERS, 0.75F, 1.0F);
      }
    }
    ToolStack tool = ToolStack.from(bow);
    for (ModifierEntry entry : tool.getModifiers()) {
      entry.getHook(ModifierHooks.TOOL_USING).onUsingTick(tool, entry, living, duration, chargeRemaining, ModifierEntry.EMPTY);
    }
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


  /* Display */
  public ItemStack getRenderTool() {
    if (toolForRendering == null) {
      toolForRendering = ToolBuildHandler.buildToolForRendering(this, this.getToolDefinition());
    }
    return toolForRendering;
  }
  public void initializeClient(Consumer<IClientItemExtensions> consumer) {
    consumer.accept(ModifiableItemClientExtension.INSTANCE);
  }


  /* Misc */
  public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
    return shouldCauseReequipAnimation(oldStack, newStack, false);
  }
  public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
    return ModifiableItem.shouldCauseReequip(oldStack, newStack, slotChanged);
  }


  /* Harvest logic, mostly used by modifiers but technically would let you make a pickaxe bow */
  public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
    return IsEffectiveToolHook.isEffective(ToolStack.from(stack), state);
  }
  public boolean mineBlock(ItemStack stack, Level worldIn, BlockState state, BlockPos pos, LivingEntity entityLiving) {
    return ToolHarvestLogic.mineBlock(stack, worldIn, state, pos, entityLiving);
  }
  public float getDestroySpeed(ItemStack stack, BlockState state) {
    return MiningSpeedToolHook.getDestroySpeed(stack, state);
  }
  public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) {
    return false;
  }


  /* Multishot helper */

  /** Gets the angle to fire the first arrow, each additional arrow offsets an additional 10 degrees */
  public static float getAngleStart(int count) {
    return -5 * (count - 1);
  }
}
