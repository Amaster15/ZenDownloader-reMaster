import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import nobility.downloader.core.scraper.DownloadThread;
import nobility.downloader.core.scraper.data.DownloadQueue;
import nobility.downloader.core.entities.Episode;
public class HiAnimeQueueTest {
 public static void main(String[] args)throws Exception {
  var thread=new DownloadThread();
  var pendingField=DownloadThread.class.getDeclaredField("hiAnimeQueue");pendingField.setAccessible(true);
  var pending=(Map<String,DownloadQueue>)pendingField.get(thread);
  var jobsField=DownloadThread.class.getDeclaredField("currentJobs");jobsField.setAccessible(true);
  var jobs=(AtomicInteger)jobsField.get(thread);
  var take=DownloadThread.class.getDeclaredMethod("takeHiAnimeIfReady");take.setAccessible(true);
  var done=DownloadThread.class.getDeclaredMethod("hasFinished");done.setAccessible(true);
  var episode=new Episode("Episode 2 English Dubbed","test-episode-2","anime/test");
  var queue=new DownloadQueue(episode,null,null);
  pending.put(episode.getSlug(),queue);pending.put(episode.getSlug(),queue);
  if(pending.size()!=1||(boolean)done.invoke(thread))throw new AssertionError("Pending failures must prevent completion");
  thread.getDownloadQueue().add(queue);
  if(take.invoke(thread)!=null)throw new AssertionError("Must wait for WCO queue");
  thread.getDownloadQueue().clear();jobs.set(1);
  if(take.invoke(thread)!=null)throw new AssertionError("Must wait for in-flight WCO");
  jobs.set(0);
  if(take.invoke(thread)!=queue||take.invoke(thread)!=null)throw new AssertionError("Must run failure once");
  pending.put(episode.getSlug(),queue);thread.clear();
  if(take.invoke(thread)!=null||!(boolean)done.invoke(thread))throw new AssertionError("Stop must clear failures");
  thread.stop();System.out.println("PASS: WCO queue/in-flight barrier, pending completion, deduplication, stop cleanup");
 }
}
