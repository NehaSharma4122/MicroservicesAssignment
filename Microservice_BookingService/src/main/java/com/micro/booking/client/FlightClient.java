package com.micro.booking.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.micro.booking.requests.FlightRequest;

@FeignClient(name = "flight-service", url = "${flight.service.url}")
public interface FlightClient {

    @GetMapping("/flights/{id}")
    FlightRequest getFlightById(@PathVariable String id);

    @PutMapping("/flights/{id}/seats")
    void updateAvailableSeats(
            @PathVariable String id,
            @RequestParam int seats
    );
}
