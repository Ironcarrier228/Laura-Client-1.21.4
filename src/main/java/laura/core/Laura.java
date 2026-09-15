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
        // Дата окончания берётся из метаданных сборки (build.json),
        // которые задаёт build.sh / build.bat: dev — 2099 год, public — вводит сборщик.
        this.currentUser = new User("1", "Ironcarrier", "Owner", "Owner", BuildInfo.expire(), "");
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
            // Модуль GuiSelector позволяет выбрать какой экран открывать по RShift
            String guiStyle = resolveGuiStyle();
            if ("Vanilla".equals(guiStyle)) {
                // Открываем стандартное меню Minecraft (GameMenuScreen в 1.21)
                mc.setScreen(new net.minecraft.client.gui.screen.GameMenuScreen(true));
                return;
            }
            // По умолчанию и для "Laura" — открываем нативный ClickGUI Laura
            GUIScreen screen;
            if ("Wild Classic".equals(guiStyle)) {
                // Открываем WildClient-style ClickGUI (адаптированный под Laura API)
                mc.setScreen(new laura.gui.wild.ClickGuiScreen());
                return;
            }
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

    /**
     * Возвращает текущий выбор модуля GuiSelector, или "Laura" если модуль не зарегистрирован.
     * Используется в a(KeyEvent) чтобы решить какой экран открыть по RShift.
     */
    private String resolveGuiStyle() {
        try {
            Processor proc = getInstance().getModuleProcessor();
            if (proc != null && proc.t() != null) {
                for (laura.core.Module m : proc.t().e()) {
                    if (m instanceof laura.module.misc.GuiSelector gs) {
                        return gs.getSelectedStyle();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "Laura";
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
                panel.b().a(0.0f, 1.0f, 0.22f, EasingList.g, event.g());
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
