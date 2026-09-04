package laura.event;

import laura.core.Event;


import net.minecraft.text.Text;

public class ScoreboardEvent extends Event {
    private Text title;

    public ScoreboardEvent(Text title) {
        this.title = title;
    }

    public void setTitle(Text title) {
        this.title = title;
    }

    public Text b() {
        return this.title;
    }
}
