package pl.cartok.feed;

import java.time.Instant;
import java.util.List;

public record ListingView(
		String id,
		String title,
		String make,
		String model,
		int year,
		int price,
		String currency,
		int mileageKm,
		String fuel,
		String transmission,
		String location,
		String description,
		List<String> images,
		SellerView seller,
		Instant createdAt,
		boolean liked
) {

	public record SellerView(String id, String name, String kind) {
	}
}
