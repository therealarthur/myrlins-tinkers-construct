package slimeknights.tconstruct.library.client.armor;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import slimeknights.mantle.util.JsonHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier.ArmorTexture;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier.TextureType;
import slimeknights.tconstruct.library.client.armor.texture.TintedArmorTexture;
import slimeknights.tconstruct.library.client.armor.texture.TrimArmorTextureSupplier;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.tools.client.material.CombatFishingHookRenderer;
import slimeknights.tconstruct.tools.modules.cosmetic.TrimModule;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class ArmorModelManager extends SimpleJsonResourceReloadListener<JsonElement> {
  /** Folder containing the logic */
  public static final String FOLDER = "tinkering/armor_models";

  /** Object representing parsed models */
  public record ArmorModel(List<ArmorTextureSupplier> layers) {
    /** Empty instace for fallback */
    public static final ArmorModel EMPTY = new ArmorModel(List.of());
    /** Loadable for JSON parsing */
    public static final RecordLoadable<ArmorModel> LOADABLE = RecordLoadable.create(ArmorTextureSupplier.LOADER.list(1).requiredField("layers", ArmorModel::layers), ArmorModel::new);
  }

  /* Instance data */
  public static final ArmorModelManager INSTANCE = new ArmorModelManager();
  /** Map of location to texture suppliers */
  private Map<Identifier,ArmorModel> models = Collections.emptyMap();

  private static final List<ArmorModelDispatcher> DISPATCHERS = new ArrayList<>();

  /**
   * Initializes this manager, registering it with the resource manager
   * @param manager  Manager
   */
  public static void init(AddClientReloadListenersEvent manager) {
    manager.addListener(Identifier.fromNamespaceAndPath("tconstruct", "armor_models"), INSTANCE);
  }

  private ArmorModelManager() {
    super(JsonHelper.JSON_ELEMENT_CODEC, FileToIdConverter.json(FOLDER));
  }

  @Override
  protected void apply(Map<Identifier,JsonElement> splashList, ResourceManager manager, ProfilerFiller pProfiler) {
    long time = System.nanoTime();

    // first, load in all fluid textures, means we are allowed to reference them in fluid texture supplier constructors
    ArmorTextureSupplier.TEXTURE_VALIDATOR.onReloadSafe(manager);
    // reuses armor model logic for simplicity
    CombatFishingHookRenderer.clearCache();

    // load all models
    ImmutableMap.Builder<Identifier,ArmorModel> builder = ImmutableMap.builder();
    for (Entry<Identifier,JsonElement> entry : splashList.entrySet()) {
      Identifier key = entry.getKey();
      JsonElement element = entry.getValue();
      try {
        builder.put(key, ArmorModel.LOADABLE.convert(element, key.toString()));
      } catch (JsonSyntaxException e) {
        TConstruct.LOG.error("Failed to load armor model from {}", key, e);
      }
    }

    this.models = builder.build();
    // clear dispatcher model cache
    Set<Identifier> missing = new HashSet<>();
    for (ArmorModelDispatcher dispatcher : DISPATCHERS) {
      dispatcher.model = null;
      Identifier name = dispatcher.getName();
      if (!this.models.containsKey(name)) {
        missing.add(name);
      }
    }
    if (!missing.isEmpty()) {
      TConstruct.LOG.error("Missing armor models used by items: {}", missing);
    }
    TConstruct.LOG.info("Loaded {} armor models in {} ms", models.size(), (System.nanoTime() - time) / 1000000f);
  }

  /** Gets the armor model for the given location. Location typically corresponds to armor material name */
  public ArmorModel getModel(Identifier name) {
    return models.getOrDefault(name, ArmorModel.EMPTY);
  }

  /** Helper to cache armor models in the item */
  public abstract static class ArmorModelDispatcher implements IClientItemExtensions {
    private static final Identifier EMPTY_ARMOR_TEXTURE = TConstruct.getResource("textures/tinker_armor/empty.png");
    private ArmorModel model;

    public ArmorModelDispatcher() {
      DISPATCHERS.add(this);
    }

    /**
     * Gets the name of the model to use.
     * Not a constructor parameter as forge initializes client extensions before we can store fields from the parent constructor.
     */
    protected abstract Identifier getName();

    /** Fetches the model from the cache */
    protected ArmorModel getModel(ItemStack stack) {
      if (model == null) {
        model = ArmorModelManager.INSTANCE.getModel(getName());
        if (model == ArmorModel.EMPTY) {
          TConstruct.LOG.warn("Failed to find armor model {}, will skip rendering {}", getName(), stack);
        }
      }
      return model;
    }

    @Nonnull
    @Override
    public Model getGenericArmorModel(ItemStack stack, EquipmentClientInfo.LayerType layerType, Model original) {
      syncVanillaTrim(stack);
      return original;
    }

    @Override
    public Identifier getArmorTexture(ItemStack stack, EquipmentClientInfo.LayerType layerType, EquipmentClientInfo.Layer layer, Identifier original) {
      syncVanillaTrim(stack);
      ArmorTexture texture = getTinkerTexture(stack, toTextureType(layerType), layer);
      if (texture instanceof TintedArmorTexture tinted) {
        return tinted.texture();
      }
      // No tinker texture for this layer (an empty option or the trim layer). The vanilla path in original,
      // textures/entity/equipment/<type>/armor/layer_N.png, is only an index into the armor model and never exists,
      // so returning it logged "Missing resource" once; the layer is tinted to nothing anyway, so use the empty texture.
      return EMPTY_ARMOR_TEXTURE;
    }

    @Override
    public int getArmorLayerTintColor(ItemStack stack, EquipmentClientInfo.Layer layer, int layerIndex, int currentTint) {
      syncVanillaTrim(stack);
      int index = getLayerIndex(layer.textureId());
      ArmorModel model = getModel(stack);
      if (index < 0 || index >= model.layers().size() || model.layers().get(index) instanceof TrimArmorTextureSupplier) {
        return 0;
      }
      ArmorTexture texture = getTinkerTexture(stack, TextureType.ARMOR, layer);
      if (texture == ArmorTexture.EMPTY) {
        return 0;
      }
      if (texture instanceof TintedArmorTexture tinted && tinted.color() != -1) {
        return tinted.color();
      }
      return -1;
    }

    private static void syncVanillaTrim(ItemStack stack) {
      if (stack.has(DataComponents.TRIM)) {
        return;
      }
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level == null || !stack.is(TinkerTags.Items.MODIFIABLE)) {
        return;
      }
      slimeknights.tconstruct.library.tools.nbt.ToolStack tool = slimeknights.tconstruct.library.tools.nbt.ToolStack.from(stack);
      slimeknights.tconstruct.library.modifiers.ModifierId trimId = TinkerModifiers.trim.getId();
      String materialId = tool.getPersistentData().getString(TrimModule.materialKey(trimId));
      String patternId = tool.getPersistentData().getString(TrimModule.patternKey(trimId));
      if (materialId.isEmpty() || patternId.isEmpty()) {
        return;
      }
      RegistryAccess access = minecraft.level.registryAccess();
      Holder<TrimMaterial> material = access.lookupOrThrow(Registries.TRIM_MATERIAL).get(Identifier.tryParse(materialId)).orElse(null);
      Holder<TrimPattern> pattern = access.lookupOrThrow(Registries.TRIM_PATTERN).get(Identifier.tryParse(patternId)).orElse(null);
      if (material != null && pattern != null) {
        stack.set(DataComponents.TRIM, new ArmorTrim(material, pattern));
      }
    }

    private ArmorTexture getTinkerTexture(ItemStack stack, TextureType type, EquipmentClientInfo.Layer layer) {
      int index = getLayerIndex(layer.textureId());
      ArmorModel model = getModel(stack);
      if (index < 0 || index >= model.layers().size()) {
        return ArmorTexture.EMPTY;
      }
      ArmorTextureSupplier supplier = model.layers().get(index);
      if (supplier instanceof TrimArmorTextureSupplier) {
        return ArmorTexture.EMPTY;
      }
      RegistryAccess access = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.registryAccess() : RegistryAccess.EMPTY;
      return supplier.getArmorTexture(stack, type, access);
    }

    private static TextureType toTextureType(EquipmentClientInfo.LayerType layerType) {
      return switch (layerType) {
        case HUMANOID_LEGGINGS -> TextureType.LEGGINGS;
        case WINGS -> TextureType.WINGS;
        default -> TextureType.ARMOR;
      };
    }

    private static int getLayerIndex(Identifier texture) {
      String path = texture.getPath();
      if (!texture.getNamespace().equals(TConstruct.MOD_ID) || !path.startsWith("armor/layer_")) {
        return -1;
      }
      try {
        return Integer.parseInt(path.substring("armor/layer_".length()));
      } catch (NumberFormatException ignored) {
        return -1;
      }
    }
  }
}
