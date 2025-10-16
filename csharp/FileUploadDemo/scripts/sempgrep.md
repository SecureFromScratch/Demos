# File Upload Detection with Semgrep

## scanning with semgrep default rules

```bash
semgrep --config "p/csharp" Controllers/FileUploadControllerVulnerable.cs
```

```bash
semgrep --config "p/csharp" Controllers/SafeFileUploadController.cs
```

but it's not enough the default rults don't check the file size and also the type

## scanning with semgrep default rules + custom rules 
semgrep --config "p/csharp" Controllers/SafeFileUploadController.cs --config semgrep-rules/csharp-file-upload-missing-type-validation.yml --config semgrep-rules/csharp-file-upload-missing-size-check.yml

