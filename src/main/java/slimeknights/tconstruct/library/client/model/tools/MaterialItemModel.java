package slimeknights.tconstruct.library.client.model.tools;

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
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Dynamic item model for single-material tool parts and repair kits in NeoForge 26.1. */
public class MaterialItemModel implements ItemModel {
  private final Unbaked unbaked;
  private final ItemModel.BakingContext context;
  private final Matrix4fc transformation;
  private final Map<MaterialVariantId,ItemModel> cache = new HashMap<>();

  private MaterialItemModel(Unbaked unbaked, ItemModel.BakingContext context, Matrix4fc transformation) {
    this.unbaked = unbaked;
    this.context = context;
    this.transformation = transformation;
  }

  @Override
  public void update(ItemStackRenderState output, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
    MaterialVariantId material = IMaterialItem.getMaterialFromStack(stack);
    if (material == null) {
      material = IMaterial.UNKNOWN_ID;
    }
    cache.computeIfAbsent(material, this::bakeForMaterial).update(output, stack, resolver, displayContext, level, owner, seed);
  }

  private ItemModel bakeForMaterial(MaterialVariantId material) {
    ModelBaker baker = context.blockModelBaker();
    Material texture = new Material(unbaked.texture);
    ModelDebugName debugName = () -> TConstruct.MOD_ID + ":material_item/" + unbaked.texture;
    java.util.function.Function<Material,net.minecraft.client.renderer.texture.TextureAtlasSprite> spriteGetter = mat -> baker.materials().get(mat, debugName).sprite();

    QuadCollection.Builder builder = new QuadCollection.Builder();
    List<BakedQuad> quads = MaterialModel.getQuadsForMaterial(spriteGetter, texture, material, unbaked.index, offsetTransform(unbaked.offsetX, unbaked.offsetY), null);
    for (BakedQuad quad : quads) {
      builder.addUnculledFace(quad);
    }
    QuadCollection quadCollection = builder.build();

    ResolvedModel baseModel = baker.getModel(Identifier.fromNamespaceAndPath("neoforge", "item/default"));
    TextureSlots textureSlots = baseModel.getTopTextureSlots();
    ModelRenderProperties baseProperties = ModelRenderProperties.fromResolvedModel(baker, baseModel, textureSlots);
    ModelRenderProperties properties = new ModelRenderProperties(baseProperties.usesBlockLight(), new Material.Baked(spriteGetter.apply(texture), false), baseProperties.transforms());
    return new CuboidItemModelWrapper(List.of(), quadCollection, properties, transformation);
  }

  private static Transformation offsetTransform(float x, float y) {
    if (x == 0 && y == 0) {
      return Transformation.IDENTITY;
    }
    return new Transformation(new Vector3f(x / 16.0F, -y / 16.0F, 0), null, null, null);
  }

  public record Unbaked(Identifier texture, int index, float offsetX, float offsetY) implements ItemModel.Unbaked {
    public static final Identifier ID = TConstruct.getResource("material");
    public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
      Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture),
      Codec.INT.optionalFieldOf("index", 0).forGetter(Unbaked::index),
      Codec.FLOAT.optionalFieldOf("offset_x", 0.0F).forGetter(Unbaked::offsetX),
      Codec.FLOAT.optionalFieldOf("offset_y", 0.0F).forGetter(Unbaked::offsetY)
    ).apply(instance, Unbaked::new));

    @Override
    public MapCodec<? extends ItemModel.Unbaked> type() {
      return MAP_CODEC;
    }

    @Override
    public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
      return new MaterialItemModel(this, context, transformation);
    }

    @Override
    public void resolveDependencies(ResolvableModel.Resolver resolver) {}
  }
}
