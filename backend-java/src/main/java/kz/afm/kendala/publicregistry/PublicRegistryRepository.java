package kz.afm.kendala.publicregistry;

import java.sql.Timestamp;
import java.util.List;
import kz.afm.kendala.application.enums.ActivityType;
import kz.afm.kendala.application.enums.ApplicantCategory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PublicRegistryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PublicRegistryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageSlice findApproved(
            PublicRegistryFilter filter,
            int offset,
            int limit,
            String sortColumn,
            boolean ascending
    ) {
        SqlFilter sqlFilter = filter(filter);
        sqlFilter.parameters().addValue("offset", offset);
        sqlFilter.parameters().addValue("limit", limit);
        String direction = ascending ? " ASC" : " DESC";
        String query = """
                SELECT a.application_number,
                       a.region,
                       a.activity_type,
                       a.production_type,
                       COALESCE(a.approved_amount, a.requested_amount) AS approved_amount,
                       COALESCE(a.decision_at, a.updated_at) AS decision_date,
                       a.applicant_category,
                       s.risk_category AS scoring_category
                FROM applications a
                LEFT JOIN application_scores s ON s.application_id = a.id
                """ + sqlFilter.where()
                + " ORDER BY " + sortColumn + direction + ", a.application_number ASC"
                + " OFFSET :offset LIMIT :limit";
        List<PublicApprovedApplicationResponse> content = jdbc.query(
                query,
                sqlFilter.parameters(),
                (rs, rowNum) -> new PublicApprovedApplicationResponse(
                        rs.getString("application_number"),
                        rs.getString("region"),
                        ActivityType.valueOf(rs.getString("activity_type")),
                        rs.getString("production_type"),
                        rs.getBigDecimal("approved_amount"),
                        rs.getTimestamp("decision_date").toInstant(),
                        ApplicantCategory.valueOf(rs.getString("applicant_category")),
                        rs.getString("scoring_category")
                )
        );
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM applications a" + sqlFilter.where(),
                sqlFilter.parameters(),
                Long.class
        );
        return new PageSlice(content, total == null ? 0 : total);
    }

    private SqlFilter filter(PublicRegistryFilter filter) {
        StringBuilder where = new StringBuilder(" WHERE a.status = 'APPROVED' AND a.is_public = TRUE");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (filter.region() != null) {
            where.append(" AND a.region = :region");
            parameters.addValue("region", filter.region());
        }
        if (filter.productionType() != null) {
            where.append(" AND a.production_type = :productionType");
            parameters.addValue("productionType", filter.productionType());
        }
        if (filter.dateFrom() != null) {
            where.append(" AND COALESCE(a.decision_at, a.updated_at) >= :dateFrom");
            parameters.addValue("dateFrom", Timestamp.from(filter.dateFrom()));
        }
        if (filter.dateToExclusive() != null) {
            where.append(" AND COALESCE(a.decision_at, a.updated_at) < :dateToExclusive");
            parameters.addValue("dateToExclusive", Timestamp.from(filter.dateToExclusive()));
        }
        if (filter.minAmount() != null) {
            where.append(" AND COALESCE(a.approved_amount, a.requested_amount) >= :minAmount");
            parameters.addValue("minAmount", filter.minAmount());
        }
        if (filter.maxAmount() != null) {
            where.append(" AND COALESCE(a.approved_amount, a.requested_amount) <= :maxAmount");
            parameters.addValue("maxAmount", filter.maxAmount());
        }
        return new SqlFilter(where.toString(), parameters);
    }

    private record SqlFilter(String where, MapSqlParameterSource parameters) {
    }

    public record PageSlice(
            List<PublicApprovedApplicationResponse> content,
            long totalElements
    ) {
    }
}
