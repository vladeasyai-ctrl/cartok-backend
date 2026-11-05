package pl.cartok.listing;

import java.time.OffsetDateTime;

public record SlugActivity(String slug, OffsetDateTime lastSeenAt) {
}
