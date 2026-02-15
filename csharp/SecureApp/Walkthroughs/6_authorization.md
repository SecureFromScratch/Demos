# Authorization (ASP.NET Core)

## Authorization models (conceptual)

### 1. RBAC – Role Based Access Control

**Idea:** Users are assigned roles. Roles grant access.

**Examples**

* Roles: `User`, `Admin`
* Rules: “Admins can delete tasks”

**Pros**

* Simple
* Easy to explain and manage

**Cons**

* Role explosion
* Poor fit for ownership and context rules

---

### 2. PBAC – Permission Based Access Control

**Idea:** Access is controlled by fine-grained permissions, often carried as claims.

**Examples**

* Permissions: `Task.Read`, `Task.Delete`, `Report.Export`
* Roles become bundles of permissions

**Pros**

* More expressive than RBAC
* Reduces role explosion

**Cons**

* Requires a clear permission catalog

---

### 3. ABAC – Attribute Based Access Control

**Idea:** Decisions depend on attributes of user, resource, and environment.

**Examples**

* User: `department`, `tenantId`, `clearance`
* Resource: `ownerTenant`, `classification`
* Environment: `time`, `ip`, `riskScore`

**Pros**

* Very expressive
* Good for multi-tenant and regulated systems

**Cons**

* Harder to reason about
* Needs discipline and tooling

---

### 4. ReBAC – Relationship Based Access Control

**Idea:** Decisions are based on relationships between entities.

**Examples**

* Owner of resource
* Member of a group
* Manager of another user

**Pros**

* Models real-world collaboration well
* Natural fit for ownership rules

**Cons**

* Requires resource-aware checks
* More complex than static roles

---

## Policy-based authorization (ASP.NET Core mechanism)

**Important:**
Policy-based authorization is **not a separate model**.
It is the **framework mechanism** ASP.NET Core provides to implement **RBAC, PBAC, ABAC, and ReBAC** in a unified way.

### What a policy can enforce

* Roles (`RequireRole`)
* Permissions (`RequireClaim`)
* Attributes (custom logic)
* Relationships and ownership (resource-based handlers)

### Why policies matter

* Centralized authorization logic
* Composable rules
* Testable and explicit
* Works consistently across controllers, minimal APIs, and services

---

## Mapping models to policies

| Model | How it’s implemented                          |
| ----- | --------------------------------------------- |
| RBAC  | `RequireRole("Admin")`                        |
| PBAC  | `RequireClaim("permission", "Task.Delete")`   |
| ABAC  | Custom requirement checking claims/attributes |
| ReBAC | Resource-based authorization handlers         |

---

## Design best practices

* Start with **RBAC + ownership checks**
* Treat **roles as coarse**, permissions as fine-grained
* Prefer **resource-based authorization** for ownership
* Centralize policy and permission names
* Deny by default, allow explicitly
* Avoid leaking authorization info accidentally
* Unit test authorization handlers

---

## Samples

### 1. Admin or owner can delete a task

(RBAC + ReBAC via policy)

**Requirement & handler**

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

**Registration**

```csharp
builder.Services.AddAuthorization(options =>
{
    options.AddPolicy("Tasks.Delete", policy =>
        policy.Requirements.Add(new CanDeleteTaskRequirement()));
});

builder.Services.AddSingleton<IAuthorizationHandler, CanDeleteTaskHandler>();
```

**Usage**

```csharp
var result = await m_auth.AuthorizeAsync(User, task, "Tasks.Delete");
if (!result.Succeeded)
{
    return Forbid();
}
```

---

### 2. Admin-only delete (pure RBAC / PBAC)

```csharp
options.AddPolicy("Tasks.Delete",
    policy => policy.RequireRole("Admin"));
```

```csharp
[Authorize(Policy = "Tasks.Delete")]
[HttpDelete("{id:long}")]
public async Task<IActionResult> Delete(long id)
{
    await m_tasks.DeleteAsync(id);
    return NoContent();
}
```

---

### 3. User can see only their own profile

(ReBAC via identity)

```csharp
[Authorize]
[HttpGet("profile")]
public async Task<IActionResult> Profile()
{
    var userName = User.Identity?.Name
        ?? throw new InvalidOperationException();

    var user = await m_userManager.FindByNameAsync(userName)
        ?? throw new InvalidOperationException();

    return Ok(new
    {
        user.UserName,
        user.Email
    });
}
```

---

### 4. Debug endpoint to inspect authorization state

```csharp
[Authorize]
[HttpGet("profile/debug")]
public IActionResult DebugAuth()
{
    var auth = HttpContext.User;

    return Ok(new
    {
        IsAuthenticated = auth.Identity?.IsAuthenticated,
        Name = auth.Identity?.Name,
        Claims = auth.Claims.Select(c => $"{c.Type}={c.Value}")
    });
}
```

