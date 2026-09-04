package laura.handler;

import laura.core.EventManager;

public class BaseHandler {
    public BaseHandler() {
        EventManager.a(this);
    }
}
