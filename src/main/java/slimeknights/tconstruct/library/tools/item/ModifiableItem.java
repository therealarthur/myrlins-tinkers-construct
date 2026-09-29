package slimeknights.tconstruct.library.tools.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.EquipmentSlot.Type;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.Rarity;

import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.client.item.ModifiableItemClientExtension;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.behavior.AttributesModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.behavior.EnchantmentModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.display.DurabilityDisplayModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.EntityInteractionModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.InteractionSource;
import slimeknights.tconstruct.library.modifiers.hook.interaction.InventoryTickModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.SlotStackModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.UsingToolModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.build.RarityModule;
import slimeknights.tconstruct.library.tools.IndestructibleItemEntity;
import slimeknights.tconstruct.library.tools.capability.ToolCapabilityProvider;
import slimeknights.tconstruct.library.tools.capability.fluid.ToolTankHelper;
import slimeknights.tconstruct.library.tools.capability.inventory.ToolInventoryCapability;
import slimeknights.mantle.registration.deferred.ItemDeferredRegister;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.module.display.ToolNameHook;
import slimeknights.tconstruct.library.tools.definition.module.mining.IsEffectiveToolHook;
import slimeknights.tconstruct.library.tools.definition.module.mining.MiningSpeedToolHook;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.helper.ToolHarvestLogic;
import slimeknights.tconstruct.library.tools.helper.TooltipUtil;
import slimeknights.tconstruct.library.tools.nbt.IModDataView;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.shared.TinkerCommons;
import slimeknights.tconstruct.tools.TinkerToolActions;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A standard modifiable item which implements melee hooks
 * This class handles how all the modifier hooks and display data for items made out of different materials
 */
public class ModifiableItem extends Item implements IModifiableDisplay {
  /** Tool definition for the given tool */
  @Getter
  private final ToolDefinition toolDefinition;

  /** Max stack size override */
  private final int maxStackSize;

  /** Cached tool for rendering on UIs */
  private ItemStack toolForRendering;

  public ModifiableItem(Properties properties, ToolDefinition toolDefinition) {
    this(properties, toolDefinition, 1);
  }

  public ModifiableItem(Properties properties, ToolDefinition toolDefinition, int maxStackSize) {
    super(ItemDeferredRegister.setIdFromCurrentKey(TooltipUtil.hideVanillaEnchantments(properties)));
    this.toolDefinition = toolDefinition;
    this.maxStackSize = maxStackSize;
  }
  public int getMaxStackSize(ItemStack stack) {
    return stack.isDamaged() ? 1 : maxStackSize;
  }

  /* Basic properties */
  public boolean isNotReplaceableByPickAction(ItemStack stack, Player player, int inventorySlot) {
    return true;
  }

  @Nullable
  public EquipmentSlot getEquipmentSlot(ItemStack stack) {
    if (stack.is(TinkerTags.Items.HELD_ARMOR)) {
      return EquipmentSlot.OFFHAND;
    }
    return null;
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
    TinkerCommons.TOOL_INVENTORY_CHANGED_TRIGGER.trigger(playerIn, stack);
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


  /* Damage/Durability */
  public boolean isRepairable(ItemStack stack) {
    // handle in the tinker station
    return false;
  }
  public boolean isValidRepairItem(ItemStack pToRepair, ItemStack pRepair) {
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

  public void onDestroyed(ItemEntity entity) {
    ToolInventoryCapability.onDestroyed(entity);
  }


  /* Durability display */
  public boolean isBarVisible(ItemStack stack) {
    return stack.getCount() == 1 && DurabilityDisplayModifierHook.showDurabilityBar(stack);
  }
  public int getBarColor(ItemStack pStack) {
    return DurabilityDisplayModifierHook.getDurabilityRGB(pStack);
  }
  public int getBarWidth(ItemStack pStack) {
    return DurabilityDisplayModifierHook.getDurabilityWidth(pStack);
  }


  /* Attacking */
  public boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) {
    return stack.getCount() > 1 || EntityInteractionModifierHook.leftClickEntity(stack, player, target);
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


  /* Harvest logic */
  public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
    return IsEffectiveToolHook.isEffective(ToolStack.from(stack), state);
  }
  public boolean mineBlock(ItemStack stack, Level worldIn, BlockState state, BlockPos pos, LivingEntity entityLiving) {
    return ToolHarvestLogic.mineBlock(stack, worldIn, state, pos, entityLiving);
  }
  public float getDestroySpeed(ItemStack stack, BlockState state) {
    return stack.getCount() == 1 ? MiningSpeedToolHook.getDestroySpeed(stack, state) : 0;
  }
  public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) {
    return stack.getCount() > 1;
  }


  /* Modifier interactions */
  @Override
  public void inventoryTick(ItemStack stack, ServerLevel worldIn, Entity entityIn, @Nullable EquipmentSlot slot) {
    // arthur.8: 26.1 replacement for verifyTagAfterLoad, rebuilds stale derived stats once per loaded stack
    slimeknights.tconstruct.tools.logic.ToolLoadVerification.verifyOnce(stack);
    TinkerCommons.TOOL_INVENTORY_CHANGED_TRIGGER.trigger(entityIn, stack);
    if (stack.has(DataComponents.CUSTOM_DATA) && stack.is(TinkerTags.Items.HARVEST) && !stack.has(DataComponents.TOOL)) {
      ToolStack.from(stack).updateStack(stack, false);
    }
    InventoryTickModifierHook.heldInventoryTick(stack, worldIn, entityIn, slot == null ? -1 : slot.ordinal(), slot != null && entityIn instanceof LivingEntity living && living.getItemBySlot(slot) == stack);
  }
  public boolean overrideStackedOnOther(ItemStack held, Slot slot, ClickAction action, Player player) {
    return SlotStackModifierHook.overrideStackedOnOther(held, slot, action, player);
  }
  public boolean overrideOtherStackedOnMe(ItemStack slotStack, ItemStack held, Slot slot, ClickAction action, Player player, SlotAccess access) {
    return SlotStackModifierHook.overrideOtherStackedOnMe(slotStack, held, slot, action, player, access);
  }


  /* Right click hooks */

  /** If true, this interaction hook should defer to the offhand */
  protected static boolean shouldInteract(@Nullable LivingEntity player, ToolStack toolStack, InteractionHand hand) {
    IModDataView volatileData = toolStack.getVolatileData();
    if (volatileData.getBoolean(NO_INTERACTION)) {
      return false;
    }
    // off hand always can interact
    if (hand == InteractionHand.OFF_HAND) {
      return true;
    }
    // main hand may wish to defer to the offhand if it has a tool
    return player == null || !volatileData.getBoolean(DEFER_OFFHAND) || player.getOffhandItem().isEmpty();
  }
  public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
    if (stack.getCount() == 1) {
      ToolStack tool = ToolStack.from(stack);
      InteractionHand hand = context.getHand();
      if (shouldInteract(context.getPlayer(), tool, hand)) {
        for (ModifierEntry entry : tool.getModifierList()) {
          InteractionResult result = entry.getHook(ModifierHooks.BLOCK_INTERACT).beforeBlockUse(tool, entry, context, InteractionSource.RIGHT_CLICK);
          if (result.consumesAction()) {
            tool.updateStack(stack, false);
            return result;
          }
        }
      }
    }
    return InteractionResult.PASS;
  }
  public InteractionResult useOn(UseOnContext context) {
    ItemStack stack = context.getItemInHand();
    if (stack.getCount() == 1) {
      ToolStack tool = ToolStack.from(stack);
      InteractionHand hand = context.getHand();
      if (shouldInteract(context.getPlayer(), tool, hand)) {
        for (ModifierEntry entry : tool.getModifierList()) {
          InteractionResult result = entry.getHook(ModifierHooks.BLOCK_INTERACT).afterBlockUse(tool, entry, context, InteractionSource.RIGHT_CLICK);
          if (result.consumesAction()) {
            tool.updateStack(stack, false);
            return result;
          }
        }
      }
    }
    return InteractionResult.PASS;
  }
  public InteractionResult interactLivingEntity(ItemStack stack, Player playerIn, LivingEntity target, InteractionHand hand) {
    ToolStack tool = ToolStack.from(stack);
    if (shouldInteract(playerIn, tool, hand)) {
      for (ModifierEntry entry : tool.getModifierList()) {
        InteractionResult result = entry.getHook(ModifierHooks.ENTITY_INTERACT).afterEntityUse(tool, entry, playerIn, target, hand, InteractionSource.RIGHT_CLICK);
        if (result.consumesAction()) {
          tool.updateStack(stack, false);
          return result;
        }
      }
    }
    return InteractionResult.PASS;
  }
  public InteractionResult use(Level worldIn, Player playerIn, InteractionHand hand) {
    ItemStack stack = playerIn.getItemInHand(hand);
    if (stack.getCount() > 1) {
      return InteractionResult.PASS;
    }
    ToolStack tool = ToolStack.from(stack);
    if (shouldInteract(playerIn, tool, hand)) {
      for (ModifierEntry entry : tool.getModifierList()) {
        InteractionResult result = entry.getHook(ModifierHooks.GENERAL_INTERACT).onToolUse(tool, entry, playerIn, hand, InteractionSource.RIGHT_CLICK);
        if (result.consumesAction()) {
          tool.updateStack(stack, false);
          return result;
        }
      }
    }
    if (playerIn.isCrouching() && (ToolTankHelper.TANK_HELPER.getCapacity(tool) > 0 || tool.getVolatileData().getInt(ToolInventoryCapability.TOTAL_SLOTS) > 0)) {
      return ToolInventoryCapability.tryOpenContainer(stack, tool, tool.getDefinition(), playerIn, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
    }
    return InteractionResult.PASS;
  }
  public void onUseTick(Level pLevel, LivingEntity entityLiving, ItemStack stack, int timeLeft) {
    ToolStack tool = ToolStack.from(stack);
    ModifierEntry activeModifier = GeneralInteractionModifierHook.getActiveModifier(tool);
    // new hook gets called on all actively in use modifiers
    GeneralInteractionModifierHook hook = activeModifier.getHook(ModifierHooks.GENERAL_INTERACT);
    int duration = hook.getUseDuration(tool, activeModifier);
    for (ModifierEntry entry : tool.getModifiers()) {
      entry.getHook(ModifierHooks.TOOL_USING).onUsingTick(tool, entry, entityLiving, duration, timeLeft, activeModifier);
    }
    // old hook is called on just the main modifier
    hook.onUsingTick(tool, activeModifier, entityLiving, timeLeft);
  }
  public boolean canContinueUsing(ItemStack oldStack, ItemStack newStack) {
    if (super.canContinueUsing(oldStack, newStack)) {
      if (oldStack != newStack) {
        GeneralInteractionModifierHook.finishUsing(ToolStack.from(oldStack));
      }
    }
    return super.canContinueUsing(oldStack, newStack);
  }
  public ItemStack finishUsingItem(ItemStack stack, Level worldIn, LivingEntity entityLiving) {
    ToolStack tool = ToolStack.from(stack);
    ModifierEntry activeModifier = GeneralInteractionModifierHook.getActiveModifier(tool);
    GeneralInteractionModifierHook hook = activeModifier.getHook(ModifierHooks.GENERAL_INTERACT);
    int duration = hook.getUseDuration(tool, activeModifier);
    for (ModifierEntry entry : tool.getModifiers()) {
      entry.getHook(ModifierHooks.TOOL_USING).beforeReleaseUsing(tool, entry, entityLiving, duration, 0, activeModifier);
    }
    hook.onFinishUsing(tool, activeModifier, entityLiving);
    return stack;
  }
  public boolean releaseUsing(ItemStack stack, Level worldIn, LivingEntity entityLiving, int timeLeft) {
    ToolStack tool = ToolStack.from(stack);
    ModifierEntry activeModifier = GeneralInteractionModifierHook.getActiveModifier(tool);
    GeneralInteractionModifierHook hook = activeModifier.getHook(ModifierHooks.GENERAL_INTERACT);
    int duration = hook.getUseDuration(tool, activeModifier);
    for (ModifierEntry entry : tool.getModifiers()) {
      entry.getHook(ModifierHooks.TOOL_USING).beforeReleaseUsing(tool, entry, entityLiving, duration, timeLeft, activeModifier);
    }
    hook.onStoppedUsing(tool, activeModifier, entityLiving, timeLeft);
    return true;
  }
  public void onStopUsing(ItemStack stack, LivingEntity entity, int timeLeft) {
    // triggers on scroll away and all that
    ToolStack tool = ToolStack.from(stack);
    UsingToolModifierHook.afterStopUsing(tool, entity, timeLeft);
    GeneralInteractionModifierHook.finishUsing(tool);
  }
  @Override
  public int getUseDuration(ItemStack stack, LivingEntity user) {
    ToolStack tool = ToolStack.from(stack);
    ModifierEntry activeModifier = GeneralInteractionModifierHook.getActiveModifier(tool);
    if (activeModifier != ModifierEntry.EMPTY) {
      return activeModifier.getHook(ModifierHooks.GENERAL_INTERACT).getUseDuration(tool, activeModifier);
    }
    return 0;
  }
  public ItemUseAnimation getUseAnimation(ItemStack stack) {
    ToolStack tool = ToolStack.from(stack);
    ModifierEntry activeModifier = GeneralInteractionModifierHook.getActiveModifier(tool);
    if (activeModifier != ModifierEntry.EMPTY) {
      return activeModifier.getHook(ModifierHooks.GENERAL_INTERACT).getUseAction(tool, activeModifier);
    }
    return ItemUseAnimation.NONE;
  }
  public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
    return stack.getCount() == 1 && ModifierUtil.canPerformAction(ToolStack.from(stack), itemAbility);
  }
  @Override
  public boolean canPerformAction(ItemInstance stack, ItemAbility itemAbility) {
    return stack instanceof ItemStack itemStack && canPerformAction(itemStack, itemAbility);
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

  /**
   * Logic to prevent reanimation on tools when properties such as autorepair change.
   * @param oldStack      Old stack instance
   * @param newStack      New stack instance
   * @param slotChanged   If true, a slot changed
   * @return  True if a reequip animation should be triggered
   */
  public static boolean shouldCauseReequip(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
    if (oldStack == newStack) {
      return false;
    }
    // basic changes
    if (slotChanged || oldStack.getItem() != newStack.getItem()) {
      return true;
    }

    // if the tool props changed,
    ToolStack oldTool = ToolStack.from(oldStack);
    ToolStack newTool = ToolStack.from(newStack);

    // check if modifiers or materials changed
    if (!oldTool.getMaterials().equals(newTool.getMaterials())) {
      return true;
    }
    if (!oldTool.getModifierList().equals(newTool.getModifierList())) {
      return true;
    }

    // if the attributes changed, reequip
    Multimap<Attribute,AttributeModifier> attributesNew = newStack.getItem() instanceof ModifiableItem newItem ? newItem.getAttributeModifiers(ToolStack.from(newStack), EquipmentSlot.MAINHAND) : ImmutableMultimap.of();
    Multimap<Attribute, AttributeModifier> attributesOld = oldStack.getItem() instanceof ModifiableItem oldItem ? oldItem.getAttributeModifiers(ToolStack.from(oldStack), EquipmentSlot.MAINHAND) : ImmutableMultimap.of();
    if (attributesNew.size() != attributesOld.size()) {
      return true;
    }
    for (Attribute attribute : attributesOld.keySet()) {
      if (!attributesNew.containsKey(attribute)) {
        return true;
      }
      Iterator<AttributeModifier> iter1 = attributesNew.get(attribute).iterator();
      Iterator<AttributeModifier> iter2 = attributesOld.get(attribute).iterator();
      while (iter1.hasNext() && iter2.hasNext()) {
        if (!iter1.next().equals(iter2.next())) {
          return true;
        }
      }
    }
    // no changes, no reequip
    return false;
  }
  public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
    return shouldCauseReequipAnimation(oldStack, newStack, false);
  }
  public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
    return shouldCauseReequip(oldStack, newStack, slotChanged);
  }


  /* Helpers */

  /**
   * Creates a raytrace and casts it to a BlockRayTraceResult
   *
   * @param worldIn the world
   * @param player the given player
   * @param fluidMode the fluid mode to use for the raytrace event
   *
   * @return  Raytrace
   */
  public static BlockHitResult blockRayTrace(Level worldIn, Player player, ClipContext.Fluid fluidMode) {
    return Item.getPlayerPOVHitResult(worldIn, player, fluidMode);
  }
}
