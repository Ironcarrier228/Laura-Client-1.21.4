package platform.inject.mixin;

import laura.core.Laura;
import laura.util.ChatUtil;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin для модуля PvPSafe.
 * <p>
 * Блокирует кнопку "Disconnect" в игровом меню пока PvPSafe включён
 * и игрок находится в бою.
 * <p>
 * Зарегистрирован в laura.mixins.json в секции client.
 */
@Mixin(GameMenuScreen.class)
public abstract class PvPSafeMixin extends Screen {

    protected PvPSafeMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void pvpSafe$protectDisconnect(CallbackInfo ci) {
        // Находим модуль PvPSafe через ModuleProcessor
        var proc = Laura.getInstance().getModuleProcessor();
        if (proc == null) return;

        laura.module.misc.PvPSafe pvpSafe = null;
        for (laura.core.Module m : proc.t().e()) {
            if (m instanceof laura.module.misc.PvPSafe ps) {
                pvpSafe = ps;
                break;
            }
        }

        // Если PvPSafe выключен или не найден — не делаем ничего
        if (pvpSafe == null || !pvpSafe.m()) return;

        // Если не в бою — пропускаем
        if (!pvpSafe.isInCombat()) return;

        // Блокируем кнопки "Disconnect" и "Back to Title Screen"
        boolean blocked = false;
        for (var element : children()) {
            if (!(element instanceof ButtonWidget button)) continue;

            String messageKey = button.getMessage().getString();
            if (messageKey == null) continue;

            // Кнопка "Disconnect" или "Back to Title Screen"
            if (messageKey.toLowerCase().contains("disconnect") ||
                messageKey.toLowerCase().contains("title") ||
                messageKey.contains("Выйти") ||
                messageKey.contains("Отключиться")) {
                button.active = false;
                blocked = true;
            }
        }

        if (blocked) {
            ChatUtil.sendMessage("PvPSafe", "Выход заблокирован — вы в бою!");
        }
    }
}
