package slimeknights.tconstruct.common.data.tags;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.CreativeModeTab;
import slimeknights.mantle.data.BuiltinRegistryTagProvider;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.tables.TinkerTables;

import java.util.concurrent.CompletableFuture;

/**
 * Creative tab tags, restored from official Tinkers' Construct 3.12.1 (parity/rei).
 * The tables and fluids tabs only hold retextured and filled variants, which recipe viewers
 * show through their own variant rules instead of listing every copy.
 */
public class CreativeTabTagProvider extends BuiltinRegistryTagProvider<CreativeModeTab> {
  @SuppressWarnings("deprecation")
  public CreativeTabTagProvider(PackOutput packOutput, CompletableFuture<Provider> lookupProvider) {
    super(packOutput, BuiltInRegistries.CREATIVE_MODE_TAB, lookupProvider, TConstruct.MOD_ID);
  }

  @Override
  protected void addTags(Provider provider) {
    this.tag(TinkerTags.CreativeTabs.HIDDEN_IN_RECIPE_VIEWERS).add(TinkerTables.tabTables.get(), TinkerFluids.tabFluids.get());
  }

  @Override
  public String getName() {
    return "Continuum Construct Creative Tab Tags";
  }
}
