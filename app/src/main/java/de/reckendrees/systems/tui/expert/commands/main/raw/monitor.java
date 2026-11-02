package de.reckendrees.systems.tui.expert.commands.main.raw;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import de.reckendrees.systems.tui.expert.R;
import de.reckendrees.systems.tui.expert.commands.CommandAbstraction;
import de.reckendrees.systems.tui.expert.commands.ExecutePack;
import de.reckendrees.systems.tui.expert.commands.main.MainPack;
import de.reckendrees.systems.tui.expert.commands.main.Param;
import de.reckendrees.systems.tui.expert.commands.main.specific.ParamCommand;
import de.reckendrees.systems.tui.expert.tuils.Tuils;

/**
 * T-UI Expert X live device monitor.
 * Reads only device-local status APIs and procfs counters; it does not
 * collect personal content, phone numbers, location or installed-app lists.
 */
public class monitor extends ParamCommand {

    private static final long WATCH_INTERVAL_SECONDS = 5L;
    private static ScheduledExecutorService watchExecutor;
    private static ScheduledFuture<?> watchTask;

    private enum MonitorParam implements Param {
        all {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return runAsync(pack.context, new Snapshot(true, true, true, true, true, true, true)); }
        },
        battery {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return batteryInfo(pack.context); }
        },
        memory {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return memoryInfo(pack.context); }
        },
        storage {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return storageInfo(); }
        },
        uptime {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return uptimeInfo(); }
        },
        device {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return deviceInfo(); }
        },
        cpu {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) {
                final Context context = pack.context.getApplicationContext();
                runDiagnostic(context, new Diagnostic() {
                    @Override public String run() { return cpuInfo(); }
                });
                return "CPU sample started.";
            }
        },
        network {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return networkInfo(pack.context); }
        },
        watch {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return startWatch(pack.context); }
        },
        stop {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return stopWatch(); }
        },
        help {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return pack.context.getString(R.string.help_monitor); }
        };

        static MonitorParam get(String value) {
            if (value == null) return null;
            String normalized = value.toLowerCase(Locale.US);
            for (MonitorParam param : values()) {
                if (normalized.endsWith(param.label())) return param;
            }
            return null;
        }

        static String[] labels() {
            MonitorParam[] values = values();
            String[] labels = new String[values.length];
            for (int i = 0; i < values.length; i++) labels[i] = values[i].label();
            return labels;
        }

        @Override public String label() { return Tuils.MINUS + name(); }
        @Override public String onNotArgEnough(ExecutePack pack, int n) { return pack.context.getString(R.string.help_monitor); }
        @Override public String onArgNotFound(ExecutePack pack, int index) { return pack.context.getString(R.string.help_monitor); }
    }

    private interface Diagnostic { String run() throws Exception; }

    private static final class Snapshot {
        final boolean battery, cpu, memory, storage, network, uptime, device;
        Snapshot(boolean battery, boolean cpu, boolean memory, boolean storage, boolean uptime, boolean device, boolean network) {
            this.battery = battery; this.cpu = cpu; this.memory = memory; this.storage = storage;
            this.uptime = uptime; this.device = device; this.network = network;
        }
    }

    @Override protected Param paramForString(MainPack pack, String param) { return MonitorParam.get(param); }
    @Override public String[] params() { return MonitorParam.labels(); }
    @Override protected String doThings(ExecutePack pack) { return null; }
    @Override public int priority() { return 3; }
    @Override public int helpRes() { return R.string.help_monitor; }

    private static String runAsync(final Context source, final Snapshot snapshot) {
        final Context context = source.getApplicationContext();
        runDiagnostic(context, new Diagnostic() {
            @Override public String run() { return collect(context, snapshot); }
        });
        return "Device monitor snapshot started.";
    }

    private static void runDiagnostic(final Context context, final Diagnostic diagnostic) {
        new AsyncTask<Void, Void, String>() {
            @Override protected String doInBackground(Void... ignored) {
                try { return diagnostic.run(); }
                catch (Exception e) {
                    return "Monitor error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                }
            }
            @Override protected void onPostExecute(String result) { Tuils.sendOutput(context, result); }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private static String collect(Context context, Snapshot snapshot) {
        StringBuilder out = new StringBuilder("T-UI EXPERT X — LIVE DEVICE MONITOR");
        if (snapshot.battery) append(out, batteryInfo(context));
        if (snapshot.cpu) append(out, cpuInfo());
        if (snapshot.memory) append(out, memoryInfo(context));
        if (snapshot.storage) append(out, storageInfo());
        if (snapshot.network) append(out, networkInfo(context));
        if (snapshot.uptime) append(out, uptimeInfo());
        if (snapshot.device) append(out, deviceInfo());
        return out.toString();
    }

    private static void append(StringBuilder out, String section) {
        out.append(Tuils.NEWLINE).append(Tuils.NEWLINE).append(section);
    }

    private static String batteryInfo(Context context) {
        Intent battery = context.getApplicationContext().registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return "BATTERY\nBattery status is unavailable on this device.";

        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int percent = (level >= 0 && scale > 0) ? Math.round((level * 100f) / scale) : -1;
        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int health = battery.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        int temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        int voltage = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        String technology = battery.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);

        StringBuilder out = new StringBuilder("BATTERY");
        out.append(Tuils.NEWLINE).append("Charge: ").append(percent < 0 ? "unavailable" : percent + "%");
        out.append(Tuils.NEWLINE).append("Status: ").append(batteryStatus(status));
        out.append(Tuils.NEWLINE).append("Power source: ").append(powerSource(plugged));
        out.append(Tuils.NEWLINE).append("Health: ").append(batteryHealth(health));
        if (temp != Integer.MIN_VALUE) out.append(Tuils.NEWLINE).append("Temperature: ").append(String.format(Locale.US, "%.1f °C", temp / 10.0));
        if (voltage > 0) out.append(Tuils.NEWLINE).append("Voltage: ").append(voltage).append(" mV");
        if (technology != null && technology.length() > 0) out.append(Tuils.NEWLINE).append("Technology: ").append(technology);
        return out.toString();
    }

    private static String batteryStatus(int value) {
        switch (value) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "Charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "Discharging";
            case BatteryManager.BATTERY_STATUS_FULL: return "Full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "Not charging";
            default: return "Unknown";
        }
    }

    private static String powerSource(int value) {
        switch (value) {
            case BatteryManager.BATTERY_PLUGGED_AC: return "AC";
            case BatteryManager.BATTERY_PLUGGED_USB: return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "Wireless";
            default: return "Battery / unplugged";
        }
    }

    private static String batteryHealth(int value) {
        switch (value) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Overheating";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Over voltage";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "Unspecified failure";
            case BatteryManager.BATTERY_HEALTH_COLD: return "Cold";
            default: return "Unknown";
        }
    }

    private static String memoryInfo(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return "MEMORY\nMemory service is unavailable.";
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        long total = info.totalMem;
        long available = info.availMem;
        long used = Math.max(0L, total - available);
        StringBuilder out = new StringBuilder("MEMORY (RAM)");
        out.append(Tuils.NEWLINE).append("Total: ").append(formatBytes(total));
        out.append(Tuils.NEWLINE).append("Used: ").append(formatBytes(used));
        out.append(Tuils.NEWLINE).append("Available: ").append(formatBytes(available));
        if (total > 0) out.append(Tuils.NEWLINE).append("Usage: ").append(Math.round((used * 100.0) / total)).append("%");
        out.append(Tuils.NEWLINE).append("Low-memory state: ").append(info.lowMemory ? "yes" : "no");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            out.append(Tuils.NEWLINE).append("Threshold: ").append(formatBytes(info.threshold));
        }
        return out.toString();
    }

    private static String storageInfo() {
        try {
            StatFs stats = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long total = stats.getBlockCountLong() * stats.getBlockSizeLong();
            long available = stats.getAvailableBlocksLong() * stats.getBlockSizeLong();
            long used = Math.max(0L, total - available);
            StringBuilder out = new StringBuilder("INTERNAL STORAGE");
            out.append(Tuils.NEWLINE).append("Total: ").append(formatBytes(total));
            out.append(Tuils.NEWLINE).append("Used: ").append(formatBytes(used));
            out.append(Tuils.NEWLINE).append("Available: ").append(formatBytes(available));
            if (total > 0) out.append(Tuils.NEWLINE).append("Usage: ").append(Math.round((used * 100.0) / total)).append("%");
            return out.toString();
        } catch (Exception e) {
            return "INTERNAL STORAGE\nUnable to read storage statistics: " + e.getMessage();
        }
    }

    private static String uptimeInfo() {
        long millis = SystemClock.elapsedRealtime();
        long seconds = millis / 1000L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainingSeconds = seconds % 60L;
        return "SYSTEM UPTIME" + Tuils.NEWLINE + days + " days, " + hours + " hours, " + minutes + " minutes, " + remainingSeconds + " seconds";
    }

    private static String deviceInfo() {
        StringBuilder out = new StringBuilder("DEVICE");
        out.append(Tuils.NEWLINE).append("Manufacturer: ").append(Build.MANUFACTURER);
        out.append(Tuils.NEWLINE).append("Model: ").append(Build.MODEL);
        out.append(Tuils.NEWLINE).append("Device: ").append(Build.DEVICE);
        out.append(Tuils.NEWLINE).append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            out.append(Tuils.NEWLINE).append("Security patch: ").append(Build.VERSION.SECURITY_PATCH);
        }
        out.append(Tuils.NEWLINE).append("CPU ABI: ").append(Build.CPU_ABI);
        return out.toString();
    }

    private static String networkInfo(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return "NETWORK\nConnectivity service is unavailable.";
            NetworkInfo active = manager.getActiveNetworkInfo();
            if (active == null || !active.isConnected()) return "NETWORK\nConnection: Offline";
            StringBuilder out = new StringBuilder("NETWORK");
            out.append(Tuils.NEWLINE).append("Connection: Connected");
            out.append(Tuils.NEWLINE).append("Type: ").append(active.getTypeName());
            String subtype = active.getSubtypeName();
            if (subtype != null && subtype.length() > 0) out.append(Tuils.NEWLINE).append("Subtype: ").append(subtype);
            out.append(Tuils.NEWLINE).append("State: ").append(active.getState());
            return out.toString();
        } catch (SecurityException e) {
            return "NETWORK\nPermission denied while reading connectivity state.";
        } catch (Exception e) {
            return "NETWORK\nUnable to read connectivity state: " + e.getMessage();
        }
    }

    private static String cpuInfo() {
        long[] first = readCpuCounters();
        if (first == null) return "CPU\nCPU counters are not exposed by this Android build.";
        try { Thread.sleep(350L); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return "CPU\nSampling interrupted."; }
        long[] second = readCpuCounters();
        if (second == null) return "CPU\nUnable to read CPU counters.";
        long totalDelta = second[0] - first[0];
        long idleDelta = second[1] - first[1];
        if (totalDelta <= 0) return "CPU\nCPU usage is unavailable for this sample.";
        double usage = ((double) Math.max(0L, totalDelta - idleDelta) * 100.0) / (double) totalDelta;
        return "CPU" + Tuils.NEWLINE + "Usage (whole device): " + String.format(Locale.US, "%.1f%%", Math.max(0.0, Math.min(100.0, usage)));
    }

    /** Returns total jiffies and idle+iowait jiffies from the aggregate cpu line. */
    private static long[] readCpuCounters() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("/proc/stat"));
            String line = reader.readLine();
            if (line == null || !line.startsWith("cpu ")) return null;
            String[] fields = line.trim().split("\\s+");
            if (fields.length < 5) return null;
            long total = 0L;
            long idle = 0L;
            for (int i = 1; i < fields.length; i++) {
                long value = Long.parseLong(fields[i]);
                total += value;
                if (i == 4 || i == 5) idle += value;
            }
            return new long[]{total, idle};
        } catch (Exception e) {
            return null;
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException ignored) { }
        }
    }

    private static String startWatch(Context source) {
        final Context context = source.getApplicationContext();
        synchronized (monitor.class) {
            if (watchTask != null && !watchTask.isCancelled() && !watchTask.isDone()) {
                return "Live monitor is already running. Use: monitor -stop";
            }
            watchExecutor = Executors.newSingleThreadScheduledExecutor(new java.util.concurrent.ThreadFactory() {
                @Override public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "TUIX-DeviceMonitor");
                    thread.setDaemon(true);
                    return thread;
                }
            });
            watchTask = watchExecutor.scheduleWithFixedDelay(new Runnable() {
                @Override public void run() {
                    try {
                        String report = collect(context, new Snapshot(true, true, true, true, true, false, true));
                        Tuils.sendOutput(context, "[LIVE MONITOR · refresh every 5s]" + Tuils.NEWLINE + report);
                    } catch (Exception e) {
                        Tuils.sendOutput(context, "Live monitor sample failed: " + e.getMessage());
                    }
                }
            }, 0L, WATCH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
        return "Live device monitor started. Refresh interval: 5 seconds. Use: monitor -stop";
    }

    private static String stopWatch() {
        synchronized (monitor.class) {
            if (watchTask == null) return "Live monitor is not running.";
            watchTask.cancel(true);
            watchTask = null;
            if (watchExecutor != null) {
                watchExecutor.shutdownNow();
                watchExecutor = null;
            }
        }
        return "Live device monitor stopped.";
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) return "unavailable";
        double value = bytes;
        String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) { value /= 1024.0; unit++; }
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }
}
