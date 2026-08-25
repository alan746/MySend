package com.mysend.account;

import com.mysend.room.Plan;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

@Repository
public class AccountRepository {

    private final JdbcClient jdbc;

    public AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existsByEmail(String email) {
        return jdbc.sql("select count(*) from accounts where email = :email")
                .param("email", email)
                .query(Integer.class)
                .single() > 0;
    }

    public Optional<Account> findByEmail(String email) {
        return jdbc.sql("select * from accounts where email = :email")
                .param("email", email)
                .query(AccountRepository::mapAccount)
                .optional();
    }

    public Optional<Account> findById(String id) {
        return jdbc.sql("select * from accounts where id = :id")
                .param("id", id)
                .query(AccountRepository::mapAccount)
                .optional();
    }

    public void insert(Account account) {
        jdbc.sql("""
                        insert into accounts (
                            id, email, password_hash, plan, stripe_customer_id,
                            stripe_subscription_id, created_at_ms, updated_at_ms
                        ) values (
                            :id, :email, :passwordHash, :plan, :customerId,
                            :subscriptionId, :createdAt, :updatedAt
                        )
                        """)
                .param("id", account.id())
                .param("email", account.email())
                .param("passwordHash", account.passwordHash())
                .param("plan", account.plan().name())
                .param("customerId", account.stripeCustomerId())
                .param("subscriptionId", account.stripeSubscriptionId())
                .param("createdAt", account.createdAt().toEpochMilli())
                .param("updatedAt", account.updatedAt().toEpochMilli())
                .update();
    }

    public void updatePassword(String accountId, String passwordHash, Instant updatedAt) {
        jdbc.sql("""
                        update accounts
                        set password_hash = :passwordHash,
                            updated_at_ms = :updatedAt
                        where id = :accountId
                        """)
                .param("passwordHash", passwordHash)
                .param("updatedAt", updatedAt.toEpochMilli())
                .param("accountId", accountId)
                .update();
    }

    public void activatePremium(
            String accountId,
            String customerId,
            String subscriptionId,
            Instant eventCreatedAt
    ) {
        jdbc.sql("""
                        update accounts
                        set plan = 'PREMIUM',
                            stripe_customer_id = :customerId,
                            stripe_subscription_id = :subscriptionId,
                            stripe_updated_at_ms = :eventCreatedAt,
                            updated_at_ms = :eventCreatedAt
                        where (id = :accountId
                               or stripe_customer_id = :customerId
                               or stripe_subscription_id = :subscriptionId)
                          and (stripe_updated_at_ms is null
                               or stripe_updated_at_ms <= :eventCreatedAt)
                        """)
                .param("customerId", customerId)
                .param("subscriptionId", subscriptionId)
                .param("eventCreatedAt", eventCreatedAt.toEpochMilli())
                .param("accountId", accountId)
                .update();
    }

    public void deactivateSubscription(String subscriptionId, Instant eventCreatedAt) {
        jdbc.sql("""
                        update accounts
                        set plan = 'FREE',
                            stripe_subscription_id = null,
                            stripe_updated_at_ms = :eventCreatedAt,
                            updated_at_ms = :eventCreatedAt
                        where stripe_subscription_id = :subscriptionId
                          and (stripe_updated_at_ms is null
                               or stripe_updated_at_ms <= :eventCreatedAt)
                        """)
                .param("eventCreatedAt", eventCreatedAt.toEpochMilli())
                .param("subscriptionId", subscriptionId)
                .update();
    }

    private static Account mapAccount(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Account(
                resultSet.getString("id"),
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                Plan.valueOf(resultSet.getString("plan")),
                resultSet.getString("stripe_customer_id"),
                resultSet.getString("stripe_subscription_id"),
                Instant.ofEpochMilli(resultSet.getLong("created_at_ms")),
                Instant.ofEpochMilli(resultSet.getLong("updated_at_ms"))
        );
    }
}
