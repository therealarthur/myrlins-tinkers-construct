package slimeknights.tconstruct.fluids;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import slimeknights.mantle.fluid.FlowingFluidEffects;
import slimeknights.tconstruct.shared.TinkerCommons;
import slimeknights.tconstruct.shared.particle.FluidParticleData;

import java.util.IdentityHashMap;
import java.util.Map;

/** Surface pops, underside drips, and a splash when the player steps into a Tinkers fluid. */
public final class FluidAmbientClient {
  private static final Map<Fluid, ParticleOptions> PARTICLES = new IdentityHashMap<>();
  private static boolean wasInCustomFluid;

  private FluidAmbientClient() {}

  public static void init() {
    NeoForge.EVENT_BUS.addListener(FluidAmbientClient::splashOnEnter);
    FlowingFluidEffects.setHandler(new FlowingFluidEffects.Handler() {
      @Override
      public ParticleOptions drip(Fluid source) {
        return particle(source);
      }

      @Override
      public void animate(Fluid fluid, Level level, BlockPos pos, FluidState state, RandomSource random) {
        if (random.nextInt(12) != 0) {
          return;
        }
        BlockPos above = pos.above();
        if (!level.getBlockState(above).isAir() && !level.getFluidState(above).isEmpty()) {
          return;
        }
        Fluid source = fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
        double surface = pos.getY() + Math.min(state.getHeight(level, pos), 0.9);
        level.addParticle(particle(source), pos.getX() + random.nextDouble(), surface, pos.getZ() + random.nextDouble(), 0.0, 0.04, 0.0);
      }
    });
  }

  public static ParticleOptions particle(Fluid fluid) {
    Fluid source = fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
    return PARTICLES.computeIfAbsent(source, still -> new FluidParticleData(TinkerCommons.fluidParticle.get(), new FluidStack(still, 1)));
  }

  private static void splashOnEnter(ClientTickEvent.Post event) {
    Player player = Minecraft.getInstance().player;
    if (player == null) {
      wasInCustomFluid = false;
      return;
    }
    boolean inFluid = player.getFluidInteraction().isInFluidMatching(player, (entity, type, height) -> isCustom(type) && height > 0.0);
    if (inFluid && !wasInCustomFluid) {
      FluidState state = player.level().getFluidState(player.blockPosition());
      if (state.isEmpty()) {
        state = player.level().getFluidState(BlockPos.containing(player.getX(), player.getY() - 0.1, player.getZ()));
      }
      if (!state.isEmpty() && isCustom(state.getType().getFluidType())) {
        ParticleOptions particle = particle(state.getType());
        RandomSource random = player.getRandom();
        for (int i = 0; i < 16; i++) {
          double xo = (random.nextDouble() - 0.5) * player.getBbWidth();
          double zo = (random.nextDouble() - 0.5) * player.getBbWidth();
          player.level().addParticle(particle, player.getX() + xo, player.getY() + 0.2, player.getZ() + zo, xo * 0.2, 0.15 + random.nextDouble() * 0.1, zo * 0.2);
        }
      }
    }
    wasInCustomFluid = inFluid;
  }

  private static boolean isCustom(FluidType type) {
    return type != null && !type.isVanilla();
  }
}
