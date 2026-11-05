package pl.cartok.feed;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.cartok.listing.ListingService;

@RestController
public class ListingController {

	private static final Pattern SEEN_ID = Pattern.compile("^[A-Za-z0-9_-]{1,80}$");
	private static final int SEEN_LIMIT = 50;

	private final ListingService listings;

	public ListingController(ListingService listings) {
		this.listings = listings;
	}

	@GetMapping("/api/listings")
	public Map<String, Object> list(@RequestParam(defaultValue = "30") int limit) {
		return Map.of("listings", listings.feed(pageSize(limit)));
	}

	@GetMapping("/api/feed")
	public Map<String, Object> feed(
			@RequestParam(defaultValue = "30") int limit,
			@RequestParam(required = false) String seen
	) {
		return Map.of("listings", listings.feed(pageSize(limit), seenIds(seen)));
	}

	@GetMapping("/api/listings/{id}")
	public ResponseEntity<Map<String, Object>> one(@PathVariable String id) {
		var listing = listings.findActive(id);
		if (listing == null) {
			return ResponseEntity.status(404).body(Map.of("error", "Объявление не найдено"));
		}
		return ResponseEntity.ok(Map.of("listing", listing));
	}

	private static int pageSize(int limit) {
		return Math.min(Math.max(limit, 1), 50);
	}

	private static List<String> seenIds(String raw) {
		if (raw == null || raw.isBlank()) return List.of();
		List<String> ids = new ArrayList<>();
		for (String part : raw.split(",")) {
			String id = part.trim();
			if (!SEEN_ID.matcher(id).matches() || ids.contains(id)) continue;
			ids.add(id);
			if (ids.size() >= SEEN_LIMIT) break;
		}
		return List.copyOf(ids);
	}
}
