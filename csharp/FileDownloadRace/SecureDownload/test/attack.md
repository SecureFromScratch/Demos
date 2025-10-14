# Race Condtion on file download functionality


Because the check that validates the file path and the actual file open/download 
aren’t done atomically, an attacker can change what the server opens by replacing
the directory entry that the server will later use (due to other vulnerabilities),
and the server may end up returning the top-secret file.

In this demo we swap the content by replacing the deployed pathname:
the attacker briefly places a symlink (or an atomically renamed hardlink) at `target.txt` 
that points to the protected secret, then restores the real file. That causes new `open(path)` 
calls to see the attacker-controlled inode during the vulnerable window.

In real-world scenarios this can happen in different ways. For example, an attacker 
might craft a ZIP upload that creates symlinks or performs path traversal.


## 1) Create the storage and test files using the test/create_files.sh script

---
## 2) Check if you can use the api to download a file

```bash
curl -v http://localhost:5007/api/files/download/target.txt -o test/output/target.txt
```



## 2) Run the swapper script using the test/swap_contents.sh script

Changing it once can be enough if the privileged reader 
opens the path at that exact moment, 
but in practice you usually want the attacker to flip the name multiple times 
so the race actually hits and then restore the real file to avoid easy detection 
or breaking normal operation.

---
## 3) Run the curl a few times

Now when the code is fixed you will not see the same results :)

curl -sS -D - "http://localhost:5007/api/files/download/target.txt" -o -

---

## 5) Cleanup when done

run the test/clean.sh script


