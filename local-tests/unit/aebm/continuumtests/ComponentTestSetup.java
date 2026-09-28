package aebm.continuumtests;

import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.tags.ItemTags;
import net.neoforged.neoforge.common.CommonHooks;

/** Apply vanilla defaults that FML's headless bootstrap leaves pending until server data load. */
public final class ComponentTestSetup {
  private static boolean initialized;

  private ComponentTestSetup() {}

  public static synchronized void initialize() {
    if (!initialized) {
      var lookup = VanillaRegistries.createLookup();
      // The datagen named holder uses the same identity semantics as the normal named holder.
      // This supplies defaults; it does not claim that datapack tags or materials were loaded.
      CommonHooks.markComponentClassAsValid(lookup.lookupOrThrow(Registries.ITEM).getOrThrow(ItemTags.SWORDS).getClass());
      BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup)
        .forEach(DataComponentInitializers.PendingComponents::apply);
      initialized = true;
    }
  }
}
