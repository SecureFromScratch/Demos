### The C\# File Upload Trap 🚨

**Unrestricted file upload** is one of my favorite vulnerabilities to hunt. It's often the first step an attacker takes when trying to compromise an application. What could be more satisfying than uploading a webshell and taking control of the system?

Semgrep helps us detect the vulnerability, but watch out\! The default scan often misses part of the risk.

When securing a file upload function, you need to ensure:

  * The **upload path** is safe and controlled.
  * The **file name** won't overwrite an existing file.
  * The **size** is limited to prevent denial of service (DoS) or storage consumption.
  * The **extension** and **magic bytes** are checked to eliminate risky file types.

**The Original Vulnerable Code:**

```csharp
// 🚨 VULNERABLE: Direct use of user-supplied filename
var filePath = Path.Combine(uploadsFolder, file.FileName); 
await file.CopyToAsync(new FileStream(filePath, FileMode.Create)); 
```

An attacker can submit a file named `shell.aspx` to compromise the server.

-----

### The Secure Solution: Multi-Layered Defense 🛡️

The fix requires several layers of defense:

1.  **Generate a Safe Name:** Use `Guid.NewGuid()` to create a server-controlled, non-guessable filename, which also prevents overwriting existing files.
2.  **Validate the Path:** Strictly control the final location where the content is uploaded.
3.  **Allow-List Extension:** Check the file extension against a **whitelist** (e.g., `.jpg`, `.png`).
4.  **Validate Content:** Check the **magic bytes** (`FF D8 FF` for JPEG, etc.) to ensure the file content genuinely matches the declared type, completely defeating simple extension renaming.

-----

### The Semgrep Challenge (and Why Custom Rules Matter)

When I scanned the codebase, Semgrep's default rules successfully detected the obvious **Path Traversal** risk and the lack of a **unique name**—a win for Taint Analysis\!

```bash
semgrep --config "p/csharp" Controllers/FileUploadControllerVulnerable.cs
```


```bash
semgrep --config "p/csharp" Controllers/SafeFileUploadController.cs
```

However, the default rules ***failed*** to detect the absence of the other critical checks: **file size limits** and **file content validation** (magic bytes).

## scanning with semgrep default rules + custom rules 

```bash
semgrep --config "p/csharp" Controllers/SafeFileUploadController.cs --config semgrep-rules/csharp-file-upload-missing-type-validation.yml --config semgrep-rules/csharp-file-upload-missing-size-check.yml
```

**Key Takeaway:** Don't rely solely on default rule packs. For robust security, **augment your SAST scanner with custom rules** 

\#csharp \#dotnet \#appsec \#infosec \#fileupload \#semgrep \#sast