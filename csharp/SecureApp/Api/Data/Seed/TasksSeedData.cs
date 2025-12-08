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
