package laura.module.player;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.TickEvent;
import laura.setting.SliderSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

import java.util.function.Predicate;

/**
 * Портировано из Wexside (модуль AutoInvisible).
 * Автоматически выпивает зелье невидимости, когда эффект заканчивается,
 * и возвращает предыдущий слот на место.
 */
@ModuleRegister(name = "AutoInvisible", description = "Автоматически пьёт зелье невидимости", category = Category.Player)
public class AutoInvisible extends Module {
    private final SliderSetting threshold = new SliderSetting("Порог до зелья (сек)", 5.0f, 1.0f, 60.0f, 1.0f);

    private boolean drinking;
    private long drinkStart;
    private int previousSlot = -1;

    public AutoInvisible() {
        a(this.threshold);
    }

    @Override
    public void c() {
        super.c();
        release();
    }

    @EventTarget
    public void a(TickEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }
        if (mc.currentScreen != null) {
            if (this.drinking) {
                release();
            }
            return;
        }
        if (this.drinking) {
            if (mc.player.isUsingItem() && System.currentTimeMillis() - this.drinkStart < 1850L) {
                mc.options.useKey.setPressed(true);
            } else {
                release();
            }
            return;
        }
        if (!needsPotion()) {
            return;
        }
        int potionSlot = findSlot(this::isInvisibilityPotion);
        if (potionSlot == -1) {
            return;
        }
        int hotbarSlot = swapToHotbar(potionSlot);
        if (hotbarSlot == -1) {
            return;
        }
        this.previousSlot = mc.player.getInventory().selectedSlot;
        selectSlot(hotbarSlot);
        mc.options.useKey.setPressed(true);
        this.drinking = true;
        this.drinkStart = System.currentTimeMillis();
    }

    private void release() {
        mc.options.useKey.setPressed(false);
        if (this.previousSlot >= 0 && this.previousSlot < 9 && mc.player != null) {
            selectSlot(this.previousSlot);
        }
        this.drinking = false;
        this.previousSlot = -1;
    }

    private boolean needsPotion() {
        StatusEffectInstance effect = mc.player.getStatusEffect(StatusEffects.INVISIBILITY);
        return effect == null || effect.getDuration() <= this.threshold.c().intValue() * 20;
    }

    private int findSlot(Predicate<ItemStack> predicate) {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private int swapToHotbar(int slot) {
        if (slot >= 0 && slot < 9) {
            return slot;
        }
        int target = mc.player.getInventory().selectedSlot;
        for (int hotbar = 0; hotbar < 9; hotbar++) {
            if (mc.player.getInventory().getStack(hotbar).isEmpty()) {
                target = hotbar;
                break;
            }
        }
        int screenSlot = slot < 9 ? slot + 36 : slot;
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, screenSlot, target, SlotActionType.SWAP, mc.player);
        return target;
    }

    private void selectSlot(int slot) {
        mc.player.getInventory().selectedSlot = slot;
        if (mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private boolean isInvisibilityPotion(ItemStack stack) {
        if (!stack.isOf(Items.POTION)) {
            return false;
        }
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) {
            return false;
        }
        for (StatusEffectInstance effect : contents.getEffects()) {
            if (effect.getEffectType() == StatusEffects.INVISIBILITY) {
                return true;
            }
        }
        return false;
    }
}
