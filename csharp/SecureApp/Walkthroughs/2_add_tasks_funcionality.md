## 1. API: Entity + enum (EF Core)

**Enum**

* `Api/Models/TaskStatus.cs`

  * `public enum TaskStatus { Todo, InProgress, Done }`

**Entity**

* `Api/Models/Task.cs`

  * Properties:

    * `long Id`
    * `string Name`
    * `string? Description` (max length 2000)
    * `string CreatedBy` (not updatable)
    * `string? AssignTo`
    * `TaskStatus Status` (default `Todo`)
    * `DateTime CreateDate` (not updatable, default `UtcNow`)
  * Configure with EF attributes or `OnModelCreating` so `Id` is identity, `CreateDate` and `Status` get defaults.

Best practice: don’t let client control `CreatedBy` in real app; derive from auth user.

---

## 2. API: DbContext

* `Api/Data/AppDbContext.cs`

  * Add `public DbSet<Task> Tasks { get; set; }`
  * In `OnModelCreating` configure:

    * Table name `"Tasks"`
    * `Description` max length 2000
    * `Status` stored as string
    * `CreateDate` default value (`GETUTCDATE()` or similar)
    * Mark `CreatedBy` / `CreateDate` as not updated.

* `Api/Program.cs`

  * EF Core registration already there for your other entities; just ensure `AppDbContext` is used by tasks too.

---

## 3. API: Task service

* `Api/Services/ITaskService.cs`

  * Methods:

    * `Task<List<Task>> GetAllAsync()`
    * `Task<Task?> GetByIdAsync(long id)`
    * `Task<Task> CreateAsync(Task task)`
    * `Task<Task> UpdateAsync(Task task)`
    * `Task DeleteAsync(Task task)`

* `Api/Services/TaskService.cs`

  * Uses `AppDbContext` directly: `context.Tasks`
  * On create: set `CreateDate` and default `Status` if missing.
  * On update: keep `CreateDate` unchanged.

Register in `Api/Program.cs`:

* `builder.Services.AddScoped<ITaskService, TaskService>();`

Best practice: use DTOs instead of exposing entity directly; especially for `CreatedBy`, `CreateDate`.

---

## 4. API: Tasks controller (JSON)

* `Api/Controllers/TasksController.cs`

  * `[ApiController]`
  * `[Route("api/tasks")]`
  * Inject `ITaskService` and `ILogger<TasksController>`
  * Actions (all returning DTOs or entity, depending on what you choose):

    * `GET /api/tasks` → list all tasks
    * `GET /api/tasks/{id}` → get single task or 404
    * `POST /api/tasks` → create task
    * `PUT /api/tasks/{id}` → update task, including status
    * `DELETE /api/tasks/{id}` → delete task (load from DB first, then delete)

Best practice:

* Protect with `[Authorize(Policy = "TaskPolicy")]` and perform ownership checks before delete/update.
* Use `TaskCreateDto` / `TaskUpdateDto` / `TaskResponseDto` rather than entity.

If you want, I can sketch the DTO names and mapping rules to fit your existing auth/policy setup.

---

## 5. BFF: proxy endpoints for Angular

Instead of Thymeleaf templates, the BFF forwards calls from Angular to the API.

* `Bff/Services/TasksApiClient.cs`

  * Uses `HttpClient` to call API `api/tasks` endpoints.
  * Handles auth headers / cookies as your BFF already does.

* `Bff/Controllers/TasksBffController.cs`

  * `[ApiController]`
  * `[Route("bff/tasks")]`
  * Actions:

    * `GET /bff/tasks` → calls API `GET /api/tasks`
    * `GET /bff/tasks/{id}` → calls API `GET /api/tasks/{id}`
    * `POST /bff/tasks` → calls API `POST /api/tasks`
    * `PUT /bff/tasks/{id}` → calls API `PUT /api/tasks/{id}`
    * `DELETE /bff/tasks/{id}` → calls API `DELETE /api/tasks/{id}`

Best practice: never let Angular talk directly to the API if BFF is your security boundary.

---

## 6. Angular client: list + form instead of Thymeleaf

Create a “tasks” feature module or folder.

**Model**

* `client/src/app/tasks/task.model.ts`

  * Interface matching `TaskResponseDto` from BFF:

    * `id`, `name`, `description`, `createdBy`, `assignTo`, `status`, `createDate`

**Service**

* `client/src/app/tasks/task.service.ts`

  * Uses `HttpClient` to call `/bff/tasks` endpoints.
  * Methods: `getTasks`, `getTask`, `createTask`, `updateTask`, `deleteTask`.

**Components**

* `client/src/app/tasks/tasks-list/tasks-list.component.ts/html/scss`

  * Shows the table equivalent of `list.html`.

* `client/src/app/tasks/task-form/task-form.component.ts/html/scss`

  * Shows the form equivalent of `form.html`.
  * For `status` use a `<select>` bound to enum values from a small TS enum mirroring `TaskStatus`.

**Routing**

* `client/src/app/app-routing.module.ts`

  * Routes:

    * `/tasks` → `TasksListComponent`
    * `/tasks/new` → `TaskFormComponent` (create)
    * `/tasks/:id/edit` → `TaskFormComponent` (edit)

---

## 7. Seeding tasks (API)

Instead of `data.sql`, seed from C#.

* `Api/Data/Seed/AppDbSeed.cs`

  * Static method `SeedAsync(AppDbContext context)` that checks if `Tasks` is empty and, if so, adds a few `Task` entities similar to the SQL example.

* `Api/Program.cs`

  * After building the app, resolve `AppDbContext` in a scope and run `AppDbSeed.SeedAsync(context)` on startup.

Best practice: later move seeding/migrations to something like EF Core migrations and environment-aware seeds.

---

## 8. Using the system (flow with BFF)

1. Angular opens `/tasks`

   * Calls `GET /bff/tasks`
   * BFF calls `GET /api/tasks`
   * Response rendered in the Angular table.

2. “New Task” button

   * Angular navigates to `/tasks/new`, shows form.

3. Save

   * Angular sends `POST /bff/tasks`
   * BFF forwards to `POST /api/tasks`
   * API persists via `TaskService` + `AppDbContext` → `Tasks` table → returns created task.
   * Angular navigates back to `/tasks` and reloads list.

4. Edit / delete flows are the same with `PUT` / `DELETE`.

