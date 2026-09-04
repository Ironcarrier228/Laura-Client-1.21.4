package laura.module.movement;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.TickEvent;
import net.minecraft.entity.effect.StatusEffects;

@ModuleRegister(name = "Sprint", description = "Автоматически включает спринт при движении", category = Category.Movement)
public class Sprint extends Module {
    @EventTarget
    public void a(TickEvent event) {
        mc.player.setSprinting(mc.player.input.movementForward > 0.0f && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS) && (mc.player.getAbilities().invulnerable || mc.player.getHungerManager().getFoodLevel() > 6));
    }
}
