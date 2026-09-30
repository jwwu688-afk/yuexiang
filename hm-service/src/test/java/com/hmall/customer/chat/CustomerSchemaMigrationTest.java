package com.hmall.customer.chat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerSchemaMigrationTest {
    @Test
    void createsAllSixCustomerTables() throws Exception {
        String url = "jdbc:h2:mem:customer_schema;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/test-migration").load().migrate();

        Set<String> tables = new HashSet<>();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
            while (result.next()) tables.add(result.getString(1).toLowerCase());
        }

        assertThat(tables).contains("customer_conversation", "customer_message", "customer_run",
                "customer_event", "customer_policy", "customer_ticket");
    }
}
