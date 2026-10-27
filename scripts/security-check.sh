#!/bin/sh
# Read-only audit; secret values are redacted and reports stay outside the repo.
set -eu
umask 077
ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
BIN=${GITLEAKS_BIN:-gitleaks}
command -v "$BIN" >/dev/null 2>&1 || {
  echo 'Install Gitleaks, or set GITLEAKS_BIN to a verified executable.' >&2
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
