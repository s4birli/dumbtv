package tv.dumbtv.home;

import java.util.Calendar;

/**
 * Sunrise and sunset (NOAA solar calculator equations) and which of the four backdrops the
 * family screen shows: morning, day, sunset or night. Plain Java, so it is checked on the host
 * without a device (tests/GunesTest.java). No network: the position is fixed to Reading.
 */
final class Gunes {
    static final int SABAH = 0, GUNDUZ = 1, AKSAM = 2, GECE = 3;
    static final double READING_LAT = 51.4543, READING_LON = -0.9781;

    private Gunes() {}

    /**
     * Sunrise and sunset in minutes after local midnight on that date, for a place at
     * {@code lat}/{@code lon} (east positive) whose clock is {@code tzHours} ahead of UTC.
     * Returns null for a polar day or night.
     */
    static double[] times(int year, int month, int day, double lat, double lon, double tzHours) {
        double jd = julianDay(year, month, day) + 0.5 - tzHours / 24.0;
        double t = (jd - 2451545.0) / 36525.0;
        double l0 = mod360(280.46646 + t * (36000.76983 + t * 0.0003032));
        double m = 357.52911 + t * (35999.05029 - 0.0001537 * t);
        double e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t);
        double mr = Math.toRadians(m);
        double c = Math.sin(mr) * (1.914602 - t * (0.004817 + 0.000014 * t))
                + Math.sin(2 * mr) * (0.019993 - 0.000101 * t)
                + Math.sin(3 * mr) * 0.000289;
        double omega = 125.04 - 1934.136 * t;
        double lambda = l0 + c - 0.00569 - 0.00478 * Math.sin(Math.toRadians(omega));
        double eps0 = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60;
        double eps = eps0 + 0.00256 * Math.cos(Math.toRadians(omega));
        double decl = Math.asin(Math.sin(Math.toRadians(eps)) * Math.sin(Math.toRadians(lambda)));
        double y = Math.tan(Math.toRadians(eps / 2));
        y *= y;
        double l0r = Math.toRadians(l0);
        double eqTime = 4 * Math.toDegrees(y * Math.sin(2 * l0r) - 2 * e * Math.sin(mr)
                + 4 * e * y * Math.sin(mr) * Math.cos(2 * l0r)
                - 0.5 * y * y * Math.sin(4 * l0r) - 1.25 * e * e * Math.sin(2 * mr));
        double latr = Math.toRadians(lat);
        double cosHa = Math.cos(Math.toRadians(90.833)) / (Math.cos(latr) * Math.cos(decl))
                - Math.tan(latr) * Math.tan(decl);
        if (cosHa < -1 || cosHa > 1) return null;
        double ha = Math.toDegrees(Math.acos(cosHa));
        double noon = 720 - 4 * lon - eqTime + tzHours * 60;
        return new double[] {noon - 4 * ha, noon + 4 * ha};
    }

    /** The backdrop for {@code minute} (after local midnight) on a day with that sunrise and sunset. */
    static int phase(double minute, double rise, double set) {
        if (minute >= rise - 30 && minute < rise + 120) return SABAH;
        if (minute >= rise + 120 && minute < set - 60) return GUNDUZ;
        if (minute >= set - 60 && minute < set + 40) return AKSAM;
        return GECE;
    }

    /** The backdrop for this moment in Reading, in the device's own time zone. */
    static int phase(Calendar now) {
        double tz = (now.get(Calendar.ZONE_OFFSET) + now.get(Calendar.DST_OFFSET)) / 3600000.0;
        double[] t = times(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1,
                now.get(Calendar.DAY_OF_MONTH), READING_LAT, READING_LON, tz);
        double minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (t == null) return GUNDUZ;
        return phase(minute, t[0], t[1]);
    }

    static double julianDay(int year, int month, int day) {
        if (month <= 2) {
            year -= 1;
            month += 12;
        }
        int a = year / 100;
        int b = 2 - a + a / 4;
        return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5;
    }

    private static double mod360(double v) {
        double r = v % 360;
        return r < 0 ? r + 360 : r;
    }
}
