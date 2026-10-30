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
  System.out.println("PASS: countdown replacement, interleaved episodes, new attempts, elapsed timer and clear");
 }
}
