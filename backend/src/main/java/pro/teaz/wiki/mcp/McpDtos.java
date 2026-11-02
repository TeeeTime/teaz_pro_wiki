package pro.teaz.wiki.mcp;

import java.time.Instant;
import java.util.List;

/** JSON shapes returned by the MCP tools (WikiMcpTools) and the access log API. */
public final class McpDtos {

	private McpDtos() {
	}

	public record WikiRef(String id, String name, String description, boolean isPublic) {
	}

	/** @param snippet text around the first match, or the start of the text for title-only matches */
	public record SearchHit(String wikiId, String wikiName, String title, List<String> path, String snippet) {
	}

	public record PageContent(String wikiId, String title, List<String> path, String text) {
	}

	public record AccessLogEntry(String tool, String wikiId, String query, Instant at) {
	}
}
