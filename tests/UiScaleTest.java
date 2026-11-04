import nobility.downloader.ui.theme.UiScale;
public class UiScaleTest {
 static void check(float nativeScale,int height,int zoom,float expected){float actual=UiScale.density(nativeScale,height,zoom);if(Math.abs(actual-expected)>.001)throw new AssertionError(nativeScale+" / "+height+" / "+zoom+" got "+actual);}
 public static void main(String[]args){
 check(1,1080,0,1);check(1,1440,0,1.25f);check(1,2160,0,2);
 check(1.25f,1440,0,1.25f);check(1.5f,2160,0,2);check(2,2160,0,2);check(2.5f,2160,0,2.5f);
 check(1,2160,150,1.5f);check(2,2160,100,2);check(1,1080,125,1.25f);
 // Moving from a 4K monitor to 1080p must restore system density.
 check(1,2160,0,2);check(1,1080,0,1);
 System.out.println("PASS: 1080p, 1440p, 4K, existing OS scaling, manual zoom, and monitor changes");System.exit(0);
 }
}
