using InteriorAI.Models.DTOs;
using InteriorAI.Services;
using Microsoft.AspNetCore.Mvc;

namespace InteriorAI.Controllers;

[ApiController]
[Route("api/[controller]")]
public class DesignController : ControllerBase
{
    private readonly DesignManager _designManager;
    private readonly ILogger<DesignController> _logger;
    private readonly IWebHostEnvironment _environment;

    public DesignController(
        DesignManager designManager,
        ILogger<DesignController> logger,
        IWebHostEnvironment environment)
    {
        _designManager = designManager;
        _logger = logger;
        _environment = environment;
    }

    [HttpPost("analyze")]
    public async Task<IActionResult> AnalyzeImage([FromForm] DesignRequest request)
    {
        var userId = GetUserId();
        if (string.IsNullOrWhiteSpace(userId))
        {
            return BadRequest(new { message = "Missing required user-id header." });
        }

        try
        {
            var design = await _designManager.CreateDesignAsync(
                userId,
                request.Image!,
                request.StyleId,
                request.StyleName,
                request.Style,
                request.RoomType,
                request.FeatureId,
                request.Model,
                request.Resolution);

            return Ok(new DesignResponse
            {
                DesignId = design.Id,
                OriginalImageUrl = design.OriginalImageUrl,
                Status = design.Status
            });
        }
        catch (ArgumentException ex)
        {
            return BadRequest(new { message = ex.Message });
        }
        catch (InvalidDataException ex)
        {
            return BadRequest(new { message = ex.Message });
        }
        catch (KeyNotFoundException ex)
        {
            return NotFound(new { message = ex.Message });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to create design job for user {UserId}.", userId);
            return StatusCode(StatusCodes.Status500InternalServerError, new { message = "Error creating design job." });
        }
    }

    [HttpPost("chat")]
    public async Task<IActionResult> ChatDesign([FromForm] ChatDesignRequest request)
    {
        var userId = GetUserId();
        if (string.IsNullOrWhiteSpace(userId))
        {
            return BadRequest(new { message = "Missing required user-id header." });
        }

        try
        {
            var design = await _designManager.CreateChatDesignAsync(
                userId,
                request.Image,
                request.Prompt,
                request.Model,
                request.Resolution);

            return Ok(new DesignResponse
            {
                DesignId = design.Id,
                OriginalImageUrl = design.OriginalImageUrl,
                Status = design.Status
            });
        }
        catch (ArgumentException ex)
        {
            return BadRequest(new { message = ex.Message });
        }
        catch (InvalidDataException ex)
        {
            return BadRequest(new { message = ex.Message });
        }
        catch (KeyNotFoundException ex)
        {
            return NotFound(new { message = ex.Message });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to create chat design job for user {UserId}.", userId);
            return StatusCode(StatusCodes.Status500InternalServerError, new { message = "Error creating chat design job." });
        }
    }

    [HttpGet("status/{designId}")]
    public async Task<IActionResult> GetStatus(string designId)
    {
        var userId = GetUserId();
        if (string.IsNullOrWhiteSpace(userId))
        {
            return BadRequest(new { message = "Missing required user-id header." });
        }

        try
        {
            var design = await _designManager.GetDesignStatusAsync(designId, userId);
            if (design == null)
            {
                return NotFound(new { message = "Design job was not found." });
            }

            return Ok(new DesignStatusResponse
            {
                DesignId = design.Id,
                Status = design.Status,
                OriginalImageUrl = design.OriginalImageUrl,
                DesignedImageUrl = design.DesignedImageUrl,
                ErrorMessage = design.ErrorMessage
            });
        }
        catch (ArgumentException ex)
        {
            return BadRequest(new { message = ex.Message });
        }
        catch (KeyNotFoundException ex)
        {
            return NotFound(new { message = ex.Message });
        }
        catch (UnauthorizedAccessException ex)
        {
            return StatusCode(StatusCodes.Status403Forbidden, new { message = ex.Message });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to get status for design job {DesignId}.", designId);
            return StatusCode(StatusCodes.Status500InternalServerError, new { message = "Error getting design status." });
        }
    }

    [HttpGet("styles")]
    public async Task<IActionResult> GetStyles()
    {
        return Ok(await _designManager.GetDesignStylesAsync());
    }

    [HttpPost("generate-design")]
    public async Task<IActionResult> GenerateDesign([FromForm] DesignRequest request)
    {
        if (!_environment.IsDevelopment())
        {
            return NotFound();
        }

        if (request.Image == null || request.Image.Length == 0)
        {
            return BadRequest("Please upload an image of the room.");
        }

        if (request.StyleId == null && string.IsNullOrWhiteSpace(request.StyleName) && string.IsNullOrWhiteSpace(request.Style))
        {
            return BadRequest("Please specify a design styleId or styleName.");
        }

        try
        {
            var result = await _designManager.GenerateDesignPreviewAsync(
                request.Image!,
                request.StyleId,
                request.StyleName,
                request.Style,
                request.RoomType,
                request.FeatureId,
                request.Model,
                request.Resolution);

            return Ok(new
            {
                originalImageUrl = result.OriginalImageUrl,
                designedImageUrl = result.DesignedImageUrl,
                designPrompt = result.DesignPrompt
            });
        }
        catch (ArgumentException ex)
        {
            return BadRequest(new { message = ex.Message });
        }
        catch (InvalidDataException ex)
        {
            return BadRequest(new { message = ex.Message });
        }
        catch (KeyNotFoundException ex)
        {
            return NotFound(new { message = ex.Message });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error generating design through development endpoint.");
            return StatusCode(StatusCodes.Status500InternalServerError, new { message = "Error generating design." });
        }
    }

    [HttpGet("projects")]
    public async Task<IActionResult> GetUserProjects(
        [FromQuery] int page = 1,
        [FromQuery] int pageSize = 10)
    {
        var userId = GetUserId();
        if (string.IsNullOrWhiteSpace(userId))
        {
            return BadRequest(new { message = "Missing required user-id header." });
        }

        if (page < 1 || pageSize < 1 || pageSize > 50)
        {
            return BadRequest(new { message = "Invalid page or pageSize. Valid range: page >= 1, 1 <= pageSize <= 50." });
        }

        try
        {
            var (projects, total) = await _designManager.GetUserProjectsAsync(userId, page, pageSize);

            return Ok(new
            {
                data = projects,
                page = page,
                pageSize = pageSize,
                total = total,
                totalPages = (total + pageSize - 1) / pageSize
            });
        }
        catch (UnauthorizedAccessException ex)
        {
            return StatusCode(StatusCodes.Status403Forbidden, new { message = ex.Message });
        }
        catch (KeyNotFoundException ex)
        {
            return NotFound(new { message = ex.Message });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to get projects for user {UserId}.", userId);
            return StatusCode(StatusCodes.Status500InternalServerError, new { message = ex.Message });
        }
    }

    [HttpDelete("{designId}")]
    public async Task<IActionResult> DeleteProject(string designId)
    {
        var userId = GetUserId();
        if (string.IsNullOrWhiteSpace(userId))
        {
            return BadRequest(new { message = "Missing required user-id header." });
        }

        try
        {
            await _designManager.DeleteProjectAsync(designId, userId);
            return Ok(new { message = "Deleted successfully" });
        }
        catch (UnauthorizedAccessException ex)
        {
            return StatusCode(StatusCodes.Status403Forbidden, new { message = ex.Message });
        }
        catch (KeyNotFoundException ex)
        {
            return NotFound(new { message = ex.Message });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to delete project {DesignId}.", designId);
            return StatusCode(StatusCodes.Status500InternalServerError, new { message = ex.Message });
        }
    }

    private string GetUserId()
    {
        return HttpContext.Request.Headers["user-id"].ToString();
    }
}
