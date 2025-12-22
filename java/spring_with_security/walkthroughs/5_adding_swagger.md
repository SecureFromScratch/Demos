
# Adding Swagger UI to a Spring Boot app: **Springdoc OpenAPI**

## 1) Add the dependency in build.gradle

### Gradle

```gradle
dependencies {
    implementation "org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0"
}
```

## 2) Enable Springdoc's built-in CSRF support 

Add the following to application.properties

```
springdoc.swagger-ui.csrf.enabled=true

```

## 3) Add a dev profile in the websecurityconfig classs

``` java
@Bean
    @Profile("dev")
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        http

        .authorizeHttpRequests((requests) -> requests
                        .requestMatchers("/", "/home", "/register", "/setup").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")                        
                        .anyRequest().authenticated())
                .formLogin((form) -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/tasks", true)
                        .permitAll())
                .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                )
                .logout((logout) -> logout.permitAll());
                

        return http.build();
    }

```
Pay attention to the following line:


``` java 
.csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                )

```

Because Swagger UI runs JavaScript in your browser, and it needs to read the CSRF token value so it can send it back in a header on POST.

With CookieCsrfTokenRepository, Spring puts the CSRF token in a cookie (commonly XSRF-TOKEN).

Swagger’s JS reads that cookie and adds a header (commonly X-XSRF-TOKEN) on POST/PUT/DELETE.

If the cookie is HttpOnly=true, JavaScript cannot read it, so Swagger can’t add the header, and Spring Security returns 403 (CSRF missing/invalid).

So withHttpOnlyFalse() is specifically to make the CSRF cookie readable by JS clients like Swagger UI (and many SPAs). 


## 4) Run the the application with dev profile

```
infisical run --env=dev --projectId 4027f1c4-9559-408f-8538-407a392d1479 -- \
./gradlew bootRun --debug-jvm --args='--spring.profiles.active=dev'

```

## 5) Open swagger UI:

* Swagger UI: `http://localhost:8080/swagger-ui/index.html`


