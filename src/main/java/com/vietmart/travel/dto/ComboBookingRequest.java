package com.vietmart.travel.dto;

import com.vietmart.travel.enums.TestScenario;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComboBookingRequest {
    private String bookingId;
    private String customerId;
    private String customerName;
    private String flightCode;
    private String hotelCode;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private TestScenario testScenario;
}
