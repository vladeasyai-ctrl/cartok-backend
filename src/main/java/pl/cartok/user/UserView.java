package pl.cartok.user;

public record UserView(String id, String email, String name) {

	static UserView of(UserEntity entity) {
		return new UserView(entity.getId(), entity.getEmail(), entity.getName());
	}
}
