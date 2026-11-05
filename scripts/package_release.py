#!/usr/bin/env python3
"""Package prebuilt app/launcher JARs with extracted platform Java runtimes."""
import argparse
import hashlib
from pathlib import Path
import shutil
import zipfile

p = argparse.ArgumentParser()
p.add_argument('--version', required=True)
p.add_argument('--linux-runtime', type=Path, required=True)
p.add_argument('--windows-runtime', type=Path, required=True)
a = p.parse_args()
repo = Path(__file__).resolve().parents[1]
out = repo / 'app/build/releases' / a.version
out.mkdir(parents=True, exist_ok=True)
app = repo / f'app/build/custom-jars/ZenDownloader-{a.version}.jar'
launcher = repo / 'bootstrap/build/libs/launcher.jar'
assert launcher.is_file(), 'Build :bootstrap:packageBootstrapJar before packaging'
readme = '''ZenDownloader-reMaster {version}

Extract the entire ZIP before running. Keep app, runtime, and launcher.jar together.
Windows x64: double-click run.bat.
Linux x64: run chmod +x run.sh, then ./run.sh.
Java 21 is included. Install Google Chrome or Chromium separately.
FFmpeg/FFplay can be installed from the app or supplied in ~/.zen_database.
Settings and download history are stored in your user profile, outside this folder.

WCO retries default to 30 attempts after the initial attempt, 30 seconds apart.
HiAnime Fallback is enabled by default and runs after all WCO work finishes.
It requires an exact show/episode/language match; unavailable matches are skipped.
Normal HTTPS certificate validation is enabled by default.

Windows package is cross-built; interactive Windows testing was not available.
Source and issue information: https://github.com/Amaster15/ZenDownloader-reMaster
Original project: https://github.com/NobilityDeviant/ZenDownloader
'''.format(version=a.version)
assets = []
for os, runtime in [('windows', a.windows_runtime), ('linux', a.linux_runtime)]:
    name = f'ZenDownloader-reMaster-{a.version}-{os}-x64'
    stage = out / name
    if stage.exists():
        shutil.rmtree(stage)
    (stage / 'app').mkdir(parents=True)
    shutil.copy2(app, stage / 'app' / app.name)
    shutil.copy2(launcher, stage / 'launcher.jar')
    shutil.copytree(runtime, stage / 'runtime', symlinks=False)
    shutil.copy2(repo / 'LICENSE', stage / 'LICENSE')
    (stage / 'README.txt').write_text(readme)
    if os == 'windows':
        (stage / 'run.bat').write_bytes(b'@echo off\r\nset "DIR=%~dp0"\r\n"%DIR%runtime\\bin\\java.exe" -jar "%DIR%launcher.jar" %*\r\nif errorlevel 1 pause\r\n')
        assert (stage / 'runtime/bin/java.exe').exists()
    else:
        script = stage / 'run.sh'
        script.write_text('#!/usr/bin/env bash\nset -euo pipefail\nDIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"\nexec "$DIR/runtime/bin/java" -jar "$DIR/launcher.jar" "$@"\n')
        script.chmod(0o755)
        (stage / 'runtime/bin/java').chmod(0o755)
    archive = out / (name + '.zip')
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for file in sorted(stage.rglob('*')):
            if file.is_file():
                z.write(file, file.relative_to(out))
    assets.append(archive)
standalone = out / app.name
shutil.copy2(app, standalone)
assets.append(standalone)
(out / 'SHA256SUMS.txt').write_text(''.join(f'{hashlib.sha256(f.read_bytes()).hexdigest()}  {f.name}\n' for f in assets))
for f in assets:
    print(f'{f.name}: {f.stat().st_size:,} bytes')
