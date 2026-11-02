package pro.teaz.wiki.mcp;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pro.teaz.wiki.WikiProperties;

/**
 * OAuth 2.0 Protected Resource Metadata (RFC 9728): tells an MCP client that
 * /api/mcp is protected by the same Keycloak realm SecurityConfig already
 * validates tokens against. Served at a fixed, unprefixed path required by
 * the spec (see frontend/nginx.conf for why that needs its own routing) --
 * only meaningful where that realm is real, i.e. in prod.
 */
@RestController
@Profile("prod")
public class WellKnownController {

	private final String issuer;
	private final String resource;

	public WellKnownController(WikiProperties properties,
			@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
		this.issuer = issuer;
		this.resource = properties.publicUrl() + "/api/mcp";
	}

	@GetMapping("/.well-known/oauth-protected-resource")
	public Map<String, Object> metadata() {
		return Map.of(
				"resource", resource,
				"authorization_servers", List.of(issuer),
				"scopes_supported", List.of("wiki:read"),
				"bearer_methods_supported", List.of("header"));
	}
}
