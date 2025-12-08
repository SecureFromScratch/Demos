

Goal: keep your **API** as a clean JWT-protected service, and add a **BFF** that:

* Talks to the API with **Bearer tokens**
* Talks to the browser (Angular) with **cookies**
* Keeps JWTs **out of the browser**

Architecture after this:

```text
Angular SPA  ──(cookies, JSON)──►  BFF  ──(Bearer JWT)──►  API  ──► SQL Server
```

Below is a **full, from-previous-setup-to-BFF** tutorial. I’ll assume you already have:

* `SecureApp/Api` – the JWT API we built
* `SecureApp/client` – Angular SPA
* SQL Server + Infisical secrets as before

We will add:

* `SecureApp/Bff` – ASP.NET Core BFF

---

## 1. BFF: new ASP.NET Core project

From `SecureApp/`:

```bash
dotnet new webapi -n Bff
cd Bff
```

And

```bash
dotnet restore
```

### 1.1 appsettings.json for BFF

We only need to know where the API lives:

`Bff/appsettings.json`:

```json
{
  "ApiBaseUrl": "https://localhost:5001/",
  "Logging": {
    "LogLevel": {
      "Default": "Information",
      "Microsoft.AspNetCore": "Warning"
    }
  },
  "AllowedHosts": "*"
}
```

No secrets here. JWT verification still happens on the **API**, not the BFF.

---

## 2. BFF Program.cs – cookie auth + HttpClient to API

Replace `Bff/Program.cs` with:

```csharp
using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Security.Claims;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.Authorization;

var builder = WebApplication.CreateBuilder(args);

// HttpClient for calling the API
builder.Services.AddHttpClient("Api", client =>
{
   var baseUrl = builder.Configuration["ApiBaseUrl"] ?? "https://localhost:5001/";
   client.BaseAddress = new Uri(baseUrl);
});

// Cookie auth for browser ↔ BFF
builder.Services
   .AddAuthentication("bff")
   .AddCookie("bff", options =>
   {
      options.LoginPath = "/bff/unauthorized";
      options.Cookie.Name = "bff_auth";
      options.Cookie.SecurePolicy = CookieSecurePolicy.Always;
      options.Cookie.SameSite = SameSiteMode.None; // needed for cross-origin dev
   });

builder.Services.AddAuthorization();

// CORS for Angular dev (4200 → 5002)
builder.Services.AddCors(options =>
{
   options.AddPolicy("SpaDev", policy =>
   {
      policy.WithOrigins("http://localhost:4200")
            .AllowAnyHeader()
            .AllowAnyMethod()
            .AllowCredentials();
   });
});

// DTOs matching the API's JSON
public record LoginRequest(string UserName, string Password);
public record MeResponse(string UserName, string[] Roles);
public record LoginResponse(string Token, MeResponse User);

var app = builder.Build();

if (!app.Environment.IsDevelopment())
{
   app.UseExceptionHandler("/error");
   app.UseHsts();
}

app.UseHttpsRedirection();
app.UseRouting();

app.UseCors("SpaDev");

app.UseAuthentication();
app.UseAuthorization();

// Helper to get JWT from cookie claims
string? GetToken(ClaimsPrincipal user)
   => user.FindFirst("access_token")?.Value;

// ------------- BFF ENDPOINTS -------------

// Angular can call this just to see "you're not logged in"
app.MapGet("/bff/unauthorized", () => Results.Unauthorized());

// Login: BFF forwards credentials to API, stores token in cookie
app.MapPost("/bff/account/login", async (
   LoginRequest req,
   IHttpClientFactory factory,
   HttpContext http) =>
{
   var client = factory.CreateClient("Api");

   // Call API: /api/account/login
   var resp = await client.PostAsJsonAsync("api/account/login", req);

   if (!resp.IsSuccessStatusCode)
   {
      var body = await resp.Content.ReadAsStringAsync();
      return Results.StatusCode((int)resp.StatusCode, body);
   }

   var login = await resp.Content.ReadFromJsonAsync<LoginResponse>();
   if (login is null) return Results.StatusCode((int)HttpStatusCode.InternalServerError);

   var claims = new List<Claim>
   {
      new(ClaimTypes.Name, login.User.UserName),
      new("access_token", login.Token)
   };

   foreach (var role in login.User.Roles)
   {
      claims.Add(new Claim(ClaimTypes.Role, role));
   }

   var identity = new ClaimsIdentity(claims, "bff");
   var principal = new ClaimsPrincipal(identity);

   await http.SignInAsync("bff", principal, new AuthenticationProperties
   {
      IsPersistent = true,
      ExpiresUtc = DateTimeOffset.UtcNow.AddHours(1)
   });

   // Return user info (no token)
   return Results.Ok(login.User);
});

// Logout: clear cookie
app.MapPost("/bff/account/logout", async (HttpContext http) =>
{
   await http.SignOutAsync("bff");
   return Results.NoContent();
});

// Me: use cookie identity only (or forward to API if you prefer)
app.MapGet("/bff/account/me", [Authorize(AuthenticationSchemes = "bff")] (ClaimsPrincipal user) =>
{
   var name = user.Identity?.Name ?? string.Empty;
   var roles = user.Claims
      .Where(c => c.Type == ClaimTypes.Role)
      .Select(c => c.Value)
      .ToArray();

   return Results.Ok(new MeResponse(name, roles));
});

// First-user check → proxy to API /api/account/is-first-user
app.MapGet("/bff/account/is-first-user", async (IHttpClientFactory factory) =>
{
   var client = factory.CreateClient("Api");
   var resp = await client.GetAsync("api/account/is-first-user");
   var body = await resp.Content.ReadAsStringAsync();
   return Results.StatusCode((int)resp.StatusCode, body);
});

// Setup first admin → proxy to API /api/account/setup
app.MapPost("/bff/account/setup", async (
   LoginRequest req,
   IHttpClientFactory factory) =>
{
   var client = factory.CreateClient("Api");
   var resp = await client.PostAsJsonAsync("api/account/setup", req);
   var body = await resp.Content.ReadAsStringAsync();
   return Results.StatusCode((int)resp.StatusCode, body);
});

// Register normal user → proxy to API /api/account/register
app.MapPost("/bff/account/register", async (
   LoginRequest req,
   IHttpClientFactory factory) =>
{
   var client = factory.CreateClient("Api");
   var resp = await client.PostAsJsonAsync("api/account/register", req);
   var body = await resp.Content.ReadAsStringAsync();
   return Results.StatusCode((int)resp.StatusCode, body);
});

// Admin dashboard → call API /api/admin/dashboard with JWT from cookie
app.MapGet("/bff/admin/dashboard", [Authorize(AuthenticationSchemes = "bff")] async (
   IHttpClientFactory factory,
   ClaimsPrincipal user) =>
{
   var token = GetToken(user);
   if (token is null)
      return Results.Unauthorized();

   var client = factory.CreateClient("Api");
   var request = new HttpRequestMessage(HttpMethod.Get, "api/admin/dashboard");
   request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);

   var resp = await client.SendAsync(request);
   var body = await resp.Content.ReadAsStringAsync();
   return Results.StatusCode((int)resp.StatusCode, body);
});

app.Run();
```

Now:

* Browser talks to BFF with **cookies**.
* BFF talks to API with **Bearer token** stored in cookie claims.
* Angular never sees the JWT.

> Make sure BFF uses its own port (VS will create `launchSettings.json` with e.g. 5002). If it collides with Api, change it there.

---

## 3. API: no changes needed

The `Api` project stays **exactly** as in your last tutorial:

* `/api/account/login` returns `{ token, user }`
* Auth is JWT bearer
* Roles and first admin logic all unchanged

BFF just consumes those endpoints.

You **can** tighten API CORS now (only allow BFF), but since BFF talks to API server-to-server and doesn’t use browser, CORS is not relevant for API anymore.

---

## 4. Angular: talk to BFF instead of API

Now we point Angular at BFF and let cookies handle auth.

### 4.1 Proxy: target BFF

Change `client/proxy.conf.json` to:

```json
{
  "/bff": {
    "target": "https://localhost:5002",
    "secure": false,
    "changeOrigin": true
  }
}
```

(Port must match BFF’s HTTPS port.)

And `package.json` script `start` is already:

```json
"start": "ng serve --proxy-config proxy.conf.json"
```

### 4.2 AuthService: remove manual JWT, use cookies

Update `src/app/services/auth.service.ts`:

```ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

export interface MeResponse {
  userName: string;
  roles: string[];
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private baseUrl = '/bff/account';

  constructor(private http: HttpClient) { }

  isFirstUser(): Observable<boolean> {
    return this.http.get<boolean>(`${this.baseUrl}/is-first-user`, {
      withCredentials: true
    });
  }

  setup(userName: string, password: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/setup`,
      { userName, password },
      { withCredentials: true });
  }

  register(userName: string, password: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/register`,
      { userName, password },
      { withCredentials: true });
  }

  login(userName: string, password: string): Observable<MeResponse> {
    return this.http.post<MeResponse>(`${this.baseUrl}/login`,
      { userName, password },
      { withCredentials: true });
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/logout`, {}, {
      withCredentials: true
    }).pipe(map(() => void 0));
  }

  me(): Observable<MeResponse | null> {
    return this.http.get<MeResponse>(`${this.baseUrl}/me`, {
      withCredentials: true
    }).pipe(
      map(x => x ?? null)
    );
  }
}
```

Key points:

* All calls use `withCredentials: true` so cookies flow to BFF.
* No token stored in `localStorage` / memory: BFF handles that.

### 4.3 Remove the JWT interceptor

Delete or stop using `AuthTokenInterceptor`:

* Either delete `auth-token.interceptor.ts` and remove from `providers` in `app.module.ts`, or just remove it from `providers`.

In `app.module.ts` remove:

```ts
providers: [
  {
    provide: HTTP_INTERCEPTORS,
    useClass: AuthTokenInterceptor,
    multi: true
  }
],
```

No need for Authorization header anymore; cookie does it.

### 4.4 Components (setup/register/login)

They stay the same aside from imports:

* `setup`: calls `auth.isFirstUser`, `auth.setup`
* `register`: calls `auth.isFirstUser`, `auth.register`
* `login`: calls `auth.login`, then router navigate

No code changes required other than the service already updated.

---

## 5. Run everything together

From `SecureApp/`:

### 5.1 Start SQL Server (once per dev session)

```bash
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- docker compose up -d
```

(If it’s already running, this is a no-op.)

### 5.2 Make sure API DB schema exists

From `SecureApp/Api`:

```bash
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- dotnet ef database update
```

(Only needed when model changes or first run.)

### 5.3 Run API

From `SecureApp/Api`:

```bash
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- dotnet run
```

API on `https://localhost:5001`.

### 5.4 Run BFF

From `SecureApp/Bff`:

```bash
dotnet run
```

BFF on `https://localhost:5002` (check `launchSettings.json` if different).

### 5.5 Run Angular

From `SecureApp/client`:

```bash
npm install    # first time
npm start
```

Angular on `http://localhost:4200`, calling BFF via `/bff/...` proxy.

---

## 6. Flows (with BFF)

### 6.1 First admin setup

1. Open `http://localhost:4200/register`
   → Angular calls `GET /bff/account/is-first-user`
   → BFF proxies to API `/api/account/is-first-user`
   → DB empty → `true` → Angular redirects to `/setup`.

2. On `/setup`, submit admin credentials.
   → Angular POST `/bff/account/setup`
   → BFF proxies to API `/api/account/setup` (no auth required first time).

Admin user is now `ADMIN,USER`.

### 6.2 Login

1. Angular POST `/bff/account/login` with `{ userName, password }`.

2. BFF POSTs to API `/api/account/login`.

3. API returns `{ token, user }`.

4. BFF:

   * Stores `token` in cookie claims as `access_token`.
   * Adds roles & name into claims.
   * Issues **cookie** back to browser.

5. Angular gets user info (but no token) and navigates.

### 6.3 Authenticated requests

* Angular calls `/bff/account/me` with `withCredentials:true`.
* BFF reads claims from cookie and returns `{ userName, roles }`.
* For admin features Angular can call `/bff/admin/dashboard`; BFF injects Bearer JWT call to API `/api/admin/dashboard`.

### 6.4 Logout

* Angular POST `/bff/account/logout`.
* BFF clears cookie.

---

If you want, next step could be:

* Add an Angular `AuthGuard` that calls `/bff/account/me` and only allows navigation when logged in / has ADMIN role, or
* Harden cookie settings for prod (domain, SameSite, HTTPS-only, etc.) and show how to serve the built Angular app from BFF or API behind a single origin.
