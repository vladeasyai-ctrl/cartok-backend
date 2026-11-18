package pl.cartok.preference;

import java.util.List;

/**
 * priorities[0] — что показывать чаще всего (60% страницы), затем 30% и 10%.
 * Пустая модель — подходит вся марка. Год, топливо и пробег здесь не хранятся.
 */
public record Preferences(List<List<Choice>> priorities) {

	public static final int PRIORITY_COUNT = 3;

	public static Preferences none() {
		return new Preferences(List.of(List.of(), List.of(), List.of()));
	}

	public boolean hasChoices() {
		return priorities.stream().anyMatch(tier -> !tier.isEmpty());
	}

	public record Choice(String make, String model) {
	}
}
