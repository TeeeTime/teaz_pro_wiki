package pro.teaz.wiki.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * /api/mcp lets anonymous callers through (public wikis need no login, same
 * as the rest of the API) and never answers 401/403 for a missing or wrong
 * scope either -- such a token is downgraded to anonymous instead (see
 * SecurityConfig.RequireMcpReadScope; RequireMcpReadScopeTests covers that
 * downgrade itself, i.e. that it doesn't leak an unrelated token's identity).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"prod", "test"})
class McpSecurityTests {

	@Autowired
	MockMvc mvc;

	@Test
	void mcpIsReachableWithOrWithoutTheReadScope() throws Exception {
		assertNotASecurityRejection(mvc.perform(post("/api/mcp")));
		assertNotASecurityRejection(mvc.perform(post("/api/mcp").with(jwt().jwt(t -> t.subject("mock-jordan")))));
		assertNotASecurityRejection(mvc.perform(post("/api/mcp")
				.with(jwt().jwt(t -> t.subject("mock-jordan")).authorities(new SimpleGrantedAuthority("SCOPE_wiki:read")))));
	}

	private static void assertNotASecurityRejection(ResultActions result) throws Exception {
		int status = result.andReturn().getResponse().getStatus();
		assertThat(status).isNotIn(401, 403);
	}

	@Test
	void resourceMetadataIsPublic() throws Exception {
		mvc.perform(get("/.well-known/oauth-protected-resource"))
			.andExpect(status().isOk());
	}
}
