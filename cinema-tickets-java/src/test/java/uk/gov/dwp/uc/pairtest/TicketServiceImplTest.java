package uk.gov.dwp.uc.pairtest;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.ADULT;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.CHILD;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.INFANT;

class TicketServiceImplTest {
    private static final long ACCOUNT_ID = 123L;

    private TicketPaymentService paymentService;
    private SeatReservationService reservationService;
    private TicketService service;

    @BeforeEach
    void setUp() {
        paymentService = mock(TicketPaymentService.class);
        reservationService = mock(SeatReservationService.class);
        service = new TicketServiceImpl(paymentService, reservationService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validPurchases")
    void chargesAndReservesExactlyOnce(String description, TicketTypeRequest[] requests,
                                     int expectedAmount, int expectedSeats) {
        service.purchaseTickets(ACCOUNT_ID, requests);

        verify(paymentService).makePayment(ACCOUNT_ID, expectedAmount);
        verify(reservationService).reserveSeat(ACCOUNT_ID, expectedSeats);
        verifyNoMoreInteractions(paymentService, reservationService);
    }

    private static Stream<Arguments> validPurchases() {
        return Stream.of(
                Arguments.of("one adult", requests(new TicketTypeRequest(ADULT, 1)), 25, 1),
                Arguments.of("multiple adults", requests(new TicketTypeRequest(ADULT, 3)), 75, 3),
                Arguments.of("adult and children", requests(
                        new TicketTypeRequest(ADULT, 1), new TicketTypeRequest(CHILD, 2)), 55, 3),
                Arguments.of("infant needs no payment or seat", requests(
                        new TicketTypeRequest(ADULT, 1), new TicketTypeRequest(INFANT, 1)), 25, 1),
                Arguments.of("all types", requests(new TicketTypeRequest(ADULT, 2),
                        new TicketTypeRequest(CHILD, 3), new TicketTypeRequest(INFANT, 2)), 95, 5),
                Arguments.of("just below the limit", requests(new TicketTypeRequest(ADULT, 24)), 600, 24),
                Arguments.of("at the limit", requests(new TicketTypeRequest(ADULT, 25)), 625, 25),
                Arguments.of("mixed purchase at the limit", requests(new TicketTypeRequest(ADULT, 10),
                        new TicketTypeRequest(CHILD, 10), new TicketTypeRequest(INFANT, 5)), 400, 20),
                Arguments.of("one adult and children at the limit", requests(
                        new TicketTypeRequest(ADULT, 1), new TicketTypeRequest(CHILD, 24)), 385, 25),
                Arguments.of("adults and infants at the limit", requests(
                        new TicketTypeRequest(ADULT, 13), new TicketTypeRequest(INFANT, 12)), 325, 13),
                Arguments.of("repeated ticket types", requests(new TicketTypeRequest(ADULT, 1),
                        new TicketTypeRequest(CHILD, 1), new TicketTypeRequest(ADULT, 2),
                        new TicketTypeRequest(CHILD, 2)), 120, 6),
                Arguments.of("adult can appear last", requests(new TicketTypeRequest(INFANT, 1),
                        new TicketTypeRequest(CHILD, 2), new TicketTypeRequest(ADULT, 1)), 55, 3),
                Arguments.of("no unstated infant to adult ratio", requests(
                        new TicketTypeRequest(ADULT, 1), new TicketTypeRequest(INFANT, 2)), 25, 1)
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsInvalidAccountWithoutCallingProviders(Long accountId) {
        assertThrows(InvalidPurchaseException.class,
                () -> service.purchaseTickets(accountId, new TicketTypeRequest(ADULT, 1)));

        verifyNoInteractions(paymentService, reservationService);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, Long.MAX_VALUE})
    void acceptsPositiveAccountIds(long accountId) {
        service.purchaseTickets(accountId, new TicketTypeRequest(ADULT, 1));

        verify(paymentService).makePayment(accountId, 25);
        verify(reservationService).reserveSeat(accountId, 1);
        verifyNoMoreInteractions(paymentService, reservationService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPurchases")
    void rejectsInvalidPurchaseWithoutCallingProviders(String description, TicketTypeRequest[] requests) {
        assertThrows(InvalidPurchaseException.class, () -> service.purchaseTickets(ACCOUNT_ID, requests));

        verifyNoInteractions(paymentService, reservationService);
    }

    private static Stream<Arguments> invalidPurchases() {
        return Stream.of(
                Arguments.of("null request array", (Object) null),
                Arguments.of("empty purchase", requests()),
                Arguments.of("null request", requests((TicketTypeRequest) null)),
                Arguments.of("null type", requests(new TicketTypeRequest(null, 1))),
                Arguments.of("zero adults", requests(new TicketTypeRequest(ADULT, 0))),
                Arguments.of("zero children in otherwise valid purchase", requests(
                        new TicketTypeRequest(ADULT, 1), new TicketTypeRequest(CHILD, 0))),
                Arguments.of("zero infants in otherwise valid purchase", requests(
                        new TicketTypeRequest(ADULT, 1), new TicketTypeRequest(INFANT, 0))),
                Arguments.of("negative adults", requests(new TicketTypeRequest(ADULT, -1))),
                Arguments.of("negative children", requests(
                        new TicketTypeRequest(ADULT, 2), new TicketTypeRequest(CHILD, -1))),
                Arguments.of("negative infants", requests(
                        new TicketTypeRequest(ADULT, 2), new TicketTypeRequest(INFANT, -1))),
                Arguments.of("minimum integer quantity", requests(new TicketTypeRequest(ADULT, Integer.MIN_VALUE))),
                Arguments.of("children without adult", requests(new TicketTypeRequest(CHILD, 1))),
                Arguments.of("infants without adult", requests(new TicketTypeRequest(INFANT, 1))),
                Arguments.of("children and infants without adult", requests(
                        new TicketTypeRequest(CHILD, 1), new TicketTypeRequest(INFANT, 1))),
                Arguments.of("one over the limit", requests(new TicketTypeRequest(ADULT, 26))),
                Arguments.of("combined types exceed limit", requests(
                        new TicketTypeRequest(ADULT, 13), new TicketTypeRequest(CHILD, 13))),
                Arguments.of("infants count towards limit", requests(
                        new TicketTypeRequest(ADULT, 13), new TicketTypeRequest(INFANT, 13))),
                Arguments.of("repeated types exceed limit", requests(
                        new TicketTypeRequest(ADULT, 13), new TicketTypeRequest(ADULT, 13))),
                Arguments.of("maximum integer quantity", requests(new TicketTypeRequest(ADULT, Integer.MAX_VALUE))),
                Arguments.of("quantities cannot wrap the total", requests(new TicketTypeRequest(ADULT, 1),
                        new TicketTypeRequest(CHILD, Integer.MAX_VALUE))),
                Arguments.of("later null request", requests(new TicketTypeRequest(ADULT, 1), null)),
                Arguments.of("later null type", requests(new TicketTypeRequest(ADULT, 1),
                        new TicketTypeRequest(null, 1)))
        );
    }

    @Test
    void defaultConstructorConnectsTheSuppliedProviders() {
        TicketService defaultService = new TicketServiceImpl();

        assertDoesNotThrow(() -> defaultService.purchaseTickets(ACCOUNT_ID, new TicketTypeRequest(ADULT, 1)));
    }

    @Test
    void rejectsMissingPaymentDependency() {
        assertThrows(NullPointerException.class, () -> new TicketServiceImpl(null, reservationService));

        verifyNoInteractions(reservationService);
    }

    @Test
    void rejectsMissingReservationDependency() {
        assertThrows(NullPointerException.class, () -> new TicketServiceImpl(paymentService, null));

        verifyNoInteractions(paymentService);
    }

    @ParameterizedTest
    @EnumSource(value = TicketTypeRequest.Type.class, names = {"CHILD", "INFANT"})
    void adultFromEarlierPurchaseDoesNotSatisfyLaterPurchase(TicketTypeRequest.Type type) {
        service.purchaseTickets(ACCOUNT_ID, new TicketTypeRequest(ADULT, 1));

        assertThrows(InvalidPurchaseException.class,
                () -> service.purchaseTickets(ACCOUNT_ID, new TicketTypeRequest(type, 1)));

        verify(paymentService).makePayment(ACCOUNT_ID, 25);
        verify(reservationService).reserveSeat(ACCOUNT_ID, 1);
        verifyNoMoreInteractions(paymentService, reservationService);
    }

    @Test
    void doesNotCarryTotalsBetweenPurchases() {
        service.purchaseTickets(ACCOUNT_ID, new TicketTypeRequest(ADULT, 25));
        service.purchaseTickets(456L, new TicketTypeRequest(ADULT, 1), new TicketTypeRequest(CHILD, 1));

        verify(paymentService).makePayment(ACCOUNT_ID, 625);
        verify(reservationService).reserveSeat(ACCOUNT_ID, 25);
        verify(paymentService).makePayment(456L, 40);
        verify(reservationService).reserveSeat(456L, 2);
        verifyNoMoreInteractions(paymentService, reservationService);
    }

    @Test
    void rejectedPurchaseDoesNotAffectNextPurchase() {
        assertThrows(InvalidPurchaseException.class, () -> service.purchaseTickets(ACCOUNT_ID,
                new TicketTypeRequest(ADULT, 25), new TicketTypeRequest(INFANT, 1)));
        verifyNoInteractions(paymentService, reservationService);

        service.purchaseTickets(ACCOUNT_ID, new TicketTypeRequest(ADULT, 1));

        verify(paymentService).makePayment(ACCOUNT_ID, 25);
        verify(reservationService).reserveSeat(ACCOUNT_ID, 1);
        verifyNoMoreInteractions(paymentService, reservationService);
    }

    private static TicketTypeRequest[] requests(TicketTypeRequest... requests) {
        return requests;
    }
}
