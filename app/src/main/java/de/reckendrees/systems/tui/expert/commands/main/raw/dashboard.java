package de.reckendrees.systems.tui.expert.commands.main.raw;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

import de.reckendrees.systems.tui.expert.R;
import de.reckendrees.systems.tui.expert.commands.CommandAbstraction;
import de.reckendrees.systems.tui.expert.commands.ExecutePack;
import de.reckendrees.systems.tui.expert.commands.main.MainPack;
import de.reckendrees.systems.tui.expert.commands.main.Param;
import de.reckendrees.systems.tui.expert.commands.main.specific.ParamCommand;
import de.reckendrees.systems.tui.expert.managers.AliasManager;
import de.reckendrees.systems.tui.expert.managers.AppsManager;
import de.reckendrees.systems.tui.expert.managers.ProfileInstaller;
import de.reckendrees.systems.tui.expert.tuils.Tuils;

/**
 * Custom command dashboard for T-UI Expert X.
 * It is a searchable terminal-native hub for all five development phases.
 * Pinned shortcuts are constrained to built-in, non-shell T-UI commands.
 */
public class dashboard extends ParamCommand {

    private static final String PREFIX = "dkx_";
    private static final Pattern SAFE_ALIAS = Pattern.compile("dkx_[a-z0-9_]{1,20}");
    private static final Pattern SAFE_TOKEN = Pattern.compile("[A-Za-z0-9_.:-]{1,96}");
    private static final String[] ALLOWED_COMMANDS = new String[]{
            "dashboard", "cyber", "monitor", "automate", "security", "status",
            "notes", "theme", "help", "time", "search"
    };

    private enum DashboardParam implements Param {
        home {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return homeText(); }
        },
        cyber {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return category("CYBER SECURITY TOOLBOX", CYBER); }
        },
        device {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return category("LIVE DEVICE MONITOR", DEVICE); }
        },
        automation {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return category("SMART AUTOMATION ENGINE", AUTOMATION); }
        },
        security {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return category("SECURITY & PRIVACY CENTER", SECURITY); }
        },
        launcher {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return category("LAUNCHER & PRODUCTIVITY", LAUNCHER); }
        },
        apps {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return appBrowser((MainPack) pack); }
        },
        search {
            @Override public int[] args() { return new int[]{CommandAbstraction.PLAIN_TEXT}; }
            @Override public String exec(ExecutePack pack) { return search(pack.getString()); }
        },
        favorites {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return favorites((MainPack) pack); }
        },
        pin {
            @Override public int[] args() { return new int[]{CommandAbstraction.TEXTLIST}; }
            @Override public String exec(ExecutePack pack) { return pin((MainPack) pack, pack.getList()); }
        },
        unpin {
            @Override public int[] args() { return new int[]{CommandAbstraction.PLAIN_TEXT}; }
            @Override public String exec(ExecutePack pack) { return unpin((MainPack) pack, pack.getString()); }
        },
        profile {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) {
                return ProfileInstaller.applyScreenshotProfile(pack.context, Tuils.getFolder());
            }
        },
        help {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return pack.context.getString(R.string.help_dashboard); }
        };

        static DashboardParam get(String value) {
            if (value == null) return null;
            String normalized = value.toLowerCase(Locale.US);
            for (DashboardParam param : values()) {
                if (normalized.endsWith(param.label())) return param;
            }
            return null;
        }

        static String[] labels() {
            DashboardParam[] values = values();
            String[] labels = new String[values.length];
            for (int i = 0; i < values.length; i++) labels[i] = values[i].label();
            return labels;
        }

        @Override public String label() { return Tuils.MINUS + name(); }
        @Override public String onNotArgEnough(ExecutePack pack, int n) { return pack.context.getString(R.string.help_dashboard); }
        @Override public String onArgNotFound(ExecutePack pack, int index) { return pack.context.getString(R.string.help_dashboard); }
    }

    private static final String[][] CYBER = new String[][]{
            {"cyber -ip", "Show local IPv4/IPv6 addresses"},
            {"cyber -net", "Show current network and DNS information"},
            {"cyber -dns <host>", "Resolve a hostname to IP addresses"},
            {"cyber -ping <host>", "Run a bounded connectivity test"},
            {"cyber -trace <host>", "Trace up to eight network hops"}
    };
    private static final String[][] DEVICE = new String[][]{
            {"monitor -all", "Complete device health snapshot"},
            {"monitor -battery", "Battery level, temperature and health"},
            {"monitor -cpu", "CPU usage sample"},
            {"monitor -memory", "RAM usage"},
            {"monitor -storage", "Internal storage usage"},
            {"monitor -network", "Current connectivity state"},
            {"monitor -uptime", "Time since last reboot"},
            {"monitor -device", "Model, Android and security patch"},
            {"monitor -watch", "Refresh the live monitor every five seconds"},
            {"monitor -stop", "Stop live monitor updates"}
    };
    private static final String[][] AUTOMATION = new String[][]{
            {"automate -status", "Show enabled automations"},
            {"automate -battery 20", "Alert at a low-battery threshold"},
            {"automate -chargeon", "Notify when charging is connected/disconnected"},
            {"automate -wifion", "Enable periodic network-change alerts"},
            {"automate -timer 15 Break", "Create a one-time reminder"},
            {"automate -daily 08:30 Morning", "Create a daily reminder"},
            {"automate -backup", "Create an allowlisted configuration backup"},
            {"automate -alloff", "Disable all T-UI automation"}
    };
    private static final String[][] SECURITY = new String[][]{
            {"security -audit", "Run the app security summary"},
            {"security -permissions", "Show permission grant state"},
            {"security -privacy", "Review privacy and app exposure"},
            {"security -config", "Scan known config for risky settings"},
            {"security -remote", "Audit remote settings without showing secrets"},
            {"security -settings", "Open Android app settings"},
            {"security -report", "Save a sanitized security report"}
    };
    private static final String[][] LAUNCHER = new String[][]{
            {"dashboard -apps", "Open the in-T-UI app browser; tap an app suggestion below"},
            {"help", "List all available T-UI commands"},
            {"apps -ls", "Print all visible installed app names in T-UI"},
            {"notes", "Open T-UI notes"},
            {"alias -ls", "List configured aliases (use alias -ls directly)"},
            {"theme -view", "Browse available themes"},
            {"search -gg <words>", "Search the web"},
            {"status", "Quick battery and connectivity status"},
            {"tui -about", "Show app version and credits"}
    };

    private static final String[][] ALL = combine(CYBER, DEVICE, AUTOMATION, SECURITY, LAUNCHER);

    @Override protected Param paramForString(MainPack pack, String param) { return DashboardParam.get(param); }
    @Override public String[] params() { return DashboardParam.labels(); }
    @Override protected String doThings(ExecutePack pack) { return null; }
    @Override public int priority() { return 4; }
    @Override public int helpRes() { return R.string.help_dashboard; }

    private static String homeText() {
        String n = Tuils.NEWLINE;
        return "╔══════════════════════════════════════╗" + n +
                "║       T-UI EXPERT X / DASHBOARD      ║" + n +
                "║         THE DARK GEEK DC             ║" + n +
                "╚══════════════════════════════════════╝" + n + n +
                "[01] CYBER SECURITY       dashboard -cyber" + n +
                "[02] DEVICE HEALTH        dashboard -device" + n +
                "[03] SMART AUTOMATION     dashboard -automation" + n +
                "[04] SECURITY & PRIVACY   dashboard -security" + n +
                "[05] LAUNCHER & TOOLS     dashboard -launcher" + n +
                "[06] PINNED SHORTCUTS     dashboard -favorites" + n +
                "[07] IN-TUI APP BROWSER   dashboard -apps" + n + n +
                "SEARCH: dashboard -search wifi" + n +
                "PIN: dashboard -pin dkx_battery monitor -battery" + n +
                "SCREEN PROFILE: dashboard -profile" + n +
                "Type 'dashboard -help' for all options.";
    }

    private static String appBrowser(MainPack pack) {
        String apps = pack.appsManager == null ? "[]" : pack.appsManager.printApps(AppsManager.SHOWN_APPS);
        StringBuilder out = new StringBuilder("T-UI APP BROWSER").append(Tuils.NEWLINE)
                .append("Installed apps stay inside the T-UI interface.").append(Tuils.NEWLINE)
                .append("Tap an app suggestion below to launch it, or type part of its name to filter.")
                .append(Tuils.NEWLINE).append(Tuils.NEWLINE)
                .append(apps);
        return out.toString();
    }

    private static String category(String title, String[][] entries) {
        StringBuilder out = new StringBuilder("╔══ ").append(title).append(" ══╗");
        for (String[] entry : entries) {
            out.append(Tuils.NEWLINE).append("  ").append(entry[0])
                    .append(Tuils.NEWLINE).append("     ").append(entry[1]);
        }
        return out.toString();
    }

    private static String search(String query) {
        if (query == null || query.trim().length() == 0) return "Usage: dashboard -search <keyword>";
        String term = query.trim().toLowerCase(Locale.US);
        StringBuilder out = new StringBuilder("DASHBOARD SEARCH: ").append(query.trim());
        int matches = 0;
        for (String[] entry : ALL) {
            if (entry[0].toLowerCase(Locale.US).contains(term) || entry[1].toLowerCase(Locale.US).contains(term)) {
                out.append(Tuils.NEWLINE).append(entry[0]).append(" — ").append(entry[1]);
                matches++;
            }
        }
        if (matches == 0) out.append(Tuils.NEWLINE).append("No matching dashboard command found.");
        out.append(Tuils.NEWLINE).append("Matches: ").append(matches);
        return out.toString();
    }

    private static String favorites(MainPack pack) {
        File file = new File(Tuils.getFolder(), AliasManager.PATH);
        if (!file.isFile()) return "No pinned shortcuts yet. Use dashboard -pin dkx_name <safe-command>.";
        StringBuilder out = new StringBuilder("PINNED T-UI X SHORTCUTS");
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf('=');
                if (separator <= 0) continue;
                String name = line.substring(0, separator).trim();
                if (name.startsWith(PREFIX)) {
                    out.append(Tuils.NEWLINE).append("  ").append(name);
                    count++;
                }
            }
        } catch (Exception e) {
            return "Unable to read shortcut names: " + e.getClass().getSimpleName();
        }
        if (count == 0) return "No pinned shortcuts yet. Use dashboard -pin dkx_name <safe-command>.";
        return out.append(Tuils.NEWLINE).append("Type a shortcut name to run it.").toString();
    }

    private static String pin(MainPack pack, ArrayList<String> args) {
        if (args == null || args.size() < 2) return "Usage: dashboard -pin dkx_name <built-in-command> [options]";
        String name = args.get(0).toLowerCase(Locale.US);
        if (!SAFE_ALIAS.matcher(name).matches()) return "Shortcut names must start with dkx_ and use letters, digits or underscore.";
        StringBuilder command = new StringBuilder();
        for (int i = 1; i < args.size(); i++) {
            String token = args.get(i);
            if (token == null || !SAFE_TOKEN.matcher(token).matches()) {
                return "Only plain T-UI command tokens are accepted. Shell syntax, paths and command chaining are blocked.";
            }
            if (i > 1) command.append(' ');
            command.append(token);
        }
        String value = command.toString();
        String root = args.get(1).toLowerCase(Locale.US);
        boolean allowed = false;
        for (String cmd : ALLOWED_COMMANDS) if (cmd.equals(root)) { allowed = true; break; }
        if (!allowed || !isSafeShortcut(args)) return "This command or option is not in the dashboard shortcut allowlist.";
        if (name.equals(root) || value.equals(name) || value.startsWith(name + " ")) return "Shortcut name cannot shadow its command.";
        if (aliasExists(name)) return "Shortcut already exists: " + name;
        pack.aliasManager.add(pack.context, name, value);
        return "Pinned shortcut saved: " + name + Tuils.NEWLINE +
                "Type '" + name + "' to run: " + value;
    }

    private static String unpin(MainPack pack, String name) {
        if (name == null) return "Usage: dashboard -unpin dkx_name";
        String clean = name.trim().toLowerCase(Locale.US);
        if (!SAFE_ALIAS.matcher(clean).matches()) return "Only dashboard shortcuts beginning with dkx_ can be removed here.";
        if (!aliasExists(clean)) return "Pinned shortcut not found: " + clean;
        pack.aliasManager.remove(pack.context, clean);
        return "Removed dashboard shortcut: " + clean;
    }

    private static boolean isSafeShortcut(ArrayList<String> args) {
        String root = args.get(1).toLowerCase(Locale.US);
        String option = args.size() > 2 ? args.get(2).toLowerCase(Locale.US) : "";
        int size = args.size();
        if ("cyber".equals(root)) {
            return (size == 3 && ("-ip".equals(option) || "-net".equals(option))) ||
                    (size == 4 && ("-dns".equals(option) || "-ping".equals(option) || "-trace".equals(option)) &&
                            args.get(3).matches("[A-Za-z0-9.-]{1,253}"));
        }
        if ("monitor".equals(root)) {
            return size == 3 && ("-all".equals(option) || "-battery".equals(option) || "-cpu".equals(option) ||
                    "-memory".equals(option) || "-storage".equals(option) || "-network".equals(option) ||
                    "-uptime".equals(option) || "-device".equals(option) || "-watch".equals(option) || "-stop".equals(option));
        }
        if ("automate".equals(root)) {
            if (size == 3) return "-status".equals(option) || "-batteryoff".equals(option) || "-chargeon".equals(option) ||
                    "-chargeoff".equals(option) || "-wifion".equals(option) || "-wifioff".equals(option) ||
                    "-cancel".equals(option) || "-alloff".equals(option) || "-backup".equals(option);
            if ("-battery".equals(option) && size == 4) {
                try { int threshold = Integer.parseInt(args.get(3)); return threshold >= 5 && threshold <= 50; }
                catch (Exception ignored) { return false; }
            }
            if ("-timer".equals(option) && size >= 5) {
                try { int minutes = Integer.parseInt(args.get(3)); return minutes >= 1 && minutes <= 10080; }
                catch (Exception ignored) { return false; }
            }
            if ("-daily".equals(option) && size >= 5) return args.get(3).matches("(?:[01]?[0-9]|2[0-3]):[0-5][0-9]");
            return false;
        }
        if ("security".equals(root)) return size == 3 && ("-audit".equals(option) || "-permissions".equals(option) ||
                "-privacy".equals(option) || "-config".equals(option) || "-remote".equals(option) ||
                "-settings".equals(option) || "-report".equals(option));
        if ("dashboard".equals(root)) return size == 3 && ("-home".equals(option) || "-cyber".equals(option) ||
                "-device".equals(option) || "-automation".equals(option) || "-security".equals(option) ||
                "-launcher".equals(option) || "-favorites".equals(option) || "-help".equals(option));
        if ("theme".equals(root)) return size == 3 && "-view".equals(option);
        if ("search".equals(root)) return size >= 4 && "-gg".equals(option);
        return ("status".equals(root) || "notes".equals(root) || "help".equals(root) || "time".equals(root)) && size == 2;
    }

    private static boolean aliasExists(String name) {
        File file = new File(Tuils.getFolder(), AliasManager.PATH);
        if (!file.isFile()) return false;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf('=');
                if (separator > 0 && name.equals(line.substring(0, separator).trim())) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static String[][] combine(String[][]... groups) {
        int size = 0;
        for (String[][] group : groups) size += group.length;
        String[][] result = new String[size][2];
        int index = 0;
        for (String[][] group : groups) for (String[] entry : group) result[index++] = entry;
        return result;
    }
}
