// SecureDownloads/DownloadOptionsConfig.cs
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;
using System.IO;
using System.Linq;

namespace SecureDownloads;

public static class DownloadOptionsConfig
{
   public static IServiceCollection AddSecureDownloads(this IServiceCollection services, IConfiguration config)
   {
      services.AddOptions<DownloadSettings>()
         .Bind(config.GetSection("Downloads"))
         .ValidateDataAnnotations()
         .Validate(s => !string.IsNullOrWhiteSpace(s.Root) && Path.IsPathRooted(s.Root) && Directory.Exists(s.Root), "Invalid Downloads:Root")
         .Validate(s => s.AllowedExtensions?.Length > 0, "Downloads:AllowedExtensions empty")
         .Validate(s => s.MaxBytes >= 1_000, "Downloads:MaxBytes too small")
         .PostConfigure(s =>
         {
            s.Root = Path.GetFullPath(s.Root).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
            s.AllowedExtensions = s.AllowedExtensions!
               .Where(e => !string.IsNullOrWhiteSpace(e))
               .Select(e => e!.Trim().ToLowerInvariant())
               .Select(e => e.StartsWith(".") ? e : "." + e)
               .Distinct()
               .ToArray();
         });

      return services;
   }
}
