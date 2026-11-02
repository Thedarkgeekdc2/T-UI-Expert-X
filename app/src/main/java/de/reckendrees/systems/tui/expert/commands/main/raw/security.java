package de.reckendrees.systems.tui.expert.commands.main.raw;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.reckendrees.systems.tui.expert.R;
import de.reckendrees.systems.tui.expert.commands.CommandAbstraction;
import de.reckendrees.systems.tui.expert.commands.ExecutePack;
import de.reckendrees.systems.tui.expert.commands.main.MainPack;
import de.reckendrees.systems.tui.expert.commands.main.Param;
import de.reckendrees.systems.tui.expert.commands.main.specific.ParamCommand;
import de.reckendrees.systems.tui.expert.tuils.Tuils;

/**
 * T-UI Expert X Security & Privacy Center.
 * Audits only this app's manifest/runtime permission state and known T-UI config
 * files. Secret values are never displayed or copied into generated reports.
 */
public class security extends ParamCommand {

    private enum SecurityParam implements Param {
        audit {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return audit(pack.context); }
        },
        permissions {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return permissionReport(pack.context); }
        },
        privacy {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return privacyReport(pack.context); }
        },
        config {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return configReport(pack.context); }
        },
        remote {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return remoteReport(pack.context); }
        },
        settings {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return openAppSettings(pack.context); }
        },
        report {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return saveSafeReport(pack.context); }
        },
        help {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return pack.context.getString(R.string.help_security); }
        };

        static SecurityParam get(String value) {
            if (value == null) return null;
            String normalized = value.toLowerCase(Locale.US);
            for (SecurityParam param : values()) if (normalized.endsWith(param.label())) return param;
            return null;
        }
        static String[] labels() {
            SecurityParam[] values = values();
            String[] labels = new String[values.length];
            for (int i = 0; i < values.length; i++) labels[i] = values[i].label();
            return labels;
        }
        @Override public String label() { return Tuils.MINUS + name(); }
        @Override public String onNotArgEnough(ExecutePack pack, int n) { return pack.context.getString(R.string.help_security); }
        @Override public String onArgNotFound(ExecutePack pack, int index) { return pack.context.getString(R.string.help_security); }
    }

    @Override protected Param paramForString(MainPack pack, String param) { return SecurityParam.get(param); }
    @Override public String[] params() { return SecurityParam.labels(); }
    @Override protected String doThings(ExecutePack pack) { return null; }
    @Override public int priority() { return 3; }
    @Override public int helpRes() { return R.string.help_security; }

    private static PackageInfo packageInfo(Context context) throws PackageManager.NameNotFoundException {
        int flags = PackageManager.GET_PERMISSIONS | PackageManager.GET_ACTIVITIES |
                PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS;
        return context.getPackageManager().getPackageInfo(context.getPackageName(), flags);
    }

    private static String audit(Context context) {
        StringBuilder out = new StringBuilder("T-UI EXPERT X — SECURITY AUDIT");
        try {
            PackageInfo info = packageInfo(context);
            PermissionStats permissions = permissionStats(context, info);
            ApplicationInfo app = context.getApplicationInfo();
            boolean backup = (app.flags & ApplicationInfo.FLAG_ALLOW_BACKUP) != 0;
            boolean debug = (app.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            boolean cleartext = Build.VERSION.SDK_INT < 23 || app.usesCleartextTraffic;
            int exposed = countExportedWithoutPermission(info);

            line(out, "Requested permissions", String.valueOf(permissions.total));
            line(out, "Granted dangerous permissions", permissions.grantedDangerous + "/" + permissions.dangerous);
            line(out, "App data backup", backup ? "ENABLED — review privacy risk" : "disabled");
            line(out, "Debug build flag", debug ? "ENABLED — do not distribute" : "off");
            line(out, "Cleartext HTTP policy", cleartext ? "may be allowed on this Android version/config" : "blocked by app policy");
            line(out, "Exported components without permission", String.valueOf(exposed));
            line(out, "T-UI external config", configRiskSummary(context));
            out.append(Tuils.NEWLINE).append("Run: security -permissions | -privacy | -config | -remote");
            if (backup || debug || cleartext || permissions.grantedDangerous > 0 || exposed > 0) {
                out.append(Tuils.NEWLINE).append("Review the findings below; permissions are not changed automatically.");
            }
        } catch (Exception e) {
            out.append(Tuils.NEWLINE).append("Audit could not be completed: ").append(safeError(e));
        }
        return out.toString();
    }

    private static String permissionReport(Context context) {
        StringBuilder out = new StringBuilder("APP PERMISSION AUDIT (T-UI EXPERT X)");
        try {
            PackageInfo info = packageInfo(context);
            String[] requested = info.requestedPermissions;
            if (requested == null || requested.length == 0) return out.append(Tuils.NEWLINE).append("No requested permissions recorded.").toString();
            int granted = 0;
            int denied = 0;
            int dangerousGranted = 0;
            for (String name : requested) {
                boolean isGranted = ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED;
                String kind = permissionKind(context, name);
                if (isGranted) granted++; else denied++;
                if (isGranted && "DANGEROUS".equals(kind)) dangerousGranted++;
                out.append(Tuils.NEWLINE).append(isGranted ? "[GRANTED] " : "[NOT GRANTED] ")
                        .append(shortPermission(name)).append(" — ").append(kind);
            }
            out.append(Tuils.NEWLINE).append(Tuils.NEWLINE).append("Summary: ").append(granted)
                    .append(" granted, ").append(denied).append(" not granted; ")
                    .append(dangerousGranted).append(" dangerous permissions currently granted.");
            out.append(Tuils.NEWLINE).append("No permissions were requested, granted or revoked by this report.");
        } catch (Exception e) {
            out.append(Tuils.NEWLINE).append("Permission audit unavailable: ").append(safeError(e));
        }
        return out.toString();
    }

    private static String privacyReport(Context context) {
        StringBuilder out = new StringBuilder("PRIVACY & APP EXPOSURE");
        ApplicationInfo app = context.getApplicationInfo();
        boolean backup = (app.flags & ApplicationInfo.FLAG_ALLOW_BACKUP) != 0;
        boolean debug = (app.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        boolean cleartext = Build.VERSION.SDK_INT < 23 || app.usesCleartextTraffic;
        line(out, "Android backup", backup ? "enabled" : "disabled");
        line(out, "Debuggable", debug ? "yes" : "no");
        line(out, "Cleartext traffic", cleartext ? "not fully blocked" : "blocked on Android 6.0+ by manifest policy");
        line(out, "Android version", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        line(out, "Shared T-UI config folder", "legacy external-storage path; treat contents as readable by other storage-capable apps");
        line(out, "Secret handling", "security reports mask values; expert.xml is excluded from automate -backup");
        try {
            PackageInfo info = packageInfo(context);
            line(out, "Exported components", String.valueOf(countExported(info)));
            line(out, "Unprotected exported components", String.valueOf(countExportedWithoutPermission(info)));
        } catch (Exception e) {
            line(out, "Component audit", "unavailable");
        }
        out.append(Tuils.NEWLINE).append("Use security -settings to review Android app permissions and special access.");
        return out.toString();
    }

    private static String configReport(Context context) {
        StringBuilder out = new StringBuilder("T-UI CONFIGURATION SAFETY AUDIT");
        File folder = Tuils.getFolder();
        if (folder == null || !folder.isDirectory()) return out.append(Tuils.NEWLINE).append("T-UI config folder not found or not accessible.").toString();

        String[] names = new String[]{"expert.xml", "behavior.xml", "notifications.xml", "rss.xml", "cmd.xml", "alias.txt"};
        int read = 0;
        int httpCount = 0;
        for (String name : names) {
            File file = new File(folder, name);
            if (!file.isFile() || file.length() > 512L * 1024L) continue;
            String content = readLimited(file, 512 * 1024);
            if (content == null) continue;
            read++;
            int http = countMatches(content, "(?i)http://");
            httpCount += http;
        }
        String expert = readLimited(new File(folder, "expert.xml"), 512 * 1024);
        if (expert != null) {
            if (hasValue(expert, "remote_pass") || hasValue(expert, "remote_password")) out.append(Tuils.NEWLINE).append("[HIGH] Remote password value exists in expert.xml; value intentionally hidden. Rotate it and disable the companion remote service when not needed.");
            if (hasValue(expert, "exit_password")) out.append(Tuils.NEWLINE).append("[MEDIUM] Exit password is stored in the external XML config; it is not encrypted by this app.");
            if (isTrue(expert, "use_root")) out.append(Tuils.NEWLINE).append("[HIGH] Root command mode is enabled. Only run commands you have reviewed.");
            if (hasValue(expert, "custom_command")) out.append(Tuils.NEWLINE).append("[MEDIUM] A custom command is configured. Review its content locally before enabling periodic execution.");
        } else {
            out.append(Tuils.NEWLINE).append("expert.xml not readable; remote/root/custom-command checks are incomplete.");
        }
        if (hasAnyValue(folder, names, new String[]{"weather_key", "api_key", "access_token", "auth_token"})) {
            out.append(Tuils.NEWLINE).append("[INFO] API key/token field(s) found in scanned XML configuration; values intentionally hidden. Do not share these files publicly.");
        }
        String rss = readLimited(new File(folder, "rss.xml"), 512 * 1024);
        int malformedRss = countInvalidRssUrls(rss);
        if (malformedRss > 0) out.append(Tuils.NEWLINE).append("[HIGH] ").append(malformedRss).append(" RSS entry URL(s) do not begin with http:// or https://. Review rss.xml.");
        if (httpCount > 0) out.append(Tuils.NEWLINE).append("[MEDIUM] ").append(httpCount).append(" plain-HTTP reference(s) found in scanned config files; prefer HTTPS where supported.");
        line(out, "Files inspected", String.valueOf(read));
        line(out, "Config storage", "shared external storage; not private app storage");
        if (read == 0) out.append(Tuils.NEWLINE).append("No known configuration files could be read. Check storage access.");
        out.append(Tuils.NEWLINE).append("Secret values are never printed by this command.");
        return out.toString();
    }

    private static String remoteReport(Context context) {
        StringBuilder out = new StringBuilder("REMOTE-ACCESS CONFIGURATION REVIEW");
        File folder = Tuils.getFolder();
        String expert = folder == null ? null : readLimited(new File(folder, "expert.xml"), 512 * 1024);
        if (expert == null) return out.append(Tuils.NEWLINE).append("expert.xml not found or not readable.").toString();
        boolean port = hasValue(expert, "remote_port");
        boolean user = hasValue(expert, "remote_user");
        String password = getValue(expert, "remote_pass");
        boolean pass = password != null && password.length() > 0;
        line(out, "Remote-related fields", (port || user || pass) ? "present in expert.xml" : "not found");
        line(out, "Port setting", port ? "configured (value hidden)" : "not configured");
        line(out, "Username setting", user ? "configured (value hidden)" : "not configured");
        line(out, "Password setting", pass ? "configured (value hidden)" : "not configured");
        if (pass && isWeakPassword(password)) out.append(Tuils.NEWLINE).append("[HIGH] Configured remote password appears short or common. Replace it with a unique random passphrase.");
        else if (pass) out.append(Tuils.NEWLINE).append("[REVIEW] A remote password is stored as plain text in shared external storage. Strong length does not encrypt the file.");
        out.append(Tuils.NEWLINE).append("This app source contains no remote server/listener implementation for these expert.xml fields. This command audits values only; it cannot stop a separate Termux script or companion service.");
        out.append(Tuils.NEWLINE).append("Never share expert.xml. If remote access is unused, disable the separate service and remove its credentials there.");
        return out.toString();
    }

    private static String openAppSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.getPackageName(), null));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return "Opened Android app settings. Review permissions, notification access and other special access manually.";
        } catch (Exception e) {
            return "Unable to open Android app settings: " + safeError(e);
        }
    }

    private static String saveSafeReport(Context context) {
        File folder = new File(context.getFilesDir(), "security-reports");
        if (!folder.exists() && !folder.mkdirs()) return "Could not create private report folder.";
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File file = new File(folder, "tuix-security-audit-" + stamp + ".txt");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            writer.write(audit(context));
            writer.write("\n\nCONFIGURATION SUMMARY\n");
            writer.write(configRiskSummary(context));
            writer.write("\n\nNote: no password, API key, token, username or raw config content is included.\n");
            return "Sanitized security report saved in private app storage:\n" + file.getAbsolutePath() +
                    "\nNo config files or secrets were copied.";
        } catch (Exception e) {
            if (file.exists()) file.delete();
            return "Could not save security report: " + safeError(e);
        }
    }

    private static PermissionStats permissionStats(Context context, PackageInfo info) {
        PermissionStats stats = new PermissionStats();
        String[] requested = info.requestedPermissions;
        if (requested == null) return stats;
        stats.total = requested.length;
        for (String permission : requested) {
            if ("DANGEROUS".equals(permissionKind(context, permission))) {
                stats.dangerous++;
                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) stats.grantedDangerous++;
            }
        }
        return stats;
    }

    private static String permissionKind(Context context, String name) {
        try {
            PermissionInfo info = context.getPackageManager().getPermissionInfo(name, 0);
            int base = info.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
            return base == PermissionInfo.PROTECTION_DANGEROUS ? "DANGEROUS" : (base == PermissionInfo.PROTECTION_NORMAL ? "NORMAL" : "SIGNATURE/OTHER");
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private static String shortPermission(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1) : name;
    }

    private static int countExported(PackageInfo info) {
        return countExported(info.activities) + countExported(info.services) + countExported(info.receivers) + countExported(info.providers);
    }

    private static int countExportedWithoutPermission(PackageInfo info) {
        return countExportedWithoutPermission(info.activities) + countExportedWithoutPermission(info.services) +
                countExportedWithoutPermission(info.receivers) + countExportedWithoutPermission(info.providers);
    }

    private static int countExported(ComponentInfo[] components) {
        int count = 0;
        if (components != null) for (ComponentInfo component : components) if (component.exported && component.enabled) count++;
        return count;
    }

    private static int countExportedWithoutPermission(ComponentInfo[] components) {
        int count = 0;
        if (components != null) for (ComponentInfo component : components) {
            if (component.exported && component.enabled && (component.permission == null || component.permission.length() == 0)) count++;
        }
        return count;
    }

    private static String configRiskSummary(Context context) {
        File folder = Tuils.getFolder();
        if (folder == null) return "not accessible";
        String expert = readLimited(new File(folder, "expert.xml"), 512 * 1024);
        if (expert == null) return "expert.xml not readable";
        List<String> risks = new ArrayList<>();
        if (hasValue(expert, "remote_pass") || hasValue(expert, "remote_password")) risks.add("remote secret present (masked)");
        if (hasValue(expert, "exit_password")) risks.add("exit password field present (masked)");
        if (isTrue(expert, "use_root")) risks.add("root mode enabled");
        if (hasValue(expert, "custom_command")) risks.add("custom command configured");
        if (hasAnyValue(folder, new String[]{"behavior.xml", "notifications.xml", "rss.xml", "expert.xml", "cmd.xml"}, new String[]{"weather_key", "api_key", "access_token", "auth_token"})) risks.add("API credential field present (masked)");
        return risks.size() == 0 ? "no flagged expert.xml fields found" : join(risks, ", ");
    }


    private static boolean hasAnyValue(File folder, String[] files, String[] tags) {
        for (String fileName : files) {
            String content = readLimited(new File(folder, fileName), 512 * 1024);
            if (content == null) continue;
            for (String tag : tags) if (hasValue(content, tag)) return true;
        }
        return false;
    }

    private static String readLimited(File file, int maxChars) {
        if (file == null || !file.isFile() || file.length() > maxChars) return null;
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                if (text.length() + read > maxChars) return null;
                text.append(buffer, 0, read);
            }
            return text.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean hasValue(String xml, String tag) {
        String value = getValue(xml, tag);
        return value != null && value.trim().length() > 0;
    }

    private static String getValue(String xml, String tag) {
        if (xml == null) return null;
        Pattern pattern = Pattern.compile("<\\s*" + Pattern.quote(tag) + "\\b[^>]*\\bvalue\\s*=\\s*(['\"])(.*?)\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(xml);
        return matcher.find() ? matcher.group(2).trim() : null;
    }

    private static boolean isTrue(String xml, String tag) {
        String value = getValue(xml, tag);
        return value != null && ("true".equalsIgnoreCase(value) || "1".equals(value));
    }

    private static boolean isWeakPassword(String value) {
        if (value == null || value.length() < 12) return true;
        String normalized = value.toLowerCase(Locale.US);
        return "password".equals(normalized) || "admin123".equals(normalized) || "123456789012".equals(normalized) ||
                "qwerty123456".equals(normalized) || "000000000000".equals(normalized);
    }

    private static int countInvalidRssUrls(String xml) {
        if (xml == null) return 0;
        Pattern p = Pattern.compile("\\burl\\s*=\\s*(['\"])(.*?)\\1", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(xml);
        int count = 0;
        while (m.find()) {
            String value = m.group(2).trim().toLowerCase(Locale.US);
            if (!value.startsWith("https://") && !value.startsWith("http://")) count++;
        }
        return count;
    }

    private static int countMatches(String value, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(value);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private static String join(List<String> values, String separator) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append(separator);
            out.append(value);
        }
        return out.toString();
    }

    private static void line(StringBuilder out, String label, String value) {
        out.append(Tuils.NEWLINE).append(label).append(": ").append(value);
    }

    private static String safeError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.length() == 0) return e.getClass().getSimpleName();
        String clean = message.replace('\n', ' ').replace('\r', ' ');
        return clean.length() > 100 ? clean.substring(0, 100) : clean;
    }

    private static final class PermissionStats {
        int total;
        int dangerous;
        int grantedDangerous;
    }
}
