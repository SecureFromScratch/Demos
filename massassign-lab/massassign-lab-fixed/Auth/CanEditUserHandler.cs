using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Routing;
using MassAssignLab.Data;

namespace MassAssignLab.Auth;

public sealed class CanEditUserHandler : AuthorizationHandler<CanEditUserRequirement>
{
   private readonly AppDbContext _db;
   private readonly IHttpContextAccessor _http;

   public CanEditUserHandler(AppDbContext db, IHttpContextAccessor http)
   { _db = db; _http = http; }

   protected override async Task HandleRequirementAsync(
      AuthorizationHandlerContext context, CanEditUserRequirement requirement)
   {
      if (context.User.IsInRole("Admin")) { context.Succeed(requirement); return; }

      var http = _http.HttpContext;
      if (http is null) return;

      var idStr = http.GetRouteData().Values.TryGetValue("id", out var v) ? v?.ToString() : null;
      if (!int.TryParse(idStr, out var routeId)) return;

      var callerIdStr = context.User.FindFirstValue(ClaimTypes.NameIdentifier)
                       ?? context.User.FindFirst("sub")?.Value;
      if (!int.TryParse(callerIdStr, out var callerId)) return;

      // Optional existence check
      var exists = await _db.Users.FindAsync(routeId);
      if (exists is null) return;

      if (callerId == routeId) context.Succeed(requirement);
   }
}
