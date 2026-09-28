package slimeknights.tconstruct.shared.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.client.render.FluidRenderer;
import slimeknights.tconstruct.library.client.RenderUtils;
import slimeknights.tconstruct.shared.particle.FluidParticleData;

/** A small, gravity-driven piece of the fluid's still texture. */
public class FluidParticle extends SingleQuadParticle {
  private final FluidStack fluid;
  private final float uCoord;
  private final float vCoord;

  protected FluidParticle(ClientLevel level, double x, double y, double z,
                          double motionX, double motionY, double motionZ, FluidStack fluid) {
    super(level, x, y, z, motionX, motionY, motionZ,
      FluidRenderer.getBlockSprite(RenderUtils.getStillTexture(fluid, fluid.getFluidType())));
    this.fluid = fluid;
    this.gravity = 1.0F;
    int color = RenderUtils.getFluidColor(fluid, fluid.getFluidType());
    this.alpha = ((color >> 24) & 0xFF) / 255f;
    this.rCol = ((color >> 16) & 0xFF) / 255f;
    this.gCol = ((color >> 8) & 0xFF) / 255f;
    this.bCol = (color & 0xFF) / 255f;
    this.quadSize /= 2.0F;
    this.uCoord = this.random.nextFloat() * 3.0F;
    this.vCoord = this.random.nextFloat() * 3.0F;
  }

  @Override
  protected Layer getLayer() {
    // Fluid tint can contain alpha even when the underlying sprite is opaque.
    return Layer.TRANSLUCENT_TERRAIN;
  }

  @Override
  protected float getU0() {
    return this.sprite.getU((this.uCoord + 1.0F) / 4.0F);
  }

  @Override
  protected float getU1() {
    return this.sprite.getU(this.uCoord / 4.0F);
  }

  @Override
  protected float getV0() {
    return this.sprite.getV(this.vCoord / 4.0F);
  }

  @Override
  protected float getV1() {
    return this.sprite.getV((this.vCoord + 1.0F) / 4.0F);
  }

  @Override
  protected int getLightCoords(float partialTick) {
    return FluidRenderer.withBlockLight(super.getLightCoords(partialTick), fluid.getFluidType().getLightLevel(fluid));
  }

  public static class Factory implements ParticleProvider<FluidParticleData> {
    @Override
    public Particle createParticle(FluidParticleData data, ClientLevel worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
      FluidStack fluid = data.getFluid();
      return fluid.isEmpty() ? null : new FluidParticle(worldIn, x, y, z, xSpeed, ySpeed, zSpeed, fluid);
    }
  }
}
