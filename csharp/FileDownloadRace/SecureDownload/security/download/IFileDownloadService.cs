// SecureDownloads/IFileDownloadService.cs
namespace SecureDownloads;

public sealed record FileOpenResult(bool Success, Stream? Stream, string? FileName, string? ContentType, int? ErrorStatus, string? ErrorMessage);

public interface IFileDownloadService
{
   Task<FileOpenResult> OpenAsync(string requestedName, CancellationToken ct = default);
}
