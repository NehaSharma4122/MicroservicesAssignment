package com.micro.booking.client;

import com.micro.booking.exception.ResourceNotFoundException;
import com.micro.booking.requests.FlightRequest;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FlightFallbackFactory implements FallbackFactory<FlightClient> {

    @Override
    public FlightClient create(Throwable cause) {
        return new FlightClient() {
            @Override
            public FlightRequest getFlightById(String flightId) {
                log.error("Error calling Flight Service for ID {}: {}", flightId, cause.getMessage());
                // Instead of returning a null/empty object (which causes "scanAvailable: true" issues)
                // we throw an exception so the user knows the service is unavailable.
                throw new RuntimeException("Flight Service is currently unavailable. Please try again later.");
            }

            @Override
            public void updateAvailableSeats(String flightId, int seats) {
                log.error("Error updating seats for ID {}: {}", flightId, cause.getMessage());
                throw new RuntimeException("Unable to update seats at this time.");
            }
        };
    }
}