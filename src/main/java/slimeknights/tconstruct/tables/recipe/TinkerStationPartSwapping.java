package slimeknights.tconstruct.tables.recipe;

import slimeknights.mantle.recipe.IMultiRecipe;
import slimeknights.tconstruct.library.materials.IMaterialRegistry;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.recipe.tinkerstation.IDisplayToolTinkering;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.ingredient.SizedIngredient;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.recipe.RecipeResult;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingLookup;
import slimeknights.tconstruct.library.recipe.modifiers.adding.ModifierRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.IMutableTinkerStationContainer;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.library.recipe.tinkerstation.building.MaterialSwappingRecipe;
import slimeknights.tconstruct.library.tools.definition.module.material.ToolPartsHook;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.LazyToolStack;
import slimeknights.tconstruct.library.tools.part.IToolPart;
import slimeknights.tconstruct.tables.TinkerTables;

import java.util.BitSet;
import java.util.List;

/**
 * Recipe that replaces a tool part with another
 */
public class TinkerStationPartSwapping extends MaterialSwappingRecipe implements IMultiRecipe<IDisplayToolTinkering> {
  public static final RecordLoadable<TinkerStationPartSwapping> LOADER = RecordLoadable.create(ContextKey.ID.requiredField(), TOOLS_FIELD, STACK_SIZE_FIELD, EXTRA_REQUIREMENTS_FIELD, TinkerStationPartSwapping::new);

  protected TinkerStationPartSwapping(Identifier id, Ingredient tools, int maxStackSize, List<SizedIngredient> extraRequirements) {
    super(id, tools, maxStackSize, extraRequirements);
  }

  /** @deprecated use {@link #TinkerStationPartSwapping(Identifier, Ingredient, int, List)} */
  @Deprecated(forRemoval = true)
  public TinkerStationPartSwapping(Identifier id, Ingredient tools, int maxStackSize) {
    this(id, tools, maxStackSize, List.of());
  }

  @Override
  public boolean matches(ITinkerStationContainer inv, Level world) {
    ItemStack tinkerable = inv.getTinkerableStack();
    if (tinkerable.isEmpty() || !tools.test(tinkerable) || !(tinkerable.getItem() instanceof IModifiable modifiable)) {
      return false;
    }
    // get the list of parts, empty means its not multipart
    List<IToolPart> parts = ToolPartsHook.parts(modifiable.getToolDefinition());
    if (parts.isEmpty()) {
      return false;
    }

    BitSet used = ModifierRecipe.makeBitset(inv);
    // we have two concerns on part swapping:
    // part must be valid in the tool
    boolean foundItem = false;
    for (int i = 0; i < inv.getInputCount(); i++) {
      ItemStack stack = inv.getInput(i);
      if (!stack.isEmpty() && stack.getItem() instanceof IToolPart part) {
        // if the part is not in the list, don't bother with this recipe even if another part mathces later
        if (!parts.contains(part)) {
          return false;
        }
        foundItem = true;
        used.set(i);
        break;
      }
    }
    // extra requiremenets ensures we do not try to swap multiple parts
    return foundItem && ModifierRecipe.checkMatch(inv, extraRequirements, used);
  }

  @Override
  public RecipeResult<LazyToolStack> getValidatedResult(ITinkerStationContainer inv, RegistryAccess access) {
    // copy the tool NBT to ensure the original tool is intact
    List<IToolPart> parts = ToolPartsHook.parts(inv.getTinkerable().getDefinition());

    // prevent part swapping on large tools in small tables
    if (parts.size() > inv.getInputCount()) {
      return TOO_MANY_PARTS;
    }

    // actual part swap logic
    for (int i = 0; i < inv.getInputCount(); i++) {
      ItemStack stack = inv.getInput(i);
      if (!stack.isEmpty() && stack.getItem() instanceof IToolPart part) {
        // ensure the part is valid
        MaterialVariantId partVariant = part.getMaterial(stack);
        if (partVariant.equals(IMaterial.UNKNOWN_ID)) {
          return RecipeResult.pass();
        }

        // the slot index doubles as the part index, so a tool using a part twice can aim at either copy by slot
        int cost = MaterialCastingLookup.getItemCost(part);
        RecipeResult<LazyToolStack> result = RecipeResult.pass();
        int index = -1;
        if (i < parts.size() && parts.get(i) == part) {
          index = i;
          result = swapMaterial(inv, partVariant, index, cost);
        }
        // a pass means that copy already holds this material, so try the other copies before giving up
        // otherwise a tool using a part twice could only ever swap whichever copy was filled first
        for (int pi = 0; pi < parts.size() && !result.isSuccess() && !result.hasError(); pi++) {
          if (pi != index && parts.get(pi) == part) {
            result = swapMaterial(inv, partVariant, pi, cost);
          }
        }
        return result;
      }
    }
    // no item found, should never happen
    return RecipeResult.pass();
  }

  @Override
  protected boolean shrinkPart(IMutableTinkerStationContainer inv, int index, ItemStack stack) {
    if (stack.getItem() instanceof IToolPart) {
      inv.shrinkInput(index, 1);
      return true;
    }
    return false;
  }

  @Override
  public RecipeSerializer<? extends Recipe<ITinkerStationContainer>> getSerializer() {
    return TinkerTables.tinkerStationPartSwappingSerializer.get();
  }

  /* JEI */

  @Override
  public List<IDisplayToolTinkering> getRecipes(RegistryAccess access) {
    if (!hasDisplayInputs()) return List.of();
    IMaterialRegistry registry = MaterialRegistry.getInstance();
    List<IDisplayToolTinkering> displays = new java.util.ArrayList<>();
    for (ItemStack stack : MaterialRecipeCache.getDisplayItems(tools)) {
      ToolStack tool = ToolStack.from(stack);
      List<IToolPart> parts = ToolPartsHook.parts(tool.getDefinition());
      if (parts.size() > MAX_SLOTS) continue;
      for (int index = 0; index < parts.size(); index++) {
        IToolPart part = parts.get(index);
        List<IMaterial> materials = registry.getVisibleMaterials().stream()
          .filter(mat -> registry.getMaterialStats(mat.getIdentifier(), part.getStatType()).isPresent()).toList();
        if (materials.isEmpty()) continue;
        int slot = index;
        displays.add(new PartDisplayRecipe(slot,
          materials.stream().map(mat -> part.withMaterialForDisplay(mat.getIdentifier())).toList(),
          List.of(withMaterial(tool.copy(), slot, MaterialVariant.of(ToolBuildHandler.getRenderMaterial(slot)))),
          materials.stream().map(mat -> withMaterial(tool.copy(), slot, MaterialVariant.of(mat))).toList(),
          materials.stream().map(MaterialVariant::of).toList(), part));
      }
    }
    return List.copyOf(displays);
  }
}
