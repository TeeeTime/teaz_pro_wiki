package pro.teaz.wiki.wiki;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WikiController {

	private final WikiService service;

	public WikiController(WikiService service) {
		this.service = service;
	}

	/** The caller's wiki list (switcher). Not under /api/wikis/* so it needs a login in prod. */
	@GetMapping("/my/wikis")
	public List<WikiDtos.WikiSummary> list() {
		return service.list();
	}

	@PostMapping("/wikis")
	public WikiDtos.WikiDetail create(@RequestBody WikiDtos.CreateWiki request) {
		return service.create(request);
	}

	@GetMapping("/wikis/{id}")
	public WikiDtos.WikiDetail detail(@PathVariable String id) {
		return service.detail(id);
	}

	@PatchMapping("/wikis/{id}")
	public WikiDtos.WikiDetail update(@PathVariable String id, @RequestBody WikiDtos.UpdateWiki request) {
		return service.update(id, request);
	}

	@DeleteMapping("/wikis/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/wikis/{id}/subscription")
	public WikiDtos.WikiDetail subscribe(@PathVariable String id) {
		return service.subscribe(id);
	}

	@DeleteMapping("/wikis/{id}/subscription")
	public WikiDtos.WikiDetail unsubscribe(@PathVariable String id) {
		return service.unsubscribe(id);
	}

	@GetMapping("/wikis/{id}/members")
	public List<WikiDtos.MemberView> members(@PathVariable String id) {
		return service.members(id);
	}

	@PutMapping("/wikis/{id}/members/{userId}")
	public List<WikiDtos.MemberView> putMember(@PathVariable String id, @PathVariable String userId,
			@RequestBody WikiDtos.MemberRequest request) {
		return service.putMember(id, userId, request);
	}

	@DeleteMapping("/wikis/{id}/members/{userId}")
	public List<WikiDtos.MemberView> removeMember(@PathVariable String id, @PathVariable String userId) {
		return service.removeMember(id, userId);
	}
}
