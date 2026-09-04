package laura.core;


public interface Cancellable {
    boolean a();

    void a(boolean z);

    default boolean isCancelled() {
        return a();
    }

    default void setCancelled(boolean state) {
        a(state);
    }
}
