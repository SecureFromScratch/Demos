
## What SameSite=Lax Does

`SameSite=Lax` is a **middle-ground** cookie policy that provides some CSRF protection while maintaining usability for common scenarios.

## Cookie Sending Behavior

With `SameSite=Lax`, the cookie is sent:

✅ **Sent in these cases:**
- Same-site requests (requests from your own domain)
- Top-level navigation GET requests from external sites
  - Example: User clicks a link on `google.com` → navigates to `yourapp.com`
  - Example: User types URL directly in browser
  - Example: User clicks bookmark

❌ **NOT sent in these cases:**
- Cross-site POST, PUT, DELETE requests
- Cross-site requests in iframes
- AJAX/fetch requests from other domains
- Image/script tags from other domains

## Comparison

```
SameSite=Strict  → Cookie NEVER sent on cross-site requests (most secure, breaks some UX)
SameSite=Lax     → Cookie sent on "safe" cross-site navigation (balanced)
SameSite=None    → Cookie always sent (requires Secure flag, least protective)
```

## Practical Example

**Scenario:** User authenticated on `yourapp.com`, then clicks link from email

| SameSite Mode | Cookie Sent? | User Experience |
|---------------|-------------|-----------------|
| **Strict** | ❌ No | User appears logged out, must log in again |
| **Lax** | ✅ Yes | User stays logged in seamlessly |
| **None** | ✅ Yes | User stays logged in |

## CSRF Protection Level

**With Lax:**
- ✅ Protected: Attacker's site can't POST to your login endpoint with user's cookie
- ✅ Protected: Cross-site AJAX calls won't include the cookie
- ⚠️ Vulnerable: Top-level GET requests still send cookie (rare CSRF vector if you have state-changing GETs)

## For Your BFF Code

If using `SameSite=Lax`:
```csharp
options.Cookie.SameSite = SameSiteMode.Lax;
```

- User can click links from external sites and remain authenticated
- CSRF attacks via forms/AJAX are blocked
- Good balance for most applications

