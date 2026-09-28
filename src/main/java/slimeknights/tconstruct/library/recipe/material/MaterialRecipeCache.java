package slimeknights.tconstruct.library.recipe.material;
import slimeknights.mantle.recipe.helper.ItemOutput;
import net.minecraft.resources.Identifier;
import com.google.gson.JsonElement;
import java.util.Optional;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.common.crafting.DifferenceIngredient;
import net.neoforged.neoforge.common.crafting.IntersectionIngredient;
import slimeknights.tconstruct.common.recipe.RecipeCacheInvalidator;
import slimeknights.tconstruct.common.recipe.RecipeCacheInvalidator.DuelSidedListener;
import slimeknights.tconstruct.library.materials.IMaterialRegistry;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

/** Cache of details related to materials */
public class MaterialRecipeCache {
  /** Registry access used only to expand tag ingredients for client recipe displays. */
  private static volatile RegistryAccess DISPLAY_REGISTRY_ACCESS;

  public static void setDisplayRegistryAccess(RegistryAccess access) {
    DISPLAY_REGISTRY_ACCESS = access;
    ITEMS_BY_MATERIAL.clear();
  }
  /** Full list of recipes in the cache */
  private static final List<MaterialRecipe> RECIPES = new ArrayList<>();
  /** Lookup from item ID to recipe */
  private static final Map<Item, MaterialRecipe> RECIPE_BY_ITEM = new ConcurrentHashMap<>();
  /** Lookup from material variant ID to recipe */
  private static final Multimap<MaterialVariantId, MaterialRecipe> RECIPES_BY_MATERIAL = HashMultimap.create();
  /** Map from material variant ID to item stack list for display */
  private static final Map<MaterialVariantId, List<ItemStack>> ITEMS_BY_MATERIAL = new ConcurrentHashMap<>();

  /** Mapping from material ID to all variants for the material */
  private static final Multimap<MaterialId, MaterialVariantId> KNOWN_VARIANTS = HashMultimap.create();
  /** List of all material variants in sorted order. See also {@link IMaterialRegistry#getVisibleMaterials()} */
  @Nullable
  private static List<MaterialVariantId> SORTED_VARIANTS = null;
  /** List of all hidden material variants in sorted order. See also {@link IMaterialRegistry#getVisibleMaterials()} */
  @Nullable
  private static List<MaterialVariantId> HIDDEN_VARIANTS = null;

  /** Listener for clearing the cache */
  private static final DuelSidedListener LISTENER = RecipeCacheInvalidator.addDuelSidedListener(() -> {
    RECIPES.clear();
    RECIPE_BY_ITEM.clear();
    RECIPES_BY_MATERIAL.clear();
    ITEMS_BY_MATERIAL.clear();
    KNOWN_VARIANTS.clear();
    SORTED_VARIANTS = null;
    HIDDEN_VARIANTS = null;
  });

  /** Registers a recipe with the cache */
  public static void registerRecipe(MaterialRecipe recipe) {
    if (recipe.getValue() > 0) {
      // ensure c ache does not need to be cleared
      LISTENER.checkClear();
      // add recipe for item lookup; too early to resolve ingredient
      RECIPES.add(recipe);
      // mark the variant as known
      MaterialVariantId variant = recipe.getMaterial().getVariant();
      addKnownVariant(variant);
      // add lookup for the variant
      RECIPES_BY_MATERIAL.put(variant, recipe);
    }
  }

  /** Rebuilds the cache from an already-loaded recipe list, used when the client receives recipes from a server. */
  public static void rebuildRecipes(Collection<MaterialRecipe> recipes) {
    LISTENER.clearCache();
    for (MaterialRecipe recipe : recipes) {
      registerRecipe(recipe);
    }
  }

  /**
   * Locates a recipe by stack
   * @param stack  Stack to check
   * @return Recipe, or {@link MaterialRecipe#EMPTY} if no match.
   */
  public static MaterialRecipe findRecipe(ItemStack stack) {
    if (stack.isEmpty()) {
      return MaterialRecipe.EMPTY;
    }
    return RECIPE_BY_ITEM.computeIfAbsent(stack.getItem(), item -> {
      for (MaterialRecipe recipe : RECIPES) {
        if (recipe.getIngredient().test(stack)) {
          return recipe;
        }
      }
      return MaterialRecipe.EMPTY;
    });
  }

  /** Gets a list of all material recipes */
  public static Collection<MaterialRecipe> getAllRecipes() {
    return RECIPES;
  }

  /** Gets all recipes for the given material variant */
  public static Collection<MaterialRecipe> getRecipes(MaterialVariantId variant) {
    return RECIPES_BY_MATERIAL.get(variant);
  }

  /** Cache lookup function for items by materials */
  private static final Function<MaterialVariantId,List<ItemStack>> GET_ITEMS_BY_MATERIAL = variant ->
    getRecipes(variant).stream().flatMap(r -> {
      Stream<ItemStack> stacks = getDisplayItems(r.getIngredient()).stream();
      // if we need multiple, increase the stack size of the display stacks
      if (r.needed > r.value) {
        int size = (r.needed + r.value - 1) / r.value;
        stacks = stacks.map(stack -> stack.copyWithCount(size));
      }
      return stacks;
    }).toList();

  public static List<ItemStack> getDisplayItems(Ingredient ingredient) {
    if (ingredient.isCustom()) {
      var custom = ingredient.getCustomIngredient();
      if (custom instanceof DifferenceIngredient difference) {
        return resolveDisplayItems(difference.base()).stream()
          .filter(stack -> !matchesDisplayIngredient(difference.subtracted(), stack))
          .toList();
      }
      if (custom instanceof CompoundIngredient compound) {
        return compound.children().stream()
          .flatMap(child -> resolveDisplayItems(child).stream())
          .toList();
      }
      if (custom instanceof IntersectionIngredient intersection && !intersection.children().isEmpty()) {
        List<ItemStack> items = new ArrayList<>(resolveDisplayItems(intersection.children().get(0)));
        for (int i = 1; i < intersection.children().size(); i++) {
          Ingredient child = intersection.children().get(i);
          items.removeIf(stack -> !matchesDisplayIngredient(child, stack));
        }
        return items;
      }
      try {
        // items() supplies candidate item identities, not component-bearing display stacks.
        // In particular, material and data-component ingredients must retain their display's components.
        RegistryAccess access = DISPLAY_REGISTRY_ACCESS;
        var context = new ContextMap.Builder()
          .withParameter(SlotDisplayContext.REGISTRIES, access == null ? RegistryAccess.EMPTY : access)
          .create(SlotDisplayContext.CONTEXT);
        return custom.display().resolveForStacks(context).stream().filter(stack -> !stack.isEmpty()).toList();
      } catch (UnsupportedOperationException | IllegalStateException ignored) {
        return List.of();
      }
    }
    return resolveDisplayItems(ingredient);
  }

  /** Resolves an ItemOutput tag to all concrete item stacks for JEI display. */
  public static List<ItemStack> getDisplayItems(ItemOutput output) {
    if (output == null) {
      return List.of();
    }
    try {
      JsonElement serialized = output.serialize(false);
      RegistryAccess access = DISPLAY_REGISTRY_ACCESS;
      if (access != null && serialized != null && serialized.isJsonObject()) {
        JsonElement tagElement = serialized.getAsJsonObject().get("tag");
        if (tagElement != null) {
          TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(tagElement.getAsString()));
          return access.lookupOrThrow(Registries.ITEM).get(tag)
            .map(holders -> holders.stream().map(ItemStack::new).toList())
            .orElseGet(List::of);
        }
      }
    } catch (RuntimeException ignored) {
      // Fall through to the preferred concrete stack for ordinary outputs.
    }
    ItemStack stack = output.get();
    return stack.isEmpty() ? List.of() : List.of(stack);
  }
  private static List<ItemStack> resolveDisplayItems(Ingredient ingredient) {
    if (ingredient.isCustom()) {
      return getDisplayItems(ingredient);
    }
    RegistryAccess access = DISPLAY_REGISTRY_ACCESS;
    Optional<TagKey<Item>> tag = ingredient.getValues().unwrapKey();
    if (access != null && tag.isPresent()) {
      return access.lookupOrThrow(Registries.ITEM).get(tag.get())
        .map(holders -> holders.stream().map(ItemStack::new).toList())
        .orElseGet(List::of);
    }
    try {
      return ingredient.items().map(ItemStack::new).toList();
    } catch (UnsupportedOperationException | IllegalStateException ignored) {
      return List.of();
    }
  }

  private static boolean matchesDisplayIngredient(Ingredient ingredient, ItemStack stack) {
    try {
      return ingredient.test(stack);
    } catch (UnsupportedOperationException | IllegalStateException ignored) {
      return resolveDisplayItems(ingredient).stream()
        .anyMatch(candidate -> candidate.getItem() == stack.getItem());
    }
  }

  /** Gets all recipes for the given material variant */
  public static List<ItemStack> getItems(MaterialVariantId variant) {
    return ITEMS_BY_MATERIAL.computeIfAbsent(variant, GET_ITEMS_BY_MATERIAL);
  }


  /* Material variants */

  /** Registers a material variant for the lookups. */
  public static void addKnownVariant(MaterialVariantId variant) {
    LISTENER.checkClear();
    KNOWN_VARIANTS.put(variant.getMaterialId(), variant);
    // null cache of sorted variants as its outdated now
    SORTED_VARIANTS = null;
    HIDDEN_VARIANTS = null;
  }

  /** Gets a list of known material variants for the given material ID */
  public static Collection<MaterialVariantId> getVariants(MaterialId materialId) {
    Collection<MaterialVariantId> variants = KNOWN_VARIANTS.get(materialId);
    if (variants.isEmpty()) {
      return List.of(materialId);
    }
    return Collections.unmodifiableCollection(variants);
  }

  /** Gets a sorted list of all known non-hidden material variants */
  public static List<MaterialVariantId> getAllVariants() {
    if (SORTED_VARIANTS == null) {
      Comparator<MaterialVariantId> variantSorter = Comparator.comparing(MaterialVariantId::getVariant);
      SORTED_VARIANTS = MaterialRegistry.getInstance().getVisibleMaterials().stream()
        .flatMap(material -> {
          // if no variants are registered, just list the material itself; useful for uncraftable materials
          MaterialId id = material.getIdentifier();
          Collection<MaterialVariantId> variants = KNOWN_VARIANTS.get(id);
          if (variants.isEmpty()) {
            return Stream.of(id);
          }
          return variants.stream().sorted(variantSorter);
        }).toList();
    }
    return SORTED_VARIANTS;
  }

  /** Gets a sorted list of all known hidden material variants. Should not be directly displayed in recipes, rather added as a hidden input. */
  public static List<MaterialVariantId> getHiddenVariants() {
    if (HIDDEN_VARIANTS == null) {
      Comparator<MaterialVariantId> variantSorter = Comparator.comparing(MaterialVariantId::getVariant);
      HIDDEN_VARIANTS = MaterialRegistry.getInstance().getAllMaterials().stream()
        .filter(IMaterial::isHidden).sorted()
        .flatMap(material -> {
          // if no variants are registered, just list the material itself; useful for uncraftable materials
          MaterialId id = material.getIdentifier();
          Collection<MaterialVariantId> variants = KNOWN_VARIANTS.get(id);
          if (variants.isEmpty()) {
            return Stream.of(id);
          }
          return variants.stream().sorted(variantSorter);
        }).toList();
    }
    return HIDDEN_VARIANTS;
  }
}
