package pro.teaz.wiki.mcp;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import pro.teaz.wiki.node.NodeDtos;
import pro.teaz.wiki.node.NodeService;
import pro.teaz.wiki.security.CurrentUser;
import pro.teaz.wiki.wiki.Wiki;
import pro.teaz.wiki.wiki.WikiAccess;

/**
 * Read-only MCP tools: everything here only ever calls into the existing,
 * unchanged {@code access.read(wikiId)} path (via NodeService), so an AI
 * assistant using these tools can never see more than the signed-in caller
 * could already see in the browser. There is deliberately no write tool.
 */
@Component
public class WikiMcpTools {

	private static final int MAX_WIKIS_SEARCHED = 20;
	private static final int MAX_TOTAL_HITS = 30;

	private final CurrentUser user;
	private final WikiAccess access;
	private final NodeService nodes;
	private final McpAccessLogService accessLog;

	public WikiMcpTools(CurrentUser user, WikiAccess access, NodeService nodes, McpAccessLogService accessLog) {
		this.user = user;
		this.access = access;
		this.nodes = nodes;
		this.accessLog = accessLog;
	}

	@McpTool(name = "list_wikis",
			description = "List the wikis available to search: every public wiki, plus (when signed in) the "
					+ "caller's own and shared private wikis. Use the returned id as wikiId in other tools.")
	public List<McpDtos.WikiRef> listWikis() {
		accessLog.record(callerId(), null, "list_wikis", null);
		return access.readableWikis().stream()
				.map(wiki -> new McpDtos.WikiRef(wiki.getId(), wiki.getName(), wiki.getDescription(), wiki.isPublic()))
				.toList();
	}

	@McpTool(name = "search_wiki",
			description = "Search wiki pages by keyword. Give wikiId to search one wiki (from list_wikis); omit "
					+ "it to search across every wiki the caller may read.")
	public List<McpDtos.SearchHit> searchWiki(
			@McpToolParam(description = "Keyword(s) to search for.", required = true) String query,
			@McpToolParam(description = "Limit the search to this wiki id. Omit to search all readable wikis.",
					required = false) String wikiId) {
		if (wikiId != null && !wikiId.isBlank()) {
			accessLog.record(callerId(), wikiId, "search_wiki", query);
			Wiki wiki = access.read(wikiId).wiki();
			return hits(wiki.getId(), wiki.getName(), query);
		}
		List<McpDtos.SearchHit> all = new ArrayList<>();
		int wikisSearched = 0;
		for (Wiki wiki : access.readableWikis()) {
			if (all.size() >= MAX_TOTAL_HITS || wikisSearched >= MAX_WIKIS_SEARCHED) break;
			wikisSearched++;
			// One entry per wiki, so it shows in the settings of each wiki that was searched.
			accessLog.record(callerId(), wiki.getId(), "search_wiki", query);
			all.addAll(hits(wiki.getId(), wiki.getName(), query));
		}
		return all.stream().limit(MAX_TOTAL_HITS).toList();
	}

	@McpTool(name = "get_page",
			description = "Read the full text of one page, by its wiki id and path (the slugs after the wiki id, "
					+ "e.g. \"guides/setup/linux\"; from search_wiki's results).")
	public McpDtos.PageContent getPage(
			@McpToolParam(description = "The wiki id, from list_wikis or search_wiki.", required = true) String wikiId,
			@McpToolParam(description = "The page's path, the slugs after the wiki id.", required = true) String path) {
		accessLog.record(callerId(), wikiId, "get_page", path);
		NodeDtos.TextDetail text = nodes.textByPath(wikiId, path);
		return new McpDtos.PageContent(wikiId, text.title(), text.path(), text.text());
	}

	private List<McpDtos.SearchHit> hits(String wikiId, String wikiName, String query) {
		return nodes.search(wikiId, query).stream()
				.map(hit -> new McpDtos.SearchHit(wikiId, wikiName, hit.title(), hit.path(), hit.snippet()))
				.toList();
	}

	private String callerId() {
		return user.id().orElse(McpAccessLogService.GUEST);
	}
}
