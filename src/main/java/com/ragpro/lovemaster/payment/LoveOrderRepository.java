package com.ragpro.lovemaster.payment;

import com.ragpro.lovemaster.model.LoveOrder;
import com.ragpro.lovemaster.payment.domestic.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class LoveOrderRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<LoveOrder> ROW_MAPPER = (rs, rowNum) -> LoveOrder.builder()
            .id(rs.getLong("id"))
            .courseId(rs.getString("course_id"))
            .courseName(rs.getString("course_name"))
            .outTradeNo(rs.getString("out_trade_no"))
            .paymentProvider(rs.getString("payment_provider"))
            .payChannel(rs.getString("pay_channel"))
            .stripeSessionId(rs.getString("stripe_session_id"))
            .stripePaymentIntentId(rs.getString("stripe_payment_intent_id"))
            .transactionId(rs.getString("transaction_id"))
            .openId(rs.getString("open_id"))
            .amountCents(rs.getLong("amount_cents"))
            .currency(rs.getString("currency"))
            .status(rs.getString("status"))
            .customerEmail(rs.getString("customer_email"))
            .createdAt(toInstant(rs.getTimestamp("created_at")))
            .updatedAt(toInstant(rs.getTimestamp("updated_at")))
            .build();

    public void initSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS love_orders (
                    id BIGSERIAL PRIMARY KEY,
                    course_id VARCHAR(64) NOT NULL,
                    course_name VARCHAR(256) NOT NULL,
                    out_trade_no VARCHAR(64),
                    payment_provider VARCHAR(16) DEFAULT 'stripe',
                    pay_channel VARCHAR(32),
                    stripe_session_id VARCHAR(128),
                    stripe_payment_intent_id VARCHAR(128),
                    transaction_id VARCHAR(128),
                    open_id VARCHAR(128),
                    amount_cents BIGINT NOT NULL,
                    currency VARCHAR(8) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    customer_email VARCHAR(256),
                    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
                )
                """);
        migrateColumns();
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_love_orders_out_trade_no
                ON love_orders(out_trade_no) WHERE out_trade_no IS NOT NULL
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_love_orders_stripe_session
                ON love_orders(stripe_session_id) WHERE stripe_session_id IS NOT NULL
                """);
        log.info("love_orders schema initialized");
    }

    private void migrateColumns() {
        jdbcTemplate.execute("ALTER TABLE love_orders ADD COLUMN IF NOT EXISTS out_trade_no VARCHAR(64)");
        jdbcTemplate.execute("ALTER TABLE love_orders ADD COLUMN IF NOT EXISTS payment_provider VARCHAR(16) DEFAULT 'stripe'");
        jdbcTemplate.execute("ALTER TABLE love_orders ADD COLUMN IF NOT EXISTS pay_channel VARCHAR(32)");
        jdbcTemplate.execute("ALTER TABLE love_orders ADD COLUMN IF NOT EXISTS transaction_id VARCHAR(128)");
        jdbcTemplate.execute("ALTER TABLE love_orders ADD COLUMN IF NOT EXISTS open_id VARCHAR(128)");
    }

    public String nextOutTradeNo() {
        return "LOVE" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    public void insert(LoveOrder order) {
        jdbcTemplate.update("""
                INSERT INTO love_orders
                (course_id, course_name, out_trade_no, payment_provider, pay_channel,
                 stripe_session_id, stripe_payment_intent_id, transaction_id, open_id,
                 amount_cents, currency, status, customer_email, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                order.getCourseId(),
                order.getCourseName(),
                order.getOutTradeNo(),
                order.getPaymentProvider(),
                order.getPayChannel(),
                order.getStripeSessionId(),
                order.getStripePaymentIntentId(),
                order.getTransactionId(),
                order.getOpenId(),
                order.getAmountCents(),
                order.getCurrency(),
                order.getStatus(),
                order.getCustomerEmail(),
                Timestamp.from(order.getCreatedAt()),
                Timestamp.from(order.getUpdatedAt()));
    }

    public Optional<LoveOrder> findBySessionId(String sessionId) {
        var results = jdbcTemplate.query(
                "SELECT * FROM love_orders WHERE stripe_session_id = ? OR out_trade_no = ?",
                ROW_MAPPER,
                sessionId, sessionId);
        return results.stream().findFirst();
    }

    public Optional<LoveOrder> findByOutTradeNo(String outTradeNo) {
        var results = jdbcTemplate.query(
                "SELECT * FROM love_orders WHERE out_trade_no = ?",
                ROW_MAPPER,
                outTradeNo);
        return results.stream().findFirst();
    }

    public void updatePaid(String outTradeNo, String transactionId, String openId) {
        jdbcTemplate.update("""
                UPDATE love_orders
                SET status = 'paid',
                    transaction_id = COALESCE(?, transaction_id),
                    open_id = COALESCE(?, open_id),
                    stripe_payment_intent_id = COALESCE(?, stripe_payment_intent_id),
                    updated_at = NOW()
                WHERE out_trade_no = ?
                """,
                transactionId,
                openId,
                transactionId,
                outTradeNo);
    }

    public void updateStripePaid(String sessionId, String paymentIntentId, String customerEmail) {
        jdbcTemplate.update("""
                UPDATE love_orders
                SET status = 'paid',
                    stripe_payment_intent_id = ?,
                    transaction_id = COALESCE(?, transaction_id),
                    customer_email = COALESCE(?, customer_email),
                    updated_at = NOW()
                WHERE stripe_session_id = ? OR out_trade_no = ?
                """,
                paymentIntentId,
                paymentIntentId,
                customerEmail,
                sessionId,
                sessionId);
    }

    public static String providerName(PaymentProvider provider) {
        return provider.name().toLowerCase();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
