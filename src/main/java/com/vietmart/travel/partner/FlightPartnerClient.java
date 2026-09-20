package com.vietmart.travel.partner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class FlightPartnerClient {

    public String bookFlight(String customerId, String flightCode) {
        log.info("[FLIGHT PARTNER] Requesting flight reservation for customer: {}, flight: {}", customerId, flightCode);
        String ticketNumber = "VN-AIR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[FLIGHT PARTNER SUCCESS] Confirmed flight ticket: {}", ticketNumber);
        return ticketNumber;
    }

    public boolean cancelFlightBooking(String flightBookingCode, String reason) {
        log.warn("[FLIGHT PARTNER COMPENSATE] Cancelling flight ticket: {} due to: {}", flightBookingCode, reason);
        log.info("[FLIGHT PARTNER COMPENSATE SUCCESS] Flight ticket {} has been successfully voided/cancelled.", flightBookingCode);
        return true;
    }
}
