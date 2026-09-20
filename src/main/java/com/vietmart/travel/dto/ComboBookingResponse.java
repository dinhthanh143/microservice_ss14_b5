package com.vietmart.travel.dto;

import com.vietmart.travel.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComboBookingResponse {
    private String bookingId;
    private String customerId;
    private BookingStatus finalStatus;
    private String flightBookingCode;
    private String hotelBookingCode;
    private String paymentTransactionId;
    private BigDecimal amountCharged;
    private String message;
    private List<SagaStepLog> executionLogs;
}
