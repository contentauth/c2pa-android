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

published_releases() {
  gh api --paginate "repos/contentauth/c2pa-android/releases?per_page=100" \
    --jq '.[] | select(.draft == false) | .tag_name'
}

# The release workflow lists a stable release and pushes its tag before the release is published,
# and leaves it a draft unless asked to publish it. Until then the release is left out; publishing
# it fires the release trigger in docs.yml, which rebuilds the site with it. The releases endpoint
# answers a draft's tag with a 404, so a draft is recognised as a listed tag with no published
# release. A listed version that is not a tag at all is an error.
select_versions() {
  local root="$1" published="$2" tag
  while IFS= read -r tag; do
    if grep -Fxq -- "$tag" "$published"; then
      printf '%s\n' "$tag"
    elif git -C "$root" rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
      printf 'Skipping %s: its GitHub release is not published.\n' "$tag" >&2
    else
      fail "$tag is listed in docs/versions.json but is not a tag."; return 1
    fi
  done
}

export_release() {
  local root="$1" tag="$2" destination="$3"
  git -C "$root" archive --format=tar "refs/tags/$tag" | tar -xf - -C "$destination"
}

build_docs() {
  local stage="$1"
  (
    unset GH_TOKEN GITHUB_TOKEN
    make -C "$stage" docs
  ) || return 1
  [[ -f "$stage/build/docs/index.html" && -f "$stage/build/docs/$PACKAGE_INDEX" ]] || {
    fail "Dokka did not generate the library and C2PA package landing pages."; return 1;
  }
}

render_docs() {
  local source="$1" output="$2" tag="$3" versions="$4" prefix="$5"
  local page relative directory assets additions
  mkdir -p "$output"
  cp -R "$source/." "$output/"
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
<script defer src=\"${assets}doc-version-selector.js\" data-version=\"$tag\" data-versions=\"$versions\" data-prefix=\"$prefix\"></script>"
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
  local versions=() tag workspace output stage default_docs=""
  mkdir -p "$ROOT/build"
  output="$ROOT/build/docs-versioned"
  workspace="$(mktemp -d "$ROOT/build/versioned-docs-XXXXXX")"
  trap 'status=$?; if [[ -d "$workspace/previous" && ! -e "$output" ]]; then mv "$workspace/previous" "$output" || status=1; fi; rm -rf -- "$workspace"; exit "$status"' EXIT
  load_versions "$ROOT/docs/versions.json" > "$workspace/listed"
  published_releases > "$workspace/published"
  select_versions "$ROOT" "$workspace/published" < "$workspace/listed" > "$workspace/versions"
  while IFS= read -r tag; do versions+=("$tag"); done < "$workspace/versions"
  (( ${#versions[@]} > 0 )) || { fail "None of the releases in docs/versions.json is published."; return 1; }
  for tag in "${versions[@]}"; do
    stage="$workspace/$tag"
    mkdir "$stage"
    export_release "$ROOT" "$tag" "$stage"
    build_docs "$stage"
    [[ "$tag" != "${versions[0]}" ]] || default_docs="$stage/build/docs"
    render_docs "$stage/build/docs" "$workspace/site/versions/$tag" "$tag" "${versions[*]}" "versions/$tag"
  done
  render_docs "$default_docs" "$workspace/default" "${versions[0]}" "${versions[*]}" ""
  cp -R "$workspace/default/." "$workspace/site/"
  touch "$workspace/site/.nojekyll"
  verify_site "$workspace/site" "${versions[0]}" "${versions[@]}"
  if [[ -e "$output" ]]; then mv "$output" "$workspace/previous"; fi
  mv "$workspace/site" "$output"
  printf 'Versioned Dokka documentation generated at: %s\n' "$output"
)

build_site
