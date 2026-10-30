package pro.teaz.wiki;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code wiki.*}, see application.properties.
 *
 * @param storageDir where uploaded images are kept
 * @param publicUrl  the app's own URL, for links in link previews
 * @param keycloak   Admin API access for the user search
 */
@ConfigurationProperties("wiki")
public record WikiProperties(Path storageDir, @DefaultValue("http://localhost:5173") String publicUrl,
		@DefaultValue Keycloak keycloak) {

	/**
	 * @param adminClientSecret unset or blank = MockUserDirectory
	 */
	public record Keycloak(
			@DefaultValue("https://auth.teaz.pro") String url,
			@DefaultValue("teazpro") String realm,
			@DefaultValue("wiki-admin") String adminClientId,
			String adminClientSecret) {

		public boolean enabled() {
			return adminClientSecret != null && !adminClientSecret.isBlank();
		}
	}
}
