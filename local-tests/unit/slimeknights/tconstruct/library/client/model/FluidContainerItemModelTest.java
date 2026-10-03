package slimeknights.tconstruct.library.client.model;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

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
}
