package slimeknights.tconstruct.shared;

import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterSpriteSourcesEvent;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RegisterSelectItemModelPropertyEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;

import org.joml.Matrix4f;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fluids.FluidClientEvents;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.client.MossyModifierModel;
import slimeknights.tconstruct.tools.client.ShieldBannerModifierSpriteSource;
import slimeknights.tconstruct.tools.modifiers.effect.HelmetChargingEffect;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.client.recipe.ClientRecipeCache;
import slimeknights.tconstruct.library.events.MaterialsLoadedEvent;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier;
import slimeknights.tconstruct.library.client.armor.texture.DyedArmorTextureSupplier;
import slimeknights.tconstruct.library.client.armor.texture.FirstArmorTextureSupplier;
import slimeknights.tconstruct.library.client.armor.texture.FixedArmorTextureSupplier;
import slimeknights.tconstruct.library.client.armor.texture.MaterialArmorTextureSupplier;
import slimeknights.tconstruct.library.client.armor.texture.MaterialHasFallbackTextureSupplier;
import slimeknights.tconstruct.library.client.armor.texture.TrimArmorTextureSupplier;
import slimeknights.tconstruct.library.client.book.TinkerBook;
import slimeknights.tconstruct.library.client.data.spritetransformer.FramesSpriteTransformer;
import slimeknights.tconstruct.library.client.data.spritetransformer.GreyToColorMapping;
import slimeknights.tconstruct.library.client.data.spritetransformer.GreyToSpriteTransformer;
import slimeknights.tconstruct.library.client.data.spritetransformer.IColorMapping;
import slimeknights.tconstruct.library.client.data.spritetransformer.ISpriteTransformer;
import slimeknights.tconstruct.library.client.data.spritetransformer.OffsettingSpriteTransformer;
import slimeknights.tconstruct.library.client.data.spritetransformer.RecolorSpriteTransformer;

import slimeknights.tconstruct.library.client.modifiers.DyedModifierModel;
import slimeknights.tconstruct.library.client.modifiers.MaterialModifierModel;
import slimeknights.tconstruct.library.client.modifiers.ModifierIconManager;
import slimeknights.tconstruct.library.client.modifiers.NormalModifierModel;
import slimeknights.tconstruct.library.client.modifiers.PotionModifierModel;
import slimeknights.tconstruct.tools.client.SlimeskullModifierModel;
import slimeknights.tconstruct.library.client.modifiers.model.BannerModifierModel;
import slimeknights.tconstruct.library.client.modifiers.model.CompoundModifierModel;
import slimeknights.tconstruct.library.client.modifiers.model.ConditionalModifierModel;
import slimeknights.tconstruct.library.client.modifiers.model.FluidModifierModel;
import slimeknights.tconstruct.library.client.modifiers.model.MaterialHasFallbackModifierModel;
import slimeknights.tconstruct.library.client.modifiers.model.ModifierModel;
import slimeknights.tconstruct.library.client.modifiers.model.TankModifierModel;
import slimeknights.tconstruct.library.client.modifiers.model.TraitModel;
import slimeknights.tconstruct.library.client.item.ModifiableItemClientExtension;
import slimeknights.tconstruct.library.client.modifiers.model.TrimModifierModel;
import slimeknights.tconstruct.library.client.model.FluidTextureModel;
import slimeknights.tconstruct.library.client.model.LegacyPassthroughModelLoader;
import slimeknights.tconstruct.library.client.model.TankModel;

import slimeknights.tconstruct.library.client.model.TinkerItemProperties;
import slimeknights.tconstruct.library.client.model.tools.MaterialItemModel;
import slimeknights.tconstruct.library.client.model.tools.MaterialBlockModel;
import slimeknights.tconstruct.library.client.model.tools.ToolItemModel;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.item.armor.MultilayerArmorItem;
import slimeknights.tconstruct.tools.item.SlimeskullItem;

import java.util.function.Consumer;

import static slimeknights.tconstruct.TConstruct.getResource;

/**
 * This class should only be referenced on the client side
 */
public class TinkerClient {
  /**
   * Called by TConstruct to handle any client side logic that needs to run during the constructor
   */
  public static void onConstruct() {
    TConstruct.MOD_EVENT_BUS.addListener(TinkerClient::registerClientExtensions);
    TConstruct.MOD_EVENT_BUS.addListener(TinkerClient::registerLegacyModelLoaders);
    TConstruct.MOD_EVENT_BUS.addListener(TinkerClient::registerMaterialItemModels);
    TConstruct.MOD_EVENT_BUS.addListener(TinkerClient::registerMaterialBlockStateModels);
    TConstruct.MOD_EVENT_BUS.addListener(TinkerClient::registerConditionalItemModelProperties);
    TConstruct.MOD_EVENT_BUS.addListener(TinkerClient::registerRangeItemModelProperties);
    TConstruct.MOD_EVENT_BUS.addListener(TinkerClient::registerSelectItemModelProperties);
    FluidClientEvents.onConstruct(TConstruct.MOD_EVENT_BUS);
    TConstruct.MOD_EVENT_BUS.addListener((RegisterSpriteSourcesEvent event) -> ShieldBannerModifierSpriteSource.register(event));
    TinkerBook.initBook();
    // needs to register listeners early enough for minecraft to load
    ModifierIconManager.init();

    // Recipe data is needed even when neither optional viewer is installed.
    Consumer<RecipesReceivedEvent> recipesUpdated = event -> {
      var connection = Minecraft.getInstance().getConnection();
      if (connection != null) {
        ClientRecipeCache.receive(connection.registryAccess(), event.getRecipeMap());
      }
    };
    // Viewers may rebuild at normal priority in this same event.
    NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, RecipesReceivedEvent.class, recipesUpdated);
    NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientRecipeCache.clear());
    NeoForge.EVENT_BUS.addListener((MaterialsLoadedEvent event) ->
      Minecraft.getInstance().execute(ClientRecipeCache::materialsUpdated));

    // register datagen serializers
    ISpriteTransformer.SERIALIZER.registerDeserializer(RecolorSpriteTransformer.NAME, RecolorSpriteTransformer.DESERIALIZER);
    GreyToSpriteTransformer.init();
    ISpriteTransformer.SERIALIZER.registerDeserializer(OffsettingSpriteTransformer.NAME, OffsettingSpriteTransformer.DESERIALIZER);
    ISpriteTransformer.SERIALIZER.registerDeserializer(FramesSpriteTransformer.NAME, FramesSpriteTransformer.DESERIALIZER);
    IColorMapping.SERIALIZER.registerDeserializer(GreyToColorMapping.NAME, GreyToColorMapping.DESERIALIZER);

    // armor textures
    ArmorTextureSupplier.LOADER.register(getResource("fixed"), FixedArmorTextureSupplier.LOADER);
    ArmorTextureSupplier.LOADER.register(getResource("dyed"), DyedArmorTextureSupplier.LOADER);
    ArmorTextureSupplier.LOADER.register(getResource("first_present"), FirstArmorTextureSupplier.LOADER);
    ArmorTextureSupplier.LOADER.register(getResource("material"), MaterialArmorTextureSupplier.Material.LOADER);
    ArmorTextureSupplier.LOADER.register(getResource("persistent_data"), MaterialArmorTextureSupplier.PersistentData.LOADER);
    ArmorTextureSupplier.LOADER.register(getResource("trim"), TrimArmorTextureSupplier.LOADER);
    ArmorTextureSupplier.LOADER.register(getResource("material_has_fallback"), MaterialHasFallbackTextureSupplier.LOADER);

    // modifier models
    ModifierModel.LOADER.register(getResource("empty"), ModifierModel.EMPTY.getLoader());
    ModifierModel.LOADER.register(getResource("compound"), CompoundModifierModel.LOADER);
    ModifierModel.LOADER.register(getResource("conditional"), ConditionalModifierModel.LOADER);
    ModifierModel.LOADER.register(getResource("trait"), TraitModel.LOADER);
    ModifierModel.LOADER.register(getResource("basic"), NormalModifierModel.LOADER);
    ModifierModel.LOADER.register(getResource("mossy"), MossyModifierModel.LOADER);
    ModifierModel.LOADER.register(getResource("dyed"), DyedModifierModel.LOADER);
    ModifierModel.LOADER.register(getResource("material"), MaterialModifierModel.LOADER);
    ModifierModel.LOADER.register(getResource("potion"), PotionModifierModel.LOADER);
    ModifierModel.LOADER.register(getResource("armor_trim"), TrimModifierModel.Armor.LOADER);
    ModifierModel.LOADER.register(getResource("custom_trim"), TrimModifierModel.Custom.LOADER);
    ModifierModel.LOADER.register(getResource("banner"), BannerModifierModel.LOADER);
    ModifierModel.LOADER.register(getResource("fluid"), FluidModifierModel.LOADER);
    ModifierModel.LOADER.register(getResource("tank"), TankModifierModel.LOADER);
    ModifierModel.LOADER.register(getResource("material_has_fallback"), MaterialHasFallbackModifierModel.LOADER);
    ModifierModel.LOADER.register(getResource("slimeskull"), SlimeskullModifierModel.LOADER);
  }

  private static void registerConditionalItemModelProperties(RegisterConditionalItemModelPropertyEvent event) {
    TinkerItemProperties.registerConditionalProperties(event);
  }

  private static void registerRangeItemModelProperties(RegisterRangeSelectItemModelPropertyEvent event) {
    TinkerItemProperties.registerRangeProperties(event);
  }

  private static void registerSelectItemModelProperties(RegisterSelectItemModelPropertyEvent event) {
    TinkerItemProperties.registerSelectProperties(event);
  }

  private static void registerMaterialBlockStateModels(RegisterBlockStateModels event) {
        event.registerModel(MaterialBlockModel.ID, MaterialBlockModel.Unbaked.MAP_CODEC);
    event.registerModel(TankModel.ID, TankModel.Unbaked.MAP_CODEC);

  }
  private static void registerMaterialItemModels(RegisterItemModelsEvent event) {
    event.register(MaterialItemModel.Unbaked.ID, MaterialItemModel.Unbaked.MAP_CODEC);
    event.register(ToolItemModel.Unbaked.ID, ToolItemModel.Unbaked.MAP_CODEC);
  }

  private static void registerLegacyModelLoaders(ModelEvent.RegisterLoaders event) {
    event.register(getResource("material"), LegacyPassthroughModelLoader.INSTANCE);
    event.register(getResource("tool"), LegacyPassthroughModelLoader.INSTANCE);
    event.register(getResource("fluid_container"), LegacyPassthroughModelLoader.INSTANCE);
        event.register(getResource("tank"), TankModel.LOADER);

    event.register(getResource("gui"), LegacyPassthroughModelLoader.INSTANCE);

        event.register(getResource("fluid_texture"), FluidTextureModel.LOADER);

  }


  private static IClientMobEffectExtensions helmetChargingExtension() {
    return new IClientMobEffectExtensions() {
      private final Minecraft mc = Minecraft.getInstance();

      @Override
      public boolean isVisibleInInventory(MobEffectInstance instance) {
        return false;
      }

      @Override
      public boolean renderGuiIcon(MobEffectInstance instance, net.minecraft.client.gui.Gui gui, net.minecraft.client.gui.GuiGraphicsExtractor graphics, int x, int y, float z, float alpha) {
        if (mc.player != null) {
          ItemStack helmet = mc.player.getItemBySlot(EquipmentSlot.HEAD);
          if (!helmet.isEmpty()) {
            int duration = instance.getDuration();
            int drawtime = ModifierUtil.getPersistentInt(helmet, GeneralInteractionModifierHook.KEY_DRAWTIME, 0);
            if (drawtime > 0 && duration < drawtime + 20) {
              int height = duration < 20 ? 18 : (drawtime + 20 - duration) * 18 / drawtime;
              graphics.fill(x + 3, y + 3 + (18 - height), x + 21, y + 21, 0xFFFFFFFF);
            }
          }
        }
        return true;
      }
    };
  }
  private static void registerClientExtensions(RegisterClientExtensionsEvent event) {
    event.registerMobEffect(helmetChargingExtension(), TinkerModifiers.helmetCharging.get());
    // NeoForge 26 no longer invokes ModifiableItem.initializeClient automatically.
    // Register the shared hand transform for standard modifiable held tools.
    // Armor, launcher, and skull items provide their own client extensions below or in their item class.
    for (Item item : BuiltInRegistries.ITEM) {
      if (item instanceof ModifiableItem) {
        event.registerItem(ModifiableItemClientExtension.INSTANCE, item);
      }
    }
    TinkerTools.travelersGear.forEach(item -> registerItemExtension(event, item));
    TinkerTools.plateArmor.forEach(item -> registerItemExtension(event, item));
    TinkerTools.slimesuit.forEach(item -> registerItemExtension(event, item));
    registerItemExtension(event, TinkerTools.slimeWings.get());
  }

  private static void registerItemExtension(RegisterClientExtensionsEvent event, net.minecraft.world.item.Item item) {
    if (item instanceof MultilayerArmorItem armor) {
      armor.initializeClient(extension -> event.registerItem(extension, armor));
    } else if (item instanceof SlimeskullItem skull) {
      skull.initializeClient(extension -> event.registerItem(extension, skull));
    }
  }

  @SubscribeEvent
  static void renderBlockOverlay(RenderBlockScreenEffectEvent event) {
    BlockState state = event.getBlockState();
    if (state.is(TinkerTags.Blocks.TRANSPARENT_OVERLAY)) {
      event.setCanceled(true);
    }
  }
}


