package com.hmall.customer.query;

import com.hmall.domain.po.Item;

import java.util.List;

public interface CustomerItemRepository {
    List<Item> search(String key, String brand, String category, Integer minPrice, Integer maxPrice, int limit);

    Item findAvailableById(Long id);
}
