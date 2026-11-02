package de.reckendrees.systems.tui.expert.commands.main.raw;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;

import de.reckendrees.systems.tui.expert.LauncherActivity;
import de.reckendrees.systems.tui.expert.R;

/** Receives only declared system events and app-created alarm broadcasts. */
public class AutomationReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "tuix_automation_alerts";
    private static final int NOTIFICATION_BASE = 8400;

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        Context app = context.getApplicationContext();
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            automate.scheduleDailyFromPrefs(app);
            automate.updatePollForReceiver(app);
            return;
        }
        if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
            SharedPreferences p = automate.prefs(app);
            if (p.getBoolean("charge_enabled", false)) notify(app, "Charging connected", "Your device is connected to power.", 1);
            return;
        }
        if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
            SharedPreferences p = automate.prefs(app);
            if (p.getBoolean("charge_enabled", false)) notify(app, "Charging disconnected", "Your device is running on battery.", 2);
            return;
        }
        if (automate.ACTION_POLL.equals(action)) {
            checkBattery(app);
            checkNetwork(app);
            return;
        }
        if (automate.ACTION_TIMER.equals(action)) {
            SharedPreferences p = automate.prefs(app);
            String message = intent.getStringExtra("message");
            if (message == null || message.length() == 0) message = p.getString("timer_message", "Your reminder is due.");
            p.edit().putBoolean("timer_enabled", false).remove("timer_message").apply();
            notify(app, "T-UI Expert X reminder", message, 3);
            return;
        }
        if (automate.ACTION_DAILY.equals(action)) {
            SharedPreferences p = automate.prefs(app);
            if (p.getBoolean("daily_enabled", false)) notify(app, "Daily routine", p.getString("daily_message", "Your daily reminder is due."), 4);
        }
    }

    private static void checkBattery(Context context) {
        SharedPreferences p = automate.prefs(context);
        if (!p.getBoolean("battery_enabled", false)) return;
        Intent battery = context.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return;
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0) return;
        int percent = Math.round(level * 100f / scale);
        int threshold = p.getInt("battery_threshold", 20);
        boolean fired = p.getBoolean("battery_fired", false);
        if (percent <= threshold && !fired) {
            notify(context, "Low battery", "Battery level is " + percent + "% (alert threshold " + threshold + "%).", 5);
            p.edit().putBoolean("battery_fired", true).apply();
        } else if (percent >= threshold + 4 && fired) {
            p.edit().putBoolean("battery_fired", false).apply();
        }
    }

    @SuppressWarnings("deprecation")
    private static void checkNetwork(Context context) {
        SharedPreferences p = automate.prefs(context);
        if (!p.getBoolean("wifi_enabled", false)) return;
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = manager == null ? null : manager.getActiveNetworkInfo();
        String state = info != null && info.isConnected() ? info.getTypeName() + ":connected" : "disconnected";
        String previous = p.getString("last_network_state", null);
        if (previous != null && !previous.equals(state)) {
            notify(context, "Network status changed", "Connection is now " + state.replace(':', ' ') + ".", 6);
        }
        p.edit().putString("last_network_state", state).apply();
    }

    private static void notify(Context context, String title, String message, int id) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Automation alerts", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("User-configured T-UI Expert X reminders and device alerts");
            manager.createNotificationChannel(channel);
        }
        Intent launch = new Intent(context, LauncherActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent content = PendingIntent.getActivity(context, NOTIFICATION_BASE + id, launch, flags);
        Notification.Builder builder = new Notification.Builder(context)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(content)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());
        if (Build.VERSION.SDK_INT >= 26) builder.setChannelId(CHANNEL_ID);
        else builder.setDefaults(Notification.DEFAULT_ALL);
        try {
            manager.notify(NOTIFICATION_BASE + id, builder.build());
        } catch (SecurityException ignored) {
            // Notifications can be disabled or denied by the user on newer Android versions.
        }
    }
}
