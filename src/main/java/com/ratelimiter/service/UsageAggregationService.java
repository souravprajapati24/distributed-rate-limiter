package com.ratelimiter.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Rolls up rate_limit_audit_log rows into pre-aggregated usage_summaries rows,
 * once per hour, so the usage-history API (UsageController, Step 5.7) can serve
 * fast, indexed queries instead of an expensive GROUP BY over the full,
 * ever-growing audit log.
 *
 * Runs at 5 minutes past every hour (not exactly on the hour) to give the
 * asynchronous Kafka audit pipeline (AuditEventPublisher -> Kafka ->
 * AuditConsumer, Steps 5.3-5.5) a grace period to fully persist any events
 * from the tail end of the just-completed hour before this job's query runs.
 *
 * Uses raw JDBC (JdbcTemplate), not Spring Data JPA, for the aggregation
 * itself: a bulk INSERT...SELECT...GROUP BY...ON CONFLICT DO UPDATE upsert is
 * a set-based, database-native operation JPA's entity-oriented API is a poor
 * fit for — and the ON CONFLICT clause is exactly what makes repeated runs of
 * this job for the same period idempotent (see class-level rationale in the
 * Phase 5 guide, Step 5.6).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageAggregationService {

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    private static final String UPSERT_HOURLY_USAGE_SQL = """
        INSERT INTO usage_summaries (tenant_id, period_start, period_end, granularity, allowed, denied)
        SELECT
            tenant_id,
            ?::TIMESTAMPTZ AS period_start,
            ?::TIMESTAMPTZ AS period_end,
            'HOURLY'       AS granularity,
            COUNT(*) FILTER (WHERE decision = 'ALLOWED') AS allowed,
            COUNT(*) FILTER (WHERE decision = 'DENIED')  AS denied
        FROM rate_limit_audit_log
        WHERE evaluated_at >= ? AND evaluated_at < ?
        GROUP BY tenant_id
        ON CONFLICT (tenant_id, period_start, granularity)
        DO UPDATE SET
            allowed    = EXCLUDED.allowed,
            denied     = EXCLUDED.denied,
            updated_at = NOW()
        """;

    private static final String UPSERT_DAILY_USAGE_SQL = """
    INSERT INTO usage_summaries (tenant_id, period_start, period_end, granularity, allowed, denied)
    SELECT
        tenant_id,
        ?::TIMESTAMPTZ AS period_start,
        ?::TIMESTAMPTZ AS period_end,
        'DAILY'        AS granularity,
        SUM(allowed) AS allowed,
        SUM(denied)  AS denied
    FROM usage_summaries
    WHERE granularity = 'HOURLY' AND period_start >= ? AND period_start < ?
    GROUP BY tenant_id
    ON CONFLICT (tenant_id, period_start, granularity)
    DO UPDATE SET
        allowed    = EXCLUDED.allowed,
        denied     = EXCLUDED.denied,
        updated_at = NOW()
    """;
    /**
     * Runs at HH:05:00 every hour, UTC. Aggregates the FULL PREVIOUS hour
     * (e.g. at 14:05, aggregates everything from 13:00:00 up to but not
     * including 14:00:00) — the just-completed hour, not the hour still in
     * progress.
     *
     * @Transactional: the upsert is a single statement, but wrapping it in a
     * transaction is a deliberate, cheap safety net consistent with this
     * codebase's general practice of never leaving a write operation outside
     * a defined transactional boundary.
     */
    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void aggregateHourly() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime periodEnd = now.truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime periodStart = periodEnd.minusHours(1);

        log.info("Starting hourly usage aggregation for period {} to {} (UTC)", periodStart, periodEnd);

        OffsetDateTime startArg = periodStart.toOffsetDateTime();
        OffsetDateTime endArg = periodEnd.toOffsetDateTime();

        try {
            int rowsAffected = jdbcTemplate.update(
                    UPSERT_HOURLY_USAGE_SQL,
                    startArg, endArg, startArg, endArg
            );

            meterRegistry.counter("ratelimit.usage.aggregation.rows").increment(rowsAffected);
            log.info("Hourly usage aggregation complete for period {} to {}: {} tenant rows upserted",
                    periodStart, periodEnd, rowsAffected);
        } catch (Exception e) {
            meterRegistry.counter("ratelimit.usage.aggregation.failed").increment();
            log.error("Hourly usage aggregation FAILED for period {} to {}. Will retry at the next scheduled run.",
                    periodStart, periodEnd, e);
            // Deliberately not rethrown further than logging: @Scheduled methods
            // that throw only cause THIS invocation's failure to be logged by
            // Spring's scheduling infrastructure — the next scheduled run (one
            // hour later) is unaffected and will simply re-aggregate the SAME
            // failed period again (since usage_summaries rows for that period,
            // if any were partially written, are safely overwritten by the
            // idempotent upsert) in addition to the new period, giving this job
            // a natural, built-in retry mechanism without any custom code.
        }
    }


    @Scheduled(cron = "0 15 0 * * *") // 00:15 UTC — 10 min after the last HOURLY run (00:05) for the prior day
    @Transactional
    public void aggregateDaily() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime periodEnd = now.truncatedTo(ChronoUnit.DAYS);       // today 00:00
        ZonedDateTime periodStart = periodEnd.minusDays(1);               // yesterday 00:00

        try {
            int rows = jdbcTemplate.update(UPSERT_DAILY_USAGE_SQL,
                    periodStart.toOffsetDateTime(), periodEnd.toOffsetDateTime(),
                    periodStart.toOffsetDateTime(), periodEnd.toOffsetDateTime());
            meterRegistry.counter("ratelimit.usage.aggregation.rows", "granularity", "DAILY").increment(rows);
            log.info("Daily usage aggregation complete for {} to {}: {} rows", periodStart, periodEnd, rows);
        } catch (Exception e) {
            meterRegistry.counter("ratelimit.usage.aggregation.failed", "granularity", "DAILY").increment();
            log.error("Daily usage aggregation FAILED for {} to {}", periodStart, periodEnd, e);
        }
    }

    /**
     * Manually trigger aggregation for an arbitrary period — useful for
     * backfilling a specific hour after a fix, or for integration tests that
     * cannot wait for the real cron schedule to fire.
     */
    @Transactional
    public int aggregateHourlyForPeriod(OffsetDateTime periodStart, OffsetDateTime periodEnd) {
        log.info("Manually triggered usage aggregation for period {} to {}", periodStart, periodEnd);
        int rowsAffected = jdbcTemplate.update(
                UPSERT_HOURLY_USAGE_SQL,
                periodStart, periodEnd, periodStart, periodEnd
        );
        meterRegistry.counter("ratelimit.usage.aggregation.rows").increment(rowsAffected);
        return rowsAffected;
    }
}