package slimeknights.tconstruct.shared.particle;

import com.mojang.serialization.MapCodec;
import lombok.Getter;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;

/** Particle data for a fluid particle */
public class FluidParticleData implements ParticleOptions {
  @Getter
  private final ParticleType<FluidParticleData> type;
  @Getter
  private final FluidStack fluid;

  @SuppressWarnings("unchecked")
  public FluidParticleData(ParticleType<?> type, FluidStack fluid) {
    this.type = (ParticleType<FluidParticleData>) type;
    this.fluid = fluid;
  }

  /** Particle type for a fluid particle */
  public static class Type extends ParticleType<FluidParticleData> {
    private final Identifier id;
    private final MapCodec<FluidParticleData> codec;
    private final StreamCodec<? super RegistryFriendlyByteBuf, FluidParticleData> streamCodec;

    public Type(Identifier id) {
      super(false);
      this.id = id;
      // The decoded options must keep this type. A null type makes the client drop the particle.
      this.codec = FluidStack.MAP_CODEC.xmap(
        fluid -> new FluidParticleData(this, fluid), data -> data.fluid
      ).fieldOf("fluid");
      this.streamCodec = FluidStack.STREAM_CODEC.map(
        fluid -> new FluidParticleData(this, fluid), data -> data.fluid
      );
    }

    @Override
    public MapCodec<FluidParticleData> codec() {
      return codec;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, FluidParticleData> streamCodec() {
      return streamCodec;
    }
  }
}
