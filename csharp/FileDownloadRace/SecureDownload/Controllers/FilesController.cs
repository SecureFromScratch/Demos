// FilesController.cs
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SecureDownloads;

[ApiController]
[Route("api/[controller]")]
[Authorize(Policy = "CanDownloadFiles")]
public class FilesController : ControllerBase
{
   private readonly IFileDownloadService _svc;
   private readonly ILogger<FilesController> _logger;

   public FilesController(IFileDownloadService svc, ILogger<FilesController> logger)
   {
      _svc = svc;
      _logger = logger;
   }

   [HttpGet("download/{fileName}")]
   public async Task<IActionResult> DownloadFile(string fileName, CancellationToken ct)
   {
      var r = await _svc.OpenAsync(fileName, ct);
      if (!r.Success)
      {
         _logger.LogWarning("Download rejected: {File} {Status} {Msg}", fileName, r.ErrorStatus, r.ErrorMessage);
         return StatusCode(r.ErrorStatus ?? 400);
      }

      return new SecureFileResult(r.Stream!, r.ContentType!, r.FileName!);
   }
}
