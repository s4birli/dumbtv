package tv.dumbtv.home;

/** Host-side checks for the family screen's sunrise, sunset and backdrop choice. */
public final class GunesTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    private static boolean near(double minutes, int h, int m, double tolerance) {
        return Math.abs(minutes - (h * 60 + m)) <= tolerance;
    }

    public static void main(String[] args) {
        // Reading, 9 November 2026 (GMT): sunrise about 07:14, sunset about 16:24.
        double[] nov = Gunes.times(2026, 11, 9, Gunes.READING_LAT, Gunes.READING_LON, 0);
        check(nov != null, "november has a sunrise");
        check(near(nov[0], 7, 14, 8), "november sunrise " + nov[0]);
        check(near(nov[1], 16, 24, 8), "november sunset " + nov[1]);

        // Reading, 21 June 2026 (BST, UTC+1): sunrise about 04:47, sunset about 21:25.
        double[] jun = Gunes.times(2026, 6, 21, Gunes.READING_LAT, Gunes.READING_LON, 1);
        check(near(jun[0], 4, 47, 8), "june sunrise " + jun[0]);
        check(near(jun[1], 21, 25, 8), "june sunset " + jun[1]);

        double rise = 7 * 60 + 14, set = 16 * 60 + 24;
        check(Gunes.phase(2 * 60 + 32, rise, set) == Gunes.GECE, "02:32 is night");
        check(Gunes.phase(7 * 60 + 45, rise, set) == Gunes.SABAH, "07:45 is morning");
        check(Gunes.phase(13 * 60 + 10, rise, set) == Gunes.GUNDUZ, "13:10 is day");
        check(Gunes.phase(16 * 60, rise, set) == Gunes.AKSAM, "16:00 is sunset");
        check(Gunes.phase(17 * 60 + 30, rise, set) == Gunes.GECE, "17:30 is night");

        // 2026-01-01 is Julian day 2461041.5 at 0h UTC.
        check(Gunes.julianDay(2026, 1, 1) == 2461041.5, "julian day");
        System.out.println("GunesTest passed");
    }
}
