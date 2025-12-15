// Controllers/DiagnosticsController.cs
using System;
using System.Text.Json;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace Bff.Controllers;

[ApiController]
[Route("bff")]
public sealed class DiagnosticsController : ControllerBase
{
   // GET /bff/health
   [HttpGet("health")]
   [AllowAnonymous]
   public IActionResult Health()
   {
      return Ok(new
      {
         status = "ok",
         utc = DateTimeOffset.UtcNow
      });
   }

   // POST /bff/echo
   [HttpPost("echo")]
   [AllowAnonymous]
   [Consumes("application/json")]
   [Produces("application/json")]
   public IActionResult Echo([FromBody] JsonElement body)
   {
      // Returns exactly what was sent (handy for CORS/preflight testing)
      return Ok(body);
   }
}
