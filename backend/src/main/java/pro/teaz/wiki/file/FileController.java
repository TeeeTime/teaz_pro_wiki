package pro.teaz.wiki.file;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import pro.teaz.wiki.wiki.WikiAccess;

/** Images for pages and logos. Uploading needs edit rights; reading the same rights as the wiki. */
@RestController
@RequestMapping("/api")
public class FileController {

	static final long MAX_BYTES = 10L * 1024 * 1024;

	public record FileView(UUID id, String mediaType, Integer width, Integer height) {
	}

	private final WikiAccess access;
	private final StoredFileRepository files;
	private final FileStorage storage;

	public FileController(WikiAccess access, StoredFileRepository files, FileStorage storage) {
		this.access = access;
		this.files = files;
		this.storage = storage;
	}

	/** Multipart field "file": PNG, JPEG, GIF or WebP, up to 10 MB. */
	@PostMapping("/wikis/{wikiId}/files")
	public FileView upload(@PathVariable String wikiId, @RequestParam("file") MultipartFile file) throws IOException {
		WikiAccess.Grant grant = access.edit(wikiId);
		if (file.getSize() > MAX_BYTES) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Images can be up to 10 MB.");
		}
		byte[] bytes = file.getBytes();
		String mediaType = sniff(bytes);
		if (mediaType == null) {
			throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PNG, JPEG, GIF and WebP images.");
		}
		Integer width = null;
		Integer height = null;
		// ImageIO can't read WebP; the browser knows the size once it has loaded the image anyway.
		BufferedImage image = mediaType.equals("image/webp") ? null : ImageIO.read(new ByteArrayInputStream(bytes));
		if (image != null) {
			width = image.getWidth();
			height = image.getHeight();
		}
		UUID id = UUID.randomUUID();
		String path = storage.save(wikiId, id, bytes);
		files.save(new StoredFile(id, wikiId, grant.userId().orElseThrow(), mediaType, bytes.length, width, height, path));
		return new FileView(id, mediaType, width, height);
	}

	/**
	 * Files never change (a new upload is a new id), so they're cached for a year:
	 * publicly for public wikis, only by the browser for private ones.
	 */
	@GetMapping("/files/{id}")
	public ResponseEntity<Resource> download(@PathVariable UUID id) {
		StoredFile file = files.findById(id).orElseThrow(FileController::notFound);
		WikiAccess.Grant grant;
		try {
			grant = access.read(file.getWikiId());
		} catch (ResponseStatusException e) {
			throw notFound();
		}
		CacheControl cache = CacheControl.maxAge(Duration.ofDays(365)).immutable();
		cache = grant.wiki().isPublic() ? cache.cachePublic() : cache.cachePrivate();
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.getMediaType()))
				.cacheControl(cache)
				.header("X-Content-Type-Options", "nosniff")
				.body(new FileSystemResource(storage.resolve(file.getStoragePath())));
	}

	/** The real type from the first bytes, whatever the upload claims. Null = not an accepted image. */
	static String sniff(byte[] bytes) {
		if (startsWith(bytes, 0, 0x89, 'P', 'N', 'G')) return "image/png";
		if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
		if (startsWith(bytes, 0, 'G', 'I', 'F', '8')) return "image/gif";
		if (startsWith(bytes, 0, 'R', 'I', 'F', 'F') && startsWith(bytes, 8, 'W', 'E', 'B', 'P')) return "image/webp";
		return null;
	}

	private static boolean startsWith(byte[] bytes, int offset, int... expected) {
		if (bytes.length < offset + expected.length) return false;
		return Arrays.equals(Arrays.copyOfRange(bytes, offset, offset + expected.length),
				toBytes(expected));
	}

	private static byte[] toBytes(int[] values) {
		byte[] result = new byte[values.length];
		for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
		return result;
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found.");
	}
}
