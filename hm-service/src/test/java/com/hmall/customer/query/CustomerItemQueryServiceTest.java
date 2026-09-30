package com.hmall.customer.query;

import com.hmall.domain.po.Item;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerItemQueryServiceTest {
    private List<Item> rows = List.of();
    private SearchArguments lastSearch;
    private final CustomerItemRepository repository = new CustomerItemRepository() {
        @Override
        public List<Item> search(String key, String brand, String category, Integer minPrice,
                Integer maxPrice, int limit) {
            lastSearch = new SearchArguments(key, brand, category, minPrice, maxPrice, limit);
            return rows;
        }

        @Override
        public Item findAvailableById(Long id) {
            return rows.stream()
                    .filter(item -> item.getId().equals(id) && item.getStatus() == 1)
                    .findFirst()
                    .orElse(null);
        }
    };
    private final CustomerItemQueryService queries = new CustomerItemQueryService(repository);

    @Test
    void searchHidesOffShelfItemsAndCapsResultsAtTwenty() {
        rows = new ArrayList<>();
        rows.add(item(999L, 2));
        rows.addAll(IntStream.rangeClosed(1, 25)
                .mapToObj(id -> item((long) id, 1))
                .collect(Collectors.toList()));

        assertThat(queries.search("phone", "悦享", "数码", 100, 1000, 100))
                .hasSize(20)
                .allMatch(item -> item.getId() != 999L);
        assertThat(lastSearch).isEqualTo(new SearchArguments("phone", "悦享", "数码", 100, 1000, 20));
    }

    @Test
    void getReturnsOnlyAvailableItem() {
        rows = List.of(item(1L, 2));
        assertThat(queries.get(1L)).isNull();

        rows = List.of(item(1L, 1));
        assertThat(queries.get(1L)).isNotNull();
    }

    @Test
    void rejectsReversedPriceRange() {
        assertThatThrownBy(() -> queries.search(null, null, null, 200, 100, 10))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatus().value()).isEqualTo(400));
    }

    private static Item item(Long id, int status) {
        return new Item().setId(id).setName("item-" + id).setPrice(100).setStock(5).setStatus(status);
    }

    private static final class SearchArguments {
        private final String key;
        private final String brand;
        private final String category;
        private final Integer minPrice;
        private final Integer maxPrice;
        private final int limit;

        private SearchArguments(String key, String brand, String category, Integer minPrice, Integer maxPrice, int limit) {
            this.key = key;
            this.brand = brand;
            this.category = category;
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
            this.limit = limit;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof SearchArguments)) return false;
            SearchArguments value = (SearchArguments) other;
            return java.util.Objects.equals(key, value.key)
                    && java.util.Objects.equals(brand, value.brand)
                    && java.util.Objects.equals(category, value.category)
                    && java.util.Objects.equals(minPrice, value.minPrice)
                    && java.util.Objects.equals(maxPrice, value.maxPrice)
                    && limit == value.limit;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(key, brand, category, minPrice, maxPrice, limit);
        }
    }
}
