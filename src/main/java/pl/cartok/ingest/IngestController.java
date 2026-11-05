package pl.cartok.ingest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.cartok.config.CartokProperties;

@RestController
public class IngestController {

	private final IngestService ingest;
	private final CartokProperties properties;

	public IngestController(IngestService ingest, CartokProperties properties) {
		this.ingest = ingest;
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
		IngestService.Report report = make == null || make.isBlank() ? ingest.ingestNext() : ingest.ingest(make);
		return ResponseEntity.ok(Map.of("make", report.make(), "saved", report.saved()));
	}

	private boolean authorized(String token) {
		String expected = properties.ingest().token();
		if (expected == null || expected.isBlank() || token == null) return false;
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
	}
}
