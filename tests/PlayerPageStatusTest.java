import java.nio.file.*;
import nobility.downloader.core.scraper.video_download.PlayerPageStatus;
public class PlayerPageStatusTest {
    public static void main(String[] args) throws Exception {
        var detector = PlayerPageStatus.INSTANCE;
        for (String path : args) {
            if (!detector.isServerBusy(Files.readString(Path.of(path)))) throw new AssertionError(path);
        }
        if (detector.isServerBusy("<title>Video Player</title><script>$.getJSON(\"/video.json\", function(response){})</script>")) {
            throw new AssertionError("Playable page rejected");
        }
        if (detector.isServerBusy("<title>Player</title><script>const message='Servers Busy';</script>")) {
            throw new AssertionError("Incidental script text rejected");
        }
        if (detector.isServerBusy("<title>403 Forbidden</title>")) throw new AssertionError("Unrelated error misclassified");
        System.out.println("PASS: capacity response recognized; normal player, script text, and other errors preserved");
    }
}
