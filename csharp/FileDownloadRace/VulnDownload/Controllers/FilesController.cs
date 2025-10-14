// Controllers/FilesController.cs
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using System.IO;

namespace SecureDownloadDemo.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    [Authorize(Policy = "CanDownloadFiles")]
    public class FilesController : ControllerBase
    {
        private readonly string _fileStoragePath;
        private readonly ILogger<FilesController> _logger;

        public FilesController(IConfiguration configuration, ILogger<FilesController> logger)
        {
            _logger = logger;
            // VULNERABLE: uses current directory fallback, not an absolute configured path outside the web root
            _fileStoragePath = configuration.GetValue<string>("Downloads:Root") ??
               Path.Combine(Directory.GetCurrentDirectory(), "UploadedFiles");
        }

        // Controllers/FilesController.cs  -- VULNERABLE (demo-only)
        [HttpGet("download/{fileName}")]
        [Authorize(Policy = "CanDownloadFiles")]
        public async Task<IActionResult> DownloadFile(string fileName)
        {
            try
            {
                var sanitizedFileName = Path.GetFileName(fileName);
                var filePath = Path.Combine(_fileStoragePath, sanitizedFileName);

                if (!System.IO.File.Exists(filePath))
                {
                    _logger.LogWarning("Download attempt for non-existent file: {FileName}", sanitizedFileName);
                    return NotFound();
                }

                // DEMO ONLY: widen window so we can reliably race it. Remove this in real apps.
                //await Task.Delay(2000);

                const string contentType = "application/octet-stream";
                _logger.LogInformation("Serving file (vulnerable): {FileName} -> {Path}", sanitizedFileName, filePath);

                return PhysicalFile(filePath, contentType, sanitizedFileName);
            }
            catch (System.Exception ex)
            {
                _logger.LogError(ex, "Error while processing file download (vulnerable).");
                return StatusCode(500);
            }
        }

    }
}
