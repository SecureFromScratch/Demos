# Tutorial: Preventing SSRF in Spring Boot with a Trusted Image Fetcher

This tutorial explains how to safely implement a “download image from URL” feature in Spring Boot, using the `TrustedImageFetcher` snippet as the core building block.

---

## 1) The Goal

Allow users to provide an image URL, but only if:
1. The URL uses HTTPS
2. The host is in a trusted allowlist
3. DNS resolution does not return private or local IPs
4. No redirects are followed
5. The response is really an image
6. The response size is bounded

Your `TrustedImageFetcher` implements these layers.

---

## 2) Configuration (application.properties)

Define two security controls in config:
- trusted domain allowlist
- maximum bytes allowed to download

```properties
security.trusted-domains=www.pexels.com,www.istockphoto.com
security.max-upload-bytes=10485760
````

Notes:

* Do not include `/` in domains (hostnames never include `/`)
* Keep the list small and explicit

---

## 3) Properties binding: SecurityProperties

Spring binds `security.*` keys into this class.

```java
package com.example.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    private Set<String> trustedDomains = new HashSet<>();
    private long maxUploadBytes = 10485760;

    public Set<String> getTrustedDomains() {
        return trustedDomains;
    }

    public void setTrustedDomains(Set<String> trustedDomains) {
        this.trustedDomains = (trustedDomains == null) ? new HashSet<>() : trustedDomains;
    }

    public long getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public void setMaxUploadBytes(long maxUploadBytes) {
        this.maxUploadBytes = maxUploadBytes;
    }
}
```

Why this matters:

* `trustedDomains` is always initialized
* `maxUploadBytes` has a safe default
* Binding matches the property names:

  * `security.trusted-domains`
  * `security.max-upload-bytes`

---

## 4) The Secure Fetcher: What each layer does

Your `TrustedImageFetcher` is a Spring `@Component`, so it can be injected into services.

### Layer A: Enforce HTTPS, reject userinfo

```java
URI uri = parseHttpsUrl(userUrl);
```

This blocks:

* `http://...` (no TLS)
* `https://user@host/...` (userinfo confusion)

### Layer B: Host allowlist (trusted domains)

```java
requireTrustedHost(host);
```

This prevents “fetch anything on the internet”.

Key idea:

> SSRF defense starts with restricting destinations, not filtering IPs.

### Layer C: DNS resolve and block internal/private IP ranges

```java
List<InetAddress> verifiedIps = resolveAndVerifyIps(host);
```

This blocks:

* loopback
* private IPv4 ranges
* link-local ranges
* IPv6 ULA + link-local

This prevents:

* direct private IP SSRF
* many DNS-based tricks

### Layer D: DNS pinning

```java
.dns(new PinnedDns(host, verifiedIps))
```

This prevents time-of-check/time-of-use DNS changes:

* You resolve once
* You force the HTTP client to use only those IPs
* Any unexpected hostname lookup fails

### Layer E: No redirects

```java
.followRedirects(false)
.followSslRedirects(false)
```

This blocks common SSRF bypass patterns:

* allowed domain → redirects to internal IP

### Layer F: Enforce response type and size

```java
Content-Type must start with image/
contentLength <= maxBytes
bytes.length <= maxBytes
```

This blocks:

* non-image responses
* huge files and memory exhaustion

---

## 5) How to use it in your application

Because it’s a `@Component`, Spring injects it.

Example service usage:

```java
@Service
public class RecipeService {

    private final TrustedImageFetcher trustedImageFetcher;

    public RecipeService(TrustedImageFetcher trustedImageFetcher) {
        this.trustedImageFetcher = trustedImageFetcher;
    }

    public byte[] downloadRecipeImage(String url) {
        return trustedImageFetcher.fetchHttpsFromTrustedDomain(url);
    }
}
```

Common mistake:

* Do not call `new TrustedImageFetcher()`
* Always inject it

---

## 6) Checklist: “Is anything missing” review

### Required items to make your snippet work end-to-end

* `TrustedImageFetcher` must have `@Component`
* `SecurityProperties` must have:

  * `@Component`
  * `@ConfigurationProperties(prefix="security")`
  * correct field names that match properties
* `application.properties` keys must match:

  * `security.trusted-domains`
  * `security.max-upload-bytes`
* `trustedDomains` must not contain trailing `/`

### Recommended hardening (optional)

* Reject non-443 ports if you want strict HTTPS:

  * `uri.getPort()` must be `-1` or `443`
* Disable proxy usage explicitly in OkHttp if relevant:

  * `.proxy(Proxy.NO_PROXY)`
* Add logging for rejected cases (without leaking internals)

---

## 7) Student takeaways

1. SSRF happens whenever user input influences server-side network access.
2. The strongest control is a strict allowlist.
3. DNS resolution must be verified and pinned.
4. Redirects must be disabled.
5. Limit the blast radius with timeouts and byte limits.

---

