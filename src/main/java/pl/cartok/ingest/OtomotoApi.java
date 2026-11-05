package pl.cartok.ingest;

import java.net.URI;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(accept = "text/html")
public interface OtomotoApi {

	String NEWEST_FIRST = "created_at_first:desc";

	@GetExchange("/osobowe/{make}")
	String search(
			@PathVariable String make,
			@RequestParam int page,
			@RequestParam("search[order]") String order
	);

	@GetExchange
	String offer(URI url);
}
