package pl.cartok.preference;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferenceService {

	private static final int CHOICES_PER_PRIORITY = 30;

	private final PreferenceRepository preferences;

	public PreferenceService(PreferenceRepository preferences) {
		this.preferences = preferences;
	}

	@Transactional(readOnly = true)
	public Preferences load(String userId) {
		List<List<Preferences.Choice>> priorities = emptyPriorities();
		for (PreferenceEntity row : preferences.findByUserIdOrderByPriorityAscIdAsc(userId)) {
			if (row.getPriority() < 1 || row.getPriority() > Preferences.PRIORITY_COUNT) continue;
			priorities.get(row.getPriority() - 1).add(new Preferences.Choice(row.getMake(), row.getModel()));
		}
		return new Preferences(priorities);
	}

	/** Сохраняет целиком, заменяя прежние. Некорректный ввод — IllegalArgumentException с текстом для пользователя. */
	@Transactional
	public Preferences save(String userId, Preferences input) {
		Preferences clean = normalize(input);
		preferences.deleteByUserId(userId);
		List<PreferenceEntity> rows = new ArrayList<>();
		for (int index = 0; index < clean.priorities().size(); index++) {
			for (Preferences.Choice choice : clean.priorities().get(index)) {
				rows.add(new PreferenceEntity(userId, index + 1, choice.make(), choice.model()));
			}
		}
		preferences.saveAll(rows);
		return clean;
	}

	private static Preferences normalize(Preferences input) {
		List<List<Preferences.Choice>> raw = input == null || input.priorities() == null ? List.of() : input.priorities();
		if (raw.size() > Preferences.PRIORITY_COUNT) throw new IllegalArgumentException("Не больше трёх приоритетов");
		List<List<Preferences.Choice>> priorities = emptyPriorities();
		Set<String> taken = new HashSet<>();
		for (int index = 0; index < raw.size(); index++) {
			List<Preferences.Choice> choices = raw.get(index) == null ? List.of() : raw.get(index);
			for (Preferences.Choice choice : choices) {
				Preferences.Choice clean = cleanChoice(choice);
				if (!taken.add(clean.make().toLowerCase(Locale.ROOT) + "\n" + clean.model().toLowerCase(Locale.ROOT))) continue;
				priorities.get(index).add(clean);
			}
			if (priorities.get(index).size() > CHOICES_PER_PRIORITY) {
				throw new IllegalArgumentException("В приоритете не больше " + CHOICES_PER_PRIORITY + " вариантов");
			}
		}
		return new Preferences(priorities);
	}

	private static Preferences.Choice cleanChoice(Preferences.Choice choice) {
		String make = choice == null || choice.make() == null ? "" : choice.make().trim();
		String model = choice == null || choice.model() == null ? "" : choice.model().trim();
		if (make.isEmpty() || make.length() > 40) throw new IllegalArgumentException("Укажите марку");
		if (model.length() > 60) throw new IllegalArgumentException("Слишком длинная модель");
		return new Preferences.Choice(make, model);
	}

	private static List<List<Preferences.Choice>> emptyPriorities() {
		List<List<Preferences.Choice>> priorities = new ArrayList<>();
		for (int index = 0; index < Preferences.PRIORITY_COUNT; index++) priorities.add(new ArrayList<>());
		return priorities;
	}
}
