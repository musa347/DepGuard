package io.depguard.eol;

import io.depguard.shared.AssertUtil;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * EOL enrichment result for one dependency in one scan.
 *
 * <p>The primary key is the combination of the scanned dependency and the owning scan; each dependency
 * has at most one EOL record per scan. The entity carries a {@link jakarta.persistence.Version} column
 * so that concurrent enrichment attempts are detected rather than silently overwritten.
 */
@Entity
@Table(name = "eol_records")
public class EolRecord {

    @EmbeddedId
    @AttributeOverrides({
        @AttributeOverride(
                name = "dependencyId",
                column = @Column(name = "dependency_id", nullable = false, length = 26)),
        @AttributeOverride(name = "scanId", column = @Column(name = "scan_id", nullable = false, length = 26))
    })
    private EolRecordId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EolStatus status;

    @Column(name = "eol_date")
    private LocalDate eolDate;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "data_source_fetched_at", nullable = false)
    private Instant dataSourceFetchedAt;

    @jakarta.persistence.Version
    @Column(name = "version_col", nullable = false)
    private int versionCol;

    /** Required by JPA. */
    protected EolRecord() {}

    EolRecord(EolRecordId id, EolStatus status, LocalDate eolDate, String source, Instant dataSourceFetchedAt) {
        this.id = AssertUtil.requireNotNull(id, "EOL record id must not be null");
        this.status = AssertUtil.requireNotNull(status, "EOL status must not be null");
        this.source = AssertUtil.requireNotBlank(source, "EOL source must not be blank");
        this.dataSourceFetchedAt = AssertUtil.requireNotNull(dataSourceFetchedAt, "fetched-at must not be null");
        this.eolDate = eolDate;
    }

    public static EolRecord of(EolRecordId id, EolInfo info, String source) {
        if (info != null) {
            return new EolRecord(id, info.status(), info.eolDate(), source, info.dataSourceFetchedAt());
        }
        // info is null — caller did not get data from the API; record as UNKNOWN/NO_MAPPING or UNKNOWN/API
        return new EolRecord(id, EolStatus.UNKNOWN, null, source, Instant.now());
    }

    /** Creates an UNKNOWN record directly (no API data available). */
    public static EolRecord of(EolRecordId id, EolStatus status, String source, Instant fetchedAt) {
        return new EolRecord(id, status, null, source, fetchedAt);
    }

    public EolRecordId getId() {
        return id;
    }

    public EolStatus getStatus() {
        return status;
    }

    public LocalDate getEolDate() {
        return eolDate;
    }

    public String getSource() {
        return source;
    }

    public Instant getDataSourceFetchedAt() {
        return dataSourceFetchedAt;
    }

    public int getVersionCol() {
        return versionCol;
    }
}
