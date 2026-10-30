"""Smoke-test the packaged launcher using a tiny Java fixture (no UI/network)."""
import pathlib
import shutil
import subprocess
import sys
import tempfile

java_home = pathlib.Path(sys.argv[1]).resolve()
launcher = pathlib.Path(sys.argv[2]).resolve()
with tempfile.TemporaryDirectory(prefix="zen launcher ") as temp:
    root = pathlib.Path(temp)
    install = root / "installation with spaces"
    app = install / "app"
    app.mkdir(parents=True)
    shutil.copy2(launcher, install / "launcher.jar")
    source = root / "Probe.java"
    source.write_text('''
public class Probe {
    public static void main(String[] args) {
        System.out.println("WORKDIR=" + System.getProperty("user.dir"));
        System.out.println("ARG=" + args[0]);
    }
}
''')
    subprocess.run([str(java_home / "bin/javac"), str(source)], check=True)
    subprocess.run([
        str(java_home / "bin/jar"), "--create", "--file",
        str(app / "ZenDownloader-1.3.1.jar"), "--main-class", "Probe",
        "-C", str(root), "Probe.class"
    ], check=True)
    result = subprocess.run([
        str(java_home / "bin/java"), "-jar", str(install / "launcher.jar"),
        "argument with spaces"
    ], cwd=root, capture_output=True, text=True, timeout=30, check=True)
    assert f"WORKDIR={install}" in result.stdout, result.stdout + result.stderr
    assert "ARG=argument with spaces" in result.stdout, result.stdout + result.stderr
    print("PASS: launch from another directory, spaces in paths, argument forwarding, active Java runtime")
