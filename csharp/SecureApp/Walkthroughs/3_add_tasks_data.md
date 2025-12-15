### 1. Make sure the `Tasks` table exists

From `Api/` folder, after adding the entity and DbSet:

```bash
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- dotnet ef migrations add AddTasks
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- dotnet ef database update
```

(If you already have migrations, just add another one.)

---

### 2. Add a simple data seeder

Create this file:

```csharp
// File: Api/Data/Seed/TasksSeedData.cs
using Api.Data;
using Microsoft.Extensions.Logging;
using System;
using System.Linq;
using System.Threading.Tasks;
using TaskEntity = Api.Models.Task;
using TaskStatusEntity = Api.Models.TaskStatus;

namespace Api.Data.Seed
{
   public static class TasksSeedData
   {
      public static async Task SeedAsync(AppDbContext context, ILogger logger)
      {
         if (context.Tasks.Any())
         {
            return;
         }

         logger.LogInformation("Seeding initial tasks...");

         var now = DateTime.UtcNow;

         var tasks = new[]
         {
            new TaskEntity
            {
               Name = "Set up project",
               Description = "Create .NET API + BFF + Angular task app",
               CreatedBy = "Or",
               AssignTo = "Or",
               Status = TaskStatusEntity.InProgress,
               CreateDate = now
            },
            new TaskEntity
            {
               Name = "Write documentation",
               Description = "Add README with setup instructions",
               CreatedBy = "Or",
               AssignTo = "Dev1",
               Status = TaskStatusEntity.Todo,
               CreateDate = now
            },
            new TaskEntity
            {
               Name = "Design database schema",
               Description = "Create initial ERD and normalize tables for tasks module",
               CreatedBy = "Dev1",
               AssignTo = "Dev2",
               Status = TaskStatusEntity.Todo,
               CreateDate = now
            },
            new TaskEntity
            {
               Name = "Implement Task controller",
               Description = "Add CRUD endpoints and connect to service layer",
               CreatedBy = "Dev2",
               AssignTo = "Dev2",
               Status = TaskStatusEntity.InProgress,
               CreateDate = now
            },
            new TaskEntity
            {
               Name = "UI layout for task list",
               Description = "Create Angular components for list and form views",
               CreatedBy = "Dev3",
               AssignTo = "Dev3",
               Status = TaskStatusEntity.Todo,
               CreateDate = now
            },
            new TaskEntity
            {
               Name = "Add validation and error messages",
               Description = "Use DataAnnotations and Angular validation",
               CreatedBy = "Or",
               AssignTo = "Dev1",
               Status = TaskStatusEntity.Todo,
               CreateDate = now
            },
            new TaskEntity
            {
               Name = "Smoke test in staging",
               Description = "Run create, edit, delete, list flows",
               CreatedBy = "QA1",
               AssignTo = "QA1",
               Status = TaskStatusEntity.Todo,
               CreateDate = now
            },
            new TaskEntity
            {
               Name = "Prepare deployment checklist",
               Description = "Define steps for config, DB migrations, and rollback plan",
               CreatedBy = "PM1",
               AssignTo = "PM1",
               Status = TaskStatusEntity.Todo,
               CreateDate = now
            }
         };

         await context.Tasks.AddRangeAsync(tasks);
         await context.SaveChangesAsync();

         logger.LogInformation("Seeding initial tasks completed.");
      }
   }
}
```

---

### 3. Call the seeder on startup

Edit `Api/Program.cs` (top-level statements file):

```csharp
using Api.Data;
using Api.Data.Seed;
using Microsoft.EntityFrameworkCore;
// ... your other usings

var builder = WebApplication.CreateBuilder(args);

// existing builder.Services...

var app = builder.Build();

// Ensure DB + seed tasks
using (var scope = app.Services.CreateScope())
{
   var services = scope.ServiceProvider;
   var db = services.GetRequiredService<AppDbContext>();
   var logger = services.GetRequiredService<ILoggerFactory>()
      .CreateLogger("DbInitializer");

   // Apply migrations automatically (optional but handy in dev)
   db.Database.Migrate();

   await TasksSeedData.SeedAsync(db, logger);
}

// existing middleware pipeline
// app.UseAuthentication();
// app.UseAuthorization();
// app.MapControllers();

app.Run();
```

Now:

1. `dotnet ef database update` (if not done).

```bash
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- dotnet ef migrations add AddTasks
```

```bash
infisical run --env=dev --projectId  <YOUR_PROJECT_ID> -- dotnet ef database update
```
2. Run the API.
3. Hit `GET /api/tasks` (or via BFF `/bff/tasks`) and you should see demo data.


