## Secure API documentation with Swagger (OpenAPI) in Spring Boot

### What this is

Swagger (OpenAPI) gives you interactive, machine-readable API docs. With Springdoc, docs are generated from controllers and annotations and exposed via Swagger UI, where you can try requests. In this setup, Swagger is JWT-aware so protected endpoints can be tested.

---

## What you’ll build

* OpenAPI metadata (title, version)
* JWT bearer auth wired into Swagger UI
* Spring Security config that permits Swagger but protects APIs
* File-upload endpoints rendered correctly in Swagger
* A safe strategy for dev vs prod exposure

---

## 1) Dependency

```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0'
```

Best practice: pin versions and update regularly.

---

## 2) OpenAPI + JWT definition

```java
@Configuration
@OpenAPIDefinition(
    info = @Info(title = "API", version = "v1"),
    security = { @SecurityRequirement(name = "bearerAuth") }
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
}
```

Result: Swagger UI shows an Authorize button that injects `Authorization: Bearer <token>`.

---

## 3) Spring Security configuration

Key points:

* Stateless JWT
* Swagger endpoints are public (in dev)
* APIs require authentication
* JWT filter runs before UsernamePasswordAuthenticationFilter

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Order(1)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthFilter) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/account/is-first-user",
                    "/api/account/setup",
                    "/api/account/register",
                    "/api/account/login",

                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/v3/api-docs/swagger-config"
                ).permitAll()
                .requestMatchers("/api/recipes/**").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

Best practices:

* Keep JWT validation strict (issuer, audience, small clock skew).
* Store secrets outside code (env/secret manager).

---

## 4) File upload endpoints in Swagger

Using `@RequestPart` with `multipart/form-data` makes Swagger render file inputs correctly.

```java
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@io.swagger.v3.oas.annotations.Operation(summary = "Create recipe with image file upload")
public ResponseEntity<RecipeResponse> createRecipeWithFile(
        @RequestPart("name") String name,
        @RequestPart(value = "description", required = false) String description,
        @RequestPart(value = "status", required = false) String status,
        @RequestPart(value = "file", required = false) MultipartFile file) {
    ...
}
```

Same pattern applies to `PUT`.

---

## 5) Using Swagger UI (dev)

1. Open `/swagger-ui.html`
2. Click Authorize
3. Paste JWT (without the `Bearer` prefix)
4. Try protected endpoints

---

# Critical: do NOT expose Swagger in production

Swagger is a developer tool. In prod it can leak:

* full endpoint inventory
* schemas and auth patterns
* easy automation for probing

### Recommended approach: different exposure for dev vs prod

* **Dev/test:** enable Swagger UI + `/v3/api-docs`
* **Prod:** disable them entirely (best default), or expose only behind an internal/admin boundary

```
springdoc.api-docs.enabled=false
springdoc.swagger-ui.enabled=false

```

---


## 7) If you must have docs in prod, expose safely

* Separate Spring Boot app (best isolation)
* Create a second service FacadeApplication
* It forwards to internal API (HTTP) or calls shared service layer directly
* Separate SecurityFilterChain and config
* Separate OpenAPI spec and groups

Rule of thumb: if the public internet can reach `/v3/api-docs`, you are helping attackers.

