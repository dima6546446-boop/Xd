package dev.duckvisual.mixin;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.impl.visual.AspectRatio;
import dev.duckvisual.module.impl.visual.NoRender;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void duck$noHurtCam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (NoRender.active(nr -> nr.hurtCam)) ci.cancel();
    }

    @Redirect(method = "getBasicProjectionMatrix",
            at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;", remap = false))
    private Matrix4f duck$aspectRatio(Matrix4f matrix, float fovy, float aspect, float near, float far) {
        AspectRatio ar = DuckVisual.modules().get(AspectRatio.class);
        return matrix.perspective(fovy, ar.isEnabled() ? ar.ratio.getFloat() : aspect, near, far);
    }
}
