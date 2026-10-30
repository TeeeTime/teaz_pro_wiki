package pro.teaz.wiki.node;

import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks page content (Tiptap JSON, see frontend/src/editor/) before it is
 * stored and extracts its text for search and link previews. The browser
 * only renders node types its editor knows, so this checks the shape, the
 * size and the one thing that could run code: link targets.
 */
@Component
public class ContentText {

	static final int MAX_LENGTH = 2_000_000;

	private static final Pattern SAFE_HREF = Pattern.compile("^(https?://|mailto:|/|#).*", Pattern.CASE_INSENSITIVE);

	/** Inline nodes: no line break after them. */
	private static final Set<String> INLINE = Set.of("text", "inlineMath", "hardBreak");

	private final JsonMapper json;

	public ContentText(JsonMapper json) {
		this.json = json;
	}

	/** @return the plain text, blocks separated by line breaks */
	public String check(String content) {
		if (content.length() > MAX_LENGTH) throw badRequest("The page is too large.");
		JsonNode root;
		try {
			root = json.readTree(content);
		} catch (JacksonException e) {
			throw badRequest("The page content is not valid.");
		}
		if (!root.isObject() || !"doc".equals(root.path("type").asString(""))) {
			throw badRequest("The page content is not valid.");
		}
		StringBuilder text = new StringBuilder();
		walk(root, text);
		return text.toString().replaceAll("[ \\t]+", " ").replaceAll("\\n{2,}", "\n").strip();
	}

	private void walk(JsonNode node, StringBuilder text) {
		String type = node.path("type").asString("");
		for (JsonNode mark : node.path("marks")) {
			if ("link".equals(mark.path("type").asString(""))) {
				String href = mark.path("attrs").path("href").asString("");
				if (!SAFE_HREF.matcher(href).matches()) {
					throw badRequest("Links must start with http://, https://, mailto: or /.");
				}
			}
		}
		switch (type) {
			case "text" -> text.append(node.path("text").asString(""));
			case "hardBreak" -> text.append('\n');
			case "inlineMath", "blockMath" -> text.append(' ').append(node.path("attrs").path("latex").asString("")).append(' ');
			default -> {
			}
		}
		// Image captions are searchable too.
		for (JsonNode image : node.path("attrs").path("images")) {
			String caption = image.path("caption").asString("");
			if (!caption.isBlank()) text.append(caption).append('\n');
		}
		for (JsonNode child : node.path("content")) walk(child, text);
		if (!INLINE.contains(type)) text.append('\n');
	}

	private static ResponseStatusException badRequest(String detail) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
	}
}
