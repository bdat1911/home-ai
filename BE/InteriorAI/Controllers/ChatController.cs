using System.Net;
using InteriorAI.Models.DTOs;
using InteriorAI.Services;
using Microsoft.AspNetCore.Mvc;

namespace InteriorAI.Controllers;

// The app still identifies users with a client-supplied header. Keep chat data local to
// Development on loopback until server-issued authentication and ownership claims are implemented.
[ApiController]
[Route("api/v1/chat/conversations")]
public sealed class ChatController : ControllerBase
{
    private readonly ChatConversationService _chat;
    private readonly ChatBriefService _briefs;
    private readonly IWebHostEnvironment _environment;

    public ChatController(
        ChatConversationService chat, ChatBriefService briefs, IWebHostEnvironment environment)
    {
        _chat = chat;
        _briefs = briefs;
        _environment = environment;
    }

    [HttpPost]
    public Task<IActionResult> Create(
        [FromBody] CreateChatConversationRequest request, CancellationToken cancellationToken) =>
        Run(userId => _chat.CreateAsync(userId, request.ClientConversationId, cancellationToken));

    [HttpGet]
    public Task<IActionResult> List(CancellationToken cancellationToken) =>
        Run(userId => _chat.ListAsync(userId, cancellationToken));

    [HttpGet("{conversationId}/messages")]
    public Task<IActionResult> Messages(string conversationId, CancellationToken cancellationToken) =>
        Run(userId => _chat.GetMessagesAsync(userId, conversationId, cancellationToken));

    [HttpPost("{conversationId}/messages")]
    public Task<IActionResult> Send(
        string conversationId, [FromBody] SendChatMessageRequest request,
        CancellationToken cancellationToken) =>
        Run(userId => _chat.SendAsync(
            userId, conversationId, request.ClientMessageId, request.Content, cancellationToken));

    [HttpGet("{conversationId}/brief")]
    public Task<IActionResult> GetBrief(string conversationId, CancellationToken cancellationToken) =>
        Run(userId => _briefs.GetAsync(userId, conversationId, cancellationToken));

    [HttpPatch("{conversationId}/brief")]
    public Task<IActionResult> UpdateBrief(
        string conversationId, [FromBody] UpdateDesignBriefRequest request,
        CancellationToken cancellationToken) =>
        Run(userId => _briefs.UpdateAsync(userId, conversationId, request, cancellationToken));

    [HttpPost("{conversationId}/brief/confirm")]
    public Task<IActionResult> ConfirmBrief(
        string conversationId, [FromForm] ConfirmDesignBriefRequest request,
        CancellationToken cancellationToken) =>
        Run(userId => _briefs.ConfirmAsync(userId, conversationId, request, cancellationToken));

    private async Task<IActionResult> Run<T>(Func<string, Task<T>> action)
    {
        if (!IsLocalDemo()) return NotFound();
        var userId = UserId();
        if (userId == null) return BadRequest(new { message = "Thiếu user-id header." });
        try
        {
            return Ok(await action(userId));
        }
        catch (ArgumentException ex) { return BadRequest(new { message = ex.Message }); }
        catch (InvalidDataException ex) { return BadRequest(new { message = ex.Message }); }
        catch (KeyNotFoundException ex) { return NotFound(new { message = ex.Message }); }
        catch (InvalidOperationException ex) { return Conflict(new { message = ex.Message }); }
    }

    private string? UserId()
    {
        var userId = Request.Headers["user-id"].ToString().Trim();
        return string.IsNullOrWhiteSpace(userId) ? null : userId;
    }

    private bool IsLocalDemo()
    {
        var remote = HttpContext.Connection.RemoteIpAddress;
        if (!_environment.IsDevelopment() || remote == null) return false;
        if (remote.IsIPv4MappedToIPv6) remote = remote.MapToIPv4();
        return IPAddress.IsLoopback(remote);
    }
}
