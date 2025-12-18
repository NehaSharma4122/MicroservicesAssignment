package com.micro.booking.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.micro.booking.client.FlightClient;
import com.micro.booking.entity.Passenger;
import com.micro.booking.entity.Ticket;
import com.micro.booking.exception.ResourceNotFoundException;
import com.micro.booking.exception.UnprocessableException;
import com.micro.booking.repository.TicketRepository;
import com.micro.booking.requests.BookingRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class BookingServiceImpl implements BookingService {

	@Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private FlightClient flightClient;

    @Override
    public Mono<Ticket> bookFlight(String flightId, BookingRequest bookingRequest) {

        return Mono.fromCallable(() -> flightClient.getFlightById(flightId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(flight -> {

                    int requestedSeats = bookingRequest.getTotalSeats();

                    if (flight.getAvailableSeats() < requestedSeats) {
                        return Mono.error(
                                new UnprocessableException("Not enough seats available"));
                    }

                    Ticket ticket = new Ticket();
                    ticket.setPnr(generatePNR());
                    ticket.setFlightId(flightId);
                    ticket.setCustomerName(bookingRequest.getCustomerName());
                    ticket.setCustomerEmail(bookingRequest.getCustomerEmail());
                    ticket.setNumSeats(requestedSeats);
                    ticket.setMealpref(bookingRequest.getMealpref());
                    ticket.setSeatNumber(bookingRequest.getSeatNumber());
                    ticket.setBookingDate(LocalDateTime.now());
                    ticket.setStatus("CONFIRMED");

                    if (bookingRequest.getPassenger() != null) {
                        ticket.setPassenger(
                                bookingRequest.getPassenger()
                                        .stream()
                                        .map(this::convertToPassenger)
                                        .toList()
                        );
                    }

                    return Mono.fromCallable(() -> {
                                flightClient.updateAvailableSeats(
                                        flightId,
                                        flight.getAvailableSeats() - requestedSeats
                                );
                                return ticket;
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(ticketRepository::save);
                });
    }

    @Override
    public Mono<Ticket> getTicketByPnr(String pnr) {
        return ticketRepository.findByPnr(pnr)
                .switchIfEmpty(Mono.error(
                        new ResourceNotFoundException("Ticket not found with PNR: " + pnr)));
    }

    @Override
    public Flux<Ticket> getBookingHistory(String email) {
        return ticketRepository.findByCustomerEmail(email);
    }

    @Override
    public Mono<String> cancelTicket(String pnr) {

        return ticketRepository.findByPnr(pnr)
                .switchIfEmpty(Mono.error(
                        new ResourceNotFoundException("Ticket not found with PNR: " + pnr)))
                .flatMap(ticket ->
                        Mono.fromCallable(() ->
                                flightClient.getFlightById(ticket.getFlightId()))
                                .flatMap(flight -> {

                                    if (flight == null) {
                                        return Mono.error(
                                                new ResourceNotFoundException("Flight not found"));
                                    }

                                    if (LocalDateTime.now().plusHours(24)
                                            .isAfter(flight.getDeparture())) {
                                        return Mono.error(new UnprocessableException(
                                                "Cancellation not allowed within 24 hours of departure"));
                                    }

                                    ticket.setStatus("CANCELLED");

                                    return Mono.fromRunnable(() ->
                                                    flightClient.updateAvailableSeats(
                                                            flight.getId(),
                                                            flight.getAvailableSeats()
                                                                    + ticket.getNumSeats()
                                                    ))
                                            .then(ticketRepository.save(ticket))
                                            .thenReturn("Ticket cancelled successfully");
                                })
                );
    }


    private String generatePNR() {
        return UUID.randomUUID().toString()
                .substring(0, 8)
                .toUpperCase();
    }

    private Passenger convertToPassenger(Passenger req) {
        Passenger p = new Passenger();
        p.setName(req.getName());
        p.setGender(req.getGender());
        p.setAge(req.getAge());
        return p;
    }
}