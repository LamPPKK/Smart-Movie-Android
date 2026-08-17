#!/usr/bin/env bash

set -u

failures=0

pass() {
  printf 'PASS  %s\n' "$1"
}

fail() {
  printf 'FAIL  %s\n' "$1"
  failures=$((failures + 1))
}

require_command() {
  if command -v "$1" >/dev/null 2>&1; then
    pass "$1 is available"
  else
    fail "$1 is required"
  fi
}

java_binary() {
  local configured_home="$1"
  if [[ -n "$configured_home" && -x "$configured_home/bin/java" ]]; then
    printf '%s' "$configured_home/bin/java"
  else
    command -v java 2>/dev/null || true
  fi
}

java_major() {
  local binary="$1"
  local raw
  raw="$("$binary" -version 2>&1 | head -n 1 | sed -E 's/.*"([^"]+)".*/\1/')"
  if [[ "$raw" == 1.* ]]; then
    raw="${raw#1.}"
  fi
  printf '%s' "${raw%%.*}"
}

check_java() {
  local label="$1"
  local configured_home="$2"
  local expected="$3"
  local policy="$4"
  local binary
  binary="$(java_binary "$configured_home")"
  if [[ -z "$binary" ]]; then
    fail "$label requires JDK $expected${policy:+ ($policy)}"
    return
  fi
  local major
  major="$(java_major "$binary")"
  if [[ "$major" =~ ^[0-9]+$ ]] && { [[ "$policy" == "exact" && "$major" == "$expected" ]] || [[ "$policy" == "minimum" && "$major" -ge "$expected" ]]; }; then
    pass "$label uses JDK $major ($binary)"
  else
    fail "$label requires JDK $expected ($policy); detected '${major:-unknown}' at $binary"
  fi
}

require_command python3
require_command git

android_java_home="${ANDROID_JAVA_HOME:-${JAVA_HOME:-}}"
kmp_java_home="${KMP_JAVA_HOME:-${JAVA_HOME:-}}"
check_java "Android native build" "$android_java_home" 17 exact
check_java "Compose desktop/web build" "$kmp_java_home" 21 minimum

sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk_root" && "$(uname -s)" == "Darwin" && -d "$HOME/Library/Android/sdk" ]]; then
  sdk_root="$HOME/Library/Android/sdk"
fi
if [[ -n "$sdk_root" && -d "$sdk_root" ]]; then
  pass "Android SDK is available at $sdk_root"
  [[ -d "$sdk_root/platforms/android-36" ]] && pass "Android platform 36 is installed" || fail "Android platform 36 is required"
  [[ -d "$sdk_root/build-tools/36.0.0" ]] && pass "Android Build Tools 36.0.0 are installed" || fail "Android Build Tools 36.0.0 are required"
else
  fail "ANDROID_SDK_ROOT or ANDROID_HOME must point to Android SDK 36"
fi

[[ -x ./gradlew ]] && pass "Android Gradle wrapper is executable" || fail "./gradlew is missing or not executable"
[[ -x ./multiplatform/gradlew ]] && pass "Compose Gradle wrapper is executable" || fail "./multiplatform/gradlew is missing or not executable"

if (( failures > 0 )); then
  printf '\nDoctor found %d blocking issue(s). No system changes were made.\n' "$failures"
  exit 1
fi

printf '\nSmartMovie Android/desktop/web development environment is ready.\n'
