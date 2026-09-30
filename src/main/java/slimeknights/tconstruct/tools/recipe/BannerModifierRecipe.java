package slimeknights.tconstruct.tools.recipe;

import lombok.Getter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BannerItem;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import slimeknights.mantle.data.loadable.common.IngredientLoadable;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.IMultiRecipe;
import slimeknights.mantle.recipe.ingredient.EmptyIngredient;
import slimeknights.mantle.util.RegistryHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.json.IntRange;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.RecipeResult;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.modifiers.ModifierRecipeLookup;
import slimeknights.tconstruct.library.recipe.modifiers.adding.IDisplayModifierRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationRecipe;
import slimeknights.tconstruct.library.tools.item.IModifiableDisplay;
import slimeknights.tconstruct.library.tools.nbt.LazyToolStack;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.modules.cosmetic.BannerModule;

import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Recipe to add a banner to a shield */
public class BannerModifierRecipe implements ITinkerStationRecipe, IMultiRecipe<IDisplayModifierRecipe> {
  /** Failure when clearing a banner that has no patterns, official 3.12.1 {@code recipe.tconstruct.banner.clear.no_patterns} */
  private static final RecipeResult<LazyToolStack> NO_PATTERNS = RecipeResult.failure(TConstruct.makeTranslationKey("recipe", "banner.clear.no_patterns"));
  /**
   * parity (oracle): official 3.12.1 reads an optional {@code clear_input}; with it in the station the banner's base color is
   * left off so only the patterns go on the shield. Continuum registered the serializer with the ID alone and dropped the field.
   */
  public static final RecordLoadable<BannerModifierRecipe> LOADER = RecordLoadable.create(
    ContextKey.ID.requiredField(),
    IngredientLoadable.ALLOW_EMPTY.defaultField("clear_input", EmptyIngredient.VANILLA, false, r -> r.clearInput),
    BannerModifierRecipe::new);

  @Getter
  private final Identifier id;
  /** Ingredient that removes the base color, empty to disable clearing */
  private final Ingredient clearInput;

  public BannerModifierRecipe(Identifier id, Ingredient clearInput) {
    this.id = id;
    this.clearInput = clearInput;
    ModifierRecipeLookup.addRecipeModifier(null, TinkerModifiers.banner);
  }

  public BannerModifierRecipe(Identifier id) {
    this(id, EmptyIngredient.VANILLA);
  }

  /** True when this recipe has a clear input configured */
  private boolean hasClearInput() {
    return clearInput != EmptyIngredient.VANILLA && !clearInput.isEmpty();
  }

  @Override
  public boolean matches(ITinkerStationContainer inv, Level world) {
    // ensure this modifier can be applied
    if (!inv.getTinkerableStack().is(TinkerTags.Items.BANNER)) {
      return false;
    }
    // slots must be only banner, plus at most one clear input
    boolean found = false;
    boolean clear = false;
    boolean canClear = hasClearInput();
    for (int i = 0; i < inv.getInputCount(); i++) {
      ItemStack input = inv.getInput(i);
      if (!input.isEmpty()) {
        if (canClear && clearInput.test(input)) {
          // multiple clears
          if (clear) {
            return false;
          }
          clear = true;
        } else if (input.getItem() instanceof BannerItem) {
          if (found) {
            // multiple banners
            return false;
          }
          found = true;
        } else {
          // non-banner input
          return false;
        }
      }
    }
    return found;
  }

  @Override
  public RecipeResult<LazyToolStack> getValidatedResult(ITinkerStationContainer inv, RegistryAccess access) {
    ToolStack tool = inv.getTinkerable().copy();

    ModDataNBT persistentData = tool.getPersistentData();
    ModifierId key = TinkerModifiers.banner.getId();

    // locate the banner
    ItemStack banner = ItemStack.EMPTY;
    DyeColor dye = DyeColor.BLACK;
    for (int i = 0; i < inv.getInputCount(); i++) {
      ItemStack stack = inv.getInput(i);
      if (!stack.isEmpty() && stack.getItem() instanceof BannerItem bannerItem) {
        banner = stack;
        dye = bannerItem.getColor();
        // only need 1
        break;
      }
    }

    // should never happen
    if (banner.isEmpty()) {
      return RecipeResult.pass();
    }

    // remove the base color if the clear input is present
    if (hasClearInput()) {
      for (int i = 0; i < inv.getInputCount(); i++) {
        ItemStack stack = inv.getInput(i);
        if (!stack.isEmpty() && clearInput.test(stack)) {
          dye = null;
          break;
        }
      }
    }

    BannerPatternLayers patterns = banner.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
    // disallow no patterns when going clear
    if (dye == null && patterns.layers().isEmpty()) {
      return NO_PATTERNS;
    }

    // apply the pattern
    BannerModule.copyPatterns(tool.getPersistentData(), key, dye, patterns);

    // add the modifier if missing
    if (tool.getModifierLevel(key) == 0) {
      tool.addModifier(key, 1);
    }
    return ITinkerStationRecipe.success(tool, inv);
  }

  /** Gets the clear input, for the serializer and viewers */
  public Ingredient getClearInput() {
    return clearInput;
  }

  @Override
  public RecipeSerializer<? extends Recipe<ITinkerStationContainer>> getSerializer() {
    return TinkerModifiers.bannerModifierSerializer.get();
  }


  /* JEI */

  @Nullable
  private List<IDisplayModifierRecipe> displayRecipes;

  @Override
  public List<IDisplayModifierRecipe> getRecipes(RegistryAccess access) {
    if (displayRecipes == null) {
      List<ItemStack> toolInputs = RegistryHelper.getTagValueStream(BuiltInRegistries.ITEM, TinkerTags.Items.BANNER)
        .map(item -> {
          ItemStack stack = IModifiableDisplay.getDisplayStack(item);
          if (stack.getMaxStackSize() > 1) {
            stack = stack.copyWithCount(Math.min(stack.getMaxStackSize(), DEFAULT_TOOL_STACK_SIZE));
          }
          return stack;
        }).toList();
      if (!toolInputs.isEmpty()) {
        Identifier id = getId();
        List<IDisplayModifierRecipe> recipes = RegistryHelper.getTagValueStream(BuiltInRegistries.ITEM, ItemTags.BANNERS)
          .flatMap(item -> {
            if (item instanceof BannerItem banner) {
              return Stream.<IDisplayModifierRecipe>of(new DisplayRecipe(id, toolInputs, banner));
            }
            return Stream.empty();
          }).collect(Collectors.toList());
        // official 3.12.1 adds a second display for the clear input: every banner plus the clear item
        if (hasClearInput()) {
          List<ItemStack> banners = RegistryHelper.getTagValueStream(BuiltInRegistries.ITEM, ItemTags.BANNERS)
            .filter(item -> item instanceof BannerItem).map(ItemStack::new).toList();
          List<ItemStack> clearItems = MaterialRecipeCache.getDisplayItems(clearInput);
          if (!banners.isEmpty() && !clearItems.isEmpty()) {
            recipes.add(new ClearDisplayRecipe(id, toolInputs, banners, clearItems));
          }
        }
        displayRecipes = List.copyOf(recipes);
      } else {
        displayRecipes = List.of();
      }
    }
    return displayRecipes;
  }

  /** Display recipe instance */
  private static class DisplayRecipe implements IDisplayModifierRecipe {
    private static final IntRange LEVELS = new IntRange(1, 1);
    private final ModifierEntry RESULT = new ModifierEntry(TinkerModifiers.banner, 1);

    @Getter
    private final Identifier recipeId;
    private final List<ItemStack> banner;
    @Getter
    private final List<ItemStack> toolWithoutModifier;
    @Getter
    private final List<ItemStack> toolWithModifier;
    @Getter
    private final Component variant;
    public DisplayRecipe(Identifier recipeId, List<ItemStack> tools, BannerItem banner) {
      this.recipeId = recipeId;
      this.toolWithoutModifier = tools;
      this.banner = List.of(new ItemStack(banner));
      DyeColor dye = banner.getColor();
      this.variant = Component.translatable("color.minecraft." + dye.getSerializedName());

      ModifierId key = RESULT.getId();
      ListTag patterns = new ListTag();
      List<ModifierEntry> results = List.of(RESULT);
      toolWithModifier = tools.stream().map(stack -> IDisplayModifierRecipe.withModifiers(stack, DEFAULT_TOOL_STACK_SIZE, results, data -> BannerModule.copyPatterns(data, key, dye, patterns))).toList();
    }

    @Override
    public ModifierEntry getDisplayResult() {
      return RESULT;
    }

    @Override
    public int getInputCount() {
      return 1;
    }

    @Override
    public List<ItemStack> getDisplayItems(int slot) {
      if (slot == 0) {
        return banner;
      }
      return List.of();
    }

    @Override
    public IntRange getLevel() {
      return LEVELS;
    }
  }
  /** Display for clearing the base color, official 3.12.1 {@code recipe.tconstruct.banner.clear} variant */
  private static class ClearDisplayRecipe implements IDisplayModifierRecipe {
    private static final IntRange LEVELS = new IntRange(1, 1);
    private final ModifierEntry RESULT = new ModifierEntry(TinkerModifiers.banner, 1);

    @Getter
    private final Identifier recipeId;
    private final List<ItemStack> banners;
    private final List<ItemStack> clearInput;
    @Getter
    private final List<ItemStack> toolWithoutModifier;
    @Getter
    private final List<ItemStack> toolWithModifier;
    @Getter
    private final Component variant = TConstruct.makeTranslation("recipe", "banner.clear");

    public ClearDisplayRecipe(Identifier recipeId, List<ItemStack> tools, List<ItemStack> banners, List<ItemStack> clearInput) {
      this.recipeId = recipeId;
      this.toolWithoutModifier = tools;
      this.banners = banners;
      this.clearInput = clearInput;
      List<ModifierEntry> results = List.of(RESULT);
      ModifierId key = RESULT.getId();
      ListTag patterns = new ListTag();
      this.toolWithModifier = tools.stream().map(stack -> IDisplayModifierRecipe.withModifiers(stack, DEFAULT_TOOL_STACK_SIZE, results, data -> BannerModule.copyPatterns(data, key, (DyeColor) null, patterns))).toList();
    }

    @Override
    public ModifierEntry getDisplayResult() {
      return RESULT;
    }

    @Override
    public int getInputCount() {
      return 2;
    }

    @Override
    public List<ItemStack> getDisplayItems(int slot) {
      if (slot == 0) {
        return banners;
      }
      if (slot == 1) {
        return clearInput;
      }
      return List.of();
    }

    @Override
    public IntRange getLevel() {
      return LEVELS;
    }
  }
}
