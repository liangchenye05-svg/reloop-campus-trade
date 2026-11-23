package com.campustrade.trade;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.campustrade.common.BusinessException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class OrderServiceTest {
    private static final String ID = "933d05f4-37ee-4f57-ab96-8b35f8c43fc1";
    private final OrderMapper mapper = mock(OrderMapper.class);
    private final OutboxMapper outbox = mock(OutboxMapper.class);
    private final OrderService service = new OrderService(mock(CartMapper.class), mapper, outbox,
            mock(ApplicationEventPublisher.class), Duration.ofMinutes(15));

    @Test
    void aRepeatedCancellationDoesNotRestoreStockAgain() {
        when(mapper.lock(ID)).thenReturn(order(TradeOrder.Status.CANCELLED, 30));
        assertEquals(TradeOrder.Status.CANCELLED, service.change(7, ID, TradeOrder.Status.CANCELLED).status());
        verify(mapper, never()).release(anyLong(), anyInt());
        verifyNoInteractions(outbox);
    }

    @Test
    void anExpiredOrderCannotBePaid() {
        when(mapper.lock(ID)).thenReturn(order(TradeOrder.Status.PENDING_PAYMENT, -30));
        var failure = assertThrows(BusinessException.class, () -> service.change(7, ID, TradeOrder.Status.PAID));
        assertEquals("ORDER_EXPIRED", failure.code());
        verify(mapper, never()).consume(anyLong(), anyInt());
        verifyNoInteractions(outbox);
    }

    @Test
    void duplicateTimeoutDeliveryReleasesStockOnlyOnTheFirstTransition() {
        when(mapper.lock(ID)).thenReturn(order(TradeOrder.Status.PENDING_PAYMENT, -30),
                order(TradeOrder.Status.EXPIRED, -30));
        when(mapper.items(ID)).thenReturn(List.of(new TradeOrder.Item(ID, 11, 17, "Book", BigDecimal.TEN, 2)));
        assertTrue(service.expire(ID));
        assertFalse(service.expire(ID));
        verify(mapper).release(11, 2);
        verify(mapper).transition(eq(ID), eq(TradeOrder.Status.EXPIRED), any());
        verify(outbox).enqueue(eq(ID), eq(OutboxMapper.Kind.EXPIRED), anyString(), any(), any());
    }

    @Test
    void otherUsersCannotCancelAnOrder() {
        when(mapper.lock(ID)).thenReturn(order(TradeOrder.Status.PENDING_PAYMENT, 30));
        assertEquals("NOT_FOUND",
                assertThrows(BusinessException.class, () -> service.change(8, ID, TradeOrder.Status.CANCELLED)).code());
        verify(mapper, never()).transition(anyString(), any(), any());
        verifyNoInteractions(outbox);
    }

    private TradeOrder order(TradeOrder.Status status, int expiresInSeconds) {
        var now = LocalDateTime.now(ZoneOffset.UTC);
        return new TradeOrder(ID, 7, "test", "hash", status, BigDecimal.TEN, now.minusHours(1),
                now.plusSeconds(expiresInSeconds), null, null);
    }
}
