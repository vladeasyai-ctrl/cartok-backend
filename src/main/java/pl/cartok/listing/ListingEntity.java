package pl.cartok.listing;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import org.hibernate.annotations.BatchSize;
import java.util.List;

@Entity
@Table(
		name = "listing",
		uniqueConstraints = @UniqueConstraint(name = "listing_source_external_id_key", columnNames = {"source", "external_id"})
)
public class ListingEntity {

	@Id
	private String id;

	@Column(nullable = false)
	private String source;

	@Column(name = "external_id", nullable = false)
	private String externalId;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String make = "";

	@Column(nullable = false)
	private String model = "";

	private Integer year;

	@Column(name = "price_amount")
	private Integer priceAmount;

	@Column(nullable = false)
	private String currency = "PLN";

	@Column(name = "mileage_km")
	private Integer mileageKm;

	@Column(nullable = false)
	private String fuel = "";

	@Column(nullable = false)
	private String transmission = "";

	@Column(nullable = false)
	private String location = "";

	@Column(nullable = false)
	private String description = "";

	@Column(name = "seller_id", nullable = false)
	private String sellerId = "";

	@Column(name = "seller_name", nullable = false)
	private String sellerName = "";

	@Column(name = "seller_kind", nullable = false)
	private String sellerKind = "private";

	@Column(name = "search_slug", nullable = false)
	private String searchSlug = "";

	@Column(name = "make_slug", nullable = false)
	private String makeSlug = "";

	@Column(name = "model_slug", nullable = false)
	private String modelSlug = "";

	@Column(name = "source_url", nullable = false)
	private String sourceUrl;

	@Column(nullable = false)
	private String status = "active";

	@Column(name = "published_at")
	private OffsetDateTime publishedAt;

	@Column(name = "last_seen_at", nullable = false)
	private OffsetDateTime lastSeenAt;

	/** Страница объявления уже снята. Карточка только из поиска ещё ждёт подробностей. */
	@Column(nullable = false)
	private boolean detailed = true;

	@OneToMany(mappedBy = "listing", cascade = CascadeType.ALL, orphanRemoval = true)
	@BatchSize(size = 50)
	private List<ListingPhotoEntity> photos = new ArrayList<>();

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getExternalId() {
		return externalId;
	}

	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getMake() {
		return make;
	}

	public void setMake(String make) {
		this.make = make;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Integer getYear() {
		return year;
	}

	public void setYear(Integer year) {
		this.year = year;
	}

	public Integer getPriceAmount() {
		return priceAmount;
	}

	public void setPriceAmount(Integer priceAmount) {
		this.priceAmount = priceAmount;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public Integer getMileageKm() {
		return mileageKm;
	}

	public void setMileageKm(Integer mileageKm) {
		this.mileageKm = mileageKm;
	}

	public String getFuel() {
		return fuel;
	}

	public void setFuel(String fuel) {
		this.fuel = fuel;
	}

	public String getTransmission() {
		return transmission;
	}

	public void setTransmission(String transmission) {
		this.transmission = transmission;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getSellerId() {
		return sellerId;
	}

	public void setSellerId(String sellerId) {
		this.sellerId = sellerId;
	}

	public String getSellerName() {
		return sellerName;
	}

	public void setSellerName(String sellerName) {
		this.sellerName = sellerName;
	}

	public String getSellerKind() {
		return sellerKind;
	}

	public void setSellerKind(String sellerKind) {
		this.sellerKind = sellerKind;
	}

	public String getSearchSlug() {
		return searchSlug;
	}

	public void setSearchSlug(String searchSlug) {
		this.searchSlug = searchSlug;
	}

	public String getMakeSlug() {
		return makeSlug;
	}

	public void setMakeSlug(String makeSlug) {
		this.makeSlug = makeSlug;
	}

	public String getModelSlug() {
		return modelSlug;
	}

	public void setModelSlug(String modelSlug) {
		this.modelSlug = modelSlug;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public void setSourceUrl(String sourceUrl) {
		this.sourceUrl = sourceUrl;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public OffsetDateTime getPublishedAt() {
		return publishedAt;
	}

	public void setPublishedAt(OffsetDateTime publishedAt) {
		this.publishedAt = publishedAt;
	}

	public OffsetDateTime getLastSeenAt() {
		return lastSeenAt;
	}

	public void setLastSeenAt(OffsetDateTime lastSeenAt) {
		this.lastSeenAt = lastSeenAt;
	}

	public boolean isDetailed() {
		return detailed;
	}

	public void setDetailed(boolean detailed) {
		this.detailed = detailed;
	}

	public List<ListingPhotoEntity> getPhotos() {
		return photos;
	}
}
