package tv.dumbtv.home;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
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
 */
final class Filter {
    private static final ComponentName BOOT =
            new ComponentName("dnsfilter.android", "dnsfilter.android.BootUpReceiver");
    static final String CHECK = "tv.dumbtv.home.CHECK_FILTER";

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
     * or one the owner started, such as Surfshark: Android runs one VPN at a time, and waking
     * the filter would take the slot back and cut the other one off. Once that VPN is turned
     * off, the next HOME press or the 15-minute check brings the filter back.
     */
    static void wake(Context c) {
        if (vpnUp(c)) return;
        c.sendBroadcast(new Intent().setComponent(BOOT));
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
}
