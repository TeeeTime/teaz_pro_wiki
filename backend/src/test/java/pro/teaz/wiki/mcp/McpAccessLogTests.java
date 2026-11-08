package pro.teaz.wiki.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;

/** The log in a wiki's settings: owner only, with names, and gone after 30 days. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"prod", "test"})
class McpAccessLogTests {

	static final String OWNER = "mock-jordan";
	static final String MEMBER = "mock-sam";
	static final String STRANGER = "mock-alex";

	@Autowired
	MockMvc mvc;
	@Autowired
	JsonMapper json;
	@Autowired
	McpAccessLogService accessLog;
	@Autowired
	McpAccessLogRepository entries;

	private static JwtRequestPostProcessor user(String id) {
		return jwt().jwt(token -> token.subject(id).claim("preferred_username", id).claim("name", "Name of " + id));
	}

	private String createWiki(String name) throws Exception {
		String response = mvc.perform(post("/api/wikis").with(user(OWNER)).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", name, "description", ""))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return json.readTree(response).path("id").asString();
	}

	@Test
	void onlyTheOwnerSeesIt() throws Exception {
		String wiki = createWiki("Owner only");
		mvc.perform(put("/api/wikis/" + wiki + "/members/" + MEMBER).with(user(OWNER))
				.contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"EDITOR\"}")).andExpect(status().isOk());
		String log = "/api/wikis/" + wiki + "/mcp-access-log";

		mvc.perform(get(log).with(user(OWNER))).andExpect(status().isOk());
		mvc.perform(get(log).with(user(MEMBER))).andExpect(status().isForbidden());
		mvc.perform(get(log).with(user(STRANGER))).andExpect(status().isNotFound());
		mvc.perform(get(log)).andExpect(status().is4xxClientError());
	}

	@Test
	void showsWhoReadWhatNewestFirst() throws Exception {
		String wiki = createWiki("Who read what");
		accessLog.record(OWNER, wiki, "search_wiki", "roadmap");
		Thread.sleep(5);
		accessLog.record(McpAccessLogService.GUEST, wiki, "get_page", "guides/setup");
		accessLog.record(OWNER, createWiki("Another"), "search_wiki", "elsewhere");

		mvc.perform(get("/api/wikis/" + wiki + "/mcp-access-log").with(user(OWNER)))
				.andExpect(jsonPath("$.entries.length()").value(2))
				.andExpect(jsonPath("$.hasMore").value(false))
				.andExpect(jsonPath("$.entries[0].user").doesNotExist())
				.andExpect(jsonPath("$.entries[0].tool").value("get_page"))
				.andExpect(jsonPath("$.entries[0].query").value("guides/setup"))
				.andExpect(jsonPath("$.entries[1].user.name").value("Name of " + OWNER))
				.andExpect(jsonPath("$.entries[1].query").value("roadmap"));
	}

	@Test
	void pagesThroughLongLogs() throws Exception {
		String wiki = createWiki("Long log");
		for (int i = 0; i < 51; i++) accessLog.record(OWNER, wiki, "search_wiki", "term " + i);
		String log = "/api/wikis/" + wiki + "/mcp-access-log";

		mvc.perform(get(log).with(user(OWNER)))
				.andExpect(jsonPath("$.entries.length()").value(50))
				.andExpect(jsonPath("$.hasMore").value(true));
		mvc.perform(get(log + "?page=1").with(user(OWNER)))
				.andExpect(jsonPath("$.entries.length()").value(1))
				.andExpect(jsonPath("$.hasMore").value(false));
	}

	@Test
	void entriesExpireAfter30Days() throws Exception {
		String wiki = createWiki("Expiry");
		entries.save(entryFrom(McpAccessLogService.RETENTION.toDays() + 1, wiki, "old"));
		entries.save(entryFrom(McpAccessLogService.RETENTION.toDays() - 1, wiki, "recent"));

		accessLog.deleteExpired();

		assertThat(accessLog.forWiki(wiki, 0).entries()).extracting(McpDtos.WikiAccessEntry::query).containsExactly("recent");
	}

	/** An AI assistant sends whatever it likes; logging it must never break the tool call. */
	@Test
	void oversizedInputIsShortenedNotRejected() throws Exception {
		String wiki = createWiki("Long input");
		String longQuery = "q".repeat(497) + "😀".repeat(10); // ends in emoji: 2 UTF-16 units each

		accessLog.record(OWNER, wiki, "search_wiki", longQuery);
		accessLog.record(OWNER, "not-a-wiki-id-at-all", "get_page", "guides/setup");

		String stored = accessLog.forWiki(wiki, 0).entries().getFirst().query();
		assertThat(stored.codePointCount(0, stored.length())).isLessThanOrEqualTo(500);
		assertThat(stored).startsWith("q".repeat(497)).endsWith("…");
		assertThat(Character.isHighSurrogate(stored.charAt(stored.length() - 2))).isFalse();
		assertThat(accessLog.recent(OWNER)).extracting(McpDtos.AccessLogEntry::query).contains("guides/setup");
	}

	private static McpAccessLog entryFrom(long daysAgo, String wiki, String query) throws Exception {
		McpAccessLog entry = new McpAccessLog(OWNER, wiki, "search_wiki", query);
		Field createdAt = McpAccessLog.class.getDeclaredField("createdAt");
		createdAt.setAccessible(true);
		createdAt.set(entry, Instant.now().minusSeconds(daysAgo * 86_400));
		return entry;
	}
}
