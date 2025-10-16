using System;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using HtmlAgilityPack;
using Microsoft.AspNetCore.Mvc;

[ApiController]
[Route("api/[controller]")]
public class FileUploadController : ControllerBase
{
	[HttpPost("upload")]
	public async Task<IActionResult> UploadFile(IFormFile file)
	{
		if (file == null || file.Length == 0)
			return BadRequest("No file selected.");

		// Set the directory where to save the file
		var uploadsFolder = Path.Combine(Directory.GetCurrentDirectory(), "UploadedFiles");

		// Ensure the folder exists
		if (!Directory.Exists(uploadsFolder))
			Directory.CreateDirectory(uploadsFolder);

		// Build the file path
		var filePath = Path.Combine(uploadsFolder, file.FileName);

		// Save the file
		using (var stream = new FileStream(filePath, FileMode.Create))
		{
			await file.CopyToAsync(stream);
		}

		return Ok(new { file.FileName, filePath });
	}
}
