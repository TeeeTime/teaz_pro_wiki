package pro.teaz.wiki.preview;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import pro.teaz.wiki.WikiProperties;
import pro.teaz.wiki.node.Node;
import pro.teaz.wiki.node.NodeRepository;
import pro.teaz.wiki.wiki.Wiki;
import pro.teaz.wiki.wiki.WikiRepository;

/**
 * Link previews (Discord, Slack, WhatsApp, ...): their crawlers don't run the
 * SPA, so nginx sends them here instead (frontend/nginx.conf), with the path
 * they asked for after /api/preview. Answers a small page with Open Graph
 * tags. Only public wikis show their names and text; for anything else the
 * preview is the same as for a wiki that doesn't exist.
 */
@RestController
public class PreviewController {

	static final String PREFIX = "/api/preview";
	static final int DESCRIPTION_LENGTH = 200;
	static final String SITE_NAME = "Teaz Wiki";
	/** --color-accent of the light theme: the stripe next to Discord's embed. */
	static final String THEME_COLOR = "#3b6df0";

	record Preview(String title, String description, String url, String imageUrl) {
	}

	private final WikiRepository wikis;
	private final NodeRepository nodes;
	private final WikiProperties properties;

	public PreviewController(WikiRepository wikis, NodeRepository nodes, WikiProperties properties) {
		this.wikis = wikis;
		this.nodes = nodes;
		this.properties = properties;
	}

	@GetMapping({ PREFIX, PREFIX + "/**" })
	@Transactional(readOnly = true)
	public ResponseEntity<String> preview(HttpServletRequest request) {
		String rest = request.getRequestURI().substring(request.getContextPath().length() + PREFIX.length());
		List<String> segments = Arrays.stream(rest.split("/"))
				.filter(s -> !s.isBlank())
				.map(s -> URLDecoder.decode(s, StandardCharsets.UTF_8))
				.toList();
		Preview preview = segments.isEmpty() ? null : wikiPreview(segments).orElse(null);
		if (preview == null) {
			preview = new Preview(SITE_NAME, "Wikis on Teaz.", base() + "/", null);
		}
		return ResponseEntity.ok()
				.contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
				.cacheControl(CacheControl.noCache())
				.body(html(preview));
	}

	private Optional<Preview> wikiPreview(List<String> segments) {
		Optional<Wiki> found = wikis.findById(segments.getFirst()).filter(Wiki::isPublic);
		if (found.isEmpty()) return Optional.empty();
		Wiki wiki = found.get();
		String imageUrl = wiki.getLogoFileId() == null ? null : base() + "/api/files/" + wiki.getLogoFileId();
		String wikiUrl = base() + "/" + wiki.getId();

		Node node = null;
		for (String slug : segments.subList(1, Math.min(segments.size(), 4))) {
			UUID parentKey = node == null ? new UUID(0, 0) : node.getId();
			node = nodes.findByWikiIdAndParentKeyAndSlug(wiki.getId(), parentKey, slug.toLowerCase(Locale.ROOT)).orElse(null);
			if (node == null) break;
		}
		if (node == null) {
			String description = wiki.getDescription().isBlank() ? "A wiki on Teaz." : wiki.getDescription();
			return Optional.of(new Preview(wiki.getName(), shorten(description), wikiUrl, imageUrl));
		}
		String description = node.getPlainText().isBlank()
				? (node.getKind() == Node.Kind.CATEGORY ? "Category in " : "Page in ") + wiki.getName()
				: node.getPlainText();
		return Optional.of(new Preview(node.getTitle() + " · " + wiki.getName(), shorten(description),
				wikiUrl + "/" + String.join("/", segments.subList(1, segments.size())), imageUrl));
	}

	private String base() {
		return properties.publicUrl().replaceAll("/+$", "");
	}

	static String shorten(String text) {
		String flat = text.replaceAll("\\s+", " ").strip();
		if (flat.length() <= DESCRIPTION_LENGTH) return flat;
		int cut = flat.lastIndexOf(' ', DESCRIPTION_LENGTH);
		return flat.substring(0, cut > DESCRIPTION_LENGTH / 2 ? cut : DESCRIPTION_LENGTH) + "…";
	}

	static String html(Preview preview) {
		String title = escape(preview.title());
		String description = escape(preview.description());
		String url = escape(preview.url());
		StringBuilder html = new StringBuilder()
				.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n")
				.append("<title>").append(title).append("</title>\n")
				.append("<meta name=\"description\" content=\"").append(description).append("\">\n")
				.append("<meta property=\"og:type\" content=\"website\">\n")
				.append("<meta property=\"og:site_name\" content=\"").append(SITE_NAME).append("\">\n")
				.append("<meta property=\"og:title\" content=\"").append(title).append("\">\n")
				.append("<meta property=\"og:description\" content=\"").append(description).append("\">\n")
				.append("<meta property=\"og:url\" content=\"").append(url).append("\">\n")
				.append("<meta name=\"theme-color\" content=\"").append(THEME_COLOR).append("\">\n")
				.append("<meta name=\"twitter:card\" content=\"summary\">\n");
		if (preview.imageUrl() != null) {
			html.append("<meta property=\"og:image\" content=\"").append(escape(preview.imageUrl())).append("\">\n");
		}
		return html.append("</head>\n<body><a href=\"").append(url).append("\">").append(title)
				.append("</a></body>\n</html>\n")
				.toString();
	}

	/** Only what HTML needs escaped; the page is UTF-8, so · and umlauts stay as they are. */
	private static String escape(String text) {
		return HtmlUtils.htmlEscape(text, StandardCharsets.UTF_8.name());
	}
}
