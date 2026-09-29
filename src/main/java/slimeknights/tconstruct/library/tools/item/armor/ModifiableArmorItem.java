package slimeknights.tconstruct.library.tools.item.armor;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import lombok.Getter;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.shared.TinkerCommons;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.behavior.EnchantmentModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.display.DurabilityDisplayModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.SlotStackModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.build.RarityModule;
import slimeknights.tconstruct.library.tools.IndestructibleItemEntity;
import slimeknights.tconstruct.library.tools.capability.ToolCapabilityProvider;
import slimeknights.tconstruct.library.tools.capability.inventory.ToolInventoryCapability;
import slimeknights.tconstruct.library.tools.definition.ModifiableArmorMaterial;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.module.display.ToolNameHook;
import slimeknights.tconstruct.library.tools.definition.module.build.ToolElytraHooks;

import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.helper.TooltipUtil;
import slimeknights.tconstruct.library.tools.item.IModifiableDisplay;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.StatsNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.library.utils.Util;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ModifiableArmorItem extends Item implements IModifiableDisplay {
  /** Volatile modifier tag to make piglins neutal when worn */
  public static final Identifier PIGLIN_NEUTRAL = TConstruct.getResource("piglin_neutral");
  /** Volatile modifier tag to make this item an elytra */
  public static final Identifier ELYTRA = TConstruct.getResource("elyta");
  /** Volatile flag for a boot item to walk on powdered snow. Cold immunity is handled through a tag */
  public static final Identifier SNOW_BOOTS = TConstruct.getResource("snow_boots");
  /** Volatile flag for an item to act as an enderman mask, stopping them from getting angry. */
  public static final Identifier ENDERMASK = TConstruct.getResource("endermask");
  /** Generic equipment asset; client extension remaps its numbered layers to Tinkers armor textures. */
  private static final ResourceKey<EquipmentAsset> TINKER_ARMOR_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, TConstruct.getResource("tinker_armor"));

  @Getter
  private final ToolDefinition toolDefinition;
  private final ArmorType type;
  /** Cache of the tool built for rendering */
  private ItemStack toolForRendering = null;
  public ModifiableArmorItem(Properties builderIn, ToolDefinition toolDefinition) {
    this(builderIn, toolDefinition, ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_GENERIC);
  }

  /** Constructor for JsonThings armor types with a custom equip sound. */
  public ModifiableArmorItem(DummyArmorMaterial material, ArmorType type, Properties properties, ToolDefinition toolDefinition) {
    this(properties, toolDefinition, type, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(material.getEquipSound()));
  }

  public ModifiableArmorItem(ModifiableArmorMaterial material, ArmorType type, Properties properties) {
    this(properties, Objects.requireNonNull(material.getArmorDefinition(type), "Missing tool definition for " + type.getName()), type, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(material.getEquipSound()));
  }

  public ModifiableArmorItem(Properties properties, ToolDefinition toolDefinition, ArmorType type) {
    this(properties, toolDefinition, type, SoundEvents.ARMOR_EQUIP_GENERIC);
  }

  protected ModifiableArmorItem(Properties properties, ToolDefinition toolDefinition, ArmorType type, Holder<SoundEvent> equipSound) {
    this(properties, toolDefinition, type, equipSound, TINKER_ARMOR_ASSET);
  }

  protected ModifiableArmorItem(Properties properties, ToolDefinition toolDefinition, ArmorType type,
                                Holder<SoundEvent> equipSound, ResourceKey<EquipmentAsset> equipmentAsset) {
    super(slimeknights.mantle.registration.deferred.ItemDeferredRegister.setIdFromCurrentKey(
      TooltipUtil.hideVanillaEnchantments(addEquippable(properties, type, equipSound, equipmentAsset))));
    this.toolDefinition = toolDefinition;
    this.type = type;
  }

  private static Properties addEquippable(Properties properties, ArmorType type, Holder<SoundEvent> equipSound,
                                          ResourceKey<EquipmentAsset> equipmentAsset) {
    return properties.component(DataComponents.EQUIPPABLE, Equippable.builder(type.getSlot())
      .setEquipSound(equipSound)
      .setAsset(equipmentAsset)
      .setSwappable(true)
      .setDamageOnHurt(true)
      .build());
  }
  /* Basic properties */
  public int getMaxStackSize(ItemStack stack) {
    return 1;
  }
  public boolean makesPiglinsNeutral(ItemStack stack, LivingEntity wearer) {
    return ModifierUtil.checkVolatileFlag(stack, PIGLIN_NEUTRAL);
  }
  public boolean canWalkOnPowderedSnow(ItemStack stack, LivingEntity wearer) {
    return type == ArmorType.BOOTS && ModifierUtil.checkVolatileFlag(stack, SNOW_BOOTS);
  }
  /** 26.1 renamed the enderman mask hook and widened it to cover every gaze attack, such as the creaking */
  @Override
  public boolean isGazeDisguise(ItemStack stack, Player player, @Nullable LivingEntity target) {
    return type == ArmorType.HELMET && ModifierUtil.checkVolatileFlag(stack, ENDERMASK);
  }
  public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
    return ModifierUtil.canPerformAction(ToolStack.from(stack), itemAbility);
  }
  @Override
  public boolean canPerformAction(ItemInstance stack, ItemAbility itemAbility) {
    return stack instanceof ItemStack itemStack && canPerformAction(itemStack, itemAbility);
  }
  public boolean isNotReplaceableByPickAction(ItemStack stack, Player player, int inventorySlot) {
    return true;
  }


  /* Enchantments */
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
  public InteractionResult use(Level levelIn, Player playerIn, InteractionHand handIn) {
    if (playerIn.isCrouching()) {
      ItemStack stack = playerIn.getItemInHand(handIn);
      InteractionResult result = ToolInventoryCapability.tryOpenContainer(stack, null, getToolDefinition(), playerIn, Util.getSlotType(handIn));
      if (result.consumesAction()) {
        return result;
      }
    }
    return super.use(levelIn, playerIn, handIn);
  }

  public void onDestroyed(ItemEntity entity) {
    ToolInventoryCapability.onDestroyed(entity);
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
  public Entity createEntity(Level level, Entity original, ItemStack stack) {
    return IndestructibleItemEntity.createFrom(level, original, stack);
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
      // parity/materials: ToolStack.from works on a copy of the custom data on 26.1, so write the damage back
      // the same way ModifiableItem does; without this, vanilla repairs such as mending never changed Tinkers armor.
      ToolStack tool = ToolStack.from(stack);
      tool.setDamage(damage);
      tool.updateStack(stack, false);
    }
  }
  public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T damager, Consumer<Item> onBroken) {
    if (canBeDepleted() && ToolDamageUtil.damage(ToolStack.from(stack), amount, damager, stack)) {
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


  /* Armor properties */
  public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
    return false;
  }
  public Multimap<Attribute,AttributeModifier> getAttributeModifiers(IToolStackView tool, EquipmentSlot slot) {
    if (slot != type.getSlot()) {
      return ImmutableMultimap.of();
    }

    ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
    if (!tool.isBroken()) {
      // base stats
      StatsNBT statsNBT = tool.getStats();
      Identifier modifierId = TConstruct.getResource("armor/" + type.getName());
      float armor = statsNBT.get(ToolStats.ARMOR);
      if (armor > 0) {
        builder.put(Attributes.ARMOR.value(), new AttributeModifier(modifierId.withSuffix("/armor"), armor, AttributeModifier.Operation.ADD_VALUE));
      }
      float toughness = statsNBT.get(ToolStats.ARMOR_TOUGHNESS);
      if (toughness > 0) {
        builder.put(Attributes.ARMOR_TOUGHNESS.value(), new AttributeModifier(modifierId.withSuffix("/toughness"), toughness, AttributeModifier.Operation.ADD_VALUE));
      }
      double knockbackResistance = statsNBT.get(ToolStats.KNOCKBACK_RESISTANCE);
      if (knockbackResistance > 0) {
        builder.put(Attributes.KNOCKBACK_RESISTANCE.value(), new AttributeModifier(modifierId.withSuffix("/knockback_resistance"), knockbackResistance, AttributeModifier.Operation.ADD_VALUE));
      }
      // grab attributes from modifiers
      BiConsumer<Attribute,AttributeModifier> attributeConsumer = builder::put;
      for (ModifierEntry entry : tool.getModifierList()) {
        entry.getHook(ModifierHooks.ATTRIBUTES).addAttributes(tool, entry, slot, attributeConsumer);
      }
    }

    return builder.build();
  }
  public Multimap<Attribute,AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
    if (slot != type.getSlot() || !stack.has(DataComponents.CUSTOM_DATA)) {
      return ImmutableMultimap.of();
    }
    return getAttributeModifiers(ToolStack.from(stack), slot);
  }
  @Override
  public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
    if (!stack.has(DataComponents.CUSTOM_DATA)) {
      return ItemAttributeModifiers.EMPTY;
    }
    ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
    EquipmentSlot slot = type.getSlot();
    EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
    getAttributeModifiers(ToolStack.from(stack), slot).forEach((attribute, modifier) -> builder.add(BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute), modifier, group));
    return builder.build();
  }

  /* Elytra */
  public boolean canElytraFly(ItemStack stack, LivingEntity entity) {
    return type == ArmorType.CHESTPLATE && !ToolDamageUtil.isBroken(stack) && ModifierUtil.checkVolatileFlag(stack, ELYTRA);
  }

  /**
   * Runs the modifier flight hooks for a gliding wearer, called from {@link slimeknights.tconstruct.tools.logic.ModifierEvents}.
   * Unlike older versions this does not damage the tool, as vanilla damages every glider it finds through {@link #damageItem}.
   * @return  True if the wearer should keep flying
   */
  public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {
    if (type.getSlot() == EquipmentSlot.CHEST) {
      ToolStack tool = ToolStack.from(stack);
      if (!tool.isBroken()) {
        // if any modifier says stop flying, stop flying
        for (ModifierEntry entry : tool.getModifierList()) {
          if (entry.getHook(ModifierHooks.ELYTRA_FLIGHT).elytraFlightTick(tool, entry, entity, flightTicks)) {
            return false;
          }
        }
        if (!entity.level().isClientSide()) {
          ToolElytraHooks.onFlightTick(tool, stack, entity, flightTicks);
        }
        return true;
      }
    }
    return false;
  }


  /* Ticking */
  @Override
  public void inventoryTick(ItemStack stack, ServerLevel levelIn, Entity entityIn, @Nullable EquipmentSlot slot) {
    // arthur.8: 26.1 replacement for verifyTagAfterLoad, rebuilds stale derived stats once per loaded stack
    slimeknights.tconstruct.tools.logic.ToolLoadVerification.verifyOnce(stack);
    TinkerCommons.TOOL_INVENTORY_CHANGED_TRIGGER.trigger(entityIn, stack);
    // don't care about non-living, they skip most tool context
    if (entityIn instanceof LivingEntity living) {
      ToolStack tool = ToolStack.from(stack);
      tool.ensureHasData();
      // chestplates saved before the glider component existed cannot gain it on load, as verifyTagAfterLoad only sees the tag
      if (type == ArmorType.CHESTPLATE) {
        boolean glides = canElytraFly(stack, living);
        if (glides != stack.has(DataComponents.GLIDER)) {
          if (glides) {
            stack.set(DataComponents.GLIDER, Unit.INSTANCE);
          } else {
            stack.remove(DataComponents.GLIDER);
          }
        }
      }
      List<ModifierEntry> modifiers = tool.getModifierList();
      if (!modifiers.isEmpty()) {
        boolean isCorrectSlot = living.getItemBySlot(type.getSlot()) == stack;
        // we pass in the stack for most custom context, but for the sake of armor its easier to tell them that this is the correct slot for effects
        for (ModifierEntry entry : modifiers) {
          entry.getHook(ModifierHooks.INVENTORY_TICK).onInventoryTick(tool, entry, levelIn, living, slot == null ? -1 : slot.ordinal(), false, isCorrectSlot, stack);
        }
      }
    }
  }
  public boolean overrideStackedOnOther(ItemStack held, Slot slot, ClickAction action, Player player) {
    return SlotStackModifierHook.overrideStackedOnOther(held, slot, action, player) || super.overrideStackedOnOther(held, slot, action, player);
  }
  public boolean overrideOtherStackedOnMe(ItemStack slotStack, ItemStack held, Slot slot, ClickAction action, Player player, SlotAccess access) {
    return SlotStackModifierHook.overrideOtherStackedOnMe(slotStack, held, slot, action, player, access) || super.overrideOtherStackedOnMe(slotStack, held, slot, action, player, access);
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
  public List<Component> getStatInformation(IToolStackView tool, @Nullable Player player, List<Component> tooltips, TooltipKey key, TooltipFlag tooltipFlag) {
    tooltips = TooltipUtil.getArmorStats(tool, player, tooltips, key, tooltipFlag);
    TooltipUtil.addAttributes(this, tool, player, tooltips, TooltipUtil.SHOW_ARMOR_ATTRIBUTES, type.getSlot());
    return tooltips;
  }
  public int getDefaultTooltipHideFlags(ItemStack stack) {
    return TooltipUtil.getModifierHideFlags(getToolDefinition());
  }

  /* Display items */
  public ItemStack getRenderTool() {
    if (toolForRendering == null) {
      toolForRendering = ToolBuildHandler.buildToolForRendering(this, this.getToolDefinition());
    }
    return toolForRendering;
  }
}
