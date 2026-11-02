package pro.teaz.wiki.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import pro.teaz.wiki.node.Node;
import pro.teaz.wiki.node.NodeDtos;
import pro.teaz.wiki.node.NodeService;
import pro.teaz.wiki.wiki.Wiki;
import pro.teaz.wiki.wiki.WikiDtos;
import pro.teaz.wiki.wiki.WikiService;

/**
 * The MCP tools never see more than the underlying WikiAccess.read(wikiId)
 * already lets the caller see: a private wiki the caller isn't a member of
 * is absent from list_wikis/search_wiki, and still 404s even if its id is
 * passed to search_wiki/get_page directly (defense in depth, see WikiAccess).
 */
@SpringBootTest
@ActiveProfiles("test")
class WikiMcpToolsTests {

	static final String OWNER = "mcp-owner";

	@Autowired
	WikiService wikiService;
	@Autowired
	NodeService nodeService;
	@Autowired
	WikiMcpTools tools;
	@Autowired
	McpAccessLogService accessLog;

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	private static void signIn(String userId) {
		Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", userId)
				.issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
	}

	private static String doc(String text) {
		return "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\""
				+ text + "\"}]}]}";
	}

	/** A wiki with one page whose text is {@code text}, at path "docs/page". */
	private String wikiWithPage(String name, Wiki.Visibility visibility, String text) {
		signIn(OWNER);
		String wikiId = wikiService.create(new WikiDtos.CreateWiki(name, "About " + name)).id();
		if (visibility == Wiki.Visibility.PUBLIC) {
			wikiService.update(wikiId, new WikiDtos.UpdateWiki(null, null, null, null, Wiki.Visibility.PUBLIC));
		}
		NodeDtos.NodeDetail category = nodeService.create(wikiId, new NodeDtos.CreateNode(null, Node.Kind.CATEGORY, "Docs", null));
		NodeDtos.NodeDetail page = nodeService.create(wikiId,
				new NodeDtos.CreateNode(category.id(), Node.Kind.PAGE, "Page", null));
		nodeService.update(wikiId, page.id(), new NodeDtos.UpdateNode(null, doc(text), page.version()));
		return wikiId;
	}

	@Test
	void listWikisOnlyShowsPublicAndTheCallersOwnWikis() {
		String privateWiki = wikiWithPage("Private", Wiki.Visibility.PRIVATE, "secret stuff");
		String publicWiki = wikiWithPage("Public", Wiki.Visibility.PUBLIC, "public stuff");

		signIn(OWNER);
		assertThat(tools.listWikis().stream().map(McpDtos.WikiRef::id)).contains(privateWiki, publicWiki);

		SecurityContextHolder.clearContext(); // a stranger, not signed in at all
		List<String> seenByStranger = tools.listWikis().stream().map(McpDtos.WikiRef::id).toList();
		assertThat(seenByStranger).contains(publicWiki).doesNotContain(privateWiki);
	}

	@Test
	void searchAcrossAllWikisExcludesOnesTheCallerCantRead() {
		String privateWiki = wikiWithPage("Private search", Wiki.Visibility.PRIVATE, "confidential roadmap details");
		wikiWithPage("Public search", Wiki.Visibility.PUBLIC, "confidential-looking but public text");

		SecurityContextHolder.clearContext();
		List<McpDtos.SearchHit> hits = tools.searchWiki("confidential", null);
		assertThat(hits.stream().map(McpDtos.SearchHit::wikiId)).doesNotContain(privateWiki);
	}

	@Test
	void searchAndGetPageStill404ForAWikiIdTheCallerCantRead() {
		String privateWiki = wikiWithPage("Private direct", Wiki.Visibility.PRIVATE, "hidden text");

		SecurityContextHolder.clearContext();
		assertThatThrownBy(() -> tools.searchWiki("hidden", privateWiki))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
		assertThatThrownBy(() -> tools.getPage(privateWiki, "docs/page"))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	@Test
	void getPageReturnsPlainTextAndEveryCallIsLogged() {
		String wikiId = wikiWithPage("Readable", Wiki.Visibility.PUBLIC, "the quokka is a marsupial");
		signIn(OWNER);

		McpDtos.PageContent page = tools.getPage(wikiId, "docs/page");
		assertThat(page.text()).contains("quokka");
		assertThat(page.path()).containsExactly("docs", "page");

		List<McpDtos.AccessLogEntry> log = accessLog.recent(OWNER);
		assertThat(log).extracting(McpDtos.AccessLogEntry::tool).contains("get_page");
	}
}
