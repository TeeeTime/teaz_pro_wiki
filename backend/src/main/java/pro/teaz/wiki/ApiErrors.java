package pro.teaz.wiki;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pro.teaz.wiki.users.UserDirectory;

/** Failures that aren't a ResponseStatusException, as problem details with a readable detail. */
@RestControllerAdvice
public class ApiErrors {

	/** Two requests creating the same name at once: the unique index catches what the check before missed. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail duplicate(DataIntegrityViolationException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "That name is already taken here.");
	}

	@ExceptionHandler(UserDirectory.DirectoryException.class)
	public ProblemDetail directory(UserDirectory.DirectoryException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
	}
}
