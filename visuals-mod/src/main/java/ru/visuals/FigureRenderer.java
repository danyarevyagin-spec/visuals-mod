package ru.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.awt.Color;

public class FigureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    public FigureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (!Features.FIGURE.on() || state.invisible) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (Features.FG_ONLY_SELF.value && mc.player != null && state.id != mc.player.getId()) {
            return;
        }

        int c1 = resolveColor(Features.FG_COLOR1.index, 0);
        int c2 = resolveColor(Features.FG_COLOR2.index, 140);
        int wingCol = resolveColor(Features.FG_WINGS_COL.index, 70);

        float time = (System.nanoTime() / 1_000_000_000f);

        // 1. Китайская шляпа (China Hat)
        if (Features.FG_HAT.value) {
            matrices.push();
            this.getContextModel().head.rotate(matrices);
            float yOff = state.equippedHeadStack.isEmpty() ? -0.46f : -0.54f;
            matrices.translate(0.0f, yOff, 0.0f);
            float rad = Features.FG_HAT_SIZE.getf();
            renderCone(matrices, rad, rad * 0.48f, c1, c2, 0.90f);
            matrices.pop();
        }

        // 2. Нимб (Halo)
        if (Features.FG_HALO.value) {
            matrices.push();
            this.getContextModel().head.rotate(matrices);
            float hover = MathHelper.sin(time * 3.2f) * 0.025f;
            float baseY = Features.FG_HAT.value ? -0.78f : -0.62f;
            matrices.translate(0.0f, baseY + hover, 0.0f);
            renderHalo(matrices, 0.28f, c1);
            matrices.pop();
        }

        // 3. Кошачьи ушки (Cat Ears)
        if (Features.FG_EARS.value) {
            matrices.push();
            this.getContextModel().head.rotate(matrices);
            matrices.translate(0.0f, -0.49f, 0.0f);
            for (int side : new int[]{-1, 1}) {
                matrices.push();
                matrices.translate(side * 0.16f, 0.0f, 0.0f);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 15f));
                renderCone(matrices, 0.085f, 0.16f, c1, c2, 0.95f);
                matrices.pop();
            }
            matrices.pop();
        }

        // 4. Корона (Crown)
        if (Features.FG_CROWN.value) {
            matrices.push();
            this.getContextModel().head.rotate(matrices);
            matrices.translate(0.0f, -0.50f, 0.0f);
            renderCrown(matrices, 0.24f, 0.12f, c1, c2);
            matrices.pop();
        }

        // 5. Крылья (Wings)
        if (Features.FG_WINGS.value) {
            matrices.push();
            this.getContextModel().body.rotate(matrices);
            matrices.translate(0.0f, 0.14f, 0.16f);
            float scale = Features.FG_WINGS_SIZE.getf();
            matrices.scale(scale, scale, scale);
            boolean fast = state.isGliding || limbDistance > 0.25f;
            renderWings(matrices, time, fast, wingCol, c2);
            matrices.pop();
        }
    }

    private static int resolveColor(int swatchIndex, int offsetDeg) {
        if (Features.FG_RAINBOW.value) {
            float hue = ((System.currentTimeMillis() + offsetDeg * 10L) % 3500L) / 3500.0f;
            return Color.HSBtoRGB(hue, 0.82f, 1.0f);
        }
        if (swatchIndex <= 0) return 0xFF000000 | Theme.accent();
        return 0xFF000000 | Theme.SWATCHES[Math.min(swatchIndex, Theme.SWATCHES.length) - 1];
    }

    private static void setup3D() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_COLOR);
    }

    private static void restore3D() {
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderCone(MatrixStack matrices, float radius, float height, int topCol, int rimCol, float alpha) {
        setup3D();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float tr = ((topCol >> 16) & 255) / 255f;
        float tg = ((topCol >> 8) & 255) / 255f;
        float tb = (topCol & 255) / 255f;

        float rr = ((rimCol >> 16) & 255) / 255f;
        float rg = ((rimCol >> 8) & 255) / 255f;
        float rb = (rimCol & 255) / 255f;

        buf.vertex(mat, 0f, -height, 0f).color(tr, tg, tb, alpha);
        int segs = 36;
        for (int i = 0; i <= segs; i++) {
            double a = (2.0 * Math.PI * i) / segs;
            buf.vertex(mat, (float) Math.cos(a) * radius, 0f, (float) Math.sin(a) * radius)
                    .color(rr, rg, rb, alpha * 0.9f);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());
        restore3D();
    }

    private static void renderHalo(MatrixStack matrices, float radius, int col) {
        setup3D();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float r = ((col >> 16) & 255) / 255f;
        float g = ((col >> 8) & 255) / 255f;
        float b = (col & 255) / 255f;

        float inner = radius - 0.045f;
        int segs = 36;
        for (int i = 0; i <= segs; i++) {
            double a = (2.0 * Math.PI * i) / segs;
            float cs = (float) Math.cos(a);
            float sn = (float) Math.sin(a);
            buf.vertex(mat, cs * inner, 0f, sn * inner).color(r, g, b, 0.95f);
            buf.vertex(mat, cs * radius, 0f, sn * radius).color(r, g, b, 0.95f);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());
        restore3D();
    }

    private static void renderCrown(MatrixStack matrices, float radius, float height, int baseCol, int tipCol) {
        setup3D();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float br = ((baseCol >> 16) & 255) / 255f, bg = ((baseCol >> 8) & 255) / 255f, bb = (baseCol & 255) / 255f;
        float tr = ((tipCol >> 16) & 255) / 255f, tg = ((tipCol >> 8) & 255) / 255f, tb = (tipCol & 255) / 255f;

        int points = 8;
        for (int i = 0; i < points; i++) {
            double a1 = (2.0 * Math.PI * i) / points;
            double a2 = (2.0 * Math.PI * (i + 1)) / points;
            double am = (a1 + a2) * 0.5;

            float x1 = (float) Math.cos(a1) * radius, z1 = (float) Math.sin(a1) * radius;
            float x2 = (float) Math.cos(a2) * radius, z2 = (float) Math.sin(a2) * radius;
            float xm = (float) Math.cos(am) * (radius * 1.06f), zm = (float) Math.sin(am) * (radius * 1.06f);

            buf.vertex(mat, x1, 0f, z1).color(br, bg, bb, 0.95f);
            buf.vertex(mat, x2, 0f, z2).color(br, bg, bb, 0.95f);
            buf.vertex(mat, xm, -height, zm).color(tr, tg, tb, 0.98f);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());
        restore3D();
    }

    private static void renderWings(MatrixStack matrices, float time, boolean fast, int c1, int c2) {
        setup3D();
        Tessellator tess = Tessellator.getInstance();
        float r1 = ((c1 >> 16) & 255) / 255f, g1 = ((c1 >> 8) & 255) / 255f, b1 = (c1 & 255) / 255f;
        float r2 = ((c2 >> 16) & 255) / 255f, g2 = ((c2 >> 8) & 255) / 255f, b2 = (c2 & 255) / 255f;

        float speed = fast ? 7.5f : 2.8f;
        float amp = fast ? 26f : 11f;
        float flap = 26f + MathHelper.sin(time * speed) * amp;

        for (int side : new int[]{-1, 1}) {
            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * flap));
            Matrix4f mat = matrices.peek().getPositionMatrix();

            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            // Верхнее крыло
            buf.vertex(mat, 0f, 0f, 0f).color(r1, g1, b1, 0.92f);
            buf.vertex(mat, side * 0.88f, -0.36f, 0.14f).color(r2, g2, b2, 0.82f);
            buf.vertex(mat, side * 0.66f, 0.24f, 0.08f).color(r1, g1, b1, 0.88f);

            // Нижнее крыло
            buf.vertex(mat, 0f, 0.05f, 0f).color(r1, g1, b1, 0.92f);
            buf.vertex(mat, side * 0.66f, 0.24f, 0.08f).color(r1, g1, b1, 0.88f);
            buf.vertex(mat, side * 0.36f, 0.66f, 0.04f).color(r2, g2, b2, 0.78f);

            BufferRenderer.drawWithGlobalProgram(buf.end());
            matrices.pop();
        }
        restore3D();
    }
}
