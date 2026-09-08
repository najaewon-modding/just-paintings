package net.njw.justpaintings.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.njw.justpaintings.entity.CustomPaintingEntity;

import java.util.UUID;

public final class CustomPaintingRenderer extends EntityRenderer<CustomPaintingEntity, CustomPaintingRenderer.State> {
    public CustomPaintingRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CustomPaintingEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.imageId = entity.getImageId();
        state.width = entity.getPaintingWidth();
        state.height = entity.getPaintingHeight();
        state.direction = entity.getDirection();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        super.submit(state, poseStack, collector, cameraState);
        Identifier texture = ClientPaintingTextures.getOrRequest(state.imageId);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.direction.toYRot()));
        float halfWidth = state.width * 0.5F;
        float halfHeight = state.height * 0.5F;
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, buffer) -> renderQuad(pose, buffer, halfWidth, halfHeight, light));
        poseStack.popPose();
    }

    private static void renderQuad(PoseStack.Pose pose, VertexConsumer buffer, float halfWidth, float halfHeight, int light) {
        vertex(buffer, pose, -halfWidth, halfHeight, 0.0F, 0.0F, 0.0F, light);
        vertex(buffer, pose, halfWidth, halfHeight, 0.0F, 1.0F, 0.0F, light);
        vertex(buffer, pose, halfWidth, -halfHeight, 0.0F, 1.0F, 1.0F, light);
        vertex(buffer, pose, -halfWidth, -halfHeight, 0.0F, 0.0F, 1.0F, light);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light) {
        buffer.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(0).setLight(light).setNormal(pose, 0.0F, 0.0F, -1.0F);
    }

    public static final class State extends EntityRenderState {
        private UUID imageId = new UUID(0L, 0L);
        private int width = 1;
        private int height = 1;
        private Direction direction = Direction.SOUTH;
    }
}
