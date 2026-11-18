package pl.cartok.user;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	private final UserRepository users;
	private final PasswordEncoder passwords = new BCryptPasswordEncoder();
	/** Сверяем пароль и для несуществующего email, чтобы по времени ответа не было видно, есть ли аккаунт. */
	private final String missingUserHash = passwords.encode(UUID.randomUUID().toString());

	public UserService(UserRepository users) {
		this.users = users;
	}

	/** Пусто — email уже занят. */
	public Optional<UserView> register(String email, String password, String name) {
		String normalized = normalizeEmail(email);
		if (users.existsByEmail(normalized)) return Optional.empty();
		UserEntity user = new UserEntity();
		user.setId("user_" + UUID.randomUUID());
		user.setEmail(normalized);
		user.setName(name.trim());
		user.setPasswordHash(passwords.encode(password));
		user.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
		try {
			return Optional.of(UserView.of(users.saveAndFlush(user)));
		} catch (DataIntegrityViolationException error) {
			return Optional.empty();
		}
	}

	public Optional<UserView> login(String email, String password) {
		Optional<UserEntity> user = users.findByEmail(normalizeEmail(email));
		String hash = user.map(UserEntity::getPasswordHash).orElse(null);
		if (hash == null || hash.isBlank()) {
			passwords.matches(password, missingUserHash);
			return Optional.empty();
		}
		return passwords.matches(password, hash) ? user.map(UserView::of) : Optional.empty();
	}

	/**
	 * Пусто — этот email уже привязан к другому Google-аккаунту.
	 * Тот же подтверждённый email без google_sub привязывается к существующему пользователю.
	 */
	public Optional<UserView> signInWithGoogle(String sub, String email, String name) {
		Optional<UserEntity> bySub = users.findByGoogleSub(sub);
		if (bySub.isPresent()) return bySub.map(UserView::of);

		String normalized = normalizeEmail(email);
		Optional<UserEntity> byEmail = users.findByEmail(normalized);
		if (byEmail.isPresent()) {
			UserEntity existing = byEmail.get();
			if (existing.getGoogleSub() != null && !existing.getGoogleSub().equals(sub)) return Optional.empty();
			existing.setGoogleSub(sub);
			return Optional.of(UserView.of(users.saveAndFlush(existing)));
		}

		UserEntity user = new UserEntity();
		user.setId("user_" + UUID.randomUUID());
		user.setEmail(normalized);
		user.setName(displayName(name, normalized));
		user.setGoogleSub(sub);
		user.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
		try {
			return Optional.of(UserView.of(users.saveAndFlush(user)));
		} catch (DataIntegrityViolationException error) {
			return users.findByGoogleSub(sub)
					.or(() -> users.findByEmail(normalized))
					.filter(existing -> existing.getGoogleSub() == null || sub.equals(existing.getGoogleSub()))
					.map(existing -> {
						if (existing.getGoogleSub() == null) existing.setGoogleSub(sub);
						return UserView.of(users.saveAndFlush(existing));
					});
		}
	}

	public Optional<UserView> find(String id) {
		return users.findById(id).map(UserView::of);
	}

	void ensureDemo() {
		if (users.existsByEmail(DemoUser.EMAIL)) return;
		UserEntity user = new UserEntity();
		user.setId(DemoUser.ID);
		user.setEmail(DemoUser.EMAIL);
		user.setName(DemoUser.NAME);
		user.setPasswordHash(passwords.encode(DemoUser.PASSWORD));
		user.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
		try {
			users.saveAndFlush(user);
		} catch (DataIntegrityViolationException ignored) {
			// Второй экземпляр бекенда успел создать его раньше.
		}
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private static String displayName(String name, String email) {
		String cleaned = name == null ? "" : name.trim().replaceAll("\\s+", " ");
		if (cleaned.length() > 40) cleaned = cleaned.substring(0, 40).trim();
		if (cleaned.length() >= 2) return cleaned;
		int at = email.indexOf('@');
		String local = (at > 0 ? email.substring(0, at) : email).replaceAll("[^\\p{L}\\p{N} ._-]", "").trim();
		if (local.length() > 40) local = local.substring(0, 40).trim();
		if (local.length() >= 2) return local;
		return "Водитель";
	}
}
