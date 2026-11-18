package pl.cartok.ingest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.cartok.config.CartokProperties;

@RestController
public class IngestController {

	private static final int PLAN_SIZE = 15;

	private final IngestService ingest;
	private final ScrapePlanner planner;
	private final CartokProperties properties;

	public IngestController(IngestService ingest, ScrapePlanner planner, CartokProperties properties) {
		this.ingest = ingest;
		this.planner = planner;
		this.properties = properties;
	}

	@PostMapping("/api/ingest")
	public ResponseEntity<Map<String, Object>> ingest(
			@RequestHeader(value = "X-Ingest-Token", required = false) String token,
			@RequestParam(required = false) String make
	) {
		if (!authorized(token)) {
			return ResponseEntity.status(401).body(Map.of("error", "Неверный токен"));
		}
		try {
			IngestService.Report report = make == null || make.isBlank() ? ingest.ingestNext() : ingest.ingest(make);
			return ResponseEntity.ok(Map.of(
					"query", report.query(),
					"page", report.page(),
					"saved", report.saved(),
					"known", report.known(),
					"reason", report.reason()));
		} catch (ResponseStatusException error) {
			return ResponseEntity.status(error.getStatusCode()).body(Map.of("error", String.valueOf(error.getReason())));
		}
	}

	/** Кого планировщик скачал бы сейчас и почему: для подстройки весов. */
	@GetMapping("/api/ingest/plan")
	public ResponseEntity<Map<String, Object>> plan(@RequestHeader(value = "X-Ingest-Token", required = false) String token) {
		if (!authorized(token)) {
			return ResponseEntity.status(401).body(Map.of("error", "Неверный токен"));
		}
		List<Map<String, Object>> candidates = planner.rank().stream()
				.limit(PLAN_SIZE)
				.map(candidate -> {
					Map<String, Object> row = new LinkedHashMap<>();
					row.put("target", candidate.target());
					row.put("interest", round(candidate.interest()));
					row.put("supply", round(candidate.supply()));
					row.put("deficit", round(candidate.deficit()));
					row.put("query", candidate.query().key());
					return row;
				})
				.toList();
		return ResponseEntity.ok(Map.of("candidates", candidates));
	}

	private boolean authorized(String token) {
		String expected = properties.ingest().token();
		if (expected == null || expected.isBlank() || token == null) return false;
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
	}

	private static double round(double value) {
		return Math.round(value * 100) / 100.0;
	}
}
