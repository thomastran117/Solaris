package backend.configurations.environment;

import backend.models.enums.RiskMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * All tunables for the fraud / risk engine. Defaults are safe for SHADOW mode;
 * override via env vars (see application.properties).
 */
@Configuration
@ConfigurationProperties(prefix = "app.risk")
public class RiskProperties {

    /** Master kill switch. When false, the engine returns ALLOW unconditionally. */
    private boolean enabled = true;

    /** SHADOW (default): log-only. ENFORCE: honour non-ALLOW decisions. */
    private RiskMode mode = RiskMode.SHADOW;

    /** Score ≥ verifyThreshold → VERIFY. */
    private int verifyThreshold = 40;

    /** Score ≥ blockThreshold → BLOCK. */
    private int blockThreshold = 70;

    /** Segment id that short-circuits all checks (ALLOW). Null disables. */
    private UUID vipSegmentId;

    private final FailedPayment failedPayment = new FailedPayment();
    private final Device device = new Device();
    private final AccountAge accountAge = new AccountAge();
    private final Coupon coupon = new Coupon();
    private final ReturnPolicy returnPolicy = new ReturnPolicy();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public RiskMode getMode() { return mode != null ? mode : RiskMode.SHADOW; }
    public void setMode(RiskMode mode) { this.mode = mode; }

    public int getVerifyThreshold() { return verifyThreshold; }
    public void setVerifyThreshold(int v) { this.verifyThreshold = Math.max(0, Math.min(1000, v)); }

    public int getBlockThreshold() { return blockThreshold; }
    public void setBlockThreshold(int v) { this.blockThreshold = Math.max(0, Math.min(1000, v)); }

    public UUID getVipSegmentId() { return vipSegmentId; }
    public void setVipSegmentId(UUID vipSegmentId) { this.vipSegmentId = vipSegmentId; }

    public FailedPayment getFailedPayment() { return failedPayment; }
    public Device getDevice() { return device; }
    public AccountAge getAccountAge() { return accountAge; }
    public Coupon getCoupon() { return coupon; }
    public ReturnPolicy getReturnPolicy() { return returnPolicy; }

    public static class FailedPayment {
        private int windowMinutes = 60;
        private int mediumCount = 2;
        private int highCount = 5;
        private int ipWindowHours = 24;
        private int ipHighCount = 10;

        public int getWindowMinutes() { return windowMinutes; }
        public void setWindowMinutes(int v) { this.windowMinutes = Math.max(1, Math.min(10_080, v)); }
        public int getMediumCount() { return mediumCount; }
        public void setMediumCount(int v) { this.mediumCount = Math.max(1, Math.min(1000, v)); }
        public int getHighCount() { return highCount; }
        public void setHighCount(int v) { this.highCount = Math.max(1, Math.min(1000, v)); }
        public int getIpWindowHours() { return ipWindowHours; }
        public void setIpWindowHours(int v) { this.ipWindowHours = Math.max(1, Math.min(720, v)); }
        public int getIpHighCount() { return ipHighCount; }
        public void setIpHighCount(int v) { this.ipHighCount = Math.max(1, Math.min(10_000, v)); }
    }

    public static class Device {
        private int distinctUsersMedium = 2;
        private int distinctUsersHigh = 4;
        private int burstWindowMinutes = 60;

        public int getDistinctUsersMedium() { return distinctUsersMedium; }
        public void setDistinctUsersMedium(int v) { this.distinctUsersMedium = Math.max(1, Math.min(1000, v)); }
        public int getDistinctUsersHigh() { return distinctUsersHigh; }
        public void setDistinctUsersHigh(int v) { this.distinctUsersHigh = Math.max(1, Math.min(1000, v)); }
        public int getBurstWindowMinutes() { return burstWindowMinutes; }
        public void setBurstWindowMinutes(int v) { this.burstWindowMinutes = Math.max(1, Math.min(10_080, v)); }
    }

    public static class AccountAge {
        private int newMinutes = 60;

        public int getNewMinutes() { return newMinutes; }
        public void setNewMinutes(int v) { this.newMinutes = Math.max(1, Math.min(10_080, v)); }
    }

    public static class Coupon {
        private int perUser24hHigh = 5;
        private int perIp24hHigh = 10;
        private int firstOrderPctThreshold = 20;

        public int getPerUser24hHigh() { return perUser24hHigh; }
        public void setPerUser24hHigh(int v) { this.perUser24hHigh = Math.max(1, Math.min(10_000, v)); }
        public int getPerIp24hHigh() { return perIp24hHigh; }
        public void setPerIp24hHigh(int v) { this.perIp24hHigh = Math.max(1, Math.min(10_000, v)); }
        public int getFirstOrderPctThreshold() { return firstOrderPctThreshold; }
        public void setFirstOrderPctThreshold(int v) { this.firstOrderPctThreshold = Math.max(1, Math.min(100, v)); }
    }

    public static class ReturnPolicy {
        private double rateHigh = 0.5;
        private int rateMinDenominator = 5;
        private int fastMinutes = 60;
        private int fastHighValueUsd = 500;
        private int windowDays = 14;
        private int repeatCountHigh = 3;

        public double getRateHigh() { return rateHigh; }
        public void setRateHigh(double v) { this.rateHigh = Math.max(0.0, Math.min(1.0, v)); }
        public int getRateMinDenominator() { return rateMinDenominator; }
        public void setRateMinDenominator(int v) { this.rateMinDenominator = Math.max(1, Math.min(10_000, v)); }
        public int getFastMinutes() { return fastMinutes; }
        public void setFastMinutes(int v) { this.fastMinutes = Math.max(1, Math.min(10_080, v)); }
        public int getFastHighValueUsd() { return fastHighValueUsd; }
        public void setFastHighValueUsd(int v) { this.fastHighValueUsd = Math.max(0, Math.min(1_000_000, v)); }
        public int getWindowDays() { return windowDays; }
        public void setWindowDays(int v) { this.windowDays = Math.max(1, Math.min(365, v)); }
        public int getRepeatCountHigh() { return repeatCountHigh; }
        public void setRepeatCountHigh(int v) { this.repeatCountHigh = Math.max(1, Math.min(10_000, v)); }
    }
}
