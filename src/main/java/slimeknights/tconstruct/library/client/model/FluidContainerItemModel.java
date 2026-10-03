package slimeknights.tconstruct.library.client.model;

import com.google.common.base.Suppliers;
import com.mojang.serialization.MapCodec;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.model.item.DynamicFluidContainerModel;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import net.neoforged.neoforge.client.model.quad.BakedNormals;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import slimeknights.tconstruct.TConstruct;

/** Uses standard item sheets for Iris shader hands, keeping native fluid rendering elsewhere. */
public record FluidContainerItemModel(DynamicFluidContainerModel.Unbaked delegate) implements ItemModel.Unbaked {
  public static final Identifier ID = TConstruct.getResource("fluid_container");
  public static final MapCodec<FluidContainerItemModel> MAP_CODEC =
    DynamicFluidContainerModel.Unbaked.MAP_CODEC.xmap(FluidContainerItemModel::new, FluidContainerItemModel::delegate);

  @Override
  public MapCodec<FluidContainerItemModel> type() {
    return MAP_CODEC;
  }

  @Override
  public void resolveDependencies(Resolver resolver) {
    delegate.resolveDependencies(resolver);
  }

  @Override
  public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
    return withShaderHandModel(delegate.bake(context, transformation),
      () -> delegate.bake(new ItemModel.BakingContext(
        new FluidModelBaker(context.blockModelBaker()), context.entityModelSet(), context.sprites(),
        context.playerSkinRenderCache(), context.missingItemModel(), context.contextSwapper(), context.pendingAnimations()), transformation),
      () -> IrisState.SHADERS_ACTIVE.getAsBoolean());
  }

  /** Query current shader state on each hand update so shader toggles do not leave a stale model selected. */
  static ItemModel withShaderHandModel(ItemModel nativeModel, Supplier<ItemModel> shaderHandModel, BooleanSupplier shadersActive) {
    Supplier<ItemModel> shaderModel = Suppliers.memoize(shaderHandModel::get);
    return (output, item, resolver, displayContext, level, owner, seed) -> {
      ItemModel selected = displayContext.firstPerson() && shadersActive.getAsBoolean() ? shaderModel.get() : nativeModel;
      selected.update(output, item, resolver, displayContext, level, owner, seed);
    };
  }

  private static class IrisState {
    private static final BooleanSupplier SHADERS_ACTIVE = optionalIrisState(() -> ModList.get().isLoaded("iris"), Class::forName);
  }

  @FunctionalInterface
  interface ClassLookup {
    Class<?> load(String name) throws ClassNotFoundException;
  }

  /** Resolve the optional API once, only after the mod-presence guard succeeds. */
  static BooleanSupplier optionalIrisState(BooleanSupplier irisLoaded, ClassLookup classes) {
    Supplier<BooleanSupplier> query = Suppliers.memoize(() -> {
      try {
        Class<?> irisApi = classes.load("net.irisshaders.iris.api.v0.IrisApi");
        Object api = irisApi.getMethod("getInstance").invoke(null);
        Method shaderPackInUse = irisApi.getMethod("isShaderPackInUse");
        return () -> {
          try {
            return Boolean.TRUE.equals(shaderPackInUse.invoke(api));
          } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return false;
          }
        };
      } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
        return () -> false;
      }
    });
    return () -> irisLoaded.getAsBoolean() && query.get().getAsBoolean();
  }

  private record FluidModelBaker(ModelBaker delegate) implements ModelBaker {
    @Override public ResolvedModel getModel(Identifier id) { return delegate.getModel(id); }
    @Override public BlockStateModelPart missingBlockModelPart() { return delegate.missingBlockModelPart(); }
    @Override public MaterialBaker materials() { return delegate.materials(); }
    @Override public <T> T compute(SharedOperationKey<T> key) { return delegate.compute(key); }
    @Override public Interner interner() { return new FluidInterner(delegate.interner()); }
  }

  record FluidInterner(ModelBaker.Interner delegate) implements ModelBaker.Interner {
    @Override public Vector3fc vector(Vector3fc vector) { return delegate.vector(vector); }
    @Override public BakedNormals normals(BakedNormals normals) { return delegate.normals(normals); }
    @Override public BakedColors colors(BakedColors colors) { return delegate.colors(colors); }

    @Override
    public BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo material) {
      RenderType type = material.itemRenderType();
      RenderType standard = standardItemSheet(type);
      if (standard != type) {
        // Preserve material metadata while replacing the shader render pass.
        material = new BakedQuad.MaterialInfo(material.sprite(), material.layer(), standard,
          material.tintIndex(), material.shade(), material.lightEmission(), material.ambientOcclusion());
      }
      return delegate.materialInfo(material);
    }
  }

  private static RenderType standardItemSheet(RenderType type) {
    if (type == NeoForgeRenderTypes.getItemCutoutUnlit(TextureAtlas.LOCATION_BLOCKS)) {
      return Sheets.cutoutBlockItemSheet();
    }
    if (type == NeoForgeRenderTypes.getItemCutoutUnlit(TextureAtlas.LOCATION_ITEMS)) {
      return Sheets.cutoutItemSheet();
    }
    if (type == NeoForgeRenderTypes.getItemTranslucentUnlit(TextureAtlas.LOCATION_BLOCKS)) {
      return Sheets.translucentBlockItemSheet();
    }
    if (type == NeoForgeRenderTypes.getItemTranslucentUnlit(TextureAtlas.LOCATION_ITEMS)) {
      return Sheets.translucentItemSheet();
    }
    return type;
  }
}
