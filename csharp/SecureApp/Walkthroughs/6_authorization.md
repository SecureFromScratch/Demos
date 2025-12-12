
# Authorization (ASP.NET Core)

## Authorization models

### 1. RBAC - Role Based Access Control

**Idea:** Users get roles, roles have permissions, so users gain permissions through their roles.

**Core pieces:**

* Users
* Roles, for example `User`, `Admin`
* Permissions, for example `TaskRead`, `TaskDelete`
* Role to permission mapping

**Pros:**

* Easy to explain and manage for small to medium systems
* Good for job based access, for example admin, manager, viewer

**Cons:**

* Can lead to role explosion if you try to encode every business nuance into roles
* Not expressive enough alone for ownership rules such as "user can only see their own tasks"



---

### 2. Permission based access (PBAC)

**Idea:** Users or roles are linked directly to fine grained permissions, not just coarse roles.

Example structure:

* Permissions: `TaskReadOwn`, `TaskReadAll`, `TaskDelete`, `ReportExport`
* Roles are bundles of permissions, or you assign permissions directly as claims to users

**Pros:**

* More expressive than pure RBAC
* Easier to avoid role explosion if you treat roles as small bundles

**Cons:**

* More complex to design
* You must maintain a clear permission catalog


---

### 3. ABAC - Attribute Based Access Control

**Idea:** Decisions are based on attributes of user, resource, and environment.

Examples of attributes:

* User: department, country, clearance level
* Resource: classification, owner department
* Environment: time of day, client IP, risk score

Policies look like:

* "Allow access if `user.department == resource.department` and `resource.classification <= user.clearance`"

**Pros:**

* Very expressive
* Useful in large organizations and multi tenant or regulated systems

**Cons:**

* More difficult to reason about and test
* Needs a policy engine and good tooling

---


### 4. ReBAC - Relationship Based Access Control

**Idea:** Decisions are based on relationships like "owner of", "member of group", "manager of".

Examples:

* "User can edit a document if user is the owner or is a member of a group that has edit rights on that document."
* Used in social networks, GitHub style orgs and repos

**Pros:**

* Models real world collaboration nicely
* Fits graph data and microservices well

**Cons:**

* Implementation is more complex, needs good modeling of relationships
* Harder to implement only with static roles

---

## Design best practices

* Start with RBAC plus ownership checks, do not over engineer on day one
* Avoid role explosion, keep a small role set and add permissions or attributes when needed
* Centralize role and permission names in constants or enums
* Deny by default, explicitly allow required operations
* Write tests for "allowed" and "forbidden" cases, especially for ownership

---


## Samples

### 1. Allow delete only to admins or the user who created the task

(RBAC and ReBAC - admin role or owner)

Assume you have `TaskEntity` with `CreatedBy`.

**Policy setup** in `Program.cs`:

```csharp
public class CanDeleteTaskRequirement : IAuthorizationRequirement
{
}

public class CanDeleteTaskHandler
   : AuthorizationHandler<CanDeleteTaskRequirement, TaskEntity>
{
   protected override Task HandleRequirementAsync(
      AuthorizationHandlerContext context,
      CanDeleteTaskRequirement requirement,
      TaskEntity resource)
   {
      var userName = context.User.Identity?.Name;
      var isAdmin = context.User.IsInRole("Admin");
      var isOwner = userName != null && userName == resource.CreatedBy;

      if (isAdmin || isOwner)
      {
         context.Succeed(requirement);
      }

      return Task.CompletedTask;
   }
}
```

Register:

```csharp
builder.Services.AddAuthorization(options =>
{
   options.AddPolicy("CanDeleteTask", policy =>
      policy.Requirements.Add(new CanDeleteTaskRequirement()));
});

builder.Services.AddSingleton<IAuthorizationHandler, CanDeleteTaskHandler>();
```

**Use in controller**:

```csharp
[Authorize]
[Route("api/tasks")]
[ApiController]
public class TasksController : ControllerBase
{
   private readonly ITaskService m_tasks;
   private readonly IAuthorizationService m_auth;

   public TasksController(ITaskService tasks, IAuthorizationService auth)
   {
      m_tasks = tasks;
      m_auth = auth;
   }

   [HttpDelete("{id:long}")]
   public async Task<IActionResult> Delete(long id)
   {
      var task = await m_tasks.GetByIdAsync(id);
      if (task == null)
      {
         return NotFound();
      }

      var result = await m_auth.AuthorizeAsync(User, task, "CanDeleteTask");
      if (!result.Succeeded)
      {
         return Forbid();
      }

      await m_tasks.DeleteAsync(id);
      return NoContent();
   }
}
```
If delete is “admins only”, the attribute is enough:

```csharp
[Authorize(Policy = "TaskDelete")] // e.g. RequireRole("Admin") or RequireClaim("permission", "TaskDelete")
[HttpDelete("{id:long}")]
public async Task<IActionResult> Delete(long id)
{
   var task = await m_tasks.GetByIdAsync(id);
   if (task == null) return NotFound();

   await m_tasks.DeleteAsync(id);
   return NoContent();
}

```
and in Program.cs

```csharp
options.AddPolicy("TaskDelete",
   policy => policy.RequireRole("Admin"));

```

---

### 2. Profile endpoint where each user sees their own data

(ReBAC - user can view their own profile)

Using ASP.NET Core Identity with `AppUser`.

```csharp
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;

[Authorize]
[Route("profile")]
public class ProfileController : Controller
{
   private readonly UserManager<AppUser> m_userManager;

   public ProfileController(UserManager<AppUser> userManager)
   {
      m_userManager = userManager;
   }

   [HttpGet("")]
   public async Task<IActionResult> ShowProfile()
   {
      var userName = User.Identity?.Name
         ?? throw new InvalidOperationException("User not logged in");

      var user = await m_userManager.FindByNameAsync(userName)
         ?? throw new InvalidOperationException("User not found");

     
      // For API style:
      return Ok(new
      {
         user.UserName,
         user.Email
         // any other safe fields
      });
   }
}
```

---

### 3. Debug endpoint to inspect the authentication object

Equivalent of the Spring `/debug` endpoint.

```csharp
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using System.Linq;
using System.Security.Claims;

[Authorize]
[Route("profile")]
public class ProfileController : Controller
{
   // other actions...

   [HttpGet("debug")]
   public IActionResult DebugAuth()
   {
      var auth = HttpContext.User;
      Console.WriteLine("=== AUTHENTICATION DEBUG ===");

      // 1. Principal
      Console.WriteLine($"Principal type: {auth.GetType().Name}");
      Console.WriteLine($"Principal: {auth}");

      // 2. Claims (roles, permissions, etc.)
      var authoritiesString = string.Join(", ",
         auth.Claims.Select(c => $"{c.Type}={c.Value}"));
      Console.WriteLine($"Claims: {authoritiesString}");

      // 3. Identity details
      var identity = auth.Identity;
      var isAuthenticated = identity?.IsAuthenticated == true;
      var name = identity?.Name ?? "(null)";

      Console.WriteLine($"Is Authenticated: {isAuthenticated}");
      Console.WriteLine($"Name: {name}");

      // 4. Build a simple view model or JSON
      var model = new
      {
         PrincipalType = auth.GetType().Name,
         Claims = authoritiesString,
         IsAuthenticated = isAuthenticated,
         Name = name
      };

      // For MVC view: return View(model);
      return Ok(model);
   }
}
```

