package pro.teaz.wiki.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"prod", "test"})
class SecurityConfigTests {

	@Autowired
	MockMvc mvc;

	@Test
	void changesNeedAToken() throws Exception {
		mvc.perform(post("/api/wikis").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
			.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/my/wikis")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/users/search?q=a")).andExpect(status().isUnauthorized());
	}

	@Test
	void readingIsLetThroughToWikiAccess() throws Exception {
		// No such wiki: 404 from WikiAccess, not 401 from security.
		mvc.perform(get("/api/wikis/nosuchwk")).andExpect(status().isNotFound());
		mvc.perform(get("/api/wikis/nosuchwk/tree")).andExpect(status().isNotFound());
		mvc.perform(get("/api/preview/nosuchwk/a/b")).andExpect(status().isOk());
	}

	@Test
	void anyRealmUserMayUseWiki() throws Exception {
		mvc.perform(get("/api/my/wikis").with(jwt())).andExpect(status().isOk());
	}
}
