package laura.config;

import laura.core.EventManager;

import laura.core.Interface;

public abstract class BaseProcessor implements Interface {
    public BaseProcessor() {
        EventManager.a(this);
    }

    public abstract void setup();

    public abstract void unSetup();
}
