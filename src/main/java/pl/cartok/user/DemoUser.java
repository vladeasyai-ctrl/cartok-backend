package pl.cartok.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Демо-аккаунт, который подсказывает экран входа фронтенда. */
@Component
class DemoUser implements ApplicationRunner {

	static final String ID = "user_demo";
	static final String EMAIL = "demo@cartok.dev";
	static final String NAME = "Демо";
	static final String PASSWORD = "demo1234";

	private final UserService users;

	DemoUser(UserService users) {
		this.users = users;
	}

	@Override
	public void run(ApplicationArguments args) {
		users.ensureDemo();
	}
}
