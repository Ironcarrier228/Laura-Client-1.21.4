package platform.inject.mixin;


import laura.core.EventManager;
import laura.core.Interface;
import laura.event.PushEvent;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({PlayerEntity.class})
public class PlayerEntityMixin {
    @Inject(method = {"isPushedByFluids"}, at = {@At("HEAD")}, cancellable = true)
    private void removePushFromFluids(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == Interface.mc.player) {
            PushEvent event = new PushEvent(PushEvent.type.FLUIDS);
            EventManager.a(event);
            if (event.a()) {
                cir.setReturnValue(false);
            }
        }
    }
}
