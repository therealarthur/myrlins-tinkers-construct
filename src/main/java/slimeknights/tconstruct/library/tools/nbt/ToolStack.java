package slimeknights.tconstruct.library.tools.nbt;

import com.google.common.collect.ImmutableSet;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus.Internal;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.modules.build.RarityModule;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierManager;
import slimeknights.tconstruct.library.modifiers.hook.build.ModifierTraitHook.TraitBuilder;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.context.ToolRebuildContext;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.ToolDefinitionData;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.definition.module.build.ToolStatsHooks;
import slimeknights.tconstruct.library.tools.definition.module.build.ToolVolatileDataHooks;
import slimeknights.tconstruct.library.tools.definition.module.material.MissingMaterialsToolHook;
import slimeknights.tconstruct.library.tools.definition.module.mining.MiningTierToolHook;
import slimeknights.tconstruct.library.tools.helper.TooltipUtil;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.item.armor.ModifiableArmorItem;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.library.utils.RestrictedCompoundTag;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Class handling parsing all tool related NBT
 */
public class ToolStack implements IToolStackView {
  /** Error messages for when there are not enough remaining modifiers */
  private static final String KEY_VALIDATE_SLOTS = TConstruct.makeTranslationKey("recipe", "modifier.validate_slots");

  // persistent NBT
  /** Tag for list of materials */
  public static final String TAG_MATERIALS = "tic_materials";
  /** Tag for extra arbitrary modifier data */
  public static final String TAG_PERSISTENT_MOD_DATA = "tic_persistent";
  /** Tag for recipe based modifier */
  public static final String TAG_UPGRADES = "tic_upgrades";
  /** Tag marking a tool as broken */
  public static final String TAG_BROKEN = "tic_broken";

  // volatile NBT
  /** Tag for calculated stats */
  protected static final String TAG_STATS = "tic_stats";
  /** Tag for tool stat global multipliers */
  protected static final String TAG_MULTIPLIERS = "tic_multipliers";
  /** Tag for arbitrary modifier data rebuilt on stat rebuild */
  public static final String TAG_VOLATILE_MOD_DATA = "tic_volatile_data"; // TODO: consider dropping "_data" from the key for consistency
  /** Tag for merged modifiers of upgrades and traits */
  public static final String TAG_MODIFIERS = "tic_modifiers";

  // vanilla tags
  protected static final String TAG_DAMAGE = "Damage";
  private static final String TAG_UNBREAKABLE = "Unbreakable";
  private static final String TAG_HIDE_FLAGS = "HideFlags";

  /** List of tags to disallow editing for the relevant modifier hooks, disallows all tags we touch. Ignores unbreakable as we only look at that tag for vanilla compat */
  private static final Set<String> RESTRICTED_TAGS = ImmutableSet.of(TAG_MATERIALS, TAG_STATS, TAG_MULTIPLIERS, TAG_PERSISTENT_MOD_DATA, TAG_VOLATILE_MOD_DATA, TAG_UPGRADES, TAG_MODIFIERS, TAG_BROKEN, TAG_DAMAGE, TAG_HIDE_FLAGS);

  /** Item representing this tool */
  private final Item item;
  /** Tool definition, describing part count and alike */
  private final ToolDefinition definition;
  /** Original tool NBT */
  private CompoundTag nbt;
  /** Public view of the internal NBT, to give to modifier hooks */
  private CompoundTag restrictedNBT;

  @Override
  public ToolDefinition getDefinition() {
    return definition;
  }

  @Override
  public net.minecraft.world.item.Item getItem() {
    return item;
  }

  public CompoundTag getNbt() {
    return nbt;
  }

  // durability
  /** Current damage of the tool, -1 means unloaded */
  private int damage = -1;
  /** If true, tool is broken. Null means unloaded */
  @Nullable
  private Boolean broken;

  // tool data: these properties describe the tool
  /** Data object containing materials */
  @Nullable
  private MaterialNBT materials;
  /** Upgrades are modifiers that come from recipes. Abilities are included with these in NBT */
  @Nullable
  private ModifierNBT upgrades;
  /** Data object containing modifier data that persists on stat rebuild */
  @Nullable
  private ToolDataNBT persistentModData;

  // nbt cache: these values are calculated tool data
  /** Combination of modifiers from upgrades and material traits */
  @Nullable
  private ModifierNBT modifiers;
  /** Data object containing the original tool stats */
  @Nullable
  private StatsNBT stats;
  /** Data object containing stat multipliers for each stat */
  @Nullable
  private MultiplierNBT multipliers;
  /** Data object containing modifier data that is recreated when the modifier list changes */
  @Nullable
  private IModDataView volatileModData;

  /**
   * Updates vanilla item components that gate damageability in MC 26.1.
   * Tinkers still stores the real dynamic durability in tool NBT/stats, but vanilla now requires
   * MAX_DAMAGE and DAMAGE components to treat the stack as damageable at all.
   */
  private void updateStackComponents(ItemStack stack) {
    if (getStats().getInt(ToolStats.DURABILITY) > 0 && hasTag(TinkerTags.Items.DURABILITY)) {
      stack.set(DataComponents.MAX_STACK_SIZE, 1);
      stack.set(DataComponents.MAX_DAMAGE, getStats().getInt(ToolStats.DURABILITY));
      stack.set(DataComponents.DAMAGE, getDamage());
    } else {
      stack.remove(DataComponents.MAX_DAMAGE);
      stack.remove(DataComponents.DAMAGE);
    }
    if (hasTag(TinkerTags.Items.HARVEST)) {
      stack.set(DataComponents.TOOL, buildToolComponent());
    }
    // 26.1 gates gliding on DataComponents.GLIDER, the IItemExtension#canElytraFly hook no longer exists
    if (!isBroken() && getVolatileData().getBoolean(ModifiableArmorItem.ELYTRA)) {
      stack.set(DataComponents.GLIDER, Unit.INSTANCE);
    } else {
      stack.remove(DataComponents.GLIDER);
    }
    // 26.1 colors the item name from DataComponents.RARITY, not Item.getRarity(ItemStack)
    RarityModule.applyToStack(stack);
  }

  /** Builds a vanilla tool component so external mods checking DataComponents.TOOL see Tinkers' dynamic mining tier. */
  private Tool buildToolComponent() {
    Object tier = MiningTierToolHook.getTier(this);
    ToolMaterial material = tier instanceof ToolMaterial toolMaterial ? toolMaterial : ToolMaterial.WOOD;
    return new Tool(List.of(
      Tool.Rule.deniesDrops(HolderSet.emptyNamed(BuiltInRegistries.BLOCK, material.incorrectBlocksForDrops())),
      Tool.Rule.minesAndDrops(HolderSet.emptyNamed(BuiltInRegistries.BLOCK, BlockTags.MINEABLE_WITH_PICKAXE), getStats().get(ToolStats.MINING_SPEED)),
      Tool.Rule.minesAndDrops(HolderSet.emptyNamed(BuiltInRegistries.BLOCK, BlockTags.MINEABLE_WITH_AXE), getStats().get(ToolStats.MINING_SPEED)),
      Tool.Rule.minesAndDrops(HolderSet.emptyNamed(BuiltInRegistries.BLOCK, BlockTags.MINEABLE_WITH_SHOVEL), getStats().get(ToolStats.MINING_SPEED)),
      Tool.Rule.minesAndDrops(HolderSet.emptyNamed(BuiltInRegistries.BLOCK, BlockTags.MINEABLE_WITH_HOE), getStats().get(ToolStats.MINING_SPEED))
    ), 1.0F, 1, true);
  }

  /* Creating */
  private ToolStack(Item item, ToolDefinition definition, CompoundTag nbt) {
    this.item = item;
    this.definition = definition;
    this.nbt = nbt;
  }


  /**
   * Creates a new tool stack from item and NBT
   * @param item        Item instance
   * @param definition  Item tool definition
   * @param nbt         Tool stack NBT
   * @return  Tool stack instance
   */
  public static ToolStack from(Item item, ToolDefinition definition, CompoundTag nbt) {
    return new ToolStack(item, definition, nbt);
  }

  /**
   * Creates a tool stack from an item stack
   * @param stack    Base stack
   * @param copyNbt  If true, NBT is copied from the stack
   * @return  Tool stack
   */
  private static ToolStack from(ItemStack stack, boolean copyNbt) {
    Item item = stack.getItem();
    ToolDefinition definition = item instanceof IModifiable mod
                                ? mod.getToolDefinition()
                                : ToolDefinition.EMPTY;
    CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
    CompoundTag nbt = customData != null ? customData.copyTag() : null;
    if (nbt == null) {
      nbt = new CompoundTag();
      if (!copyNbt) {
        // only a wrongly made tool will have an empty definition. check preferred to a tag check as tags may not be loaded when this is first called
        if (definition != ToolDefinition.EMPTY) {
          // bypass the setter as vanilla insists on setting damage values there, along with verifying the tag
          // both are things we will do later, doing so now causes us to recursively call this method (though not infinite)
          stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
          // no need to set the damage value, if the tool wanted it set the stack would have had a tag already
        } else {
          switch (Config.COMMON.logInvalidToolStack.get()) {
            case STACKTRACE ->
              TConstruct.LOG.warn("Tool stack constructed using non-modifiable tool, this may cause issues as it has no NBT. Stacktrace can be disabled in config.", new Exception("Stack trace"));
            case WARNING ->
              TConstruct.LOG.warn("Tool stack constructed using non-modifiable tool, this may cause issues as it has no NBT. To debug this issue or disable the warning, use logInvalidToolStack in the config.");
          }
        }
      }
    } else if (copyNbt) {
      nbt = nbt.copy();
    }
    return from(item, definition, nbt);
  }

  /**
   * Creates a tool stack from the given item stack, not copying NBT
   * @param stack  Stack
   * @return  Tool stack
   */
  public static ToolStack from(ItemStack stack) {
    return from(stack, false);
  }

  /**
   * Creates a tool stack from the given item stack, copying the NBT
   * @param stack  Stack
   * @return  Tool stack
   */
  public static ToolStack copyFrom(ItemStack stack) {
    return from(stack, true);
  }

  /**
   * Creates a new tool stack for a completely new tool
   * @param item        Item
   * @param definition  Tool definition
   * @param materials  Materials list
   * @return  Tool stack
   */
  public static ToolStack createTool(Item item, ToolDefinition definition, MaterialNBT materials) {
    ToolStack tool = from(item, definition, new CompoundTag());
    // set cached to empty, saves a NBT lookup or two
    tool.damage = 0;
    tool.broken = false;
    tool.upgrades = ModifierNBT.EMPTY;
    // update the materials, this will also rebuild the stats
    tool.setMaterials(materials);
    return tool;
  }

  /**
   * Creates a copy of this tool to prevent modifications to the original.
   * Will copy over cached parsed NBT when possible, making this more efficient than calling {@link #copyFrom(ItemStack)}.
   * @return  Copy of this tool
   */
  public ToolStack copy() {
    ToolStack tool = from(item, definition, nbt.copy());
    // copy over relevant loaded data
    tool.damage = this.damage;
    tool.broken = this.broken;
    tool.materials = this.materials;
    tool.upgrades = this.upgrades;
    tool.modifiers = this.modifiers;
    tool.stats = this.stats;
    // skipping mod data as those are mutable, so not safe to share the same instance
    return tool;
  }

  /** Clears all cached data, used with capabilities to prevent cached data from being out of sync due to external changes */
  public void clearCache() {
    this.damage = -1;
    this.broken = null;
    this.materials = null;
    this.upgrades = null;
    this.modifiers = null;
    this.stats = null;
    this.multipliers = null;
    this.volatileModData = null;
    this.persistentModData = null;
  }

  /** Updates the tool stack instance to match the given item stack */
  @Internal
  public void refreshTag(ItemStack stack) {
    CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
    CompoundTag tag = customData != null ? customData.copyTag() : new CompoundTag();
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    this.nbt = tag;
    clearCache();
  }

  /** Creates an item stack from this tool stack */
  public ItemStack createStack(int size) {
    ItemStack stack = new ItemStack(item, size);
    // set the raw tag to avoid going through verifyTagAfterLoad and rebuilding stats again
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
    updateStackComponents(stack);
    // damage value is already enforced via the stack creation above
    return stack;
  }

  /** Creates an item stack from this tool stack */
  public ItemStack createStack() {
    return createStack(1);
  }

  /**
   * Sets the NBT on the given stack
   * @param stack  Stack instance
   * @return  New NBT
   */
  public ItemStack updateStack(ItemStack stack) {
    return updateStack(stack, true);
  }

  /**
   * Sets the NBT on the given stack
   * @param stack  Stack instance
   * @param copyNBT  If true, copies the NBT
   * @return  New NBT
   */
  public ItemStack updateStack(ItemStack stack, boolean copyNBT) {
    if (stack.getItem() != item) {
      throw new IllegalArgumentException("Wrong item in stack");
    }
    CompoundTag stackTag = copyNBT ? nbt.copy() : nbt;
    // ensure the damage value is set on the stack for the sake of stacking, since bypassing the vanilla setter skips that
    if (!stackTag.contains(TAG_DAMAGE) && stack.getItem().isDamageable(stack)) {
      stackTag.putInt(TAG_DAMAGE, 0);
    }
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(stackTag));
    updateStackComponents(stack);
    return stack;
  }

  /** Creates a stack a copy of the given stack */
  public ItemStack copyStack(ItemStack stack) {
    return updateStack(stack.copy(), false);
  }

  /** Creates a stack a copy of the given stack with size no greater than the passed amount */
  public ItemStack copyStack(ItemStack stack, int size) {
    return updateStack(stack.copyWithCount(size), false);
  }

  /**
   * Gets a restricted view of the tools NBT
   * @return  Tool NBT without access to internal tags
   */
  public RestrictedCompoundTag getRestrictedData() {
    return new RestrictedCompoundTag(getRestrictedNBT(), RESTRICTED_TAGS);
  }

  public CompoundTag getRestrictedNBT() {
    if (restrictedNBT == null) {
      restrictedNBT = nbt.copy();
    }
    return restrictedNBT;
  }

  @Override
  public boolean isSameStack(ItemStack stack) {
    // tool stacks share NBT with their stack instance unless copied so changes are mirrored
    // item check allows empty as empty stacks change their item to air. This won't false positive with ItemStack#EMPTY as the NBT won't match.
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    CompoundTag stackTag = data != null ? data.copyTag() : null;
    return Objects.equals(nbt, stackTag) && (stack.isEmpty() || stack.getItem() == item);
  }


  /* Durability */

  /**
   * Checks if this tool is currently broken
   * @return  True if broken
   */
  @Override
  public boolean isBroken() {
    if (broken == null) {
      broken = nbt.getBoolean(TAG_BROKEN).orElse(false);
    }
    return broken;
  }

  @Override
  public boolean isUnbreakable() {
    return nbt.getBoolean(TAG_UNBREAKABLE).orElse(false);
  }

  /**
   * Sets the broken state on the tool
   * @param broken  New broken value
   */
  protected void setBrokenRaw(boolean broken) {
    this.broken = broken;
    nbt.putBoolean(TAG_BROKEN, broken);
  }

  /**
   * Breaks the tool
   */
  protected void breakTool() {
    setDamage(getStats().getInt(ToolStats.DURABILITY));
  }

  /**
   * Gets damage, ignoring broken checks
   * @return  Damage ignoring broken state
   */
  protected int getDamageRaw() {
    if (damage == -1) {
      damage = nbt.getInt(TAG_DAMAGE).orElse(0);
    }
    return damage;
  }

  /**
   * Gets the tools current damage from NBT
   * @return  Current damage
   */
  @Override
  public int getDamage() {
    // if broken, return full damage
    int durability = getStats().getInt(ToolStats.DURABILITY);
    if (isBroken()) {
      return durability;
    }
    // ensure we never return a number larger than max
    return Math.min(getDamageRaw(), durability - 1);
  }

  /**
   * Gets the current durability remaining for this tool
   * @return  Tool durability
   */
  @Override
  public int getCurrentDurability() {
    if (isBroken()) {
      return 0;
    }
    // ensure we never return a number smaller than 0
    return Math.max(0, getStats().getInt(ToolStats.DURABILITY) - getDamageRaw());
  }

  /**
   * Sets the tools damage
   * @param  damage  New damage
   */
  @Override
  public void setDamage(int damage) {
    int durability = getStats().getInt(ToolStats.DURABILITY);
    if (damage >= durability) {
      damage = Math.max(0, durability);
      setBrokenRaw(true);
    } else {
      setBrokenRaw(false);
    }
    this.damage = damage;
    nbt.putInt(TAG_DAMAGE, damage);
  }

  /* Stats */

  /**
   * Gets the tool stats if parsed, or parses from NBT if not yet parsed
   * @return stats
   */
  @Override
  public StatsNBT getStats() {
    if (stats == null) {
      stats = StatsNBT.readFromNBT(nbt.get(TAG_STATS));
    }
    return stats;
  }

  /**
   * Sets the tool stats, and stores it in NBT
   * @param stats  Stats instance
   */
  protected void setStats(StatsNBT stats) {
    this.stats = stats;
    nbt.put(TAG_STATS, stats.serializeToNBT());
    // if we no longer have enough durability, decrease the damage and mark it broken
    int newMax = getStats().getInt(ToolStats.DURABILITY);
    if (getDamageRaw() >= newMax) {
      setDamage(newMax);
    }
  }

  @Override
  public MultiplierNBT getMultipliers() {
    if (multipliers == null) {
      multipliers = MultiplierNBT.readFromNBT(nbt.get(TAG_MULTIPLIERS));
    }
    return multipliers;
  }

  /**
   * Sets the tool multipliers, and stores it in NBT
   * @param multipliers  Stats instance
   */
  protected void setMultipliers(MultiplierNBT multipliers) {
    if (multipliers.getContainedStats().isEmpty()) {
      this.multipliers = MultiplierNBT.EMPTY;
      nbt.remove(TAG_MULTIPLIERS);
    } else {
      this.multipliers = multipliers;
      nbt.put(TAG_MULTIPLIERS, multipliers.serializeToNBT());
    }
  }


  /* Materials */

  @Override
  public MaterialNBT getMaterials() {
    if (!getDefinition().hasMaterials()) {
      return MaterialNBT.EMPTY;
    }
    if (materials == null) {
      materials = MaterialNBT.readFromNBT(nbt.get(TAG_MATERIALS));
    }
    return materials;
  }

  /**
   * Sets the materials without updating the tool stats
   * @param materials  New materials
   */
  protected void setMaterialsRaw(MaterialNBT materials) {
    this.materials = materials;
    if (materials == MaterialNBT.EMPTY) {
      this.nbt.remove(TAG_MATERIALS);
    } else {
      this.nbt.put(TAG_MATERIALS, materials.serializeToNBT());
    }
  }

  /**
   * Sets the materials on this tool stack, updating tool stats
   * @param materials  New materials NBT
   */
  public void setMaterials(MaterialNBT materials) {
    setMaterialsRaw(materials);
    rebuildStats();
  }

  /**
   * Replaces the material at the given index
   * @param index        Index to replace
   * @param replacement  New material
   * @throws IndexOutOfBoundsException  If the index is invalid
   */
  public void replaceMaterial(int index, MaterialVariant replacement) {
    setMaterials(getMaterials().replaceMaterial(index, replacement));
  }

  /**
   * Replaces the material at the given index
   * @param index        Index to replace
   * @param replacement  New material
   * @throws IndexOutOfBoundsException  If the index is invalid
   */
  public void replaceMaterial(int index, MaterialVariantId replacement) {
    setMaterials(getMaterials().replaceMaterial(index, replacement));
  }


  /* Modifiers */

  /**
   * Gets a list of modifiers added from recipes.
   * In general you should use {@link #getModifiers()} when performing modifier actions to include traits.
   * @return  Recipe modifier list
   */
  @Override
  public ModifierNBT getUpgrades() {
    if (upgrades == null) {
      upgrades = ModifierNBT.readFromNBT(nbt.get(TAG_UPGRADES));
    }
    return upgrades;
  }

  /**
   * Updates the upgrades list on the tool
   * @param modifiers  New upgrades
   */
  public void setUpgrades(ModifierNBT modifiers) {
    this.upgrades = modifiers;
    nbt.put(TAG_UPGRADES, modifiers.serializeToNBT());
    rebuildStats();
  }

  /**
   * Adds a single modifier to this tool
   * @param modifier  Modifier to add
   * @param level     Level to add
   */
  public void addModifier(ModifierId modifier, int level) {
    if (level <= 0) {
      throw new IllegalArgumentException("Invalid level, must be above 0");
    }
    setUpgrades(getUpgrades().withModifier(modifier, level));
  }

  /**
   * Adds a single modifier to this tool
   * @param modifier  Modifier to add
   * @param amount    Amount to add
   * @param needed    Amount needed for a full level
   */
  public void addModifierAmount(ModifierId modifier, int amount, int needed) {
    if (needed <= 0) {
      throw new IllegalArgumentException("Invalid needed, must be above 0");
    }
    if (amount > 0) {
      setUpgrades(getUpgrades().addAmount(modifier, amount, needed));
    }
  }

  /**
   * Removes a single modifier to this tool
   * @param modifier  Modifier to remove
   * @param level     Level to remove
   */
  public void removeModifier(ModifierId modifier, int level) {
    if (level <= 0) {
      throw new IllegalArgumentException("Invalid level, must be above 0");
    }
    ModifierNBT newModifiers = getUpgrades().withoutModifier(modifier, level);
    this.upgrades = newModifiers;
    nbt.put(TAG_UPGRADES, newModifiers.serializeToNBT());
    rebuildStats();
  }

  @Override
  public ModifierNBT getModifiers() {
    if (modifiers == null) {
      modifiers = ModifierNBT.readFromNBT(nbt.get(TAG_MODIFIERS));
    }
    return modifiers;
  }

  /**
   * Updates the list of all modifiers in NBT, called in {@link #rebuildStats()}
   * @param modifiers  New modifiers
   */
  protected void setModifiers(ModifierNBT modifiers) {
    this.modifiers = modifiers;
    nbt.put(TAG_MODIFIERS, this.modifiers.serializeToNBT());
  }


  /* Data */

  @Override
  public ToolDataNBT getPersistentData() {
    if (persistentModData == null) {
      // parse if the tag already exists
      if (nbt.contains(TAG_PERSISTENT_MOD_DATA)) {
        persistentModData = ToolDataNBT.readFromNBT(nbt.getCompound(TAG_PERSISTENT_MOD_DATA).orElse(new CompoundTag()));
      } else {
        // if no tag exists, create it
        CompoundTag tag = new CompoundTag();
        nbt.put(TAG_PERSISTENT_MOD_DATA, tag);
        persistentModData = ToolDataNBT.readFromNBT(tag);
      }
    }
    return persistentModData;
  }

  @Override
  public IModDataView getVolatileData() {
    if (volatileModData == null) {
      // parse if the tag already exists
      if (nbt.contains(TAG_VOLATILE_MOD_DATA)) {
        volatileModData = ToolDataNBT.readFromNBT(nbt.getCompound(TAG_VOLATILE_MOD_DATA).orElse(new CompoundTag()));
      } else {
        // if no tag exists, return empty
        volatileModData = IModDataView.EMPTY;
      }
    }
    return volatileModData;
  }

  /**
   * Updates the volatile mod data in NBT, called in {@link #rebuildStats()}
   * @param modData  New data
   */
  protected void setVolatileModData(ToolDataNBT modData) {
    CompoundTag data = modData.getData();
    if (data.isEmpty()) {
      volatileModData = IModDataView.EMPTY;
      nbt.remove(TAG_VOLATILE_MOD_DATA);
    } else {
      volatileModData = modData;
      nbt.put(TAG_VOLATILE_MOD_DATA, data);
    }
  }


  /* Utilities */

  @Nullable
  public Component tryValidate() {
    // first check slot counts
    for (SlotType slotType : SlotType.getAllSlotTypes()) {
      if (getFreeSlots(slotType) < 0) {
        return Component.translatable(KEY_VALIDATE_SLOTS, slotType.getDisplayName());
      }
    }
    // next, ensure modifiers validate
    Component result;
    for (ModifierEntry entry : getModifiers()) {
      result = entry.getHook(ModifierHooks.VALIDATE).validate(this, entry);
      if (result != null) {
        return result;
      }
    }
    // some validations should only run if the modifier was crafted on the tool
    for (ModifierEntry entry : getUpgrades()) {
      result = entry.getHook(ModifierHooks.VALIDATE_UPGRADE).validate(this, entry);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  /** Called on inventory tick to ensure the tool has all required data including materials and starting slots, prevents tools with no stats from existing */
  public void ensureHasData() {
    // if we try initializing before datapacks load we will get garbage data
    if (definition.isDataLoaded()) {
      // check if missing materials; either means we have none or too few
      MissingMaterialsToolHook missingMaterials = definition.getHook(ToolHooks.MISSING_MATERIALS);
      boolean needsMaterials = definition.hasMaterials() && (!nbt.contains(TAG_MATERIALS) || missingMaterials.needsMaterials(definition, nbt.getList(TAG_MATERIALS).orElse(new ListTag()).size()));
      // build data if we either lack data (signified by no stats) or we lack materials but expect them
      if (needsMaterials || !isInitialized(nbt)) {
        // randomize materials if missing
        if (needsMaterials) {
          setMaterialsRaw(missingMaterials.fillMaterials(definition, getMaterials(), RandomSource.create()));
        }
        rebuildStats();
      }
    }
  }

  /**
   * Recalculates any relevant cached data. Called after either the materials or modifiers list changes
   */
  public void rebuildStats() {
    // quick safety checks: to rebuild stats we need
    // * tool definition (contains stats and traits)
    // * material registry (to fetch material stats and traits)
    // * modifier registry (run relevant modifier hooks)
    // * item tags (control tool behaviors in various places)
    // if any of these are missing, attempting to rebuild stats may corrupt the tool's state (persistent data, damage, broken)
    if (!definition.isDataLoaded() || !MaterialRegistry.isFullyLoaded() || !ModifierManager.INSTANCE.isDynamicModifiersLoaded() || !TinkerTags.isTagsLoaded()) {
      return;
    }

    // add tool slots to volatile data, ensures it is there even from an empty tool, and properly updates on datapack update
    ToolDefinitionData toolData = getDefinitionData();

    // first, determine the list of modifiers, this is done in a couple stages
    // we start by cloning upgrades and adding tool traits and material traits
    MaterialNBT materials = getMaterials();
    ModifierNBT.Builder modBuilder = ModifierNBT.builder();
    modBuilder.add(getUpgrades());
    toolData.getHook(ToolHooks.TOOL_TRAITS).addTraits(definition, materials, modBuilder);
    ModifierNBT beforeTraits = modBuilder.build();

    // temporary context while we add modifier traits, will recreate if we have modifiers
    // clear out volatile data, mostly affects the volatile data hook
    ToolRebuildContext context = new ToolRebuildContext(item, definition, materials, getUpgrades(), beforeTraits, getPersistentData());

    // if we have modifiers, apply modifier traits, saves creating some builders if empty
    List<ModifierEntry> modifierList = Collections.emptyList();
    if (beforeTraits.isEmpty()) {
      // if no modifiers, just clear modifiers
      setModifiers(ModifierNBT.EMPTY);
    } else {
      modBuilder = ModifierNBT.builder();
      TraitBuilder traitBuilder = new TraitBuilder(context, modBuilder);
      traitBuilder.add(beforeTraits);

      // set the final modifier list on the tool
      ModifierNBT allMods = modBuilder.build();
      setModifiers(allMods);
      modifierList = allMods.getModifiers();
      // context for further modifier hooks
      context = context.withModifiers(allMods);
    }

    // build volatile data first, it's a parameter to the other hooks
    ToolDataNBT volatileData = new ToolDataNBT();
    toolData.getHook(ToolHooks.VOLATILE_DATA).addVolatileData(context, volatileData);
    for (ModifierEntry entry : modifierList) {
      entry.getHook(ModifierHooks.VOLATILE_DATA).addVolatileData(context, entry, volatileData);
    }
    ToolVolatileDataHooks.apply(context, volatileData);
    setVolatileModData(volatileData);

    // regular stats last so we can include volatile data
    ModifierStatsBuilder statBuilder = ModifierStatsBuilder.builder();
    toolData.getHook(ToolHooks.TOOL_STATS).addToolStats(context, statBuilder);
    for (ModifierEntry entry : modifierList) {
      entry.getHook(ModifierHooks.TOOL_STATS).addToolStats(context, entry, statBuilder);
    }
    ToolStatsHooks.apply(context, statBuilder);
    setStats(statBuilder.build());
    setMultipliers(statBuilder.buildMultipliers());

    // finally, update raw data, called last to make the parameters more convenient mostly, plus no other hooks should be responding to this data
    for (ModifierEntry entry : modifierList) {
      entry.getHook(ModifierHooks.RAW_DATA).addRawData(this, entry, getRestrictedData());
    }
  }


  /* Static helpers */

  /**
   * Checks if the given tool stats have been initialized, used as a marker to indicate slots are not yet applied
   * @param stack  Stack to check
   * @return  True if initialized
   */
  public static boolean isInitialized(ItemStack stack) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    CompoundTag tag = data != null ? data.copyTag() : null;
    return tag != null && isInitialized(tag);
  }

  /**
   * Checks if the given tool stats have been initialized, used as a marker to indicate slots are not yet applied
   * @param tag  Tag to check
   * @return  True if initialized
   */
  public static boolean isInitialized(CompoundTag tag) {
    return tag.contains(TAG_STATS);
  }

  /**
   * Ensures the given item stack is initialized. Called in crafting hooks
   * @param stack ItemStack to initialize
   */
  public static void ensureInitialized(ItemStack stack) {
    if (stack.getItem() instanceof IModifiable modifiable) {
      ensureInitialized(stack, modifiable.getToolDefinition());
    }
  }

  /**
   * Ensures the given item stack is initialized. Intended to be called in {@link Item#onCraftedBy(ItemStack, Level, Player)}
   * @param stack           ItemStack to initialize
   * @param toolDefinition  Tool definition
   */
  public static void ensureInitialized(ItemStack stack, ToolDefinition toolDefinition) {
    // must be loaded
    if (!toolDefinition.isDataLoaded()) {
      return;
    }
    CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
    CompoundTag tag = customData != null ? customData.copyTag() : null;
    // already initialized? nothing to do
    if (tag != null && isInitialized(tag)) {
      return;
    }
    // time to initialize
    ToolStack tool = ToolStack.from(stack);
    tool.ensureHasData();
    tool.updateStack(stack, false);
  }

  /**
   * Rebuilds the item stack when loaded from NBT
   * stops things from being wrong if modifiers or materials change
   * @param item        Item to build
   * @param tag         Stack tag
   * @param definition  Tool definition
   */
  public static void verifyTag(Item item, CompoundTag tag, ToolDefinition definition) {
    // this function is sometimes called before datapack contents load, do nothing then
    if (tag.getBoolean(TooltipUtil.KEY_DISPLAY).orElse(false)) {
      return;
    }

    // resolve all material redirects
    boolean hasMaterials = MaterialRegistry.isFullyLoaded() && tag.contains(ToolStack.TAG_MATERIALS);
    if (hasMaterials) {
      MaterialIdNBT stored = MaterialIdNBT.readFromNBT(tag.getList(ToolStack.TAG_MATERIALS).orElse(new ListTag()));
      MaterialIdNBT resolved = stored.resolveRedirects();
      if (resolved != stored) {
        resolved.updateNBT(tag);
      }
    }
    // only rebuild stats if we either have materials, or we don't need materials
    if (definition.isDataLoaded() && (hasMaterials || !definition.hasMaterials())) {
      ToolStack.from(item, definition, tag).rebuildStats();
    }
  }


  /* 26.1 load verification (arthur.8) */

  /** Outcome of {@link #verifyStackAfterLoad(ItemStack)}. */
  public enum LoadVerifyResult {
    /** Not a modifiable item, empty, a display tool or not yet initialized; nothing to verify */
    SKIPPED,
    /** Datapack data (tool definitions, materials, modifiers or tags) is not loaded yet; try again later */
    NOT_READY,
    /** Derived data was already current; the stack was not touched */
    UNCHANGED,
    /** Derived data was recomputed and written back to the stack */
    UPDATED,
    /** Recomputing would have changed data that must be preserved; the stack was not touched */
    REFUSED
  }

  /** Keys that describe what the player built or did to the tool. Verification must never change them. */
  private static final List<String> LOAD_VERIFY_PRESERVED = List.of(TAG_UPGRADES, TAG_DAMAGE, TAG_BROKEN, TAG_UNBREAKABLE);

  /**
   * 26.1 replacement for official {@code Item#verifyTagAfterLoad}. Official 1.20.1 re-ran {@link #verifyTag(Item, CompoundTag, ToolDefinition)}
   * every time a tool was read from NBT, so balance and data changes reached saved tools. NeoForge 26.1 decodes stacks
   * through the plain ItemStack constructor and has no item hook after load, so {@code slimeknights.tconstruct.tools.logic.ToolLoadVerification}
   * calls this once per loaded stack instead.
   * <p>
   * The work is the same as verifyTag: material redirects are resolved and derived data (modifier list from upgrades
   * and traits, volatile data, stats, multipliers, modifier raw data) is rebuilt. Safety rules on top of official:
   * <ul>
   *   <li>Runs on a copy; the stack is only written when the rebuilt data differs and passes every check below.</li>
   *   <li>Upgrades, damage, broken and unbreakable flags must be identical afterwards.</li>
   *   <li>Persistent modifier data must be identical (an absent tag counts as empty).</li>
   *   <li>The material list must keep its length, and each entry may only change to its registered redirect.</li>
   *   <li>Nothing runs before tool definitions, materials, dynamic modifiers and tags are loaded.</li>
   * </ul>
   * If a check fails the stack keeps its old data and {@link LoadVerifyResult#REFUSED} is returned.
   * @param stack  Stack to verify, updated in place when derived data changed
   * @return  What happened
   */
  public static LoadVerifyResult verifyStackAfterLoad(ItemStack stack) {
    if (stack.isEmpty() || !(stack.getItem() instanceof IModifiable modifiable)) {
      return LoadVerifyResult.SKIPPED;
    }
    CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
    if (customData == null) {
      return LoadVerifyResult.SKIPPED;
    }
    CompoundTag original = customData.copyTag();
    // uninitialized tools are handled by ensureHasData (which may also fill missing materials); display tools are fake
    if (!isInitialized(original) || original.getBoolean(TooltipUtil.KEY_DISPLAY).orElse(false)) {
      return LoadVerifyResult.SKIPPED;
    }
    ToolDefinition definition = modifiable.getToolDefinition();
    if (!definition.isDataLoaded() || !MaterialRegistry.isFullyLoaded() || !ModifierManager.INSTANCE.isDynamicModifiersLoaded() || !TinkerTags.isTagsLoaded()) {
      return LoadVerifyResult.NOT_READY;
    }

    CompoundTag verified = original.copy();
    verifyTag(stack.getItem(), verified, definition);
    // rebuildStats creates an empty persistent data tag when none existed; that is not a change worth writing
    if (!original.contains(TAG_PERSISTENT_MOD_DATA) && verified.getCompound(TAG_PERSISTENT_MOD_DATA).map(CompoundTag::isEmpty).orElse(false)) {
      verified.remove(TAG_PERSISTENT_MOD_DATA);
    }
    if (verified.equals(original)) {
      return LoadVerifyResult.UNCHANGED;
    }

    // safety: everything the player made must survive unchanged
    for (String key : LOAD_VERIFY_PRESERVED) {
      if (!Objects.equals(original.get(key), verified.get(key))) {
        return refuseLoadVerify(stack, "changed " + key);
      }
    }
    CompoundTag persistentBefore = original.getCompound(TAG_PERSISTENT_MOD_DATA).orElseGet(CompoundTag::new);
    CompoundTag persistentAfter = verified.getCompound(TAG_PERSISTENT_MOD_DATA).orElseGet(CompoundTag::new);
    if (!persistentBefore.equals(persistentAfter)) {
      return refuseLoadVerify(stack, "changed persistent modifier data");
    }
    if (!Objects.equals(original.get(TAG_MATERIALS), verified.get(TAG_MATERIALS))) {
      ListTag before = original.getList(TAG_MATERIALS).orElseGet(ListTag::new);
      ListTag after = verified.getList(TAG_MATERIALS).orElseGet(ListTag::new);
      MaterialIdNBT expected = MaterialIdNBT.readFromNBT(before).resolveRedirects();
      // readFromNBT drops entries it cannot parse, so compare against the raw list length as well
      if (before.size() != after.size() || expected.getMaterials().size() != before.size() || !expected.equals(MaterialIdNBT.readFromNBT(after))) {
        return refuseLoadVerify(stack, "changed materials beyond redirects");
      }
    }

    // write the verified data, then let the tool refresh the vanilla components that mirror it (durability, tool, glider, rarity)
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(verified));
    ToolStack.from(stack).updateStack(stack, false);
    return LoadVerifyResult.UPDATED;
  }

  /** Logs a refused load verification once per call and leaves the stack alone. */
  private static LoadVerifyResult refuseLoadVerify(ItemStack stack, String reason) {
    TConstruct.LOG.warn("Not updating saved tool {} after load: recomputing its stats {}. The tool keeps its previous data.", BuiltInRegistries.ITEM.getKey(stack.getItem()), reason);
    return LoadVerifyResult.REFUSED;
  }
}
