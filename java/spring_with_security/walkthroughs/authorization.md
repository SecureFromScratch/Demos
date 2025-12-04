# Authorization

## Authorization models

## 1. RBAC – Role Based Access Control

**Idea:** Users get roles, roles have permissions, so users gain permissions through their roles.

**Core pieces:**

* Users
* Roles, for example `USER`, `ADMIN`
* Permissions, for example `TASK_READ`, `TASK_DELETE`
* Role to permission mapping

**Pros:**

* Easy to explain and manage for small to medium systems
* Good for “job based” access, for example admin, manager, viewer

**Cons:**

* Can lead to “role explosion” if you try to encode every business nuance into roles
* Not expressive enough alone for ownership rules such as “user can only see their own tasks”

**In Spring:**

* `hasRole('ADMIN')` or `hasAnyRole('USER','ADMIN')`
* Authorities are actually `ROLE_ADMIN`, `ROLE_USER` under the hood

---

## 2. Permission based access (sometimes called PBAC)

**Idea:** Users or roles are linked directly to fine grained permissions rather than only coarse roles.

Example structure:

* Permissions: `TASK_READ_OWN`, `TASK_READ_ALL`, `TASK_DELETE`, `REPORT_EXPORT`
* Roles are just bundles of permissions, or you assign permissions directly to users

**Pros:**

* Much more expressive than pure RBAC
* Easier to avoid role explosion if you treat roles as “friendly bundles”

**Cons:**

* More complex to design
* You must maintain a clear permission catalog

**In Spring:**

* Use `hasAuthority('TASK_DELETE')`, `hasAuthority('ROLE_ADMIN')`
* Often you combine RBAC and permission based, roles map to authorities and some authorities are finer grained permissions

---

## 3. ABAC – Attribute Based Access Control

**Idea:** Decisions are based on attributes of user, resource, and environment.

Examples of attributes:

* User: department, country, clearance level
* Resource: classification, owner department
* Environment: time of day, client IP, risk score

Policies look like:

* “Allow access if `user.department == resource.department` and `resource.classification <= user.clearance`”

**Pros:**

* Very expressive
* Useful in large organizations and multi tenant or regulated systems

**Cons:**

* More difficult to reason about and test
* Needs a policy engine and good tooling, otherwise it becomes unmanageable

**In Spring:**

* Usually expressed through SpEL in `@PreAuthorize` or in a custom authorization service, for example
  `@PreAuthorize("@authz.canView(#doc, authentication)")`

---

## 4. ReBAC – Relationship Based Access Control

**Idea:** Decisions are based on graph relationships like “owner of”, “member of group”, “manager of”.

Examples:

* “User can edit a document if user is the owner or is a member of a group that has edit rights on that document”
* Social networks, GitHub style orgs and repos

**Pros:**

* Models real world collaboration nicely
* Fits graph data and microservices well

**Cons:**

* Implementation is more complex, needs good modeling of relationships
* Harder to implement only with static roles

**In Spring:**

* Implemented with resource checks, often with a repository query:
  `@PreAuthorize("@docAuthz.canEdit(#docId, authentication)")`

---


## Design best practices

* Start with RBAC plus ownership checks, do not over engineer on day one
* Avoid role explosion, prefer small role set and add permissions or attributes when needed
* Centralize role and permission names in constants
* Deny by default, explicitly allow required operations
* Write tests for “allowed” and “forbidden” cases, especially for ownership


## **Spring, When to Use Each:**

| Method | Use When |
|--------|----------|
| `authentication.getName()` | You only need the username (most common) |
| `authentication.getPrincipal()` | You need full user details (password, enabled status, etc.) |
| `authentication.getAuthorities()` | You need to check roles/permissions |
| `authentication.isAuthenticated()` | You need to verify if user is logged in |

The `getPrincipal()` method gives you the **most complete information** about the authenticated user!
```
```

## Samples

### 1. Allowing delete funcionality only to admins or the ones who created the task
(RBAC - Role Based Access Control, and ReBAC – Relationship Based Access Control(owner))

In the websecurityconfig class add the following attribute:

``` Java
@EnableMethodSecurity(prePostEnabled = true)
```

Put the security rule on the service, with a Task parameter

``` Java
  @PreAuthorize("hasRole('ADMIN') or #task.createdBy == authentication.name")


```

Let's disable the delete buttton when the user isn't allowed to click it

In the list.html add the following attribute to the button. in that way, only users that are allowed to delete a task see the button 

``` html
sec:authorize="hasRole('ADMIN') or #authentication.name == #vars.task.createdBy">

```

If want to improve the UI you can show a disabled version of the button to the others.

``` html
<button type="button"
           disabled
           style="display:inline"
           sec:authorize="!(hasRole('ADMIN') or #authentication.name == #vars.task.createdBy)">
            Delete
</button>
```

### 2. Add a profile endpoint where every user see her/his own personal details
(ReBAC – Relationship Based Access Control)

Add a profile controller
``` java
package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/profile")
public class ProfileController {
    
    @Autowired
    private UserRepository userRepository;
    
    @GetMapping
    public String showProfile(Model model, Authentication authentication) {
        // Get current logged-in user's username
        String username = authentication.getName();
        
        // Fetch the user from database
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        model.addAttribute("user", user);
        return "profile";
    }
}


```

Add a profile template

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>My Profile</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            display: flex;
            justify-content: center;
            align-items: center;
            padding: 20px;
        }
        
        .profile-container {
            background: white;
            border-radius: 20px;
            box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
            max-width: 500px;
            width: 100%;
            overflow: hidden;
        }
        
        .profile-header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            padding: 40px 30px;
            text-align: center;
            color: white;
        }
        
        .profile-avatar {
            width: 100px;
            height: 100px;
            background: white;
            border-radius: 50%;
            margin: 0 auto 20px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 48px;
            font-weight: bold;
            color: #667eea;
            box-shadow: 0 4px 15px rgba(0, 0, 0, 0.2);
        }
        
        .profile-header h1 {
            font-size: 28px;
            margin-bottom: 5px;
        }
        
        .profile-role {
            background: rgba(255, 255, 255, 0.2);
            display: inline-block;
            padding: 5px 15px;
            border-radius: 20px;
            font-size: 14px;
            margin-top: 10px;
        }
        
        .profile-body {
            padding: 30px;
        }
        
        .info-group {
            margin-bottom: 25px;
            padding-bottom: 20px;
            border-bottom: 1px solid #f0f0f0;
        }
        
        .info-group:last-child {
            border-bottom: none;
            margin-bottom: 0;
        }
        
        .info-label {
            font-size: 12px;
            text-transform: uppercase;
            color: #888;
            font-weight: 600;
            margin-bottom: 8px;
            letter-spacing: 0.5px;
        }
        
        .info-value {
            font-size: 16px;
            color: #333;
            font-weight: 500;
        }
        
        .status-badge {
            display: inline-block;
            padding: 6px 12px;
            border-radius: 20px;
            font-size: 14px;
            font-weight: 600;
        }
        
        .status-active {
            background: #d4edda;
            color: #155724;
        }
        
        .status-inactive {
            background: #f8d7da;
            color: #721c24;
        }
        
        .profile-actions {
            display: flex;
            gap: 10px;
            margin-top: 30px;
        }
        
        .btn {
            flex: 1;
            padding: 12px 20px;
            border: none;
            border-radius: 8px;
            font-size: 16px;
            font-weight: 600;
            cursor: pointer;
            text-decoration: none;
            text-align: center;
            transition: all 0.3s ease;
        }
        
        .btn-primary {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
        }
        
        .btn-primary:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
        }
        
        .btn-secondary {
            background: #f0f0f0;
            color: #333;
        }
        
        .btn-secondary:hover {
            background: #e0e0e0;
        }
        
        .btn-logout {
            background: white;
            border: none;
            width: 100%;
        }
    </style>
</head>
<body>
    <div class="profile-container">
        <div class="profile-header">
            <div class="profile-avatar" th:text="${user.username.substring(0, 1).toUpperCase()}">U</div>
            <h1 th:text="${user.username}">Username</h1>
            <span class="profile-role" th:text="${user.roles}">USER</span>
        </div>
        
        <div class="profile-body">
            <div class="info-group">
                <div class="info-label">User ID</div>
                <div class="info-value" th:text="${user.id}">#12345</div>
            </div>
            
            <div class="info-group">
                <div class="info-label">Username</div>
                <div class="info-value" th:text="${user.username}">username</div>
            </div>
            
            <div class="info-group">
                <div class="info-label">Roles</div>
                <div class="info-value" th:text="${user.roles}">USER</div>
            </div>
            
            <div class="info-group">
                <div class="info-label">Account Status</div>
                <div class="info-value">
                    <span th:if="${user.enabled}" class="status-badge status-active">Active</span>
                    <span th:unless="${user.enabled}" class="status-badge status-inactive">Inactive</span>
                </div>
            </div>
            
            <div class="profile-actions">
                <form th:action="@{/logout}" method="post" class="btn-logout">
                    <button type="submit" class="btn btn-primary">Logout</button>
                </form>
            </div>
        </div>
    </div>
</body>
</html>

```

### 3. Add a funcionlity to view task types, all the user can see it, but only people from project_managment department can edit it.
ABAC – Attribute Based Access Control


#### **1. Add Department Field to User Entity**
```java
@Column(nullable = true)
private String department; // "project_management", "development", "hr"

// Add getter and setter
```

#### **2. Update Database**
Run this SQL to add the department column:

``` bash
docker exec -it spring-security-postgres psql -U appuser -d appdb

```

```sql
ALTER TABLE users ADD COLUMN department VARCHAR(100);

-- Assign departments to existing users
UPDATE users SET department = 'project_management' WHERE username = 'PM1';
UPDATE users SET department = 'development' WHERE username LIKE 'Dev%';
```

#### **3. Create the Files:**
- `TaskType.java` - Entity
- `TaskTypeRepository.java` - Repository
- `TaskTypeController.java` - Controller with access control
- `task-types.html` - View page (all users)
- `task-type-form.html` - Create/Edit form (project_management only)

Lets see the important code in the controller:

``` java
 // Helper method to check if user is from project_management
    private boolean isProjectManagement(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return "project_management".equalsIgnoreCase(user.getDepartment());
    }
```

``` java
 // Show create form - Only project_management
    @GetMapping("/create")
    public String showCreateForm(Model model, Authentication authentication, RedirectAttributes redirectAttributes) {
        if (!isProjectManagement(authentication)) {
            redirectAttributes.addFlashAttribute("error", "Access denied. Only Project Management can create task types.");
            return "redirect:/task-types";
        }
        
        model.addAttribute("taskType", new TaskType());
        return "task-type-form";
    }
```
and also in the websecurityconfig:

``` java


```

### 4. Demostration of more implementations of spring authentication object

Create the following enpoint in profile controller

``` java
@GetMapping("/debug")
    public String debugAuth(Authentication authentication, Model model) {
        System.out.println("=== AUTHENTICATION DEBUG ===");

        // 1. Principal (who you are)
        Object principal = authentication.getPrincipal();
        System.out.println("Principal type: " + principal.getClass().getName());
        System.out.println("Principal: " + principal);

        // Convert principal to String for Thymeleaf
        String principalString;
        String principalType = principal.getClass().getSimpleName();

        if (principal instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) principal;
            principalString = "Username: " + userDetails.getUsername() +
                    ", Enabled: " + userDetails.isEnabled() +
                    ", Account Non-Locked: " + userDetails.isAccountNonLocked();
        } else {
            principalString = principal.toString();
        }

        // 2. Credentials (password - usually null after authentication)
        Object credentials = authentication.getCredentials();
        String credentialsString = (credentials != null) ? "[PROTECTED]" : "null";
        System.out.println("Credentials: " + credentialsString);

        // 3. Authorities (roles/permissions) - Convert to String
        String authoritiesString = authentication.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .collect(Collectors.joining(", "));
        System.out.println("Authorities: " + authoritiesString);

        // 4. Details (extra info like IP, session ID)
        Object details = authentication.getDetails();
        String detailsString = (details != null) ? details.toString() : "null";
        System.out.println("Details: " + detailsString);

        // 5. Is authenticated?
        boolean isAuthenticated = authentication.isAuthenticated();
        System.out.println("Is Authenticated: " + isAuthenticated);

        // 6. Name (shortcut for username)
        String name = authentication.getName();
        System.out.println("Name: " + name);

        // Add STRING versions to model (Thymeleaf-friendly)
        model.addAttribute("principalType", principalType);
        model.addAttribute("principal", principalString);
        model.addAttribute("authorities", authoritiesString);
        model.addAttribute("details", detailsString);
        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("name", name);

        return "debug";
    }
```
and a corresponding temple

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Authentication Debug</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: 'Courier New', monospace;
            background: #1e1e1e;
            color: #d4d4d4;
            padding: 20px;
            line-height: 1.6;
        }
        
        .container {
            max-width: 900px;
            margin: 0 auto;
            background: #252526;
            border-radius: 8px;
            padding: 30px;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.5);
        }
        
        h1 {
            color: #4ec9b0;
            margin-bottom: 30px;
            text-align: center;
            font-size: 28px;
        }
        
        .debug-section {
            margin-bottom: 25px;
            padding: 20px;
            background: #1e1e1e;
            border-left: 4px solid #007acc;
            border-radius: 4px;
        }
        
        .debug-label {
            color: #569cd6;
            font-weight: bold;
            font-size: 14px;
            text-transform: uppercase;
            margin-bottom: 10px;
            display: block;
        }
        
        .debug-value {
            color: #ce9178;
            font-size: 16px;
            word-break: break-all;
            padding: 10px;
            background: #2d2d30;
            border-radius: 4px;
        }
        
        .status-badge {
            display: inline-block;
            padding: 5px 12px;
            border-radius: 4px;
            font-weight: bold;
            font-size: 14px;
        }
        
        .status-true {
            background: #1e7e34;
            color: #fff;
        }
        
        .status-false {
            background: #bd2130;
            color: #fff;
        }
        
        .back-link {
            display: inline-block;
            margin-top: 30px;
            padding: 12px 24px;
            background: #007acc;
            color: white;
            text-decoration: none;
            border-radius: 4px;
            transition: background 0.3s;
        }
        
        .back-link:hover {
            background: #005a9e;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>🔍 Authentication Debug Info</h1>
        
        <div class="debug-section">
            <span class="debug-label">Name (Username)</span>
            <div class="debug-value" th:text="${name}">username</div>
        </div>
        
        <div class="debug-section">
            <span class="debug-label">Is Authenticated</span>
            <div class="debug-value">
                <span th:if="${isAuthenticated}" class="status-badge status-true">TRUE ✓</span>
                <span th:unless="${isAuthenticated}" class="status-badge status-false">FALSE ✗</span>
            </div>
        </div>
        
        <div class="debug-section">
            <span class="debug-label">Principal Type</span>
            <div class="debug-value" th:text="${principalType}">UserDetails</div>
        </div>
        
        <div class="debug-section">
            <span class="debug-label">Principal Details</span>
            <div class="debug-value" th:text="${principal}">Principal info</div>
        </div>
        
        <div class="debug-section">
            <span class="debug-label">Authorities (Roles)</span>
            <div class="debug-value" th:text="${authorities}">ROLE_USER, ROLE_ADMIN</div>
        </div>
        
        <div class="debug-section">
            <span class="debug-label">Credentials</span>
            <div class="debug-value" th:text="${credentials}">null</div>
        </div>
        
        <div class="debug-section">
            <span class="debug-label">Details</span>
            <div class="debug-value" th:text="${details}">WebAuthenticationDetails</div>
        </div>
        
        <div style="text-align: center;">
            <a href="/profile" class="back-link">← Back to Profile</a>
        </div>
    </div>
</body>
</html>

```