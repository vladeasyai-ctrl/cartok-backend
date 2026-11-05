package pl.cartok.feed;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.cartok.listing.ListingService;

@RestController
public class ListingController {

	private final ListingService listings;

	public ListingController(ListingService listings) {
		this.listings = listings;
	}

	@GetMapping("/api/listings")
	public Map<String, Object> list(@RequestParam(defaultValue = "30") int limit) {
		int size = Math.min(Math.max(limit, 1), 50);
		return Map.of("listings", listings.feed(size));
	}

	@GetMapping("/api/listings/{id}")
	public ResponseEntity<Map<String, Object>> one(@PathVariable String id) {
		var listing = listings.findActive(id);
		if (listing == null) {
			return ResponseEntity.status(404).body(Map.of("error", "Объявление не найдено"));
		}
		return ResponseEntity.ok(Map.of("listing", listing));
	}
}
