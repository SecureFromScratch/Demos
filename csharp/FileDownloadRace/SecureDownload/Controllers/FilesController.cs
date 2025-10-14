// Controllers/FilesController.cs
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.StaticFiles;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using System.IO;
using System.Threading.Tasks;

namespace SecureDownloadDemo.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    [Authorize(Policy = "CanDownloadFiles")]
    public class FilesController : ControllerBase
    {
        private readonly string _root;
        private readonly ILogger<FilesController> _logger;
        private readonly FileExtensionContentTypeProvider _contentTypes = new();

        public FilesController(IConfiguration cfg, ILogger<FilesController> logger)
        {
            _logger = logger;

            var configured = cfg.GetValue<string>("Downloads:Root");
            if (string.IsNullOrWhiteSpace(configured) || !Path.IsPathRooted(configured))
                throw new InvalidDataException("Downloads:Root must be an absolute path outside the web root.");

            if (!Directory.Exists(configured))
                throw new DirectoryNotFoundException($"Downloads root does not exist: {configured}");

            // Canonical absolute root with trailing separator for prefix checks
            _root = Path.GetFullPath(configured).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        }

        [HttpGet("download/{fileName}")]
        [Authorize(Policy = "CanDownloadFiles")]
        public async Task<IActionResult> DownloadFile(string fileName)
        {
            try
            {
                // 1) Normalize to a plain file name
                var nameOnly = Path.GetFileName(fileName);
                if (string.IsNullOrWhiteSpace(nameOnly))
                    return BadRequest("Invalid file name.");

                // Optional allowlist for extensions
                // if (!new[] { ".txt", ".pdf", ".jpg", ".png" }.Contains(Path.GetExtension(nameOnly).ToLowerInvariant()))
                //    return BadRequest("File type not allowed.");

                // 2) Build target and canonicalize
                var combined = Path.Combine(_root, nameOnly);
                var fullPath = Path.GetFullPath(combined);

                // 3) Enforce that final path stays under the configured root
                if (!fullPath.StartsWith(_root))
                    return BadRequest("Invalid path.");

                // 4) Reject any symlinks in the path chain
                if (ContainsSymlink(fullPath, _root))
                    return BadRequest("File path not allowed.");

                if (!System.IO.File.Exists(fullPath))
                {
                    _logger.LogWarning("Download attempt for non existent file: {FileName}", nameOnly);
                    return NotFound();
                }

                // 5) Open the file before responding to avoid TOCTOU replacement
                var fso = new FileStreamOptions
                {
                    Mode = FileMode.Open,
                    Access = FileAccess.Read,
                    Share = FileShare.Read,
                    Options = FileOptions.Asynchronous | FileOptions.SequentialScan
                };
                var stream = new FileStream(fullPath, fso);

                if (!_contentTypes.TryGetContentType(nameOnly, out var contentType))
                    contentType = "application/octet-stream";

                _logger.LogInformation("Serving file: {FileName} -> {Path}", nameOnly, fullPath);
                return File(stream, contentType, fileDownloadName: nameOnly, enableRangeProcessing: true);
            }
            catch (System.Exception ex)
            {
                _logger.LogError(ex, "Error while processing file download.");
                return StatusCode(500);
            }
        }

        private static bool ContainsSymlink(string fullPath, string rootWithSep)
        {
            // Walk from root to leaf and reject if any segment is a symlink
            var current = rootWithSep.TrimEnd(Path.DirectorySeparatorChar);
            var segments = fullPath.Substring(rootWithSep.Length).Split(Path.DirectorySeparatorChar);
            foreach (var seg in segments)
            {
                current = Path.Combine(current, seg);
                var fsi = new FileInfo(current);
#if NET8_0_OR_GREATER
                // LinkTarget is non null when this segment is a link
                if (!string.IsNullOrEmpty(fsi.LinkTarget))
                    return true;
#else
            // Fallback heuristic for older targets
            var attrs = fsi.Attributes;
            if ((attrs & FileAttributes.ReparsePoint) == FileAttributes.ReparsePoint)
               return true;
#endif
            }
            return false;
        }
    }
}
