package slimeknights.tconstruct.library.client.recipe;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.tools.definition.module.build.ToolTraitHook;
import slimeknights.tconstruct.library.tools.item.IModifiableDisplay;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Which recipe viewer categories a modifiable tool acts as a workstation for, from its trait modifiers.
 * Follows official 3.12.1 {@code JEIPlugin.registerRecipeCatalysts}: tools whose traits carry a
 * {@code tinkering/tags/modifiers/jei/*} modifier are catalysts for crafting, smelting, severing and melting,
 * and melting tools that are also melee tools are catalysts for entity melting as well.
 * <p>
 * Viewer-neutral: no REI or JEI classes, so the rule is tested headlessly.
 */
public final class RecipeWorkstations {
  private RecipeWorkstations() {}

  /** Recipe viewer categories a tool can be a workstation for. */
  public enum Station { CRAFTING, SMELTING, SEVERING, MELTING, ENTITY_MELTING }

  /**
   * Applies the official rule to a tool's traits.
   * @param traitsHave  true when the tool's traits include a modifier in the tag
   * @param melee       true when the tool is in {@code tconstruct:modifiable/melee}
   */
  public static EnumSet<Station> forTraits(Predicate<TagKey<Modifier>> traitsHave, boolean melee) {
    EnumSet<Station> stations = EnumSet.noneOf(Station.class);
    if (traitsHave.test(TinkerTags.Modifiers.CRAFTING)) stations.add(Station.CRAFTING);
    if (traitsHave.test(TinkerTags.Modifiers.SMELTING)) stations.add(Station.SMELTING);
    if (traitsHave.test(TinkerTags.Modifiers.SEVERING)) stations.add(Station.SEVERING);
    if (traitsHave.test(TinkerTags.Modifiers.MELTING)) {
      stations.add(Station.MELTING);
      // only add to entity melting if it is melee too
      if (melee) stations.add(Station.ENTITY_MELTING);
    }
    return stations;
  }

  /** Stations for an item from its tool definition traits; empty when it is not a modifiable display tool. */
  public static EnumSet<Station> forItem(Item item) {
    if (!(item instanceof IModifiableDisplay modifiable)) return EnumSet.noneOf(Station.class);
    ModifierNBT traits = ToolTraitHook.getTraits(modifiable.getToolDefinition(), MaterialNBT.EMPTY);
    return forTraits(traits::has, item.builtInRegistryHolder().is(TinkerTags.Items.MELEE));
  }

  /** Every modifiable tool with at least one station, in tag order. Needs synchronized tool definitions and modifier tags. */
  public static Map<Item, EnumSet<Station>> all() {
    Map<Item, EnumSet<Station>> result = new LinkedHashMap<>();
    for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(TinkerTags.Items.MODIFIABLE)) {
      EnumSet<Station> stations = forItem(holder.value());
      if (!stations.isEmpty()) result.put(holder.value(), stations);
    }
    return result;
  }
}
