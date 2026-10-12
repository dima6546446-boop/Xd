package dev.duckvisual.mixin;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.impl.visual.AspectRatio;
import dev.duckvisual.module.impl.visual.NoRender;
import dev.duckvisual.module.impl.visual.Zoom;
import dev.duckvisual.util.Animation;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    /** One frame-time update per frame, shared by every animation (HUD and GUI). */
    @Inject(method = "render", at = @At("HEAD"))
    private void duck$frame(RenderTickCounter counter, boolean tick, CallbackInfo ci) {
        Animation.updateFrame();
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void duck$noHurtCam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (NoRender.active(nr -> nr.hurtCam)) ci.cancel();
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void duck$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        Zoom zoom = DuckVisual.modules().get(Zoom.class);
        // only the world FOV; the hand keeps its normal FOV like OptiFine zoom
        if (zoom.isEnabled() && changingFov) cir.setReturnValue(zoom.apply(cir.getReturnValue()));
    }

    @Redirect(method = "getBasicProjectionMatrix",
            at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;", remap = false))
    private Matrix4f duck$aspectRatio(Matrix4f matrix, float fovy, float aspect, float near, float far) {
        AspectRatio ar = DuckVisual.modules().get(AspectRatio.class);
        return matrix.perspective(fovy, ar.isEnabled() ? ar.ratio.getFloat() : aspect, near, far);
    }
}
