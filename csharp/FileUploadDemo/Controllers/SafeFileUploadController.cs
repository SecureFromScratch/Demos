using System;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;

[ApiController]
[Route("api/[controller]")]
public class SafeFileUploadController : ControllerBase
{
    // Define a secure allow-list of extensions
    private static readonly string[] AllowedExtensions = { ".jpg", ".jpeg", ".png", ".gif" };
    private const long MaxFileSize = 5 * 1024 * 1024; // 5 MB

    // Magic Bytes/Signatures for common images to verify content
    // Key: file extension, Value: byte signature (prefix)
    private static readonly Dictionary<string, byte[]> FileSignatures = new Dictionary<string, byte[]>(StringComparer.OrdinalIgnoreCase)
    {
        { ".jpeg", new byte[] { 0xFF, 0xD8, 0xFF } }, // JPEG/JPG
        { ".jpg", new byte[] { 0xFF, 0xD8, 0xFF } },  // JPEG/JPG
        { ".png", new byte[] { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A } }, // PNG
        { ".gif", new byte[] { 0x47, 0x49, 0x46, 0x38 } } // GIF
    };

    [HttpPost("upload")]
    public async Task<IActionResult> UploadFile(IFormFile file)
    {
        if (file == null || file.Length == 0)
            return BadRequest("No file selected.");

        // 1. Validate File Size
        if (file.Length > MaxFileSize)
            return BadRequest($"File size exceeds the limit of {MaxFileSize / (1024 * 1024)} MB.");
        
        // 2. Validate Extension (basic initial filter)
        var originalFileName = file.FileName;
        var fileExtension = Path.GetExtension(originalFileName).ToLowerInvariant();

        if (!AllowedExtensions.Contains(fileExtension))
        {
            return BadRequest("Invalid file extension. Only common images are allowed.");
        }
        
        // 3. Magic Byte Validation (Deep Content Check)
        // NOTE: This call automatically opens and reads from the stream.
        if (!await IsValidFileSignature(file, fileExtension))
        {
            return BadRequest("Invalid file content (failed signature check).");
        }
        
        
        // --- Core Security Fixes: Path Traversal Prevention ---

        // 4. Generate a Safe, Unique Filename
        
        var uniqueFileName = Guid.NewGuid().ToString() + fileExtension;
        var uploadsFolder = Path.Combine(Directory.GetCurrentDirectory(), "UploadedFiles");
        if (!Directory.Exists(uploadsFolder))
            Directory.CreateDirectory(uploadsFolder);

        var safeFilePath = Path.Combine(uploadsFolder, uniqueFileName);

        // 5. Save the file
        // Open the stream again (or re-use it if possible, but opening a new one is safer here)
        using (var fileStream = file.OpenReadStream())
        {
            // FIX: The Seek operation must be performed on the stream object (fileStream), not IFormFile.
            // We reset the stream position to the beginning (0) because the IsValidFileSignature 
            // method already advanced the position when reading the magic bytes.
            fileStream.Seek(0, SeekOrigin.Begin);

            using (var stream = new FileStream(safeFilePath, FileMode.Create))
            {
                await fileStream.CopyToAsync(stream);
            }
        }

        return Ok(new { FileName = uniqueFileName, Path = safeFilePath });
    }

    /// <summary>
    /// Checks the file's content against known file signatures (magic bytes).
    /// </summary>
    private async Task<bool> IsValidFileSignature(IFormFile file, string extension)
    {
        if (!FileSignatures.TryGetValue(extension, out var signature))
        {
            // If the extension is allowed but we don't have a signature for it, we might accept it 
            // OR you can reject it to be stricter. Rejecting is safer here.
            return false;
        }

        using (var reader = new BinaryReader(file.OpenReadStream()))
        {
            // Read enough bytes to cover the longest signature
            var headerBytes = reader.ReadBytes(signature.Length);

            if (headerBytes.Length < signature.Length)
            {
                // File is too small to contain the full signature
                return false;
            }

            // Compare the read bytes with the expected signature
            return signature.SequenceEqual(headerBytes);
        }
    }
}