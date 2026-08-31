package kz.afm.kendala.analytics;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import kz.afm.kendala.analytics.dto.AnalyticsSummaryResponse;
import kz.afm.kendala.analytics.dto.ApplicationTrendResponse;
import kz.afm.kendala.analytics.dto.DecisionRateResponse;
import kz.afm.kendala.analytics.dto.ProcessingTimeResponse;
import kz.afm.kendala.analytics.dto.RegionMetricResponse;
import kz.afm.kendala.analytics.dto.RiskCategoryMetricResponse;
import kz.afm.kendala.analytics.dto.StatusMetricResponse;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.enums.DecisionType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalyticsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AnalyticsSummaryResponse summary(AnalyticsFilter filter) {
        SqlFilter sqlFilter = filter(filter);
        SummaryAmounts amounts = jdbc.queryForObject(
                """
                SELECT COUNT(*) AS total_applications,
                       COALESCE(SUM(a.requested_amount), 0) AS total_requested_amount,
                       COALESCE(SUM(a.approved_amount), 0) AS total_approved_amount,
                       COALESCE(AVG(a.requested_amount), 0) AS average_requested_amount,
                       COALESCE(
                           AVG(EXTRACT(EPOCH FROM (a.decision_at - a.created_at)) / 3600.0)
                               FILTER (WHERE a.decision_at IS NOT NULL),
                           0
                       ) AS average_processing_hours
                FROM applications a
                """ + sqlFilter.where(),
                sqlFilter.parameters(),
                (rs, rowNum) -> new SummaryAmounts(
                        rs.getLong("total_applications"),
                        decimal(rs, "total_requested_amount"),
                        decimal(rs, "total_approved_amount"),
                        decimal(rs, "average_requested_amount"),
                        decimal(rs, "average_processing_hours")
                )
        );

        EnumMap<ApplicationStatus, Long> statusCounts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            statusCounts.put(status, 0L);
        }
        jdbc.query(
                """
                SELECT a.status, COUNT(*) AS application_count
                FROM applications a
                """ + sqlFilter.where() + " GROUP BY a.status",
                sqlFilter.parameters(),
                (rs, rowNum) -> Map.entry(
                        ApplicationStatus.valueOf(rs.getString("status")),
                        rs.getLong("application_count")
                )
        ).forEach(entry -> statusCounts.put(entry.getKey(), entry.getValue()));

        EnumMap<DecisionType, Long> decisionCounts = new EnumMap<>(DecisionType.class);
        for (DecisionType type : DecisionType.values()) {
            decisionCounts.put(type, 0L);
        }
        jdbc.query(
                """
                SELECT d.decision_type, COUNT(*) AS decision_count
                FROM decisions d
                JOIN applications a ON a.id = d.application_id
                """ + sqlFilter.where() + " GROUP BY d.decision_type",
                sqlFilter.parameters(),
                (rs, rowNum) -> Map.entry(
                        DecisionType.valueOf(rs.getString("decision_type")),
                        rs.getLong("decision_count")
                )
        ).forEach(entry -> decisionCounts.put(entry.getKey(), entry.getValue()));

        long completedAiScoring = queryCount(
                """
                SELECT COUNT(DISTINCT a.id)
                FROM application_scores s
                JOIN applications a ON a.id = s.application_id
                """ + sqlFilter.where(),
                sqlFilter.parameters()
        );
        long failedAiTasks = queryCount(
                """
                SELECT COUNT(*)
                FROM ai_processing_jobs j
                JOIN applications a ON a.id = j.application_id
                """ + sqlFilter.where() + " AND j.status IN ('FAILED', 'FAILED_RETRYABLE', 'FAILED_TERMINAL', 'FAILED_OPTIONAL')",
                sqlFilter.parameters()
        );

        return new AnalyticsSummaryResponse(
                amounts.totalApplications(),
                Map.copyOf(statusCounts),
                amounts.totalRequestedAmount(),
                amounts.totalApprovedAmount(),
                amounts.averageRequestedAmount(),
                amounts.averageProcessingHours(),
                decisionCounts.get(DecisionType.APPROVED),
                decisionCounts.get(DecisionType.REJECTED),
                decisionCounts.get(DecisionType.ADDITIONAL_DOCUMENTS_REQUESTED),
                completedAiScoring,
                failedAiTasks
        );
    }

    public PageSlice<StatusMetricResponse> byStatus(
            AnalyticsFilter filter, int offset, int limit
    ) {
        SqlFilter sqlFilter = paged(filter, offset, limit);
        String grouped = """
                SELECT a.status,
                       COUNT(*) AS application_count,
                       COALESCE(SUM(a.requested_amount), 0) AS requested_amount,
                       COALESCE(SUM(a.approved_amount), 0) AS approved_amount
                FROM applications a
                """ + sqlFilter.where() + " GROUP BY a.status";
        List<StatusMetricResponse> content = jdbc.query(
                grouped + """
                 ORDER BY application_count DESC, a.status
                 OFFSET :offset LIMIT :limit
                """,
                sqlFilter.parameters(),
                (rs, rowNum) -> new StatusMetricResponse(
                        ApplicationStatus.valueOf(rs.getString("status")),
                        rs.getLong("application_count"),
                        decimal(rs, "requested_amount"),
                        decimal(rs, "approved_amount")
                )
        );
        return new PageSlice<>(content, countGroups(grouped, sqlFilter.parameters()));
    }

    public PageSlice<RegionMetricResponse> byRegion(
            AnalyticsFilter filter, int offset, int limit
    ) {
        SqlFilter sqlFilter = paged(filter, offset, limit);
        String grouped = """
                SELECT COALESCE(a.region, 'UNSPECIFIED') AS region,
                       COUNT(*) AS application_count,
                       COALESCE(SUM(a.requested_amount), 0) AS requested_amount,
                       COALESCE(SUM(a.approved_amount), 0) AS approved_amount
                FROM applications a
                """ + sqlFilter.where() + " GROUP BY COALESCE(a.region, 'UNSPECIFIED')";
        List<RegionMetricResponse> content = jdbc.query(
                grouped + """
                 ORDER BY application_count DESC, region
                 OFFSET :offset LIMIT :limit
                """,
                sqlFilter.parameters(),
                (rs, rowNum) -> new RegionMetricResponse(
                        rs.getString("region"),
                        rs.getLong("application_count"),
                        decimal(rs, "requested_amount"),
                        decimal(rs, "approved_amount")
                )
        );
        return new PageSlice<>(content, countGroups(grouped, sqlFilter.parameters()));
    }

    public PageSlice<ApplicationTrendResponse> trend(
            AnalyticsFilter filter, int offset, int limit
    ) {
        SqlFilter sqlFilter = paged(filter, offset, limit);
        String grouped = """
                SELECT (a.created_at AT TIME ZONE 'UTC')::date AS application_date,
                       COUNT(*) AS application_count,
                       COALESCE(SUM(a.requested_amount), 0) AS requested_amount
                FROM applications a
                """ + sqlFilter.where()
                + " GROUP BY (a.created_at AT TIME ZONE 'UTC')::date";
        List<ApplicationTrendResponse> content = jdbc.query(
                grouped + """
                 ORDER BY application_date
                 OFFSET :offset LIMIT :limit
                """,
                sqlFilter.parameters(),
                (rs, rowNum) -> new ApplicationTrendResponse(
                        rs.getObject("application_date", java.time.LocalDate.class),
                        rs.getLong("application_count"),
                        decimal(rs, "requested_amount")
                )
        );
        return new PageSlice<>(content, countGroups(grouped, sqlFilter.parameters()));
    }

    public PageSlice<ProcessingTimeResponse> processingTime(
            AnalyticsFilter filter, int offset, int limit
    ) {
        SqlFilter sqlFilter = paged(filter, offset, limit);
        String grouped = """
                SELECT (a.decision_at AT TIME ZONE 'UTC')::date AS decision_date,
                       COUNT(*) AS completed_applications,
                       COALESCE(
                           AVG(EXTRACT(EPOCH FROM (a.decision_at - a.created_at)) / 3600.0),
                           0
                       ) AS average_processing_hours
                FROM applications a
                """ + sqlFilter.where()
                + " AND a.decision_at IS NOT NULL"
                + " GROUP BY (a.decision_at AT TIME ZONE 'UTC')::date";
        List<ProcessingTimeResponse> content = jdbc.query(
                grouped + """
                 ORDER BY decision_date
                 OFFSET :offset LIMIT :limit
                """,
                sqlFilter.parameters(),
                (rs, rowNum) -> new ProcessingTimeResponse(
                        rs.getObject("decision_date", java.time.LocalDate.class),
                        rs.getLong("completed_applications"),
                        decimal(rs, "average_processing_hours")
                )
        );
        return new PageSlice<>(content, countGroups(grouped, sqlFilter.parameters()));
    }

    public PageSlice<RiskCategoryMetricResponse> riskCategories(
            AnalyticsFilter filter, int offset, int limit
    ) {
        SqlFilter sqlFilter = paged(filter, offset, limit);
        String grouped = """
                SELECT COALESCE(s.risk_category, 'UNSCORED') AS risk_category,
                       COUNT(*) AS application_count
                FROM applications a
                LEFT JOIN application_scores s ON s.application_id = a.id
                """ + sqlFilter.where() + " GROUP BY COALESCE(s.risk_category, 'UNSCORED')";
        List<RiskCategoryMetricResponse> content = jdbc.query(
                grouped + """
                 ORDER BY application_count DESC, risk_category
                 OFFSET :offset LIMIT :limit
                """,
                sqlFilter.parameters(),
                (rs, rowNum) -> new RiskCategoryMetricResponse(
                        rs.getString("risk_category"),
                        rs.getLong("application_count")
                )
        );
        return new PageSlice<>(content, countGroups(grouped, sqlFilter.parameters()));
    }

    public DecisionRateResponse decisionRates(AnalyticsFilter filter) {
        SqlFilter sqlFilter = filter(filter);
        return jdbc.queryForObject(
                """
                SELECT
                    COUNT(*) FILTER (WHERE a.status = 'APPROVED') AS approved_count,
                    COUNT(*) FILTER (WHERE a.status = 'REJECTED') AS rejected_count
                FROM applications a
                """ + sqlFilter.where(),
                sqlFilter.parameters(),
                (rs, rowNum) -> {
                    long approved = rs.getLong("approved_count");
                    long rejected = rs.getLong("rejected_count");
                    long total = approved + rejected;
                    java.math.BigDecimal approvalRate = percent(approved, total);
                    java.math.BigDecimal rejectionRate = percent(rejected, total);
                    return new DecisionRateResponse(approved, rejected, total, approvalRate, rejectionRate);
                }
        );
    }

    private long countGroups(String groupedSql, MapSqlParameterSource parameters) {
        return queryCount("SELECT COUNT(*) FROM (" + groupedSql + ") grouped_results", parameters);
    }

    private long queryCount(String sql, MapSqlParameterSource parameters) {
        Long result = jdbc.queryForObject(sql, parameters, Long.class);
        return result == null ? 0 : result;
    }

    private SqlFilter paged(AnalyticsFilter filter, int offset, int limit) {
        SqlFilter sqlFilter = filter(filter);
        sqlFilter.parameters().addValue("offset", offset);
        sqlFilter.parameters().addValue("limit", limit);
        return sqlFilter;
    }

    private SqlFilter filter(AnalyticsFilter filter) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (filter.dateFrom() != null) {
            where.append(" AND a.created_at >= :dateFrom");
            parameters.addValue("dateFrom", java.sql.Timestamp.from(filter.dateFrom()));
        }
        if (filter.dateToExclusive() != null) {
            where.append(" AND a.created_at < :dateToExclusive");
            parameters.addValue(
                    "dateToExclusive",
                    java.sql.Timestamp.from(filter.dateToExclusive())
            );
        }
        if (filter.region() != null) {
            where.append(" AND a.region = :region");
            parameters.addValue("region", filter.region());
        }
        if (filter.status() != null) {
            where.append(" AND a.status = :status");
            parameters.addValue("status", filter.status().name());
        }
        if (filter.productionType() != null) {
            where.append(" AND a.production_type = :productionType");
            parameters.addValue("productionType", filter.productionType());
        }
        if (filter.activityType() != null) {
            where.append(" AND a.activity_type = :activityType");
            parameters.addValue("activityType", filter.activityType().name());
        }
        return new SqlFilter(where.toString(), parameters);
    }

    private static BigDecimal percent(long value, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(value)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }

    private record SqlFilter(String where, MapSqlParameterSource parameters) {
    }

    private record SummaryAmounts(
            long totalApplications,
            BigDecimal totalRequestedAmount,
            BigDecimal totalApprovedAmount,
            BigDecimal averageRequestedAmount,
            BigDecimal averageProcessingHours
    ) {
    }

    public record PageSlice<T>(List<T> content, long totalElements) {
    }
}
