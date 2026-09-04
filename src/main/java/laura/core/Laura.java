package laura.core;

import laura.event.DrawEvent;
import laura.event.KeyEvent;
import laura.render.EasingList;
import laura.render.ScaleUtil;
import laura.ui.screen.GUIPanel;
import laura.ui.screen.GUIScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class Laura {

    private static Laura instance;
    private static volatile Laura instanceRef;
    private Processor moduleProcessor;
    private GUIScreen currentScreen;
    private Client networkClient;
    private User currentUser;

    public Laura() {
        initialize();
    }

    public static void publishUser(User user) {
        Laura client = instanceRef;
        if (client != null) {
            client.currentUser = user;
        }
    }

    public static void jc$publishUnifiedUser$(User user) {
        publishUser(user);
    }

    public static Laura getInstance() {
        return instance;
    }

    protected void initialize() {
        this.currentUser = new User("1", "Ironcarrier", "Owner", "Owner", "01.01.2099 00:00", "");
        instance = this;
        instanceRef = this;

        this.moduleProcessor = new Processor();
        this.networkClient = new Client(false);

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> this.a());

        EventManager.a(this);

        this.moduleProcessor.a();
    }

    protected void shutdown() {
        this.moduleProcessor.b();
    }

    public String getDeveloperName() {
        return null;
    }

    public Processor getModuleProcessor() {
        return this.moduleProcessor;
    }

    public GUIScreen getCurrentScreen() {
        return this.currentScreen;
    }

    public void setCurrentScreen(GUIScreen guiScreen) {
        this.currentScreen = guiScreen;
    }

    public GUIScreen e() {
        return this.currentScreen;
    }

    public Client f() {
        return this.networkClient;
    }

    public User g() {
        return this.currentUser;
    }

    public String c() {
        return getDeveloperName();
    }

    public void a(Processor processor) {
        this.moduleProcessor = processor;
    }

    public void a(GUIScreen guiScreen) {
        this.currentScreen = guiScreen;
    }

    public void a(Client client) {
        this.networkClient = client;
    }

    public void a(User user) {
        this.currentUser = user;
    }

    public void a() {
        shutdown();
    }

    @EventTarget
    public void a(KeyEvent event) {
        if (event.getAction() == 1 && Interface.mc.currentScreen == null && event.getKey() == 344) {
            MinecraftClient mc = Interface.mc;
            GUIScreen screen;
            if (this.currentScreen != null) {
                screen = this.currentScreen;
            } else {
                GUIScreen newScreen = new GUIScreen(Text.literal(""));
                screen = newScreen;
                this.currentScreen = newScreen;
            }
            mc.setScreen(screen);
        }
    }

    @EventTarget(a = 0)
    public void a(DrawEvent event) {
        if (event.b()) {
            ScaleUtil.a(event.i(), 2);
            for (Module module : getInstance().getModuleProcessor().t().e()) {
                module.f().a(0.0f, 1.0f, 0.3f, EasingList.i, event.g());
                module.f().a(module.m());
                module.g().a(0.0f, 1.0f, 0.3f, EasingList.i, event.g());
                module.g().a(module.n());
            }
            for (GUIPanel panel : e().c()) {
                panel.b().a(0.0f, 1.0f, 0.3f, EasingList.g, event.g());
                panel.b().a(Interface.mc.currentScreen instanceof GUIScreen);
            }
        }
    }

    @EventTarget(a = 4)
    public void b(DrawEvent event) {
        if (event.b()) {
            ScaleUtil.a(event.i());
        }
    }
}
