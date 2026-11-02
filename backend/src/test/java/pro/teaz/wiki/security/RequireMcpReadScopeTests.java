package pro.teaz.wiki.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The downgrade at the heart of the MCP security chain: a token without the
 * "wiki:read" scope must count as no token at all, never as that user. If
 * this were wrong, an AI assistant holding someone's ordinary browser-session
 * token (no extra scope, never consented to AI access) would see that
 * person's private wikis through list_wikis/search_wiki.
 */
class RequireMcpReadScopeTests {

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	private static JwtAuthenticationToken tokenWithAuthorities(List<SimpleGrantedAuthority> authorities) {
		Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "someone")
				.issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
		return new JwtAuthenticationToken(jwt, authorities);
	}

	private static void runFilter() throws Exception {
		new SecurityConfig.RequireMcpReadScope()
				.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
	}

	@Test
	void aTokenWithoutTheScopeIsDowngradedToAnonymous() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(tokenWithAuthorities(List.of()));
		runFilter();
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void aTokenWithAnUnrelatedScopeIsAlsoDowngraded() throws Exception {
		SecurityContextHolder.getContext()
				.setAuthentication(tokenWithAuthorities(List.of(new SimpleGrantedAuthority("SCOPE_profile"))));
		runFilter();
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void aTokenWithTheReadScopeIsKept() throws Exception {
		JwtAuthenticationToken token = tokenWithAuthorities(List.of(new SimpleGrantedAuthority("SCOPE_wiki:read")));
		SecurityContextHolder.getContext().setAuthentication(token);
		runFilter();
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(token);
	}

	@Test
	void noTokenAtAllStaysNoToken() throws Exception {
		runFilter();
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}
}
