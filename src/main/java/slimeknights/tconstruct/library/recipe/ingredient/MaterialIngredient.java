package slimeknights.tconstruct.library.recipe.ingredient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.IngredientType;
import slimeknights.tconstruct.library.recipe.ingredient.LegacyIngredientType;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.json.TinkerLoadables;
import slimeknights.tconstruct.library.json.predicate.material.MaterialPredicate;
import slimeknights.tconstruct.library.json.predicate.material.MaterialPredicateField;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Extension of the vanilla ingredient to display materials on items and support matching by materials
 */
public class MaterialIngredient extends NestedIngredient {
  public static final Identifier ID = TConstruct.getResource("material");
  public static final IngredientType<MaterialIngredient> TYPE = LegacyIngredientType.of(MaterialIngredient::parseCustom, MaterialIngredient::toJson);
  private static final LoadableField<IJsonPredicate<MaterialVariantId>,MaterialIngredient> MATERIAL_FIELD = new MaterialPredicateField<>("material", i -> i.material);

  private final IJsonPredicate<MaterialVariantId> material;
  @Nullable
  private ItemStack[] materialStacks;
  protected MaterialIngredient(Ingredient nested, IJsonPredicate<MaterialVariantId> material) {
    super(nested);
    this.material = material;
  }

  /** @deprecated use {@link #MaterialIngredient(Ingredient, IJsonPredicate)} */
  @Deprecated(forRemoval = true)
  protected MaterialIngredient(Ingredient nested, MaterialVariantId material, @Nullable TagKey<IMaterial> tag) {
    this(nested, makePredicate(material, tag));
  }

  /** Converts the legacy material and tag into a predicate */
  private static IJsonPredicate<MaterialVariantId> makePredicate(MaterialVariantId material, @Nullable TagKey<IMaterial> tag) {
    // UNKNOWN is the legacy way to express any material
    IJsonPredicate<MaterialVariantId> predicate = material.equals(IMaterial.UNKNOWN.getIdentifier()) ? MaterialPredicate.ANY : MaterialPredicate.variant(material);
    if (tag != null) {
      IJsonPredicate<MaterialVariantId> tagPredicate = MaterialPredicate.tag(tag);
      if (predicate == MaterialPredicate.ANY) {
        predicate = tagPredicate;
      } else {
        predicate = MaterialPredicate.and(predicate, tagPredicate);
      }
    }
    return predicate;
  }

  /** Creates an ingredient matching the given materials */
  public static MaterialIngredient of(Ingredient ingredient, IJsonPredicate<MaterialVariantId> material) {
    return new MaterialIngredient(ingredient, material);
  }

  /** Creates an ingredient matching the given materials */
  public static MaterialIngredient of(ItemLike item, IJsonPredicate<MaterialVariantId> material) {
    return of(Ingredient.of(item), material);
  }

  /** Creates an ingredient matching a specific material */
  public static MaterialIngredient of(Ingredient ingredient) {
    return new MaterialIngredient(ingredient, MaterialPredicate.ANY);
  }

  /** Creates an ingredient matching a single material */
  public static MaterialIngredient of(Ingredient ingredient, MaterialVariantId material) {
    return of(ingredient, MaterialPredicate.variant(material));
  }

  /** Creates an ingredient matching a material tag */
  public static MaterialIngredient of(Ingredient ingredient, TagKey<IMaterial> tag) {
    return of(ingredient, MaterialPredicate.tag(tag));
  }

  /**
   * Creates a new instance from an item with a fixed material
   * @param item      Material item
   * @param material  Material ID
   * @return  Material ingredient instance
   */
  public static MaterialIngredient of(ItemLike item, MaterialVariantId material) {
    return of(Ingredient.of(item), material);
  }

  /**
   * Creates a new instance from an item with a tagged material
   * @param item      Material item
   * @param tag   Material tag
   * @return  Material ingredient instance
   */
  public static MaterialIngredient of(ItemLike item, TagKey<IMaterial> tag) {
    return of(Ingredient.of(item), tag);
  }

  /**
   * Creates a new ingredient matching any material from items
   * @param item  Material item
   * @return  Material ingredient instance
   */
  public static MaterialIngredient of(ItemLike item) {
    return of(Ingredient.of(item));
  }

  /**
   * Creates a new ingredient from a tag
   * @param tag       Tag instance
   * @param material  Material value
   * @return  Material with tag
   */
  public static MaterialIngredient of(TagKey<Item> tag, MaterialVariantId material) {
    return of(LegacyIngredientType.ofTag(tag), material);
  }

  /**
   * Creates a new ingredient matching any material from a tag
   * @param tag       Tag instance
   * @return  Material with tag
   */
  public static MaterialIngredient of(TagKey<Item> tag) {
    return of(LegacyIngredientType.ofTag(tag));
  }

  @Override
  public boolean test(@Nullable ItemStack stack) {
    // check super first, should be faster
    if (stack == null || stack.isEmpty() || !super.test(stack)) {
      return false;
    }
    // no need to read material NBT if the material is the any predicate
    if (material != MaterialPredicate.ANY) {
      return material.matches(IMaterialItem.getMaterialFromStack(stack));
    }
    return true;
  }

  public ItemStack[] getItems() {
    if (materialStacks == null) {
      if (!MaterialRegistry.isFullyLoaded()) {
        return MaterialRecipeCache.getDisplayItems(nested).toArray(ItemStack[]::new);
      }
      // no material? apply all materials for variants
      Stream<ItemStack> items = MaterialRecipeCache.getDisplayItems(nested).stream();
      // find all materials matching the filter; note this only shows craftable material variants
      items = items.flatMap(stack -> MaterialRecipeCache.getAllVariants().stream()
        .filter(material::matches)
        .filter(mat -> !(stack.getItem() instanceof IMaterialItem part) || part.canUseMaterial(mat.getMaterialId()))
        .map(mat -> IMaterialItem.withMaterial(stack, mat))
        .filter(candidate -> !candidate.isEmpty()));
      materialStacks = items.distinct().toArray(ItemStack[]::new);
    }
    return materialStacks;
  }

  @Override
  public SlotDisplay display() {
    return new SlotDisplay.Composite(Arrays.stream(getItems())
      .filter(stack -> !stack.isEmpty() && test(stack))
      .map(stack -> (SlotDisplay)new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(stack)))
      .toList());
  }

  public JsonElement toJson() {
    JsonObject result = new JsonObject();
    result.addProperty("neoforge:ingredient_type", ID.toString());
    result.add("match", nestedToJson(nested));
    MATERIAL_FIELD.serialize(this, result);
    return result;
  }

  protected void invalidate() {
    this.materialStacks = null;
  }

  @Override
  public boolean isSimple() {
    return material == MaterialPredicate.ANY;
  }

  @Override
  public IngredientType<?> getType() {
    return TYPE;
  }

  private static MaterialIngredient parseCustom(JsonObject json) {
    Ingredient ingredient = json.has("match") ? LegacyIngredientType.parseIngredient(json.get("match")) : slimeknights.mantle.recipe.ingredient.EmptyIngredient.VANILLA;
    IJsonPredicate<MaterialVariantId> material = MATERIAL_FIELD.get(json);
    // deprecated tag field
    if (json.has("tag")) {
      TConstruct.LOG.warn("Using deprecated tag field on material ingredient");
      IJsonPredicate<MaterialVariantId> tagPredicate = MaterialPredicate.tag(TinkerLoadables.MATERIAL_TAGS.getIfPresent(json, "tag"));
      if (material == MaterialPredicate.ANY) {
        material = tagPredicate;
      } else {
        material = MaterialPredicate.and(material, tagPredicate);
      }
    }
    return new MaterialIngredient(ingredient, material);
  }

  /** Serializer instance */
  public enum Serializer {
    INSTANCE;
    public static final Identifier ID = MaterialIngredient.ID;
    public Ingredient parse(JsonObject json) {
      return MaterialIngredient.parseCustom(json).toVanilla();
    }
  }
}
