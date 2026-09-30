package com.hmall.customer.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.domain.po.Order;
import com.hmall.domain.po.OrderDetail;
import com.hmall.domain.po.OrderLogistics;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerOrderQueryServiceTest {
    private Order order;
    private List<OrderDetail> details = List.of();
    private OrderLogistics logistics;
    private final CustomerOrderRepository repository = new CustomerOrderRepository() {
        @Override public List<Order> listOwned(Long userId, int limit) { return order == null ? List.of() : List.of(order); }
        @Override public Order findOwned(Long userId, Long orderId) { return order; }
        @Override public List<OrderDetail> listDetails(Long orderId) { return details; }
        @Override public OrderLogistics findLogistics(Long orderId) { return logistics; }
    };
    private final CustomerOrderQueryService queries = new CustomerOrderQueryService(repository);

    @Test
    void anonymousAndForeignOrdersLookMissing() {
        order = new Order().setId(77L).setUserId(2L).setStatus(3);

        assertThatThrownBy(() -> queries.getOwnedOrder(null, 77L)).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatus().value()).isEqualTo(404));
        assertThatThrownBy(() -> queries.getOwnedOrder(1L, 77L)).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatus().value()).isEqualTo(404));
        assertThatThrownBy(() -> queries.getOwnedLogistics(1L, 77L)).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatus().value()).isEqualTo(404));
    }

    @Test
    void ownedOrderExposesOnlySafeOrderAndLogisticsFields() throws Exception {
        order = new Order().setId(77L).setUserId(1L).setStatus(3).setTotalFee(200);
        details = List.of(new OrderDetail().setOrderId(77L).setItemId(9L).setName("safe").setNum(2).setPrice(100));
        logistics = new OrderLogistics().setOrderId(77L).setLogisticsCompany("SF").setLogisticsNumber("NO1")
                .setContact("private").setMobile("13800000000").setStreet("private");

        assertThat(queries.getOwnedOrder(1L, 77L).getDetails()).singleElement()
                .satisfies(detail -> assertThat(detail.getName()).isEqualTo("safe"));
        String logisticsJson = new ObjectMapper().writeValueAsString(queries.getOwnedLogistics(1L, 77L));
        assertThat(logisticsJson).contains("NO1").doesNotContain("private", "13800000000", "mobile", "street", "contact");
    }

    @Test
    void listRequiresLoginAndCapsPageSize() {
        assertThatThrownBy(() -> queries.listOwnedOrders(null, 10)).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatus().value()).isEqualTo(401));
        order = new Order().setId(77L).setUserId(1L).setStatus(3);
        assertThat(queries.listOwnedOrders(1L, 100)).hasSize(1);
    }
}
