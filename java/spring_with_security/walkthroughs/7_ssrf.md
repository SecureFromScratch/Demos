
# SSRF: Secure Coding

## What is SSRF

**Server-Side Request Forgery (SSRF)** occurs when an attacker can influence a **server-side outbound request**. The server becomes the attacker’s proxy, making requests from a trusted network position with elevated access.

The core problem is **user input influencing the destination** of a backend request.

---

## Why SSRF is dangerous

SSRF is rarely just “fetching a URL”. From the server’s network position, attackers can:

* Reach **internal services** not exposed externally
* Bypass **IP allowlists and firewalls**
* Access **cloud metadata endpoints**
* Pivot into **internal APIs or admin panels**
* Trigger **denial of service** via slow or large responses

SSRF often escalates into full system compromise.

---

## Where SSRF usually appears

Any feature that turns input into an outbound request is suspicious:

* Import-from-URL, link preview, webhook testers
* Image, PDF, or document fetch and conversion
* Feed readers, plugin or package installers
* SSO metadata or key fetching
* Redirect-following HTTP clients

Rule of thumb:

> If the server fetches something “on behalf of the user”, assume SSRF risk.

---

## Threat model (minimal and accurate)

Untrusted input affects one or more of:

* Scheme (http, https, file, etc.)
* Hostname or IP
* Port
* Path
* Redirect targets
* DNS resolution

If that happens, you must defend.

---

## How to fix SSRF (stack-agnostic)

### 1) Eliminate user-controlled URLs (best fix)

**Best practice:** never accept raw URLs.

Instead:

* Accept an **ID, enum, or key**
* Map it to a predefined destination on the server

This removes SSRF entirely by design.

---

### 2) Enforce strict allowlists

If URLs are unavoidable:

* Allow only **explicitly approved hosts**
* Allow only known schemes (usually https)
* Lock ports to expected values

Never rely on blocklists. They are bypassed.

---

### 3) Parse and normalize before validation

* Use a proper URL parser
* Normalize once
* Validate components separately:

  * scheme
  * host
  * port
  * path

Never validate raw strings.

---

### 4) Resolve DNS before connecting

Do not trust hostnames.

Flow:

1. Resolve hostname to IPs
2. Validate **every resolved IP**
3. Use the validated IP for the actual connection

This prevents DNS tricks and rebinding.

---

### 5) Block local and internal IP ranges (mandatory)

Reject requests if the destination IP is:

* Loopback (localhost)
* Private/internal networks
* Link-local addresses
* Reserved or special-use ranges
* IPv6 unique local and loopback ranges

This check must happen **after DNS resolution**, not on the hostname.

Never trust headers or client-provided IPs.

---

### 6) Defend against DNS rebinding

* Resolve once
* Bind the resolved IP to the socket
* Do not re-resolve during redirects or retries

If the IP changes, re-validate.

---

### 7) Control redirects

Redirects are a common SSRF bypass.

Best options:

* Disable redirects entirely
  or
* Re-validate **every redirect target** using the same rules:

  * allowlist
  * DNS resolution
  * IP range checks

---

### 8) Lock down request behavior

Prevent SSRF from becoming DoS:

* Short connect timeout
* Short read timeout
* Max response size
* Max redirect count

Never allow unbounded reads.

---

### 9) Isolate outbound traffic (defense in depth)

Treat SSRF as a **network problem**, not just an app bug:

* Default-deny outbound traffic
* Explicitly allow required destinations only
* Block metadata and internal ranges at the network layer

Application checks + network egress controls together.

---

### 10) Log and alert

* Log rejected destinations and resolution results
* Alert on repeated failures or probing patterns

SSRF attempts often look like scanning.

---

## Secure request flow (reference)

1. Accept input
2. Parse URL
3. Normalize
4. Check scheme, host, port against allowlist
5. Resolve DNS
6. Reject local/private/reserved IPs
7. Bind resolved IP
8. Apply timeouts and limits
9. Fetch
10. Re-validate on redirects

If any step fails, **abort**.

---

## One-line rule for developers

> If user input can influence where the server connects, you must either remove that control or strictly constrain it before the request is made.

---

