package backend.services.intf.marketing;

import backend.models.enums.WorkflowTrigger;

import java.util.UUID;

public interface WorkflowEnrollmentService {

    void enrol(WorkflowTrigger trigger, UUID companyId, UUID userId);

    void processScheduledEnrollments();

    void processOneEnrollment(UUID enrollmentId);
}
