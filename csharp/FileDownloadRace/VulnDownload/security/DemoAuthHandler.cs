using System.Security.Claims;
using System.Text.Encodings.Web;
using Microsoft.AspNetCore.Authentication;
using Microsoft.Extensions.Options;



public sealed class DemoAuthHandler : AuthenticationHandler<DemoAuthOptions>
{
   public DemoAuthHandler(
      IOptionsMonitor<DemoAuthOptions> options,
      ILoggerFactory logger,
      UrlEncoder encoder)
      : base(options, logger, encoder)
   {
   }

   protected override Task<AuthenticateResult> HandleAuthenticateAsync()
   {
      // simple “always authenticated” demo user
      var id = new ClaimsIdentity(new[]
      {
         new Claim(ClaimTypes.Name, "demo-user"),
         new Claim(ClaimTypes.Role, "Tester")
      }, Scheme.Name);

      var principal = new ClaimsPrincipal(id);
      var ticket = new AuthenticationTicket(principal, Scheme.Name);
      return Task.FromResult(AuthenticateResult.Success(ticket));
   }
}
