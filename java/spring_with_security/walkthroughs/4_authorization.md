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

Add a profile template ...


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
Add a corresponding template (you can  see the output in the console without it)

