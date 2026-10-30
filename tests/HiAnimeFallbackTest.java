import java.util.*;
import nobility.downloader.core.scraper.video_download.HiAnimeFallback;
public class HiAnimeFallbackTest {
 static void reject(Runnable f){try{f.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("Expected rejection");}
 public static void main(String[] args){
  if(!Integer.valueOf(2).equals(HiAnimeFallback.episodeNumber("Episode 2 English Dubbed")))throw new AssertionError();
  for(String s:List.of("Episode 2.5 English Dubbed","Episode 2 Episode 3","Movie","Episode 0"))if(HiAnimeFallback.episodeNumber(s)!=null)throw new AssertionError(s);
  if(!"dub".equals(HiAnimeFallback.language("Episode 2 English Dubbed"))||HiAnimeFallback.language("Episode 2")!=null)throw new AssertionError();
  String html="<a href='https://hianime.ms/details/a'>Overgeared</a><a href='/details/b'>Overgeared Season 2</a><a href='/details/c'>Other</a>";
  if(HiAnimeFallback.matchingSeries(html,"Overgeared").size()!=1||!HiAnimeFallback.matchingSeries(html,"Missing").isEmpty())throw new AssertionError();
  if(HiAnimeFallback.matchingSeries(html+"<a href='/details/remake'>Overgeared</a>","Overgeared").size()!=2)throw new AssertionError();
  String base="https://media.example/master.m3u8";
  if(HiAnimeFallback.variants("#EXTM3U\n#EXT-X-STREAM-INF:RESOLUTION=1920x1080\n1080.m3u8\n#EXT-X-STREAM-INF:RESOLUTION=640x360\n360.m3u8",base).size()!=1)throw new AssertionError();
  reject(()->HiAnimeFallback.segments("#EXTM3U\nhttps://media.example/1.ts",base));
  reject(()->HiAnimeFallback.segments("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128\n1.ts\n#EXT-X-ENDLIST",base));
  reject(()->HiAnimeFallback.segments("#EXTM3U\nfile:///tmp/no\n#EXT-X-ENDLIST",base));
  byte[] payload=new byte[188*8];for(int i=0;i<payload.length;i+=188)payload[i]=0x47;
  byte[] wrapped=new byte[252+payload.length];System.arraycopy(payload,0,wrapped,252,payload.length);
  if(!Arrays.equals(payload,HiAnimeFallback.tsPayload(wrapped)))throw new AssertionError();
  reject(()->HiAnimeFallback.tsPayload(new byte[20]));
  reject(()->HiAnimeFallback.tsPayload(Arrays.copyOf(wrapped,wrapped.length-1)));
  System.out.println("PASS: exact/ambiguous titles, episode/language parsing, supported qualities, VOD restrictions, image-wrapped TS validation");
 }
}
