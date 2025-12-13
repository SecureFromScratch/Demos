using Microsoft.AspNetCore.Authentication.Cookies;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();

// HttpClient for calling the API
builder.Services.AddHttpClient("Api", client =>
{
   var baseUrl = builder.Configuration["ApiBaseUrl"] ?? "https://localhost:5120/";
   client.BaseAddress = new Uri(baseUrl);
});

// Cookie auth for browser ↔ BFF
builder.Services
   .AddAuthentication("bff")
   .AddCookie("bff", options =>
   {
      options.LoginPath = "/bff/unauthorized";
      options.Cookie.Name = "bff_auth";
      options.Cookie.SameSite = SameSiteMode.Lax;
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

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();
