package pro.teaz.wiki.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Production security: a valid Keycloak access token (Bearer JWT) from the
 * realm in application-prod.properties for everything, except reading:
 * public wikis are readable without login, so reading a wiki is let through
 * here and WikiAccess decides per wiki (private ones answer 404 to strangers).
 * A token, if sent, is still checked. Any realm user may use Wiki, no extra role.
 *
 * The MCP server (pro.teaz.wiki.mcp, under /api/mcp) gets its own, narrower
 * chain: same Keycloak realm (so a bad signature/issuer still 401s), but a
 * token without the "wiki:read" scope -- issued only to the wiki-mcp client a
 * user explicitly connected, see README "AI access" -- is downgraded to
 * anonymous (RequireMcpReadScope) rather than trusted: otherwise someone's
 * ordinary browser-session token, with no extra scope, would silently grant
 * the AI the same private access as logging into the wiki itself. Anonymous
 * callers are let through so public wikis stay searchable without any login,
 * same as the rest of the API; WikiAccess still decides per wiki either way.
 */
@Configuration
@Profile("prod")
public class SecurityConfig {

	@Bean
	@Order(1)
	SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http, UserSync userSync) throws Exception {
		http
			.securityMatcher("/api/mcp/**")
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
			.addFilterAfter(new RequireMcpReadScope(), BearerTokenAuthenticationFilter.class)
			.addFilterAfter(userSync, RequireMcpReadScope.class)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(csrf -> csrf.disable());
		return http.build();
	}

	/** A JWT without the "wiki:read" scope counts as no token at all, here. */
	static final class RequireMcpReadScope extends OncePerRequestFilter {
		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
				throws ServletException, IOException {
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			if (auth instanceof JwtAuthenticationToken jwt
					&& jwt.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("SCOPE_wiki:read"))) {
				SecurityContextHolder.clearContext();
			}
			chain.doFilter(request, response);
		}
	}

	@Bean
	@Order(2)
	SecurityFilterChain securityFilterChain(HttpSecurity http, UserSync userSync) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/api/wikis/*", "/api/wikis/*/**", "/api/files/*",
						"/api/preview/**", "/.well-known/oauth-protected-resource").permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
			.addFilterAfter(userSync, BearerTokenAuthenticationFilter.class)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(csrf -> csrf.disable());
		return http.build();
	}
}
