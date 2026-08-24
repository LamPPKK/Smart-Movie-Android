#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
scope="${1:-all}"
train_file="$repo_root/release/train.json"
snapshot_manifest="$repo_root/catalog-contract/manifest.json"
contract_manifest="$repo_root/catalog-contract/v2/manifest.json"
openapi_file="$repo_root/catalog-contract/v2/openapi.json"
legacy_contract_manifest="$repo_root/catalog-contract/v1/manifest.json"

case "$scope" in
  all|mobile|kmp) ;;
  *)
    printf 'Usage: %s [all|mobile|kmp]\n' "$0" >&2
    exit 2
    ;;
esac

read_json() {
  python3 -c 'import json,sys; value=json.load(open(sys.argv[1]));
for key in sys.argv[2].split("."): value=value[key]
print(value)' "$1" "$2"
}

sha256() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{ print $1 }'
  else
    sha256sum "$1" | awk '{ print $1 }'
  fi
}

sha256_stream() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 | awk '{ print $1 }'
  else
    sha256sum | awk '{ print $1 }'
  fi
}

fixtures_sha256() {
  local directory="$1"
  (
    cd "$directory"
    while IFS= read -r -d '' fixture; do
      printf '%s  %s\n' "$(sha256 "$fixture")" "$fixture"
    done < <(find . -type f -name '*.json' -print0 | sort -z)
  ) | sha256_stream
}

expected_version="$(read_json "$train_file" train_version)"
expected_code="$(read_json "$train_file" android.version_code)"
expected_contract_version="$(read_json "$train_file" contract_version)"
expected_checksum="$(read_json "$train_file" contract_openapi_sha256)"

manifest_version_name="$(read_json "$train_file" android.version_name)"
snapshot_version="$(read_json "$snapshot_manifest" contract_version)"
snapshot_upstream_commit="$(read_json "$snapshot_manifest" upstream_commit)"
snapshot_checksum="$(read_json "$snapshot_manifest" openapi_sha256)"
contract_version="$(read_json "$contract_manifest" contract_version)"
contract_checksum="$(read_json "$contract_manifest" openapi_sha256)"
snapshot_fixtures_checksum="$(read_json "$snapshot_manifest" fixtures_sha256)"
contract_fixtures_checksum="$(read_json "$contract_manifest" fixtures_sha256)"
actual_checksum="$(sha256 "$openapi_file")"
actual_fixtures_checksum="$(fixtures_sha256 "$repo_root/catalog-contract/v2/fixtures")"
legacy_contract_version="$(read_json "$legacy_contract_manifest" contract_version)"

[[ "$manifest_version_name" == "$expected_version" ]] || { printf 'Android manifest version %s does not match train %s\n' "$manifest_version_name" "$expected_version"; exit 1; }

if [[ "$scope" == "all" || "$scope" == "mobile" ]]; then
  app_version="$(awk -F'"' '/versionName =/ { print $2; exit }' "$repo_root/app/build.gradle.kts")"
  wear_version="$(awk -F'"' '/versionName =/ { print $2; exit }' "$repo_root/wear/build.gradle.kts")"
  app_code="$(awk '/versionCode =/ { print $3; exit }' "$repo_root/app/build.gradle.kts")"
  wear_code="$(awk '/versionCode =/ { print $3; exit }' "$repo_root/wear/build.gradle.kts")"
  app_id="$(awk -F'"' '/applicationId =/ { print $2; exit }' "$repo_root/app/build.gradle.kts")"
  wear_id="$(awk -F'"' '/applicationId =/ { print $2; exit }' "$repo_root/wear/build.gradle.kts")"
  [[ "$app_version" == "$expected_version" ]] || { printf 'App version %s does not match train %s\n' "$app_version" "$expected_version"; exit 1; }
  [[ "$wear_version" == "$expected_version" ]] || { printf 'Wear version %s does not match train %s\n' "$wear_version" "$expected_version"; exit 1; }
  [[ "$app_code" == "$expected_code" ]] || { printf 'App versionCode %s does not match release manifest %s\n' "$app_code" "$expected_code"; exit 1; }
  [[ "$wear_code" == "$expected_code" ]] || { printf 'Wear versionCode %s does not match release manifest %s\n' "$wear_code" "$expected_code"; exit 1; }
  [[ "$app_id" == "com.lamndt.smartmovie" && "$wear_id" == "$app_id" ]] || { printf 'App and Wear application IDs must both be com.lamndt.smartmovie\n'; exit 1; }
fi

if [[ "$scope" == "all" || "$scope" == "kmp" ]]; then
  desktop_version="$(awk -F'"' '/^version =/ { print $2; exit }' "$repo_root/multiplatform/composeApp/build.gradle.kts")"
  package_version="$(awk -F'"' '/packageVersion =/ { print $2; exit }' "$repo_root/multiplatform/composeApp/build.gradle.kts")"
  [[ "$desktop_version" == "$expected_version" ]] || { printf 'Desktop/web version %s does not match train %s\n' "$desktop_version" "$expected_version"; exit 1; }
  [[ "$package_version" == "$expected_version" ]] || { printf 'Desktop packageVersion %s does not match train %s\n' "$package_version" "$expected_version"; exit 1; }
fi
[[ "$snapshot_version" == "$expected_contract_version" && "$contract_version" == "$expected_contract_version" ]] || { printf 'Contract versions do not match release train %s\n' "$expected_contract_version"; exit 1; }
[[ "$snapshot_checksum" == "$expected_checksum" && "$contract_checksum" == "$expected_checksum" && "$actual_checksum" == "$expected_checksum" ]] || { printf 'Vendored contract checksum does not match release train %s\n' "$expected_checksum"; exit 1; }
[[ "$snapshot_fixtures_checksum" == "$contract_fixtures_checksum" && "$actual_fixtures_checksum" == "$contract_fixtures_checksum" ]] || { printf 'Vendored fixture checksum does not match contract manifest %s\n' "$contract_fixtures_checksum"; exit 1; }
[[ "$legacy_contract_version" == "1.0.0" ]] || { printf '/v1 snapshot must remain frozen at 1.0.0 while SmartMovie 2.0 is supported.\n'; exit 1; }
if [[ "${REQUIRE_UPSTREAM_COMMIT:-0}" == "1" && ! "$snapshot_upstream_commit" =~ ^[0-9a-f]{40}$ ]]; then
  printf 'Release candidates require a 40-character canonical upstream commit; snapshot is still bootstrap-only.\n'
  exit 1
fi

printf 'Release train %s, %s scope, and vendored catalog contract %s are consistent.\n' "$expected_version" "$scope" "$expected_contract_version"
