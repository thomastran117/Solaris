package backend.services.impl.marketing;

import backend.models.core.MarketingWorkflow;
import backend.services.intf.marketing.WorkflowEnrollmentService;

import java.time.Instant;
import java.util.UUID;

// Package-private: keeps internal scheduler fan-out methods off the public
// WorkflowEnrollmentService interface while ensuring Spring AOP proxies intercept them
// correctly under both CGLIB and JDK dynamic proxy modes.
interface WorkflowSchedulerPort extends WorkflowEnrollmentService {

    void processOneEnrollment(UUID enrollmentId);

    void scheduleEnrollmentInNewTx(MarketingWorkflow workflow, UUID userId, Instant fireAt);

    void incrementRetryOrMarkFailed(UUID enrollmentId);
}
