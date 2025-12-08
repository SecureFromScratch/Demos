# Authentication, the API part


# 1. Create solution skeleton

```bash
mkdir SecureApp
cd SecureApp
```

We will put:

* `docker-compose.yml`        (SQL Server)
* `Api/`                      (.NET backend)
* `client/`                   (Angular)

---

# 3. Infisical secrets

In Infisical, in your project `dev` environment, define:

```text
DB_HOST      = localhost
DB_PORT      = 14333
DB_NAME      = MyAppDb
DB_USER      = sa
DB_PASSWORD  = Your_Strong_SA_Password!123

JWT_SECRET   = a_very_long_random_string_at_least_32_chars
JWT_ISSUER   = secureapp-api
JWT_AUDIENCE = secureapp-client
```

No secrets in code or git.

---

# 4. SQL Server in Docker with persistence

In `SecureApp/docker-compose.yml`:

```yaml
version: "3.9"

services:
   sqlserver:
      image: mcr.microsoft.com/mssql/server:2022-latest
      container_name: secureapp-sqlserver
      restart: unless-stopped
      environment:
         ACCEPT_EULA: "Y"
         SA_PASSWORD: ${DB_PASSWORD}
      ports:
         - "14333:1433"
      volumes:
         - mssql_data:/var/opt/mssql

volumes:
   mssql_data:
```

Start it with Infisical injecting secrets:

```bash
cd SecureApp
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- docker compose up -d
```
For example:
```bash
cd SecureApp
infisical run --env=dev --projectId f1d7ef76-6d48-455f-af61-cc011057c5b3 -- docker compose up -d
```

Check:

```bash
docker ps
docker logs secureapp-sqlserver
```

---

# 5. Backend: ASP.NET Core 8 Web API

## 5.1 Create project

```bash
cd SecureApp
dotnet new webapi -n Api
cd Api
```

Delete WeatherForecast related files if you want.

Then:

```bash
dotnet restore
```

## 5.3 `appsettings.json` (no secrets)

Replace with:

```json
{
   "Logging": {
      "LogLevel": {
         "Default": "Information",
         "Microsoft.AspNetCore": "Warning"
      }
   },
   "AllowedHosts": "*"
}
```

## 5.4 Folders

```bash
mkdir -p Models Data Services Controllers
```

## 5.5 Model: `Models/AppUser.cs`

```csharp
using System.ComponentModel.DataAnnotations;

namespace Api.Models;

public class AppUser
{
   public int Id { get; set; }

   [Required]
   [MaxLength(64)]
   public string UserName { get; set; } = string.Empty;

   [Required]
   public string PasswordHash { get; set; } = string.Empty;

   // "ADMIN,USER" or "USER"
   [Required]
   public string Roles { get; set; } = "USER";

   public bool Enabled { get; set; } = true;

   public int FailedAttempts { get; set; }

   public DateTimeOffset? LockoutEnd { get; set; }
}
```

## 5.6 DbContext: `Data/AppDbContext.cs`

```csharp
using Microsoft.EntityFrameworkCore;
using Api.Models;

namespace Api.Data;

public class AppDbContext : DbContext
{
   public AppDbContext(DbContextOptions<AppDbContext> options)
      : base(options)
   {
   }

   public DbSet<AppUser> Users => Set<AppUser>();
}
```

## 5.7 User service interface: `Services/IUserService.cs`

```csharp
using Api.Models;

namespace Api.Services;

public interface IUserService
{
   Task<bool> IsFirstUserAsync();
   Task<AppUser> RegisterUserAsync(string userName, string rawPassword, bool isAdmin);
   Task<AppUser> RegisterFirstAdminAsync(string userName, string rawPassword);
   Task<AppUser?> FindByUserNameAsync(string userName);
   Task<bool> VerifyPasswordAsync(AppUser user, string rawPassword);
}
```

## 5.8 User service: `Services/UserService.cs`

```csharp
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;
using Api.Data;
using Api.Models;

namespace Api.Services;

public class UserService : IUserService
{
   private readonly AppDbContext m_db;
   private readonly IPasswordHasher<AppUser> m_passwordHasher;

   public UserService(AppDbContext db, IPasswordHasher<AppUser> passwordHasher)
   {
      m_db = db;
      m_passwordHasher = passwordHasher;
   }

   public async Task<bool> IsFirstUserAsync()
   {
      return !await m_db.Users.AnyAsync();
   }

   private void ValidatePasswordStrength(string password)
   {
      if (string.IsNullOrWhiteSpace(password) || password.Length < 8)
         throw new ArgumentException("Password must be at least 8 characters long");

      if (!password.Any(char.IsUpper))
         throw new ArgumentException("Password must contain at least one uppercase letter");

      if (!password.Any(char.IsLower))
         throw new ArgumentException("Password must contain at least one lowercase letter");

      if (!password.Any(char.IsDigit))
         throw new ArgumentException("Password must contain at least one number");

      const string specials = "!@#$%^&*()_+-=[]{};':\"\\|,.<>/?";
      if (!password.Any(specials.Contains))
         throw new ArgumentException("Password must contain at least one special character");
   }

   public async Task<AppUser> RegisterUserAsync(string userName, string rawPassword, bool isAdmin)
   {
      if (string.IsNullOrWhiteSpace(userName))
         throw new ArgumentException("Username cannot be empty");

      userName = userName.Trim();
      if (userName.Length < 3)
         throw new ArgumentException("Username must be at least 3 characters long");

      if (await m_db.Users.AnyAsync(u => u.UserName == userName))
         throw new ArgumentException("Username already exists");

      ValidatePasswordStrength(rawPassword);

      var user = new AppUser
      {
         UserName = userName,
         Roles = isAdmin ? "ADMIN,USER" : "USER",
         Enabled = true
      };

      user.PasswordHash = m_passwordHasher.HashPassword(user, rawPassword);

      m_db.Users.Add(user);
      await m_db.SaveChangesAsync();

      return user;
   }

   public async Task<AppUser> RegisterFirstAdminAsync(string userName, string rawPassword)
   {
      if (!await IsFirstUserAsync())
         throw new InvalidOperationException("First user already exists");

      return await RegisterUserAsync(userName, rawPassword, isAdmin: true);
   }

   public Task<AppUser?> FindByUserNameAsync(string userName)
   {
      return m_db.Users.SingleOrDefaultAsync(u => u.UserName == userName);
   }

   public Task<bool> VerifyPasswordAsync(AppUser user, string rawPassword)
   {
      var result = m_passwordHasher.VerifyHashedPassword(user, user.PasswordHash, rawPassword);
      return Task.FromResult(result == PasswordVerificationResult.Success);
   }
}
```

## 5.9 JWT based AccountController: `Controllers/AccountController.cs`

```csharp
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.IdentityModel.Tokens;
using Api.Services;

namespace Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class AccountController : ControllerBase
{
   private readonly IUserService m_userService;
   private readonly IConfiguration m_config;

   public AccountController(IUserService userService, IConfiguration config)
   {
      m_userService = userService;
      m_config = config;
   }

   public record RegisterRequest(string UserName, string Password);
   public record LoginRequest(string UserName, string Password);
   public record MeResponse(string UserName, string[] Roles);
   public record LoginResponse(string Token, MeResponse User);

   [HttpGet("is-first-user")]
   [AllowAnonymous]
   public async Task<ActionResult<bool>> IsFirstUser()
   {
      return Ok(await m_userService.IsFirstUserAsync());
   }

   [HttpPost("setup")]
   [AllowAnonymous]
   public async Task<IActionResult> Setup([FromBody] RegisterRequest req)
   {
      try
      {
         await m_userService.RegisterFirstAdminAsync(req.UserName, req.Password);
         return NoContent();
      }
      catch (ArgumentException ex)
      {
         return BadRequest(new { error = ex.Message });
      }
      catch (InvalidOperationException ex)
      {
         return Conflict(new { error = ex.Message });
      }
   }

   [HttpPost("register")]
   [AllowAnonymous]
   public async Task<IActionResult> Register([FromBody] RegisterRequest req)
   {
      try
      {
         await m_userService.RegisterUserAsync(req.UserName, req.Password, isAdmin: false);
         return NoContent();
      }
      catch (ArgumentException ex)
      {
         return BadRequest(new { error = ex.Message });
      }
   }

   [HttpPost("login")]
   [AllowAnonymous]
   public async Task<IActionResult> Login([FromBody] LoginRequest req)
   {
      var user = await m_userService.FindByUserNameAsync(req.UserName);
      if (user == null || !user.Enabled)
         return Unauthorized(new { error = "Invalid credentials" });

      if (!await m_userService.VerifyPasswordAsync(user, req.Password))
         return Unauthorized(new { error = "Invalid credentials" });

      var roles = user.Roles
         .Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
         .ToArray();

      var token = GenerateToken(user.UserName, roles);

      var me = new MeResponse(user.UserName, roles);
      return Ok(new LoginResponse(token, me));
   }

   [HttpPost("logout")]
   public IActionResult Logout()
   {
      // JWT logout is handled client side by deleting token
      return NoContent();
   }

   [HttpGet("me")]
   [Authorize]
   public ActionResult<MeResponse> Me()
   {
      if (!User.Identity?.IsAuthenticated ?? true)
         return Unauthorized();

      var userName = User.Identity!.Name ?? string.Empty;
      var roles = User.Claims
         .Where(c => c.Type == ClaimTypes.Role)
         .Select(c => c.Value)
         .ToArray();

      return Ok(new MeResponse(userName, roles));
   }

   private string GenerateToken(string userName, string[] roles)
   {
      var key = m_config["JWT_SECRET"]
         ?? throw new InvalidOperationException("JWT_SECRET is not set");
      var issuer = m_config["JWT_ISSUER"] ?? "secureapp-api";
      var audience = m_config["JWT_AUDIENCE"] ?? "secureapp-client";

      var signingKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(key));
      var creds = new SigningCredentials(signingKey, SecurityAlgorithms.HmacSha256);

      var claims = new List<Claim>
      {
         new Claim(ClaimTypes.Name, userName)
      };

      foreach (var role in roles)
      {
         claims.Add(new Claim(ClaimTypes.Role, role));
      }

      var token = new JwtSecurityToken(
         issuer: issuer,
         audience: audience,
         claims: claims,
         expires: DateTime.UtcNow.AddHours(1),
         signingCredentials: creds);

      return new JwtSecurityTokenHandler().WriteToken(token);
   }
}
```

## 5.10 Admin controller: `Controllers/AdminController.cs`

```csharp
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize(Policy = "AdminOnly")]
public class AdminController : ControllerBase
{
   [HttpGet("dashboard")]
   public IActionResult Dashboard()
   {
      return Ok(new { message = "Admin only dashboard" });
   }
}
```

## 5.11 Program.cs – DB, JWT, CORS, DI

Replace `Program.cs` with:

```csharp
using System.Text;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Api.Data;
using Api.Models;
using Api.Services;

var builder = WebApplication.CreateBuilder(args);

// Build SQL Server connection string from env / Infisical
string BuildConnectionString(IConfiguration config)
{
   var host = config["DB_HOST"] ?? "localhost";
   var port = config["DB_PORT"] ?? "14333";
   var db   = config["DB_NAME"] ?? "MyAppDb";
   var user = config["DB_USER"] ?? "sa";
   var pass = config["DB_PASSWORD"]
      ?? throw new InvalidOperationException("DB_PASSWORD is not set");

   return $"Server={host},{port};Database={db};User Id={user};Password={pass};TrustServerCertificate=True;";
}

var connectionString = BuildConnectionString(builder.Configuration);

builder.Services.AddDbContext<AppDbContext>(options =>
   options.UseSqlServer(connectionString));

builder.Services.AddScoped<IUserService, UserService>();
builder.Services.AddScoped<IPasswordHasher<AppUser>, PasswordHasher<AppUser>>();

// JWT config
var jwtKey = builder.Configuration["JWT_SECRET"]
   ?? throw new InvalidOperationException("JWT_SECRET is not set");
var jwtIssuer = builder.Configuration["JWT_ISSUER"] ?? "secureapp-api";
var jwtAudience = builder.Configuration["JWT_AUDIENCE"] ?? "secureapp-client";

var signingKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtKey));

builder.Services
   .AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
   .AddJwtBearer(options =>
   {
      options.TokenValidationParameters = new TokenValidationParameters
      {
         ValidateIssuer = true,
         ValidIssuer = jwtIssuer,
         ValidateAudience = true,
         ValidAudience = jwtAudience,
         ValidateIssuerSigningKey = true,
         IssuerSigningKey = signingKey,
         ValidateLifetime = true,
         ClockSkew = TimeSpan.FromMinutes(1)
      };
   });

builder.Services.AddAuthorization(options =>
{
   options.AddPolicy("AdminOnly", policy => policy.RequireRole("ADMIN"));
});

builder.Services.AddControllers();

builder.Services.AddCors(options =>
{
   options.AddPolicy("SpaDev", policy =>
   {
      policy.WithOrigins("http://localhost:4200")
            .AllowAnyHeader()
            .AllowAnyMethod();
   });
});

var app = builder.Build();

if (!app.Environment.IsDevelopment())
{
   app.UseExceptionHandler("/error");
   app.UseHsts();
}

app.UseHttpsRedirection();
app.UseStaticFiles();

app.UseRouting();

app.UseCors("SpaDev");

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();
```

## 5.12 Migrations

Install EF tool once if needed:

```bash
dotnet tool install --global dotnet-ef
```

From `SecureApp/Api`:

```bash
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- dotnet ef migrations add InitialSqlServer
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- dotnet ef database update
```
for example:
```bash 
infisical run --env=dev --projectId f1d7ef76-6d48-455f-af61-cc011057c5b3 -- dotnet ef migrations add InitialSqlServer
infisical run --env=dev --projectId f1d7ef76-6d48-455f-af61-cc011057c5b3 -- dotnet ef database update

```

---

# 6. Frontend: Angular SPA

## 6.1 Create Angular project

From `SecureApp`:

```bash
ng new client --routing true --style css
cd client
```

## 6.2 Proxy for backend

Create `proxy.conf.json`:
(put the bff address and port)

```json
{
  "/api": {
    "target": "http://localhost:5247",
    "secure": false,
    "changeOrigin": true
  }
}
```

In `package.json`:

```json
"scripts": {
  "start": "ng serve --proxy-config proxy.conf.json",
  "build": "ng build",
  "test": "ng test",
  "lint": "ng lint"
}
```

## 6.3 App module basics

`src/app/app.module.ts`:

```ts
import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { SetupComponent } from './pages/setup/setup.component';
import { RegisterComponent } from './pages/register/register.component';
import { LoginComponent } from './pages/login/login.component';
import { AuthTokenInterceptor } from './interceptors/auth-token.interceptor';

@NgModule({
  declarations: [
    AppComponent,
    SetupComponent,
    RegisterComponent,
    LoginComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
    FormsModule
  ],
  providers: [
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthTokenInterceptor,
      multi: true
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
```

## 6.4 Routing

`src/app/app-routing.module.ts`:

```ts
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SetupComponent } from './pages/setup/setup.component';
import { RegisterComponent } from './pages/register/register.component';
import { LoginComponent } from './pages/login/login.component';

const routes: Routes = [
  { path: 'setup', component: SetupComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'login', component: LoginComponent },
  { path: '', redirectTo: 'register', pathMatch: 'full' },
  { path: '**', redirectTo: 'register' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
```

## 6.5 Auth service

`src/app/services/auth.service.ts`:

```ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

export interface MeResponse {
  userName: string;
  roles: string[];
}

export interface LoginResponse {
  token: string;
  user: MeResponse;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private baseUrl = '/api/account';
  private tokenKey = 'auth_token';

  constructor(private http: HttpClient) { }

  isFirstUser(): Observable<boolean> {
    return this.http.get<boolean>(`${this.baseUrl}/is-first-user`);
  }

  setup(userName: string, password: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/setup`, { userName, password });
  }

  register(userName: string, password: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/register`, { userName, password });
  }

  login(userName: string, password: string): Observable<MeResponse> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/login`, { userName, password })
      .pipe(
        map(res => {
          this.setToken(res.token);
          return res.user;
        })
      );
  }

  logout(): void {
    this.clearToken();
  }

  me(): Observable<MeResponse | null> {
    return this.http.get<MeResponse>(`${this.baseUrl}/me`).pipe(
      map(x => x ?? null)
    );
  }

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  private setToken(token: string): void {
    localStorage.setItem(this.tokenKey, token);
  }

  private clearToken(): void {
    localStorage.removeItem(this.tokenKey);
  }
}
```

## 6.6 HTTP interceptor

`src/app/interceptors/auth-token.interceptor.ts`:

```ts
import { Injectable } from '@angular/core';
import {
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest
} from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from '../services/auth.service';

@Injectable()
export class AuthTokenInterceptor implements HttpInterceptor {

  constructor(private auth: AuthService) { }

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const token = this.auth.getToken();

    if (!token) {
      return next.handle(req);
    }

    const authReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });

    return next.handle(authReq);
  }
}
```

## 6.7 Setup page

`src/app/pages/setup/setup.component.ts`:

```ts
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-setup',
  templateUrl: './setup.component.html'
})
export class SetupComponent implements OnInit {

  userName = '';
  password = '';
  error = '';

  constructor(private auth: AuthService, private router: Router) { }

  ngOnInit(): void {
    this.auth.isFirstUser().subscribe(isFirst => {
      if (!isFirst) {
        this.router.navigate(['/register']);
      }
    });
  }

  onSubmit(): void {
    this.error = '';
    this.auth.setup(this.userName, this.password).subscribe({
      next: () => this.router.navigate(['/login'], { queryParams: { setup: 'success' } }),
      error: err => {
        this.error = err.error?.error ?? 'Setup failed';
      }
    });
  }
}
```

`src/app/pages/setup/setup.component.html`:

```html
<div class="container">
  <h1>First-Time Setup</h1>

  <div class="info">
    <strong>Welcome!</strong> Create the first admin account to get started.
  </div>

  <div *ngIf="error" class="error">
    {{ error }}
  </div>

  <form (ngSubmit)="onSubmit()">
    <div class="form-group">
      <label for="userName">Username:</label>
      <input id="userName" name="userName" [(ngModel)]="userName" required />
    </div>

    <div class="form-group">
      <label for="password">Password:</label>
      <input id="password" name="password" type="password" [(ngModel)]="password" required minlength="8" />
      <small class="hint">
        Password must contain:
        <ul>
          <li>At least 8 characters</li>
          <li>One uppercase letter (A-Z)</li>
          <li>One lowercase letter (a-z)</li>
          <li>One number (0-9)</li>
          <li>One special character (!@#$%^&*)</li>
        </ul>
      </small>
    </div>

    <button type="submit">Create Admin Account</button>
  </form>
</div>
```

## 6.8 Register page

`src/app/pages/register/register.component.ts`:

```ts
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-register',
  templateUrl: './register.component.html'
})
export class RegisterComponent implements OnInit {

  userName = '';
  password = '';
  error = '';

  constructor(private auth: AuthService, private router: Router) { }

  ngOnInit(): void {
    this.auth.isFirstUser().subscribe(isFirst => {
      if (isFirst) {
        this.router.navigate(['/setup']);
      }
    });
  }

  onSubmit(): void {
    this.error = '';
    this.auth.register(this.userName, this.password).subscribe({
      next: () => this.router.navigate(['/login'], { queryParams: { registered: 'true' } }),
      error: err => {
        this.error = err.error?.error ?? 'Registration failed';
      }
    });
  }
}
```

`src/app/pages/register/register.component.html`:

```html
<div class="container">
  <h1>Register</h1>

  <div *ngIf="error" class="error">
    {{ error }}
  </div>

  <form (ngSubmit)="onSubmit()">
    <div class="form-group">
      <label for="userName">Username:</label>
      <input id="userName" name="userName" [(ngModel)]="userName" required />
    </div>

    <div class="form-group">
      <label for="password">Password:</label>
      <input id="password" name="password" type="password" [(ngModel)]="password" required minlength="8" />
      <small class="hint">
        Password must contain:
        <ul>
          <li>At least 8 characters</li>
          <li>One uppercase letter (A-Z)</li>
          <li>One lowercase letter (a-z)</li>
          <li>One number (0-9)</li>
          <li>One special character (!@#$%^&*)</li>
        </ul>
      </small>
    </div>

    <button type="submit">Register</button>
  </form>

  <p>
    Already have an account?
    <a routerLink="/login">Login here</a>
  </p>
</div>
```

## 6.9 Login page

`src/app/pages/login/login.component.ts`:

```ts
import { Component } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService, MeResponse } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html'
})
export class LoginComponent {

  userName = '';
  password = '';
  error = '';
  info = '';

  constructor(
    private auth: AuthService,
    private router: Router,
    route: ActivatedRoute
  ) {
    const setup = route.snapshot.queryParamMap.get('setup');
    const registered = route.snapshot.queryParamMap.get('registered');
    if (setup === 'success') {
      this.info = 'Admin setup completed. Please log in.';
    } else if (registered === 'true') {
      this.info = 'Registration successful. Please log in.';
    }
  }

  onSubmit(): void {
    this.error = '';
    this.info = '';
    this.auth.login(this.userName, this.password).subscribe({
      next: (me: MeResponse) => {
        this.router.navigate(['/']);
      },
      error: err => {
        this.error = err.error?.error ?? 'Login failed';
      }
    });
  }
}
```

`src/app/pages/login/login.component.html`:

```html
<div class="container">
  <h1>Login</h1>

  <div *ngIf="info" class="info">
    {{ info }}
  </div>

  <div *ngIf="error" class="error">
    {{ error }}
  </div>

  <form (ngSubmit)="onSubmit()">
    <div class="form-group">
      <label for="userName">Username:</label>
      <input id="userName" name="userName" [(ngModel)]="userName" required />
    </div>

    <div class="form-group">
      <label for="password">Password:</label>
      <input id="password" name="password" type="password" [(ngModel)]="password" required />
    </div>

    <button type="submit">Login</button>
  </form>
</div>
```

You can add simple CSS in `src/styles.css` for `.container`, `.form-group`, `.info`, `.error`.

---

# 7. Run everything

## 7.1 SQL Server

From `SecureApp`:

```bash
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- docker compose up -d
```

## 7.2 Apply migrations (once)

From `SecureApp/Api`:

```bash
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- dotnet ef database update
```

## 7.3 Run backend

From `SecureApp/Api`:

```bash
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- dotnet run
```
For example:
```bash
infisical run --env=dev --projectId f1d7ef76-6d48-455f-af61-cc011057c5b3 -- dotnet run
```

API at `http://localhost:5120`.

## 7.4 Run Angular

From `SecureApp/client`:

```bash
npm install
npm start
```

Angular at `http://localhost:4200`.

---

# 8. Test flows

1. Visit `http://localhost:4200/register`
   Angular sees `isFirstUser === true` and redirects to `/setup`.

2. On `/setup`, create first user (for example `admin` / `StrongP@ss2024!`).
   That user becomes `ADMIN,USER`.

3. Go to `/login` and log in.
   Angular receives JWT, stores it, and sends `Authorization: Bearer` on all future requests.

4. Optionally call `/api/admin/dashboard` from dev tools or later from Angular, you should get `{ message: "Admin only dashboard" }` as admin and 403 as normal user.

---

If you want, next step can be:

* Add Angular route guards for `/admin` that check `me()` result and role, or
* Swap the password hasher to Argon2 instead of the built in PBKDF2.
