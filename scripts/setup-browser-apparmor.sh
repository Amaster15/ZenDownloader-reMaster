#!/usr/bin/env bash
# Run as the desktop user: sudo is requested only for the specific policy installation.
set -euo pipefail
if [[ $(id -u) == 0 ]]; then
  echo 'Run this script as your normal desktop user, without sudo.' >&2
  exit 1
fi
command -v apparmor_parser >/dev/null || { echo 'AppArmor tools are not installed.' >&2; exit 1; }
# Reject AppArmor pattern/control characters in the account path.
case "$HOME" in
  *[!a-zA-Z0-9_./-]*) echo 'Home path requires a manually reviewed AppArmor profile.' >&2; exit 1 ;;
esac
profile=$(mktemp)
trap 'rm -f "$profile"' EXIT
cat > "$profile" <<POLICY
abi <abi/4.0>,
include <tunables/global>
profile zendownloader-managed-chrome "$HOME/.zen_database/browser/*/chrome-linux64/chrome" flags=(unconfined) {
  userns,
}
POLICY
cat "$profile"
echo 'This permits user namespaces only for Chrome in your managed ZenDownloader browser directory.'
echo 'Other executables placed at that matching path would also receive this permission.'
read -r -p 'Install this AppArmor policy? [y/N] ' answer
[[ "$answer" == y || "$answer" == Y ]] || exit 0
sudo install -m 0644 "$profile" /etc/apparmor.d/zendownloader-managed-chrome
sudo apparmor_parser -r /etc/apparmor.d/zendownloader-managed-chrome
echo 'Policy loaded. Retry the visible browser in ZenDownloader.'
