package pl.cartok.preference;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.cartok.user.UserService;

/** Под X-Service-Token, как и остальные /api/users/**. */
@RestController
public class PreferenceController {

	private final PreferenceService preferences;
	private final UserService users;

	public PreferenceController(PreferenceService preferences, UserService users) {
		this.preferences = preferences;
		this.users = users;
	}

	@GetMapping("/api/users/{id}/preferences")
	public ResponseEntity<Map<String, Object>> load(@PathVariable String id) {
		if (users.find(id).isEmpty()) return notFound();
		return ResponseEntity.ok(Map.of("preferences", preferences.load(id)));
	}

	@PutMapping("/api/users/{id}/preferences")
	public ResponseEntity<Map<String, Object>> save(@PathVariable String id, @RequestBody Preferences body) {
		if (users.find(id).isEmpty()) return notFound();
		try {
			return ResponseEntity.ok(Map.of("preferences", preferences.save(id, body)));
		} catch (IllegalArgumentException error) {
			return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
		}
	}

	private static ResponseEntity<Map<String, Object>> notFound() {
		return ResponseEntity.status(404).body(Map.of("error", "Пользователь не найден"));
	}
}
