// DownloadSettings.cs
using System.ComponentModel.DataAnnotations;

public sealed class DownloadSettings
{
   [Required, MinLength(1)]
   public string Root { get; set; } = default!;

   [Range(1_000, long.MaxValue)]
   public long MaxBytes { get; set; } = 50_000_000;

   [MinLength(1)]
   public string[] AllowedExtensions { get; set; } = Array.Empty<string>();
}

