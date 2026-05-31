package backend.repositories;

import backend.models.core.IndexVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndexVersionRepository extends JpaRepository<IndexVersion, String> {}
