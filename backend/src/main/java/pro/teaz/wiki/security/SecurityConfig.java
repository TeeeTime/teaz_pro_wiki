package pro.teaz.wiki.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Production security: a valid Keycloak access token (Bearer JWT) from the
 * realm in application-prod.properties for everything, except reading:
 * public wikis are readable without login, so reading a wiki is let through
 * here and WikiAccess decides per wiki (private ones answer 404 to strangers).
 * A token, if sent, is still checked. Any realm user may use Wiki, no extra role.
 */
@Configuration
@Profile("prod")
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, UserSync userSync) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/api/wikis/*", "/api/wikis/*/**", "/api/files/*",
						"/api/preview/**").permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
			.addFilterAfter(userSync, BearerTokenAuthenticationFilter.class)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(csrf -> csrf.disable());
		return http.build();
	}
}
