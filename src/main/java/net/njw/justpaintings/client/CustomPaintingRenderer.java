package net.njw.justpaintings.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.njw.justpaintings.entity.CustomPaintingEntity;

import java.util.UUID;

public final class CustomPaintingRenderer extends EntityRenderer<CustomPaintingEntity, CustomPaintingRenderer.State> {
    private static final Identifier BACK_SPRITE_LOCATION = Identifier.withDefaultNamespace("back");
    private static final Identifier EMPTY_FRONT_TEXTURE = Identifier.withDefaultNamespace("textures/map/map_background.png");
    private static final float FRAME = 1.0F / 16.0F;
    private static final float FRONT_FRAME_Z = -0.03135F;
    private final TextureAtlas paintingsAtlas;

    public CustomPaintingRenderer(EntityRendererProvider.Context context) {
        super(context);
        paintingsAtlas = context.getAtlas(AtlasIds.PAINTINGS);
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
        if (state.lightCoordsPerBlock.length != state.width * state.height) state.lightCoordsPerBlock = new int[state.width * state.height];
        float offsetX = -state.width / 2.0F;
        float offsetY = -state.height / 2.0F;
        Level level = entity.level();
        for (int segmentY = 0; segmentY < state.height; segmentY++) {
            for (int segmentX = 0; segmentX < state.width; segmentX++) {
                float segmentOffsetX = segmentX + offsetX + 0.5F;
                float segmentOffsetY = segmentY + offsetY + 0.5F;
                int x = entity.getBlockX();
                int y = Mth.floor(entity.getY() + segmentOffsetY);
                int z = entity.getBlockZ();
                switch (state.direction) {
                    case NORTH -> x = Mth.floor(entity.getX() + segmentOffsetX);
                    case WEST -> z = Mth.floor(entity.getZ() - segmentOffsetX);
                    case SOUTH -> x = Mth.floor(entity.getX() - segmentOffsetX);
                    case EAST -> z = Mth.floor(entity.getZ() + segmentOffsetX);
                }
                state.lightCoordsPerBlock[segmentX + segmentY * state.width] = LevelRenderer.getLightCoords(level, new BlockPos(x, y, z));
            }
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        Identifier frontTexture = ClientPaintingTextures.getOrRequest(state.imageId);
        if (frontTexture == null) frontTexture = EMPTY_FRONT_TEXTURE;
        TextureAtlasSprite backSprite = paintingsAtlas.getSprite(BACK_SPRITE_LOCATION);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.direction.get2DDataValue() * 90.0F));
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolidZOffsetForward(backSprite.atlasLocation()), (pose, buffer) -> renderBackEdgesAndFrame(pose, buffer, state.lightCoordsPerBlock, state.width, state.height, backSprite));
        Identifier finalFrontTexture = frontTexture;
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolidZOffsetForward(finalFrontTexture), (pose, buffer) -> renderFront(pose, buffer, state.lightCoordsPerBlock, state.width, state.height));
        poseStack.popPose();
        super.submit(state, poseStack, collector, cameraState);
    }

    private static void renderFront(PoseStack.Pose pose, VertexConsumer buffer, int[] lights, int width, int height) {
        float offsetX = -width / 2.0F;
        float offsetY = -height / 2.0F;
        for (int segmentX = 0; segmentX < width; segmentX++) {
            for (int segmentY = 0; segmentY < height; segmentY++) {
                float x0 = offsetX + segmentX + 1;
                float x1 = offsetX + segmentX;
                float y0 = offsetY + segmentY + 1;
                float y1 = offsetY + segmentY;
                int light = lights[segmentX + segmentY * width];
                float u0 = (float)(width - segmentX) / width;
                float u1 = (float)(width - segmentX - 1) / width;
                float v0 = (float)(height - segmentY) / height;
                float v1 = (float)(height - segmentY - 1) / height;
                vertex(pose, buffer, x0, y1, u1, v0, -0.03125F, 0, 0, -1, light);
                vertex(pose, buffer, x1, y1, u0, v0, -0.03125F, 0, 0, -1, light);
                vertex(pose, buffer, x1, y0, u0, v1, -0.03125F, 0, 0, -1, light);
                vertex(pose, buffer, x0, y0, u1, v1, -0.03125F, 0, 0, -1, light);
            }
        }
    }

    private static void renderBackEdgesAndFrame(PoseStack.Pose pose, VertexConsumer buffer, int[] lights, int width, int height, TextureAtlasSprite back) {
        float offsetX = -width / 2.0F;
        float offsetY = -height / 2.0F;
        float backU0 = back.getU0();
        float backU1 = back.getU1();
        float backV0 = back.getV0();
        float backV1 = back.getV1();
        float topBottomU0 = back.getU0();
        float topBottomU1 = back.getU1();
        float topBottomV0 = back.getV0();
        float topBottomV1 = back.getV(0.0625F);
        float leftRightU0 = back.getU0();
        float leftRightU1 = back.getU(0.0625F);
        float leftRightV0 = back.getV0();
        float leftRightV1 = back.getV1();
        for (int segmentX = 0; segmentX < width; segmentX++) {
            for (int segmentY = 0; segmentY < height; segmentY++) {
                float x0 = offsetX + segmentX + 1;
                float x1 = offsetX + segmentX;
                float y0 = offsetY + segmentY + 1;
                float y1 = offsetY + segmentY;
                int light = lights[segmentX + segmentY * width];
                vertex(pose, buffer, x0, y0, backU1, backV0, 0.03125F, 0, 0, 1, light);
                vertex(pose, buffer, x1, y0, backU0, backV0, 0.03125F, 0, 0, 1, light);
                vertex(pose, buffer, x1, y1, backU0, backV1, 0.03125F, 0, 0, 1, light);
                vertex(pose, buffer, x0, y1, backU1, backV1, 0.03125F, 0, 0, 1, light);
                if (segmentY == height - 1) {
                    vertex(pose, buffer, x0, y0, topBottomU0, topBottomV0, -0.03125F, 0, 1, 0, light);
                    vertex(pose, buffer, x1, y0, topBottomU1, topBottomV0, -0.03125F, 0, 1, 0, light);
                    vertex(pose, buffer, x1, y0, topBottomU1, topBottomV1, 0.03125F, 0, 1, 0, light);
                    vertex(pose, buffer, x0, y0, topBottomU0, topBottomV1, 0.03125F, 0, 1, 0, light);
                    frontFrameQuad(pose, buffer, x1, x0, y0 - FRAME, y0, light, back);
                }
                if (segmentY == 0) {
                    vertex(pose, buffer, x0, y1, topBottomU0, topBottomV0, 0.03125F, 0, -1, 0, light);
                    vertex(pose, buffer, x1, y1, topBottomU1, topBottomV0, 0.03125F, 0, -1, 0, light);
                    vertex(pose, buffer, x1, y1, topBottomU1, topBottomV1, -0.03125F, 0, -1, 0, light);
                    vertex(pose, buffer, x0, y1, topBottomU0, topBottomV1, -0.03125F, 0, -1, 0, light);
                    frontFrameQuad(pose, buffer, x1, x0, y1, y1 + FRAME, light, back);
                }
                if (segmentX == width - 1) {
                    vertex(pose, buffer, x0, y0, leftRightU1, leftRightV0, 0.03125F, -1, 0, 0, light);
                    vertex(pose, buffer, x0, y1, leftRightU1, leftRightV1, 0.03125F, -1, 0, 0, light);
                    vertex(pose, buffer, x0, y1, leftRightU0, leftRightV1, -0.03125F, -1, 0, 0, light);
                    vertex(pose, buffer, x0, y0, leftRightU0, leftRightV0, -0.03125F, -1, 0, 0, light);
                    frontFrameQuad(pose, buffer, x0 - FRAME, x0, y1, y0, light, back);
                }
                if (segmentX == 0) {
                    vertex(pose, buffer, x1, y0, leftRightU1, leftRightV0, -0.03125F, 1, 0, 0, light);
                    vertex(pose, buffer, x1, y1, leftRightU1, leftRightV1, -0.03125F, 1, 0, 0, light);
                    vertex(pose, buffer, x1, y1, leftRightU0, leftRightV1, 0.03125F, 1, 0, 0, light);
                    vertex(pose, buffer, x1, y0, leftRightU0, leftRightV0, 0.03125F, 1, 0, 0, light);
                    frontFrameQuad(pose, buffer, x1, x1 + FRAME, y1, y0, light, back);
                }
            }
        }
    }

    private static void frontFrameQuad(PoseStack.Pose pose, VertexConsumer buffer, float x1, float x0, float y1, float y0, int light, TextureAtlasSprite texture) {
        float u0 = texture.getU0();
        float u1 = texture.getU1();
        float v0 = texture.getV0();
        float v1 = texture.getV1();
        vertex(pose, buffer, x0, y1, u1, v0, FRONT_FRAME_Z, 0, 0, -1, light);
        vertex(pose, buffer, x1, y1, u0, v0, FRONT_FRAME_Z, 0, 0, -1, light);
        vertex(pose, buffer, x1, y0, u0, v1, FRONT_FRAME_Z, 0, 0, -1, light);
        vertex(pose, buffer, x0, y0, u1, v1, FRONT_FRAME_Z, 0, 0, -1, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float u, float v, float z, int nx, int ny, int nz, int light) {
        buffer.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    public static final class State extends EntityRenderState {
        private UUID imageId = new UUID(0L, 0L);
        private int width = 1;
        private int height = 1;
        private Direction direction = Direction.SOUTH;
        private int[] lightCoordsPerBlock = new int[0];
    }
}
