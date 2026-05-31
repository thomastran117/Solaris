package backend.services.intf.imports;

import java.util.UUID;
import backend.dtos.requests.imports.AttachImagesRequest;
import backend.dtos.requests.imports.CreateImportJobRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.imports.ImportDownloadResponse;
import backend.dtos.responses.imports.ImportJobResponse;
import backend.dtos.responses.imports.ImportJobRowResponse;

public interface ImportService {

    /**
     * Persists a new {@link backend.models.core.ImportJob}, publishes an internal
     * {@code ImportJobRequestedEvent} for the Kafka publisher to relay after commit,
     * and returns the freshly created job in {@code PENDING} state.
     */
    ImportJobResponse createJob(UUID companyId, UUID ownerId, CreateImportJobRequest request);

    /**
     * Executed by the Kafka consumer. Idempotent — short-circuits if the job is
     * already terminal. Streams the CSV from S3, validates, and applies all rows.
     */
    void processJob(UUID jobId);

    /**
     * Marks an import job as FAILED with the supplied reason. Used by the Kafka
     * consumer's catch block so worker-thread exceptions surface in the operator-
     * visible job list instead of being silently dropped.
     */
    void markJobFailed(UUID jobId, String reason);

    ImportJobResponse getJob(UUID companyId, UUID ownerId, UUID jobId);

    PagedResponse<ImportJobResponse> listJobs(UUID companyId, UUID ownerId, int page, int size);

    PagedResponse<ImportJobRowResponse> listErrors(UUID companyId, UUID ownerId, UUID jobId, int page, int size);

    /** Returns a presigned GET URL for the per-job error-report CSV. */
    ImportDownloadResponse getErrorReport(UUID companyId, UUID ownerId, UUID jobId);

    /**
     * Synchronous catalogue export — writes a CSV of all company products to S3
     * and returns a presigned GET URL. For large catalogues consider switching to
     * an async job (jobType = EXPORT).
     */
    ImportDownloadResponse exportCatalogue(UUID companyId, UUID ownerId);

    /** Bulk-attach already-uploaded image URLs to products by SKU. */
    int attachImages(UUID companyId, UUID ownerId, AttachImagesRequest request);
}
