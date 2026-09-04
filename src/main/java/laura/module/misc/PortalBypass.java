package laura.module.misc;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.PortalEvent;

@ModuleRegister(name = "Portal Bypass", description = "Позволяет открывать окна, находясь в портале", category = Category.Misc)
public class PortalBypass extends Module {
    @EventTarget
    public void a(PortalEvent event) {
        event.setInPortal(false);
    }
}
