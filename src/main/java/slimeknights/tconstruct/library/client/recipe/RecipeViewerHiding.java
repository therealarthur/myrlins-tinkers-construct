package slimeknights.tconstruct.library.client.recipe;

import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.smeltery.item.CopperCanItem;
import slimeknights.tconstruct.smeltery.item.TankItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Entries official 3.12.1 keeps out of the recipe viewer, for viewers that cannot read creative tab tags.
 * <p>
 * Official JEI hides the {@code tconstruct:fluids} creative tab through {@code c:hidden_from_recipe_viewers}, so the
 * filled tanks, gauges, lanterns and copper cans only appear when the {@code showFilledFluidTanks} client config adds
 * them back ({@code TankHidingIngredientListener} then keeps them in step with fluid visibility). REI filters item,
 * block and fluid tags but not creative tab tags, so without this every fluid would also be listed six more times as
 * a filled container. The stacks are built by the same helpers that fill the creative tab, so they compare exactly.
 * The empty containers stay visible.
 */
public final class RecipeViewerHiding {
  private RecipeViewerHiding() {}

  /** Every filled container the fluids creative tab lists; empty when registries or tags are not ready. */
  public static List<ItemStack> filledContainers() {
    List<ItemStack> stacks = new ArrayList<>();
    try {
      TankItem.addFilledVariants(stacks::add);
      CopperCanItem.addFilledVariants(stacks::add);
    } catch (RuntimeException exception) {
      // unbound tags before a world is joined; hiding nothing is the safe answer
      TConstruct.LOG.debug("Filled container hiding skipped until tags are bound", exception);
      return List.of();
    }
    return stacks;
  }
}
