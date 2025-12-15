# Adding tasks funcionality

## 1. Entity + enum

```java
// src/main/java/com/example/demo/model/TaskStatus.java
package com.example.demo.model;

public enum TaskStatus {
   TODO,
   IN_PROGRESS,
   DONE
}
```

```java
// src/main/java/com/example/demo/model/Task.java
package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
public class Task {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   @Column(name = "id")
   private Long id;

   @Column(name = "name")
   private String name;

   @Column(name = "description", length = 2000)
   private String description;

   @Column(name = "created_by", updatable=false)
   private String createdBy;

   @Column(name = "assign_to")
   private String assignTo;

   @Enumerated(EnumType.STRING)
   @Column(name = "status")
   private TaskStatus status;

   
   @Column(name = "create_date", updatable=false)
   private LocalDateTime createDate;

   @PrePersist
   public void onCreate() {
      if (createDate == null) {
         createDate = LocalDateTime.now();
      }
      if (status == null) {
         status = TaskStatus.TODO;
      }
   }

   // getters and setters...
   public void setId(Long id) {
      this.id = id;
   }

   // Add this getter method
   public Long getId() {
      return id;
   }

   public String getName() {
      return name;
   }

   public String getDescription() {
      return description;
   }

   public String getCreatedBy() {
      return createdBy;
   }

   public String getAssignTo() {
      return assignTo;
   }

   public TaskStatus getStatus() {
      return status;
   }

   public LocalDateTime getCreateDate() {
      return createDate;
   }

   // Add these to your Task.java
   public void setName(String name) {
      this.name = name;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public void setCreatedBy(String createdBy) {
      this.createdBy = createdBy;
   }

   public void setAssignTo(String assignTo) {
      this.assignTo = assignTo;
   }

   public void setStatus(TaskStatus status) {
      this.status = status;
   }

   public void setCreateDate(LocalDateTime createDate) {
      this.createDate = createDate;
   }
}
```

---

## 2. Repository

```java
// src/main/java/com/example/demo/repository/TaskRepository.java
package com.example.demo.repository;

import com.example.demo.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
}
```

---

## 3. Service

```java
// src/main/java/com/example/demo/service/TaskService.java
package com.example.demo.service;

import com.example.demo.model.Task;
import com.example.demo.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskService {

   private final TaskRepository repo;

   public TaskService(TaskRepository repo) {
      this.repo = repo;
   }

   public List<Task> findAll() {
      return repo.findAll();
   }

   public Task findById(Long id) {
      return repo.findById(id).orElseThrow();
   }

   public Task save(Task task) {
      return repo.save(task);
   }

   public void delete(Task task) {
      repo.delete(task);
   }
}

```

---

## 4. Controller

```java
// src/main/java/com/example/demo/controller/TaskController.java
// src/main/java/com/example/demo/controller/TaskController.java
package com.example.demo.controller;

import com.example.demo.model.Task;
import com.example.demo.model.TaskStatus;
import com.example.demo.service.TaskService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/tasks")
public class TaskController {

   private final TaskService service;

   public TaskController(TaskService service) {
      this.service = service;
   }

   @GetMapping
   public String list(Model model) {
      model.addAttribute("tasks", service.findAll());
      return "tasks/list";
   }

   @GetMapping("/new")
   public String createForm(Model model) {
      model.addAttribute("task", new Task());
      model.addAttribute("statuses", TaskStatus.values());
      return "tasks/form";
   }

   @PostMapping
   public String create(@ModelAttribute Task task) {
      service.save(task);
      return "redirect:/tasks";
   }

   @GetMapping("/{id}/edit")
   public String editForm(@PathVariable Long id, Model model) {
      model.addAttribute("task", service.findById(id));
      model.addAttribute("statuses", TaskStatus.values());
      return "tasks/form";
   }

   @PostMapping("/{id}")
   public String update(@PathVariable Long id, @ModelAttribute Task task) {
      task.setId(id);      
      service.save(task);
      return "redirect:/tasks";
   }

   @PostMapping("/{id}/delete")
   public String delete(@PathVariable Long id) {
      Task task = service.findById(id); // load from DB
      service.delete(task); // security check happens here
      return "redirect:/tasks";
   }
}

```

---

## 5. Thymeleaf templates

Create directory:
`src/main/resources/templates/tasks/`

### `list.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
   <meta charset="UTF-8">
   <title>Tasks</title>
</head>
<body>
<h1>Tasks</h1>

<a th:href="@{/tasks/new}">New Task</a>

<table border="1">
   <thead>
   <tr>
      <th>ID</th>
      <th>Name</th>
      <th>Description</th>
      <th>Created By</th>
      <th>Assign To</th>
      <th>Status</th>
      <th>Create Date</th>
      <th>Actions</th>
   </tr>
   </thead>
   <tbody>
   <tr th:each="task : ${tasks}">
      <td th:text="${task.id}"></td>
      <td th:text="${task.name}"></td>
      <td th:text="${task.description}"></td>
      <td th:text="${task.createdBy}"></td>
      <td th:text="${task.assignTo}"></td>
      <td th:text="${task.status}"></td>
      <td th:text="${task.createDate}"></td>
      <td>
         <a th:href="@{'/tasks/' + ${task.id} + '/edit'}">Edit</a>
         <form th:action="@{'/tasks/' + ${task.id} + '/delete'}" method="post" style="display:inline">
            <button type="submit">Delete</button>
         </form>
      </td>
   </tr>
   </tbody>
</table>
</body>
</html>
```

### `form.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
   <meta charset="UTF-8">
   <title th:text="${task.id != null} ? 'Edit Task' : 'New Task'">Task Form</title>
</head>
<body>
<h1 th:text="${task.id != null} ? 'Edit Task' : 'New Task'">Task Form</h1>

<form th:object="${task}"
      th:action="${task.id != null} ? @{/tasks/{id}(id=${task.id})} : @{/tasks}"
      method="post">
   
   <div>
      <label for="name">Name:</label>
      <input type="text" id="name" th:field="*{name}" />
   </div>

   <div>
      <label for="description">Description:</label>
      <textarea id="description" th:field="*{description}"></textarea>
   </div>

   <div>
      <label for="createdBy">Created By:</label>
      <input type="text" id="createdBy" th:field="*{createdBy}" />
   </div>

   <div>
      <label for="assignTo">Assign To:</label>
      <input type="text" id="assignTo" th:field="*{assignTo}" />
   </div>

   <div>
      <label for="status">Status:</label>
      <select id="status" th:field="*{status}">
         <option th:each="statusOption : ${T(com.example.demo.model.TaskStatus).values()}"
                 th:value="${statusOption}"
                 th:text="${statusOption}">
         </option>
      </select>
   </div>

   <button type="submit">Save</button>
   <a th:href="@{/tasks}">Cancel</a>
</form>
</body>
</html>
```

---

## 6. Your `application.properties`

Yours is already fine. For convenience during dev you can add:

```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

Best practices to consider later: add Bean Validation (`@NotBlank`, `@Size`, etc.), use Flyway/Liquibase for schema, and restrict who can change `createdBy` in the controller.

Do you want me to add basic validation annotations and show how to display errors in the Thymeleaf form?

## 7. Add some tasks to play with


Create this file:

`src/main/resources/data.sql`:

```sql
INSERT INTO tasks (name, description, created_by, assign_to, status, create_date)
SELECT seed.name, seed.description, seed.created_by, seed.assign_to, seed.status, seed.create_date
FROM (
   SELECT
      'Set up project' AS name,
      'Create Spring Boot + Thymeleaf task app' AS description,
      'Or' AS created_by,
      'Or' AS assign_to,
      'IN_PROGRESS' AS status,
      NOW() AS create_date
   UNION ALL
   SELECT
      'Write documentation',
      'Add README with setup instructions',
      'Or',
      'Dev1',
      'TODO',
      NOW()
   UNION ALL
   SELECT
      'Design database schema',
      'Create initial ERD and normalize tables for tasks module',
      'Dev1',
      'Dev2',
      'TODO',
      NOW()
   UNION ALL
   SELECT
      'Implement Task controller',
      'Add CRUD endpoints and connect to service/repository layer',
      'Dev2',
      'Dev2',
      'IN_PROGRESS',
      NOW()
   UNION ALL
   SELECT
      'UI layout for task list',
      'Style Thymeleaf templates for list and form views',
      'Dev3',
      'Dev3',
      'TODO',
      NOW()
   UNION ALL
   SELECT
      'Add validation and error messages',
      'Use Bean Validation annotations and show errors in the form',
      'Or',
      'Dev1',
      'TODO',
      NOW()
   UNION ALL
   SELECT
      'Smoke test in staging',
      'Run basic flows: create, edit, delete, list tasks',
      'QA1',
      'QA1',
      'TODO',
      NOW()
   UNION ALL
   SELECT
      'Prepare deployment checklist',
      'Define steps for config, DB migrations, and rollback plan',
      'PM1',
      'PM1',
      'TODO',
      NOW()
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM tasks);
```


And in `application.properties` add:

```properties
spring.jpa.defer-datasource-initialization=true
spring.sql.init.mode=always
```

Then on startup, Spring will run `data.sql` after Hibernate creates/updates the schema.



## 8. Using the system

1. You open the UI:

   * Go to `http://localhost:9090/tasks`
   * Click “New Task”

2. You fill the form:

   * name, description, createdBy, assignTo, status
   * `createDate` is auto set in `@PrePersist`

3. When you click **Save**:

   * The form is sent as a POST to `/tasks`
   * Spring binds it into a `Task` object (`@ModelAttribute Task task`)
   * `TaskService.save(task)` calls `TaskRepository.save(task)`
   * JPA runs `INSERT` into your PostgreSQL `tasks` table

4. You’re redirected back to `/tasks` and see the new row.

Best practice: open your DB (psql / DBeaver / IntelliJ) and run `SELECT * FROM tasks;` to verify inserts.

