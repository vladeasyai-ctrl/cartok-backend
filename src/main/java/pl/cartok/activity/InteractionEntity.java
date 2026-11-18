package pl.cartok.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * История одного пользователя с одним объявлением. Пишется только через {@link ActivityService}
 * (upsert на SQL), здесь — для чтения и подзапросов ленты.
 */
@Entity
@Table(name = "listing_interaction")
@IdClass(InteractionEntity.Key.class)
public class InteractionEntity {

	@Id
	@Column(name = "user_id")
	private String userId;

	@Id
	@Column(name = "listing_id")
	private String listingId;

	@Column(name = "first_event_at", nullable = false)
	private OffsetDateTime firstEventAt;

	@Column(name = "last_event_at", nullable = false)
	private OffsetDateTime lastEventAt;

	@Column(name = "seen_at")
	private OffsetDateTime seenAt;

	@Column(nullable = false)
	private int views;

	@Column(name = "dwell_ms", nullable = false)
	private long dwellMs;

	@Column(nullable = false)
	private int opens;

	@Column(name = "liked_at")
	private OffsetDateTime likedAt;

	protected InteractionEntity() {
	}

	public String getUserId() {
		return userId;
	}

	public String getListingId() {
		return listingId;
	}

	public OffsetDateTime getFirstEventAt() {
		return firstEventAt;
	}

	public OffsetDateTime getLastEventAt() {
		return lastEventAt;
	}

	public OffsetDateTime getSeenAt() {
		return seenAt;
	}

	public int getViews() {
		return views;
	}

	public long getDwellMs() {
		return dwellMs;
	}

	public int getOpens() {
		return opens;
	}

	public OffsetDateTime getLikedAt() {
		return likedAt;
	}

	public static class Key implements Serializable {

		private String userId;
		private String listingId;

		public Key() {
		}

		public Key(String userId, String listingId) {
			this.userId = userId;
			this.listingId = listingId;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Key key && Objects.equals(userId, key.userId) && Objects.equals(listingId, key.listingId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(userId, listingId);
		}
	}
}
