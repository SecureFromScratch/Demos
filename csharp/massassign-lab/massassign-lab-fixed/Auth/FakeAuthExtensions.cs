using System.Security.Claims;
using System.Text.Encodings.Web;
using Microsoft.AspNetCore.Authentication;
using Microsoft.Extensions.Options;

namespace MassAssignLab.Auth;

public static class FakeAuthExtensions
{
   public const string Scheme = "Fake";

   public static IServiceCollection AddFakeAuth(this IServiceCollection services)
   {
      services.AddAuthentication(Scheme)
              .AddScheme<AuthenticationSchemeOptions, FakeAuthHandler>(
                 Scheme,
                 o => { o.TimeProvider = TimeProvider.System; }  // <-- modern replacement
              );
      return services;
   }

   public static IApplicationBuilder UseFakeAuth(this IApplicationBuilder app) => app.UseAuthentication();

   // NOTE: use the newer base constructor WITHOUT ISystemClock
   private sealed class FakeAuthHandler : AuthenticationHandler<AuthenticationSchemeOptions>
   {
      public FakeAuthHandler(
         IOptionsMonitor<AuthenticationSchemeOptions> options,
         ILoggerFactory logger,
         UrlEncoder encoder)
         : base(options, logger, encoder) { }

      protected override Task<AuthenticateResult> HandleAuthenticateAsync()
      {
         var claims = new[]
         {
            new Claim(ClaimTypes.NameIdentifier, "1"),
            new Claim(ClaimTypes.Name, "alice"),
            new Claim(ClaimTypes.Role, "user")
         };

         var identity  = new ClaimsIdentity(claims, Scheme.Name);   // string auth type
         var principal = new ClaimsPrincipal(identity);
         var ticket    = new AuthenticationTicket(principal, Scheme.Name);

         return Task.FromResult(AuthenticateResult.Success(ticket));
      }
   }
}
