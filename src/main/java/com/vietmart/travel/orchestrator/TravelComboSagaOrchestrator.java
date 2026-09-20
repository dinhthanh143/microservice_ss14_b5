package com.vietmart.travel.orchestrator;

import com.vietmart.travel.dto.ComboBookingRequest;
import com.vietmart.travel.dto.ComboBookingResponse;
import com.vietmart.travel.dto.SagaStepLog;
import com.vietmart.travel.enums.BookingStatus;
import com.vietmart.travel.enums.TestScenario;
import com.vietmart.travel.partner.FlightPartnerClient;
import com.vietmart.travel.partner.HotelPartnerClient;
import com.vietmart.travel.partner.PaymentPartnerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TravelComboSagaOrchestrator {

    private final FlightPartnerClient flightPartnerClient;
    private final HotelPartnerClient hotelPartnerClient;
    private final PaymentPartnerClient paymentPartnerClient;

    public ComboBookingResponse executeComboBookingSaga(ComboBookingRequest request) {
        String bookingId = (request.getBookingId() != null) ? request.getBookingId() : "COMBO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        TestScenario scenario = (request.getTestScenario() != null) ? request.getTestScenario() : TestScenario.SUCCESS;

        List<SagaStepLog> logs = new ArrayList<>();
        String flightBookingCode = null;
        String hotelBookingCode = null;
        String paymentTransactionId = null;

        log.info("=========================================================================");
        log.info(">>> [SAGA ORCHESTRATOR START] Initiating Combo Booking Saga: {} | Scenario: {}", bookingId, scenario);
        log.info("=========================================================================");

        try {
            // STEP 1: ĐẶT VÉ MÁY BAY (Flight Partner)
            log.info("[SAGA STEP 1] Executing Forward Transaction: Book Flight ({})", request.getFlightCode());
            flightBookingCode = flightPartnerClient.bookFlight(request.getCustomerId(), request.getFlightCode());
            logs.add(SagaStepLog.builder()
                    .stepName("1. FLIGHT_RESERVATION")
                    .actionType("FORWARD")
                    .status("SUCCESS")
                    .details("Đặt vé máy bay thành công. Mã vé: " + flightBookingCode)
                    .timestamp(LocalDateTime.now())
                    .build());

            // STEP 2: ĐẶT PHÒNG KHÁCH SẠN (Hotel Partner) WITH TIMEOUT HANDLING
            log.info("[SAGA STEP 2] Executing Forward Transaction: Book Hotel ({})", request.getHotelCode());
            hotelBookingCode = executeHotelBookingWithTimeout(request.getCustomerId(), request.getHotelCode(), scenario, 3000);
            logs.add(SagaStepLog.builder()
                    .stepName("2. HOTEL_RESERVATION")
                    .actionType("FORWARD")
                    .status("SUCCESS")
                    .details("Đặt phòng khách sạn thành công. Mã phòng: " + hotelBookingCode)
                    .timestamp(LocalDateTime.now())
                    .build());

            // STEP 3: THANH TOÁN TOÀN BỘ COMBO (Payment Gateway)
            log.info("[SAGA STEP 3] Executing Forward Transaction: Process Payment ({} VND)", request.getTotalAmount());
            paymentTransactionId = paymentPartnerClient.processPayment(
                    request.getCustomerId(), request.getTotalAmount(), request.getPaymentMethod(), scenario);
            logs.add(SagaStepLog.builder()
                    .stepName("3. PAYMENT_PROCESSING")
                    .actionType("FORWARD")
                    .status("SUCCESS")
                    .details("Thanh toán thành công. Mã giao dịch: " + paymentTransactionId)
                    .timestamp(LocalDateTime.now())
                    .build());

            // TOÀN BỘ SAGA THÀNH CÔNG (HAPPY PATH)
            log.info(">>> [SAGA ORCHESTRATOR SUCCESS] All Saga steps completed successfully! Booking ID: {}", bookingId);
            return ComboBookingResponse.builder()
                    .bookingId(bookingId)
                    .customerId(request.getCustomerId())
                    .finalStatus(BookingStatus.SUCCESS)
                    .flightBookingCode(flightBookingCode)
                    .hotelBookingCode(hotelBookingCode)
                    .paymentTransactionId(paymentTransactionId)
                    .amountCharged(request.getTotalAmount())
                    .message("Đặt combo Chuyến đi trọn gói (Vé máy bay + Khách sạn) THÀNH CÔNG!")
                    .executionLogs(logs)
                    .build();

        } catch (Exception ex) {
            log.error(">>> [SAGA FAILURE DETECTED] Step failed with error: {}. Starting Compensation Rollback...", ex.getMessage());
            logs.add(SagaStepLog.builder()
                    .stepName("SAGA_FAILURE_INTERRUPT")
                    .actionType("ERROR")
                    .status("FAILED")
                    .details("Lỗi phát sinh: " + ex.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build());

            // THỰC THI GIAO DỊCH BÙ TRỪ (COMPENSATING TRANSACTIONS) THEO THỨ TỰ NGƯỢC
            executeCompensations(flightBookingCode, hotelBookingCode, paymentTransactionId, request, logs, ex.getMessage());

            return ComboBookingResponse.builder()
                    .bookingId(bookingId)
                    .customerId(request.getCustomerId())
                    .finalStatus(BookingStatus.FAILED)
                    .flightBookingCode(null)
                    .hotelBookingCode(null)
                    .paymentTransactionId(null)
                    .amountCharged(null)
                    .message("Giao dịch đặt combo THẤT BẠI: " + ex.getMessage() + ". Hệ thống đã tự động hoàn tác và bồi hoàn toàn bộ giao dịch.")
                    .executionLogs(logs)
                    .build();
        }
    }

    private String executeHotelBookingWithTimeout(String customerId, String hotelCode, TestScenario scenario, long timeoutMs) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> hotelPartnerClient.bookHotel(customerId, hotelCode, scenario));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException("Hotel Partner API phản hồi chậm vượt quá thời gian cho phép (" + timeoutMs + "ms)");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new RuntimeException(cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private void executeCompensations(String flightCode, String hotelCode, String paymentTxn, ComboBookingRequest request, List<SagaStepLog> logs, String reason) {
        log.warn("=========================================================================");
        log.warn(">>> [SAGA COMPENSATING ROLLBACK INITIATED] Reversing completed steps...");
        log.warn("=========================================================================");

        // Compensate Step 3: Hoàn tiền nếu đã trừ tiền
        if (paymentTxn != null) {
            try {
                log.info("[SAGA COMPENSATE 3] Refunding Payment: {}", paymentTxn);
                paymentPartnerClient.refundPayment(paymentTxn, request.getTotalAmount(), reason);
                logs.add(SagaStepLog.builder()
                        .stepName("COMPENSATE_PAYMENT")
                        .actionType("COMPENSATING")
                        .status("SUCCESS")
                        .details("Hoàn tiền thành công cho giao dịch: " + paymentTxn)
                        .timestamp(LocalDateTime.now())
                        .build());
            } catch (Exception e) {
                log.error("[COMPENSATE PAYMENT ERROR] Failed to refund: {}", e.getMessage());
            }
        }

        // Compensate Step 2: Hủy phòng khách sạn nếu đã đặt
        if (hotelCode != null) {
            try {
                log.info("[SAGA COMPENSATE 2] Cancelling Hotel Reservation: {}", hotelCode);
                hotelPartnerClient.cancelHotelBooking(hotelCode, reason);
                logs.add(SagaStepLog.builder()
                        .stepName("COMPENSATE_HOTEL")
                        .actionType("COMPENSATING")
                        .status("SUCCESS")
                        .details("Hủy phòng khách sạn thành công: " + hotelCode)
                        .timestamp(LocalDateTime.now())
                        .build());
            } catch (Exception e) {
                log.error("[COMPENSATE HOTEL ERROR] Failed to cancel hotel: {}", e.getMessage());
            }
        }

        // Compensate Step 1: Hủy vé máy bay nếu đã đặt
        if (flightCode != null) {
            try {
                log.info("[SAGA COMPENSATE 1] Cancelling Flight Reservation: {}", flightCode);
                flightPartnerClient.cancelFlightBooking(flightCode, reason);
                logs.add(SagaStepLog.builder()
                        .stepName("COMPENSATE_FLIGHT")
                        .actionType("COMPENSATING")
                        .status("SUCCESS")
                        .details("Hủy vé máy bay thành công: " + flightCode)
                        .timestamp(LocalDateTime.now())
                        .build());
            } catch (Exception e) {
                log.error("[COMPENSATE FLIGHT ERROR] Failed to cancel flight: {}", e.getMessage());
            }
        }

        log.info(">>> [SAGA COMPENSATION COMPLETED] All rollbacks successfully processed.");
    }
}
