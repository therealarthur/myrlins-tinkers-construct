package slimeknights.tconstruct.library.recipe.material;

import com.mojang.serialization.MapCodec;
import lombok.Getter;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.tables.TinkerTables;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("unchecked")
public class ShapedMaterialsRecipe extends ShapedRecipe implements MaterialsCraftingTableRecipe {
  private static ItemStackTemplate template(ItemStack result) {
    return result.isEmpty() ? new ItemStackTemplate(Items.STICK) : ItemStackTemplate.fromNonEmptyStack(result);
  }

  private static NonNullList<Ingredient> ingredients(List<Optional<Ingredient>> pattern) {
    NonNullList<Ingredient> result = NonNullList.withSize(pattern.size(), slimeknights.mantle.recipe.ingredient.EmptyIngredient.VANILLA);
    for (int i = 0; i < pattern.size(); i++) {
      final int index = i;
      pattern.get(index).ifPresent(ingredient -> result.set(index, ingredient));
    }
    return result;
  }

  private final Identifier id;
  @Getter
  private final List<Ingredient> parts;
  private final boolean checkRepeats;
  @Getter
  private final List<MaterialVariantId> extraMaterials;
  private final ItemStackTemplate result;

  public ShapedMaterialsRecipe(Identifier id, String group, net.minecraft.world.item.crafting.CraftingBookCategory category, int width, int height, NonNullList<Ingredient> ingredients, ItemStack result, boolean showNotification, List<Ingredient> parts, List<MaterialVariantId> extraMaterials) {
    super(new Recipe.CommonInfo(showNotification), new CraftingRecipe.CraftingBookInfo(category, group), new ShapedRecipePattern(width, height, ingredients.stream().map(ingredient -> ingredient.isEmpty() ? Optional.<Ingredient>empty() : Optional.of(ingredient)).toList(), Optional.empty()), template(result));
    this.id = id;
    this.parts = List.copyOf(parts);
    this.checkRepeats = parts.stream().unordered().distinct().count() == parts.size();
    this.extraMaterials = List.copyOf(extraMaterials);
    this.result = template(result);
  }

  /** Creates the runtime material recipe from a decoded vanilla shaped recipe. */
  ShapedMaterialsRecipe(ShapedRecipe recipe, ItemStackTemplate result, List<Ingredient> parts, List<MaterialVariantId> extraMaterials) {
    super(new Recipe.CommonInfo(recipe.showNotification()), new CraftingRecipe.CraftingBookInfo(recipe.category(), recipe.group()), recipe.pattern, result);
    this.id = TConstruct.getResource("decoded_shaped_materials");
    this.parts = List.copyOf(parts);
    this.checkRepeats = parts.stream().unordered().distinct().count() == parts.size();
    this.extraMaterials = List.copyOf(extraMaterials);
    this.result = result;
  }

  public Identifier getId() {
    return id;
  }

  @Override
  public int getPartCount() {
    return parts.size();
  }

  @Nullable
  static MaterialVariantId[] findMaterials(CraftingInput inventory, List<Ingredient> parts, int partCount, boolean checkRepeats) {
    MaterialVariantId[] materials = new MaterialVariantId[partCount];
    for (int i = 0; i < inventory.size(); i++) {
      ItemStack stack = inventory.getItem(i);
      if (!stack.isEmpty()) {
        for (int p = 0; p < partCount; p++) {
          MaterialVariantId current = materials[p];
          if ((current == null || checkRepeats) && parts.get(p).test(stack)) {
            MaterialVariantId matched;
            if (stack.getItem() instanceof IMaterialItem materialItem) {
              matched = materialItem.getMaterial(stack);
            } else {
              matched = MaterialRecipeCache.findRecipe(stack).getMaterial().getVariant();
            }
            if (current == null) {
              materials[p] = matched;
              break;
            } else if (!current.matchesVariant(matched)) {
              if (current.getId().equals(matched.getId())) {
                materials[p] = current.getMaterialId();
                break;
              }
              return null;
            }
          }
        }
      }
    }
    for (int p = 0; p < partCount; p++) {
      if (materials[p] == null) {
        return null;
      }
    }
    return materials;
  }

  @Override
  public boolean matches(CraftingInput inventory, Level level) {
    return super.matches(inventory, level) && findMaterials(inventory, parts, parts.size(), checkRepeats) != null;
  }

  public static void setMaterial(ItemStack stack, MaterialVariantId material, List<MaterialVariantId> extraMaterials) {
    if (extraMaterials.isEmpty() && stack.getItem() instanceof IMaterialItem materialItem) {
      materialItem.setMaterial(stack, material);
    } else {
      MaterialNBT.Builder builder = MaterialNBT.builder();
      builder.add(material);
      builder.add(extraMaterials);
      setToolMaterials(stack, builder.build());
    }
  }

  /**
   * parity/materials: sets the materials on a tool stack and writes them back.
   * On 1.20.1 {@code ToolStack.from(stack)} shared the stack's tag, so setting materials on it changed the stack.
   * On 26.1 it works on a copy of the custom data component, so the result must be written back explicitly,
   * otherwise the crafted tool has no materials and later initializes with its default materials.
   */
  private static void setToolMaterials(ItemStack stack, MaterialNBT materials) {
    ToolStack tool = ToolStack.from(stack);
    tool.setMaterials(materials);
    tool.updateStack(stack, false);
  }

  @Override
  public void setMaterial(ItemStack stack, MaterialVariantId material) {
    setMaterial(stack, material, extraMaterials);
  }

  static ItemStack assemble(ItemStack stack, CraftingInput inventory, List<Ingredient> parts, int partCount, boolean checkRepeats, List<MaterialVariantId> extraMaterials) {
    MaterialVariantId[] materials = findMaterials(inventory, parts, partCount, checkRepeats);
    if (materials != null) {
      if (materials.length == 1 && extraMaterials.isEmpty() && stack.getItem() instanceof IMaterialItem materialItem) {
        return materialItem.setMaterial(stack, materials[0]);
      }
      MaterialNBT.Builder builder = MaterialNBT.builder();
      for (MaterialVariantId material : materials) {
        builder.add(material);
      }
      builder.add(extraMaterials);
      setToolMaterials(stack, builder.build());
    }
    return stack;
  }

  @Override
  public ItemStack assemble(CraftingInput inventory) {
    return assemble(result.create(), inventory, parts, parts.size(), checkRepeats, extraMaterials);
  }

  @Override
  public RecipeSerializer<ShapedRecipe> getSerializer() {
    return (RecipeSerializer<ShapedRecipe>)(RecipeSerializer<?>)TinkerTables.shapedMaterialsRecipeSerializer.get();
  }

  public static RecipeSerializer<ShapedMaterialsRecipe> serializer() {
    return new RecipeSerializer<>((MapCodec<ShapedMaterialsRecipe>)(MapCodec<?>)MaterialRecipeMapCodecs.SHAPED_MATERIALS, StreamCodec.of(ShapedMaterialsRecipe::encodeNetwork, ShapedMaterialsRecipe::decodeNetwork));
  }

  private static void encodeNetwork(RegistryFriendlyByteBuf buffer, ShapedMaterialsRecipe recipe) {
    ShapedRecipe.STREAM_CODEC.encode(buffer, recipe);
    buffer.writeVarInt(recipe.parts.size());
    for (Ingredient ingredient : recipe.parts) {
      Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient);
    }
    buffer.writeVarInt(recipe.extraMaterials.size());
    for (MaterialVariantId material : recipe.extraMaterials) {
      buffer.writeUtf(material.toString());
    }
  }

  private static ShapedMaterialsRecipe decodeNetwork(RegistryFriendlyByteBuf buffer) {
    Recipe.CommonInfo commonInfo = Recipe.CommonInfo.STREAM_CODEC.decode(buffer);
    CraftingRecipe.CraftingBookInfo bookInfo = CraftingRecipe.CraftingBookInfo.STREAM_CODEC.decode(buffer);
    ShapedRecipePattern pattern = ShapedRecipePattern.STREAM_CODEC.decode(buffer);
    ItemStackTemplate result = ItemStackTemplate.STREAM_CODEC.decode(buffer);
    ShapedRecipe recipe = new ShapedRecipe(commonInfo, bookInfo, pattern, result);
    int partCount = buffer.readVarInt();
    List<Ingredient> parts = new ArrayList<>(partCount);
    for (int i = 0; i < partCount; i++) {
      parts.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer));
    }
    int extraCount = buffer.readVarInt();
    List<MaterialVariantId> extraMaterials = new ArrayList<>(extraCount);
    for (int i = 0; i < extraCount; i++) {
      extraMaterials.add(MaterialVariantId.parse(buffer.readUtf()));
    }
    return new ShapedMaterialsRecipe(recipe, result, parts, extraMaterials);
  }
}
