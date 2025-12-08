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
      return Results.Content(body, "application/json", statusCode: (int)resp.StatusCode);

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
   return Results.Content(body, "application/json", statusCode: (int)resp.StatusCode);

});

// Setup first admin → proxy to API /api/account/setup
app.MapPost("/bff/account/setup", async (
   LoginRequest req,
   IHttpClientFactory factory) =>
{
   var client = factory.CreateClient("Api");
   var resp = await client.PostAsJsonAsync("api/account/setup", req);
   var body = await resp.Content.ReadAsStringAsync();
   return Results.Content(body, "application/json", statusCode: (int)resp.StatusCode);

});

// Register normal user → proxy to API /api/account/register
app.MapPost("/bff/account/register", async (
   LoginRequest req,
   IHttpClientFactory factory) =>
{
   var client = factory.CreateClient("Api");
   var resp = await client.PostAsJsonAsync("api/account/register", req);
   var body = await resp.Content.ReadAsStringAsync();
   return Results.Content(body, "application/json", statusCode: (int)resp.StatusCode);

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
   return Results.Content(body, "application/json", statusCode: (int)resp.StatusCode);

});

app.Run();

// DTOs matching the API's JSON
public record LoginRequest(string UserName, string Password);
public record MeResponse(string UserName, string[] Roles);
public record LoginResponse(string Token, MeResponse User);
