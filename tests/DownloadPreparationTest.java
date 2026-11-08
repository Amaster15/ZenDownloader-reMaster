import nobility.downloader.core.scraper.DownloadThread;
import nobility.downloader.core.entities.Episode;
import nobility.downloader.core.scraper.data.DownloadQueue;
import java.util.List;

public class DownloadPreparationTest {
    public static void main(String[] args) {
        var thread = new DownloadThread();
        var ep = new Episode("Episode 1", "example-episode-1", "anime/example");
        if (thread.addToQueue(List.of(ep), null) != 1) throw new AssertionError("enqueue");
        var pending = thread.getPreparation().get(ep.getSlug());
        if (pending == null || !pending.getDownloadProgress().getValue().equals("Queued"))
            throw new AssertionError("Must be visible before link resolution");
        if (pending.isComplete()) throw new AssertionError("Preparation cannot be completed");
        thread.preparationStatus(ep.getSlug(), "Waiting for player");
        if (!pending.getDownloadProgress().getValue().equals("Waiting for player"))
            throw new AssertionError("Live status");
        if (thread.addToQueue(List.of(ep), null) != 0) throw new AssertionError("Duplicate queue item");
        thread.removeFromQueue(thread.getDownloadQueue().get(0));
        if (!thread.getPreparation().isEmpty()) throw new AssertionError("Remove preparation");
        thread.addToQueue(List.of(ep), null);
        thread.clear();
        if (!thread.getPreparation().isEmpty()) throw new AssertionError("Clear preparation");
        thread.stop();
        System.out.println("PASS: queued visibility, status, duplicate, removal and clear");
    }
}
