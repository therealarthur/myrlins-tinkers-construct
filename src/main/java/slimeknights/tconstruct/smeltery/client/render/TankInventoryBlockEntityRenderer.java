package slimeknights.tconstruct.smeltery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import slimeknights.mantle.client.render.FluidCuboid;
import slimeknights.mantle.client.render.MantleRenderTypes;
import slimeknights.mantle.client.render.RenderItem;
import slimeknights.mantle.client.render.RenderingHelper;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.client.RenderUtils;
import slimeknights.tconstruct.library.fluid.FluidTankAnimated;
import slimeknights.tconstruct.smeltery.block.entity.ITankBlockEntity.ITankInventoryBlockEntity;

import java.util.List;

public class TankInventoryBlockEntityRenderer<T extends BlockEntity & ITankInventoryBlockEntity> implements BlockEntityRenderer<T, TankInventoryRenderState> {
  private final EnumProperty<Direction> directionProperty;
  private final ItemModelResolver itemModelResolver;
  public TankInventoryBlockEntityRenderer(EnumProperty<Direction> directionProperty, Context context) {
    this.directionProperty = directionProperty;
    this.itemModelResolver = context.itemModelResolver();
  }

  @Override
  public TankInventoryRenderState createRenderState() {
    return new TankInventoryRenderState();
  }

  @Override
  public void extractRenderState(T melter, TankInventoryRenderState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
    BlockEntityRenderState.extractBase(melter, state, breakProgress);
    state.blockState = melter.getBlockState();
    FluidTankAnimated tank = melter.getTank();
    state.fluidStack = tank.getFluid();
    state.capacity = tank.getCapacity();
    float offset = tank.getRenderOffset();
    if (offset > 1.2f || offset < -1.2f) {
      offset = offset - ((offset / 12f + 0.1f) * partialTicks);
      tank.setRenderOffset(offset);
      state.renderOffset = offset;
    } else {
      tank.setRenderOffset(0);
      state.renderOffset = 0;
    }
    state.items.clear();
    List<RenderItem> renderItems = RenderItem.STATE_REGISTRY.get(state.blockState, List.of());
    for (int i = 0; i < Math.min(melter.getItemHandler().getSlots(), renderItems.size()); i++) {
      TankInventoryRenderState.ItemEntry entry = new TankInventoryRenderState.ItemEntry();
      this.itemModelResolver.updateForTopItem(entry.itemState, melter.getItemHandler().getStackInSlot(i), renderItems.get(i).getTransform(), melter.getLevel(), null, (int)(melter.getBlockPos().asLong() + i));
      state.items.add(entry);
    }
  }

  @Override
  public void submit(TankInventoryRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
    BlockState blockState = state.blockState;
    boolean useModel = false;
    try {
        useModel = Config.CLIENT != null && Config.CLIENT.tankFluidModel != null && Config.CLIENT.tankFluidModel.get();
    } catch (Exception e) {}
    List<FluidCuboid> fluids = useModel ? List.of() : FluidCuboid.REGISTRY.get(blockState, List.of());
    List<RenderItem> renderItems = RenderItem.STATE_REGISTRY.get(blockState, List.of());
    if (!fluids.isEmpty() || !renderItems.isEmpty()) {
      boolean isRotated = RenderingHelper.applyRotation(poseStack, blockState.getValue(directionProperty));

      if (!fluids.isEmpty()) {
        PoseStack renderPose = RenderUtils.copyPose(poseStack);
        submitNodeCollector.submitCustomGeometry(renderPose, MantleRenderTypes.FLUID, (pose, buffer) -> {
          PoseStack localPoseStack = new PoseStack();
          localPoseStack.last().pose().set(pose.pose());
          localPoseStack.last().normal().set(pose.normal());
          for (FluidCuboid fluid : fluids) {
            RenderUtils.renderScaled(localPoseStack, buffer, fluid, state.fluidStack, state.renderOffset, state.capacity, state.lightCoords, true);
          }
        });
      }

      for (int i = 0; i < Math.min(renderItems.size(), state.items.size()); i++) {
        RenderingHelper.renderItem(poseStack, submitNodeCollector, state.items.get(i).itemState, renderItems.get(i), state.lightCoords);
      }

      if (isRotated) {
        poseStack.popPose();
      }
    }
  }
}
