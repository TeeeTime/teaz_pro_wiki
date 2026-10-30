package pro.teaz.wiki.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import pro.teaz.wiki.users.MockUserDirectory;
import pro.teaz.wiki.users.Users;

/**
 * Keeps app_user current with the caller's name from their token, so owners,
 * editors and comment authors show with their current names. Only part of the
 * security chains (UserSyncConfig), not a servlet filter of its own.
 */
public class UserSync extends OncePerRequestFilter {

	private final Users users;

	public UserSync(Users users) {
		this.users = users;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof JwtAuthenticationToken token) {
			Jwt jwt = token.getToken();
			String username = jwt.getClaimAsString("preferred_username");
			if (username == null) username = jwt.getSubject();
			String name = jwt.getClaimAsString("name");
			users.remember(jwt.getSubject(), username, name != null ? name : username, jwt.getClaimAsString("email"));
		} else if (auth instanceof MockSecurityConfig.MockAuthentication mock) {
			MockUserDirectory.byId(mock.getName()).ifPresent(users::remember);
		}
		chain.doFilter(request, response);
	}
}
