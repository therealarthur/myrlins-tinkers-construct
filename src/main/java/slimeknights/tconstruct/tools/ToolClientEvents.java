package slimeknights.tconstruct.tools;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;

import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterSpriteSourcesEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import net.neoforged.fml.ModList;

import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import slimeknights.mantle.client.ResourceColorManager;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.mantle.data.listener.ISafeManagerReloadListener;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.ClientEventBase;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.library.client.armor.AbstractArmorModel;
import slimeknights.tconstruct.library.client.armor.ArmorModelManager;
import slimeknights.tconstruct.library.client.armor.texture.TrimArmorTextureSupplier;
import slimeknights.tconstruct.library.client.book.content.AbstractMaterialContent;
import slimeknights.tconstruct.library.client.materials.MaterialTooltipCache;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfoLoader;
import slimeknights.tconstruct.library.client.model.DynamicTextureLoader;
//import slimeknights.tconstruct.library.client.model.TinkerItemProperties;
//import slimeknights.tconstruct.library.client.model.tools.MaterialBlockModel;
import slimeknights.tconstruct.library.client.model.tools.MaterialModel;
import slimeknights.tconstruct.library.client.modifiers.DyedModifierModel;
import slimeknights.tconstruct.library.client.modifiers.FluidModifierModel;
import slimeknights.tconstruct.library.client.modifiers.MaterialModifierModel;
import slimeknights.tconstruct.library.client.modifiers.ModifierModelManager;
import slimeknights.tconstruct.library.client.modifiers.ModifierModelManager.ModifierModelRegistrationEvent;
import slimeknights.tconstruct.library.client.modifiers.ModifierModelMapManager;
import slimeknights.tconstruct.library.client.modifiers.NormalModifierModel;
import slimeknights.tconstruct.library.client.modifiers.PotionModifierModel;
import slimeknights.tconstruct.library.client.modifiers.TankModifierModel;
import slimeknights.tconstruct.library.client.modifiers.TrimModifierModel;
import slimeknights.tconstruct.library.client.particle.AttackParticle;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierManager;
import slimeknights.tconstruct.library.modifiers.modules.technical.ArmorStatModule;
import slimeknights.tconstruct.library.tools.capability.TinkerDataKeys;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.library.utils.HarvestTiers;
import slimeknights.tconstruct.library.utils.Util;
import slimeknights.tconstruct.shared.TinkerAttributes;
import slimeknights.tconstruct.shared.TinkerEffects;
import slimeknights.tconstruct.tools.client.CrystalshotRenderer;
import slimeknights.tconstruct.tools.client.ClientInteractionHandler;
import slimeknights.tconstruct.tools.client.FluidEffectProjectileRenderer;
import slimeknights.tconstruct.tools.client.ModifierClientEvents;
import slimeknights.tconstruct.tools.client.OverslimeModifierModel;
import slimeknights.tconstruct.tools.client.ShieldBannerModifierSpriteSource;
import slimeknights.tconstruct.tools.client.SlimeskullArmorModel;
import slimeknights.tconstruct.tools.client.SlimeskullLayer;
import slimeknights.tconstruct.tools.client.ToolContainerScreen;
import slimeknights.tconstruct.tools.client.ToolRenderEvents;
import slimeknights.tconstruct.tools.client.material.CombatFishingHookRenderer;
import slimeknights.tconstruct.tools.client.material.ThrownShurikenRenderer;
import slimeknights.tconstruct.tools.client.material.ThrownToolRenderer;
import slimeknights.tconstruct.tools.item.ModifierCrystalItem;
import slimeknights.tconstruct.tools.logic.DoubleJumpHandler;
import slimeknights.tconstruct.tools.logic.InteractionHandler;
import slimeknights.tconstruct.tools.modules.ranged.ammo.SmashingModule;
import slimeknights.tconstruct.tools.network.TinkerControlPacket;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import static slimeknights.tconstruct.TConstruct.getResource;
@SuppressWarnings("unused")
@EventBusSubscriber(modid = TConstruct.MOD_ID, value = Dist.CLIENT)
public class ToolClientEvents extends ClientEventBase {
  private static final KeyMapping.Category TCON_CATEGORY = new KeyMapping.Category(getResource("tconstruct"));
  /** Keybinding for interacting using a helmet */
  private static final KeyMapping HELMET_INTERACT = new KeyMapping(TConstruct.makeTranslationKey("key", "helmet_interact"), KeyConflictContext.IN_GAME, InputConstants.getKey("key.keyboard.z"), TCON_CATEGORY);
  /** Keybinding for interacting using leggings */
  private static final KeyMapping LEGGINGS_INTERACT = new KeyMapping(TConstruct.makeTranslationKey("key", "leggings_interact"), KeyConflictContext.IN_GAME, InputConstants.getKey("key.keyboard.i"), TCON_CATEGORY);

  /** Listener to clear modifier cache */
  private static final ISafeManagerReloadListener MODIFIER_RELOAD_LISTENER = manager -> {
    ModifierManager.INSTANCE.getAllValues().forEach(modifier -> modifier.clearCache(PackType.CLIENT_RESOURCES));
  };

  @SubscribeEvent
  static void addResourceListener(AddClientReloadListenersEvent manager) {
    DynamicTextureLoader.init(manager);
    ModifierModelManager.init(manager);
    manager.addListener(getResource("modifier_model_maps"), ModifierModelMapManager.INSTANCE);
    MaterialTooltipCache.init(manager);
    manager.addListener(getResource("material_render_info"), MaterialRenderInfoLoader.INSTANCE);
    manager.addDependency(getResource("material_render_info"), VanillaClientListeners.MODELS);

    manager.addListener(getResource("modifier_cache"), MODIFIER_RELOAD_LISTENER);
    manager.addListener(getResource("slimeskull_armor_model_cache"), SlimeskullArmorModel.RELOAD_LISTENER);
    manager.addListener(getResource("harvest_tiers_cache"), HarvestTiers.RELOAD_LISTENER);
    ArmorModelManager.init(manager);
    manager.addListener(getResource("trim_armor_texture_cache"), TrimArmorTextureSupplier.CACHE_INVALIDATOR);
  }
  @SubscribeEvent
  static void registerModifierModels(ModifierModelRegistrationEvent event) {
    event.registerModel(getResource("normal"), NormalModifierModel.UNBAKED_INSTANCE);
    event.registerModel(getResource("overslime"), OverslimeModifierModel.UNBAKED_INSTANCE);
    event.registerModel(getResource("fluid"), FluidModifierModel.UNBAKED_INSTANCE);
    event.registerModel(getResource("tank"), TankModifierModel.UNBAKED_INSTANCE);
    event.registerModel(getResource("material"), MaterialModifierModel.UNBAKED_INSTANCE);
    event.registerModel(getResource("dyed"), DyedModifierModel.UNBAKED_INSTANCE);
    // trim shows up as valid on every tool, skip to reduce memory overhead on tools using the new system - add it using the new system if you want it
    event.registerModel(getResource("trim"), TrimModifierModel.UNBAKED_INSTANCE);
    ModifierModelMapManager.legacyBlacklist(TrimModifierModel.UNBAKED_INSTANCE);
    event.registerModel(getResource("potion"), PotionModifierModel.UNBAKED_INSTANCE);
    event.registerModel(getResource("smashing_fluid"), new FluidModifierModel.Unbaked(SmashingModule.TANK_HELPER));
  }

  @SubscribeEvent
  static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerEntityRenderer(TinkerTools.indestructibleItem.get(), ItemEntityRenderer::new);
    event.registerEntityRenderer(TinkerTools.crystalshotEntity.get(), CrystalshotRenderer::new);
    event.registerEntityRenderer(TinkerTools.fishingHook.get(), CombatFishingHookRenderer::new);
    // TODO: config option for vanilla style renderer?
    event.registerEntityRenderer(TinkerTools.materialArrow.get(), ThrownToolRenderer::new);
    event.registerEntityRenderer(TinkerTools.thrownShuriken.get(), ThrownShurikenRenderer::new);
    event.registerEntityRenderer(TinkerTools.thrownTool.get(), ThrownToolRenderer::new);
    event.registerEntityRenderer(TinkerModifiers.fluidSpitEntity.get(), FluidEffectProjectileRenderer::new);
    event.registerEntityRenderer(TinkerModifiers.fireball.get(), context -> new ThrownItemRenderer<>(context, 0.75f, true));
  }

  @SubscribeEvent
  static void addLayers(EntityRenderersEvent.AddLayers event) {
    for (EntityType<?> type : event.getEntityTypes()) {
      addSlimeskullLayer(event.getRenderer(type));
    }
    for (PlayerModelType skin : event.getSkins()) {
      addSlimeskullLayer(event.getPlayerRenderer(skin));
      addSlimeskullLayer(event.getMannequinRenderer(skin));
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void addSlimeskullLayer(@Nullable EntityRenderer<?, ?> renderer) {
    if (renderer instanceof LivingEntityRenderer living && living.getModel() instanceof HumanoidModel) {
      living.addLayer(new SlimeskullLayer(living));
    }
  }

  @SubscribeEvent
  static void registerKeyBinding(RegisterKeyMappingsEvent event) {
    event.registerCategory(TCON_CATEGORY);
    event.register(HELMET_INTERACT);
    event.register(LEGGINGS_INTERACT);
  }

  @SubscribeEvent
  static void clientSetupEvent(FMLClientSetupEvent event) {
    NeoForge.EVENT_BUS.addListener(ToolClientEvents::handleKeyBindings);
    NeoForge.EVENT_BUS.addListener(ToolClientEvents::handleInput);
    NeoForge.EVENT_BUS.register(ClientInteractionHandler.class);
    NeoForge.EVENT_BUS.register(ToolRenderEvents.class);
    NeoForge.EVENT_BUS.register(ModifierClientEvents.class);
    AbstractArmorModel.init();

    // keybinds
    event.enqueueWork(() -> {
      // fake ingot showing in the book is a little nicer than the repair kits
      AbstractMaterialContent.registerFallbackPart(TinkerToolParts.fakeIngot);
      AbstractMaterialContent.registerFallbackPart(TinkerToolParts.fakeStorageBlockItem);

      // TODO: Rewrite for NeoForge 1.21.4 - TinkerItemProperties is commented out
      //// properties
      //// stone
      //TinkerItemProperties.registerToolProperties(TinkerTools.pickaxe);
      //TinkerItemProperties.registerToolProperties(TinkerTools.sledgeHammer);
      //TinkerItemProperties.registerToolProperties(TinkerTools.veinHammer);
      //// dirt
      //TinkerItemProperties.registerToolProperties(TinkerTools.mattock);
      //TinkerItemProperties.registerToolProperties(TinkerTools.pickadze);
      //TinkerItemProperties.registerToolProperties(TinkerTools.excavator);
      //// axe
      //TinkerItemProperties.registerToolProperties(TinkerTools.handAxe);
      //TinkerItemProperties.registerToolProperties(TinkerTools.broadAxe);
      //// leaves
      //TinkerItemProperties.registerToolProperties(TinkerTools.kama);
      //TinkerItemProperties.registerToolProperties(TinkerTools.scythe);
      //// sword
      //TinkerItemProperties.registerToolProperties(TinkerTools.dagger);
      //TinkerItemProperties.registerToolProperties(TinkerTools.sword);
      //TinkerItemProperties.registerToolProperties(TinkerTools.cleaver);
      //// bow
      //TinkerItemProperties.registerCrossbowProperties(TinkerTools.crossbow);
      //TinkerItemProperties.registerToolProperties(TinkerTools.longbow);
      //TinkerItemProperties.registerToolProperties(TinkerTools.fishingRod);
      //TinkerItemProperties.registerToolProperties(TinkerTools.javelin);
      //// misc
      //TinkerItemProperties.registerToolProperties(TinkerTools.flintAndBrick);
      //TinkerItemProperties.registerToolProperties(TinkerTools.skyStaff);
      //TinkerItemProperties.registerToolProperties(TinkerTools.earthStaff);
      //TinkerItemProperties.registerToolProperties(TinkerTools.ichorStaff);
      //TinkerItemProperties.registerToolProperties(TinkerTools.enderStaff);
      //// ancient
      //TinkerItemProperties.registerToolProperties(TinkerTools.meltingPan);
      //TinkerItemProperties.registerCrossbowProperties(TinkerTools.warPick);
      //TinkerItemProperties.registerToolProperties(TinkerTools.battlesign);
      //TinkerItemProperties.registerToolProperties(TinkerTools.swasher);
      //if (ModList.get().isLoaded("twilightforest")) {
      //  TinkerItemProperties.registerToolProperties(TinkerTools.minotaurAxe);
      //}
      //// armor
      //TinkerItemProperties.registerToolProperties(TinkerTools.travelersShield);
      //TinkerItemProperties.registerToolProperties(TinkerTools.plateShield);
      //Consumer<Item> brokenConsumer = TinkerItemProperties::registerBrokenProperty;
      //TinkerTools.travelersGear.forEach(brokenConsumer);
      //TinkerTools.plateArmor.forEach(brokenConsumer);
      //TinkerTools.slimesuit.forEach(brokenConsumer);
      //TinkerItemProperties.registerToolProperties(TinkerTools.slimeWings);
    });
  }

  @SubscribeEvent
  static void registerMenuScreens(RegisterMenuScreensEvent event) {
    event.register(TinkerTools.toolContainer.get(), ToolContainerScreen::new);
  }

  @SubscribeEvent
  static void registerParticleFactories(RegisterParticleProvidersEvent event) {
    ParticleResources.SpriteParticleRegistration<SimpleParticleType> factory = AttackParticle.Factory::new;
    event.registerSpriteSet(TinkerTools.hammerAttackParticle.get(), factory);
    event.registerSpriteSet(TinkerTools.axeAttackParticle.get(), factory);
    event.registerSpriteSet(TinkerTools.bonkAttackParticle.get(), factory);
  }

  // TODO NeoForge 26.1: restore tool and armor item tints using ItemTintSource MapCodec.

  // values to check if a key was being pressed last tick, safe as a static value as we only care about a single player client side
  /** If true, we were jumping last tick */
  private static boolean wasJumping = false;
  /** Number of double jumps requested since last grounded reset. */
  private static int clientExtraJumps = 0;
  /** If true, we were interacting with helmet last tick */
  private static boolean wasHelmetInteracting = false;
  /** If true, we were interacting with leggings last tick */
  private static boolean wasLeggingsInteracting = false;

  /** Called on player tick to handle keybinding presses */
  private static void handleKeyBindings(PlayerTickEvent.Pre event) {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.player != null && minecraft.player == event.getEntity() && !minecraft.player.isSpectator()) {

      // jumping in mid air for double jump
      // ensure we pressed the key since the last tick, holding should not use all your jumps at once
      boolean isJumping = minecraft.options.keyJump.isDown();
      if (event.getEntity().onGround() || event.getEntity().onClimbable() || event.getEntity().isInWater()) {
        clientExtraJumps = 0;
      }
      if (!wasJumping && isJumping) {
        if (TinkerEffects.antigravity.get().antigravityJump(event.getEntity())) {
          TinkerNetwork.getInstance().sendToServer(TinkerControlPacket.ANTIGRAVITY_JUMP);
        }
        else if (DoubleJumpHandler.canAttemptExtraJump(event.getEntity()) && clientExtraJumps < DoubleJumpHandler.getExtraJumps(event.getEntity())) {
          clientExtraJumps++;
          DoubleJumpHandler.performJump(event.getEntity());
          TinkerNetwork.getInstance().sendToServer(TinkerControlPacket.DOUBLE_JUMP);
        }
      }
      wasJumping = isJumping;

      // helmet interaction
      boolean isHelmetInteracting = HELMET_INTERACT.isDown();
      if (!wasHelmetInteracting && isHelmetInteracting) {
        TooltipKey key = SafeClientAccess.getTooltipKey();
        if (InteractionHandler.startArmorInteract(event.getEntity(), EquipmentSlot.HEAD, key)) {
          TinkerNetwork.getInstance().sendToServer(TinkerControlPacket.getStartHelmetInteract(key));
        }
      }
      if (wasHelmetInteracting && !isHelmetInteracting) {
        if (InteractionHandler.stopArmorInteract(event.getEntity(), EquipmentSlot.HEAD)) {
          TinkerNetwork.getInstance().sendToServer(TinkerControlPacket.STOP_HELMET_INTERACT);
        }
      }

      // leggings interaction
      boolean isLeggingsInteract = LEGGINGS_INTERACT.isDown();
      if (!wasLeggingsInteracting && isLeggingsInteract) {
        TooltipKey key = SafeClientAccess.getTooltipKey();
        if (InteractionHandler.startArmorInteract(event.getEntity(), EquipmentSlot.LEGS, key)) {
          TinkerNetwork.getInstance().sendToServer(TinkerControlPacket.getStartLeggingsInteract(key));
        }
      }
      if (wasLeggingsInteracting && !isLeggingsInteract) {
        if (InteractionHandler.stopArmorInteract(event.getEntity(), EquipmentSlot.LEGS)) {
          TinkerNetwork.getInstance().sendToServer(TinkerControlPacket.STOP_LEGGINGS_INTERACT);
        }
      }

      wasHelmetInteracting = isHelmetInteracting;
      wasLeggingsInteracting = isLeggingsInteract;
    }
  }

  @SuppressWarnings("removal")
  private static void handleInput(MovementInputUpdateEvent event) {
    Player player = event.getEntity();
    if (player.isUsingItem() && !player.isPassenger()) {
      ItemStack using = player.getUseItem();
      // start with the attribute
      double speed = player.getAttributeValue(TinkerAttributes.USE_ITEM_SPEED);
      // start by calculating tool stat, not an attribute to ensure both hands get their say
      if (using.is(TinkerTags.Items.HELD)) {
        ToolStack tool = ToolStack.from(using);
        speed += tool.getStats().get(ToolStats.USE_ITEM_SPEED) - ToolStats.USE_ITEM_SPEED.getDefaultValue();
      }
      // next, add in deprecated key bonus
      speed = Mth.clamp(speed + ArmorStatModule.getStat(player, TinkerDataKeys.USE_ITEM_SPEED), 0, 1);
      // LocalPlayer applies USE_EFFECTS after this event. Preserve the original correction
      // relative to vanilla's 20% default, while retaining custom item use-effect multipliers.
      ClientInput input = event.getInput();
      input.moveVector = input.getMoveVector().scale((float) (speed * 5));
    }
  }
}
