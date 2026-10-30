package pro.teaz.wiki.users;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import pro.teaz.wiki.WikiProperties;

/** Keycloak when an admin client secret is set, the mock otherwise (like the frontend's auth). */
@Configuration
public class DirectoryConfig {

	private static final Logger log = LoggerFactory.getLogger(DirectoryConfig.class);

	@Bean
	UserDirectory userDirectory(WikiProperties properties) {
		if (properties.keycloak().enabled()) return new KeycloakUserDirectory(properties.keycloak(), RestClient.builder());
		log.info("No wiki.keycloak.admin-client-secret: user search uses MockUserDirectory");
		return new MockUserDirectory();
	}
}
