// Program.cs
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Builder;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using System.Security.Claims;
using System.Text.Encodings.Web;
using Microsoft.Extensions.Options;
using Microsoft.Extensions.Logging;



var builder = WebApplication.CreateBuilder(args);

// Minimal demo auth (everyone is "authenticated")
builder.Services.AddAuthentication("Demo").AddScheme<DemoAuthOptions, DemoAuthHandler>("Demo", _ => {});

// Coarse policy
builder.Services.AddAuthorization(options =>
{
   options.AddPolicy("CanDownloadFiles", p => p.RequireAuthenticatedUser());
});

builder.Services.AddControllers();

var app = builder.Build();

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();


