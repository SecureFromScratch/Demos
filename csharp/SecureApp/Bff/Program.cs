using Microsoft.AspNetCore.Antiforgery;
using Microsoft.AspNetCore.Authentication.Cookies;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();

// HttpClient for calling the API
builder.Services.AddHttpClient("Api", client =>
{
   //var baseUrl = builder.Configuration["ApiBaseUrl"] ?? "https://localhost:5120/";
   var baseUrl = builder.Configuration["ApiBaseUrl"] ?? "https://localhost:7253/";
   client.BaseAddress = new Uri(baseUrl);
});

// Cookie auth for browser ↔ BFF
builder.Services
   .AddAuthentication("bff")
   .AddCookie("bff", options =>
   {
      options.LoginPath = "/bff/unauthorized";
      options.Cookie.Name = "bff_auth";
      // Use Lax (the default). This works fine between localhost:5555 and localhost:4200
      //options.Cookie.SameSite = SameSiteMode.None;
      options.Cookie.SameSite = SameSiteMode.Lax;
      // Remove the Secure policy (or set to SameAsRequest)
      options.Cookie.SecurePolicy = CookieSecurePolicy.Always;

   });

builder.Services.AddAuthorization();

#region csrf protection
builder.Services.AddAntiforgery(options =>
{
      options.HeaderName = "X-XSRF-TOKEN"; 
});
#endregion

// CORS for Angular dev (4200 → 5002)
builder.Services.AddCors(options =>
{
   /*options.AddPolicy("SpaDev", policy =>
   {
      policy.WithOrigins("http://localhost:4200","http://localhost:5555")
            .AllowAnyHeader()
            .AllowAnyMethod()
            .AllowCredentials();
   });
   */
   // ⚠️ DANGEROUS: This creates the "Login CSRF" vulnerability
   options.AddPolicy("SpaDev", policy =>
   {
      policy.SetIsOriginAllowed(origin => true) // <--- Accepts ANY origin and reflects it back
         .AllowAnyHeader()
         .AllowAnyMethod()
         .AllowCredentials();
   });
});

builder.Services.AddScoped<Bff.Services.ApiProxy>();


var app = builder.Build();

if (!app.Environment.IsDevelopment())
{
   app.UseExceptionHandler("/error");
   app.UseHsts();
}

app.UseHttpsRedirection();
app.UseRouting();

app.UseCors("SpaDev");

# region csrf protection
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
#endregion

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();

