#!/bin/bash
set -euo pipefail
source_app="$1"
active_app="$2"
parent_pid="$3"
work="$4"
on_exit() { local result=$?; if [[ "$result" -ne 0 ]]; then /usr/bin/open "$active_app" || true; fi; return "$result"; }
trap on_exit EXIT
for n in {1..120}; do
  if ! kill -0 "$parent_pid" 2>/dev/null; then break; fi
  sleep 0.25
done
if kill -0 "$parent_pid" 2>/dev/null; then echo 'Client did not quit; nothing replaced'; exit 1; fi
apply_one() {
  local target="$1" label="$2" saved="$work/backup-$2-$$" stage="$work/stage-$2-$$"
  [[ -d "$target/Contents" ]] || return 0
  [[ "$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$target/Contents/Info.plist")" == dev.tungtung.client ]] || return 1
  mkdir -p "$saved"
  /bin/cp -cR "$source_app/Contents" "$stage"
  /usr/bin/codesign --verify --deep --strict "$source_app"
  mv "$target/Contents" "$saved/Contents"
  if ! mv "$stage" "$target/Contents"; then mv "$saved/Contents" "$target/Contents"; return 1; fi
}
apply_one "$active_app" active
for candidate in '/Applications/Tung Tung Client.app' "$HOME/Applications/Tung Tung Client.app"; do
  if [[ "$candidate" != "$active_app" && -d "$candidate/Contents" && -w "$candidate" ]]; then
    apply_one "$candidate" "$(echo "$candidate" | /usr/bin/shasum | /usr/bin/cut -c1-12)" || true
  fi
done
/usr/bin/open "$active_app"
