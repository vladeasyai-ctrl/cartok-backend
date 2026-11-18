package pl.cartok.ingest;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScrapeQueryRepository extends JpaRepository<ScrapeQueryState, String> {
}
