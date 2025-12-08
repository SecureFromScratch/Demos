## Allowing anonymous access

Sometimes you want:

* Most endpoints: **authenticated** (and maybe role/permission checked)
* A few endpoints: **public** (login, register, health, maybe a public “tasks overview” etc.)

In ASP.NET Core this is done with **two ideas**:

1. **Require auth by default** (recommended)
2. Use `[AllowAnonymous]` on the few actions that must be public

---

### 1. Require auth by default

In `Program.cs`:

```csharp
builder.Services.AddAuthorization(options =>
{
   // Fallback policy = applied when no [Authorize]/[AllowAnonymous] is present
   options.FallbackPolicy = new AuthorizationPolicyBuilder()
      .RequireAuthenticatedUser()
      .Build();
});
```

Effect:

* Any controller / action **without** `[AllowAnonymous]` or a specific `[Authorize]` → requires a logged-in user.

Best practice:
This is safer than “opt-in” authorization, because new endpoints are protected by default.

---

### 2. Make specific endpoints anonymous with `[AllowAnonymous]`

**Attribute**:

```csharp
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

[Route("api/auth")]
[ApiController]
public class AuthController : ControllerBase
{
   // Public: login endpoint
   [HttpPost("login")]
   [AllowAnonymous]
   public async Task<IActionResult> Login([FromBody] LoginRequest request)
   {
      // login logic...
      return Ok();
   }

   // Public: register endpoint
   [HttpPost("register")]
   [AllowAnonymous]
   public async Task<IActionResult> Register([FromBody] RegisterRequest request)
   {
      // registration logic...
      return Ok();
   }
}
```

Important details:

* `[AllowAnonymous]` **overrides** `[Authorize]` on the controller or global policy.
  So:

```csharp
[Authorize]
[Route("api/tasks")]
[ApiController]
public class TasksController : ControllerBase
{
   // This is *still protected*
   [HttpGet]
   public async Task<IActionResult> GetAll() { ... }

   // This one is public even though controller has [Authorize]
   [HttpGet("public-info")]
   [AllowAnonymous]
   public IActionResult GetPublicInfo()
   {
      return Ok(new { Message = "This is visible to everyone" });
   }
}
```

---

### 3. Combine with RBAC / resource-based rules

The delete example stays the same (must be logged-in, plus policy):

```csharp
[Authorize] // or omit when fallback policy requires auth anyway
[HttpDelete("{id:long}")]
public async Task<IActionResult> Delete(long id)
{
   var task = await m_tasks.GetByIdAsync(id);
   if (task == null) return NotFound();

   var result = await m_auth.AuthorizeAsync(User, task, "CanDeleteTask");
   if (!result.Succeeded) return Forbid();

   await m_tasks.DeleteAsync(id);
   return NoContent();
}
```

---

### Best practices for anonymous endpoints

* Keep the anonymous surface **as small as possible** (login, register, health, maybe docs).
* Never allow anonymous on **state-changing** endpoints (`POST`, `PUT`, `DELETE`) unless you really mean it.
* Be careful what data you expose in anonymous GETs (no personal info, PII, internal IDs if not needed).
* With fallback policy enabled, you can quickly see “special” endpoints: search for `[AllowAnonymous]` in the solution.

