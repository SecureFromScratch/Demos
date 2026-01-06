# Recipe CSV Import


## Overview

The CSV import feature allows you to bulk-import multiple recipes into your system by uploading a single CSV file. This is useful for:
- Initial data migration
- Bulk recipe creation
- Importing recipes from external sources
- Backing up and restoring recipe data

**Endpoint:** `POST /api/recipes/import-csv`

---

## Step 1: Prepare Your CSV File

### 1.1 Understanding the CSV Structure

Your CSV file must have the following structure:

**Header Row (Required):**
```
name,description,status,imageUrl
```

**Column Details:**

| Column | Required | Type | Description | Examples |
|--------|----------|------|-------------|----------|
| `name` | ✅ Yes | Text | Recipe name (cannot be empty) | "Chocolate Chip Cookies", "Spaghetti Carbonara" |
| `description` | ❌ No | Text | Detailed recipe description | "Delicious homemade cookies with chocolate chips" |
| `status` | ❌ No | Enum | Recipe status (defaults to NEW) | NEW, IN_PROGRESS, COMPLETED |
| `imageUrl` | ❌ No | URL | Public URL to recipe image | "https://example.com/image.jpg" |

### 1.2 Create Your CSV File


```csv
name,description,status,imageUrl
Chocolate Chip Cookies,Classic homemade cookies with chocolate chips,NEW,https://images.unsplash.com/photo-1499636136210-6f4ee915583e
Spaghetti Carbonara,Traditional Italian pasta with eggs and pancetta,IN_PROGRESS,https://images.unsplash.com/photo-1612874742237-6526221588e3
Caesar Salad,Fresh romaine lettuce with Caesar dressing,COMPLETED,
Banana Smoothie,Healthy breakfast smoothie with banana and yogurt,NEW,https://images.unsplash.com/photo-1505252585461-04db1eb84625
Grilled Chicken Breast,Simple grilled chicken with herbs,,
```

### 1.3 Important CSV Rules

---

## Step 2: Upload via Swagger UI

### 2.1 Access Swagger UI

1. Ensure your Spring Boot application is running
2. Open your web browser
3. Navigate to: `http://localhost:8080/swagger-ui.html`
   
   *(or `http://localhost:8080/swagger-ui/index.html` depending on your configuration)*

### 2.2 Authenticate 

If your API requires authentication:
1. Login using the login method, and copy the JWT
1. Click the **"Authorize"** button at the top right
2. Enter your JWT
3. Click **"Authorize"** then **"Close"**

### 2.3 Find the Import Endpoint

1. Scroll down to the **"recipe-controller"** section
2. Look for: `POST /api/recipes/import-csv`
3. Click on it to expand

### 2.4 Upload Your File

1. Click the **"Try it out"** button (top right of the endpoint)
2. You'll see the interface change to an interactive form
3. Click **"Choose File"** button under the `file` parameter
4. Select your `recipes.csv` file from your computer
5. The filename should appear next to the button

### 2.5 Execute the Request

1. Click the blue **"Execute"** button
2. Wait for the request to complete (progress indicator may appear)
3. Scroll down to see the response

### 2.6 Review the Response

You should see:
- **Response Code:** `200` (green badge = success)
- **Response Body:** JSON with import results

**Example Success Response:**
```json
{
  "message": "CSV import completed",
  "imported": 5,
  "errors": 0,
  "errorDetails": [],
  "recipes": [
    {
      "id": 1,
      "name": "Chocolate Chip Cookies",
      "description": "Classic homemade cookies with chocolate chips",
      "status": "NEW",
      "imageUrl": "https://your-server.com/uploads/abc123.jpg",
      "createdAt": "2026-01-05T10:30:00"
    },
    // ... more recipes
  ]
}
```

---

## Step 3: Upload via Postman

### 3.1 Create a New Request

1. Open Postman
2. Click **"New"** → **"HTTP Request"**
3. Set method to **POST**
4. Enter URL: `http://localhost:8080/api/recipes/import-csv`

### 3.2 Configure Authentication (if required)

1. Go to the **"Authorization"** tab
2. Select your auth type (Bearer Token, API Key, etc.)
3. Enter your credentials

### 3.3 Set Up the File Upload

1. Go to the **"Body"** tab
2. Select **"form-data"** (radio button)
3. In the key-value table:
   - **Key:** Enter `file`
   - **Type:** Change from "Text" to **"File"** (dropdown on the right)
   - **Value:** Click **"Select Files"** and choose your CSV file

### 3.4 Send the Request

1. Click the **"Send"** button
2. Wait for the response
3. View results in the response panel below

---

## Step 4: Upload via cURL

### 4.1 cURL Command With Authentication

Open your terminal and run:

```bash
curl -X POST http://localhost:8080/api/recipes/import-csv \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@/path/to/your/recipes.csv"
```
**Replace `/path/to/your/recipes.csv`** with the actual path to your file.
**Replace ng `YOUR_TOKEN_HERE` with the actual JWT

### 4.3 Save Response to File

```bash
curl -X POST http://localhost:8080/api/recipes/import-csv \
  -H "Content-Type: multipart/form-data" \
  -F "file=@recipes.csv" \
  -o import_result.json
```

### 4.4 Pretty Print Response

On Linux/Mac with `jq` installed:

```bash
curl -X POST http://localhost:8080/api/recipes/import-csv \
  -H "Content-Type: multipart/form-data" \
  -F "file=@recipes.csv" | jq
```

---

## Understanding the Response

### Success Response Structure

```json
{
  "message": "CSV import completed",
  "imported": 5,           // Number of successfully imported recipes
  "errors": 0,             // Number of failed imports
  "errorDetails": [],      // Array of error messages (empty if no errors)
  "recipes": [             // Array of successfully imported recipes
    {
      "id": 1,
      "name": "Recipe Name",
      "description": "Recipe description",
      "status": "NEW",
      "imageUrl": "https://...",
      "createdAt": "2026-01-05T10:30:00"
    }
  ]
}
```

### Partial Success Response

When some rows fail:

```json
{
  "message": "CSV import completed",
  "imported": 3,
  "errors": 2,
  "errorDetails": [
    "Line 3: Recipe name cannot be empty",
    "Line 5: Failed to upload image from URL: Connection timeout"
  ],
  "recipes": [
    // Only successfully imported recipes
  ]
}
```

### Response Field Descriptions

| Field | Type | Description |
|-------|------|-------------|
| `message` | String | Overall import status message |
| `imported` | Number | Count of successfully imported recipes |
| `errors` | Number | Count of failed recipe imports |
| `errorDetails` | Array | List of error messages with line numbers |
| `recipes` | Array | Full details of successfully imported recipes |

---

## Error Handling

### Common Errors and Solutions

#### 1. "CSV file is empty"

**Error Response:**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "CSV file is empty"
}
```

**Solution:**
- Ensure your file contains data
- Check that the file isn't corrupted
- Verify the file was selected correctly

---

#### 2. "File must be a CSV file"

**Error Response:**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "File must be a CSV file"
}
```

**Solution:**
- Save your file with `.csv` extension
- Ensure MIME type is `text/csv`
- Don't upload Excel (.xlsx) files directly - export as CSV first

---

#### 3. "Invalid CSV format: missing name"

**Error in errorDetails:**
```json
{
  "errorDetails": [
    "Line 3: Invalid CSV format: missing name"
  ]
}
```

**Solution:**
- Check line 3 of your CSV file
- Ensure the first column (name) is not empty
- Verify you have commas separating all fields

---

#### 4. "Recipe name cannot be empty"

**Error in errorDetails:**
```json
{
  "errorDetails": [
    "Line 5: Recipe name cannot be empty"
  ]
}
```

**Solution:**
- Add a name to the recipe on line 5
- Remove empty lines from your CSV file

---

#### 5. "Failed to upload image from URL"

**Error in errorDetails:**
```json
{
  "errorDetails": [
    "Line 4: Failed to upload image from URL: Connection timeout"
  ]
}
```

**Solution:**
- Verify the image URL is publicly accessible
- Check your internet connection
- Ensure the URL points to an actual image file
- Try accessing the URL in your browser first

---

#### 6. File Size Too Large

**Error Response:**
```json
{
  "status": 413,
  "error": "Payload Too Large",
  "message": "Maximum upload size exceeded"
}
```

**Solution:**
- Reduce the number of recipes in your CSV
- Split into multiple smaller CSV files
- Increase upload limit in `application.properties`:
  ```properties
  spring.servlet.multipart.max-file-size=10MB
  spring.servlet.multipart.max-request-size=10MB
  ```

---


## Troubleshooting

### Issue: Swagger shows JSON instead of file upload

**Symptoms:**
- No "Choose File" button in Swagger
- Request body shows `{"file": "string"}`

**Solution:**
- Verify the endpoint has `consumes = MediaType.MULTIPART_FORM_DATA_VALUE`
- Restart your Spring Boot application
- Clear browser cache and refresh Swagger UI

---

### Issue: All recipes fail to import

**Symptoms:**
```json
{
  "imported": 0,
  "errors": 10,
  "errorDetails": ["Line 2: Invalid CSV format: missing name", ...]
}
```

**Solution:**
- Check your CSV header row is exactly: `name,description,status,imageUrl`
- Ensure you have commas separating all fields
- Verify no extra spaces in the header
- Make sure you're not using semicolons (;) instead of commas

---

### Issue: Special characters appear garbled

**Symptoms:**
- Recipe names with accents show as `Ã©` instead of `é`

**Solution:**
- Save your CSV file with UTF-8 encoding
- In Excel: File → Save As → CSV UTF-8
- In Notepad++: Encoding → Convert to UTF-8
- In VS Code: Save with encoding → UTF-8

---

### Issue: Images not uploading

**Symptoms:**
```json
{
  "errorDetails": ["Line 3: Failed to upload image from URL: ..."]
}
```

**Solution:**
1. Test the URL in your browser
2. Ensure URL starts with `http://` or `https://`
3. Check the URL points to an image file (.jpg, .png, etc.)
4. Verify your server can access the URL (firewall/network)
5. Try a different image hosting service

---

### Issue: 401 Unauthorized

**Symptoms:**
```json
{
  "status": 401,
  "error": "Unauthorized"
}
```

**Solution:**
- Add authentication headers
- In Swagger: Click "Authorize" and enter credentials
- In Postman: Add authorization in the "Authorization" tab
- In cURL: Add `-H "Authorization: Bearer YOUR_TOKEN"`

---

## Advanced Examples

### Example 1: Import Recipes Without Images

```csv
name,description,status,imageUrl
Simple Pasta,Quick pasta recipe,NEW,
Easy Salad,Fresh vegetable salad,NEW,
Basic Soup,Vegetable soup,IN_PROGRESS,
```

### Example 2: Import with All Fields

```csv
name,description,status,imageUrl
Gourmet Pizza,Wood-fired pizza with fresh mozzarella and basil,COMPLETED,https://images.unsplash.com/photo-1513104890138-7c749659a591
Thai Green Curry,Spicy coconut curry with vegetables and Thai basil,IN_PROGRESS,https://images.unsplash.com/photo-1455619452474-d2be8b1e70cd
French Croissants,Buttery flaky pastries perfect for breakfast,NEW,https://images.unsplash.com/photo-1555507036-ab1f4038808a
```

### Example 3: Mixed - Some with Images

```csv
name,description,status,imageUrl
Morning Smoothie Bowl,Healthy breakfast bowl with fruits and granola,NEW,https://images.unsplash.com/photo-1590301157890-4810ed352733
Grilled Cheese,Classic comfort food sandwich,NEW,
Chocolate Cake,Rich and moist chocolate layer cake,COMPLETED,https://images.unsplash.com/photo-1578985545062-69928b1d9587
```

---

## Next Steps

After successfully importing your recipes:

1. **Verify the Import**
   - Use `GET /api/recipes` to list all recipes
   - Check that all expected recipes are present

2. **Update Individual Recipes**
   - Use `PUT /api/recipes/{id}` to modify specific recipes
   - Add or change images using `PATCH /api/recipes/{id}/image`

3. **Export for Backup**
   - Consider creating an export feature for future backups
   - Save your CSV files as templates

4. **Monitor Performance**
   - For large imports (100+ recipes), consider async processing
   - Monitor server resources during bulk imports

---

