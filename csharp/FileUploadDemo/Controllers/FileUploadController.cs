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
	public async Task<IActionResult> uploadFile()
	{

	}
}
