#!/usr/bin/env bash
# Uploads the built jar to Modrinth or CurseForge.
#
# Usage: publish.sh modrinth|curseforge
# Env:   JAR, VERSION, CHANGELOG (file), DRY_RUN (true/false),
#        MODRINTH_TOKEN / CURSEFORGE_TOKEN
#
# With DRY_RUN=true nothing is uploaded: the script checks the token,
# resolves everything the upload needs and prints the request it would send.
set -euo pipefail

MODRINTH_PROJECT=iPz0KUii
CURSEFORGE_PROJECT=1590868
MC_VERSION=26.1.2
LOADER=neoforge
RELEASE_TYPE=release
UA="ImEden6/ThermaDucts release workflow"

target=${1:?usage: publish.sh modrinth|curseforge}
: "${JAR:?JAR not set}" "${VERSION:?VERSION not set}" "${CHANGELOG:?CHANGELOG not set}"
DRY_RUN=${DRY_RUN:-true}
[ -f "$JAR" ] || { echo "::error::Jar not found: $JAR"; exit 1; }
[ -f "$CHANGELOG" ] || { echo "::error::Changelog not found: $CHANGELOG"; exit 1; }

dry() { [ "$DRY_RUN" = "true" ]; }
# A missing token is only tolerated in a local dry run; on GitHub (CI=true) it is an error.
local_dry() { dry && [ "${CI:-}" != "true" ]; }

modrinth() {
  local api=https://api.modrinth.com/v2
  local auth=()
  if [ -n "${MODRINTH_TOKEN:-}" ]; then
    auth=(-H "Authorization: $MODRINTH_TOKEN")
    local user status
    user=$(curl -sS -A "$UA" "${auth[@]}" -w '\n%{http_code}' "$api/user")
    status=${user##*$'\n'}
    user=${user%$'\n'*}
    if [ "$status" != 200 ]; then
      echo "::error::Modrinth rejected MODRINTH_TOKEN (HTTP $status): $user"
      echo "The token needs the 'Create versions' and 'Read user data' scopes."
      exit 1
    fi
    local user_id username
    user_id=$(jq -r .id <<<"$user")
    username=$(jq -r .username <<<"$user")
    curl -fsS -A "$UA" "$api/project/$MODRINTH_PROJECT/members" \
      | jq -e --arg id "$user_id" 'any(.[]; .user.id == $id)' >/dev/null \
      || { echo "::error::Modrinth user $username is not a member of $MODRINTH_PROJECT"; exit 1; }
    echo "Modrinth token OK (user $username)"
  elif local_dry; then
    echo "::warning::MODRINTH_TOKEN not set; skipping token check"
  else
    echo "::error::MODRINTH_TOKEN not set. Add it as a secret on the release environment."; exit 1
  fi

  if curl -fsS -A "$UA" "$api/project/$MODRINTH_PROJECT/version" \
      | jq -e --arg v "$VERSION" 'any(.[]; .version_number == $v)' >/dev/null; then
    echo "Modrinth already has version $VERSION; nothing to do"
    return 0
  fi

  local data
  data=$(jq -n \
    --arg project "$MODRINTH_PROJECT" --arg version "$VERSION" \
    --arg mc "$MC_VERSION" --arg loader "$LOADER" --arg type "$RELEASE_TYPE" \
    --rawfile changelog "$CHANGELOG" \
    '{project_id: $project, version_number: $version, name: ("ThermaDucts " + $version),
      changelog: $changelog, dependencies: [], game_versions: [$mc], loaders: [$loader],
      version_type: $type, featured: true, status: "listed",
      file_parts: ["file"], primary_file: "file"}')

  if dry; then
    echo "[dry run] Would POST $api/version with $(basename "$JAR") and:"
    jq 'del(.changelog)' <<<"$data"
    return 0
  fi

  curl -fsS -A "$UA" "${auth[@]}" -X POST "$api/version" \
    --form-string "data=$data" -F "file=@$JAR" | jq '{id, version_number}'
  echo "Published $VERSION to Modrinth"
}

curseforge() {
  local api=https://minecraft.curseforge.com/api
  if [ -z "${CURSEFORGE_TOKEN:-}" ]; then
    if local_dry; then
      echo "::warning::CURSEFORGE_TOKEN not set; CurseForge needs it even to look up versions, skipping"
      return 0
    fi
    echo "::error::CURSEFORGE_TOKEN not set. Add it as a secret on the release environment."; exit 1
  fi
  local auth=(-H "X-Api-Token: $CURSEFORGE_TOKEN")

  local types versions
  types=$(curl -fsS -A "$UA" "${auth[@]}" "$api/game/version-types") \
    || { echo "::error::CurseForge rejected CURSEFORGE_TOKEN"; exit 1; }
  versions=$(curl -fsS -A "$UA" "${auth[@]}" "$api/game/versions")
  echo "CurseForge token OK"

  # Pick exactly one version ID by name, limited to version types whose slug matches.
  resolve() {
    local name=$1 type_pattern=$2 ids
    ids=$(jq -r --argjson types "$types" --arg name "$name" --arg pat "$type_pattern" '
      [$types[] | select(.slug | test($pat)) | .id] as $tids
      | [.[] | select(.name == $name and (.gameVersionTypeID as $t | $tids | index($t))) | .id]
      | unique | .[]' <<<"$versions")
    if [ "$(grep -c . <<<"$ids")" != 1 ]; then
      echo "::error::Expected one CurseForge version named '$name' (types /$type_pattern/), got: ${ids:-none}" >&2
      exit 1
    fi
    echo "$ids"
  }
  local mc neoforge client server
  mc=$(resolve "$MC_VERSION" '^minecraft-')
  neoforge=$(resolve NeoForge '^modloader$')
  client=$(resolve Client '^environment$')
  server=$(resolve Server '^environment$')

  local metadata
  metadata=$(jq -n \
    --arg name "thermaducts-$MC_VERSION-NeoForge-$VERSION" --arg type "$RELEASE_TYPE" \
    --argjson ids "[$mc,$neoforge,$client,$server]" --rawfile changelog "$CHANGELOG" \
    '{changelog: $changelog, changelogType: "markdown", displayName: $name,
      gameVersions: $ids, releaseType: $type}')

  if dry; then
    echo "[dry run] Would POST $api/projects/$CURSEFORGE_PROJECT/upload-file with $(basename "$JAR") and:"
    jq 'del(.changelog)' <<<"$metadata"
    echo "(game versions: $MC_VERSION=$mc NeoForge=$neoforge Client=$client Server=$server)"
    return 0
  fi

  curl -fsS -A "$UA" "${auth[@]}" -X POST "$api/projects/$CURSEFORGE_PROJECT/upload-file" \
    --form-string "metadata=$metadata" -F "file=@$JAR"
  echo
  echo "Published $VERSION to CurseForge"
}

case "$target" in
  modrinth) modrinth ;;
  curseforge) curseforge ;;
  *) echo "usage: publish.sh modrinth|curseforge" >&2; exit 2 ;;
esac
