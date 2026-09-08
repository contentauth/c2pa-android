#!/usr/bin/env bash
# Build the four Android c2pa-c-ffi archives from a c2pa-rs checkout using
# upstream's own packaging: `make release TARGET=<triple>` in c2pa_c_ffi/.
# That Makefile drives cross for each Android triple, applies the 16KB
# page-size linker flag, and zips include/ and lib/ into
# target/artifacts/c2pa-v<version>-<triple>.zip -- exactly the layout the
# downloadNativeLibraries Gradle task consumes via c2paArchiveDir.
#
# Linux with a working Docker daemon: upstream's Android targets build through
# cross, which runs the compiler in a container carrying the NDK. rustup and
# cargo must already be installed; the Makefile installs cross itself.
#
# Usage: build-c2pa-archives.sh <c2pa-rs checkout> <output dir>
#
# Exit codes:
#   0         all four archives are in <output dir>
#   non-zero  a target failed to build (the script exits with make's status
#             under set -e, stopping at the first failure)
#   1         usage error, not a c2pa-rs checkout, or a target produced zero
#             or more than one archive
set -euo pipefail

checkout="${1:?usage: $0 <c2pa-rs checkout> <output dir>}"
out="${2:?usage: $0 <c2pa-rs checkout> <output dir>}"

# shellcheck source=c2pa-android-targets.sh
. "$(dirname "$0")/c2pa-android-targets.sh"

if [ ! -f "$checkout/c2pa_c_ffi/Makefile" ]; then
  echo "not a c2pa-rs checkout: ${checkout} has no c2pa_c_ffi/Makefile" >&2
  exit 1
fi

# The Makefile's TARGET_DIR defaults to ../target relative to c2pa_c_ffi.
artifacts="$checkout/target/artifacts"

# A restored cargo cache can carry artifacts/ from an earlier run at a
# different upstream version; clear it so every archive below is from this
# build and each target has exactly one.
rm -rf "$artifacts"
# A reused output directory must not accumulate archives from an earlier
# version.
rm -rf "$out"
mkdir -p "$out"

for target in $C2PA_ANDROID_TARGETS; do
  echo "==> Building ${target}"
  make -C "$checkout/c2pa_c_ffi" release TARGET="$target"

  shopt -s nullglob
  matches=("$artifacts"/c2pa-*-"$target".zip)
  shopt -u nullglob
  if [ "${#matches[@]}" -ne 1 ]; then
    echo "expected exactly one c2pa-*-${target}.zip in ${artifacts}, found ${#matches[@]}:" >&2
    for m in ${matches[@]+"${matches[@]}"}; do echo "  ${m}" >&2; done
    exit 1
  fi

  cp "${matches[0]}" "$out/"
done

echo "==> Archives in ${out}:"
ls -l "$out"
