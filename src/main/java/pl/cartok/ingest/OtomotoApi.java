package pl.cartok.ingest;

import java.net.URI;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** Вызывать только из {@link IngestService}: частоту обращений к otomoto держит {@link OtomotoGate}. */
@HttpExchange(accept = "text/html")
public interface OtomotoApi {

	@GetExchange
	String page(URI url);
}
