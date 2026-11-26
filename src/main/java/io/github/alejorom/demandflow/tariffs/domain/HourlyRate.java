package io.github.alejorom.demandflow.tariffs.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "hourly_rate", schema = "demandflow")
public class HourlyRate {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_id", nullable = false, updatable = false)
    private TariffPeriod period;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_id", nullable = false, updatable = false)
    private RequirementType type;
    @Column(name = "amount_cop", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountCop;
    @Column(nullable = false)
    private boolean active = true;
    @Version
    @Column(nullable = false)
    private Long version;

    protected HourlyRate() { }

    public HourlyRate(TariffPeriod period, RequirementType type, BigDecimal amountCop) {
        if (period == null || type == null) {
            throw new IllegalArgumentException("Rate period and type are required");
        }
        this.period = period;
        this.type = type;
        this.amountCop = validateAmount(amountCop);
    }

    public static BigDecimal parseAmount(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Rate amount is required");
        }
        try {
            return validateAmount(new BigDecimal(value));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Rate amount must be decimal");
        }
    }

    public static BigDecimal validateAmount(BigDecimal value) {
        if (value == null || value.scale() > 2 || value.signum() <= 0 || value.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Rate amount must be positive, within NUMERIC(15,2), with at most two decimals");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public boolean correctAmount(BigDecimal amountCop) {
        BigDecimal validated = validateAmount(amountCop);
        if (this.amountCop.compareTo(validated) == 0) {
            return false;
        }
        this.amountCop = validated;
        return true;
    }

    public void deactivate() {
        if (!active) {
            throw new CatalogConflictException(CatalogConflictException.Reason.REDUNDANT_STATE);
        }
        active = false;
    }

    public void reactivate() {
        if (active) {
            throw new CatalogConflictException(CatalogConflictException.Reason.REDUNDANT_STATE);
        }
        validateAmount(amountCop);
        active = true;
    }

    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public TariffPeriod getPeriod() { return period; }
    public RequirementType getType() { return type; }
    public BigDecimal getAmountCop() { return amountCop; }
    public boolean isActive() { return active; }
}
