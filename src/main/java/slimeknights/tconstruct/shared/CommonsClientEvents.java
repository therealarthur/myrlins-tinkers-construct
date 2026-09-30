package slimeknights.tconstruct.shared;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.bus.api.SubscribeEvent;

import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.ClientEventBase;
import slimeknights.tconstruct.library.client.book.TinkerBook;
import slimeknights.tconstruct.library.utils.DomainDisplayName;
import slimeknights.tconstruct.shared.client.FluidParticle;

import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = TConstruct.MOD_ID, value = Dist.CLIENT)
public class CommonsClientEvents extends ClientEventBase {
  @SubscribeEvent
  static void addResourceListeners(AddClientReloadListenersEvent event) {
    DomainDisplayName.addResourceListener(event);
  }


  @SubscribeEvent
  static void clientSetup(final FMLClientSetupEvent event) {
    Font unicode = unicodeFontRender();
    TinkerBook.MATERIALS_AND_YOU.fontRenderer = unicode;
    TinkerBook.TINKERS_GADGETRY.fontRenderer = unicode;
    TinkerBook.PUNY_SMELTING.fontRenderer = unicode;
    TinkerBook.MIGHTY_SMELTING.fontRenderer = unicode;
    TinkerBook.FANTASTIC_FOUNDRY.fontRenderer = unicode;
    TinkerBook.ENCYCLOPEDIA.fontRenderer = unicode;
  }

  @SubscribeEvent
  static void registerParticleFactories(RegisterParticleProvidersEvent event) {
    event.registerSpecial(TinkerCommons.fluidParticle.get(), new FluidParticle.Factory());
  }

  private static Font unicodeRenderer;

  /**
   * Gets the unicode font renderer. Official built a Font over Minecraft's uniform font set (the small unifont glyphs) and
   * gave it to every Tinkers book; the port returned the normal font, so book text was wider, wrapped more and was cut
   * off at the page bottom. 26.1 no longer exposes FontManager's font sets, so this wraps the game font's glyph provider
   * and sends every font resource to minecraft:uniform, which draws the same glyphs official did. It is a separate
   * instance from Continuum Core's uniform font on purpose: Core resets a book that asks for its own uniform font.
   */
  public static Font unicodeFontRender() {
    if (unicodeRenderer == null) {
      unicodeRenderer = fontOverSet(Minecraft.getInstance().font, Minecraft.UNIFORM_FONT);
    }
    return unicodeRenderer;
  }

  /**
   * Creates a font that draws every font resource with the given font set, sharing the base font's glyph cache.
   * Falls back to the base font if its glyph provider cannot be read.
   */
  static Font fontOverSet(Font base, net.minecraft.resources.Identifier fontSet) {
    try {
      java.lang.reflect.Field field = Font.class.getDeclaredField("provider");
      field.setAccessible(true);
      Font.Provider provider = (Font.Provider) field.get(base);
      net.minecraft.network.chat.FontDescription target = new net.minecraft.network.chat.FontDescription.Resource(fontSet);
      return new Font(new Font.Provider() {
        @Override
        public net.minecraft.client.gui.GlyphSource glyphs(net.minecraft.network.chat.FontDescription font) {
          // sprite and player head glyphs keep their own source
          return provider.glyphs(font instanceof net.minecraft.network.chat.FontDescription.Resource ? target : font);
        }

        @Override
        public net.minecraft.client.gui.font.glyphs.EffectGlyph effect() {
          return provider.effect();
        }
      });
    } catch (ReflectiveOperationException | RuntimeException e) {
      TConstruct.LOG.error("Could not create the {} font for the Tinkers books, using the default font", fontSet, e);
      return base;
    }
  }
}

