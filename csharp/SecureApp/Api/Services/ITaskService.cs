// Api/Services/ITaskService.cs
using Api.Models;
using System.Collections.Generic;
using System.Threading.Tasks;
using TaskEntity = Api.Models.Task;

namespace Api.Services
{
   public interface ITaskService
   {
      Task<List<TaskEntity>> GetAllAsync();
      Task<TaskEntity?> GetByIdAsync(long id);
      Task<TaskEntity> CreateAsync(TaskEntity task, string currentUser);
      Task<TaskEntity?> UpdateAsync(long id, TaskEntity updated);
      Task<bool> DeleteAsync(long id);
   }
}
