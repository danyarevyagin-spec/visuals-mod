package ru.visuals;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/**
 * Значок «KK» над ником: рисуется через WorldRenderEvents.AFTER_ENTITIES.
 * Работает только у игроков с КК Visuals включённым значком.
 */
public final class KkBadge {
    private KkBadge() { }

    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            if (!Features.BADGE.on()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.player == null) return;
            MatrixStack ms = ctx.matrixStack();
            if (ms == null) return;
            VertexConsumerProvider.Immediate vc = ctx.consumers();
            if (vc == null) return;
            String[] styles = {"KK", "✦KK✦", "《KK》", "✮KK✮"};
            String label = styles[Features.BD_STYLE.index];
            float time = System.nanoTime() / 1_000_000_000f;
            int col = Features.BD_RAINBOW.value ? (0xFF000000 | Theme.rainbow(time, 0f)) : (0xFF000000 | Theme.pick(Features.BD_COLOR));
            for (Entity e : mc.world.getEntitiesByClass(PlayerEntity.class,
                    mc.player.getBoundingBox().expand(32), p -> p != mc.player || Features.BD_SELF.value)) {
                float y = (float)(e.getHeight() + 0.6f + Features.BD_Y.get());
                ms.push();
                ms.translate(e.getX() - mc.getEntityRenderDispatcher().camera.getPos().x,
                        e.getY() - mc.getEntityRenderDispatcher().camera.getPos().y + y,
                        e.getZ() - mc.getEntityRenderDispatcher().camera.getPos().z);
                ms.multiply(mc.getEntityRenderDispatcher().getRotation());
                float sc = Features.BD_SCALE.getf() * -0.025f;
                ms.scale(sc, sc, sc);
                TextRenderer tr = mc.textRenderer;
                int w = tr.getWidth(label);
                tr.drawWithOutline(Text.literal(label).asOrderedText(), -w / 2f, 0, col, 0xFF000000,
                        ms.peek().getPositionMatrix(), vc, 15728880);
                ms.pop();
            }
        });
    }
}
