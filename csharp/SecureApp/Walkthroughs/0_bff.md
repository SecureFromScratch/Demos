
## BFF vs direct JWT SPA

### 1. Big picture

|                    | **Direct JWT SPA**                     | **SPA + BFF (recommended)**                       |
| ------------------ | -------------------------------------- | ------------------------------------------------- |
| Browser talks to   | Multiple APIs directly                 | Only BFF                                          |
| Auth state         | JWT in browser (localStorage / memory) | HttpOnly cookies handled by BFF                   |
| Public endpoints   | Many APIs exposed                      | Only BFF is public, APIs can be internal          |
| CORS               | Needed on every API                    | Simple. Usually SPA and BFF share origin / proxy  |
| Secrets and tokens | Close to attacker (browser, many hops) | Centralized inside BFF and backend                |
| Security controls  | Duplicated in each API                 | Central place in BFF (rate limit, logging, rules) |
| When to use        | Small demo, internal, PoC              | Real product, multi service, internet facing      |

---

### 2. Data flow diagrams

**Direct JWT SPA**

```text
+----------+          Authorization: Bearer <JWT>           +-----------+
|  SPA     |  ------------------------------------------->   |   API     |
| (Angular)|                                               | (Backend) |
+----------+   <-------------------------------------------+-----------+
                 JSON data

 - SPA stores JWT (often localStorage)
 - Each request adds Authorization header
 - Each API must validate JWT and handle CORS
```

**SPA + BFF**

```text
+----------+         HTTP (cookies)          +----------+         HTTP          +-----------+
|  SPA     |  ---------------------------->  |   BFF    |  -------------------> |   API     |
| (Angular)|                                 | (.NET)   |                        | (Backend) |
+----------+  <----------------------------  +----------+  <-------------------+-----------+
                 HTML/JSON (no tokens)                         JSON data

 - SPA never sees tokens
 - Browser sends HttpOnly cookies to BFF automatically
 - BFF holds tokens, secrets, and talks to APIs
 - APIs can sit behind firewall, only BFF is public
```

---

### 3. Security message for the slide

* Direct JWT SPA keeps tokens near the attacker and exposes more public APIs.
* BFF centralizes security, hides internal APIs, and keeps secrets server side.


