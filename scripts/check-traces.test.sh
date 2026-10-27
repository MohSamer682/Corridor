#!/usr/bin/env bash
set -euo pipefail

script="$(cd "$(dirname "$0")" && pwd)/check-traces.sh"
repo="$(mktemp -d)"
trap 'rm -rf "$repo"' EXIT

git -C "$repo" init -q
git -C "$repo" config user.name "Test"
git -C "$repo" config user.email "test@example.com"
printf 'hello\n<modelVersion>4.0.0</modelVersion>\n' > "$repo/file.txt"
git -C "$repo" add file.txt
git -C "$repo" commit -q -m "feat: add thing"
git -C "$repo" commit -q --allow-empty -m "feat: add thing" -m "Co-Authored-By: Bot <bot@example.com>"

expect_exit() {
  local expected=$1 range=$2 actual=0
  (cd "$repo" && bash "$script" "$range" > /dev/null) || actual=$?
  if [[ $actual -ne $expected ]]; then
    echo "FAIL: range $range exited $actual, expected $expected"
    exit 1
  fi
}

expect_exit 0 "$(git -C "$repo" rev-list --max-parents=0 HEAD)"
expect_exit 1 HEAD~1..HEAD
echo "PASS"
