package com.hmall.customer.query;

import com.hmall.api.dto.customer.CustomerItemDTO;
import com.hmall.common.utils.BeanUtils;
import com.hmall.domain.po.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerItemQueryService {
    private static final int MAX_PAGE_SIZE = 20;

    private final CustomerItemRepository repository;

    public List<CustomerItemDTO> search(String key, String brand, String category, Integer minPrice,
            Integer maxPrice, Integer pageSize) {
        if (minPrice != null && minPrice < 0 || maxPrice != null && maxPrice < 0
                || minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "价格范围无效");
        }
        int limit = pageSize == null ? 10 : Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        return repository.search(key, brand, category, minPrice, maxPrice, limit).stream()
                .filter(item -> Objects.equals(item.getStatus(), 1))
                .limit(limit)
                .map(item -> BeanUtils.copyBean(item, CustomerItemDTO.class))
                .collect(Collectors.toList());
    }

    public CustomerItemDTO get(Long id) {
        if (id == null) {
            return null;
        }
        Item item = repository.findAvailableById(id);
        if (item == null || !Objects.equals(item.getId(), id) || !Objects.equals(item.getStatus(), 1)) {
            return null;
        }
        return BeanUtils.copyBean(item, CustomerItemDTO.class);
    }
}
