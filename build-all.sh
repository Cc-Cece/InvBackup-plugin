#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIST_DIR="${ROOT_DIR}/dist"

build_profile() {
  local profile="$1"
  local output="$2"

  echo
  echo "Building profile: ${profile}"
  "${ROOT_DIR}/gradlew" clean build "-PbuildTarget=${profile}" -x test

  local jar
  jar="$(find "${ROOT_DIR}/build/libs" -maxdepth 1 -type f -name '*.jar' | head -n 1)"
  if [[ -z "${jar}" ]]; then
    echo "No jar produced for profile: ${profile}" >&2
    exit 1
  fi

  cp "${jar}" "${DIST_DIR}/${output}"
  echo "Created: ${DIST_DIR}/${output}"
}

echo "========================================"
echo "InvBackup dual-profile build"
echo "========================================"

rm -rf "${DIST_DIR}"
mkdir -p "${DIST_DIR}"

build_profile "paper-1.18-1.20" "InvBackup-paper-1.18-1.20.jar"
build_profile "paper-1.21-plus" "InvBackup-paper-1.21-plus.jar"

echo
echo "Done. Artifacts:"
ls -1 "${DIST_DIR}"/*.jar
