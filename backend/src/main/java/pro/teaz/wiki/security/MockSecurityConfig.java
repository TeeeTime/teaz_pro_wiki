package pro.teaz.wiki.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import pro.teaz.wiki.users.MockUserDirectory;

/**
 * Local-dev stand-in for {@link SecurityConfig}: no Keycloak; the frontend's
 * mock auth sends the chosen user's id as X-Mock-User (no header = guest), so
 * sharing and anonymous reading can be tried locally with several users.
 * Active whenever the "prod" profile is not.
 */
@Configuration
@Profile("!prod")
public class MockSecurityConfig {

	public static final String HEADER = "X-Mock-User";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, UserSync userSync) throws Exception {
		MockUserFilter mockUser = new MockUserFilter();
		http
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
			.addFilterBefore(mockUser, AnonymousAuthenticationFilter.class)
			.addFilterAfter(userSync, MockUserFilter.class)
			.csrf(csrf -> csrf.disable());
		return http.build();
	}

	/** Only the ids of MockUserDirectory's users count, anything else is a guest. */
	static class MockUserFilter extends OncePerRequestFilter {
		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
				throws ServletException, IOException {
			String id = request.getHeader(HEADER);
			if (id != null && MockUserDirectory.byId(id).isPresent()) {
				SecurityContextHolder.getContext().setAuthentication(new MockAuthentication(id));
			}
			chain.doFilter(request, response);
		}
	}

	public static class MockAuthentication extends AbstractAuthenticationToken {
		private final String id;

		MockAuthentication(String id) {
			super(List.of());
			this.id = id;
			setAuthenticated(true);
		}

		@Override
		public Object getCredentials() {
			return "";
		}

		@Override
		public Object getPrincipal() {
			return id;
		}

		@Override
		public String getName() {
			return id;
		}
	}
}
