package pl.cartok.user;

import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Вызывается сервером Next под X-Service-Token; подробная проверка ввода — на его стороне. */
@RestController
public class UserController {

	private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private final UserService users;

	public UserController(UserService users) {
		this.users = users;
	}

	@PostMapping("/api/users")
	public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest body) {
		String error = credentialsError(body.email(), body.password());
		int nameLength = body.name() == null ? 0 : body.name().trim().length();
		if (error == null && (nameLength < 2 || nameLength > 40)) {
			error = "Имя должно быть от 2 до 40 символов";
		}
		if (error != null) return ResponseEntity.badRequest().body(Map.of("error", error));
		return users.register(body.email(), body.password(), body.name())
				.<ResponseEntity<Map<String, Object>>>map(user -> ResponseEntity.status(201).body(Map.of("user", user)))
				.orElseGet(() -> ResponseEntity.status(409).body(Map.of("error", "Такой email уже зарегистрирован")));
	}

	@PostMapping("/api/users/google")
	public ResponseEntity<Map<String, Object>> google(@RequestBody GoogleRequest body) {
		if (!Boolean.TRUE.equals(body.emailVerified())) {
			return ResponseEntity.status(401).body(Map.of("error", "Google не подтвердил email"));
		}
		if (emailError(body.email()) != null || body.sub() == null || !body.sub().matches("^[A-Za-z0-9_-]{1,255}$")) {
			return ResponseEntity.badRequest().body(Map.of("error", "Некорректные данные Google"));
		}
		return users.signInWithGoogle(body.sub(), body.email(), body.name())
				.<ResponseEntity<Map<String, Object>>>map(user -> ResponseEntity.ok(Map.of("user", user)))
				.orElseGet(() -> ResponseEntity.status(409).body(Map.of("error", "Этот email уже привязан к другому Google-аккаунту")));
	}

	@PostMapping("/api/users/login")
	public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest body) {
		if (credentialsError(body.email(), body.password()) != null) return invalidLogin();
		return users.login(body.email(), body.password())
				.<ResponseEntity<Map<String, Object>>>map(user -> ResponseEntity.ok(Map.of("user", user)))
				.orElseGet(UserController::invalidLogin);
	}

	@GetMapping("/api/users/{id}")
	public ResponseEntity<Map<String, Object>> one(@PathVariable String id) {
		return users.find(id)
				.<ResponseEntity<Map<String, Object>>>map(user -> ResponseEntity.ok(Map.of("user", user)))
				.orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "Пользователь не найден")));
	}

	private static String credentialsError(String email, String password) {
		String error = emailError(email);
		if (error != null) return error;
		if (password == null || password.length() < 8 || password.length() > 72) {
			return "Пароль должен быть от 8 до 72 символов";
		}
		return null;
	}

	private static String emailError(String email) {
		if (email == null || email.length() > 120 || !EMAIL.matcher(email.trim()).matches()) {
			return "Укажите корректный email";
		}
		return null;
	}

	private static ResponseEntity<Map<String, Object>> invalidLogin() {
		return ResponseEntity.status(401).body(Map.of("error", "Неверный email или пароль"));
	}

	public record RegisterRequest(String email, String password, String name) {
	}

	public record LoginRequest(String email, String password) {
	}

	public record GoogleRequest(String sub, String email, String name, Boolean emailVerified) {
	}
}
