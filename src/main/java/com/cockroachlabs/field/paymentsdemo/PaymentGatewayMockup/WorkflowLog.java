package com.cockroachlabs.field.paymentsdemo.PaymentGatewayMockup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID logId;

    WorkflowLog() {}

    WorkflowLog(UUID transactionId, String eventDescription) {
        this.transactionId = transactionId;
        this.eventDescription = eventDescription;
        this.logDate = Instant.now();
    }

    private UUID transactionId;

    private Instant logDate;

    private String eventDescription;

    public UUID getLogId() {
        return this.logId;
    }
    public UUID getTransactionId() {
        return this.transactionId;
    }
    public Instant getLogDate() {
        return this.logDate;
    }
    public String getEventDescription() {
        return this.eventDescription;
    }

    public void setLogId(UUID logId) {
        this.logId = logId;
    }
    public void setTransactionId(UUID transactionId) {
        this.transactionId = transactionId;
    }
    public void setLogDate(Instant logDate) {
        this.logDate = logDate;
    }
    public void setEventDescription(String eventDescription) {
        this.eventDescription = eventDescription;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o)
            return true;
        if (!(o instanceof WorkflowLog))
            return false;
        WorkflowLog workflowLog = (WorkflowLog) o;
        return Objects.equals(this.logId, workflowLog.logId)
                && Objects.equals(this.transactionId, workflowLog.transactionId)
                && Objects.equals(this.logDate, workflowLog.logDate)
                && Objects.equals(this.eventDescription, workflowLog.eventDescription);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.logId,
                this.transactionId,
                this.logDate,
                this.eventDescription);
    }

    @Override
    public String toString() {
        return "WorkflowLog {"
                + "Log ID = " + this.logId
                + ", Transaction ID = " + this.transactionId
                + ", Log Date = " + this.logDate
                + ", Event Description = '" + this.eventDescription + '\''
                + '}';
    }

}
