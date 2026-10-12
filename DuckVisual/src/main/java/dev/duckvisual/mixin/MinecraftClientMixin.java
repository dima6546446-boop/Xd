package dev.duckvisual.mixin;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.impl.visual.GlowESP;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void duck$glow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (DuckVisual.modules() != null && DuckVisual.modules().get(GlowESP.class).shouldGlow(entity)) cir.setReturnValue(true);
    }
}
