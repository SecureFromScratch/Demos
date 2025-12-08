// Api/Controllers/TasksController.cs
using Api.Models;
using Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Logging;
using System.Collections.Generic;
using System.Threading.Tasks;
using TaskEntity = Api.Models.Task;
using TaskStatusEntity = Api.Models.TaskStatus;

namespace Api.Controllers
{
   [ApiController]
   [Route("api/tasks")]
   //[Authorize(Policy = "TaskPolicy")]
   [Authorize]
   public class TasksController : ControllerBase
   {
      private readonly ITaskService m_service;
      private readonly ILogger<TasksController> m_logger;

      public TasksController(ITaskService service, ILogger<TasksController> logger)
      {
         m_service = service;
         m_logger = logger;
      }

      [HttpGet]
      public async Task<ActionResult<IEnumerable<TaskEntity>>> GetAll()
      {
         var tasks = await m_service.GetAllAsync();
         return Ok(tasks);
      }

      [HttpGet("{id:long}")]
      public async Task<ActionResult<TaskEntity>> GetById(long id)
      {
         var task = await m_service.GetByIdAsync(id);
         if (task == null)
         {
            return NotFound();
         }

         return Ok(task);
      }

      public class TaskCreateUpdateDto
      {
         public string Name { get; set; } = string.Empty;
         public string? Description { get; set; }
         public string? AssignTo { get; set; }

         // Use the alias, not bare TaskStatus
         public TaskStatusEntity Status { get; set; } = TaskStatusEntity.Todo;
      }

      [HttpPost]
      public async Task<ActionResult<TaskEntity>> Create([FromBody] TaskCreateUpdateDto dto)
      {
         var userName = User.Identity?.Name ?? "unknown";

         var entity = new TaskEntity
         {
            Name = dto.Name,
            Description = dto.Description,
            AssignTo = dto.AssignTo,
            Status = dto.Status
         };

         var created = await m_service.CreateAsync(entity, userName);
         return CreatedAtAction(nameof(GetById), new { id = created.Id }, created);
      }

      [HttpPut("{id:long}")]
      public async Task<ActionResult<TaskEntity>> Update(long id, [FromBody] TaskCreateUpdateDto dto)
      {
         var entity = new TaskEntity
         {
            Name = dto.Name,
            Description = dto.Description,
            AssignTo = dto.AssignTo,
            Status = dto.Status
         };

         var updated = await m_service.UpdateAsync(id, entity);
         if (updated == null)
         {
            return NotFound();
         }

         return Ok(updated);
      }

      [HttpDelete("{id:long}")]
      public async Task<IActionResult> Delete(long id)
      {
         var ok = await m_service.DeleteAsync(id);
         if (!ok)
         {
            return NotFound();
         }

         return NoContent();
      }
   }
}
