package slimeknights.tconstruct.library.client.model;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FluidContainerItemModelTest {
  private static final class TestSprite extends TextureAtlasSprite {
    TestSprite(Identifier atlas, SpriteContents contents) {
      super(atlas, contents, 1, 1, 0, 0, 0);
    }
  }

  private static final class RecordingInterner implements ModelBaker.Interner {
    private BakedQuad.MaterialInfo received;
    private int calls;

    @Override public Vector3fc vector(Vector3fc vector) { return vector; }
    @Override public BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo material) {
      received = material;
      calls++;
      return material;
    }
  }

  @Test
  void allUnlitItemPassesKeepTheirAtlasAndMaterialMetadata() {
    Identifier[] atlases = {TextureAtlas.LOCATION_BLOCKS, TextureAtlas.LOCATION_ITEMS};
    for (Identifier atlas : atlases) {
      try (var contents = new SpriteContents(Identifier.parse("tconstruct:test_fluid"), new FrameSize(1, 1), new NativeImage(1, 1, true))) {
        var sprite = new TestSprite(atlas, contents);
        for (boolean translucent : new boolean[]{false, true}) {
          RenderType originalType = translucent ? NeoForgeRenderTypes.getItemTranslucentUnlit(atlas) : NeoForgeRenderTypes.getItemCutoutUnlit(atlas);
          RenderType expected = atlas.equals(TextureAtlas.LOCATION_BLOCKS)
            ? (translucent ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockItemSheet())
            : (translucent ? Sheets.translucentItemSheet() : Sheets.cutoutItemSheet());
          var original = new BakedQuad.MaterialInfo(sprite,
            translucent ? ChunkSectionLayer.TRANSLUCENT : ChunkSectionLayer.SOLID,
            originalType, 3, translucent, translucent ? 7 : 15, !translucent);
          var delegate = new RecordingInterner();
          var actual = new FluidContainerItemModel.FluidInterner(delegate).materialInfo(original);

          assertNotSame(original, actual);
          assertSame(expected, actual.itemRenderType());
          assertSame(sprite, actual.sprite());
          assertSame(original.layer(), actual.layer());
          assertEquals(original.tintIndex(), actual.tintIndex());
          assertEquals(original.shade(), actual.shade());
          assertEquals(original.lightEmission(), actual.lightEmission());
          assertEquals(original.ambientOcclusion(), actual.ambientOcclusion());
          assertSame(actual, delegate.received);
          assertEquals(1, delegate.calls);
        }
      }
    }
  }

  @Test
  void unrelatedPassAndAlreadyStandardPassesRetainMaterialIdentity() {
    RenderType[] controls = {Sheets.cutoutBlockItemSheet(), Sheets.cutoutItemSheet(),
      Sheets.translucentBlockItemSheet(), Sheets.translucentItemSheet(), Sheets.cutoutBlockSheet()};
    try (var contents = new SpriteContents(Identifier.parse("tconstruct:test_control"), new FrameSize(1, 1), new NativeImage(1, 1, true))) {
      var sprite = new TestSprite(TextureAtlas.LOCATION_BLOCKS, contents);
      for (RenderType control : controls) {
        var original = new BakedQuad.MaterialInfo(sprite, ChunkSectionLayer.SOLID, control, -1, true, 0, true);
        var delegate = new RecordingInterner();
        assertSame(original, new FluidContainerItemModel.FluidInterner(delegate).materialInfo(original));
        assertSame(original, delegate.received);
        assertEquals(1, delegate.calls);
      }
    }
  }

  @Test
  void absentIrisNeverLoadsItsApi() {
    var status = FluidContainerItemModel.optionalIrisState(() -> false, name -> {
      throw new AssertionError("Absent Iris must not load " + name);
    });
    assertFalse(status.getAsBoolean());
    assertFalse(status.getAsBoolean());
  }

  public static final class TestIrisApi {
    private static final TestIrisApi INSTANCE = new TestIrisApi();
    private static int instanceRequests;
    private boolean enabled;
    private boolean failQuery;

    public static TestIrisApi getInstance() {
      instanceRequests++;
      return INSTANCE;
    }

    public boolean isShaderPackInUse() {
      if (failQuery) {
        throw new IllegalStateException("Shader status unavailable");
      }
      return enabled;
    }
  }

  @Test
  void optionalApiIsResolvedOnceButShaderStateRemainsLive() {
    TestIrisApi.instanceRequests = 0;
    TestIrisApi.INSTANCE.enabled = false;
    TestIrisApi.INSTANCE.failQuery = false;
    var lookups = new AtomicInteger();
    var status = FluidContainerItemModel.optionalIrisState(() -> true, name -> {
      assertEquals("net.irisshaders.iris.api.v0.IrisApi", name);
      lookups.incrementAndGet();
      return TestIrisApi.class;
    });
    assertEquals(0, lookups.get());
    assertFalse(status.getAsBoolean());
    TestIrisApi.INSTANCE.enabled = true;
    assertTrue(status.getAsBoolean());
    TestIrisApi.INSTANCE.failQuery = true;
    assertFalse(status.getAsBoolean(), "an optional API failure must select the native model");
    TestIrisApi.INSTANCE.failQuery = false;
    assertTrue(status.getAsBoolean(), "query failure must not cache a stale disabled status");
    TestIrisApi.INSTANCE.enabled = false;
    assertFalse(status.getAsBoolean());
    assertEquals(1, lookups.get());
    assertEquals(1, TestIrisApi.instanceRequests);
  }

  @Test
  void unavailableOptionalApiFallsBackWithoutRepeatedClassLoading() {
    var lookups = new AtomicInteger();
    var status = FluidContainerItemModel.optionalIrisState(() -> true, name -> {
      lookups.incrementAndGet();
      throw new ClassNotFoundException(name);
    });
    assertFalse(status.getAsBoolean());
    assertFalse(status.getAsBoolean());
    assertEquals(1, lookups.get());
  }

  @Test
  void onlyActiveShaderHandsSelectTheLazyAdapterAndTogglesRestoreNativeRendering() {
    var enabled = new AtomicBoolean(true);
    var queries = new AtomicInteger();
    var shaderBakes = new AtomicInteger();
    var nativeContexts = new ArrayList<ItemDisplayContext>();
    var shaderContexts = new ArrayList<ItemDisplayContext>();
    var state = new ItemStackRenderState();
    ItemModel nativeModel = (output, item, resolver, context, level, owner, seed) -> {
      assertSame(state, output);
      assertSame(ItemStack.EMPTY, item);
      assertEquals(73, seed);
      nativeContexts.add(context);
    };
    ItemModel shaderModel = (output, item, resolver, context, level, owner, seed) -> {
      assertSame(state, output);
      assertSame(ItemStack.EMPTY, item);
      assertEquals(73, seed);
      shaderContexts.add(context);
    };
    ItemModel routed = FluidContainerItemModel.withShaderHandModel(nativeModel, () -> {
      shaderBakes.incrementAndGet();
      return shaderModel;
    }, () -> {
      queries.incrementAndGet();
      return enabled.get();
    });
    var nativeOnly = List.of(ItemDisplayContext.NONE, ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
      ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.HEAD, ItemDisplayContext.GUI,
      ItemDisplayContext.GROUND, ItemDisplayContext.FIXED, ItemDisplayContext.ON_SHELF);
    for (var context : nativeOnly) {
      routed.update(state, ItemStack.EMPTY, null, context, null, null, 73);
    }
    assertEquals(nativeOnly, nativeContexts);
    assertEquals(0, queries.get(), "non-hand rendering must not query or load Iris");
    assertEquals(0, shaderBakes.get());

    enabled.set(false);
    routed.update(state, ItemStack.EMPTY, null, ItemDisplayContext.FIRST_PERSON_LEFT_HAND, null, null, 73);
    assertEquals(0, shaderBakes.get());
    enabled.set(true);
    routed.update(state, ItemStack.EMPTY, null, ItemDisplayContext.FIRST_PERSON_LEFT_HAND, null, null, 73);
    routed.update(state, ItemStack.EMPTY, null, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, null, null, 73);
    enabled.set(false);
    routed.update(state, ItemStack.EMPTY, null, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, null, null, 73);

    assertEquals(List.of(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND), shaderContexts);
    assertEquals(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, nativeContexts.get(nativeOnly.size()));
    assertEquals(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, nativeContexts.get(nativeOnly.size() + 1));
    assertEquals(4, queries.get());
    assertEquals(1, shaderBakes.get());
  }
}
