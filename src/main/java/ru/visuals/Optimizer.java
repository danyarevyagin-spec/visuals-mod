package ru.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.option.SimpleOption;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Оптимизация: временно меняет настройки видео и возвращает их при выключении. */
public final class Optimizer {
    private Optimizer() { }

    private static final Map<String, Object> SAVED = new HashMap<>();
    private static final Map<String, SimpleOption<?>> OPTS = new HashMap<>();
    private static int lastSig = Integer.MIN_VALUE;

    private static <T> void put(String key, boolean enabled, SimpleOption<T> opt, T target) {
        if (enabled) {
            if (!SAVED.containsKey(key)) {
                SAVED.put(key, opt.getValue());
                OPTS.put(key, opt);
            }
            if (!Objects.equals(opt.getValue(), target)) opt.setValue(target);
        } else {
            restoreOne(key);
        }
    }

    @SuppressWarnings("unchecked")
    private static void restoreOne(String key) {
        if (!SAVED.containsKey(key)) return;
        SimpleOption<Object> o = (SimpleOption<Object>) OPTS.get(key);
        Object v = SAVED.remove(key);
        OPTS.remove(key);
        if (o != null && !Objects.equals(o.getValue(), v)) o.setValue(v);
    }

    public static void restoreAll() {
        for (String k : new java.util.ArrayList<>(SAVED.keySet())) restoreOne(k);
        lastSig = Integer.MIN_VALUE;
    }

    public static void tick(MinecraftClient mc) {
        if (mc.options == null) return;
        if (!Features.OPT.on()) {
            if (!SAVED.isEmpty()) restoreAll();
            return;
        }
        int sig = java.util.Objects.hash(
                Features.OPT_VIEW_ON.value, Features.OPT_VIEW.geti(),
                Features.OPT_SIM_ON.value, Features.OPT_SIM.geti(),
                Features.OPT_ENT_ON.value, Features.OPT_ENT.geti(),
                Features.OPT_CLOUDS.value, Features.OPT_PART.index, Features.OPT_SHADOW.value,
                Features.OPT_BLEND.value, Features.OPT_FAST.value, Features.OPT_VSYNC.value,
                Features.OPT_FPS_ON.value, Features.OPT_FPS.geti());
        if (sig == lastSig) return;
        lastSig = sig;

        put("view", Features.OPT_VIEW_ON.value, mc.options.getViewDistance(), Features.OPT_VIEW.geti());
        put("sim", Features.OPT_SIM_ON.value, mc.options.getSimulationDistance(), Features.OPT_SIM.geti());
        put("ent", Features.OPT_ENT_ON.value, mc.options.getEntityDistanceScaling(), Features.OPT_ENT.get() / 100.0);
        put("clouds", Features.OPT_CLOUDS.value, mc.options.getCloudRenderMode(), CloudRenderMode.OFF);
        put("part", Features.OPT_PART.index > 0, mc.options.getParticles(),
                Features.OPT_PART.index == 2 ? ParticlesMode.MINIMAL : ParticlesMode.DECREASED);
        put("shadow", Features.OPT_SHADOW.value, mc.options.getEntityShadows(), Boolean.FALSE);
        put("blend", Features.OPT_BLEND.value, mc.options.getBiomeBlendRadius(), 1);
        put("fast", Features.OPT_FAST.value, mc.options.getGraphicsMode(), GraphicsMode.FAST);
        put("vsync", Features.OPT_VSYNC.value, mc.options.getEnableVsync(), Boolean.FALSE);
        put("fps", Features.OPT_FPS_ON.value, mc.options.getMaxFps(), Features.OPT_FPS.geti());
    }

    /** Во сколько раз уменьшать количество частиц мода при низком FPS. */
    public static float particleScale(MinecraftClient mc) {
        if (!Features.OPT.on() || !Features.OPT_ADAPT.value) return 1f;
        float fps = mc.getCurrentFps();
        float target = Features.OPT_ADAPT_FPS.getf();
        if (fps <= 0f) return 1f;
        return Math.max(0.25f, Math.min(1f, fps / target));
    }
}
