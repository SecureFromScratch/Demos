using MassAssignLab.Auth;
using MassAssignLab.Contracts.Users;
using MassAssignLab.Data;
using MassAssignLab.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace MassAssignLab.Controllers;

[ApiController]
[Route("users")]
public class UsersController : ControllerBase
{
   private readonly AppDbContext _db;
   public UsersController(AppDbContext db) => _db = db;

   [Authorize(Policy = AuthorizationSetup.CanEditUserPolicy)]
   [HttpPatch("{id:int}")]
   public async Task<IActionResult> Patch(int id, [FromBody] UserUpdateRequest req)
   {
      if (req is null) return BadRequest();

      if (!TryValidateModel(req)) return ValidationProblem(ModelState);
   
      var user = await _db.Users.FirstOrDefaultAsync(u => u.Id == id);
      if (user is null) return NotFound();
      

      req.ApplyTo(user);
      await _db.SaveChangesAsync();

      return Ok(new { user.Id, user.Username, user.FullName, user.Email });
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