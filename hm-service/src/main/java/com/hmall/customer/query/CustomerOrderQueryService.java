package com.hmall.customer.query;

import com.hmall.api.dto.customer.CustomerLogisticsDTO;
import com.hmall.api.dto.customer.CustomerOrderDTO;
import com.hmall.api.dto.customer.CustomerOrderDetailDTO;
import com.hmall.domain.po.Order;
import com.hmall.domain.po.OrderDetail;
import com.hmall.domain.po.OrderLogistics;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerOrderQueryService {
    private static final int MAX_PAGE_SIZE = 20;
    private final CustomerOrderRepository repository;

    public List<CustomerOrderDTO> listOwnedOrders(Long userId, int pageSize) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "需要登录");
        }
        int limit = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        return repository.listOwned(userId, limit).stream()
                .filter(order -> Objects.equals(order.getUserId(), userId))
                .limit(limit).map(this::toOrder).collect(Collectors.toList());
    }

    public CustomerOrderDTO getOwnedOrder(Long userId, Long orderId) {
        return toOrder(owned(userId, orderId));
    }

    public CustomerLogisticsDTO getOwnedLogistics(Long userId, Long orderId) {
        Order order = owned(userId, orderId);
        OrderLogistics logistics = repository.findLogistics(orderId);
        CustomerLogisticsDTO result = new CustomerLogisticsDTO();
        result.setOrderId(orderId);
        result.setOrderStatus(order.getStatus());
        result.setConsignTime(order.getConsignTime());
        if (logistics != null) {
            result.setLogisticsCompany(logistics.getLogisticsCompany());
            result.setLogisticsNumber(logistics.getLogisticsNumber());
        }
        return result;
    }

    private Order owned(Long userId, Long orderId) {
        if (userId == null || orderId == null) throw notFound();
        Order order = repository.findOwned(userId, orderId);
        if (order == null || !Objects.equals(order.getUserId(), userId) || !Objects.equals(order.getId(), orderId)) {
            throw notFound();
        }
        return order;
    }

    private CustomerOrderDTO toOrder(Order order) {
        CustomerOrderDTO result = new CustomerOrderDTO();
        result.setId(order.getId());
        result.setStatus(order.getStatus());
        result.setTotalFee(order.getTotalFee());
        result.setCreateTime(order.getCreateTime());
        result.setPayTime(order.getPayTime());
        result.setConsignTime(order.getConsignTime());
        result.setDetails(repository.listDetails(order.getId()).stream().map(this::toDetail).collect(Collectors.toList()));
        return result;
    }

    private CustomerOrderDetailDTO toDetail(OrderDetail detail) {
        CustomerOrderDetailDTO result = new CustomerOrderDetailDTO();
        result.setItemId(detail.getItemId());
        result.setName(detail.getName());
        result.setSpec(detail.getSpec());
        result.setPrice(detail.getPrice());
        result.setNum(detail.getNum());
        result.setImage(detail.getImage());
        return result;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
    }
}
