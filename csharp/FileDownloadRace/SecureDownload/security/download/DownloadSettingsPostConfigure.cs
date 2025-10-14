// OptionsPostConfigure.cs  (normalize values)
using Microsoft.Extensions.Options;

public sealed class DownloadSettingsPostConfigure : IPostConfigureOptions<DownloadSettings>
{
   public void PostConfigure(string? name, DownloadSettings s)
   {
      // Canonical absolute root with trailing separator
      s.Root = Path.GetFullPath(s.Root).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;

      // Normalize extensions: ensure leading dot, lowercase, distinct
      s.AllowedExtensions = s.AllowedExtensions
         .Select(e => (e ?? string.Empty).Trim())
         .Where(e => e.Length > 0)
         .Select(e => e.StartsWith(".") ? e.ToLowerInvariant() : "." + e.ToLowerInvariant())
         .Distinct(StringComparer.OrdinalIgnoreCase)
         .ToArray();
   }
}
