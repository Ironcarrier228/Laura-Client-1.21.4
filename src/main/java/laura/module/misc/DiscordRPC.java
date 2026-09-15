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

@ModuleRegister(
        name = "DiscordRPC",
        description = "Статус Laura Client в Discord. Введите свой Application ID для отображения кнопок (Скачать, GitHub).",
        category = Category.Misc
)
public class DiscordRPC extends Module {
    private final StringSetting clientId = new StringSetting(
            "Client ID",
            Long.toString(DiscordProcessor.getClientId()),
            true
    );

    public DiscordRPC() {
        a(
                clientId,
                new ButtonSetting("Применить ID", this::apply),
                new ButtonSetting("Сбросить ID", this::reset)
        );
    }

    private Long validatedId() {
        String value = clientId.h().trim();
        try {
            if (!value.matches("[0-9]{1,19}")) throw new NumberFormatException();
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) {
            notify("DiscordRPC: введите корректный Client ID (число 1-19 цифр)", false);
            return null;
        }
    }

    private void apply() {
        Long id = validatedId();
        if (id == null) return;

        // Если модуль был выключен — сначала включаем (это запустит DiscordRPC с этим ID)
        if (!m()) {
            // Сначала принудительно ставим enabled=true через родительский a(boolean)
            // Чтобы не было бесконечного цикла (b() снова вызовет start), делаем это так:
            a(true); // toggle on
        } else {
            // Модуль уже включён — перезапускаем с новым ID
            Laura.getInstance().getModuleProcessor().g().unSetup();
            Laura.getInstance().getModuleProcessor().g().start(id);
        }
        notify("DiscordRPC: подключение с ID " + id + "...", true);
    }

    private void reset() {
        long defaultId = DiscordProcessor.getClientId();
        // Устанавливаем дефолтное значение через setter (Setting.a(Value))
        clientId.a(Long.toString(defaultId));
        // Перезапускаем с дефолтным ID
        if (m()) {
            Laura.getInstance().getModuleProcessor().g().unSetup();
            Laura.getInstance().getModuleProcessor().g().start(defaultId);
        }
        notify("DiscordRPC: ID сброшен на дефолтный (" + defaultId + ")", true);
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
        notify("DiscordRPC: статус активен (ID " + id + ")", true);
    }

    @Override
    public void c() {
        Laura.getInstance().getModuleProcessor().g().unSetup();
        super.c();
        notify("DiscordRPC: статус выключен", true);
    }

    private void notify(String text, boolean success) {
        int color = success
                ? ColorUtil.convertToARGB(230, 200, 100, 255) // жёлтый для info
                : ColorUtil.convertToARGB(230, 100, 100, 255); // красный для ошибки
        Laura.getInstance().getModuleProcessor().m().a(
                new Notification("Q", color, text, 3000)
        );
    }
}
