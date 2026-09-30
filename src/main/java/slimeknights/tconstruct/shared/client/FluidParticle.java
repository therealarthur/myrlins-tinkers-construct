package slimeknights.tconstruct.shared.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import slimeknights.mantle.client.render.FluidRenderer;
import slimeknights.tconstruct.library.client.RenderUtils;
import slimeknights.tconstruct.shared.particle.FluidParticleData;

/** Particle type that renders a fluid still texture */
public class FluidParticle extends SingleQuadParticle {
  private final FluidStack fluid;
  private final float uCoord;
  private final float vCoord;

  protected FluidParticle(ClientLevel world, double x, double y, double z, double motionX, double motionY, double motionZ, FluidStack fluid, RandomSource random) {
    super(world, x, y, z, motionX, motionY, motionZ, sprite(fluid));
    this.fluid = fluid;
    FluidType fluidType = fluid.getFluid().getFluidType();
    int color = RenderUtils.getFluidColor(fluid, fluidType);
    if ((color >>> 24) == 0) {
      color |= 0xFF000000;
    }
    this.setAlpha(((color >> 24) & 0xFF) / 255f);
    this.rCol = ((color >> 16) & 0xFF) / 255f;
    this.gCol = ((color >> 8) & 0xFF) / 255f;
    this.bCol = (color & 0xFF) / 255f;
    this.gravity = 1.0F;
    this.quadSize /= 2.0F;
    this.uCoord = random.nextFloat() * 3.0F;
    this.vCoord = random.nextFloat() * 3.0F;
  }

  private static TextureAtlasSprite sprite(FluidStack fluid) {
    FluidType fluidType = fluid.getFluid().getFluidType();
    return FluidRenderer.getBlockSprite(RenderUtils.getStillTexture(fluid, fluidType));
  }

  @Override
  protected Layer getLayer() {
    // Parity (arthur.9): official 3.12.1 draws this particle on TERRAIN_SHEET, which blends, so the fluid tint alpha
    // always applies. Layer.bySprite would pick the opaque layer for an opaque still texture and drop that alpha.
    return Layer.TRANSLUCENT_TERRAIN;
  }

  @Override
  public ParticleRenderType getGroup() {
    return ParticleRenderType.SINGLE_QUADS;
  }

  @Override
  protected float getU0() {
    return spriteCoord(this.sprite.getU0(), this.sprite.getU1(), (this.uCoord + 1.0F) / 4.0F * 16.0F);
  }

  @Override
  protected float getU1() {
    return spriteCoord(this.sprite.getU0(), this.sprite.getU1(), this.uCoord / 4.0F * 16.0F);
  }

  @Override
  protected float getV0() {
    return spriteCoord(this.sprite.getV0(), this.sprite.getV1(), this.vCoord / 4.0F * 16.0F);
  }

  @Override
  protected float getV1() {
    return spriteCoord(this.sprite.getV0(), this.sprite.getV1(), (this.vCoord + 1.0F) / 4.0F * 16.0F);
  }

  private static float spriteCoord(float min, float max, float pixel) {
    return min + (max - min) * (pixel / 16.0F);
  }

  @Override
  protected int getLightCoords(float partialTick) {
    return FluidRenderer.withBlockLight(super.getLightCoords(partialTick), fluid.getFluid().getFluidType().getLightLevel(fluid));
  }

  /** Factory to create a fluid particle */
  public static class Factory implements ParticleProvider<FluidParticleData> {
    @Override
    public Particle createParticle(FluidParticleData data, ClientLevel worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
      FluidStack fluid = data.getFluid();
      return !fluid.isEmpty() ? new FluidParticle(worldIn, x, y, z, xSpeed, ySpeed, zSpeed, fluid, random) : null;
    }
  }
}
