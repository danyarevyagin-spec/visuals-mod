package ru.visuals.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.visuals.VisualsClient;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "jump", at = @At("HEAD"))
    private void visuals$onJump(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && self == mc.player && self.getWorld().isClient()) {
            VisualsClient.onPlayerJump(self.getPos());
        }
    }
}
