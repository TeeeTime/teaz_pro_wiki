package pro.teaz.wiki.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import pro.teaz.wiki.WikiProperties;

/**
 * Uploaded images on disk, one folder per wiki: {@code <storageDir>/<wikiId>/<fileId>}.
 * Paths stored in the database are relative to storageDir, so the folder can move.
 */
@Component
public class FileStorage {

	private final Path root;

	public FileStorage(WikiProperties properties) {
		this.root = properties.storageDir().toAbsolutePath().normalize();
	}

	/** @return path relative to the storage root, for StoredFile.storagePath */
	public String save(String wikiId, UUID fileId, byte[] content) {
		Path relative = Path.of(wikiId, fileId.toString());
		Path target = root.resolve(relative);
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not store " + relative, e);
		}
		return relative.toString().replace('\\', '/');
	}

	public Path resolve(String storagePath) {
		Path path = root.resolve(storagePath).normalize();
		if (!path.startsWith(root)) throw new IllegalArgumentException("Path outside storage: " + storagePath);
		return path;
	}

	public void deleteWiki(String wikiId) {
		Path dir = root.resolve(wikiId);
		if (!Files.exists(dir)) return;
		try (Stream<Path> paths = Files.walk(dir)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		} catch (IOException e) {
			throw new UncheckedIOException("Could not delete " + dir, e);
		}
	}
}
