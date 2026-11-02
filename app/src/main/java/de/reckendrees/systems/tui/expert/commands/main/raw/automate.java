package de.reckendrees.systems.tui.expert.commands.main.raw;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.reckendrees.systems.tui.expert.R;
import de.reckendrees.systems.tui.expert.commands.CommandAbstraction;
import de.reckendrees.systems.tui.expert.commands.ExecutePack;
import de.reckendrees.systems.tui.expert.commands.main.MainPack;
import de.reckendrees.systems.tui.expert.commands.main.Param;
import de.reckendrees.systems.tui.expert.commands.main.specific.ParamCommand;
import de.reckendrees.systems.tui.expert.tuils.Tuils;

/**
 * T-UI Expert X Smart Automation Engine.
 * Automations are explicit, user-configured and reversible. This command never
 * executes arbitrary shell commands or changes radio/system settings.
 */
public class automate extends ParamCommand {

    static final String PREFS = "tuix_automation";
    static final String ACTION_POLL = "com.thedarkgeekdc.tuiexpert.AUTOMATION_POLL";
    static final String ACTION_TIMER = "com.thedarkgeekdc.tuiexpert.AUTOMATION_TIMER";
    static final String ACTION_DAILY = "com.thedarkgeekdc.tuiexpert.AUTOMATION_DAILY";
    static final String ACTION_POWER_CONNECTED = Intent.ACTION_POWER_CONNECTED;
    static final String ACTION_POWER_DISCONNECTED = Intent.ACTION_POWER_DISCONNECTED;
    static final String ACTION_BOOT = Intent.ACTION_BOOT_COMPLETED;
    static final int REQUEST_POLL = 7310;
    static final int REQUEST_TIMER = 7311;
    static final int REQUEST_DAILY = 7312;
    static final long POLL_INTERVAL_MS = 15L * 60L * 1000L;

    private enum AutomationParam implements Param {
        status {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return status(pack.context); }
        },
        battery {
            @Override public int[] args() { return new int[]{CommandAbstraction.INT}; }
            @Override public String exec(ExecutePack pack) {
                int threshold = pack.getInt();
                if (threshold < 5 || threshold > 50) return "Battery alert threshold must be between 5 and 50 percent.";
                prefs(pack.context).edit().putBoolean("battery_enabled", true).putInt("battery_threshold", threshold).putBoolean("battery_fired", false).apply();
                updatePoll(pack.context);
                return "Battery alert enabled at " + threshold + "%. Checks run every 15 minutes; alert fires once per low-battery episode.";
            }
            @Override public String onArgNotFound(ExecutePack pack, int index) { return "Enter a threshold from 5 to 50, e.g. automate -battery 20"; }
        },
        batteryoff {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) {
                prefs(pack.context).edit().putBoolean("battery_enabled", false).apply();
                updatePoll(pack.context);
                return "Battery alerts disabled.";
            }
        },
        chargeon {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) {
                prefs(pack.context).edit().putBoolean("charge_enabled", true).apply();
                return "Charging connect/disconnect alerts enabled.";
            }
        },
        chargeoff {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) {
                prefs(pack.context).edit().putBoolean("charge_enabled", false).apply();
                return "Charging alerts disabled.";
            }
        },
        wifion {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) {
                prefs(pack.context).edit().putBoolean("wifi_enabled", true).apply();
                updatePoll(pack.context);
                return "Network-change alerts enabled. Connectivity is checked every 15 minutes; only connection type/state is observed, not Wi-Fi names.";
            }
        },
        wifioff {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) {
                prefs(pack.context).edit().putBoolean("wifi_enabled", false).apply();
                updatePoll(pack.context);
                return "Network-change alerts disabled.";
            }
        },
        timer {
            @Override public int[] args() { return new int[]{CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT}; }
            @Override public String exec(ExecutePack pack) {
                int minutes = pack.getInt();
                String message = cleanMessage(pack.getString());
                if (minutes < 1 || minutes > 10080) return "Timer must be between 1 minute and 7 days.";
                if (message.length() == 0) message = "Your T-UI Expert X reminder is due.";
                scheduleTimer(pack.context, minutes, message);
                return "Reminder scheduled in " + minutes + " minute(s).";
            }
            @Override public String onNotArgEnough(ExecutePack pack, int n) { return pack.context.getString(R.string.help_automate); }
        },
        daily {
            @Override public int[] args() { return new int[]{CommandAbstraction.NO_SPACE_STRING, CommandAbstraction.PLAIN_TEXT}; }
            @Override public String exec(ExecutePack pack) {
                String time = pack.getString();
                String message = cleanMessage(pack.getString());
                int[] hm = parseTime(time);
                if (hm == null) return "Use 24-hour time HH:mm, e.g. automate -daily 08:30 Morning routine";
                if (message.length() == 0) return "Enter a short reminder message.";
                scheduleDaily(pack.context, hm[0], hm[1], message);
                return "Daily reminder saved for " + time + ". It will be restored after reboot.";
            }
            @Override public String onNotArgEnough(ExecutePack pack, int n) { return pack.context.getString(R.string.help_automate); }
        },
        cancel {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) {
                cancelTimer(pack.context);
                cancelDaily(pack.context);
                return "Pending timer and daily reminder cancelled. Battery/charging/network alerts are unchanged.";
            }
        },
        alloff {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) {
                prefs(pack.context).edit().putBoolean("battery_enabled", false).putBoolean("charge_enabled", false)
                        .putBoolean("wifi_enabled", false).apply();
                updatePoll(pack.context);
                cancelTimer(pack.context);
                cancelDaily(pack.context);
                return "All T-UI Expert X automations, alerts and reminders are disabled.";
            }
        },
        backup {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return createBackup(pack.context); }
        },
        help {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return pack.context.getString(R.string.help_automate); }
        };

        static AutomationParam get(String value) {
            if (value == null) return null;
            String normalized = value.toLowerCase(Locale.US);
            for (AutomationParam param : values()) if (normalized.endsWith(param.label())) return param;
            return null;
        }
        static String[] labels() {
            AutomationParam[] values = values();
            String[] labels = new String[values.length];
            for (int i = 0; i < values.length; i++) labels[i] = values[i].label();
            return labels;
        }
        @Override public String label() { return Tuils.MINUS + name(); }
        @Override public String onNotArgEnough(ExecutePack pack, int n) { return pack.context.getString(R.string.help_automate); }
        @Override public String onArgNotFound(ExecutePack pack, int index) { return pack.context.getString(R.string.help_automate); }
    }

    @Override protected Param paramForString(MainPack pack, String param) { return AutomationParam.get(param); }
    @Override public String[] params() { return AutomationParam.labels(); }
    @Override protected String doThings(ExecutePack pack) { return null; }
    @Override public int priority() { return 3; }
    @Override public int helpRes() { return R.string.help_automate; }

    static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String status(Context context) {
        SharedPreferences p = prefs(context);
        StringBuilder out = new StringBuilder("SMART AUTOMATION STATUS");
        out.append(Tuils.NEWLINE).append("Battery threshold: ").append(p.getBoolean("battery_enabled", false) ? p.getInt("battery_threshold", 20) + "% (enabled)" : "off");
        out.append(Tuils.NEWLINE).append("Charging alerts: ").append(p.getBoolean("charge_enabled", false) ? "enabled" : "off");
        out.append(Tuils.NEWLINE).append("Network change alerts: ").append(p.getBoolean("wifi_enabled", false) ? "enabled (15-minute check)" : "off");
        out.append(Tuils.NEWLINE).append("Daily reminder: ").append(p.getBoolean("daily_enabled", false) ? p.getString("daily_time", "configured") + " — " + p.getString("daily_message", "") : "off");
        out.append(Tuils.NEWLINE).append("One-time reminder: ").append(p.getBoolean("timer_enabled", false) ? p.getString("timer_message", "scheduled") : "off");
        out.append(Tuils.NEWLINE).append("Config backup: automate -backup");
        return out.toString();
    }

    private static void updatePoll(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences p = prefs(app);
        boolean enabled = p.getBoolean("battery_enabled", false) || p.getBoolean("wifi_enabled", false);
        AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        PendingIntent pi = pollPendingIntent(app);
        if (enabled) {
            long first = android.os.SystemClock.elapsedRealtime() + 60L * 1000L;
            alarms.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, first, POLL_INTERVAL_MS, pi);
        } else {
            alarms.cancel(pi);
            pi.cancel();
        }
    }

    static void updatePollForReceiver(Context context) {
        updatePoll(context);
    }

    private static PendingIntent pollPendingIntent(Context context) {
        Intent intent = new Intent(context, AutomationReceiver.class);
        intent.setAction(ACTION_POLL);
        return PendingIntent.getBroadcast(context, REQUEST_POLL, intent, pendingFlags());
    }

    private static int pendingFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    private static void scheduleTimer(Context context, int minutes, String message) {
        Context app = context.getApplicationContext();
        SharedPreferences.Editor edit = prefs(app).edit().putBoolean("timer_enabled", true).putString("timer_message", message);
        edit.apply();
        AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        Intent intent = new Intent(app, AutomationReceiver.class).setAction(ACTION_TIMER).putExtra("message", message);
        PendingIntent pi = PendingIntent.getBroadcast(app, REQUEST_TIMER, intent, pendingFlags());
        alarms.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes * 60L * 1000L, pi);
    }

    private static void cancelTimer(Context context) {
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, AutomationReceiver.class).setAction(ACTION_TIMER);
        PendingIntent pi = PendingIntent.getBroadcast(app, REQUEST_TIMER, intent, pendingFlags());
        AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(pi);
        pi.cancel();
        prefs(app).edit().putBoolean("timer_enabled", false).remove("timer_message").apply();
    }

    private static void scheduleDaily(Context context, int hour, int minute, String message) {
        Context app = context.getApplicationContext();
        prefs(app).edit().putBoolean("daily_enabled", true).putInt("daily_hour", hour).putInt("daily_minute", minute)
                .putString("daily_time", String.format(Locale.US, "%02d:%02d", hour, minute)).putString("daily_message", message).apply();
        scheduleDailyFromPrefs(app);
    }

    static void scheduleDailyFromPrefs(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences p = prefs(app);
        if (!p.getBoolean("daily_enabled", false)) return;
        int hour = p.getInt("daily_hour", 8);
        int minute = p.getInt("daily_minute", 0);
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
        calendar.set(java.util.Calendar.MINUTE, minute);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) calendar.add(java.util.Calendar.DAY_OF_YEAR, 1);
        Intent intent = new Intent(app, AutomationReceiver.class).setAction(ACTION_DAILY);
        PendingIntent pi = PendingIntent.getBroadcast(app, REQUEST_DAILY, intent, pendingFlags());
        AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
    }

    private static void cancelDaily(Context context) {
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, AutomationReceiver.class).setAction(ACTION_DAILY);
        PendingIntent pi = PendingIntent.getBroadcast(app, REQUEST_DAILY, intent, pendingFlags());
        AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(pi);
        pi.cancel();
        prefs(app).edit().putBoolean("daily_enabled", false).remove("daily_message").remove("daily_time").apply();
    }

    private static String createBackup(Context context) {
        File source = Tuils.getFolder();
        if (source == null || !source.isDirectory()) return "T-UI configuration folder was not found.";
        File base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) base = new File(context.getFilesDir(), "documents");
        File folder = new File(base, "T-UI-Expert-X-Backups");
        if (!folder.exists() && !folder.mkdirs()) return "Unable to create backup folder.";
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File output = new File(folder, "tuix-config-" + stamp + ".zip");
        // Allowlist only configuration files. expert.xml is intentionally excluded
        // because it can contain remote-access credentials or other secrets. crash.txt is excluded too.
        String[] allowed = new String[]{"theme.xml", "cmd.xml", "toolbar.xml", "ui.xml", "behavior.xml", "suggestions.xml",
                "apps.xml", "notifications.xml", "rss.xml", "alias.txt", "regex.xml", "notes.xml", "htmlextract.xml", "reply.xml"};
        int copied = 0;
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output))) {
            byte[] buffer = new byte[8192];
            for (String name : allowed) {
                File file = new File(source, name);
                if (!file.isFile() || file.length() > 4L * 1024L * 1024L) continue;
                try (FileInputStream input = new FileInputStream(file)) {
                    zip.putNextEntry(new ZipEntry(name));
                    int read;
                    while ((read = input.read(buffer)) != -1) zip.write(buffer, 0, read);
                    zip.closeEntry();
                    copied++;
                }
            }
            if (copied == 0) { output.delete(); return "No supported configuration files were found to back up."; }
            return "Configuration backup created (" + copied + " files):" + Tuils.NEWLINE + output.getAbsolutePath() +
                    Tuils.NEWLINE + "Security: expert.xml and crash logs were excluded.";
        } catch (Exception e) {
            output.delete();
            return "Backup failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private static int[] parseTime(String value) {
        if (value == null || !value.matches("(?:[01]?[0-9]|2[0-3]):[0-5][0-9]")) return null;
        String[] parts = value.split(":");
        try { return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])}; }
        catch (Exception e) { return null; }
    }

    private static String cleanMessage(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (clean.length() > 120) clean = clean.substring(0, 120);
        return clean;
    }
}
