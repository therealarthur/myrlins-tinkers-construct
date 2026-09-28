package aebm.continuumtests;

import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.tags.ItemTags;
import net.neoforged.neoforge.common.CommonHooks;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.tables.menu.module.CraftingSideInventoryRegression;

/** ModDev launches FML first; this applies vanilla data defaults before using stacks. */
final class SideInventoryTest {
  @Test
  void actualSideInventoryTransferConservesItems() throws Exception {
    var lookup = VanillaRegistries.createLookup();
    // Datagen's Named holder subclass uses the same identity semantics as Named.
    // These vanilla-stack checks do not load or exercise datapack tag membership.
    CommonHooks.markComponentClassAsValid(lookup.lookupOrThrow(Registries.ITEM).getOrThrow(ItemTags.SWORDS).getClass());
    BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup)
      .forEach(DataComponentInitializers.PendingComponents::apply);
    CraftingSideInventoryRegression.main(new String[0]);
  }
}
