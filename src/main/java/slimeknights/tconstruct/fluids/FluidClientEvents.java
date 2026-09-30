package slimeknights.tconstruct.fluids;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.client.fluid.FluidTintSources;
import slimeknights.mantle.fluid.texture.FluidTexture;
import slimeknights.mantle.registration.object.FlowingFluidObject;
import slimeknights.mantle.registration.object.FluidObject;
import slimeknights.tconstruct.common.ClientEventBase;
import slimeknights.tconstruct.fluids.data.FluidTextureProvider;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

import net.neoforged.fml.common.EventBusSubscriber;
import slimeknights.tconstruct.TConstruct;

//import slimeknights.tconstruct.library.client.model.FluidContainerModel;

@EventBusSubscriber(modid = TConstruct.MOD_ID, value = Dist.CLIENT)
public class FluidClientEvents extends ClientEventBase {
  /**
   * Called externally. Do NOT add registerFluidModels or registerLoaders here
   * as the class now uses @EventBusSubscriber which auto-registers them.
   */
  public static void onConstruct(IEventBus modEventBus) {
    modEventBus.addListener(FluidClientEvents::clientSetup);
  }
  @SubscribeEvent
  static void clientSetup(final FMLClientSetupEvent event) {
    FluidAmbientClient.init();
  }

  // TODO: Update to use RegisterColorHandlersEvent.ItemTintSources with new API
  //@SubscribeEvent
  //static void itemColors(final RegisterColorHandlersEvent.ItemTintSources event) {
  //  event.register(...)
  //}

  @SubscribeEvent
  static void registerFluidModels(RegisterFluidModelsEvent event) {
    Map<FluidObject<?>,FluidTexture.Builder> textures = new LinkedHashMap<>();
    FluidTextureProvider.addTextures(fluid -> textures.computeIfAbsent(fluid, key -> new FluidTexture.Builder(key.getType())));
    textures.forEach((fluid, builder) -> registerFluidModel(event, fluid, builder.build()));
  }

  private static void registerFluidModel(RegisterFluidModelsEvent event, FluidObject<?> fluid, FluidTexture texture) {
    boolean translucent = isTranslucent(fluid);
    FluidTintSource tint = texture.color() == -1 ? null : FluidTintSources.constant(texture.color());
    FluidModel.Unbaked model = new FluidModel.Unbaked(
      material(texture.still(), translucent),
      material(texture.flowing(), translucent),
      texture.overlay() == null ? null : material(texture.overlay(), translucent),
      tint);

    if (fluid instanceof FlowingFluidObject<?> flowing) {
      event.register(model, flowing.getStill(), flowing.getFlowing());
    } else {
      event.register(model, fluid.get());
    }
  }

  private static Material material(net.minecraft.resources.Identifier texture, boolean translucent) {
    Material material = new Material(texture);
    return translucent ? material.withForceTranslucent(true) : material;
  }

  private static boolean isTranslucent(FluidObject<?> fluid) {
    return fluid == TinkerFluids.honey
      || fluid == TinkerFluids.earthSlime
      || fluid == TinkerFluids.skySlime
      || fluid == TinkerFluids.enderSlime
      || fluid == TinkerFluids.moltenDiamond
      || fluid == TinkerFluids.moltenEmerald
      || fluid == TinkerFluids.moltenGlass
      || fluid == TinkerFluids.liquidSoul
      || fluid == TinkerFluids.moltenSoulsteel
      || fluid == TinkerFluids.moltenAmethyst;
  }
}