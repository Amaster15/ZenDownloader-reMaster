import java.nio.file.*;
import nobility.downloader.core.driver.BrowserSandboxRepair;
public class BrowserSandboxRepairTest {
 public static void main(String[]args)throws Exception {
  if(!System.getProperty("os.name").startsWith("Linux"))return;
  var p=Files.createTempFile("zen-browser-probe-", ".sh");
  try {
   Files.writeString(p,"#!/bin/sh\necho 'No usable sandbox!' >&2\nexit 133\n");p.toFile().setExecutable(true);
   if(!BrowserSandboxRepair.INSTANCE.needsRepair(p.toString()))throw new AssertionError("Sandbox not detected");
   Files.writeString(p,"#!/bin/sh\necho 'Missing display' >&2\nexit 1\n");
   if(BrowserSandboxRepair.INSTANCE.needsRepair(p.toString()))throw new AssertionError("Wrong failure prompted privilege change");
   Files.writeString(p,"#!/bin/sh\necho '<html></html>'\n");
   if(BrowserSandboxRepair.INSTANCE.needsRepair(p.toString()))throw new AssertionError("Healthy browser prompted repair");
  } finally {Files.deleteIfExists(p);}
  System.out.println("PASS: only explicit sandbox failure requests repair; no privileged commands executed");
 }
}
