package pro.teaz.wiki.node;

import java.text.Normalizer;
import java.util.Locale;

/**
 * URL segments from names: "Über uns & Kontakt" → "ueber-uns-kontakt". Two
 * names with the same slug can't be siblings, since they'd share one URL.
 */
public final class Slugs {

	private Slugs() {
	}

	/** @return lowercase [a-z0-9-], never starting or ending with "-"; empty if the name has no letter or digit */
	public static String of(String name) {
		String slug = name.toLowerCase(Locale.ROOT)
				.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
		slug = Normalizer.normalize(slug, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		slug = slug.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		return slug;
	}
}
