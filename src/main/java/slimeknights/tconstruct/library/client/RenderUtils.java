package slimeknights.tconstruct.library.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.joml.Vector3f;
import slimeknights.mantle.client.render.FluidCuboid;
import slimeknights.mantle.client.render.FluidRenderer;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.fluids.fluids.PotionFluidType;
import slimeknights.tconstruct.library.fluid.FluidTankAnimated;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RenderUtils {
  /** Copies the current pose for deferred custom geometry submission. */
  public static PoseStack copyPose(PoseStack source) {
    PoseStack copy = new PoseStack();
    copy.last().pose().set(source.last().pose());
    copy.last().normal().set(source.last().normal());
    return copy;
  }
  public static Identifier getStillTexture(FluidStack fluid, FluidType fluidType) {
    return FluidRenderer.getStillSprite(fluid).contents().name();
  }

  public static Identifier getFlowingTexture(FluidStack fluid, FluidType fluidType) {
    return FluidRenderer.getFlowingSprite(fluid).contents().name();
  }

  public static int getFluidColor(FluidStack fluid, FluidType fluidType) {
    // Potion color lives on the stack. The fluid model tint only knows the default magenta.
    if (fluid.getFluid() == TinkerFluids.potion.get()) {
      PotionContents contents = PotionFluidType.getPotionContents(fluid);
      if (contents.customColor().isPresent()) {
        return contents.customColor().get() | 0xFF000000;
      }
      if (contents.potion().isPresent() || !contents.customEffects().isEmpty()) {
        return contents.getColor() | 0xFF000000;
      }
    }
    return FluidRenderer.getFluidColor(fluid);
  }

  /** Renders a transparent fluid cuboid using SubmitNodeCollector */
  public static void renderTransparentCuboid(PoseStack matrices, VertexConsumer buffer, FluidCuboid cube, FluidStack fluid, int opacity, int light) {
    if (opacity < 0 || fluid.isEmpty()) return;
    FluidType fluidType = fluid.getFluid().getFluidType();
    TextureAtlasSprite still = FluidRenderer.getBlockSprite(getStillTexture(fluid, fluidType));
    TextureAtlasSprite flowing = FluidRenderer.getBlockSprite(getFlowingTexture(fluid, fluidType));
    boolean isGas = fluidType.isLighterThanAir();
    light = FluidRenderer.withBlockLight(light, fluidType.getLightLevel(fluid));
    int color = getFluidColor(fluid, fluidType);
    if (opacity < 0xFF) {
      int alpha = ((color >> 24) & 0xFF) * opacity / 0xFF;
      color = (color & 0xFFFFFF) | (alpha << 24);
    }
    FluidRenderer.renderCuboid(matrices, buffer, cube, still, flowing, cube.getFromScaled(), cube.getToScaled(), color, light, isGas);
  }

  /** Renders a fluid tank using SubmitNodeCollector */
  public static void renderFluidTank(PoseStack matrices, VertexConsumer buffer, FluidCuboid cube, FluidTankAnimated tank, int light, float partialTicks, boolean flipGas) {
    FluidStack liquid = tank.getFluid();
    int capacity = tank.getCapacity();
    if (!liquid.isEmpty() && capacity > 0) {
      float offset = tank.getRenderOffset();
      if (offset > 1.2f || offset < -1.2f) {
        offset = offset - ((offset / 12f + 0.1f) * partialTicks);
        tank.setRenderOffset(offset);
      } else {
        tank.setRenderOffset(0);
      }
      renderScaled(matrices, buffer, cube, liquid, offset, capacity, light, flipGas);
    } else {
      tank.setRenderOffset(0);
    }
  }

  /** Renders a scaled cuboid with VertexConsumer */
  public static void renderScaled(PoseStack matrices, VertexConsumer buffer, FluidCuboid cube, FluidStack fluid, float offset, int capacity, int light, boolean flipGas) {
    if (fluid.isEmpty() || capacity <= 0) {
      return;
    }
    FluidType fluidType = fluid.getFluid().getFluidType();
    TextureAtlasSprite still = FluidRenderer.getBlockSprite(getStillTexture(fluid, fluidType));
    TextureAtlasSprite flowing = FluidRenderer.getBlockSprite(getFlowingTexture(fluid, fluidType));
    boolean isGas = flipGas && fluidType.isLighterThanAir();
    light = FluidRenderer.withBlockLight(light, fluidType.getLightLevel(fluid));
    int color = getFluidColor(fluid, fluidType);
    Vector3f from = cube.getFromScaled();
    Vector3f to = cube.getToScaled();
    float minY = from.y();
    float maxY = to.y();
    float fill = Math.max(0, Math.min(1, (fluid.getAmount() - offset) / capacity));
    if (isGas) {
      from = new Vector3f(from.x(), maxY + (fill * (minY - maxY)), from.z());
    } else {
      to = new Vector3f(to.x(), minY + (fill * (maxY - minY)), to.z());
    }
    FluidRenderer.renderCuboid(matrices, buffer, cube, still, flowing, from, to, color, light, isGas);
  }
}
