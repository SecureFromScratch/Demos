## Secure file upload in spring 

## 1) configuration
Add the following to application.properties

``` java
# File upload configuration
app.upload.upload-dir=uploads/
app.upload.max-files-per-request=3
app.upload.max-bytes-per-file=10485760
app.upload.allowed-extensions=pdf,jpg,jpeg,png,gif,doc,docx,xls,xlsx,txt,zip

# Spring multipart settings
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=30MB
spring.servlet.multipart.enabled=true

```

## 2) Add FileUploadConfig 

src/main/java/com/example/demo/config/FileUploadConfig.java

## 3) Add FileUpload Service

src/main/java/com/example/demo/service/FileValidationService.java

## 4) Add FileUpload Controller 
src/main/java/com/example/demo/controller/FileUploadController.java

**Pay Attention:**

A few adaptations were made for supporting swagger interface

1. **`@RequestPart` instead of `@RequestParam`** - This properly handles multipart file uploads in Swagger
2. **`consumes = MediaType.MULTIPART_FORM_DATA_VALUE`** - Explicitly tells Swagger this endpoint accepts multipart data
3. **`@Operation` and `@Parameter` annotations** - Properly documents the API for Swagger UI
4. **Added springdoc-openapi dependency** - Provides better OpenAPI 3.0 support

## Security 

### Prevent path traversal with Sandbox         
BoxedPath filePath = PathSandbox.boxroot(uploadPath).resolve(fileName);

### Check Secure controls mention in the previous tutorial