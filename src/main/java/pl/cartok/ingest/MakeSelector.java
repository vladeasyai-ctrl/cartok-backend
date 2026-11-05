package pl.cartok.ingest;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import pl.cartok.config.CartokProperties;
import pl.cartok.listing.ListingService;

/**
 * Picks the make that was refreshed longest ago. Attempts are remembered in memory as well,
 * otherwise a make whose page fails or comes back empty would be picked on every run.
 */
@Component
public class MakeSelector {

	private final List<String> makes;
	private final ListingService listings;
	private final Map<String, OffsetDateTime> attempts = new ConcurrentHashMap<>();

	public MakeSelector(CartokProperties properties, ListingService listings) {
		this.makes = properties.otomoto().makes().stream()
				.map(make -> make.trim().toLowerCase(Locale.ROOT))
				.filter(make -> !make.isBlank())
				.distinct()
				.toList();
		this.listings = listings;
	}

	public String next() {
		if (makes.isEmpty()) throw new IllegalStateException("Не задан ни один cartok.otomoto.makes");
		Map<String, OffsetDateTime> seen = listings.lastSeenBySlug();
		return makes.stream()
				.min(Comparator.comparing(make -> latest(seen.get(make), attempts.get(make)),
						Comparator.nullsFirst(Comparator.naturalOrder())))
				.orElseThrow();
	}

	public void attempted(String make, OffsetDateTime at) {
		attempts.put(make, at);
	}

	private static OffsetDateTime latest(OffsetDateTime first, OffsetDateTime second) {
		if (first == null) return second;
		if (second == null) return first;
		return first.isAfter(second) ? first : second;
	}
}
