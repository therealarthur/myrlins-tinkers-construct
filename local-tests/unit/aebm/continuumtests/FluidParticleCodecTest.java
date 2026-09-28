package aebm.continuumtests;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.shared.TinkerCommons;
import slimeknights.tconstruct.shared.particle.FluidParticleData;

import static org.junit.jupiter.api.Assertions.*;

/** Exercise the actual global particle codecs, including a second encoding after decode. */
final class FluidParticleCodecTest {
  private static List<FluidStack> fluids() {
    FluidStack named = new FluidStack(Fluids.WATER, 73);
    named.set(DataComponents.CUSTOM_NAME, Component.literal("particle component regression"));
    return List.of(new FluidStack(Fluids.WATER, 1), new FluidStack(Fluids.LAVA, 1000), named);
  }

  @Test
  void jsonRoundtripPreservesRegisteredTypeFluidAmountAndComponents() {
    var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
    for (FluidStack fluid : fluids()) {
      var original = new FluidParticleData(TinkerCommons.fluidParticle.get(), fluid);
      var encoded = ParticleTypes.CODEC.encodeStart(ops, original).getOrThrow();
      var decoded = assertInstanceOf(FluidParticleData.class, ParticleTypes.CODEC.parse(ops, encoded).getOrThrow());
      verify(original, decoded);
      assertEquals(encoded, ParticleTypes.CODEC.encodeStart(ops, decoded).getOrThrow());
    }
  }

  @Test
  void networkRoundtripPreservesRegisteredTypeAndCanBeRelayed() {
    var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    for (FluidStack fluid : fluids()) {
      var original = new FluidParticleData(TinkerCommons.fluidParticle.get(), fluid);
      var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.NEOFORGE);
      try {
        ParticleTypes.STREAM_CODEC.encode(buffer, original);
        int encodedBytes = buffer.writerIndex();
        var decoded = assertInstanceOf(FluidParticleData.class, ParticleTypes.STREAM_CODEC.decode(buffer));
        verify(original, decoded);
        assertFalse(buffer.isReadable(), "decoder must consume exactly one particle");
        ParticleTypes.STREAM_CODEC.encode(buffer, decoded);
        assertEquals(encodedBytes * 2, buffer.writerIndex());
        verify(original, assertInstanceOf(FluidParticleData.class, ParticleTypes.STREAM_CODEC.decode(buffer)));
        assertFalse(buffer.isReadable());
      } finally {
        buffer.release();
      }
    }
  }

  private static void verify(FluidParticleData original, FluidParticleData decoded) {
    assertSame(TinkerCommons.fluidParticle.get(), decoded.getType());
    assertEquals(original.getFluid().getAmount(), decoded.getFluid().getAmount());
    assertTrue(FluidStack.isSameFluidSameComponents(original.getFluid(), decoded.getFluid()));
  }
}
