package pl.cartok.preference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_preference")
public class PreferenceEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(nullable = false)
	private int priority;

	@Column(nullable = false)
	private String make;

	@Column(nullable = false)
	private String model = "";

	protected PreferenceEntity() {
	}

	PreferenceEntity(String userId, int priority, String make, String model) {
		this.userId = userId;
		this.priority = priority;
		this.make = make;
		this.model = model;
	}

	public int getPriority() {
		return priority;
	}

	public String getMake() {
		return make;
	}

	public String getModel() {
		return model;
	}
}
