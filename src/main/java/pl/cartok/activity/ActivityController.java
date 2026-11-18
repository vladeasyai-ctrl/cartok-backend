package pl.cartok.activity;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.cartok.user.UserService;

/** Под X-Service-Token, как и остальные /api/users/**. */
@RestController
public class ActivityController {

	private final ActivityService activity;
	private final UserService users;

	public ActivityController(ActivityService activity, UserService users) {
		this.activity = activity;
		this.users = users;
	}

	@PostMapping("/api/users/{id}/activity")
	public ResponseEntity<Map<String, Object>> record(@PathVariable String id, @RequestBody Batch body) {
		if (users.find(id).isEmpty()) {
			return ResponseEntity.status(404).body(Map.of("error", "Пользователь не найден"));
		}
		List<ActivityService.Event> events = body.events() == null ? List.of() : body.events();
		if (events.size() > ActivityService.MAX_EVENTS) {
			return ResponseEntity.badRequest().body(Map.of("error", "Слишком много событий за раз"));
		}
		return ResponseEntity.ok(Map.of("recorded", activity.record(id, events)));
	}

	public record Batch(List<ActivityService.Event> events) {
	}
}
