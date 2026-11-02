package pro.teaz.wiki.mcp;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pro.teaz.wiki.security.CurrentUser;

/** What an AI assistant has read through the MCP server, for the caller to audit. */
@RestController
public class McpAccessLogController {

	private final CurrentUser user;
	private final McpAccessLogService accessLog;

	public McpAccessLogController(CurrentUser user, McpAccessLogService accessLog) {
		this.user = user;
		this.accessLog = accessLog;
	}

	@GetMapping("/api/my/mcp-access-log")
	public List<McpDtos.AccessLogEntry> recent() {
		return accessLog.recent(user.requireId());
	}
}
