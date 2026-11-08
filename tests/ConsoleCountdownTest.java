import nobility.downloader.ui.components.console.ConsoleState;
public class ConsoleCountdownTest {
 static void line(ConsoleState state,String text){for(char c:(text+"\n").toCharArray())state.appendChar(c);}
 public static void main(String[] args){
  var state=new ConsoleState(false);
  line(state,"[Episode 1] Player popup: 10 seconds remaining before Close.");
  line(state,"[Episode 2] Player popup: 9 seconds remaining before Close.");
  line(state,"[Episode 1] Player popup: 8 seconds remaining before Close.");
  if(state.getLineCount()!=2||state.getConsoleText().contains("10 seconds"))throw new AssertionError("Timers must replace their own rows");
  line(state,"[Episode 1] Player popup countdown finished.");
  line(state,"[Episode 1] Player popup: 10 seconds remaining before Close.");
  if(state.getLineCount()!=4)throw new AssertionError("New attempt must have a new row");
  line(state,"[Episode 1] Player popup: waiting for Close (11 seconds elapsed).");
  if(state.getLineCount()!=4||!state.getConsoleText().contains("11 seconds elapsed"))throw new AssertionError("Elapsed fallback must update");
  state.clear();line(state,"[Episode 1] Player popup: 5 seconds remaining before Close.");
  if(state.getLineCount()!=1)throw new AssertionError("Clear must reset timers");
  state.clear();
  line(state,"[Episode 1] No video link found. Page retry 1 of 30 in 30 seconds.");
  line(state,"[Episode 2] No video link found. Page retry 1 of 30 in 30 seconds.");
  line(state,"[Episode 1] No video link found. Page retry 1 of 30 in 29 seconds.");
  if(state.getLineCount()!=2||!state.getConsoleText().contains("29 seconds"))throw new AssertionError("Retry countdown must update its own row");
  line(state,"[Episode 1] Resolving video link: attempt 2 of 31.");
  line(state,"[Episode 1] No video link found. Page retry 2 of 30 in 30 seconds.");
  if(state.getLineCount()!=4)throw new AssertionError("New retry must retain prior attempt");
  state.clear();
  state.updateDownloadProgress("wco-1", "Episode 1: 1%");
  state.updateDownloadProgress("hi-2", "Episode 2: 3%");
  line(state, "Resolving another episode");
  state.updateDownloadProgress("wco-1", "Episode 1: 75%");
  state.updateDownloadProgress("hi-2", "Episode 2: 100%");
  if(state.getLineCount()!=3 || state.getConsoleText().contains("Episode 1: 1%") || !state.getConsoleText().contains("Episode 1: 75%")) throw new AssertionError("Progress must update per download without replacing other activity");
  state.clear(); state.updateDownloadProgress("wco-1", "Episode 1: 2%");
  if(state.getLineCount()!=1) throw new AssertionError("Progress must recover after clear");
  state.clear();
  state.updateDownloadProgress("provider-a", "Same title: 0%");
  state.updateDownloadProgress("provider-b", "Same title: 0%");
  state.updateDownloadProgress("provider-a", "Same title: 12%");
  state.updateDownloadProgress("provider-b", "Same title: 25%");
  if(state.getLineCount()!=2 || !state.getConsoleText().contains("12%") || !state.getConsoleText().contains("25%")) throw new AssertionError("Identical titles need independent rows");
  for(int i=0;i<1100;i++)line(state,"Log "+i);
  state.updateDownloadProgress("provider-a", "Same title: 50%");
  if(state.getLineCount()!=1000 || !state.getConsoleText().contains("50%")) throw new AssertionError("Progress row must survive history eviction");
  System.out.println("PASS: countdown replacement, interleaved episodes, new attempts, elapsed timer and clear");
 }
}
