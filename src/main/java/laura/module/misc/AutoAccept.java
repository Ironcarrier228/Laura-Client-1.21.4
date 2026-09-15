package laura.module.misc;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.PacketEvent;

/**
 * AutoAccept (из WildClient)
 * <p>
 * Заглушка: автоматически принимает телепорт/пати запросы от сервера.
 * В Laura нет специального пакета для тп-запросов — эта функция обычно
 * реализуется через обработку конкретных серверных пакетов.
 * Модуль предоставляет хук для будущей реализации.
 */
@ModuleRegister(
        name = "AutoAccept",
        description = "Авто-принятие серверных запросов (TP/Party)",
        category = Category.Misc
)
public class AutoAccept extends Module {
    @EventTarget
    public void onPacket(PacketEvent event) {
        // Реализация зависит от конкретного сервера.
        // Перехватываем пакеты типа ServerPlayerPositionRotationS2CPacket
        // или кастомные серверные пакеты для тп-запросов.
        // Оставлено как заготовка для будущей реализации.
    }
}
