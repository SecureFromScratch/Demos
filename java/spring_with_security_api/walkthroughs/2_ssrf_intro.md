
# SSRF (Server-Side Request Forgery)
## What it is, why it matters, and how to protect against it

## 1. What is SSRF?

**Server-Side Request Forgery (SSRF)** is a vulnerability where an attacker causes **your server** to make network requests to destinations chosen by the attacker.

Instead of connecting directly to a target, the attacker abuses your backend as a proxy.

**Core idea:**
> Untrusted input influences where the server connects.

---

## 2. Why SSRF is dangerous

Because the request originates from **inside your infrastructure**, it can bypass:
- Firewalls
- Network ACLs
- Authentication boundaries

### Typical attacker goals
- Access internal services (`localhost`, private IPs)
- Reach cloud metadata services (credentials, tokens)
- Scan internal network ports
- Pivot to RCE or data exfiltration

### Common SSRF targets
- `127.0.0.1`, `localhost`
- `10.0.0.0/8`
- `172.16.0.0/12`
- `192.168.0.0/16`
- `169.254.169.254` (cloud metadata)
- IPv6 loopback, link-local, ULA ranges

---

## 3. Where SSRF appears in real applications

SSRF usually hides in *legitimate features*:

- “Upload image from URL”
- Webhooks and callbacks
- URL previews
- PDF / thumbnail generation
- Import-from-URL features
- OAuth / SSO integrations
- LLM tools that fetch URLs

**Rule of thumb:**
> If user input affects **where** the server connects, SSRF is possible.

---

## 4. The core security principle

> **Never allow untrusted input to decide network destinations.**

SSRF protection is about **control**, not filtering.
Blocking “bad” IPs alone is insufficient.

---

## 5. The Allowlist (Allowed Domains)

### Why an allowlist is mandatory
The strongest SSRF defense is a **strict allowlist**:
- Only known, trusted destinations are reachable
- Everything else is rejected by default

This turns SSRF from an open capability into a tightly scoped feature.

---

### What to allowlist
Allow **exact hostnames** that your application genuinely needs.

Examples:
- `www.pexels.com`
- `www.istockphoto.com`
- `api.partner.example`

---

### What NOT to allowlist
Never allow:
- IP addresses
- `localhost`
- Internal DNS names
- Cloud metadata endpoints
- Arbitrary user-provided domains

Avoid:
- Wildcards (`*.example.com`)
- Regex-based host checks
- “Blocklist” approaches

---

### Where to store the allowlist
Store allowlists in **configuration**, not code.

Example (`application.properties`):
```properties
security.trusted-image-domains=www.pexels.com,www.istockphoto.com
````

Benefits:

* Environment-specific control
* No redeploy required for changes
* Clear security boundary

---

### How host matching must work

1. Parse the URL
2. Extract the **host**
3. Normalize (lowercase, trim)
4. Compare against the allowlist

Accepted:

* `https://www.pexels.com/image.jpg`

Rejected:

* `https://pexels.com.evil.com`
* `http://user@www.pexels.com`
* `https://127.0.0.1`

**Recommendation:**

> Exact match by default. Subdomains only if explicitly required.

---

## 6. URL parsing and validation

Before any network call:

* Enforce **HTTPS only**
* Reject:

  * Userinfo (`user@host`)
  * Non-HTTP schemes
  * Missing host
* Never validate URLs using string operations

---

## 7. DNS resolution and IP verification

After allowlist check:

* Resolve the hostname server-side
* Verify **all resolved IPs**
* Reject:

  * Loopback addresses
  * Private IP ranges
  * Link-local addresses
  * Multicast addresses

**Why:** DNS rebinding attacks.

---

## 8. DNS pinning (advanced but important)

* Resolve DNS once
* Force the HTTP client to use only those IPs
* Reject any unexpected DNS lookup

Prevents:

* Time-of-check vs time-of-use attacks
* Post-validation DNS manipulation

---

## 9. Redirect handling

* Disable HTTP and HTTPS redirects
* Attackers often redirect from allowed domains to internal targets

---

## 10. Limit the blast radius

Even trusted destinations can fail or misbehave.

Apply:

* Short connection and read timeouts
* Maximum response size limits
* Strict `Content-Type` validation
* No streaming into dangerous parsers

---

## 11. Network-level defenses (defense in depth)

* Egress firewall rules
* Explicitly block metadata IPs
* Minimal network permissions per service

These controls **do not replace** application-level SSRF defenses.

---

## 12. Mental model for developers

Ask one question during design and code review:

**“Can user input influence where my server connects?”**

If yes:

* Assume SSRF
* Require an allowlist
* Apply layered defenses

---

## 13. Key takeaways

* SSRF is a **capability leak**, not a parsing bug
* URL validation alone is insufficient
* Allowlists are the primary control
* Safe SSRF protection is **explicit, layered, and defensive**

