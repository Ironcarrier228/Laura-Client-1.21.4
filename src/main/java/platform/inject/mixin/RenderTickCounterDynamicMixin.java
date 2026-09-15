package platform.inject.mixin;

import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import laura.module.movement.Timer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Портировано из Wexside (RenderTickCounterDynamicMixin).
 * Применяет множитель скорости модуля Timer к дельте рендер-тиков.
 * В 1.21.4 структура счётчика отличается от 1.21.8: поля называются
 * lastFrameDuration / tickDelta / prevTimeMillis, а beginRenderTick(long) приватный.
 */
@Environment(EnvType.CLIENT)
@Mixin(RenderTickCounter.Dynamic.class)
public class RenderTickCounterDynamicMixin {
    @Shadow
    private float lastFrameDuration;

    @Shadow
    private float tickDelta;

    @Shadow
    private long prevTimeMillis;

    @Shadow
    @Final
    private float tickTime;

    @Shadow
    @Final
    private FloatUnaryOperator targetMillisPerTick;

    @Inject(method = "beginRenderTick(J)I", at = @At("HEAD"), cancellable = true)
    private void laura$timer(long timeMillis, CallbackInfoReturnable<Integer> cir) {
        if (Timer.speed != 1.0f) {
            this.lastFrameDuration = ((((float) (timeMillis - this.prevTimeMillis)) / this.targetMillisPerTick.apply(this.tickTime)) * Timer.speed);
            this.prevTimeMillis = timeMillis;
            this.tickDelta += this.lastFrameDuration;
            int ticks = (int) this.tickDelta;
            this.tickDelta -= ticks;
            cir.setReturnValue(ticks);
        }
    }
}
