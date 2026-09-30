package slimeknights.tconstruct.tools.modules.cosmetic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.loadable.record.SingletonLoader;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.display.DisplayNameModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.module.HookProvider;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.utils.TinkerTooltipFlags;
import slimeknights.tconstruct.library.utils.Util;

import javax.annotation.Nullable;
import java.util.List;

/** Module for banner pattern tooltips */
public enum BannerModule implements ModifierModule, DisplayNameModifierHook, TooltipModifierHook {
  INSTANCE;

  private static final List<ModuleHook<?>> DEFAULT_HOOKS = HookProvider.<BannerModule>defaultHooks(ModifierHooks.DISPLAY_NAME, ModifierHooks.TOOLTIP);
  public static final RecordLoadable<BannerModule> LOADER = new SingletonLoader<>(INSTANCE);
  /** Key for a dye color, stored as its ID */
  public static final String KEY_DYE = "dye";
  /** Key for a pattern color, as a 24 bit integer */
  public static final String KEY_COLOR = "color";
  /** Key for a pattern hash, from the banner pattern identifier */
  public static final String KEY_PATTERN = "pattern";
  /** Tooltip key saying hold shift for patterns */
  private static final Component HOLD_SHIFT = TConstruct.makeTranslation("modifier", "banner.hold_shift").withStyle(ChatFormatting.GRAY);

  @Override
  public RecordLoadable<? extends ModifierModule> getLoader() {
    return LOADER;
  }

  @Override
  public List<ModuleHook<?>> getDefaultHooks() {
    return DEFAULT_HOOKS;
  }

  @Override
  public Component getDisplayName(IToolStackView tool, ModifierEntry entry, Component name, @Nullable RegistryAccess access) {
    // color the tooltip the color of the first pattern
    ListTag patterns = tool.getPersistentData().getList(patternKey(entry.getId()), ListTag.TAG_COMPOUND);
    if (!patterns.isEmpty()) {
      return name.copy().withStyle(name.getStyle().withColor(DyeColor.byId(patterns.getCompound(0).flatMap(tag -> tag.getInt(KEY_DYE)).orElse(0)).getTextColor()));
    }
    return name;
  }

  @Override
  public void addTooltip(IToolStackView tool, ModifierEntry modifier, @Nullable Player player, List<Component> tooltip, TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
    // add all patterns in a tineer station when holding
    if (tooltipFlag == TinkerTooltipFlags.TINKER_STATION) {
      if (tooltipKey == TooltipKey.SHIFT) {
        ListTag patterns = tool.getPersistentData().getList(patternKey(modifier.getId()), ListTag.TAG_COMPOUND);
        for (int i = 0; i < patterns.size(); i++) {
          CompoundTag tag = patterns.getCompound(i).orElseGet(CompoundTag::new);
          DyeColor dye = DyeColor.byId(tag.getInt(KEY_DYE).orElse(0));
          Identifier patternId = Identifier.tryParse(tag.getString(KEY_PATTERN).orElse(""));
          RegistryAccess access = player == null ? null : player.registryAccess();
          Holder<BannerPattern> holder = patternId == null || access == null ? null : access.lookupOrThrow(Registries.BANNER_PATTERN).get(patternId).map(h -> (Holder<BannerPattern>)h).orElse(null);
          if (holder != null) {
            // note that Forge is dumb in BannerItem with their patch - mojang already adds the mod ID to the tooltip key
            holder.unwrapKey().ifPresent(key ->
              tooltip.add(Component.translatable("block.minecraft.banner." + key.identifier().toShortLanguageKey() + '.' + dye.getName()).withStyle(ChatFormatting.GRAY)));

          }
        }
      } else {
        tooltip.add(HOLD_SHIFT);
      }
    }
  }

  /** Gets the key for the cache used in the model */
  public static Identifier cacheKey(ModifierId modifier) {
    return modifier.withSuffix("_cache");
  }

  /** Gets the key for the pattern list in NBT */
  public static Identifier patternKey(ModifierId modifier) {
    return modifier.withSuffix("_patterns");
  }

  /** Copies the given banner pattern component to the tool's NBT. A null dye skips the base color, making a clear banner. */
  public static void copyPatterns(ModDataNBT data, ModifierId id, @Nullable DyeColor dye, BannerPatternLayers banner) {
    ListTag patterns = new ListTag();
    for (BannerPatternLayers.Layer layer : banner.layers()) {
      CompoundTag tag = new CompoundTag();
      tag.putString("Pattern", layer.pattern().unwrapKey().map(key -> key.identifier().toString()).orElse(""));
      tag.putInt("Color", layer.color().getId());
      patterns.add(tag);
    }
    copyPatterns(data, id, dye, patterns);
  }
  /** Copies the given list of patterns from banner format to the tool's NBT. A null dye skips the base color, making a clear banner. */
  public static void copyPatterns(ModDataNBT data, ModifierId id, @Nullable DyeColor dye, ListTag banner) {
    ListTag patterns = new ListTag();

    // need a cache key, but it's just going to get hashed anyway, so store its hash
    int hashCode = 0;

    // parity (oracle): official 3.12.1 stores the banner's base color as a "base" pattern first; banner items only carry
    // the layers on top of it. Continuum dropped this layer, so shields lost their base color, the name took the first
    // pattern's color and the clear banner recipe had nothing to clear. Passing a null dye skips it (clear banner).
    if (dye != null) {
      int baseColor = Util.getColor(dye);
      hashCode = baseColor;
      CompoundTag basePattern = new CompoundTag();
      basePattern.putString(KEY_PATTERN, BannerPatterns.BASE.identifier().toString());
      basePattern.putInt(KEY_DYE, dye.getId());
      basePattern.putInt(KEY_COLOR, baseColor);
      patterns.add(basePattern);
    }

    // add in all other patterns
    for (int i = 0; i < banner.size(); i++) {
      CompoundTag original = banner.getCompound(i).orElseGet(CompoundTag::new);
      CompoundTag copy = new CompoundTag();
      // copy the pattern as is
      String pattern = original.getString("Pattern").orElse("");
      copy.putString(KEY_PATTERN, pattern);
      // convert the color from a dye color to an integer
      dye = DyeColor.byId(original.getInt("Color").orElse(0));
      int color = Util.getColor(dye);
      copy.putInt(KEY_DYE, dye.getId()); // dye for the tooltip
      copy.putInt(KEY_COLOR, color); // color for the model
      // add the values
      patterns.add(copy);
      // update the hash code with the new information
      hashCode = 31 * (31 * hashCode + color) + pattern.hashCode();
    }

    // add to tool NBT
    data.put(patternKey(id), patterns);
    data.putInt(cacheKey(id), hashCode);
  }
}
