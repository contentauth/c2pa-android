#!/usr/bin/env bash
set -euo pipefail

TEST_SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/test-versioned-docs.sh"
source "$(dirname "$TEST_SCRIPT")/generate-versioned-docs.sh"
SOURCE_ROOT="$ROOT"

gh() {
  [[ "$1" == api ]] || return 1
  printf '{"tag_name":"%s","draft":false}\n' "${2##*/}"
}

if [[ "${1:-}" == --build ]]; then
  ROOT="$2"
  build_site
  exit
fi

TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/versioned-docs-tests-XXXXXX")"
trap 'rm -rf -- "$TEST_ROOT"' EXIT

expect_failure() {
  if "$@" > "$ROOT/failure.log" 2>&1; then
    cat "$ROOT/failure.log"
    fail "Expected command to fail: $*"
    return 1
  fi
}

make_dokka() {
  local output="$1" relative
  mkdir -p "$output/scripts"
  for relative in index.html c2pa-android/org.contentauth.c2pa/index.html c2pa-android/org.contentauth.c2pa/-reader/index.html; do
    mkdir -p "$(dirname "$output/$relative")"
    printf '%s\n' '<html><head><title>C2PA</title></head><body><div id="library-version"></div></body></html>' > "$output/$relative"
  done
  printf '%s\n' '<div class="toc--part">Navigation</div>' > "$output/navigation.html"
  printf '%s\n' '// Existing asset' > "$output/scripts/main.js"
}

configure_root() {
  mkdir -p "$ROOT/docs" "$ROOT/.github/scripts" "$ROOT/build/docs-versioned" "$ROOT/build/docs"
  printf '%s\n' '{"versions":["0.0.15","0.0.14"]}' > "$ROOT/docs/versions.json"
  cp "$SOURCE_ROOT/.github/scripts/doc-version-selector.js" "$SOURCE_ROOT/.github/scripts/doc-version-selector.css" "$ROOT/.github/scripts/"
  printf 'Existing site\n' > "$ROOT/build/docs-versioned/previous.html"
  printf 'Working-tree site\n' > "$ROOT/build/docs/working.html"
}

commit_sources() {
  git -C "$ROOT" add -- gradlew gradle library
  git -C "$ROOT" -c user.name=Fixture -c user.email=fixture@example.invalid -c commit.gpgsign=false commit -qm fixture
}

prepare_releases() {
  configure_root
  git -C "$ROOT" init -q
  mkdir -p "$ROOT/gradle/wrapper" "$ROOT/library/src/main/kotlin"
  printf fixture > "$ROOT/gradle/wrapper/gradle-wrapper.jar"
  printf fixture > "$ROOT/library/build.gradle.kts"
  printf '# Module\n' > "$ROOT/library/MODULE.md"
  printf 'class Reader\n' > "$ROOT/library/src/main/kotlin/Reader.kt"
  {
    printf '#!/usr/bin/env bash\nset -euo pipefail\n'
    declare -f make_dokka
    printf '%s\n' '[[ -z "${GH_TOKEN+x}" && -z "${GITHUB_TOKEN+x}" ]] || exit 70' 'if [[ -f library/FAIL_BUILD ]]; then exit 71; fi' 'make_dokka "$PWD/build/docs"'
  } > "$ROOT/gradlew"
  chmod +x "$ROOT/gradlew"
  commit_sources
  git -C "$ROOT" tag 0.0.15
  git -C "$ROOT" tag 0.0.14
}

test_versions_preserve_order() {
  printf '%s\n' '{"versions":["0.0.16-beta.1","0.0.15"]}' > "$ROOT/config.json"
  [[ "$(load_versions "$ROOT/config.json")" == $'0.0.16-beta.1\n0.0.15' ]]
}

test_invalid_versions() {
  local config
  for config in '{}' '[]' '{"versions":"0.0.15"}' '{"versions":[]}' \
    '{"versions":["0.0.15","0.0.15"]}' '{"versions":["main"]}' \
    '{"versions":["../0.0.15"]}' '{"versions":["--help"]}' \
    '{"versions":["v0.0.15"]}' '{"versions":["0.0.15\n"]}' \
    '{"versions":[15]}' '{"versions":[null]}' 'not JSON'; do
    printf '%s\n' "$config" > "$ROOT/config.json"
    expect_failure load_versions "$ROOT/config.json"
  done
}

test_published_releases() {
  verify_release 0.0.15
  local response
  for response in '[]' '{"tag_name":"0.0.15"}' '{"tag_name":"0.0.15","draft":true}' '{"tag_name":"0.0.14","draft":false}'; do
    gh() { printf '%s\n' "$response"; }
    expect_failure verify_release 0.0.15
  done
  gh() { return 1; }
  expect_failure verify_release 0.0.15
}

test_export_and_permissions() {
  prepare_releases
  mkdir "$ROOT/stage"
  export_release "$ROOT" 0.0.15 "$ROOT/stage"
  [[ -x "$ROOT/stage/gradlew" && -f "$ROOT/stage/library/src/main/kotlin/Reader.kt" ]]
  expect_failure export_release "$ROOT" 0.0.1 "$ROOT/stage"
}

test_unsafe_sources() {
  prepare_releases
  local name tag=1
  for name in link 'unsafe\name' 'C:escape'; do
    if [[ "$name" == link ]]; then ln -s ../outside "$ROOT/library/$name"; else touch "$ROOT/library/$name"; fi
    commit_sources
    git -C "$ROOT" tag "1.0.$tag"
    mkdir "$ROOT/stage-$tag"
    expect_failure export_release "$ROOT" "1.0.$tag" "$ROOT/stage-$tag"
    [[ ! -e "$ROOT/stage-$tag/gradlew" ]]
    rm "$ROOT/library/$name"
    tag=$((tag + 1))
  done
}

test_missing_sources() {
  prepare_releases
  rm "$ROOT/gradle/wrapper/gradle-wrapper.jar"
  commit_sources
  git -C "$ROOT" tag 1.0.0
  mkdir "$ROOT/stage"
  expect_failure export_release "$ROOT" 1.0.0 "$ROOT/stage"
}

test_build_removes_tokens() {
  prepare_releases
  export GH_TOKEN=fixture-token GITHUB_TOKEN=fixture-token
  build_docs "$ROOT"
  [[ "$GH_TOKEN" == fixture-token && "$GITHUB_TOKEN" == fixture-token ]]
}

test_missing_dokka_output() {
  printf '#!/usr/bin/env bash\nexit 0\n' > "$ROOT/gradlew"
  chmod +x "$ROOT/gradlew"
  expect_failure build_docs "$ROOT"
  mkdir -p "$ROOT/build/docs"
  touch "$ROOT/build/docs/index.html"
  expect_failure build_docs "$ROOT"
}

test_render_relative_assets() {
  configure_root
  make_dokka "$ROOT/source"
  local prefix output
  for prefix in '' versions/0.0.15 versions/0.0.14; do
    output="$ROOT/output/${prefix:-default}"
    render_docs "$ROOT/source" "$output" 0.0.15 '["0.0.15","0.0.14"]' "$prefix"
    grep -Fq 'src="doc-version-selector.js"' "$output/index.html"
    grep -Fq 'src="../../../doc-version-selector.js"' "$output/c2pa-android/org.contentauth.c2pa/-reader/index.html"
    grep -Fq 'data-versions="[&quot;0.0.15&quot;,&quot;0.0.14&quot;]"' "$output/index.html"
    grep -Fq "data-prefix=\"$prefix\"" "$output/index.html"
    cmp "$ROOT/source/navigation.html" "$output/navigation.html"
    cmp "$ROOT/source/scripts/main.js" "$output/scripts/main.js"
    [[ -f "$output/doc-version-selector.css" && -f "$output/doc-version-selector.js" ]]
  done
  ! grep -Fq doc-version-selector "$ROOT/source/index.html"
}

test_invalid_dokka_pages() {
  configure_root
  make_dokka "$ROOT/source"
  local page count=0
  for page in '<head></head><body>No slot</body>' '<div id="library-version"></div>'; do
    printf '%s\n' "$page" > "$ROOT/source/index.html"
    expect_failure render_docs "$ROOT/source" "$ROOT/output-$count" 0.0.15 '["0.0.15"]' ''
    count=$((count + 1))
  done
}

test_complete_site() {
  prepare_releases
  GH_TOKEN=fixture-token GITHUB_TOKEN=fixture-token bash "$TEST_SCRIPT" --build "$ROOT"
  [[ -f "$ROOT/build/docs-versioned/.nojekyll" && ! -e "$ROOT/build/docs-versioned/previous.html" ]]
  local prefix
  for prefix in '' versions/0.0.15 versions/0.0.14; do
    grep -Fq "data-prefix=\"$prefix\"" "$ROOT/build/docs-versioned/$prefix/$PACKAGE_INDEX"
  done
  grep -Fq 'data-version="0.0.15"' "$ROOT/build/docs-versioned/index.html"
  grep -Fq 'data-version="0.0.14"' "$ROOT/build/docs-versioned/versions/0.0.14/index.html"
  [[ "$(cat "$ROOT/build/docs/working.html")" == 'Working-tree site' ]]
  [[ -z "$(find "$ROOT/build" -name 'versioned-docs-*' -print)" ]]
}

test_failed_build_preserves_site() {
  prepare_releases
  touch "$ROOT/library/FAIL_BUILD"
  commit_sources
  git -C "$ROOT" tag -f 0.0.14 >/dev/null
  expect_failure bash "$TEST_SCRIPT" --build "$ROOT"
  [[ "$(cat "$ROOT/build/docs-versioned/previous.html")" == 'Existing site' ]]
  [[ "$(cat "$ROOT/build/docs/working.html")" == 'Working-tree site' ]]
  [[ -z "$(find "$ROOT/build" -name 'versioned-docs-*' -print)" ]]
}

tests=(test_versions_preserve_order test_invalid_versions test_published_releases
  test_export_and_permissions test_unsafe_sources test_missing_sources
  test_build_removes_tokens test_missing_dokka_output test_render_relative_assets
  test_invalid_dokka_pages test_complete_site test_failed_build_preserves_site)
for test in "${tests[@]}"; do
  ROOT="$TEST_ROOT/$test"
  mkdir "$ROOT"
  set +e
  ( set -e; "$test" ) > "$ROOT/test.log" 2>&1
  status=$?
  set -e
  if [[ "$status" != 0 ]]; then
    cat "$ROOT/test.log"
    fail "FAIL: $test"
    exit 1
  fi
  printf 'PASS: %s\n' "$test"
done
printf '%s Bash tests passed.\n' "${#tests[@]}"