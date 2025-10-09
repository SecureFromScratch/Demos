using Microsoft.AspNetCore.Authorization;
using Microsoft.Extensions.DependencyInjection;

namespace MassAssignLab.Auth;

public static class AuthorizationSetup
{
   public const string CanEditUserPolicy = "CanEditUser";

   public static IServiceCollection AddAppAuthorization(this IServiceCollection services)
   {
      services.AddHttpContextAccessor();

      services.AddAuthorization(options =>
      {
         options.AddPolicy(CanEditUserPolicy, policy =>
            policy.AddRequirements(new CanEditUserRequirement()));
      });

      // IMPORTANT: Scoped (NOT singleton) because handler needs DbContext
      services.AddScoped<IAuthorizationHandler, CanEditUserHandler>();
      return services;
   }
}
