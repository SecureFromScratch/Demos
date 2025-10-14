// SecureDownloads/FileDownloadService.cs
using Microsoft.AspNetCore.StaticFiles;
using Microsoft.Extensions.Options;
using System.Security.Cryptography;

namespace SecureDownloads;

public sealed class FileDownloadService : IFileDownloadService
{
   private readonly DownloadSettings _s;
   private readonly HashSet<string> _exts;
   private readonly FileExtensionContentTypeProvider _types = new();

   public FileDownloadService(IOptions<DownloadSettings> s)
   {
      _s = s.Value;
      _exts = new HashSet<string>(_s.AllowedExtensions, StringComparer.OrdinalIgnoreCase);
   }

   public async Task<FileOpenResult> OpenAsync(string requestedName, CancellationToken ct = default)
   {
      var name = Path.GetFileName(requestedName);
      if (string.IsNullOrWhiteSpace(name))
         return Error(400, "Invalid file name.");

      var ext = Path.GetExtension(name);
      if (!_exts.Contains(ext))
         return Error(400, "File type not allowed.");

      var fullPath = Path.GetFullPath(Path.Combine(_s.Root, name));
      if (!fullPath.StartsWith(_s.Root))
         return Error(400, "Invalid path.");

      if (IsSymlinkOrSpecial(fullPath))
         return Error(400, "Path not allowed.");

      var fi = new FileInfo(fullPath);
      if (!fi.Exists || fi.Length <= 0)
         return Error(404, "Not found.");

      if (fi.Length > _s.MaxBytes)
         return Error(413, "File too large.");

      var fso = new FileStreamOptions {
         Mode = FileMode.Open, Access = FileAccess.Read, Share = FileShare.Read,
         Options = FileOptions.Asynchronous | FileOptions.SequentialScan
      };
      var stream = new FileStream(fullPath, fso);

      // Magic bytes check (lightweight)
      if (!MagicOk(stream, ext)) { await stream.DisposeAsync(); return Error(400, "Type mismatch."); }
      stream.Position = 0;

      if (!_types.TryGetContentType(name, out var contentType))
         contentType = "application/octet-stream";

      return new FileOpenResult(true, stream, name, contentType, null, null);
   }

   private static bool IsSymlinkOrSpecial(string path)
   {
      var fi = new FileInfo(path);
#if NET8_0_OR_GREATER
      if (!string.IsNullOrEmpty(fi.LinkTarget)) return true;
#endif
      var attrs = fi.Attributes;
      if ((attrs & FileAttributes.ReparsePoint) == FileAttributes.ReparsePoint) return true;
      if ((attrs & FileAttributes.Directory) == FileAttributes.Directory) return true;
      return false;
   }

   private static bool MagicOk(Stream s, string ext)
   {
      Span<byte> h = stackalloc byte[8];
      var r = s.Read(h);
      if (r < 4) return false;
      var e = ext.ToLowerInvariant();
      if (e is ".pdf") return h[0] == (byte)'%' && h[1] == (byte)'P' && h[2] == (byte)'D' && h[3] == (byte)'F';
      if (e is ".png") return r >= 8 && h.SequenceEqual(new byte[]{0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A});
      if (e is ".jpg" or ".jpeg") return h[0] == 0xFF && h[1] == 0xD8 && h[2] == 0xFF;
      if (e is ".txt") return true;
      return false;
   }

   private static FileOpenResult Error(int status, string msg) =>
      new(false, null, null, null, status, msg);
}
