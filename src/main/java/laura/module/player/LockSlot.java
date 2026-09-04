package laura.module.player;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.DropItemEvent;
import laura.setting.BooleanSetting;
import laura.setting.MultiModeSetting;
import laura.util.ChatUtil;
import laura.util.ServerUtil;

@ModuleRegister(name = "Lock Slot", description = "Запрещает выбрасывать предметы из выбранных слотов", category = Category.Player)
public class LockSlot extends Module {
    private final MultiModeSetting b = new MultiModeSetting("Заблокированные слоты", new BooleanSetting("1", false),
            new BooleanSetting("2", false), new BooleanSetting("3", false), new BooleanSetting("4", false),
            new BooleanSetting("5", false), new BooleanSetting("6", false), new BooleanSetting("7", false),
            new BooleanSetting("8", false), new BooleanSetting("9", false));
    private final BooleanSetting c = new BooleanSetting("Блокировать только в PVP", true);

    public LockSlot() {
        a(this.c, this.b);
    }

    @EventTarget
    public void a(DropItemEvent event) {
        if ((!this.c.c().booleanValue() || ServerUtil.e()) && this.b.a(event.b()).c().booleanValue()) {
            ChatUtil.sendMessage("Попытка выброса из слота \"&c" + (event.b() + 1) + "&7\" была заблокирована");
            event.a(true);
        }
    }
}
