package laura.core;

public enum MusicSource {
    YOUTUBE_MUSIC("YouTube Music"),
    WOLFXSPOTIFY("wolfXspotify"),
    SOUNDCLOUD("SoundCloud"),
    SPOTIFY("Spotify");

    private final String displayName;

    MusicSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }
}
