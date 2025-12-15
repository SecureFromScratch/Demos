// Program.cs
using SecureDownloads;   



var builder = WebApplication.CreateBuilder(args);

// Minimal demo auth (everyone is "authenticated")
builder.Services.AddAuthentication("Demo").AddScheme<DemoAuthOptions, DemoAuthHandler>("Demo", _ => {});

// Coarse policy
builder.Services.AddAuthorization(options =>
{
   options.AddPolicy("CanDownloadFiles", p => p.RequireAuthenticatedUser());
});


builder.Services.AddSecureDownloads(builder.Configuration);
builder.Services.AddSingleton<IFileDownloadService, FileDownloadService>();

builder.Services.AddControllers();

var app = builder.Build();

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();


