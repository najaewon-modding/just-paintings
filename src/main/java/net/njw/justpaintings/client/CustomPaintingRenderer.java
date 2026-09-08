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
    private static final float FRAME = 1.0F / 16.0F;
    private static final float DEPTH = 1.0F / 16.0F;
    private static final float FRONT_Z = -DEPTH * 0.5F;
    private static final float BACK_Z = DEPTH * 0.5F;
    private static final float IMAGE_Z = FRONT_Z - 0.001F;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final Identifier FRAME_TEXTURE = Identifier.withDefaultNamespace("textures/painting/back.png");

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
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(FRAME_TEXTURE), (pose, buffer) -> renderFrame(pose, buffer, halfWidth, halfHeight, state.lightCoords));
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(texture), (pose, buffer) -> renderImage(pose, buffer, halfWidth - FRAME, halfHeight - FRAME));
        poseStack.popPose();
    }

    private static void renderImage(PoseStack.Pose pose, VertexConsumer buffer, float halfWidth, float halfHeight) {
        face(buffer, pose,
                -halfWidth, halfHeight, IMAGE_Z,
                halfWidth, halfHeight, IMAGE_Z,
                halfWidth, -halfHeight, IMAGE_Z,
                -halfWidth, -halfHeight, IMAGE_Z,
                0.0F, 0.0F, -1.0F, FULL_BRIGHT);
    }

    private static void renderFrame(PoseStack.Pose pose, VertexConsumer buffer, float halfWidth, float halfHeight, int light) {
        cuboid(buffer, pose, -halfWidth, halfHeight - FRAME, FRONT_Z, halfWidth, halfHeight, BACK_Z, light);
        cuboid(buffer, pose, -halfWidth, -halfHeight, FRONT_Z, halfWidth, -halfHeight + FRAME, BACK_Z, light);
        cuboid(buffer, pose, -halfWidth, -halfHeight + FRAME, FRONT_Z, -halfWidth + FRAME, halfHeight - FRAME, BACK_Z, light);
        cuboid(buffer, pose, halfWidth - FRAME, -halfHeight + FRAME, FRONT_Z, halfWidth, halfHeight - FRAME, BACK_Z, light);
        face(buffer, pose,
                -halfWidth + FRAME, halfHeight - FRAME, BACK_Z,
                -halfWidth + FRAME, -halfHeight + FRAME, BACK_Z,
                halfWidth - FRAME, -halfHeight + FRAME, BACK_Z,
                halfWidth - FRAME, halfHeight - FRAME, BACK_Z,
                0.0F, 0.0F, 1.0F, light);
    }

    private static void cuboid(VertexConsumer buffer, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        face(buffer, pose, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, 0.0F, 0.0F, -1.0F, light);
        face(buffer, pose, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1, 0.0F, 0.0F, 1.0F, light);
        face(buffer, pose, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, -1.0F, 0.0F, 0.0F, light);
        face(buffer, pose, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, 1.0F, 0.0F, 0.0F, light);
        face(buffer, pose, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0.0F, 1.0F, 0.0F, light);
        face(buffer, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0.0F, -1.0F, 0.0F, light);
    }

    private static void face(VertexConsumer buffer, PoseStack.Pose pose,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz, int light) {
        vertex(buffer, pose, x0, y0, z0, 0.0F, 0.0F, nx, ny, nz, light);
        vertex(buffer, pose, x1, y1, z1, 1.0F, 0.0F, nx, ny, nz, light);
        vertex(buffer, pose, x2, y2, z2, 1.0F, 1.0F, nx, ny, nz, light);
        vertex(buffer, pose, x3, y3, z3, 0.0F, 1.0F, nx, ny, nz, light);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, float nx, float ny, float nz, int light) {
        buffer.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(0).setLight(light).setNormal(pose, nx, ny, nz);
    }

    public static final class State extends EntityRenderState {
        private UUID imageId = new UUID(0L, 0L);
        private int width = 1;
        private int height = 1;
        private Direction direction = Direction.SOUTH;
    }
}
