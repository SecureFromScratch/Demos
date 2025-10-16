using MassAssignLab.Auth;
using MassAssignLab.Data;
using MassAssignLab.Models;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddDbContext<AppDbContext>(opts => opts.UseInMemoryDatabase("lab"));
builder.Services.AddAppAuthorization();   // registers policy+handler (scoped) + IHttpContextAccessor
#if FAKE_AUTH
builder.Services.AddFakeAuth();           // dev-only auth
#else
builder.Services.AddAuthentication();
#endif

var app = builder.Build();

app.UseRouting();

#if FAKE_AUTH
app.UseFakeAuth();                        // dev-only authentication
Console.WriteLine("⚙️  Using FakeAuth (dev mode)");
#else
// In Release or production: use your real authentication
app.UseAuthentication();
#endif

app.UseAuthorization();

app.MapControllers();

// Seed a user for the lab
using (var scope = app.Services.CreateScope())
{
   var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
   if (!db.Users.Any())
   {
      db.Users.Add(new User
      {
         Id = 1,
         Username = "alice",
         Email = "alice@example.test",
         FullName = "Alice Example",
         Role = "user",
         IsActive = true
      });
      db.SaveChanges();
   }
}

app.Run();