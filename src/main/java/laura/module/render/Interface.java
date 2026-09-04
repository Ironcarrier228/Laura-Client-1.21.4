package laura.module.render;

import laura.config.ThemeInfo;
import laura.config.ThemeType;
import laura.core.*;
import laura.core.Module;
import laura.event.BackendEvent;
import laura.event.DrawEvent;
import laura.event.PacketEvent;
import laura.setting.BooleanSetting;
import laura.setting.ColorSetting;
import laura.setting.ModeSetting;
import laura.setting.MultiModeSetting;
import laura.ui.widget.*;

import java.util.ArrayList;
import java.util.List;

@ModuleRegister(name = "Interface", description = "Отображает выбранные элементы интерфейса на экране", category = Category.Render)
public class Interface extends Module {
    private final ModeSetting themeMode = new ModeSetting("Тема оформления", "Тёмная", "Тёмная", "Светлая")
            .a(selected -> {
                Laura.getInstance().getModuleProcessor().o().a(this.themeMode.l("Светлая") ? ThemeType.LIGHT : ThemeType.DARK);
            });
    private final ColorSetting globalColor = new ColorSetting("Глобальный цвет интерфейса",
            Integer.valueOf(Laura.getInstance().getModuleProcessor().o().a(ThemeInfo.PRIMARY).toIntColor()));
    private final MultiModeSetting widgetToggles = new MultiModeSetting("Элементы интерфейса",
            new BooleanSetting("Клавиши", true), new BooleanSetting("Таргет-худ", true),
            new BooleanSetting("Задержки", true), new BooleanSetting("Инфо-панель", true),
            new BooleanSetting("Уведомления", true), new BooleanSetting("Зелья", true),
            new BooleanSetting("Предметы", true), new BooleanSetting("Броня", true), new BooleanSetting("Стафф", true),
            new BooleanSetting("Окружение", true));
    private final List<Widget> widgets = new ArrayList<>();

    public Interface() {
        ThemeType current = Laura.getInstance().getModuleProcessor().o().a();
        this.themeMode.a(current == ThemeType.LIGHT ? "Светлая" : "Тёмная");
        a(this.themeMode, this.globalColor, this.widgetToggles);
        this.widgets.add(new ArmorWidget());
        this.widgets.add(new HotkeysWidget());
        this.widgets.add(new CooldownsWidget());
        this.widgets.add(new TargetWidget());
        this.widgets.add(new WatermarkWidget());
        this.widgets.add(new PotionWidget());
        this.widgets.add(new ItemsWidget());
        this.widgets.add(new NotificationWidget());
        this.widgets.add(new StaffWidget());
        this.widgets.add(new EnvironmentWidget());
    }

    public List<Widget> q() {
        return this.widgets;
    }

    @EventTarget
    public void onDraw(DrawEvent event) {
        if (event.b()) {
            Laura.getInstance().getModuleProcessor().o().a(ThemeInfo.PRIMARY).fromIntColor(this.globalColor.c().intValue());
            for (Widget widget : this.widgets) {
                if (this.widgetToggles.a(widget.j().getName()).c().booleanValue()) {
                    widget.a(event);
                }
            }
        }
    }

    @EventTarget
    public void a(GlobalEvent event) {
        for (Widget widget : this.widgets) {
            if (this.widgetToggles.a(widget.j().getName()).c().booleanValue()) {
                widget.a(event);
            }
        }
    }

    @EventTarget
    public void a(PacketEvent event) {
        for (Widget widget : this.widgets) {
            if (this.widgetToggles.a(widget.j().getName()).c().booleanValue()) {
                widget.a(event);
            }
        }
    }

    @EventTarget
    public void a(BackendEvent event) {
        for (Widget widget : this.widgets) {
            if (this.widgetToggles.a(widget.j().getName()).c().booleanValue()) {
                widget.a(event);
            }
        }
    }
}
