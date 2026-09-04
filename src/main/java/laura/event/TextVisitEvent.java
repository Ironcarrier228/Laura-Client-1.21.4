package laura.event;

import laura.core.Event;


public class TextVisitEvent extends Event {
    private String text;

    public TextVisitEvent(String text) {
        this.text = text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String b() {
        return this.text;
    }
}
