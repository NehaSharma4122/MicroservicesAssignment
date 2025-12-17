package com.micro.booking.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
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

@Service
public class BookingServiceImpl implements BookingService {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private FlightClient flightClient;

    @Override
    public Mono<Ticket> bookFlight(String flightId, BookingRequest bookingRequest) {

        return Mono.fromCallable(() -> flightClient.getFlightById(flightId))
                .switchIfEmpty(Mono.error(
                        new ResourceNotFoundException("Flight not found")))
                .flatMap(flight -> {

                    int requestedSeats = bookingRequest.getTotalSeats();

                    if (flight.getAvailableSeats() < requestedSeats) {
                        return Mono.error(
                                new UnprocessableException("Not enough seats available"));
                    }

                    Ticket ticket = new Ticket();
                    ticket.setPnr(generatePNR());
                    ticket.setFlightId(flightId);

                    ticket.setCustomerName(bookingRequest.getName());
                    ticket.setCustomerEmail(bookingRequest.getEmail());

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

                    flightClient.updateAvailableSeats(
                            flightId,
                            flight.getAvailableSeats() - requestedSeats
                    );

                    return ticketRepository.save(ticket);
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
                        Mono.fromCallable(() -> flightClient.getFlightById(ticket.getFlightId()))
                                .flatMap(flight -> {

                                    if (LocalDateTime.now().plusHours(24)
                                            .isAfter(flight.getDeparture())) {
                                        return Mono.error(new UnprocessableException(
                                                "Cancellation not allowed within 24 hours of departure"));
                                    }

                                    ticket.setStatus("CANCELLED");

                                    flightClient.updateAvailableSeats(
                                            flight.getId(),
                                            flight.getAvailableSeats() + ticket.getNumSeats()
                                    );

                                    return ticketRepository.save(ticket)
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