package uk.gov.dwp.uc.pairtest;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.ADULT;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.CHILD;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.INFANT;

class TicketServiceRequirementsTest {
    private static final long ACCOUNT_ID = 42L;

    @Test
    void checksAllTicketCountCombinationsFromZeroThroughTwentySix() {
        // Zero here means a type is omitted, not a zero-quantity request entry.
        for (int adults = 0; adults <= 26; adults++) {
            for (int children = 0; children <= 26; children++) {
                for (int infants = 0; infants <= 26; infants++) {
                    List<TicketTypeRequest> requests = new ArrayList<>();
                    addIfPresent(requests, ADULT, adults);
                    addIfPresent(requests, CHILD, children);
                    addIfPresent(requests, INFANT, infants);

                    String context = "adults=" + adults + ", children=" + children + ", infants=" + infants;
                    RecordingProviders providers = new RecordingProviders();
                    TicketService service = providers.service();
                    TicketTypeRequest[] purchase = requests.toArray(TicketTypeRequest[]::new);

                    if (adults >= 1 && adults + children + infants <= 25) {
                        service.purchaseTickets(ACCOUNT_ID, purchase);
                        providers.assertPurchase(adults * 25 + children * 15, adults + children, context);
                    } else {
                        assertThrows(InvalidPurchaseException.class,
                                () -> service.purchaseTickets(ACCOUNT_ID, purchase), context);
                        providers.assertNoCalls(context);
                    }
                }
            }
        }
    }

    @Test
    void splittingAndReorderingEveryValidCombinationPreservesThePurchase() {
        for (int adults = 1; adults <= 25; adults++) {
            for (int children = 0; children <= 25 - adults; children++) {
                for (int infants = 0; infants <= 25 - adults - children; infants++) {
                    List<TicketTypeRequest> requests = new ArrayList<>();
                    // Put adults last and split each quantity into repeated single-ticket entries.
                    addIndividualTickets(requests, INFANT, infants);
                    addIndividualTickets(requests, CHILD, children);
                    addIndividualTickets(requests, ADULT, adults);

                    String context = "split purchase: adults=" + adults
                            + ", children=" + children + ", infants=" + infants;
                    RecordingProviders providers = new RecordingProviders();

                    providers.service().purchaseTickets(ACCOUNT_ID, requests.toArray(TicketTypeRequest[]::new));

                    providers.assertPurchase(adults * 25 + children * 15, adults + children, context);
                }
            }
        }
    }

    private static void addIfPresent(List<TicketTypeRequest> requests, TicketTypeRequest.Type type, int quantity) {
        if (quantity > 0) {
            requests.add(new TicketTypeRequest(type, quantity));
        }
    }

    private static void addIndividualTickets(List<TicketTypeRequest> requests,
                                             TicketTypeRequest.Type type, int quantity) {
        for (int ticket = 0; ticket < quantity; ticket++) {
            requests.add(new TicketTypeRequest(type, 1));
        }
    }

    private record ProviderCall(long accountId, int quantity) {
    }

    private static class RecordingProviders {
        private final List<ProviderCall> payments = new ArrayList<>();
        private final List<ProviderCall> reservations = new ArrayList<>();

        private TicketService service() {
            return new TicketServiceImpl(
                    (accountId, amount) -> payments.add(new ProviderCall(accountId, amount)),
                    (accountId, seats) -> reservations.add(new ProviderCall(accountId, seats)));
        }

        private void assertPurchase(int amount, int seats, String context) {
            assertEquals(List.of(new ProviderCall(ACCOUNT_ID, amount)), payments, context + " payment");
            assertEquals(List.of(new ProviderCall(ACCOUNT_ID, seats)), reservations, context + " reservation");
        }

        private void assertNoCalls(String context) {
            assertEquals(List.of(), payments, context + " payment");
            assertEquals(List.of(), reservations, context + " reservation");
        }
    }
}
