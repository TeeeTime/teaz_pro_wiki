package pro.teaz.wiki.mcp;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pro.teaz.wiki.security.CurrentUser;
import pro.teaz.wiki.wiki.WikiAccess;

/** What an AI assistant has read through the MCP server, for the caller to audit. */
@RestController
public class McpAccessLogController {

	private final CurrentUser user;
	private final WikiAccess access;
	private final McpAccessLogService accessLog;

	public McpAccessLogController(CurrentUser user, WikiAccess access, McpAccessLogService accessLog) {
		this.user = user;
		this.access = access;
		this.accessLog = accessLog;
	}

	@GetMapping("/api/my/mcp-access-log")
	public List<McpDtos.AccessLogEntry> recent() {
		return accessLog.recent(user.requireId());
	}

	/** For the wiki's settings: who read what. Shows other people's searches, so owner only. */
	@GetMapping("/api/wikis/{wikiId}/mcp-access-log")
	public McpDtos.WikiAccessPage forWiki(@PathVariable String wikiId, @RequestParam(defaultValue = "0") int page) {
		access.own(wikiId);
		return accessLog.forWiki(wikiId, Math.max(page, 0));
	}
}
