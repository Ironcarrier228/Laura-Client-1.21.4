package laura.module.player;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.TickEvent;
import net.minecraft.item.Items;

@ModuleRegister(name = "Fast EXP", description = "Позволяет очень быстро бросать опыт", category = Category.Player)
public class FastEXP extends Module {
    @EventTarget
    public void a(TickEvent event) {
        if (mc.player.getMainHandStack().getItem() == Items.EXPERIENCE_BOTTLE) {
            ((platform.inject.accessors.MinecraftClientAccessor) mc).setItemUseCooldown(0);
        }
    }
}
