package tv.dumbtv.home;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.SystemClock;

/**
 * Brings personalDNSfilter back after its process died. Android does not restart a VPN whose
 * process was killed (lmkd under memory pressure, a crash), so until something starts the app
 * again Amazon's hosts resolve normally (#162).
 *
 * The filter's BootUpReceiver starts its VPN service without showing the app, and returns at once
 * when the service is already running, so waking it is safe at any time. It accepts only senders
 * holding RECEIVE_BOOT_COMPLETED, the one permission dumbtv home asks for on Fire OS 7 and later.
 * A force-stopped filter (dnsfilter.sh replacing its files) gets no broadcast, which is wanted.
 *
 * Aile fork: the filter can lend its VPN slot to Surfshark. Android runs one VPN at a time and
 * Surfshark refuses to connect while another is up ("Another VPN was detected").
 */
final class Filter {
    private static final ComponentName BOOT =
            new ComponentName("dnsfilter.android", "dnsfilter.android.BootUpReceiver");
    static final String CHECK = "tv.dumbtv.home.CHECK_FILTER";

    private static final String PREFS = "aile_vpn";
    /** When the slot was lent to Surfshark, 0 when it was not. */
    private static final String LENT_AT = "lent_at";
    /** Whether a VPN, Surfshark's, was seen up since the slot was lent. */
    private static final String SEEN = "seen";
    /** How long Surfshark gets to connect before the filter takes the slot back. */
    private static final long GRACE = 10 * 60 * 1000L;

    /** Wakes the filter now and every 15 minutes, including after our own process was killed. */
    static void watch(Context c) {
        wake(c);
        PendingIntent check = PendingIntent.getBroadcast(c, 0,
                new Intent(CHECK).setClass(c, BootReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarms = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        alarms.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES, check);
    }

    /**
     * Wakes the filter unless a VPN is already up. That VPN is the filter itself (nothing to do)
     * or Surfshark: waking the filter would take the slot back and cut Surfshark off.
     *
     * While the slot is lent, the filter stays down until Surfshark was seen up and is down
     * again, or until Surfshark had {@link #GRACE} to connect and did not.
     */
    static void wake(Context c) {
        SharedPreferences p = prefs(c);
        long lentAt = p.getLong(LENT_AT, 0);
        boolean up = vpnUp(c);
        if (lentAt != 0) {
            boolean seen = p.getBoolean(SEEN, false);
            long age = System.currentTimeMillis() - lentAt;
            if (up) {
                // In the first seconds the VPN still up may be the filter's, on its way out.
                if (!seen && age > 5000) p.edit().putBoolean(SEEN, true).apply();
                return;
            }
            if (!seen && age >= 0 && age < GRACE) return;
            p.edit().remove(LENT_AT).remove(SEEN).apply();
        } else if (up) {
            return;
        }
        c.sendBroadcast(new Intent().setComponent(BOOT));
    }

    /**
     * Frees the VPN slot for Surfshark. Preparing VPN for this app, which install.sh pre-consents
     * with the ACTIVATE_VPN app-op, makes Android revoke the VPN that holds the slot: the
     * filter's. Returns false when the slot could not be freed (no pre-consent).
     */
    static boolean lend(Context c) {
        SharedPreferences p = prefs(c);
        // Lent already: the VPN that is up, if any, is Surfshark's. Keep it.
        if (p.getLong(LENT_AT, 0) != 0 && vpnUp(c)) return true;
        if (vpnUp(c)) {
            try {
                if (VpnService.prepare(c) != null) return false;
            } catch (RuntimeException e) {
                return false;
            }
        }
        p.edit().putLong(LENT_AT, System.currentTimeMillis()).putBoolean(SEEN, false).apply();
        return true;
    }

    /** True while the slot is lent to Surfshark: the filter being off is then expected. */
    static boolean lent(Context c) {
        return prefs(c).getLong(LENT_AT, 0) != 0;
    }

    /** After a reboot the filter starts on its own, so a loan from before is over. */
    static void forget(Context c) {
        prefs(c).edit().remove(LENT_AT).remove(SEEN).apply();
    }

    /** True when any VPN is up. Fire OS 7 and later lack the permission, so they always wake. */
    static boolean vpnUp(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
            }
        } catch (SecurityException e) {
            // No ACCESS_NETWORK_STATE (Fire OS 7 and later): behave as before and wake.
        }
        return false;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
