package io.github.alejorom.demandflow.tariffs.domain;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Period rules shared by creation and editing, independently testable without Spring. */
@Entity
@Table(name = "tariff_period", schema = "demandflow")
public class TariffPeriod {
    // pgJDBC maps earlier LocalDates to -infinity; use the finite range of the whole JDBC path.
    private static final LocalDate MIN_DATE = LocalDate.of(-4712, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(5874897, 12, 31);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String label;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE)
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE)
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Column(nullable = false)
    private boolean active = true;
    @Version
    @Column(nullable = false)
    private Long version;

    protected TariffPeriod() { }

    public TariffPeriod(String label, LocalDate startDate, LocalDate endDate) {
        edit(label, startDate, endDate);
    }

    public static String normalizeLabel(String label) {
        if (label == null) {
            throw new IllegalArgumentException("Period label is required");
        }
        String normalized = label.strip();
        if (normalized.isBlank() || normalized.codePointCount(0, normalized.length()) > 100) {
            throw new IllegalArgumentException("Period label must contain 1 to 100 characters");
        }
        return normalized;
    }

    public static LocalDate parseDate(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Period date is required");
        }
        try {
            LocalDate date = LocalDate.parse(value);
            validateDate(date);
            return date;
        } catch (DateTimeParseException failure) {
            throw new IllegalArgumentException("Period date must be a valid ISO date");
        }
    }

    private static void validateDate(LocalDate date) {
        if (date == null || date.isBefore(MIN_DATE) || date.isAfter(MAX_DATE)) {
            throw new IllegalArgumentException("Period date must be representable through JDBC and PostgreSQL");
        }
    }

    public boolean edit(String label, LocalDate startDate, LocalDate endDate) {
        String normalized = normalizeLabel(label);
        validateDate(startDate);
        validateDate(endDate);
        // GiST uses an inclusive daterange, whose canonical upper bound is end + 1 day.
        if (endDate.equals(MAX_DATE) || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Period interval must be ordered and representable inclusively");
        }
        boolean changed = !normalized.equals(this.label) || !startDate.equals(this.startDate)
                || !endDate.equals(this.endDate);
        if (changed) {
            this.label = normalized;
            this.startDate = startDate;
            this.endDate = endDate;
        }
        return changed;
    }

    public void deactivate() {
        if (!active) {
            throw new IllegalStateException("Period is already inactive");
        }
        active = false;
    }

    public void reactivate() {
        if (active) {
            throw new IllegalStateException("Period is already active");
        }
        edit(label, startDate, endDate);
        active = true;
    }

    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public String getLabel() { return label; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isActive() { return active; }
}
