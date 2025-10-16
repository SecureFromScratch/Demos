using System.ComponentModel.DataAnnotations;
using System.Text.Json;
using System.Text.Json.Serialization;
using MassAssignLab.Models;

namespace MassAssignLab.Contracts.Users;

public sealed class UserUpdateRequest : IValidatableObject
{
   // Allowed, optional fields
   public string? FullName { get; set; }
   public string? Email { get; set; }

   // Captures raw JSON properties so we can know exactly what was sent
   [JsonExtensionData]
   public Dictionary<string, JsonElement>? Raw { get; set; }

   // Helper: which props were present in the payload (even if null)
   public bool Has(string name) =>
      Raw?.ContainsKey(name) == true ||
      (name == nameof(FullName) && FullName is not null) ||
      (name == nameof(Email)    && Email    is not null);

   // Central list of allowed properties (case sensitive to match C# names)
   private static readonly HashSet<string> Allowed = new(StringComparer.Ordinal)
   { nameof(FullName), nameof(Email) };

   public IEnumerable<ValidationResult> Validate(ValidationContext validationContext)
   {
      // 1) Must include at least one allowed field
      if (!Has(nameof(FullName)) && !Has(nameof(Email)))
         yield return new ValidationResult(
            "Request must include at least one of: FullName, Email.",
            new[] { nameof(FullName), nameof(Email) });

      // 2) Forbid extra/privileged fields (mass-assignment guard)
      if (Raw is not null)
      {
         foreach (var key in Raw.Keys)
         {
            if (!Allowed.Contains(key))
            {
               yield return new ValidationResult(
                  $"Field '{key}' is not writable.",
                  new[] { key });
            }
         }
      }

      // 3) Field-level validation
      if (Has(nameof(FullName)) && string.IsNullOrWhiteSpace(FullName))
         yield return new ValidationResult("FullName cannot be empty.", new[] { nameof(FullName) });

      if (Has(nameof(FullName)) && FullName!.Length > 120)
         yield return new ValidationResult("FullName is too long (max 120).", new[] { nameof(FullName) });

      if (Has(nameof(Email)))
      {
         if (string.IsNullOrWhiteSpace(Email))
            yield return new ValidationResult("Email cannot be empty.", new[] { nameof(Email) });
         else if (!IsValidEmail(Email!))
            yield return new ValidationResult("Email format is invalid.", new[] { nameof(Email) });
      }
   }

   public void ApplyTo(User user)
   {
      // Normalize + map only properties that were actually sent
      if (Has(nameof(FullName)))
         user.FullName = FullName!.Trim();

      if (Has(nameof(Email)))
         user.Email = Email!.Trim().ToLowerInvariant();

      user.UpdatedAt = DateTimeOffset.UtcNow; // server-controlled: set here, not from input
   }

   private static bool IsValidEmail(string email)
   {
      try { var _ = new System.Net.Mail.MailAddress(email); return true; }
      catch { return false; }
   }
}
