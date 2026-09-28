package slimeknights.tconstruct.library.client.model.tools;

import com.mojang.datafixers.util.Either;
import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import slimeknights.mantle.client.model.util.MantleItemLayerModel;
import slimeknights.mantle.util.ItemLayerPixels;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.TinkerItemDisplays;
import slimeknights.tconstruct.library.client.modifiers.IBakedModifierModel;
import slimeknights.tconstruct.library.client.modifiers.ModifierModelMap;
import slimeknights.tconstruct.library.client.modifiers.ModifierModelMapManager;
import slimeknights.tconstruct.library.client.modifiers.model.ModifierModel;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.worktable.ModifierSetWorktableRecipe;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.MaterialIdNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.utils.ItemStackNbtHelper;

import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Dynamic item model for completed Tinkers' tools in NeoForge 26.1. */
public class ToolItemModel implements ItemModel {
  private final Unbaked unbaked;
  private final ItemModel.BakingContext context;
  private final Matrix4fc transformation;
  private final Map<ToolCacheKey,ItemModel> cache = new HashMap<>();
  @Nullable
  private ModifierModelMap modifierModels;
  private static final Identifier ARROW_HEAD_TEXTURE = TConstruct.getResource("item/tool/ammo/arrow_head");
  private static final Identifier ARROW_SHAFT_TEXTURE = TConstruct.getResource("item/tool/ammo/arrow_shaft");
  private static final Identifier ARROW_FEATHER_TEXTURE = TConstruct.getResource("item/tool/ammo/arrow_feather");

  private ToolItemModel(Unbaked unbaked, ItemModel.BakingContext context, Matrix4fc transformation) {
    this.unbaked = unbaked;
    this.context = context;
    this.transformation = transformation;
  }

  @Override
  public void update(ItemStackRenderState output, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
    List<MaterialVariantId> materials = List.copyOf(MaterialIdNBT.from(stack).resolveRedirects().getMaterials());
    IToolStackView tool = ToolStack.from(stack);
    boolean largeModel = unbaked.large && shouldUseLargeTextures(displayContext);
    boolean leftHand = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    ItemStack ammo = getAmmo(tool);
    ToolCacheKey key = buildCacheKey(materials, tool, largeModel, leftHand, ammo);
    cache.computeIfAbsent(key, cacheKey -> bakeForTool(cacheKey, tool)).update(output, stack, resolver, displayContext, level, owner, seed);
  }

  private ToolCacheKey buildCacheKey(List<MaterialVariantId> materials, IToolStackView tool, boolean largeModel, boolean leftHand, ItemStack ammo) {
    ModifierNBT modifiers = unbaked.showTraits ? tool.getModifiers() : tool.getUpgrades();
    Set<ModifierId> hidden = ModifierSetWorktableRecipe.getModifierSet(tool.getPersistentData(), TConstruct.getResource("invisible_modifiers"));
    List<ModifierEntry> entries = List.copyOf(modifiers.getModifiers());
    return new ToolCacheKey(materials, entries, Set.copyOf(hidden), modifierCacheKeys(getModifierModels(), tool, entries, hidden), largeModel, leftHand, ammoKey(ammo));
  }

  /** Includes the data that actually changes modifier geometry, even when modifier levels stay the same. */
  static Map<String,Object> modifierCacheKeys(ModifierModelMap models, IToolStackView tool, List<ModifierEntry> entries, Set<ModifierId> hidden) {
    Map<String,Object> keys = new HashMap<>();
    for (ModifierEntry entry : entries) {
      if (!hidden.contains(entry.getId())) {
        IBakedModifierModel model = models.get(entry.getId());
        if (model != null) {
          Object key = model.getCacheKey(tool, entry);
          if (key != null) {
            keys.put("modifier:" + entry.getId(), key);
          }
        }
      }
    }
    models.constant().forEach((name, model) -> {
      Object key = model.getCacheKey(tool, ModifierEntry.EMPTY);
      if (key != null) {
        keys.put("constant:" + name, key);
      }
    });
    return Map.copyOf(keys);
  }

  private ModifierModelMap getModifierModels() {
    if (modifierModels == null) {
      ModelBaker baker = context.blockModelBaker();
      ModelDebugName debugName = () -> TConstruct.MOD_ID + ":tool_item/" + unbaked.model;
      modifierModels = ModifierModelMapManager.INSTANCE.getModelsForTool(
        mat -> baker.materials().get(mat, debugName).sprite(), unbaked.modifierMaps,
        unbaked.modifierRoots.small, unbaked.modifierRoots.large, unbaked.model);
    }
    return modifierModels;
  }

  private ItemModel bakeForTool(ToolCacheKey key, IToolStackView tool) {
    
    List<MaterialVariantId> materials = key.materials;
    ModelBaker baker = context.blockModelBaker();
    ResolvedModel baseModel = baker.getModel(unbaked.model);
    TextureSlots textureSlots = baseModel.getTopTextureSlots();
    ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, baseModel, textureSlots);
    ModelDebugName debugName = () -> TConstruct.MOD_ID + ":tool_item/" + unbaked.model;
    Function<Material,net.minecraft.client.renderer.texture.TextureAtlasSprite> spriteGetter = mat -> baker.materials().get(mat, debugName).sprite();

    List<Part> parts = unbaked.parts.isEmpty() ? List.of(Part.DEFAULT) : unbaked.parts;
    Transformation partTransform = key.largeModel ? unbaked.largeTransform() : Transformation.IDENTITY;
    ItemLayerPixels pixels = new ItemLayerPixels();
    List<Collection<BakedQuad>> layers = new ArrayList<>();
    Material.Baked particle = properties.particleMaterial();

    addModifierQuads(tool, spriteGetter, quads -> layers.add(List.copyOf(quads)), pixels, partTransform, key.largeModel);

    for (int i = parts.size() - 1; i >= 0; i--) {
      Part part = parts.get(i);
      Identifier textureId = unbaked.getTexture(part.name, key.largeModel);
      if (textureId == null) {
        textureId = unbaked.textures.get("layer0");
      }
      if (textureId == null) {
        continue;
      }
      Material texture = new Material(textureId);
      List<BakedQuad> quads;
      if (part.index >= 0) {
        MaterialVariantId material = part.index < materials.size() ? materials.get(part.index) : IMaterial.UNKNOWN_ID;
        quads = MaterialModel.getQuadsForMaterial(spriteGetter, texture, material, -1, partTransform, pixels);
      } else {
        quads = MantleItemLayerModel.getQuadsForSprite(-1, -1, spriteGetter.apply(texture), partTransform, 0, pixels);
      }
      layers.add(quads);
      particle = new Material.Baked(spriteGetter.apply(texture), false);
    }

    addAmmoQuads(getAmmo(tool), materials, spriteGetter, quads -> layers.add(List.copyOf(quads)), key.largeModel, key.leftHand, pixels);

    QuadCollection.Builder builder = new QuadCollection.Builder();
    QuadCollection.Builder guiBuilder = new QuadCollection.Builder();
    addLayers(builder, guiBuilder, layers);
    QuadCollection quads = builder.build();
    QuadCollection guiQuads = guiBuilder.build();
    if (quads.getAll().isEmpty()) {
      return context.missingItemModel(transformation);
    }
    ModelRenderProperties renderProperties = new ModelRenderProperties(properties.usesBlockLight(), particle, properties.transforms());
    ItemModel fullModel = new CuboidItemModelWrapper(List.of(), quads, renderProperties, transformation);
    ItemModel guiModel = guiQuads.getAll().isEmpty() ? fullModel : new CuboidItemModelWrapper(List.of(), guiQuads, renderProperties, transformation);
    return new ContextAwareToolModel(fullModel, guiModel);
  }

  private void addModifierQuads(IToolStackView tool, Function<Material,net.minecraft.client.renderer.texture.TextureAtlasSprite> spriteGetter, java.util.function.Consumer<Collection<BakedQuad>> quadConsumer, ItemLayerPixels pixels, Transformation modifierTransform, boolean largeModel) {
    ModifierModelMap modifierModels = getModifierModels();
    if (modifierModels.isEmpty()) {
      return;
    }

    int tintIndex = 0;
    Set<ModifierId> hidden = ModifierSetWorktableRecipe.getModifierSet(tool.getPersistentData(), TConstruct.getResource("invisible_modifiers"));
    List<ModifierEntry> modifiers = (unbaked.showTraits ? tool.getModifiers() : tool.getUpgrades()).getModifiers();
    ModifierEntry[] firstEntries = new ModifierEntry[unbaked.firstModifiers.size()];
    for (int i = modifiers.size() - 1; i >= 0; i--) {
      ModifierEntry entry = modifiers.get(i);
      int firstIndex = unbaked.firstModifiers.indexOf(entry.getId());
      if (hidden.contains(entry.getId())) {
        continue;
      }
      if (firstIndex != -1) {
        firstEntries[firstIndex] = entry;
      } else {
        IBakedModifierModel model = modifierModels.get(entry.getId());
        if (model != null) {
          int startTintIndex = tintIndex;
          model.addQuads(tool, entry, spriteGetter, modifierTransform, largeModel, startTintIndex, quadConsumer, pixels);
          tintIndex += model.getTintIndexes();
        }
      }
    }
    for (int i = firstEntries.length - 1; i >= 0; i--) {
      ModifierEntry entry = firstEntries[i];
      if (entry != null) {
        IBakedModifierModel model = modifierModels.get(entry.getId());
        if (model != null) {
          int startTintIndex = tintIndex;
          model.addQuads(tool, entry, spriteGetter, modifierTransform, largeModel, startTintIndex, quadConsumer, pixels);
          tintIndex += model.getTintIndexes();
        }
      }
    }
    for (ModifierModel model : modifierModels.constant().values()) {
      int startTintIndex = tintIndex;
      model.addQuads(tool, ModifierEntry.EMPTY, spriteGetter, modifierTransform, largeModel, startTintIndex, quadConsumer, pixels);
      tintIndex += model.getTintIndexes();
    }
  }

  private ItemStack getAmmo(IToolStackView tool) {
    if (unbaked.ammo.isEmpty()) {
      return ItemStack.EMPTY;
    }
    CompoundTag tag = tool.getPersistentData().getCompound(unbaked.ammo.get().key);
    return tag.isEmpty() ? ItemStack.EMPTY : ItemStackNbtHelper.parse(tag);
  }

  private static String ammoKey(ItemStack ammo) {
    return ammo.isEmpty() ? "" : ItemStackNbtHelper.save(ammo).toString();
  }

  private void addAmmoQuads(ItemStack ammo, List<MaterialVariantId> materials, Function<Material,net.minecraft.client.renderer.texture.TextureAtlasSprite> spriteGetter, java.util.function.Consumer<Collection<BakedQuad>> quadConsumer, boolean largeModel, boolean leftHand, ItemLayerPixels pixels) {
    if (ammo.isEmpty() || unbaked.ammo.isEmpty()) {
      return;
    }
    Transformation transform = unbaked.ammo.get().transform(unbaked.large, largeModel, leftHand, unbaked.largeOffset);
    List<MaterialVariantId> ammoMaterials = List.copyOf(MaterialIdNBT.from(ammo).resolveRedirects().getMaterials());
    if (ammoMaterials.size() >= 3) {
      quadConsumer.accept(MaterialModel.getQuadsForMaterial(spriteGetter, new Material(ARROW_FEATHER_TEXTURE), ammoMaterials.get(2), -1, transform, pixels));
      quadConsumer.accept(MaterialModel.getQuadsForMaterial(spriteGetter, new Material(ARROW_HEAD_TEXTURE), ammoMaterials.get(0), -1, transform, pixels));
      quadConsumer.accept(MaterialModel.getQuadsForMaterial(spriteGetter, new Material(ARROW_SHAFT_TEXTURE), ammoMaterials.get(1), -1, transform, pixels));
    } else {
      quadConsumer.accept(MantleItemLayerModel.getQuadsForSprite(-1, -1, spriteGetter.apply(new Material(ARROW_FEATHER_TEXTURE)), transform, 0, pixels));
      quadConsumer.accept(MantleItemLayerModel.getQuadsForSprite(-1, -1, spriteGetter.apply(new Material(ARROW_HEAD_TEXTURE)), transform, 0, pixels));
      quadConsumer.accept(MantleItemLayerModel.getQuadsForSprite(-1, -1, spriteGetter.apply(new Material(ARROW_SHAFT_TEXTURE)), transform, 0, pixels));
    }
  }

  private static boolean shouldUseLargeTextures(ItemDisplayContext displayContext) {
    return !isSmallToolContext(displayContext);
  }

  /** Contexts where the original renderer used small tool textures. */
  private static boolean isSmallToolContext(ItemDisplayContext displayContext) {
    return displayContext == ItemDisplayContext.GUI
      || displayContext == TinkerItemDisplays.MELTER
      || displayContext == TinkerItemDisplays.CASTING_BASIN
      || displayContext == TinkerItemDisplays.CASTING_TABLE;
  }

  static void addLayers(QuadCollection.Builder fullBuilder, QuadCollection.Builder guiBuilder, List<Collection<BakedQuad>> layers) {
    for (int i = layers.size() - 1; i >= 0; i--) {
      for (BakedQuad quad : layers.get(i)) {
        Direction direction = quad.direction();
        fullBuilder.addUnculledFace(quad);
        if (direction == Direction.SOUTH) {
          guiBuilder.addUnculledFace(quad);
        }
      }
    }
  }

  private record ContextAwareToolModel(ItemModel fullModel, ItemModel guiModel) implements ItemModel {
    @Override
    public void update(ItemStackRenderState output, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
      ItemModel model = fullModel;
      if (displayContext == ItemDisplayContext.GUI) {
        model = guiModel;
      }
      model.update(output, stack, resolver, displayContext, level, owner, seed);
    }
  }

  private record ToolCacheKey(List<MaterialVariantId> materials, List<ModifierEntry> modifierData, Set<ModifierId> hiddenModifiers, Map<String,Object> modelData, boolean largeModel, boolean leftHand, String ammoKey) {}

  public record Part(String name, int index) {
    public static final Part DEFAULT = new Part("tool", -1);
    public static final Codec<Part> CODEC = RecordCodecBuilder.create(instance -> instance.group(
      Codec.STRING.fieldOf("name").forGetter(Part::name),
      Codec.INT.optionalFieldOf("index", -1).forGetter(Part::index)
    ).apply(instance, Part::new));
  }



  public record AmmoData(Identifier key, boolean flip, boolean left, List<Float> offset, List<Float> smallOffset, List<Float> largeOffset) {
    public static final Codec<AmmoData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
      Identifier.CODEC.fieldOf("key").forGetter(AmmoData::key),
      Codec.BOOL.optionalFieldOf("flip", false).forGetter(AmmoData::flip),
      Codec.BOOL.optionalFieldOf("left", false).forGetter(AmmoData::left),
      Codec.FLOAT.listOf().optionalFieldOf("offset", List.of()).forGetter(AmmoData::offset),
      Codec.FLOAT.listOf().optionalFieldOf("small_offset", List.of()).forGetter(AmmoData::smallOffset),
      Codec.FLOAT.listOf().optionalFieldOf("large_offset", List.of()).forGetter(AmmoData::largeOffset)
    ).apply(instance, AmmoData::new));

    public Transformation transform(boolean toolLarge, boolean largeModel, boolean leftHand, List<Float> toolLargeOffset) {
      List<Float> chosen = toolLarge ? (largeModel ? largeOffset : smallOffset) : offset;
      float x = chosen.size() > 0 ? chosen.get(0) : 0;
      float y = chosen.size() > 1 ? chosen.get(1) : 0;
      float flipOffset = flip ? 1 : 0;
      Quaternionf rotation = flip ? new Quaternionf().rotationY((float)Math.PI) : null;
      Vector3f translation;
      if (toolLarge && largeModel) {
        float toolX = toolLargeOffset.size() > 0 ? toolLargeOffset.get(0) : 0;
        float toolY = toolLargeOffset.size() > 1 ? toolLargeOffset.get(1) : 0;
        translation = new Vector3f((toolX / 2 + x + 4f) / 16f + flipOffset, (-toolY / 2 - y + 4f) / 16f, 1f / 16f + flipOffset);
      } else {
        translation = new Vector3f(x / 16f + flipOffset, -y / 16f, 1f / 16f + flipOffset);
      }
      if (left && leftHand) {
        translation.z = -1f / 16f + flipOffset;
      }
      return new Transformation(translation, rotation, null, null);
    }
  }
  public record ModifierRoots(List<Identifier> small, List<Identifier> large) {
    public static final ModifierRoots EMPTY = new ModifierRoots(List.of(), List.of());
    private static final Codec<ModifierRoots> OBJECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
      Identifier.CODEC.listOf().optionalFieldOf("small", List.of()).forGetter(ModifierRoots::small),
      Identifier.CODEC.listOf().optionalFieldOf("large", List.of()).forGetter(ModifierRoots::large)
    ).apply(instance, ModifierRoots::new));
    public static final Codec<ModifierRoots> CODEC = Codec.either(Identifier.CODEC.listOf(), OBJECT_CODEC).xmap(
      either -> either.map(list -> new ModifierRoots(list, List.of()), roots -> roots),
      roots -> roots.large.isEmpty() ? Either.left(roots.small) : Either.right(roots)
    );
  }
  public record Unbaked(Identifier model, Map<String,Identifier> textures, List<Part> parts, List<Identifier> modifierMaps, ModifierRoots modifierRoots, Optional<AmmoData> ammo, List<ModifierId> firstModifiers, boolean showTraits, boolean large, List<Float> largeOffset) implements ItemModel.Unbaked {
    public static final Identifier ID = TConstruct.getResource("tool");
    public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
      Identifier.CODEC.fieldOf("model").forGetter(Unbaked::model),
      Codec.unboundedMap(Codec.STRING, Identifier.CODEC).fieldOf("textures").forGetter(Unbaked::textures),
      Part.CODEC.listOf().optionalFieldOf("parts", List.of()).forGetter(Unbaked::parts),
      Identifier.CODEC.listOf().optionalFieldOf("modifier_maps", List.of()).forGetter(Unbaked::modifierMaps),
      ModifierRoots.CODEC.optionalFieldOf("modifier_roots", ModifierRoots.EMPTY).forGetter(Unbaked::modifierRoots),
            AmmoData.CODEC.optionalFieldOf("ammo").forGetter(Unbaked::ammo),
      Identifier.CODEC.xmap(ModifierId::new, ModifierId::getId).listOf().optionalFieldOf("first_modifiers", List.of()).forGetter(Unbaked::firstModifiers),
      Codec.BOOL.optionalFieldOf("show_traits", false).forGetter(Unbaked::showTraits),
      Codec.BOOL.optionalFieldOf("large", false).forGetter(Unbaked::large),
      Codec.FLOAT.listOf().optionalFieldOf("large_offset", List.of()).forGetter(Unbaked::largeOffset)
    ).apply(instance, Unbaked::new));

    public Transformation largeTransform() {
      float x = largeOffset.size() > 0 ? largeOffset.get(0) : 0;
      float y = largeOffset.size() > 1 ? largeOffset.get(1) : 0;
      return new Transformation(new Vector3f((x - 8) / 32.0F, (-y - 8) / 32.0F, 0), null, new Vector3f(2, 2, 1), null);
    }

    public Identifier getTexture(String name, boolean largeModel) {
      if (largeModel) {
        Identifier texture = textures.get("large_" + name);
        if (texture != null) {
          return texture;
        }
      }
      return textures.get(name);
    }

    @Override
    public MapCodec<? extends ItemModel.Unbaked> type() {
      return MAP_CODEC;
    }

    @Override
    public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
      return new ToolItemModel(this, context, transformation);
    }

    @Override
    public void resolveDependencies(ResolvableModel.Resolver resolver) {
      resolver.markDependency(model);
    }
  }
}
