package com.cockroachlabs.field.paymentsdemo.PaymentGatewayMockup;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WorkflowLogRepository extends JpaRepository<WorkflowLog, UUID> {
}
