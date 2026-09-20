package com.vietmart.travel.controller;

import com.vietmart.travel.dto.ComboBookingRequest;
import com.vietmart.travel.dto.ComboBookingResponse;
import com.vietmart.travel.enums.TestScenario;
import com.vietmart.travel.orchestrator.TravelComboSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/travel/combo")
@RequiredArgsConstructor
@Slf4j
public class TravelComboController {

    private final TravelComboSagaOrchestrator sagaOrchestrator;

    @PostMapping("/book")
    public ResponseEntity<ComboBookingResponse> bookTravelCombo(
            @RequestBody(required = false) ComboBookingRequest request,
            @RequestParam(required = false) TestScenario scenario) {

        if (request == null) {
            request = ComboBookingRequest.builder()
                    .customerId("CUST-VN-8899")
                    .customerName("Nguyen Van A")
                    .flightCode("VN218-HAN-SGN")
                    .hotelCode("VINPEARL-RESORT-01")
                    .totalAmount(new BigDecimal("6500000.00"))
                    .paymentMethod("VNPAY_QR")
                    .build();
        }

        if (scenario != null) {
            request.setTestScenario(scenario);
        }

        log.info("[REST API] Received travel combo booking request for customer: {}, scenario: {}",
                request.getCustomerId(), request.getTestScenario());

        ComboBookingResponse response = sagaOrchestrator.executeComboBookingSaga(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/scenarios")
    public ResponseEntity<List<Map<String, String>>> listTestScenarios() {
        return ResponseEntity.ok(List.of(
                Map.of(
                        "scenario", "SUCCESS",
                        "description", "Kịch bản 1: Đặt vé máy bay OK -> Đặt khách sạn OK -> Thanh toán OK -> Hoàn tất giao dịch."
                ),
                Map.of(
                        "scenario", "HOTEL_FAILED",
                        "description", "Kịch bản 2: Đặt vé máy bay OK -> Đặt khách sạn THẤT BẠI -> Tự động Rollback/Hủy vé máy bay đã đặt."
                ),
                Map.of(
                        "scenario", "PAYMENT_FAILED",
                        "description", "Kịch bản 3: Đặt vé máy bay OK -> Đặt khách sạn OK -> Thanh toán THẤT BẠI -> Tự động Rollback cả khách sạn và vé máy bay."
                ),
                Map.of(
                        "scenario", "HOTEL_TIMEOUT",
                        "description", "Kịch bản 4: Đặt vé máy bay OK -> Gọi khách sạn bị TIMEOUT (>3000ms) -> Ngắt và kích hoạt Rollback vé máy bay."
                )
        ));
    }
}
