package io.depguard.eol;

import java.time.Instant;
import java.time.LocalDate;

/** EOL data for one product/cycle, as returned by endoflife.date (or derived from it). */
public record EolInfo(LocalDate eolDate, boolean isEol, EolStatus status, Instant dataSourceFetchedAt) {

    /**
     * Derives {@link EolStatus} from the endoflife.date API response.
     *
     * <p>The endoflife.date API returns the {@code eol} field as either:
     * <ul>
     *   <li>A date string (e.g. {@code "2023-11-24"}) — the cycle reached EOL on that date.</li>
     *   <li>{@code false} (serialised as {@code null} after JSON parsing, or absent) — not yet EOL.</li>
     * </ul>
     *
     * <p>Status derivation rules (in order):
     * <ol>
     *   <li>If {@code eol} date is present and is today or in the past → {@link EolStatus#EOL}.</li>
     *   <li>If {@code eol} date is present and is in the future → {@link EolStatus#MAINTENANCE}
     *       (still receiving security fixes but no new features).</li>
     *   <li>If {@code eol} date is absent/null → {@link EolStatus#SUPPORTED}.</li>
     * </ol>
     *
     * <p>Note: the {@code cycle} field in the response is the version string (e.g. {@code "2.7"}),
     * not a lifecycle state — it must never be used for status derivation.
     */
    public static EolInfo from(EolApiResponse response, Instant fetchedAt) {
        LocalDate today = LocalDate.now();
        LocalDate eolDate = response.eol();

        EolStatus status;
        boolean isEol;

        if (eolDate != null && !eolDate.isAfter(today)) {
            // EOL date is today or in the past — cycle has ended
            status = EolStatus.EOL;
            isEol = true;
        } else if (eolDate != null
                || (response.support() != null && "maintenance".equalsIgnoreCase(response.support()))) {
            // EOL date is in the future or support indicates maintenance — still receiving fixes
            status = EolStatus.MAINTENANCE;
            isEol = false;
        } else {
            // No EOL date set and not in maintenance — actively supported
            status = EolStatus.SUPPORTED;
            isEol = false;
        }

        return new EolInfo(eolDate, isEol, status, fetchedAt);
    }

    /** DTO for {@code GET https://endoflife.date/api/{product}/{cycle}.json}. */
    public record EolApiResponse(
            String cycle,
            LocalDate release,
            LocalDate eol,
            String latest,
            LocalDate latestRelease,
            String support,
            String docs,
            String blog,
            String discuss,
            String stackoverflow) {}
}
