using System.Text;
using Microsoft.AspNetCore.Identity;
using Api.Data;
using Api.Models;
using Api.Services;
using Microsoft.EntityFrameworkCore;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;



var builder = WebApplication.CreateBuilder(args);

// Build SQL Server connection string from env / Infisical
string BuildConnectionString(IConfiguration config)
{
   var host = config["DB_HOST"] ?? "localhost";
   var port = config["DB_PORT"] ?? "14333";
   var db = config["DB_NAME"] ?? "MyAppDb";
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

builder.Services.AddScoped<Api.Services.ITaskService, Api.Services.TaskService>();
builder.Services.AddScoped<Api.Services.IRecipeService, Api.Services.RecipeService>();

builder.Services.AddControllers();

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
   c.SwaggerDoc("v1", new OpenApiInfo
   {
      Title = "SecureApp API",
      Version = "v1"
   });

   // Define the BearerAuth scheme
   var securityScheme = new OpenApiSecurityScheme
   {
      Name = "Authorization",
      Description = "Enter: Bearer {your JWT}",
      In = ParameterLocation.Header,
      Type = SecuritySchemeType.Http,
      Scheme = "bearer",
      BearerFormat = "JWT",
      Reference = new OpenApiReference
      {
         Type = ReferenceType.SecurityScheme,
         Id = "Bearer"
      }
   };

   c.AddSecurityDefinition("Bearer", securityScheme);

   var securityRequirement = new OpenApiSecurityRequirement
   {
      {
         securityScheme,
         Array.Empty<string>()
      }
   };

   c.AddSecurityRequirement(securityRequirement);
});

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
   app.UseSwagger();
   app.UseSwaggerUI();
}
else
{
   app.UseExceptionHandler("/error");
   app.UseHsts();
}
//just once

using (var scope = app.Services.CreateScope())
{
   var services = scope.ServiceProvider;
   var db = services.GetRequiredService<AppDbContext>();
   var logger = services.GetRequiredService<ILoggerFactory>()
      .CreateLogger("DbInitializer");

   // Apply migrations automatically (optional but handy in dev)
   db.Database.Migrate();

   await Api.Data.Seed.TasksSeedData.SeedAsync(db, logger);
}

app.UseStaticFiles(new StaticFileOptions
{
   FileProvider = new Microsoft.Extensions.FileProviders.PhysicalFileProvider(
      Path.Combine(app.Environment.ContentRootPath, "uploads")),
   RequestPath = "/uploads"
});


// end of just once
app.UseHttpsRedirection();
app.UseStaticFiles();

app.UseRouting();

app.UseCors("SpaDev");

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();
