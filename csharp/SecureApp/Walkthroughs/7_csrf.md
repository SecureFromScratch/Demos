# What is CSRF (Cross-Site Request Forgery)

CSRF is an attack where a **malicious website tricks your browser into making unwanted requests to a site where you're already authenticated**.

## The Core Problem

Your browser **automatically sends cookies** with every request to a domain, even if the request originates from a different site. Attackers exploit this behavior.

## Simple Example

**Scenario:** You're logged into `bank.com`

1. You visit `evil.com` (attacker's site)
2. `evil.com` contains this hidden form:

```html
<form action="https://bank.com/transfer" method="POST" id="hack">
    <input type="hidden" name="to" value="attacker" />
    <input type="hidden" name="amount" value="10000" />
</form>
<script>
    document.getElementById('hack').submit();
</script>
```

3. Your browser automatically submits the form to `bank.com`
4. **Your authentication cookie is sent along with the request**
5. Bank thinks it's a legitimate request from you
6. Money transferred to attacker! 💸

## Why It Works

```
You → bank.com (login) → Cookie stored in browser
You → evil.com → evil.com triggers request to bank.com
Browser → Automatically attaches your bank.com cookie
bank.com → Sees valid cookie → Processes request ❌
```

The bank **can't tell the difference** between:
- A legitimate request you made from `bank.com`
- A forged request triggered by `evil.com`

## Real-World Attack Vectors

### 1. Hidden Form Submission
```html
<!-- On evil.com -->
<form action="https://yourapp.com/bff/account/delete" method="POST">
    <input type="hidden" name="confirm" value="yes" />
</form>
<script>document.forms[0].submit();</script>
```

### 2. Image Tag (GET-based)
```html
<img src="https://yourapp.com/bff/logout" />
```
When page loads, browser makes GET request with cookies.

### 3. AJAX Request (if CORS misconfigured)
```javascript
fetch('https://yourapp.com/bff/change-email', {
    method: 'POST',
    credentials: 'include',  // Sends cookies
    body: JSON.stringify({ email: 'attacker@evil.com' })
});
```

### 4. Link Click
```html
<a href="https://yourapp.com/bff/delete-account">
    Click for free iPhone!
</a>
```
If using GET for state changes + SameSite=Lax, this works.

## How CSRF Relates to Your BFF Code

Your login endpoint is vulnerable **without** CSRF protection:

```csharp
app.MapPost("/bff/account/login", async (...) => {
    // No CSRF token validation!
    // An attacker could potentially trigger unwanted logins
});
```

**Attack scenario:**
```html
<!-- On evil.com -->
<form action="https://yourapp.com/bff/account/login" method="POST">
    <input type="hidden" name="username" value="attacker" />
    <input type="hidden" name="password" value="attackerpass" />
</form>
<script>document.forms[0].submit();</script>
```

This logs the victim into the **attacker's account**, potentially causing them to enter sensitive data into the attacker's account.

## CSRF Protection Methods

### 1. Anti-CSRF Tokens (Most Common)
Server generates unique token, embeds in form:

```csharp
// Server includes token in page
<form method="POST">
    <input type="hidden" name="__RequestVerificationToken" value="ABC123..." />
</form>

// Server validates token
app.MapPost("/bff/account/login", 
    [ValidateAntiForgeryToken]  // ← Checks token
    async (...) => { ... });
```

**Why it works:** Attacker can't read the token from your site due to Same-Origin Policy.

### 2. SameSite Cookie Attribute

```csharp
options.Cookie.SameSite = SameSiteMode.Strict;  // or Lax
```

Prevents browser from sending cookies on cross-site requests.

**Lax vs Strict:**
- **Strict**: Cookie never sent from external sites (blocks CSRF, breaks some UX)
- **Lax**: Cookie sent on top-level navigation GETs (blocks most CSRF, allows links)

### 3. Custom Headers
```javascript
fetch('/bff/account/login', {
    method: 'POST',
    headers: { 'X-Requested-With': 'XMLHttpRequest' }
});
```

**Why it works:** Simple forms can't add custom headers, only AJAX can (and AJAX requires CORS).

### 4. Check Referer/Origin Headers
```csharp
var origin = http.Request.Headers["Origin"].ToString();
if (origin != "https://yourapp.com") 
    return Results.BadRequest();
```

**Downside:** Some proxies/browsers strip these headers.

### 5. Re-authentication for Sensitive Actions
```csharp
// Require password for critical operations
if (!await VerifyPassword(req.Password))
    return Results.Unauthorized();
```

## Updating Your BFF Code for CSRF Protection

```csharp
// Add antiforgery services
builder.Services.AddAntiforgery();

// Validate token on login
app.MapPost("/bff/account/login", async (
    LoginRequest req,
    IHttpClientFactory factory,
    HttpContext http,
    IAntiforgery antiforgery) =>  // ← Inject antiforgery
{
    // Validate CSRF token
    await antiforgery.ValidateRequestAsync(http);
    
    // ... rest of your code
});
```

Frontend must include token:
```javascript
// Get token from cookie/meta tag
const token = document.querySelector('[name="__RequestVerificationToken"]').value;

fetch('/bff/account/login', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'RequestVerificationToken': token  // ← Include token
    },
    body: JSON.stringify({ username, password })
});
```

## Defense in Depth

Best practice: **Combine multiple protections**

```csharp
builder.Services.AddAuthentication("bff")
    .AddCookie("bff", options => 
    {
        options.Cookie.HttpOnly = true;        // XSS protection
        options.Cookie.SecurePolicy = Always;   // HTTPS only
        options.Cookie.SameSite = Strict;       // CSRF protection
    });

builder.Services.AddAntiforgery();  // Token-based CSRF protection
```

## Summary

**CSRF** = Attacker tricks your browser into making requests using your credentials

**Protection** = Ensure requests actually came from your site, not an attacker's site

**Your BFF code** = Should add CSRF token validation for complete protection