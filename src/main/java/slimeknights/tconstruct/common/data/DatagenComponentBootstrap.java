package slimeknights.tconstruct.common.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import slimeknights.tconstruct.TConstruct;

/**
 * Binds default item components before any Tinkers data provider runs.
 *
 * <p>Why: in 26.1 an item's default components are only bound when a server loads its resources
 * ({@code ReloadableServerResources}) or a client receives registries. The data generator does
 * neither, so every provider that builds an {@code ItemStack} (for example the tool forge results
 * in {@code TableRecipeProvider}) failed with "Components not bound yet" and {@code runData}
 * aborted before writing anything. This applies the same vanilla initializers the server applies
 * on load, using the data generator's registry lookup. It changes no recipe, tag or loot content;
 * it only makes {@code ItemStack} construction possible during data generation.
 *
 * <p>Runs at highest priority on both data events, before any provider is created, and binds at
 * most once per JVM. The event only fires in data runs, so normal game startup is untouched.
 */
@EventBusSubscriber(modid = TConstruct.MOD_ID)
public final class DatagenComponentBootstrap {
  private static final Logger LOG = LogManager.getLogger(DatagenComponentBootstrap.class);
  private static boolean bound;

  private DatagenComponentBootstrap() {}

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  static void bindForServerData(GatherDataEvent.Server event) {
    bind(event.getLookupProvider().join());
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  static void bindForClientData(GatherDataEvent.Client event) {
    bind(event.getLookupProvider().join());
  }

  /** Applies vanilla and NeoForge default component initializers once, against the given lookup. */
  static synchronized void bind(HolderLookup.Provider lookup) {
    if (bound) {
      return;
    }
    // Components such as weapon/repairable reference item tags. The datagen lookup hands out its own
    // named holder set class, which must be accepted by NeoForge's component value check, exactly as
    // the normal named holder set is. This does not load or change any tag contents.
    CommonHooks.markComponentClassAsValid(lookup.lookupOrThrow(Registries.ITEM).getOrThrow(ItemTags.SWORDS).getClass());
    BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach(DataComponentInitializers.PendingComponents::apply);
    bound = true;
    LOG.info("Bound default item components for data generation");
  }
}
