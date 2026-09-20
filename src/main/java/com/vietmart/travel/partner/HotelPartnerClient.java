package com.vietmart.travel.partner;

import com.vietmart.travel.enums.TestScenario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Component
@Slf4j
public class HotelPartnerClient {

    public String bookHotel(String customerId, String hotelCode, TestScenario scenario) throws TimeoutException {
        log.info("[HOTEL PARTNER] Requesting room reservation for customer: {}, hotel: {}, scenario: {}",
                customerId, hotelCode, scenario);

        if (scenario == TestScenario.HOTEL_TIMEOUT) {
            log.warn("[HOTEL PARTNER TIMEOUT] Hotel Partner API is hanging / latency exceeds threshold (>3000ms)...");
            try {
                Thread.sleep(3500);
            } catch (InterruptedException ignored) {}
            throw new TimeoutException("Hotel Partner API timed out after 3000ms without response");
        }

        if (scenario == TestScenario.HOTEL_FAILED) {
            log.error("[HOTEL PARTNER ERROR] Hotel rooms sold out or booking rejected by Hotel Partner!");
            throw new IllegalStateException("Hết phòng khách sạn (Hotel Room Sold Out / Booking Rejected)");
        }

        String reservationCode = "HTL-RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[HOTEL PARTNER SUCCESS] Confirmed hotel reservation: {}", reservationCode);
        return reservationCode;
    }

    public boolean cancelHotelBooking(String hotelBookingCode, String reason) {
        log.warn("[HOTEL PARTNER COMPENSATE] Cancelling hotel reservation: {} due to: {}", hotelBookingCode, reason);
        log.info("[HOTEL PARTNER COMPENSATE SUCCESS] Hotel reservation {} has been released.", hotelBookingCode);
        return true;
    }
}
