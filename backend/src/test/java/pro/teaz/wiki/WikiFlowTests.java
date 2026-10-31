package pro.teaz.wiki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wikis, structure, permissions, comments, search and previews with the real
 * prod security. Users are MockUserDirectory's, since adding members looks
 * people up in the directory.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"prod", "test"})
class WikiFlowTests {

	static final String OWNER = "mock-jordan";
	static final String MEMBER = "mock-sam";
	static final String STRANGER = "mock-alex";

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	private static JwtRequestPostProcessor user(String id) {
		return jwt().jwt(token -> token.subject(id).claim("preferred_username", id).claim("name", "Name of " + id));
	}

	private ResultActions call(MockHttpServletRequestBuilder request, Object body) throws Exception {
		if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
		return mvc.perform(request);
	}

	private JsonNode ok(MockHttpServletRequestBuilder request, Object body) throws Exception {
		String response = call(request, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return json.readTree(response);
	}

	private String createWiki(String name) throws Exception {
		return ok(post("/api/wikis").with(user(OWNER)), Map.of("name", name, "description", "About " + name))
			.path("id").asString();
	}

	private JsonNode createNode(String wikiId, String parentId, String kind, String title) throws Exception {
		Map<String, Object> body = new java.util.HashMap<>(Map.of("kind", kind, "title", title));
		if (parentId != null) body.put("parentId", parentId);
		return ok(post("/api/wikis/" + wikiId + "/nodes").with(user(OWNER)), body);
	}

	private static String doc(String text) {
		return "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\""
				+ text + "\"}]}]}";
	}

	@Test
	void privateWikisAreOnlyForOwnerAndMembers() throws Exception {
		String wiki = createWiki("Team handbook");
		assertThat(wiki).matches("[a-z0-9]{8}");
		assertThat(ok(get("/api/wikis/" + wiki).with(user(OWNER)), null).path("visibility").asString()).isEqualTo("PRIVATE");

		mvc.perform(get("/api/wikis/" + wiki)).andExpect(status().isNotFound());
		mvc.perform(get("/api/wikis/" + wiki).with(user(STRANGER))).andExpect(status().isNotFound());

		ok(put("/api/wikis/" + wiki + "/members/" + MEMBER).with(user(OWNER)), Map.of("role", "VIEWER"));
		assertThat(ok(get("/api/wikis/" + wiki).with(user(MEMBER)), null).path("level").asString()).isEqualTo("VIEWER");
		assertThat(ok(get("/api/my/wikis").with(user(MEMBER)), null).toString()).contains(wiki, "VIEWER");

		JsonNode category = createNode(wiki, null, "CATEGORY", "Guides");
		call(post("/api/wikis/" + wiki + "/nodes").with(user(MEMBER)),
				Map.of("kind", "PAGE", "title", "Nope", "parentId", category.path("id").asString()))
			.andExpect(status().isForbidden());
		call(patch("/api/wikis/" + wiki).with(user(MEMBER)), Map.of("visibility", "PUBLIC")).andExpect(status().isForbidden());

		ok(put("/api/wikis/" + wiki + "/members/" + MEMBER).with(user(OWNER)), Map.of("role", "EDITOR"));
		JsonNode page = ok(post("/api/wikis/" + wiki + "/nodes").with(user(MEMBER)),
				Map.of("kind", "PAGE", "title", "Onboarding", "parentId", category.path("id").asString()));
		assertThat(page.path("path").toString()).isEqualTo("[\"guides\",\"onboarding\"]");
		// The editor still can't manage members.
		mvc.perform(get("/api/wikis/" + wiki + "/members").with(user(MEMBER))).andExpect(status().isForbidden());

		ok(delete("/api/wikis/" + wiki + "/members/" + MEMBER).with(user(OWNER)), null);
		mvc.perform(get("/api/wikis/" + wiki).with(user(MEMBER))).andExpect(status().isNotFound());
	}

	@Test
	void structureIsFourLevelsWithUniqueNamesPerLevel() throws Exception {
		String wiki = createWiki("Structure");
		String guides = createNode(wiki, null, "CATEGORY", "Guides").path("id").asString();
		String setup = createNode(wiki, guides, "CATEGORY", "Setup").path("id").asString();
		createNode(wiki, setup, "PAGE", "Linux");
		String distros = createNode(wiki, setup, "CATEGORY", "Distros").path("id").asString();
		String debian = createNode(wiki, distros, "PAGE", "Debian").path("id").asString();

		call(post("/api/wikis/" + wiki + "/nodes").with(user(OWNER)), Map.of("kind", "PAGE", "title", "Top page"))
			.andExpect(status().isBadRequest());
		call(post("/api/wikis/" + wiki + "/nodes").with(user(OWNER)),
				Map.of("kind", "CATEGORY", "title", "Too deep", "parentId", distros))
			.andExpect(status().isBadRequest());
		call(post("/api/wikis/" + wiki + "/nodes").with(user(OWNER)),
				Map.of("kind", "PAGE", "title", "Too deep", "parentId", debian))
			.andExpect(status().isBadRequest());
		ok(get("/api/wikis/" + wiki + "/nodes/by-path?path=guides/setup/distros/debian").with(user(OWNER)), null);
		call(post("/api/wikis/" + wiki + "/nodes").with(user(OWNER)), Map.of("kind", "CATEGORY", "title", "guides!"))
			.andExpect(status().isConflict());
		// Same name on another level or under another parent is fine.
		createNode(wiki, guides, "PAGE", "Linux");
		String other = createNode(wiki, null, "CATEGORY", "Über uns").path("id").asString();
		createNode(wiki, other, "PAGE", "Setup");

		JsonNode linux = ok(get("/api/wikis/" + wiki + "/nodes/by-path?path=guides/setup/linux").with(user(OWNER)), null);
		assertThat(linux.path("title").asString()).isEqualTo("Linux");
		assertThat(ok(get("/api/wikis/" + wiki + "/nodes/by-path?path=ueber-uns").with(user(OWNER)), null)
			.path("kind").asString()).isEqualTo("CATEGORY");

		// A category three levels deep can't become a sub-category itself.
		call(post("/api/wikis/" + wiki + "/nodes/" + guides + "/move").with(user(OWNER)), Map.of("parentId", other))
			.andExpect(status().isBadRequest());
		// A sub-category with pages and a sub-category may move to the top level...
		ok(post("/api/wikis/" + wiki + "/nodes/" + setup + "/move").with(user(OWNER)), Map.of("position", 0));
		ok(get("/api/wikis/" + wiki + "/nodes/by-path?path=setup/linux").with(user(OWNER)), null);
		// ...but not next to a sibling of the same name ("Setup" in "Über uns").
		call(post("/api/wikis/" + wiki + "/nodes/" + setup + "/move").with(user(OWNER)), Map.of("parentId", other))
			.andExpect(status().isConflict());

		call(delete("/api/wikis/" + wiki + "/nodes/" + setup).with(user(OWNER)), null).andExpect(status().isConflict());
		call(delete("/api/wikis/" + wiki + "/nodes/" + setup + "?recursive=true").with(user(OWNER)), null)
			.andExpect(status().isNoContent());
		assertThat(ok(get("/api/wikis/" + wiki + "/tree").with(user(OWNER)), null).size()).isEqualTo(4);
	}

	@Test
	void savingNeedsTheCurrentVersionAndSafeLinks() throws Exception {
		String wiki = createWiki("Versions");
		JsonNode category = createNode(wiki, null, "CATEGORY", "Docs");
		String id = category.path("id").asString();
		int version = category.path("version").asInt();

		JsonNode saved = ok(put("/api/wikis/" + wiki + "/nodes/" + id).with(user(OWNER)),
				Map.of("content", doc("Hello world"), "version", version));
		assertThat(saved.path("version").asInt()).isEqualTo(version + 1);
		call(put("/api/wikis/" + wiki + "/nodes/" + id).with(user(OWNER)), Map.of("content", doc("Stale"), "version", version))
			.andExpect(status().isConflict());

		String badLink = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\","
				+ "\"text\":\"x\",\"marks\":[{\"type\":\"link\",\"attrs\":{\"href\":\"javascript:alert(1)\"}}]}]}]}";
		call(put("/api/wikis/" + wiki + "/nodes/" + id).with(user(OWNER)), Map.of("content", badLink, "version", version + 1))
			.andExpect(status().isBadRequest());
		call(put("/api/wikis/" + wiki + "/nodes/" + id).with(user(OWNER)), Map.of("content", "[]", "version", version + 1))
			.andExpect(status().isBadRequest());
	}

	@Test
	void publicWikisAreReadableByEveryoneAndCommentableWhenSignedIn() throws Exception {
		String wiki = createWiki("Open wiki");
		String category = createNode(wiki, null, "CATEGORY", "General").path("id").asString();
		JsonNode page = createNode(wiki, category, "PAGE", "Rules");
		String pageId = page.path("id").asString();
		ok(put("/api/wikis/" + wiki + "/nodes/" + pageId).with(user(OWNER)),
				Map.of("content", doc("Be kind to the quokka population"), "version", page.path("version").asInt()));

		mvc.perform(get("/api/wikis/" + wiki + "/tree")).andExpect(status().isNotFound());
		ok(patch("/api/wikis/" + wiki).with(user(OWNER)), Map.of("visibility", "PUBLIC"));

		// Guests read, but neither comment nor edit.
		JsonNode asGuest = ok(get("/api/wikis/" + wiki), null);
		assertThat(asGuest.path("level").asString()).isEqualTo("READER");
		assertThat(asGuest.path("canComment").asBoolean()).isFalse();
		ok(get("/api/wikis/" + wiki + "/nodes/by-path?path=general/rules"), null);
		call(post("/api/wikis/" + wiki + "/nodes/" + pageId + "/comments"), Map.of("body", "hi"))
			.andExpect(status().isUnauthorized());
		call(put("/api/wikis/" + wiki + "/nodes/" + pageId).with(user(STRANGER)), Map.of("content", doc("x"), "version", 1))
			.andExpect(status().isForbidden());

		// Signed-in strangers comment; replies to replies stay one level deep.
		JsonNode comments = ok(post("/api/wikis/" + wiki + "/nodes/" + pageId + "/comments").with(user(STRANGER)),
				Map.of("body", "Nice rules"));
		String root = comments.get(0).path("id").asString();
		comments = ok(post("/api/wikis/" + wiki + "/nodes/" + pageId + "/comments").with(user(OWNER)),
				Map.of("body", "Thanks", "parentId", root));
		String reply = comments.get(0).path("replies").get(0).path("id").asString();
		comments = ok(post("/api/wikis/" + wiki + "/nodes/" + pageId + "/comments").with(user(STRANGER)),
				Map.of("body", "You're welcome", "parentId", reply));
		assertThat(comments.size()).isEqualTo(1);
		assertThat(comments.get(0).path("replies").size()).isEqualTo(2);
		assertThat(comments.get(0).path("author").path("name").asString()).isEqualTo("Name of " + STRANGER);

		// Strangers can't delete the owner's reply; the owner may delete anything.
		call(delete("/api/wikis/" + wiki + "/comments/" + reply).with(user(STRANGER)), null).andExpect(status().isForbidden());
		comments = ok(delete("/api/wikis/" + wiki + "/comments/" + root).with(user(OWNER)), null);
		assertThat(comments.get(0).path("deleted").asBoolean()).isTrue();
		assertThat(comments.get(0).path("body").asString()).isEmpty();

		// Subscriptions list public wikis only.
		ok(post("/api/wikis/" + wiki + "/subscription").with(user(STRANGER)), null);
		assertThat(ok(get("/api/my/wikis").with(user(STRANGER)), null).toString()).contains(wiki, "SUBSCRIBER");
		ok(patch("/api/wikis/" + wiki).with(user(OWNER)), Map.of("visibility", "PRIVATE"));
		assertThat(ok(get("/api/my/wikis").with(user(STRANGER)), null).toString()).doesNotContain(wiki);
	}

	@Test
	void searchFindsTitlesAndText() throws Exception {
		String wiki = createWiki("Searchable");
		String category = createNode(wiki, null, "CATEGORY", "Animals").path("id").asString();
		JsonNode page = createNode(wiki, category, "PAGE", "Marsupials");
		ok(put("/api/wikis/" + wiki + "/nodes/" + page.path("id").asString()).with(user(OWNER)),
				Map.of("content", doc("The quokka lives on Rottnest Island."), "version", page.path("version").asInt()));
		createNode(wiki, category, "PAGE", "Quokka facts");

		JsonNode hits = ok(get("/api/wikis/" + wiki + "/search?q=QUOKKA").with(user(OWNER)), null);
		assertThat(hits.size()).isEqualTo(2);
		assertThat(hits.get(0).path("title").asString()).isEqualTo("Quokka facts");
		assertThat(hits.get(1).path("snippet").asString()).contains("quokka lives");
		assertThat(ok(get("/api/wikis/" + wiki + "/search?q=quokka island").with(user(OWNER)), null).size()).isEqualTo(1);
		assertThat(ok(get("/api/wikis/" + wiki + "/search?q=100%25").with(user(OWNER)), null).size()).isZero();
	}

	@Test
	void previewsOnlyShowPublicWikis() throws Exception {
		String wiki = createWiki("Secret plans");
		String category = createNode(wiki, null, "CATEGORY", "Plans").path("id").asString();
		createNode(wiki, category, "PAGE", "World domination");

		String hidden = mvc.perform(get("/api/preview/" + wiki + "/plans/world-domination"))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(hidden).contains("og:title", "Teaz Wiki").doesNotContain("Secret", "domination");

		ok(patch("/api/wikis/" + wiki).with(user(OWNER)), Map.of("visibility", "PUBLIC"));
		String shown = mvc.perform(get("/api/preview/" + wiki + "/plans/world-domination"))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(shown).contains("World domination · Secret plans", "/" + wiki + "/plans/world-domination");
	}

	@Test
	void imagesAreCheckedByContentAndReadLikeTheWiki() throws Exception {
		String wiki = createWiki("Pictures");
		byte[] png = java.util.Base64.getDecoder().decode(
				"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
		JsonNode file = json.readTree(mvc.perform(multipart("/api/wikis/" + wiki + "/files")
				.file(new MockMultipartFile("file", "a.png", "image/png", png)).with(user(OWNER)))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertThat(file.path("width").asInt()).isEqualTo(1);

		mvc.perform(multipart("/api/wikis/" + wiki + "/files")
				.file(new MockMultipartFile("file", "a.png", "image/png", "<svg/>".getBytes())).with(user(OWNER)))
			.andExpect(status().isUnsupportedMediaType());

		String url = "/api/files/" + file.path("id").asString();
		mvc.perform(get(url).with(user(OWNER))).andExpect(status().isOk());
		mvc.perform(get(url)).andExpect(status().isNotFound());
		ok(patch("/api/wikis/" + wiki).with(user(OWNER)), Map.of("visibility", "PUBLIC", "logoFileId", file.path("id").asString()));
		mvc.perform(get(url)).andExpect(status().isOk());

		call(delete("/api/wikis/" + wiki).with(user(OWNER)), null).andExpect(status().isNoContent());
		mvc.perform(get(url)).andExpect(status().isNotFound());
	}
}
