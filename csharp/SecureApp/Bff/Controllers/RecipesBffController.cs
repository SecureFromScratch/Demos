using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Bff.Services;
using System.Net.Http.Headers;

namespace Bff.Controllers;

[ApiController]
[Route("bff/recipes")]
[Authorize(AuthenticationSchemes = "bff")]
public sealed class RecipesBffController : ControllerBase
{
    private readonly ApiProxy m_proxy;
    private readonly IHttpClientFactory m_httpClientFactory;




    public RecipesBffController(ApiProxy proxy, IHttpClientFactory httpClientFactory)
    {
        m_proxy = proxy;
        m_httpClientFactory = httpClientFactory;
    }

    [HttpGet]
    public async Task<IActionResult> GetAll()
    {
        var (status, ct, body) = await m_proxy.SendAsync(HttpContext, User, HttpMethod.Get, "api/recipes");

        return new ContentResult
        {
            Content = body,
            ContentType = ct,
            StatusCode = status
        };
    }


    [HttpGet("{id:long}")]
    public async Task<IActionResult> GetById(long id)
    {
        var (status, ct, body) = await m_proxy.SendAsync(HttpContext, User, HttpMethod.Get, $"api/recipes/{id}");
        return new ContentResult
        {
            Content = body,
            ContentType = ct,
            StatusCode = status
        };
    }

    [HttpPost]
    public async Task<IActionResult> Create()
    {
        var (status, ct, body) = await m_proxy.SendAsync(HttpContext, User, HttpMethod.Post, "api/recipes");
        return new ContentResult
        {
            Content = body,
            ContentType = ct,
            StatusCode = status
        };
    }

    [HttpPut("{id:long}")]
    public async Task<IActionResult> Update(long id)
    {
        var (status, ct, body) = await m_proxy.SendAsync(HttpContext, User, HttpMethod.Put, $"api/recipes/{id}");
        return new ContentResult
        {
            Content = body,
            ContentType = ct,
            StatusCode = status
        };
    }

    [HttpDelete("{id:long}")]
    public async Task<IActionResult> Delete(long id)
    {
        var (status, ct, body) = await m_proxy.SendAsync(HttpContext, User, HttpMethod.Delete, $"api/recipes/{id}");
        return new ContentResult
        {
            Content = body,
            ContentType = ct,
            StatusCode = status
        };
    }

    [HttpPost("{id:long}/photo")]
    public async Task<IActionResult> UploadPhoto(long id)
    {
        if (!Request.HasFormContentType)
        {
            return BadRequest("Expected multipart/form-data");
        }

        var form = await Request.ReadFormAsync();
        var file = form.Files.FirstOrDefault();

        if (file == null)
        {
            return BadRequest("No file uploaded");
        }

        using var content = new MultipartFormDataContent();
        using var fileStream = file.OpenReadStream();
        using var streamContent = new StreamContent(fileStream);

        if (!string.IsNullOrEmpty(file.ContentType))
        {
            streamContent.Headers.ContentType = new MediaTypeHeaderValue(file.ContentType);
        }

        // "photoFile" matches the backend parameter name
        content.Add(streamContent, "photoFile", file.FileName);

        var token = User.FindFirst("access_token")?.Value;
        if (string.IsNullOrWhiteSpace(token))
        {
            return Unauthorized();
        }

        var client = m_httpClientFactory.CreateClient("Api");

        var request = new HttpRequestMessage(HttpMethod.Post, $"api/recipes/{id}/photo");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        request.Content = content;

        var response = await client.SendAsync(request, HttpContext.RequestAborted);

        var responseBody = await response.Content.ReadAsStringAsync();
        var contentType = response.Content.Headers.ContentType?.ToString() ?? "application/json";

        return new ContentResult
        {
            Content = responseBody,
            ContentType = contentType,
            StatusCode = (int)response.StatusCode
        };
    }

    [AllowAnonymous] // or keep [Authorize] if you want auth
    [HttpGet("~/uploads/{**path}")] // Catch all uploads requests
    public async Task<IActionResult> GetUpload(string path)
    {
        var client = m_httpClientFactory.CreateClient("Api");

        //var response = await client.GetAsync($"uploads/{path}", HttpContext.RequestAborted);
        var response = await client.GetAsync($"{path}", HttpContext.RequestAborted);

        if (!response.IsSuccessStatusCode)
        {
            return NotFound();
        }

        var bytes = await response.Content.ReadAsByteArrayAsync();
        var contentType = response.Content.Headers.ContentType?.ToString() ?? "application/octet-stream";

        return File(bytes, contentType);
    }

}
