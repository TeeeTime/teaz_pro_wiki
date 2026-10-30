package pro.teaz.wiki.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pro.teaz.wiki.users.Users;

/** {@link UserSync} as a bean for both security chains, but not registered as a servlet filter on its own. */
@Configuration
public class UserSyncConfig {

	@Bean
	UserSync userSync(Users users) {
		return new UserSync(users);
	}

	@Bean
	FilterRegistrationBean<UserSync> userSyncRegistration(UserSync userSync) {
		FilterRegistrationBean<UserSync> registration = new FilterRegistrationBean<>(userSync);
		registration.setEnabled(false);
		return registration;
	}
}
