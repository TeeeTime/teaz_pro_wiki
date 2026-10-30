package pro.teaz.wiki.comment;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Every call returns the node's whole, updated comment list. */
@RestController
@RequestMapping("/api/wikis/{wikiId}")
public class CommentController {

	private final CommentService service;

	public CommentController(CommentService service) {
		this.service = service;
	}

	@GetMapping("/nodes/{nodeId}/comments")
	public List<CommentService.CommentView> list(@PathVariable String wikiId, @PathVariable UUID nodeId) {
		return service.list(wikiId, nodeId);
	}

	@PostMapping("/nodes/{nodeId}/comments")
	public List<CommentService.CommentView> create(@PathVariable String wikiId, @PathVariable UUID nodeId,
			@RequestBody CommentService.CommentRequest request) {
		return service.create(wikiId, nodeId, request);
	}

	@PatchMapping("/comments/{id}")
	public List<CommentService.CommentView> edit(@PathVariable String wikiId, @PathVariable UUID id,
			@RequestBody CommentService.CommentRequest request) {
		return service.edit(wikiId, id, request);
	}

	@DeleteMapping("/comments/{id}")
	public List<CommentService.CommentView> delete(@PathVariable String wikiId, @PathVariable UUID id) {
		return service.delete(wikiId, id);
	}
}
