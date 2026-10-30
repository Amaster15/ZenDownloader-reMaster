import java.io.File;
import kotlin.Unit;
import nobility.downloader.core.scraper.video_download.HiAnimeFallback;
import nobility.downloader.core.settings.Quality;
public class HiAnimeProbe {
 static <T> T await(java.util.function.Function<kotlin.coroutines.Continuation<? super T>,Object> action)throws Exception {
  var future=new java.util.concurrent.CompletableFuture<T>();
  Object immediate=action.apply(new kotlin.coroutines.Continuation<T>() {
   public kotlin.coroutines.CoroutineContext getContext(){return kotlin.coroutines.EmptyCoroutineContext.INSTANCE;}
   public void resumeWith(Object value){try{kotlin.ResultKt.throwOnFailure(value);future.complete((T)value);}catch(Throwable e){future.completeExceptionally(e);}}
  });
  if(immediate!=kotlin.coroutines.intrinsics.IntrinsicsKt.getCOROUTINE_SUSPENDED())return (T)immediate;
  return future.get(15,java.util.concurrent.TimeUnit.MINUTES);
 }
 public static void main(String[] args)throws Exception {
  var provider=new HiAnimeFallback();
  var match=HiAnimeProbe.<HiAnimeFallback.Match>await(c -> provider.find("Overgeared","Episode 2 English Dubbed",c));
  System.out.println("Matched episode="+match.getEpisode()+" language="+match.getLanguage()+" animeId="+match.getAnimeId());
  var stream=HiAnimeProbe.<HiAnimeFallback.Stream>await(c -> provider.resolve(match,Quality.HIGH,c));
  System.out.println("Resolved quality="+stream.getQuality()+" duration="+stream.getSeconds()+" segments="+stream.getSegments().size());
  if(args.length>0) HiAnimeProbe.<Unit>await(c -> provider.download(stream,new File(args[0]),"ffmpeg",()->true,p -> {if(p%10==0)System.out.println("Progress: "+p);return Unit.INSTANCE;},c));
  System.out.println("PASS: HiAnime provider");System.exit(0);
 }
}
