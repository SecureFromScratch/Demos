// DownloadSettingsValidation.cs
using Microsoft.Extensions.Options;
using System.IO;

public sealed class DownloadSettingsValidation : IValidateOptions<DownloadSettings>
{
   public ValidateOptionsResult Validate(string? name, DownloadSettings s)
   {
      if (string.IsNullOrWhiteSpace(s.Root))
         return ValidateOptionsResult.Fail("Downloads:Root is required.");
      if (!Path.IsPathRooted(s.Root))
         return ValidateOptionsResult.Fail("Downloads:Root must be an absolute path.");
      if (!Directory.Exists(s.Root))
         return ValidateOptionsResult.Fail($"Downloads:Root does not exist: {s.Root}");

      if (s.AllowedExtensions is null || s.AllowedExtensions.Length == 0)
         return ValidateOptionsResult.Fail("Downloads:AllowedExtensions must have at least one value.");

      if (s.AllowedExtensions.Any(e => string.IsNullOrWhiteSpace(e)))
         return ValidateOptionsResult.Fail("Downloads:AllowedExtensions contains an empty value.");

      if (s.MaxBytes < 1_000)
         return ValidateOptionsResult.Fail("Downloads:MaxBytes must be >= 1000.");

      return ValidateOptionsResult.Success;
   }
}
