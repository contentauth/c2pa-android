#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PACKAGE_INDEX=c2pa-android/org.contentauth.c2pa/index.html

fail() {
  printf '%s\n' "$*" >&2
  return 1
}

load_versions() {
  local filename="$1"
  jq -e --arg pattern '^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9][A-Za-z0-9.-]*)?\z' '
    if type != "object" then false
    elif (.versions | type) != "array" then false
    else .versions | length > 0 and
      all(.[]; if type == "string" then test($pattern) else false end) and
      (length == (unique | length))
    end
  ' "$filename" >/dev/null || { fail "Documentation versions must be unique release tags in a non-empty list."; return 1; }
  jq -r '.versions[]' "$filename"
}

verify_release() {
  local tag="$1" release
  release="$(gh api "repos/contentauth/c2pa-android/releases/tags/$tag")" || return 1
  printf '%s' "$release" | jq -e --arg tag "$tag" '
    type == "object" and .tag_name == $tag and .draft == false
  ' >/dev/null || { fail "$tag is not a published GitHub release."; return 1; }
}

export_release() {
  local root="$1" tag="$2" destination="$3"
  git -C "$root" archive --format=tar "refs/tags/$tag" | tar -xf - -C "$destination"
}

build_docs() {
  local stage="$1"
  (
    cd "$stage"
    unset GH_TOKEN GITHUB_TOKEN
    ./gradlew --no-daemon --console=plain :library:dokkaGeneratePublicationHtml --rerun-tasks
  ) || return 1
  [[ -f "$stage/build/docs/index.html" && -f "$stage/build/docs/$PACKAGE_INDEX" ]] || {
    fail "Dokka did not generate the library and C2PA package landing pages."; return 1;
  }
}

render_docs() {
  local source="$1" output="$2" tag="$3" versions="$4" prefix="$5"
  local page relative directory assets additions escaped_tag escaped_versions escaped_prefix
  mkdir -p "$output"
  cp -R "$source/." "$output/"
  escaped_tag="$(printf '%s' "$tag" | jq -Rr '@html')"
  escaped_versions="$(printf '%s' "$versions" | jq -Rr '@html')"
  escaped_prefix="$(printf '%s' "$prefix" | jq -Rr '@html')"
  find "$output" -type f -name '*.html' -print0 > "$output.pages"
  while IFS= read -r -d '' page; do
    relative="${page#"$output/"}"
    [[ "$relative" != navigation.html ]] || continue
    assets=""
    directory="$relative"
    while [[ "$directory" == */* ]]; do
      assets="../$assets"
      directory="${directory#*/}"
    done
    additions="<link rel=\"stylesheet\" href=\"${assets}doc-version-selector.css\">
<script defer src=\"${assets}doc-version-selector.js\" data-version=\"$escaped_tag\" data-versions=\"$escaped_versions\" data-prefix=\"$escaped_prefix\"></script>"
    DOC_VERSION_ADDITIONS="$additions" awk '
      BEGIN { additions = ENVIRON["DOC_VERSION_ADDITIONS"] }
      {
        if (index($0, "id=\"library-version\"")) slot = 1
        position = index($0, "</head>")
        if (position) {
          head = 1
          printf "%s%s\n%s\n", substr($0, 1, position - 1), additions, substr($0, position)
        } else print
      }
      END { if (!head || !slot) exit 1 }
    ' "$page" > "$page.tmp" || { fail "Dokka page is missing its head or version slot: $relative"; return 1; }
    mv "$page.tmp" "$page"
  done < "$output.pages"
  rm "$output.pages"
  cp "$ROOT/.github/scripts/doc-version-selector.js" "$ROOT/.github/scripts/doc-version-selector.css" "$output/"
}

verify_site() {
  local site="$1" default="$2" tag page
  shift 2
  for tag in "$@"; do
    for page in index.html "$PACKAGE_INDEX"; do
      [[ -f "$site/versions/$tag/$page" ]] || { fail "The site is missing the $tag page $page."; return 1; }
    done
  done
  for page in index.html "$PACKAGE_INDEX"; do
    grep -Fq "data-version=\"$default\"" "$site/$page" || {
      fail "The default documentation at $page is not $default."; return 1;
    }
  done
}

build_site() (
  set -euo pipefail
  local versions=() tag versions_json workspace output stage default_docs=""
  mkdir -p "$ROOT/build"
  output="$ROOT/build/docs-versioned"
  workspace="$(mktemp -d "$ROOT/build/versioned-docs-XXXXXX")"
  trap 'status=$?; if [[ -d "$workspace/previous" && ! -e "$output" ]]; then mv "$workspace/previous" "$output" || status=1; fi; rm -rf -- "$workspace"; exit "$status"' EXIT
  load_versions "$ROOT/docs/versions.json" > "$workspace/versions"
  while IFS= read -r tag; do versions+=("$tag"); done < "$workspace/versions"
  versions_json="$(jq -c '.versions' "$ROOT/docs/versions.json")"
  for tag in "${versions[@]}"; do verify_release "$tag"; done
  for tag in "${versions[@]}"; do
    stage="$workspace/$tag"
    mkdir "$stage"
    export_release "$ROOT" "$tag" "$stage"
    build_docs "$stage"
    [[ "$tag" != "${versions[0]}" ]] || default_docs="$stage/build/docs"
    render_docs "$stage/build/docs" "$workspace/site/versions/$tag" "$tag" "$versions_json" "versions/$tag"
  done
  render_docs "$default_docs" "$workspace/default" "${versions[0]}" "$versions_json" ""
  cp -R "$workspace/default/." "$workspace/site/"
  touch "$workspace/site/.nojekyll"
  verify_site "$workspace/site" "${versions[0]}" "${versions[@]}"
  if [[ -e "$output" ]]; then mv "$output" "$workspace/previous"; fi
  mv "$workspace/site" "$output"
  printf 'Versioned Dokka documentation generated at: %s\n' "$output"
)

build_site