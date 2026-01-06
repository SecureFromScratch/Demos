# Security Fix Migration Guide 

## CSV Import Mass Assignment Vulnerability

If you implemented the CSV import feature using the **previous version** of the tutorial, your application has a **mass assignment vulnerability** that allows users to:
- Set their own recipe status during import
- Bypass approval workflows
- Self-approve recipes by setting status to `APPROVED`
- Potentially escalate privileges through other fields

This guide will help you fix this vulnerability and audit any existing data.

---

## Understanding the Vulnerability

### What Was Wrong?

**Previous Vulnerable Code:**
```java
private RecipeRequest parseCsvLine(String line) {
    String[] parts = line.split(",", -1);
    String name = parts[0].trim();
    String description = parts.length > 1 ? parts[1].trim() : "";
    String statusStr = parts.length > 2 ? parts[2].trim() : "NEW";  // ❌ VULNERABLE
    String imageUrl = parts.length > 3 ? parts[3].trim() : null;
    
    RecipeStatus status = parseStatus(statusStr);  // ❌ User controls this!
    
    return RecipeRequest.builder()
            .name(name)
            .description(description)
            .status(status)  // ❌ Mass assignment vulnerability
            .imageUrl(imageUrl)
            .build();
}
```

**Previous Vulnerable CSV Format:**
```csv
name,description,status,imageUrl
My Recipe,Description,APPROVED,https://example.com/image.jpg
```
☝️ User could set status to `APPROVED` and bypass approval!

### Impact Assessment

**Severity:**  **HIGH**

**Potential Exploits:**
1. Users can approve their own recipes
2. Bulk approval of recipes without review
3. Bypassing approval workflows
4. Potential privilege escalation if other sensitive fields exist

**Affected Endpoints:**
- `POST /api/recipes/import-csv`

---


## Code Changes Needed

**Replace the vulnerable `parseCsvLine` method:**

```java
private RecipeRequest parseCsvLine(String line) {
    // Simple CSV parser - handles basic comma-separated values
    // For production use, consider using a CSV library like Apache Commons CSV or OpenCSV
    String[] parts = line.split(",", -1);
    
    if (parts.length < 1) {
        throw new IllegalArgumentException("Invalid CSV format: missing name");
    }
    
    String name = parts[0].trim();
    if (name.isEmpty()) {
        throw new IllegalArgumentException("Recipe name cannot be empty");
    }
    
    String description = parts.length > 1 ? parts[1].trim() : "";
    // SECURITY FIX: Don't allow users to set status via CSV import
    // Status should always start as NEW and be changed through proper approval workflow
    // NOTE: imageUrl is now at index 2, not 3!
    String imageUrl = parts.length > 2 ? parts[2].trim() : null;
    
    RecipeRequest.RecipeRequestBuilder builder = RecipeRequest.builder()
            .name(name)
            .description(description.isEmpty() ? null : description)
            .status(RecipeStatus.NEW); // ✅ FIXED: Always set to NEW for security
    
    if (imageUrl != null && !imageUrl.isEmpty()) {
        try {
            builder.imageUrl(recipeService.uploadImageFromUrl(imageUrl));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to upload image from URL: " + e.getMessage());
        }
    }
    
    return builder.build();
}
```

**Key Changes:**
- Removed status parsing from CSV
- Always set `status = RecipeStatus.NEW`
- imageUrl moved from index 3 to index 2
- Added security comment

public ResponseEntity<Map<String, Object>> importRecipesFromCsv(
        @RequestPart("file") MultipartFile file) {
    // ...
}
```

---

## Testing the Fix

### Test 1: Verify Status Cannot Be Set

**Create test CSV with status column:**
```csv
name,description,status,imageUrl
Test Recipe,This should be NEW,COMPLETED,
```

**Expected Result:**
- Recipe imports successfully
- Status is `NEW` (not `COMPLETED`)
- Old status column is treated as imageUrl

### Test 2: New Format Works

**Create test CSV without status:**
```csv
name,description,imageUrl
Test Recipe 2,This should work,https://example.com/image.jpg
```

**Expected Result:**
- Recipe imports successfully
- Status is `NEW`
- Image URL is processed correctly

### Test 3: Mass Import with Mixed Data

```csv
name,description,imageUrl
Recipe 1,Description 1,
Recipe 2,Description 2,https://example.com/img.jpg
Recipe 3,Description 3,
```

**Verify:**
```sql
SELECT name, status FROM recipes 
WHERE name IN ('Recipe 1', 'Recipe 2', 'Recipe 3');
```

**Expected: All should have status = 'NEW'**

### Test 4: Status Update Still Works

After import, verify status can be changed properly:

```bash
curl -X PATCH http://localhost:8080/api/recipes/1/status?status=COMPLETED \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

**Expected:** Status changes to COMPLETED (with proper authorization)

---

