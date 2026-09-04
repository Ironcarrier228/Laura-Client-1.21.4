package laura.module.player;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.TickEvent;
import net.minecraft.client.gui.screen.DeathScreen;

@ModuleRegister(name = "Auto Respawn", description = "Автоматически возрождает персонажа после смерти", category = Category.Player)
public class AutoRespawn extends Module {
    @EventTarget
    public void a(TickEvent event) {
        if ((mc.currentScreen instanceof DeathScreen) && mc.player.deathTime >= 5) {
            mc.player.requestRespawn();
        }
    }
}
