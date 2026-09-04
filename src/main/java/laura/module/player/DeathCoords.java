package laura.module.player;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.TickEvent;
import laura.util.ChatUtil;

@ModuleRegister(name = "Death Coords", description = "Выводит координаты последней смерти", category = Category.Player)
public class DeathCoords extends Module {
    @EventTarget
    public void a(TickEvent event) {
        if (mc.player.deathTime == 1) {
            ChatUtil.sendMessage(String.format("Вы погибли на координатах: &c[%d, %d, %d]", Integer.valueOf(mc.player.getBlockPos().getX()), Integer.valueOf(mc.player.getBlockPos().getY()), Integer.valueOf(mc.player.getBlockPos().getZ())));
        }
    }
}
