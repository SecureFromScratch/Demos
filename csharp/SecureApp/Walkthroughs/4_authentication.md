
# Authentication, the API part

## 1. Create solution skeleton

```bash
mkdir SecureApp
cd SecureApp
```

We will have:

* `docker-compose.yml`        (SQL Server – from repo)
* `Api/`                      (.NET backend API)
* `Bff/`                      (.NET BFF – frontend-facing)
* `client/`                   (Angular)

If you already cloned the repo, this structure should already exist.

---

## 3. Infisical secrets

In Infisical, in your project `dev` environment, define:

```text
DB_HOST      = localhost
DB_PORT      = 14333
DB_NAME      = MyAppDb
DB_USER      = sa
DB_PASSWORD  = Your_Strong_SA_Password!123

JWT_SECRET   = a_very_long_random_string_at_least_32_chars
JWT_ISSUER   = secureapp-api
JWT_AUDIENCE = secureapp-client-or-bff
```

> Best practice: No secrets in code or git, only via Infisical / environment.

---

## 4. SQL Server in Docker with persistence

Use the `SecureApp/docker-compose.yml` from the repo.

To start SQL Server with Infisical:

```bash
cd SecureApp
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- docker compose up -d
```

Check:

```bash
docker ps
docker logs secureapp-sqlserver
```

---

## 5. Backend: API + BFF

### 5.1 API project

If creating from scratch:

```bash
cd SecureApp
dotnet new webapi -n Api
cd Api
dotnet restore
```

From the repo, copy the API files into:

* Config:

  * `Api/appsettings.json`  (no secrets – use repo version)
* Models:

  * `Api/Models/AppUser.cs`
* Data:

  * `Api/Data/AppDbContext.cs`
* Services:

  * `Api/Services/IUserService.cs`
  * `Api/Services/UserService.cs`
* Controllers:

  * `Api/Controllers/AccountController.cs`
  * `Api/Controllers/AdminController.cs`
* Entry point:

  * `Api/Program.cs` (API configuration, EF Core, JWT, CORS)

> Best practice: Keep the API “clean” – only business logic + data + auth. No SPA-specific logic here.

### 5.2 BFF project

Your BFF should:

* Accept requests from the Angular SPA
* Call the **API** internally (usually via HTTP or same solution project)
* Manage auth/session (tokens/cookies) and expose safe endpoints to the SPA

Files to copy from the repo (paths may be slightly different depending on your naming):

* `Bff/Program.cs` (BFF setup, HTTP client to API, auth middleware, etc.)
* `Bff/Controllers/...` (BFF controllers that the SPA calls, e.g. `/bff/account`, `/bff/admin`)
* Any DTOs / services needed:

  * `Bff/Models/...`
  * `Bff/Services/...`

> Best practice: SPA only talks to BFF; BFF talks to API. Don’t let the browser talk directly to sensitive internal APIs in production.

### 5.3 Migrations (API DB)

Install EF tool once if needed:

```bash
dotnet tool install --global dotnet-ef
```

From `SecureApp/Api`:

```bash
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- dotnet ef migrations add InitialSqlServer
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- dotnet ef database update
```

---

## 6. Frontend: Angular SPA

### 6.1 Create Angular project (if starting from scratch)

From `SecureApp`:

```bash
ng new client --routing true --style css
cd client
```

If you cloned the repo, `SecureApp/client` already exists.

### 6.2 Proxy for BFF

Angular should talk to the **BFF**, not directly to the API.

Use `client/proxy.conf.json` from the repo and make sure:

* The `"target"` points to your **BFF** URL, e.g.:

  * `http://localhost:5120` or whatever port `Bff` prints on startup.

In `client/package.json`, ensure the `start` script uses the proxy:

* `ng serve --proxy-config proxy.conf.json`

> Best practice: Single origin + proxy in dev keeps CORS simple and realistic (SPA → BFF).

### 6.3 Angular modules & pages

From the repo, copy these files (do **not** rewrite by hand):

* Root module and routing:

  * `client/src/app/app.module.ts`
  * `client/src/app/app-routing.module.ts`

* Auth service:

  * `client/src/app/services/auth.service.ts`  

  > If your BFF uses cookies instead of SPA-stored JWT, the interceptor might only need to add nothing or add CSRF headers. Use the repo version that matches your BFF.

* Pages / components:

  * Setup page:

    * `client/src/app/pages/setup/setup.component.ts`
    * `client/src/app/pages/setup/setup.component.html`
  * Register page:

    * `client/src/app/pages/register/register.component.ts`
    * `client/src/app/pages/register/register.component.html`
  * Login page:

    * `client/src/app/pages/login/login.component.ts`
    * `client/src/app/pages/login/login.component.html`

* Styles:

  * `client/src/styles.css` (basic layout + `.container`, `.form-group`, `.info`, `.error`, etc.)

> Best practice: Keep all auth UX (setup/register/login) in one place and keep the actual security logic in BFF/API, not in the Angular code.

---

## 7. Run everything

### 7.1 SQL Server

From `SecureApp`:

```bash
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- docker compose up -d
```

### 7.2 Apply migrations (once)

From `SecureApp/Api`:

```bash
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- dotnet ef database update
```

### 7.3 Run API

From `SecureApp/Api`:

```bash
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- dotnet run
```

Note the port printed in the console (e.g. `http://localhost:5207`) – this is what the **BFF** uses as its upstream API.

### 7.4 Run BFF

From `SecureApp/Bff`:

```bash
dotnet run
```

Note the BFF port (e.g. `http://localhost:5120`) – this is what Angular should target in `proxy.conf.json`.

### 7.5 Run Angular

From `SecureApp/client`:

```bash
npm install
npm start
```

Angular dev server at `http://localhost:4200`.

---

## 8. Test flows (with BFF in front)

1. Open `http://localhost:4200/`.
2. SPA calls BFF (through `/api/...` or `/bff/...` as you designed).
3. BFF calls API (`/api/account/...` etc.) and manages auth/session.
4. Test:

   * first admin setup flow
   * registration of normal users
   * login + protected endpoints (e.g. admin-only)

> Best practice: Verify:
>
> * Normal user cannot access admin endpoints (403/401).
> * Auth survives refresh correctly under your chosen session model (cookies vs JWT in storage).

