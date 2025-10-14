cat > /tmp/swap_contents.sh <<'BASH'
#!/usr/bin/env bash
set -euo pipefail

STORAGE="/tmp/secure-downloads-demo"
TARGET="$STORAGE/target.txt"
REAL="$STORAGE/target-real"
ATTACK="$STORAGE/target-attack"
TMP_ATTACK="$STORAGE/._attack_tmp"

# ensure attack file exists at start
if [ ! -f "$ATTACK" ]; then
  echo "ATTACKER_CONTENT" > "$ATTACK"
fi

while true; do
  # ensure attacker file exists (recreate if needed)
  if [ ! -f "$ATTACK" ]; then
    echo "recreating missing attack file"
    echo "ATTACKER_CONTENT" > "$ATTACK"
  fi

  # atomically move attack into place (mv across same FS is atomic)
  mv -f "$ATTACK" "$TARGET"

  # small window
  sleep 0.02

  # restore normal content (atomic replace)
  cp -f "$REAL" "$TARGET"

  # tiny pause to yield CPU
  sleep 0.02
done
BASH

chmod +x /tmp/swap_contents.sh
