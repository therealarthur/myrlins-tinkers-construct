package slimeknights.tconstruct.library.recipe.ingredient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.IngredientType;
import slimeknights.tconstruct.TConstruct;

import javax.annotation.Nullable;

/** Ingredient matching an item with no container item, used to ensure NBT fluid items are empty */
public class NoContainerIngredient extends NestedIngredient {
  public static final Identifier ID = TConstruct.getResource("no_container");
  public static final IngredientType<NoContainerIngredient> TYPE = LegacyIngredientType.of(NoContainerIngredient::parseCustom, NoContainerIngredient::toJson);

  protected NoContainerIngredient(Ingredient nested) {
    super(nested);
  }

  private static NoContainerIngredient parseCustom(JsonObject json) {
    Ingredient ingredient = json.has("match") ? LegacyIngredientType.parseIngredient(json.get("match")) : slimeknights.mantle.recipe.ingredient.EmptyIngredient.VANILLA;
    return new NoContainerIngredient(ingredient);
  }

  @Override
  public boolean test(@Nullable ItemStack stack) {
    if (stack == null || !super.test(stack)) {
      return false;
    }
    var remainder = stack.getCraftingRemainder();
    return remainder == null || remainder.create().isEmpty();
  }

  @Override
  public boolean isSimple() {
    return false;
  }

  public JsonElement toJson() {
    JsonObject json = new JsonObject();
    json.addProperty("neoforge:ingredient_type", ID.toString());
    json.add("match", nestedToJson(nested));
    return json;
  }

  @Override
  public IngredientType<?> getType() {
    return TYPE;
  }

  public enum Serializer {
    INSTANCE;
    public Ingredient parse(JsonObject json) {
      return NoContainerIngredient.parseCustom(json).toVanilla();
    }
  }

  public static Ingredient of(Ingredient ingredient) {
    return new NoContainerIngredient(ingredient).toVanilla();
  }

  public static Ingredient of(ItemLike... items) {
    return of(Ingredient.of(items));
  }

  public static Ingredient of(ItemStack... stacks) {
    return of(Ingredient.of(java.util.Arrays.stream(stacks).map(ItemStack::getItem)));
  }

  public static Ingredient of(TagKey<Item> tag) {
    return of(slimeknights.tconstruct.library.recipe.ingredient.LegacyIngredientType.ofTag(tag));
  }
}
