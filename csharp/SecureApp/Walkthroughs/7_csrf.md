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

## Why CSRF attack are less common?

Cross-Site Request Forgery (CSRF), once a staple of the OWASP Top 10, has largely faded from the landscape of common web vulnerabilities due to a convergence of browser hardening and architectural shifts. The most significant defense is the widespread adoption of the **`SameSite` cookie attribute**, which modern browsers now default to `Lax`. This setting automatically prevents the browser from sending cookies during cross-site POST requests, effectively killing the attack vector for most standard implementations. 

### What SameSite=Lax Does

`SameSite=Lax` is a **middle-ground** cookie policy that provides some CSRF protection while maintaining usability for common scenarios.

### Cookie Sending Behavior

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

### Comparison

```
SameSite=Strict  → Cookie NEVER sent on cross-site requests (most secure, breaks some UX)
SameSite=Lax     → Cookie sent on "safe" cross-site navigation (balanced)
SameSite=None    → Cookie always sent (requires Secure flag, least protective)
```

### Practical Example

**Scenario:** User authenticated on `yourapp.com`, then clicks link from email

| SameSite Mode | Cookie Sent? | User Experience |
|---------------|-------------|-----------------|
| **Strict** | ❌ No | User appears logged out, must log in again |
| **Lax** | ✅ Yes | User stays logged in seamlessly |
| **None** | ✅ Yes | User stays logged in |

### CSRF Protection Level

**With Lax:**
- ✅ Protected: Attacker's site can't POST to your login endpoint with user's cookie
- ✅ Protected: Cross-site AJAX calls won't include the cookie
- ⚠️ Vulnerable: Top-level GET requests still send cookie (rare CSRF vector if you have state-changing GETs)



## Why CSRF Attacks can happen in login
During login the authentication cookie wasn't created yet. 
An attacker’s page submits a login request with her credentials (form POST or fetch),
Then it sends the victim to the vulnerable app by tricking him to click a link.

The result: the attacker tricks the victim to authenticate with her user and buy some goodies that will be sent directly to her.

for example like this:
```csharp 
   options.AddPolicy("SpaDev", policy =>
   {
      policy.SetIsOriginAllowed(origin => true) // <--- Accepts ANY origin and reflects it back
         .AllowAnyHeader()
         .AllowAnyMethod()
         .AllowCredentials();
   });
```

Here are the three most common business scenarios that pressure developers into using this insecure configuration:

### 1\. The "White-Label" SaaS Platform (Most Common)

Imagine you are building a platform like Shopify or a Customer Support Portal. You have one backend API, but thousands of customers.

  * **Customer A** uses: `portal.company-a.com`
  * **Customer B** uses: `support.big-corp.com`
  * **Customer C** uses: `help.startup.io`

**The Logic:** You cannot hardcode 10,000 domains in your `Program.cs`. You also cannot restart the server every time a new customer signs up.
**The Trap:** The developer thinks, *"I need to accept ANY domain that hits me, because it might be a new customer."*
**The Vulnerability:** Instead of checking if the domain *actually belongs to a customer* in a database, they use `origin => true` to save time.

### 2\. Dynamic Preview Environments (CI/CD)

Modern frontend hosting (like Vercel, Netlify, or Azure Static Web Apps) creates a unique URL for every code change.

  * Pull Request \#1: `https://app-git-feature-login-xyz.vercel.app`
  * Pull Request \#2: `https://app-git-fix-typo-abc.vercel.app`

**The Logic:** The backend needs to allow the frontend team to test their work. The URLs change every hour and are random.
**The Trap:** Writing a Regular Expression to match `*.vercel.app` feels complex or "might break," so the developer just opens the gates to `true` to stop the frontend team from complaining about CORS errors during development.

### 3\. "It Works on My Machine" (Developer Friction)

In microservices or large teams, developers often run services on different local ports.

  * Dev A runs the frontend on `localhost:3000`
  * Dev B runs it on `localhost:8080`
  * Dev C uses a tunneling tool like `ngrok` (`http://random-id.ngrok.io`) to demo to a client.

**The Logic:** "I am tired of changing the CORS config every time I switch tools or ports."
**The Trap:** The developer adds the "Permissive Policy" intending to keep it only for `Development` mode, but it accidentally gets promoted to `Production` because it was never flagged during code review.

### The Correct "Business Logic" Implementation

The business requirement (Dynamic Origins) is valid, but the implementation is wrong.

Instead of `origin => true`, the code should be:

```csharp
policy.SetIsOriginAllowed(origin => 
{
    // 1. Get the domain from the origin string
    var domain = GetDomainFromOrigin(origin); 
    
    // 2. Query the database/cache: "Is this a valid paying customer?"
    bool isTrusted = _tenantService.IsKnownCustomer(domain);
    
    return isTrusted; // Only reflect back if they are actually YOUR customer
})
```
### Why CSRF Attacks are rare in SPAs

The rise of Single Page Applications (SPAs) has shifted many systems from session cookies to **header-based access tokens** (for example, a JWT in the `Authorization` header). This change typically removes classic CSRF risk, because **browsers automatically attach cookies**, but they **do not automatically attach `Authorization` headers** to requests triggered by another site.

If a malicious site tries to call your API with `fetch()` and include `Authorization`, the browser treats this as a **non-simple cross-origin request** and sends a **CORS preflight (`OPTIONS`)** first. The browser will only proceed with the actual request if the API replies with the correct CORS headers (such as allowing the attacker’s origin and headers). In a correctly configured policy that allows only trusted origins, the attacker’s origin is not allowed, so the browser blocks the request and the credentialed call never happens.

But, things are getting complicated. for the cookie to be sent as header they had to be saved in the client.
and we don't want that JWT along with the claims will be saved in the client. so BFF joined the scene. and now the cookies come back along with CSRF.

## CSRF And BFF Code
After the login, we have a session cookie. and this cookie is configured as same site=strict. meaning, it will not be sent by the browser in a case of http reuqest is troggered from another website.
even if it's a get request it will not be send.
if we choose samesite=lax, the cookie will be sent by the browser when a get request will be triggered by another website, but not in a case of post.
so CSRF is not possible, excluding the cases where get request was used for chnaging data which is wrong.

So the only thing that was left vulnerble is the login. remeber? we have no session cookie.

**Attack Demostration:**

1. Go to the angular https://localhost:4200 
2. Login 
3. Go to recipes
4. Copy the url 
5. Delete the cookie,
6. Refresh the broswer to see there is no recipes anymore
7. Create the html page below 
8. Using an host of your choice for example python http server and go to the poc you created. 

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Recipe Login Redirect</title>
</head>
<body>
    <h2>Login CSRF & Redirect</h2>
    <p>Status: <span id="status">Waiting for user...</span></p>
    
    <button id="sendBtn">Login & Go to Recipes</button>

    <script>
        function executeLogin() {
            const url = 'https://localhost:4200/bff/account/login';
            const targetPage = 'https://localhost:4200/recipes';
            
            document.getElementById('status').innerText = "Attempting login...";

            const payload = {
                userName: "admin",
                password: "password"
            };

            fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json, text/plain, */*'
                },
                body: JSON.stringify(payload),
                credentials: 'include' // Important: Saves the cookie
            })
            .then(response => {
                if (response.ok) {
                    document.getElementById('status').innerText = "Login Success! Redirecting...";
                    // 1. The cookie is now saved in the browser.
                    // 2. We redirect the user to the application.
                    window.location.href = targetPage;
                } else {
                    document.getElementById('status').innerText = "Login Failed: " + response.status;
                }
            })
            .catch((error) => {
                document.getElementById('status').innerText = 'Error: ' + error;
                console.error('Error:', error);
            });
        }

        // Option 1: Manual Click
        document.getElementById('sendBtn').addEventListener('click', executeLogin);

        // Option 2: Auto-Execute (The "Perfect" Attack)
        // Uncomment the line below to run immediately when page loads
        // executeLogin();
    </script>
</body>
</html>
```
```bash
 python -m http.server 5555
```

This logs the victim into the **attacker's account**, potentially causing them to enter sensitive data into the attacker's account.

## CSRF Protection

### Anti-CSRF Tokens (Most Common)
**Why it works:** Attacker can't read the token from your site due to Same-Origin Policy.

```

## Updating Your BFF Code for CSRF Protection

```csharp
// Add antiforgery services
builder.Services.AddAntiforgery(options =>
{
      options.HeaderName = "X-XSRF-TOKEN"; 
});

app.Use(async (context, next) =>
{
    var antiforgery = context.RequestServices.GetRequiredService<IAntiforgery>();
    
    // Get the tokens
    var tokens = antiforgery.GetAndStoreTokens(context);
    
    // Create a new cookie "XSRF-TOKEN" with the request token
    context.Response.Cookies.Append("XSRF-TOKEN", tokens.RequestToken!, 
        new CookieOptions
        {
            HttpOnly = false, // CRITICAL: Angular must be able to read this
            Secure = true, 
            SameSite = SameSiteMode.None // Use None if cross-site, or Strict/Lax if same-site
        });

    await next(context);
});


// Validate token on login
public async Task<IActionResult> Login([FromBody] LoginRequest req, IAntiforgery antiforgery)
    {
        var http = HttpContext; 
        await antiforgery.ValidateRequestAsync(http);
```

if you don't implment the corresponding funcionality in the client, when performing login you will get the following error:

``` text
Microsoft.AspNetCore.Antiforgery.AntiforgeryValidationException: 
The antiforgery cookie token and request token do not match.
```

Frontend must include token

Create a csrf interceptor: csrf.interceptor.ts

```javascript
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';

export const csrfInterceptor: HttpInterceptorFn = (req, next) => {
  // 1. Inject the document to access cookies safely
  const document = inject(DOCUMENT);
  
  // 2. Define the names your C# server expects
  const cookieName = 'XSRF-TOKEN';
  const headerName = 'X-XSRF-TOKEN';

  // 3. Helper function to find the cookie value
  function getCookie(name: string): string | null {
    const nameEQ = name + "=";
    const ca = document.cookie.split(';');
    for(let i = 0; i < ca.length; i++) {
      let c = ca[i];
      while (c.charAt(0) === ' ') c = c.substring(1, c.length);
      if (c.indexOf(nameEQ) === 0) return c.substring(nameEQ.length, c.length);
    }
    return null;
  }

  // 4. Get the token
  const token = getCookie(cookieName);

  // 5. If the token exists and the request is "mutating" (POST/PUT/DELETE), add the header
  // (You can remove the method check if you want to send it on ALL requests)
  if (token && req.method !== 'GET' && req.method !== 'HEAD') {
    req = req.clone({
      headers: req.headers.set(headerName, token)
    });
  }

  return next(req);
};

```
update app.config.ts


```javascript
// app.config.tx
export const appConfig: ApplicationConfig = {
   ...
withInterceptors([
        credentialsInterceptor, 
        csrfInterceptor // <--- Add it here
      ])
   ...
```

## What happend here?
A variation of **"Double-Submit Cookie"** pattern.

### The Core Concept: "The Secret Handshake"
To prove a request is real, the server demands that the client send two secret codes in two different places:
1.  Inside a **Cookie** (Automatic).
2.  Inside a **Header** (Manual).

If they match, the server knows the request came from your legitimate Angular app.

People call basic **double-submit** “not enough” because the simplest version is often implemented in a way that makes the token **not actually a server-verified secret**, or it ignores common real-world bypass conditions.

### Why the naive “cookie == header” check can be weak

* **Not server-bound (stateless):** In classic double-submit, the server only checks *equality*, not that the value was generated by the server for *this* session/user. If an attacker can cause the victim’s browser to store a cookie value the attacker knows (cookie injection via loose `Domain`, subdomains, mis-scoped `Path`, some proxy/app misconfigs), the attacker can potentially make both sides match.

* **Cookie scoping mistakes are common:** Setting CSRF cookie with `Domain=.example.com` or broad `Path=/` increases the chance another subdomain/app can set/override it. That turns your “secret handshake” into something other apps can forge.

* **It does not help against XSS:** If your Angular app has XSS, JS can read the cookie (if not `HttpOnly`) and set the header, so CSRF protection becomes irrelevant. People dislike relying on patterns that “work unless XSS”, because XSS is common.

* **CORS / endpoint leaks can erase the benefit:** If you expose the CSRF token via an endpoint and accidentally allow cross-origin reads (bad CORS), an attacker can fetch the token and then forge requests.

* **Header-only assumption can break with legacy flows:** Some requests won’t reliably carry your custom header (form posts, some redirects, file uploads unless you control the client), leading teams to weaken checks.
---
## Synchronizer token (server-bound CSRF)

Server generates a CSRF token and stores it in the user session (session store can be in-memory, Redis, DB-backed, etc.).

Client sends the token in a header (Angular can do this automatically).

Server validates that the header token matches what’s stored for that session.

This often looks similar to “double-submit” from the outside (cookie + header), but the key difference is: the server verifies the token is the one it issued for that session, not just “header equals cookie”.

---
In ASP.NET Core Antiforgery, the server usually **does not store a per-user CSRF token anywhere** (no DB, no Redis, no session).

What happens instead:

1. **A cookie token is stored on the client**
   The framework sets an antiforgery cookie (and in your code you also set `XSRF-TOKEN`). That cookie value lives in the browser.

2. **A request token is sent by the client (header/form)**
   Angular copies the token into `X-XSRF-TOKEN` (header).

3. **The server validates using its secret keys, not a stored token**
   The request token is **cryptographically protected** (signed/encrypted) using the server’s **Data Protection keys**.
   On validation, the server “unprotects” the request token with those keys and checks it matches the cookie token and other expected data.

So the “server-side value” is not a stored token. It’s the server’s **Data Protection key ring** (the secret used to mint and verify tokens).

Practical implication:

* Single server: keys are on that server.
* Multiple servers: you must **share the Data Protection keys** across instances (common: Redis, file share, Azure blob, etc.), otherwise CSRF validation can fail after load balancing.

---

### Step-by-Step Flow

#### 1. The Setup (Page Load)
Before the user even types their password, the security setup begins.
* **Browser:** Requests the Angular app (or performs a GET request to the API).
* **.NET Server:** Your middleware runs. It generates a random, cryptographically strong token (e.g., `abc-123-secret`).
* **.NET Server:** Sends this token to the browser in a **readable cookie** named `XSRF-TOKEN`.
    * *Note: This is why we set `HttpOnly = false`. Angular needs to read it.*

#### 2. The Angular Client (The Interceptor)
When the user clicks "Login":
* **Angular:** Prepares the `POST /login` request with `username: admin`.
* **Your Interceptor:** Pauses the request.
* **Your Interceptor:** Looks into the browser's cookies (`document.cookie`), finds `XSRF-TOKEN`, and copies its value (`abc-123-secret`).
* **Your Interceptor:** Creates a custom HTTP Header named `X-XSRF-TOKEN` and pastes the value there.
* **Angular:** Sends the request. It now carries **both** the cookie (sent by browser) and the header (sent by script).

#### 3. The Validation (.NET Side)
The request hits your C# Controller marked with `[ValidateAntiForgeryToken]`.
* **.NET:** Looks at the **Cookie**: `abc-123-secret`.
* **.NET:** Decrypts and Looks at the **Header**: `abc-123-secret`.
* **.NET:** "Do they match?"
    * **YES:** It allows the Login logic to proceed.
    * **NO:** It throws a `400 Bad Request` and the code never executes.

---

### How This Protects Against Login CSRF
Imagine `attacker.com` tries to force you to log in to their account.

1.  **Attacker's Page:** Contains a hidden form or script trying to POST to `your-site.com/login`.
2.  **Browser:** Automatically sends your `XSRF-TOKEN` **Cookie** along with the request. (This is normal browser behavior).
3.  **The Missing Piece:** The attacker's script **cannot read** your cookies. This is the **Same-Origin Policy**.
    * Because the attacker cannot read the cookie, they **cannot create the `X-XSRF-TOKEN` header**.
4.  **The Failure:** The request arrives at your server:
    * **Cookie:** Present (`abc-123-secret`)
    * **Header:** **Missing**
5.  **Result:** Your server rejects the request.

The "Double-Submit" works because **only your specific domain** has the permission to read the cookie and copy it into the header.

### Lets try the attack again!

Go back to Attack Demostration and try to run the CSRF attack!
if you follow the tutorial, you'll see in the log the same excpetion we got before we implemented the angular csrf protection. 

## Summary

**CSRF** = Attacker tricks your browser into making requests using your credentials

**Protection** = Ensure requests actually came from your site, not an attacker's site

**Your BFF code** = Should add CSRF token validation for complete protection
