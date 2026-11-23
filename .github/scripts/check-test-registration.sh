#!/usr/bin/env bash
# Assert that every shared test is registered with both of its runners.
#
# A test in test-shared/ is a `suspend fun test*` on an abstract suite. Nothing
# runs it until it is called from two places, which are maintained by hand:
#
#   - a JUnit wrapper in library/src/androidTest/.../Android<Suite>.kt, which
#     is what the instrumented suite and CI run;
#   - the "Run All Tests" list in the test app's TestScreen.kt.
#
# A test that is missing from the first never runs in CI, and one that is
# missing from the second never runs in the app. Either way it can be broken
# for a long time without anyone noticing.
#
# Exit codes:
#   0  every shared test is registered in both places
#   1  at least one registration is missing (each is printed), or a file the
#      check depends on could not be found
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
shared_dir="$root/test-shared/src/main/kotlin/org/contentauth/c2pa/test/shared"
android_dir="$root/library/src/androidTest/kotlin/org/contentauth/c2pa"
test_screen="$root/test-app/app/src/main/kotlin/org/contentauth/c2pa/testapp/TestScreen.kt"

for required in "$shared_dir" "$android_dir" "$test_screen"; do
  if [ ! -e "$required" ]; then
    echo "error: $required not found" >&2
    exit 1
  fi
done

missing=0
total=0

for suite_file in "$shared_dir"/*Tests.kt; do
  suite="$(basename "$suite_file" .kt)"
  android_file="$android_dir/Android$suite.kt"
  # CoreTests -> coreTests, the name TestScreen.kt gives its instance.
  instance="$(printf '%s' "${suite:0:1}" | tr '[:upper:]' '[:lower:]')${suite:1}"

  tests="$(sed -n 's/^[[:space:]]*suspend fun \(test[A-Za-z0-9_]*\)(.*/\1/p' "$suite_file")"
  if [ -z "$tests" ]; then
    echo "error: no tests found in $suite_file" >&2
    exit 1
  fi
  if [ ! -f "$android_file" ]; then
    echo "error: $suite has no instrumented wrapper class: $android_file not found" >&2
    exit 1
  fi

  while read -r test; do
    total=$((total + 1))
    if ! grep -qE "(^|[^A-Za-z0-9_])$test\(\)" "$android_file"; then
      echo "error: $suite.$test is not called from $(basename "$android_file")" >&2
      missing=$((missing + 1))
    fi
    if ! grep -qE "(^|[^A-Za-z0-9_])$instance\.$test\(\)" "$test_screen"; then
      echo "error: $suite.$test is not called from TestScreen.kt" >&2
      missing=$((missing + 1))
    fi
  done <<<"$tests"
done

if [ "$missing" -ne 0 ]; then
  echo "$missing missing registration(s). Add a wrapper to the Android*Tests.kt class and a" >&2
  echo "results.add(...) line to TestScreen.kt for each test named above." >&2
  exit 1
fi

echo "all $total shared tests are registered in the instrumented suite and the test app"
