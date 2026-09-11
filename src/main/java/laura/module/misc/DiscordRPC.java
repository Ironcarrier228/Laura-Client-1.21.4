package laura.module.misc;

import laura.core.Category;
import laura.core.Laura;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.discord.DiscordProcessor;
import laura.notification.Notification;
import laura.render.ColorUtil;
import laura.setting.ButtonSetting;
import laura.setting.StringSetting;

@ModuleRegister(name = "DiscordRPC", description = "Статус Laura Client в Discord. Введите Application ID и нажмите Применить ID.", category = Category.Misc)
public class DiscordRPC extends Module {
    private final StringSetting clientId = new StringSetting("Client ID", Long.toString(DiscordProcessor.getClientId()), true);

    public DiscordRPC() {
        a(clientId, new ButtonSetting("Применить ID", this::apply));
    }

    private Long validatedId() {
        String value = clientId.c().trim();
        try {
            if (!value.matches("[0-9]{1,19}")) throw new NumberFormatException();
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) {
            Laura.getInstance().getModuleProcessor().m().a(new Notification("Q",
                    ColorUtil.convertToARGB(230, 100, 100, 255), "DiscordRPC: введите корректный Client ID", 3000));
            return null;
        }
    }

    private void apply() {
        Long id = validatedId();
        if (id != null && m()) {
            Laura.getInstance().getModuleProcessor().g().start(id);
        }
    }

    @Override
    public void b() {
        Long id = validatedId();
        if (id == null) {
            a(false);
            return;
        }
        super.b();
        Laura.getInstance().getModuleProcessor().g().start(id);
    }

    @Override
    public void c() {
        Laura.getInstance().getModuleProcessor().g().unSetup();
        super.c();
    }
}
