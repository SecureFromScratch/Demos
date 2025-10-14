// SecureDownloads/SecureFileResult.cs
using Microsoft.AspNetCore.Mvc;

namespace SecureDownloads;

public sealed class SecureFileResult : FileStreamResult
{
   public SecureFileResult(Stream fileStream, string contentType, string downloadFileName)
      : base(fileStream, contentType)
   {
      FileDownloadName = downloadFileName;
      EnableRangeProcessing = true;
   }

   public override Task ExecuteResultAsync(ActionContext context)
   {
      var headers = context.HttpContext.Response.Headers;
      if (!headers.ContainsKey("X-Content-Type-Options")) headers["X-Content-Type-Options"] = "nosniff";
      if (!headers.ContainsKey("Cache-Control")) headers["Cache-Control"] = "no-store, private";
      if (!headers.ContainsKey("Content-Disposition")) headers["Content-Disposition"] = $"attachment; filename=\"{FileDownloadName}\"";

      return base.ExecuteResultAsync(context);
   }
}
