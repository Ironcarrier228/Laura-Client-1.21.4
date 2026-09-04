package laura.handler;

import laura.core.Laura;
import laura.core.EventTarget;
import laura.core.Interface;
import laura.event.ClickEvent;
import laura.event.HotbarEvent;
import laura.event.TickEvent;
import laura.util.ChatUtil;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;


public class InteractHandler extends BaseHandler implements Interface {
    private final List<a> b = new ArrayList<>();

    public List<a> getTasks() {
        return this.b;
    }

    public void addTask(int slot) {
        if (this.b.isEmpty() && Laura.getInstance().getModuleProcessor().v().getInventoryHandler().a().isEmpty()) {
            this.b.add(new a(slot));
        }
    }

    public boolean hasTasks() {
        return !this.b.isEmpty();
    }

    @EventTarget
    public void onTickEvent(TickEvent event) {
        if (!this.b.isEmpty() && mc.player.age > 40) {
            InventoryHandler inventoryHandler = Laura.getInstance().getModuleProcessor().v().getInventoryHandler();
            a task = this.b.getFirst();
            boolean inventory = task.b() > 8;
            task.a(task.d() + 1);
            if (task.d() == 1) {
                if (inventory) {
                    inventoryHandler.moveItem(task.b(), task.a(), 2);
                } else {
                    mc.player.getInventory().selectedSlot = task.b();
                }
            } else if (!task.c() && task.d() > 0 && inventoryHandler.a().isEmpty()) {
                if (mc.player.isUsingItem()) {
                    task.setReturned(true);
                } else {
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                }
            } else if (task.c() && !mc.player.isUsingItem() && inventoryHandler.a().isEmpty()) {
                if (inventory) {
                    inventoryHandler.moveItem(task.a(), task.b(), 2);
                } else {
                    mc.player.getInventory().selectedSlot = task.a();
                }
                this.b.remove(task);
            }
            if (task.d() >= 60) {
                ChatUtil.sendMessage("Использование предмета не удалось по неизвестной причине");
                this.b.remove(task);
            }
        }
    }

    @EventTarget
    public void onHotbarEvent(HotbarEvent event) {
        if (hasTasks()) {
            event.a(true);
        }
    }

    @EventTarget
    public void onClickEvent(ClickEvent event) {
        if (hasTasks() && event.h() == 1) {
            event.a(true);
        }
    }

    public static final class a {
        private final int selectedSlot = Interface.mc.player.getInventory().selectedSlot;
        private final int eatSlot;
        private boolean returned;
        private int ticks;

        public a(int eatSlot) {
            this.eatSlot = eatSlot;
        }

        public void setReturned(boolean returned) {
            this.returned = returned;
        }

        public void a(int ticks) {
            this.ticks = ticks;
        }

        public int a() {
            return this.selectedSlot;
        }

        public int b() {
            return this.eatSlot;
        }

        public boolean c() {
            return this.returned;
        }

        public int d() {
            return this.ticks;
        }
    }
}
