using MassAssignLab.Data;
using MassAssignLab.Models;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace MassAssignLab.Controllers;

[ApiController]
[Route("users")]
public class UsersController : ControllerBase
{
   private readonly AppDbContext _db;
   public UsersController(AppDbContext db) => _db = db;

   
   [HttpPatch("{id:int}")]
   public async Task<IActionResult> Patch(int id, [FromBody] User dto)
   {
      if (dto is null) return BadRequest("Body required.");
      if (dto.Id != id) return BadRequest("ID mismatch.");

      _db.Attach(dto);
      _db.Entry(dto).State = EntityState.Modified; 

      await _db.SaveChangesAsync();
      return Ok(dto);
   }

// GET /users/{id}
[HttpGet("{id:int}")]
public async Task<IActionResult> Get(int id)
{
   var user = await _db.Users.FindAsync(id);
   if (user is null) return NotFound();
   return Ok(user); // in the lab we return the full entity so students can see Role/IsActive
}

}
