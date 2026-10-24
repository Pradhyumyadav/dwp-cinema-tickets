package uk.gov.dwp.uc.pairtest;

import java.util.Objects;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.paymentgateway.TicketPaymentServiceImpl;
import thirdparty.seatbooking.SeatReservationService;
import thirdparty.seatbooking.SeatReservationServiceImpl;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

public class TicketServiceImpl implements TicketService {
    private static final int MAX_TICKETS = 25;
    private static final int ADULT_PRICE = 25;
    private static final int CHILD_PRICE = 15;

    private final TicketPaymentService paymentService;
    private final SeatReservationService reservationService;

    public TicketServiceImpl() {
        this(new TicketPaymentServiceImpl(), new SeatReservationServiceImpl());
    }

    public TicketServiceImpl(TicketPaymentService paymentService, SeatReservationService reservationService) {
        this.paymentService = Objects.requireNonNull(paymentService, "Payment service is required");
        this.reservationService = Objects.requireNonNull(reservationService, "Reservation service is required");
    }

    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
        validateAccountId(accountId);
        PurchaseTotals purchase = validateAndCalculate(ticketTypeRequests);

        paymentService.makePayment(accountId, purchase.amountToPay());
        reservationService.reserveSeat(accountId, purchase.seatsToReserve());
    }

    private static void validateAccountId(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new InvalidPurchaseException("Account ID must be greater than zero");
        }
    }

    private static PurchaseTotals validateAndCalculate(TicketTypeRequest[] ticketTypeRequests) {
        if (ticketTypeRequests == null || ticketTypeRequests.length == 0) {
            throw new InvalidPurchaseException("At least one ticket request is required");
        }

        int totalTickets = 0;
        int adults = 0;
        int children = 0;

        for (TicketTypeRequest request : ticketTypeRequests) {
            if (request == null || request.getTicketType() == null) {
                throw new InvalidPurchaseException("Each request must specify a ticket type");
            }

            int quantity = request.getNoOfTickets();
            if (quantity <= 0) {
                throw new InvalidPurchaseException("Ticket quantity must be greater than zero");
            }
            // Check before addition so even very large inputs cannot overflow the total.
            if (quantity > MAX_TICKETS - totalTickets) {
                throw new InvalidPurchaseException("A purchase cannot contain more than " + MAX_TICKETS + " tickets");
            }

            totalTickets += quantity;
            switch (request.getTicketType()) {
                case ADULT -> adults += quantity;
                case CHILD -> children += quantity;
                case INFANT -> { /* Infants count towards the limit but need no payment or seat. */ }
            }
        }

        if (adults == 0) {
            throw new InvalidPurchaseException("A purchase must include an adult ticket");
        }

        return new PurchaseTotals(adults * ADULT_PRICE + children * CHILD_PRICE, adults + children);
    }

    private record PurchaseTotals(int amountToPay, int seatsToReserve) {
    }
}
