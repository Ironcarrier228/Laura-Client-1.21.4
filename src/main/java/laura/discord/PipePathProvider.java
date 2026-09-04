package laura.discord;


import java.util.List;

@FunctionalInterface
public interface PipePathProvider {
    List<String> locateAll();
}
