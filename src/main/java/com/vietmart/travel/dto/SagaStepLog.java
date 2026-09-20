package com.vietmart.travel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaStepLog {
    private String stepName;
    private String actionType; // FORWARD, COMPENSATING
    private String status;     // SUCCESS, FAILED, TIMEOUT
    private String details;
    private LocalDateTime timestamp;
}
