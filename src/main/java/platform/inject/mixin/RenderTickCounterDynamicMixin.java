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
 */
@Environment(EnvType.CLIENT)
@Mixin(RenderTickCounter.Dynamic.class)
public class RenderTickCounterDynamicMixin {
    @Shadow
    private float dynamicDeltaTicks;

    @Shadow
    private float tickProgress;

    @Shadow
    private long lastTimeMillis;

    @Shadow
    @Final
    private FloatUnaryOperator targetMillisPerTick;

    @Inject(method = "beginRenderTick(J)I", at = @At("HEAD"), cancellable = true)
    private void laura$timer(long timeMillis, CallbackInfoReturnable<Integer> cir) {
        if (Timer.speed != 1.0f) {
            float tickTime = ((RenderTickCounter) (Object) this).tickTime;
            this.dynamicDeltaTicks = (((float) (timeMillis - this.lastTimeMillis)) / this.targetMillisPerTick.apply(tickTime)) * Timer.speed;
            this.lastTimeMillis = timeMillis;
            this.tickProgress += this.dynamicDeltaTicks;
            int ticks = (int) this.tickProgress;
            this.tickProgress -= ticks;
            cir.setReturnValue(ticks);
        }
    }
}
