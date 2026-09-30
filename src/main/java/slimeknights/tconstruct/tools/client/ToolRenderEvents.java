package slimeknights.tconstruct.tools.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.CustomBlockOutlineRenderer;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.definition.module.aoe.AreaOfEffectIterator.AOEMatchType;
import slimeknights.tconstruct.library.tools.definition.module.mining.IsEffectiveToolHook;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.utils.BlockSideHitListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.List;

public class ToolRenderEvents {
  /** Maximum number of blocks from the iterator to render */
  private static final int MAX_BLOCKS = 60;

  /**
   * Renders the outline on the extra blocks.
   *
   * @param event the outline extraction event
   */
  @SubscribeEvent
  static void renderBlockHighlights(ExtractBlockOutlineRenderStateEvent event) {
    Level world = event.getLevel();
    Player player = Minecraft.getInstance().player;
    if (world == null || player == null) {
      return;
    }

    ItemStack stack = player.getMainHandItem();
    if (stack.isEmpty() || !stack.is(TinkerTags.Items.MODIFIABLE)) {
      return;
    }

    ToolStack tool = ToolStack.from(stack);
    if (tool.isBroken()) {
      return;
    }

    BlockHitResult blockTrace = event.getHitResult();
    BlockPos origin = event.getBlockPos();
    BlockState state = event.getBlockState();
    AOEMatchType matchType = AOEMatchType.BREAKING;
    if (tool.getModifiers().has(TinkerTags.Modifiers.AOE_INTERACTION)) {
      matchType = AOEMatchType.DISPLAY;
    } else if (!IsEffectiveToolHook.isEffective(tool, state)) {
      return;
    }

    UseOnContext context = new UseOnContext(world, player, InteractionHand.MAIN_HAND, stack, blockTrace);
    Iterator<BlockPos> extraBlocks = tool.getHook(ToolHooks.AOE_ITERATOR).getBlocks(tool, context, state, matchType).iterator();
    if (!extraBlocks.hasNext()) {
      return;
    }

    Vec3 camera = event.getCamera().position();
    List<ExtraOutline> outlines = new ArrayList<>();
    int rendered = 0;
    do {
      BlockPos pos = extraBlocks.next();
      if (!pos.equals(origin) && world.getWorldBorder().isWithinBounds(pos)) {
        VoxelShape shape = world.getBlockState(pos).getShape(world, pos, event.getCollisionContext());
        if (shape.isEmpty()) {
          continue;
        }
        outlines.add(new ExtraOutline(pos.immutable(), shape));
        rendered++;
      }
    } while (rendered < MAX_BLOCKS && extraBlocks.hasNext());

    if (!outlines.isEmpty()) {
      event.addCustomRenderer(new AoeOutlineRenderer(List.copyOf(outlines), camera));
    }
  }

  private record ExtraOutline(BlockPos pos, VoxelShape shape) {}

  private record AoeOutlineRenderer(List<ExtraOutline> outlines, Vec3 camera) implements CustomBlockOutlineRenderer {
    @Override
    public boolean render(BlockOutlineRenderState renderState, MultiBufferSource.BufferSource buffer, PoseStack poseStack, boolean translucentPass, LevelRenderState levelRenderState) {
      if (translucentPass != renderState.isTranslucent()) {
        return false;
      }

      VertexConsumer vertexBuilder = buffer.getBuffer(RenderTypes.lines());
      int color = renderState.highContrast() ? 0xFFFFFFFF : 0xFF000000;
      float alpha = renderState.highContrast() ? 1.0F : 0.4F;
      for (ExtraOutline outline : outlines) {
        BlockPos pos = outline.pos();
        ShapeRenderer.renderShape(poseStack, vertexBuilder, outline.shape(), pos.getX() - camera.x(), pos.getY() - camera.y(), pos.getZ() - camera.z(), color, alpha);
      }
      return false;
    }
  }

  /**
   * Adds extra mining cracks to the state consumed by Minecraft's normal breaking-model renderer.
   * Upstream 3.12.4 fixed the same missing cracks with a SubmitCustomGeometryEvent renderer that reads the local
   * player's destroy stage. The merge keeps only this extract-phase version (running both would draw every crack twice):
   * like official 3.12.1 it takes the progress recorded for the targeted block, and vanilla's submit pass applies the
   * same model render shape check official's renderBreakingTexture did.
   */
  @SubscribeEvent
  static void renderBlockDamageProgress(ExtractLevelRenderStateEvent event) {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.gameMode == null || !minecraft.gameMode.isDestroying() || minecraft.getCameraEntity() == null) {
      return;
    }
    Level world = event.getLevel();
    Player player = minecraft.player;
    if (player == null || player.level() != world) {
      return;
    }
    ItemStack stack = player.getMainHandItem();
    if (stack.isEmpty() || !stack.is(TinkerTags.Items.HARVEST)
        || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
      return;
    }
    ToolStack tool = ToolStack.from(stack);
    if (tool.isBroken()) {
      return;
    }
    BlockPos target = hit.getBlockPos();
    BlockState state = world.getBlockState(target);
    if (!IsEffectiveToolHook.isEffective(tool, state)) {
      return;
    }

    // Vanilla has already extracted this frame's cracks when the event fires. Do not invent
    // progress or replace another miner's state for a block that is already in the list.
    List<BlockBreakingRenderState> breaking = event.getRenderState().blockBreakingRenderStates;
    Set<BlockPos> existing = new HashSet<>();
    int progress = -1;
    for (BlockBreakingRenderState entry : breaking) {
      existing.add(entry.blockPos());
      if (entry.blockPos().equals(target)) {
        progress = Math.max(progress, entry.progress());
      }
    }
    if (progress < 0 || progress > 9) {
      return;
    }
    UseOnContext context = new UseOnContext(world, player, InteractionHand.MAIN_HAND, stack,
      hit.withDirection(BlockSideHitListener.getClientSideHit()));
    Iterator<BlockPos> extraBlocks = tool.getHook(ToolHooks.AOE_ITERATOR).getBlocks(tool, context, state, AOEMatchType.BREAKING).iterator();
    for (int count = 0; count < MAX_BLOCKS && extraBlocks.hasNext(); count++) {
      BlockPos pos = extraBlocks.next().immutable();
      if (pos.equals(target) || existing.contains(pos) || !world.hasChunkAt(pos) || !world.getWorldBorder().isWithinBounds(pos)) {
        continue;
      }
      BlockState extraState = world.getBlockState(pos);
      if (!extraState.isAir()) {
        breaking.add(new BlockBreakingRenderState(pos, extraState, progress));
        existing.add(pos);
      }
    }
  }
}
