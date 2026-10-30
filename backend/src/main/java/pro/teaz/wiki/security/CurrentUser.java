package pro.teaz.wiki.security;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Who makes a request: the Keycloak user id (JWT "sub") in prod, or nobody
 * (public wikis are readable without login). Without the prod profile there's
 * no Keycloak; MockSecurityConfig takes the user from the X-Mock-User header.
 */
@Component
public class CurrentUser {

	public Optional<String> id() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof JwtAuthenticationToken jwt) return Optional.of(jwt.getToken().getSubject());
		if (auth instanceof MockSecurityConfig.MockAuthentication mock) return Optional.of(mock.getName());
		return Optional.empty();
	}

	/** For everything that needs a login: anonymous callers get 401. */
	public String requireId() {
		return id().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first."));
	}
}
