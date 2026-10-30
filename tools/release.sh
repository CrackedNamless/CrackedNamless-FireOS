#!/usr/bin/env bash
# Publishes a GitHub release: signed APK + source zip + notes taken from CHANGELOG.md.
# Usage: GITHUB_TOKEN=<fine-grained token with Contents: read/write> tools/release.sh
# Before running: bump versionCode/versionName in app/build.gradle, add a "## [x.y.z] - date" section to
# CHANGELOG.md, commit everything. The script refuses to run on a dirty tree.
# Optional: GRADLE (default ./gradlew) and GRADLE_ARGS (extra Gradle arguments).
set -euo pipefail

REPO=CrackedNamless/CrackedNamless-FireOS
: "${GITHUB_TOKEN:?set GITHUB_TOKEN}"
GRADLE="${GRADLE:-./gradlew}"
cd "$(dirname "$0")/.."

[ -z "$(git status --porcelain)" ] || { echo "Working tree is not clean: commit first"; exit 1; }

VERSION="$(sed -n "s/.*versionName '\(.*\)'.*/\1/p" app/build.gradle | head -1)"
TAG="v$VERSION"
git rev-parse "$TAG" >/dev/null 2>&1 && { echo "Tag $TAG already exists: bump versionName"; exit 1; }

NOTES="$(awk -v head="## [$VERSION]" '
  index($0, head) == 1 { on = 1; next }
  on && substr($0, 1, 4) == "## [" { exit }
  on { print }' CHANGELOG.md)"
NOTES="$(printf '%s\n' "$NOTES" | sed '/./,$!d')"
[ -n "$NOTES" ] || { echo "No CHANGELOG.md section for $VERSION"; exit 1; }

# shellcheck disable=SC2086
$GRADLE assembleRelease ${GRADLE_ARGS:-}
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/CrackedNamless-FireOS.apk
git archive --format=zip --prefix="CrackedNamless-FireOS-$TAG/" -o "dist/CrackedNamless-FireOS-src-$TAG.zip" HEAD

AUTH="$(printf 'x-access-token:%s' "$GITHUB_TOKEN" | base64 | tr -d '\n')"
git -c http.extraheader="Authorization: Basic $AUTH" push origin HEAD:main
git tag "$TAG"
git -c http.extraheader="Authorization: Basic $AUTH" push origin "$TAG"

json_escape() {
  sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/\t/\\t/g' -e 's/\r//g' | awk 'BEGIN { ORS = "\\n" } { print }'
}
BODY="{\"tag_name\":\"$TAG\",\"name\":\"CrackedNamless-FireOS $VERSION\",\"body\":\"$(printf '%s\n' "$NOTES" | json_escape)\"}"

RESPONSE="$(curl -fsS -X POST "https://api.github.com/repos/$REPO/releases" \
  -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" -d "$BODY")"
ID="$(printf '%s' "$RESPONSE" | grep -o '"id": *[0-9]*' | head -1 | grep -o '[0-9]*')"
[ -n "$ID" ] || { echo "Could not create the release: $RESPONSE"; exit 1; }

upload() {
  curl -fsS -X POST "https://uploads.github.com/repos/$REPO/releases/$ID/assets?name=$(basename "$1")" \
    -H "Authorization: Bearer $GITHUB_TOKEN" -H "Content-Type: $2" --data-binary "@$1" >/dev/null
  echo "Uploaded $(basename "$1")"
}
upload dist/CrackedNamless-FireOS.apk application/vnd.android.package-archive
upload "dist/CrackedNamless-FireOS-src-$TAG.zip" application/zip

echo "Released $TAG: https://github.com/$REPO/releases/tag/$TAG"
