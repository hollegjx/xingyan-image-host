#!/bin/sh
# Read-only audit. Reports are fully redacted and never written into the repository.
set -eu
umask 077
ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
BIN=${GITLEAKS_BIN:-gitleaks}
command -v "$BIN" >/dev/null 2>&1 || {
  echo 'Install official Gitleaks, or set GITLEAKS_BIN to its verified executable.' >&2
  exit 2
}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/xingyan-security.XXXXXX")
STATUS=0
"$BIN" dir "$ROOT" --config "$ROOT/.gitleaks.toml" --redact=100 \
  --no-banner --no-color --ignore-gitleaks-allow --gitleaks-ignore-path /dev/null \
  --report-format json --report-path "$OUT/worktree.json" || STATUS=$?
"$BIN" git "$ROOT" --config "$ROOT/.gitleaks.toml" --log-opts='--all --full-history' \
  --redact=100 --no-banner --no-color --ignore-gitleaks-allow --gitleaks-ignore-path /dev/null \
  --report-format json --report-path "$OUT/history.json" || STATUS=$?
printf 'Redacted review reports: %s\n' "$OUT"
exit "$STATUS"
