package pro.teaz.wiki.users;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import pro.teaz.wiki.WikiProperties;

/**
 * Users of the realm via the Keycloak Admin API, as the confidential client
 * {@code wiki.keycloak.admin-client-id} (service account with
 * realm-management → view-users, see README). Its token is kept until shortly
 * before it expires.
 */
public class KeycloakUserDirectory implements UserDirectory {

	/** Fetch a new token this long before the current one expires. */
	private static final long TOKEN_MARGIN_SECONDS = 30;

	private record TokenResponse(String access_token, long expires_in) {
	}

	private record KeycloakUser(String id, String username, String firstName, String lastName, String email,
			Boolean enabled) {

		DirectoryUser toDirectoryUser() {
			String name = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
			return new DirectoryUser(id, username, name.isEmpty() ? username : name, email);
		}
	}

	private final WikiProperties.Keycloak settings;
	private final RestClient http;
	private String token;
	private Instant tokenValidUntil = Instant.EPOCH;

	public KeycloakUserDirectory(WikiProperties.Keycloak settings, RestClient.Builder http) {
		this.settings = settings;
		this.http = http.baseUrl(settings.url()).build();
	}

	@Override
	public List<DirectoryUser> search(String query, int max) {
		try {
			KeycloakUser[] users = http.get()
					.uri("/admin/realms/{realm}/users?search={q}&max={max}&briefRepresentation=true",
							settings.realm(), query, max)
					.header("Authorization", "Bearer " + token())
					.retrieve()
					.body(KeycloakUser[].class);
			if (users == null) return List.of();
			return Arrays.stream(users)
					.filter(user -> !Boolean.FALSE.equals(user.enabled()))
					.map(KeycloakUser::toDirectoryUser)
					.toList();
		} catch (RestClientException e) {
			throw new DirectoryException("The user search is not available right now.", e);
		}
	}

	@Override
	public Optional<DirectoryUser> find(String id) {
		try {
			KeycloakUser user = http.get()
					.uri("/admin/realms/{realm}/users/{id}", settings.realm(), id)
					.header("Authorization", "Bearer " + token())
					.retrieve()
					.body(KeycloakUser.class);
			return Optional.ofNullable(user).map(KeycloakUser::toDirectoryUser);
		} catch (HttpClientErrorException.NotFound e) {
			return Optional.empty();
		} catch (RestClientException e) {
			throw new DirectoryException("The user directory is not available right now.", e);
		}
	}

	private synchronized String token() {
		if (token != null && Instant.now().isBefore(tokenValidUntil)) return token;
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "client_credentials");
		form.add("client_id", settings.adminClientId());
		form.add("client_secret", settings.adminClientSecret());
		TokenResponse response = http.post()
				.uri("/realms/{realm}/protocol/openid-connect/token", settings.realm())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(TokenResponse.class);
		if (response == null || response.access_token() == null) {
			throw new DirectoryException("Keycloak sent no token.", null);
		}
		token = response.access_token();
		tokenValidUntil = Instant.now().plusSeconds(Math.max(0, response.expires_in() - TOKEN_MARGIN_SECONDS));
		return token;
	}
}
