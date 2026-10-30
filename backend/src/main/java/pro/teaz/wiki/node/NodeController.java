package pro.teaz.wiki.node;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wikis/{wikiId}")
public class NodeController {

	private final NodeService service;

	public NodeController(NodeService service) {
		this.service = service;
	}

	@GetMapping("/tree")
	public List<NodeDtos.TreeNode> tree(@PathVariable String wikiId) {
		return service.tree(wikiId);
	}

	@GetMapping("/overview")
	public NodeDtos.Overview overview(@PathVariable String wikiId) {
		return service.overview(wikiId);
	}

	@GetMapping("/search")
	public List<NodeDtos.SearchHit> search(@PathVariable String wikiId, @RequestParam String q) {
		return service.search(wikiId, q);
	}

	/** {@code path}: the slugs after the wiki id, e.g. "guides/setup/linux". */
	@GetMapping("/nodes/by-path")
	public NodeDtos.NodeDetail byPath(@PathVariable String wikiId, @RequestParam String path) {
		return service.byPath(wikiId, path);
	}

	@GetMapping("/nodes/{id}")
	public NodeDtos.NodeDetail get(@PathVariable String wikiId, @PathVariable UUID id) {
		return service.get(wikiId, id);
	}

	@PostMapping("/nodes")
	public NodeDtos.NodeDetail create(@PathVariable String wikiId, @RequestBody NodeDtos.CreateNode request) {
		return service.create(wikiId, request);
	}

	@PutMapping("/nodes/{id}")
	public NodeDtos.NodeDetail update(@PathVariable String wikiId, @PathVariable UUID id,
			@RequestBody NodeDtos.UpdateNode request) {
		return service.update(wikiId, id, request);
	}

	/** Returns the whole new structure. */
	@PostMapping("/nodes/{id}/move")
	public List<NodeDtos.TreeNode> move(@PathVariable String wikiId, @PathVariable UUID id,
			@RequestBody NodeDtos.MoveNode request) {
		return service.move(wikiId, id, request);
	}

	@DeleteMapping("/nodes/{id}")
	public ResponseEntity<Void> delete(@PathVariable String wikiId, @PathVariable UUID id,
			@RequestParam(defaultValue = "false") boolean recursive) {
		service.delete(wikiId, id, recursive);
		return ResponseEntity.noContent().build();
	}
}
