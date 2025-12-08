// Api/Services/TaskService.cs
using Api.Data;
using Api.Models;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using TaskEntity = Api.Models.Task;

namespace Api.Services
{
   public class TaskService : ITaskService
   {
      private readonly AppDbContext m_context;
      private readonly ILogger<TaskService> m_logger;

      public TaskService(AppDbContext context, ILogger<TaskService> logger)
      {
         m_context = context;
         m_logger = logger;
      }

      public async Task<List<TaskEntity>> GetAllAsync()
      {
         return await m_context.Tasks
            .OrderByDescending(t => t.CreateDate)
            .ToListAsync();
      }

      public async Task<TaskEntity?> GetByIdAsync(long id)
      {
         return await m_context.Tasks
            .FirstOrDefaultAsync(t => t.Id == id);
      }

      public async Task<TaskEntity> CreateAsync(TaskEntity task, string currentUser)
      {
         task.CreatedBy = currentUser;
         task.OnCreate();

         m_context.Tasks.Add(task);
         await m_context.SaveChangesAsync();
         return task;
      }

      public async Task<TaskEntity?> UpdateAsync(long id, TaskEntity updated)
      {
         var existing = await m_context.Tasks.FirstOrDefaultAsync(t => t.Id == id);
         if (existing == null)
         {
            return null;
         }

         existing.Name = updated.Name;
         existing.Description = updated.Description;
         existing.AssignTo = updated.AssignTo;
         existing.Status = updated.Status;

         await m_context.SaveChangesAsync();
         return existing;
      }

      public async Task<bool> DeleteAsync(long id)
      {
         var existing = await m_context.Tasks.FirstOrDefaultAsync(t => t.Id == id);
         if (existing == null)
         {
            return false;
         }

         m_context.Tasks.Remove(existing);
         await m_context.SaveChangesAsync();
         return true;
      }
   }
}
