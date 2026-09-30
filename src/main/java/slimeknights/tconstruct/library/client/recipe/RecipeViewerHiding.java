package slimeknights.tconstruct.library.client.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.registration.object.FluidObject;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.smeltery.data.SmelteryCompat;
import slimeknights.tconstruct.smeltery.item.CopperCanItem;
import slimeknights.tconstruct.smeltery.item.TankItem;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.data.material.MaterialIds;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

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

  /* ------------------------------------------------------------ official JEIPlugin#onRuntimeAvailable hiding */

  /**
   * Compat metals this port adds on top of official {@link SmelteryCompat} (All the Mods metals). The fluids creative tab
   * treats them like official compat: shown when the material or the {@code c:ingots/<name>} tag is present.
   */
  private record ExtraCompat(FluidObject<?> fluid, String ingot, MaterialId material) {}

  private static List<ExtraCompat> extraCompat() {
    return List.of(
      new ExtraCompat(TinkerFluids.moltenAllthemodium, "allthemodium", MaterialIds.allthemodium),
      new ExtraCompat(TinkerFluids.moltenVibranium, "vibranium", MaterialIds.vibranium),
      new ExtraCompat(TinkerFluids.moltenUnobtainium, "unobtainium", MaterialIds.unobtainium));
  }

  /**
   * Compat fluids whose metal is absent, which official 3.12.1 removes from the recipe viewer together with their bucket
   * ({@code JEIPlugin#onRuntimeAvailable}: every {@link SmelteryCompat} that is not {@link SmelteryCompat#isPresent()},
   * and molten porcelain without Ceramics). Same rule as the fluids creative tab: a compat is present when its material
   * is loaded or its {@code c:ingots/<name>} tag has an item.
   * @param ingotPresent     whether {@code c:ingots/<name>} has an item
   * @param materialPresent  whether a material is loaded
   * @param ceramicsLoaded   whether the Ceramics mod is loaded
   * @return  compat fluids to hide, in {@link SmelteryCompat} order, then the port's extra compat, then porcelain
   */
  public static List<FluidObject<?>> absentCompatFluids(Predicate<String> ingotPresent, Predicate<MaterialId> materialPresent, boolean ceramicsLoaded) {
    List<FluidObject<?>> hidden = new ArrayList<>();
    for (SmelteryCompat compat : SmelteryCompat.values()) {
      MaterialId material = compat.getMaterial();
      boolean present = (material != null && materialPresent.test(material)) || ingotPresent.test(compat.getName());
      if (!present) {
        hidden.add(compat.getFluid());
      }
    }
    for (ExtraCompat extra : extraCompat()) {
      if (!materialPresent.test(extra.material()) && !ingotPresent.test(extra.ingot())) {
        hidden.add(extra.fluid());
      }
    }
    if (!ceramicsLoaded) {
      hidden.add(TinkerFluids.moltenPorcelain);
    }
    return hidden;
  }

  /** {@link #absentCompatFluids(Predicate, Predicate, boolean)} with the tags, materials and mods currently loaded */
  public static List<FluidObject<?>> absentCompatFluids() {
    return absentCompatFluids(RecipeViewerHiding::ingotPresent,
                              id -> MaterialRegistry.getMaterial(id) != IMaterial.UNKNOWN,
                              ModList.get().isLoaded("ceramics"));
  }

  /** True when {@code c:ingots/<name>} has at least one item */
  private static boolean ingotPresent(String name) {
    return BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.create(Mantle.commonResource("ingots/" + name))).iterator().hasNext();
  }

  /**
   * Fluids official removes from the recipe viewer: the absent compat fluids and the variantless potion fluid (potions
   * are shown through their potion buckets instead). Empty until tags are bound, so nothing is hidden too early.
   */
  public static List<Fluid> hiddenFluids() {
    List<Fluid> fluids = new ArrayList<>();
    try {
      for (FluidObject<?> fluid : absentCompatFluids()) {
        fluids.add(fluid.get());
      }
    } catch (RuntimeException exception) {
      TConstruct.LOG.debug("Compat fluid hiding skipped until tags and materials are ready", exception);
      fluids.clear();
    }
    fluids.add(TinkerFluids.potion.get());
    return fluids;
  }

  /** Buckets of the absent compat fluids; official hides them through the creative tab, which REI does not read */
  public static List<ItemStack> hiddenBuckets() {
    List<ItemStack> buckets = new ArrayList<>();
    try {
      for (FluidObject<?> fluid : absentCompatFluids()) {
        ItemStack bucket = new ItemStack(fluid);
        if (!bucket.isEmpty()) {
          buckets.add(bucket);
        }
      }
    } catch (RuntimeException exception) {
      TConstruct.LOG.debug("Compat bucket hiding skipped until tags and materials are ready", exception);
      return List.of();
    }
    return buckets;
  }

  /**
   * Items official removes from the recipe viewer in every variant: the modifier crystal and the creative slot item
   * (shown through the modifier and slot ingredients instead).
   */
  public static boolean isAlwaysHiddenItem(ItemStack stack) {
    return stack.is(TinkerModifiers.modifierCrystal.get()) || stack.is(TinkerModifiers.creativeSlotItem.get());
  }

  /**
   * Which Tinkers recipe viewer values official lists as ingredients of their own: modifiers when the
   * {@code showModifiersInJEI} client config is on (the default), and slot types. Materials, patterns and entities are
   * registered with empty ingredient lists in official ({@code JEIPlugin#registerIngredients} and Mantle's entity
   * type), so they only appear inside recipes.
   */
  public static boolean listedInViewer(RecipeDisplayData.Value value, boolean showModifiers) {
    return value instanceof RecipeDisplayData.SlotValue || (showModifiers && value instanceof RecipeDisplayData.ModifierValue);
  }
}
