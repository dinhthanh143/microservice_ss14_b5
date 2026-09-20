package com.vietmart.travel;

import com.vietmart.travel.dto.ComboBookingRequest;
import com.vietmart.travel.dto.ComboBookingResponse;
import com.vietmart.travel.enums.BookingStatus;
import com.vietmart.travel.enums.TestScenario;
import com.vietmart.travel.orchestrator.TravelComboSagaOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TravelComboSagaOrchestratorTest {

    @Autowired
    private TravelComboSagaOrchestrator orchestrator;

    private ComboBookingRequest createBaseRequest(TestScenario scenario) {
        return ComboBookingRequest.builder()
                .bookingId("TEST-BK-" + System.currentTimeMillis())
                .customerId("CUST-VN-001")
                .customerName("Tran Van B")
                .flightCode("VN123-HAN-DAD")
                .hotelCode("MƯONGTHANH-DANANG-01")
                .totalAmount(new BigDecimal("5200000.00"))
                .paymentMethod("CREDIT_CARD")
                .testScenario(scenario)
                .build();
    }

    @Test
    @DisplayName("Kịch bản 1: Đặt combo thành công cả 3 bước (Flight OK -> Hotel OK -> Payment OK)")
    void testScenario1_SuccessBoth() {
        ComboBookingRequest request = createBaseRequest(TestScenario.SUCCESS);
        ComboBookingResponse response = orchestrator.executeComboBookingSaga(request);

        assertThat(response.getFinalStatus()).isEqualTo(BookingStatus.SUCCESS);
        assertThat(response.getFlightBookingCode()).isNotNull().startsWith("VN-AIR-");
        assertThat(response.getHotelBookingCode()).isNotNull().startsWith("HTL-RES-");
        assertThat(response.getPaymentTransactionId()).isNotNull().startsWith("PAY-TXN-");
        assertThat(response.getExecutionLogs()).hasSize(3);
    }

    @Test
    @DisplayName("Kịch bản 2: Flight thành công, Hotel thất bại -> Rollback hủy Flight")
    void testScenario2_HotelFailed_RollbackFlight() {
        ComboBookingRequest request = createBaseRequest(TestScenario.HOTEL_FAILED);
        ComboBookingResponse response = orchestrator.executeComboBookingSaga(request);

        assertThat(response.getFinalStatus()).isEqualTo(BookingStatus.FAILED);
        assertThat(response.getFlightBookingCode()).isNull();
        assertThat(response.getHotelBookingCode()).isNull();
        assertThat(response.getPaymentTransactionId()).isNull();

        // Kiểm tra log có chứa bước bù trừ COMPENSATE_FLIGHT
        boolean hasCompensateFlight = response.getExecutionLogs().stream()
                .anyMatch(log -> "COMPENSATE_FLIGHT".equals(log.getStepName()) && "SUCCESS".equals(log.getStatus()));
        assertThat(hasCompensateFlight).isTrue();
    }

    @Test
    @DisplayName("Kịch bản 3: Flight OK, Hotel OK, Payment thất bại -> Rollback cả Hotel và Flight")
    void testScenario3_PaymentFailed_RollbackBoth() {
        ComboBookingRequest request = createBaseRequest(TestScenario.PAYMENT_FAILED);
        ComboBookingResponse response = orchestrator.executeComboBookingSaga(request);

        assertThat(response.getFinalStatus()).isEqualTo(BookingStatus.FAILED);

        // Kiểm tra log có chứa cả 2 bước bù trừ: COMPENSATE_HOTEL và COMPENSATE_FLIGHT
        boolean hasCompensateHotel = response.getExecutionLogs().stream()
                .anyMatch(log -> "COMPENSATE_HOTEL".equals(log.getStepName()) && "SUCCESS".equals(log.getStatus()));
        boolean hasCompensateFlight = response.getExecutionLogs().stream()
                .anyMatch(log -> "COMPENSATE_FLIGHT".equals(log.getStepName()) && "SUCCESS".equals(log.getStatus()));

        assertThat(hasCompensateHotel).isTrue();
        assertThat(hasCompensateFlight).isTrue();
    }

    @Test
    @DisplayName("Kịch bản 4: Timeout khi gọi Hotel (>3000ms) -> Ngắt và Rollback hủy Flight")
    void testScenario4_HotelTimeout_RollbackFlight() {
        ComboBookingRequest request = createBaseRequest(TestScenario.HOTEL_TIMEOUT);
        ComboBookingResponse response = orchestrator.executeComboBookingSaga(request);

        assertThat(response.getFinalStatus()).isEqualTo(BookingStatus.FAILED);

        boolean hasCompensateFlight = response.getExecutionLogs().stream()
                .anyMatch(log -> "COMPENSATE_FLIGHT".equals(log.getStepName()) && "SUCCESS".equals(log.getStatus()));
        assertThat(hasCompensateFlight).isTrue();
    }
}
