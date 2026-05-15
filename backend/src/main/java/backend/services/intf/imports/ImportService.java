package backend.services.intf.imports;

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
    ImportJobResponse createJob(long companyId, long ownerId, CreateImportJobRequest request);

    /**
     * Executed by the Kafka consumer. Idempotent — short-circuits if the job is
     * already terminal. Streams the CSV from S3, validates, and applies all rows.
     */
    void processJob(long jobId);

    /**
     * Marks an import job as FAILED with the supplied reason. Used by the Kafka
     * consumer's catch block so worker-thread exceptions surface in the operator-
     * visible job list instead of being silently dropped.
     */
    void markJobFailed(long jobId, String reason);

    ImportJobResponse getJob(long companyId, long ownerId, long jobId);

    PagedResponse<ImportJobResponse> listJobs(long companyId, long ownerId, int page, int size);

    PagedResponse<ImportJobRowResponse> listErrors(long companyId, long ownerId, long jobId, int page, int size);

    /** Returns a presigned GET URL for the per-job error-report CSV. */
    ImportDownloadResponse getErrorReport(long companyId, long ownerId, long jobId);

    /**
     * Synchronous catalogue export — writes a CSV of all company products to S3
     * and returns a presigned GET URL. For large catalogues consider switching to
     * an async job (jobType = EXPORT).
     */
    ImportDownloadResponse exportCatalogue(long companyId, long ownerId);

    /** Bulk-attach already-uploaded image URLs to products by SKU. */
    int attachImages(long companyId, long ownerId, AttachImagesRequest request);
}
