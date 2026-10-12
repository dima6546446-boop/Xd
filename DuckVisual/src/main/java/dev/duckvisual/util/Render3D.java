package dev.duckvisual.util;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.duckvisual.event.WorldRender;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class Render3D {
    private Render3D() {}

    /** Prepares GL state for our world-space drawing. Must be paired with {@link #end()}. */
    public static void begin(Matrix4f projection) {
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().identity();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projection, ProjectionType.PERSPECTIVE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
    }

    public static void end() {
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.getModelViewStack().popMatrix();
    }

    public static void additive(boolean on) {
        if (on) RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        else RenderSystem.defaultBlendFunc();
    }

    public static void depth(boolean on) {
        if (on) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
    }

    public static BufferBuilder buffer(VertexFormat.DrawMode mode) {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        return Tessellator.getInstance().begin(mode, VertexFormats.POSITION_COLOR);
    }

    public static void draw(BufferBuilder b) {
        var built = b.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
    }

    /** Horizontal ring (flat on the XZ plane) centered at a camera-relative position. */
    public static void ring(WorldRender r, Vec3d c, float inner, float outer, int innerColor, int outerColor) {
        Matrix4f m = r.matrices().peek().getPositionMatrix();
        BufferBuilder b = buffer(VertexFormat.DrawMode.TRIANGLE_STRIP);
        int seg = 72;
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg;
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            b.vertex(m, (float) c.x + cos * outer, (float) c.y, (float) c.z + sin * outer).color(outerColor);
            b.vertex(m, (float) c.x + cos * inner, (float) c.y, (float) c.z + sin * inner).color(innerColor);
        }
        draw(b);
    }

    /** Camera-facing soft glowing disc. */
    public static void glow(WorldRender r, Vec3d c, float size, int color) {
        MatrixStack ms = r.matrices();
        ms.push();
        ms.translate(c.x, c.y, c.z);
        ms.multiply(r.camera().getRotation());
        Matrix4f m = ms.peek().getPositionMatrix();
        BufferBuilder b = buffer(VertexFormat.DrawMode.TRIANGLE_FAN);
        b.vertex(m, 0, 0, 0).color(color);
        int edge = ColorUtil.withAlpha(color, 0);
        for (int i = 0; i <= 20; i++) {
            double a = Math.PI * 2 * i / 20;
            b.vertex(m, (float) Math.cos(a) * size, (float) Math.sin(a) * size, 0).color(edge);
        }
        draw(b);
        ms.pop();
    }

    /** Camera-facing glow with a solid core: two layered discs. */
    public static void orb(WorldRender r, Vec3d c, float size, int color) {
        glow(r, c, size, ColorUtil.fade(color, 0.55f));
        glow(r, c, size * 0.35f, color);
    }

    public static Vec3d interpolate(net.minecraft.entity.Entity e, float tickDelta) {
        return e.getLerpedPos(tickDelta);
    }
}
