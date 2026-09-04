package laura.module.player;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.InputEvent;
import laura.event.PacketEvent;
import laura.event.TickEvent;
import laura.setting.BooleanSetting;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;

@ModuleRegister(name = "Wind Hop", description = "Автоматически прыгает после использования заряда ветра", category = Category.Player)
public class WindHop extends Module {
    private final BooleanSetting b = new BooleanSetting("Поворачивать голову вниз", true);
    private int c = -1;

    public WindHop() {
        a(this.b);
    }

    public BooleanSetting q() {
        return this.b;
    }

    @EventTarget
    public void a(PacketEvent event) {
        if (event.isSend()) {
            if (event.getPacket() instanceof PlayerInteractItemC2SPacket packet) {
                if (mc.player.getStackInHand(packet.getHand()).isOf(Items.WIND_CHARGE)) {
                    this.c = 2;
                }
            }
        }
    }

    @EventTarget
    public void a(TickEvent event) {
        if (this.c > 0) {
            this.c--;
        }
    }

    @EventTarget
    public void a(InputEvent event) {
        if (this.c == 0) {
            event.setJump(true);
            this.c = -1;
        }
    }
}
