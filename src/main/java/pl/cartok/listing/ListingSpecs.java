package pl.cartok.listing;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import pl.cartok.activity.InteractionEntity;
import pl.cartok.preference.Preferences;

final class ListingSpecs {

	private ListingSpecs() {
	}

	static Specification<ListingEntity> active() {
		return (root, query, cb) -> cb.equal(root.get("status"), "active");
	}

	static Specification<ListingEntity> idIn(Collection<String> ids) {
		return (root, query, cb) -> ids.isEmpty() ? cb.disjunction() : root.get("id").in(ids);
	}

	static Specification<ListingEntity> notIn(Collection<String> ids) {
		return (root, query, cb) -> ids.isEmpty() ? cb.conjunction() : cb.not(root.get("id").in(ids));
	}

	static Specification<ListingEntity> notSeenBy(String userId) {
		return (root, query, cb) -> {
			Subquery<Integer> seen = query.subquery(Integer.class);
			Root<InteractionEntity> interaction = seen.from(InteractionEntity.class);
			seen.select(cb.literal(1)).where(
					cb.equal(interaction.get("userId"), userId),
					cb.equal(interaction.get("listingId"), root.get("id")),
					cb.isNotNull(interaction.get("seenAt")));
			return cb.not(cb.exists(seen));
		};
	}

	static Specification<ListingEntity> anyOf(List<Preferences.Choice> choices) {
		return (root, query, cb) -> {
			List<Predicate> variants = new ArrayList<>();
			for (Preferences.Choice choice : choices) {
				Predicate make = cb.equal(cb.lower(root.get("make")), choice.make().toLowerCase(Locale.ROOT));
				variants.add(choice.model().isBlank()
						? make
						: cb.and(make, cb.equal(cb.lower(root.get("model")), choice.model().toLowerCase(Locale.ROOT))));
			}
			return variants.isEmpty() ? cb.disjunction() : cb.or(variants.toArray(Predicate[]::new));
		};
	}
}
