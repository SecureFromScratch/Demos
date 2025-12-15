using MassAssignLab.Data;
using MassAssignLab.Models;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddDbContext<AppDbContext>(opts => opts.UseInMemoryDatabase("lab"));

var app = builder.Build();

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
