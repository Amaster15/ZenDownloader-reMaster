import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Function;
import kotlin.coroutines.*;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlin.ResultKt;
import nobility.downloader.core.BoxHelper;
import nobility.downloader.core.Core;
import nobility.downloader.core.driver.BrowserDriverSetup;
import nobility.downloader.core.entities.Episode;
import nobility.downloader.core.scraper.data.*;
import nobility.downloader.core.scraper.video_download.*;
import nobility.downloader.core.settings.Defaults;

public class QualityProbe {
    static <T> T await(Function<Continuation<? super T>, Object> action) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Object immediate = action.apply(new Continuation<T>() {
            public CoroutineContext getContext() { return EmptyCoroutineContext.INSTANCE; }
            public void resumeWith(Object value) {
                try { ResultKt.throwOnFailure(value); future.complete((T)value); }
                catch (Throwable e) { future.completeExceptionally(e); }
            }
        });
        if (immediate != IntrinsicsKt.getCOROUTINE_SUSPENDED()) return (T)immediate;
        return future.get(180, TimeUnit.SECONDS);
    }
    public static void main(String[] args) throws Exception {
        BoxHelper.Companion.init();
        Core.Companion.setErrorPrintStream(System.err);
        BoxHelper.Companion.update(Defaults.WCO_DOMAIN, "wcoflix");
        BoxHelper.Companion.update(Defaults.WCO_EXTENSION, "tv");
        BrowserDriverSetup.INSTANCE.setup("/usr/bin/chromium");
        var episode = new Episode(args.length > 2 ? args[2] : "Episode 1 English Dubbed", args.length > 1 ? args[1] : "yu-gi-oh-sevens-episode-1-english-dubbed", "anime/yu-gi-oh-sevens");
        var helper = new VideoDownloadHelper(new DownloadQueue(episode, null, null), null);
        try {
            Resource<DownloadData> result = await(c -> helper.parseQualities(episode.getSlug(), c));
            System.out.println("Result: " + result.getMessage() + " code=" + result.getErrorCode());
            StringBuilder links = new StringBuilder();
            links.append(helper.getVideoDownloadData().getUserAgent()).append('\n');
            for (var data : helper.getVideoDownloadData().getDownloadDatas()) {
                System.out.println("Quality: " + data.getQuality());
                links.append(data.getDownloadLink()).append('\n');
            }
            Files.writeString(Path.of(args[0]), links);
        } finally { helper.getVideoDownloadData().getBase().killDriver(); }
        System.exit(0);
    }
}
