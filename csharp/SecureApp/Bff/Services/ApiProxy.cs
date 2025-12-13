using System.Net.Http.Headers;
using System.Security.Claims;

namespace Bff.Services;

public sealed class ApiProxy
{
   private readonly IHttpClientFactory m_factory;

   public ApiProxy(IHttpClientFactory factory) => m_factory = factory;

   private static string? GetToken(ClaimsPrincipal user)
      => user.FindFirst("access_token")?.Value;

   public async Task<(int status, string contentType, string body)> SendAsync(
      HttpContext http,
      ClaimsPrincipal user,
      HttpMethod method,
      string apiPath)
   {
      var token = GetToken(user);
      if (string.IsNullOrWhiteSpace(token))
         return (401, "application/json", "");

      var client = m_factory.CreateClient("Api");

      var target = apiPath + http.Request.QueryString.Value;

      var req = new HttpRequestMessage(method, target);
      req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);

      if (http.Request.ContentLength.GetValueOrDefault() > 0)
      {
         req.Content = new StreamContent(http.Request.Body);
         if (!string.IsNullOrWhiteSpace(http.Request.ContentType))
            req.Content.Headers.ContentType = MediaTypeHeaderValue.Parse(http.Request.ContentType);
      }

      var resp = await client.SendAsync(req, HttpCompletionOption.ResponseHeadersRead, http.RequestAborted);

      var contentType =
         resp.Content.Headers.ContentType?.ToString()
         ?? "application/json";

      var body = await resp.Content.ReadAsStringAsync(http.RequestAborted);

      return ((int)resp.StatusCode, contentType, body);
   }
}
