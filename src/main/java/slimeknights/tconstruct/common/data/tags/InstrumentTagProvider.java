package slimeknights.tconstruct.common.data.tags;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.Instruments;
import slimeknights.tconstruct.common.TinkerTags;

/** Instrument variants used by horn material recipes. */
public class InstrumentTagProvider extends TagsProvider<Instrument> {
  public InstrumentTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
    super(output, Registries.INSTRUMENT, lookup);
  }

  @Override
  protected void addTags(HolderLookup.Provider provider) {
    var tag = getOrCreateRawBuilder(TinkerTags.Instruments.VARIANT_HORNS);
    for (var instrument : List.of(Instruments.PONDER_GOAT_HORN, Instruments.SING_GOAT_HORN,
        Instruments.SEEK_GOAT_HORN, Instruments.FEEL_GOAT_HORN, Instruments.ADMIRE_GOAT_HORN,
        Instruments.CALL_GOAT_HORN, Instruments.YEARN_GOAT_HORN, Instruments.DREAM_GOAT_HORN)) {
      tag.addElement(instrument.identifier());
    }
  }

  @Override public String getName() { return "Continuum Construct Instrument Tags"; }
}
