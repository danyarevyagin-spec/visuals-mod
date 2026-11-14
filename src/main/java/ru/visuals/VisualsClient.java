package ru.visuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;
import ru.visuals.mixin.SimpleOptionAccessor;

public class VisualsClient implements ClientModInitializer {
    private static final String CATEGORY = "category.visuals";
    private static final double ZOOM_FOV = 30.0;
    private static final double FULLBRIGHT_GAMMA = 16.0;

    private KeyBinding fullbrightKey, zoomKey, hudKey;

    private boolean fullbright = false;
    private boolean hud = true;
    private boolean zooming = false;
    private double savedGamma = 1.0;
    private int savedFov = 70;

    @Override
    public void onInitializeClient() {
        fullbrightKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.visuals.fullbright", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.visuals.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY));
        hudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.visuals.hud", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudRenderCallback.EVENT.register(this::renderHud);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> restoreAll(c));
    }

    private void tick(MinecraftClient mc) {
        if (mc.options == null) return;

        while (fullbrightKey.wasPressed()) {
            fullbright = !fullbright;
            if (fullbright) {
                savedGamma = mc.options.getGamma().getValue();
                setGamma(mc, FULLBRIGHT_GAMMA);
            } else {
                setGamma(mc, savedGamma);
            }
        }
        while (hudKey.wasPressed()) hud = !hud;

        boolean wantZoom = zoomKey.isPressed() && mc.currentScreen == null;
        if (wantZoom && !zooming) {
            savedFov = mc.options.getFov().getValue();
            zooming = true;
        }
        if (zooming) {
            if (wantZoom) {
                mc.options.getFov().setValue((int) ZOOM_FOV);
            } else {
                mc.options.getFov().setValue(savedFov);
                zooming = false;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void setGamma(MinecraftClient mc, double value) {
        SimpleOption<Double> gamma = mc.options.getGamma();
        ((SimpleOptionAccessor<Double>) (Object) gamma).visuals$setValue(value);
    }

    private void restoreAll(MinecraftClient mc) {
        if (mc.options == null) return;
        if (fullbright) { setGamma(mc, savedGamma); fullbright = false; }
        if (zooming) { mc.options.getFov().setValue(savedFov); zooming = false; }
    }

    private void renderHud(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!hud || mc.player == null || mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) return;

        Direction facing = mc.player.getHorizontalFacing();
        String[] lines = {
                "FPS: " + mc.getCurrentFps(),
                String.format("XYZ: %.1f / %.1f / %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()),
                "Facing: " + facing.asString().toUpperCase(),
                "Fullbright: " + (fullbright ? "ON" : "OFF")
        };

        int x = 4, y = 4;
        for (String line : lines) {
            int w = mc.textRenderer.getWidth(line);
            ctx.fill(x - 2, y - 1, x + w + 2, y + 9, 0x90000000);
            ctx.drawText(mc.textRenderer, line, x, y, 0xFFFFFFFF, false);
            y += 12;
        }
    }
}
