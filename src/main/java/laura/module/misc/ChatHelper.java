package laura.module.misc;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.TickEvent;
import laura.setting.BooleanSetting;
import laura.util.ServerUtil;

@ModuleRegister(name = "Chat Helper", description = "Расширяет возможности чата и его настройки", category = Category.Misc)
public class ChatHelper extends Module {
    private final BooleanSetting b = new BooleanSetting("Ширина под сообщение", false);
    private final BooleanSetting c = new BooleanSetting("Автоматическое /event delay", false);
    private int d = -1;

    public ChatHelper() {
        a(this.b, this.c);
    }

    public BooleanSetting q() {
        return this.b;
    }

    @EventTarget
    public void a(TickEvent event) {
        int iB;
        if (this.c.c().booleanValue()) {
            if (ServerUtil.a.a$()) {
                iB = ServerUtil.a.d();
            } else {
                iB = ServerUtil.d.a() ? ServerUtil.d.b() : -1;
            }
            int currentAnarchy = iB;
            if (currentAnarchy == -1) {
                this.d = 0;
                return;
            }
            if (this.d == -1) {
                this.d = currentAnarchy;
            } else if (currentAnarchy != this.d && mc.player.age >= 5) {
                mc.player.networkHandler.sendChatCommand("event delay");
                this.d = currentAnarchy;
            }
        }
    }
}
