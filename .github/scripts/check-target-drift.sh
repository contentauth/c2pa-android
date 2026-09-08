#!/usr/bin/env bash
# Assert that the target list in c2pa-android-targets.sh still matches the
# `architectures` map in library/build.gradle.kts. The two are separate on
# purpose -- the Gradle map also carries the Android ABI directory names, and
# the shell list is needed by CI without a JVM -- but they name the same Rust
# target triples and must never disagree.
#
# Drift is otherwise silent until a release preflight fails or a Gradle
# download 404s partway through a build.
#
# Exit codes:
#   0  the two agree
#   1  they disagree (both lists are printed), or the map could not be parsed
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
gradle="$root/library/build.gradle.kts"

# shellcheck source=c2pa-android-targets.sh
. "$(dirname "$0")/c2pa-android-targets.sh"

[ -f "$gradle" ] || { echo "not found: $gradle" >&2; exit 1; }

# Second quoted string of each `"<abi>" to "<triple>",` line inside the map.
from_gradle="$(
  sed -n '/^val architectures =/,/^    )/p' "$gradle" \
    | sed -n 's/.*to *"\([^"]*\)".*/\1/p' \
    | sort
)"

from_script="$(printf '%s\n' "$C2PA_ANDROID_TARGETS" | grep . | sort)"

if [ -z "$from_gradle" ]; then
  echo "could not parse any target triples out of the architectures map in ${gradle}" >&2
  echo "(the map's shape changed -- this checker needs updating)" >&2
  exit 1
fi

if [ "$from_gradle" != "$from_script" ]; then
  echo "Target lists have drifted apart." >&2
  echo >&2
  echo "library/build.gradle.kts:" >&2
  printf '%s\n' "$from_gradle" | sed 's/^/  /' >&2
  echo ".github/scripts/c2pa-android-targets.sh:" >&2
  printf '%s\n' "$from_script" | sed 's/^/  /' >&2
  echo >&2
  echo "Bring them back in lockstep; both must list the same Rust target triples." >&2
  exit 1
fi

echo "Target lists agree ($(printf '%s\n' "$from_script" | wc -l | tr -d ' ') triples)."
