package laura.module.movement;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.InputEvent;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

@ModuleRegister(name = "No Crouch", description = "Убирает замедление от приседания на вашей стороне", category = Category.Movement)
public class NoCrouch extends Module {
    private boolean wasSneaking;

    @EventTarget
    public void a(InputEvent e) {
        ClientCommandC2SPacket.Mode mode;
        boolean sneaking = e.isSneak();
        if (sneaking) {
            mode = ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY;
        } else {
            mode = this.wasSneaking ? ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY : null;
        }
        if (mode != null) {
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, mode));
        }
        this.wasSneaking = sneaking;
        e.setSneak(false);
    }
}
