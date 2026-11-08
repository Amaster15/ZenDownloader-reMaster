import kotlin.Unit;
import java.util.concurrent.atomic.AtomicInteger;
import nobility.downloader.core.scraper.video_download.PageLinkRetry;
public class PageLinkRetryTest {
 static void check(int successAt,int stopAt,int expectedAttempts,long expectedWait) throws Exception {
  var attempts=new AtomicInteger();var ticks=new AtomicInteger();var notices=new AtomicInteger();
  Integer result=QualityProbe.<Integer>await(c -> PageLinkRetry.INSTANCE.<Integer>run(5,30,
   () -> ticks.get()<stopAt,
   cont -> attempts.incrementAndGet(),
   value -> value<successAt,
   (retry,total,seconds) -> {int notice=notices.getAndIncrement();if(retry!=notice/30+1||total!=5||seconds!=30-notice%30)throw new AssertionError();return Unit.INSTANCE;},
   (millis,cont) -> {if(millis!=1000L)throw new AssertionError();ticks.incrementAndGet();return Unit.INSTANCE;},c));
  if(attempts.get()!=expectedAttempts||ticks.get()*1000L!=expectedWait)throw new AssertionError("attempts="+attempts+" ticks="+ticks);
  if(stopAt!=Integer.MAX_VALUE&&result!=null)throw new AssertionError("Stop must return null");
 }
 public static void main(String[] args)throws Exception {
  var runningChecks=new AtomicInteger();
  long start=System.nanoTime();
  boolean waited=QualityProbe.<Boolean>await(c -> PageLinkRetry.INSTANCE.waitBeforeRetry(30, () -> runningChecks.incrementAndGet() == 1,c));
  long elapsed=(System.nanoTime()-start)/1000000;
  if(waited||elapsed<900||elapsed>5000)throw new AssertionError("Connection retry Stop: "+elapsed);
  check(1,Integer.MAX_VALUE,1,0);
  check(3,Integer.MAX_VALUE,3,60000);
  check(99,Integer.MAX_VALUE,6,150000);
  check(99,1,1,1000);
  System.out.println("PASS: immediate success, recovery, five-retry limit, 30-second waits, Stop during wait");
 }
}
