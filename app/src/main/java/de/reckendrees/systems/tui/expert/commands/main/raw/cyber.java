package de.reckendrees.systems.tui.expert.commands.main.raw;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import de.reckendrees.systems.tui.expert.R;
import de.reckendrees.systems.tui.expert.commands.CommandAbstraction;
import de.reckendrees.systems.tui.expert.commands.ExecutePack;
import de.reckendrees.systems.tui.expert.commands.main.MainPack;
import de.reckendrees.systems.tui.expert.commands.main.Param;
import de.reckendrees.systems.tui.expert.commands.main.specific.ParamCommand;
import de.reckendrees.systems.tui.expert.tuils.Tuils;

/**
 * Safe, user-initiated network diagnostics for the device owner.
 * Commands are deliberately limited to local network information and
 * basic reachability/DNS diagnostics for a host supplied by the user.
 */
public class cyber extends ParamCommand {

    private enum CyberParam implements Param {
        ip {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return localAddresses(pack.context); }
        },
        net {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return networkSummary(pack.context); }
        },
        dns {
            @Override public int[] args() { return new int[]{CommandAbstraction.PLAIN_TEXT}; }
            @Override public String exec(ExecutePack pack) {
                final Context context = pack.context.getApplicationContext();
                final String host = readHost(pack);
                if (host == null) return hostError(context);
                runDiagnostic(context, new Diagnostic() {
                    @Override public String run() throws Exception {
                        InetAddress[] addresses = InetAddress.getAllByName(host);
                        StringBuilder out = new StringBuilder("DNS lookup: ").append(host);
                        for (InetAddress address : addresses) {
                            out.append(Tuils.NEWLINE).append("  ").append(address.getHostAddress());
                        }
                        return out.toString();
                    }
                });
                return "DNS lookup started for " + host;
            }
        },
        ping {
            @Override public int[] args() { return new int[]{CommandAbstraction.PLAIN_TEXT}; }
            @Override public String exec(ExecutePack pack) {
                final Context context = pack.context.getApplicationContext();
                final String host = readHost(pack);
                if (host == null) return hostError(context);
                runDiagnostic(context, new Diagnostic() {
                    @Override public String run() throws Exception {
                        InetAddress resolved = InetAddress.getByName(host);
                        String numericHost = resolved.getHostAddress();
                        String[] candidates = new String[]{"/system/bin/ping", "/system/xbin/ping"};
                        IOException lastError = null;
                        for (String binary : candidates) {
                            try {
                                Process process = new ProcessBuilder(binary, "-c", "4", "-W", "2", "-n", numericHost)
                                        .redirectErrorStream(true).start();
                                StringBuilder output = new StringBuilder();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (output.length() < 5000) output.append(line).append(Tuils.NEWLINE);
                                }
                                // ping is bounded to four probes; -W limits each reply wait.
                                process.waitFor();
                                String result = output.toString().trim();
                                return result.length() == 0 ? "Ping finished (exit " + process.exitValue() + ")." : result;
                            } catch (IOException e) {
                                lastError = e;
                            }
                        }
                        return "The Android ping utility is unavailable on this device. " +
                                "Install Termux and its inetutils package to use ping there." +
                                (lastError == null ? "" : Tuils.NEWLINE + lastError.getMessage());
                    }
                });
                return "Ping started for " + host + " (4 packets maximum)";
            }
        },
        trace {
            @Override public int[] args() { return new int[]{CommandAbstraction.PLAIN_TEXT}; }
            @Override public String exec(ExecutePack pack) {
                final Context context = pack.context.getApplicationContext();
                final String host = readHost(pack);
                if (host == null) return hostError(context);
                runDiagnostic(context, new Diagnostic() {
                    @Override public String run() throws Exception {
                        InetAddress destination = InetAddress.getByName(host);
                        String numericHost = destination.getHostAddress();
                        String[] options = new String[]{"-m", "8", "-w", "1", "-q", "1", numericHost};
                        String[][] candidates = new String[][]{
                                {"/system/bin/traceroute", options[0], options[1], options[2], options[3], options[4], options[5], options[6]},
                                {"/system/xbin/traceroute", options[0], options[1], options[2], options[3], options[4], options[5], options[6]},
                                {"/system/bin/toybox", "traceroute", options[0], options[1], options[2], options[3], options[4], options[5], options[6]}
                        };
                        IOException lastError = null;
                        for (String[] command : candidates) {
                            try {
                                Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                                StringBuilder output = new StringBuilder("Traceroute to ").append(host)
                                        .append(" (").append(numericHost).append(")").append(Tuils.NEWLINE);
                                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (output.length() < 5000) output.append(line).append(Tuils.NEWLINE);
                                }
                                process.waitFor();
                                return output.toString().trim();
                            } catch (IOException e) {
                                lastError = e;
                            }
                        }
                        return "Traceroute utility is not available in this Android build. Install Termux and run " +
                                "pkg install traceroute, then use: termux traceroute " + host +
                                (lastError == null ? "" : Tuils.NEWLINE + lastError.getMessage());
                    }
                });
                return "Traceroute started for " + host + " (8 hops maximum)";
            }
        },
        help {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return pack.context.getString(R.string.help_cyber); }
        };

        static CyberParam get(String value) {
            if (value == null) return null;
            String normalized = value.toLowerCase(Locale.US);
            for (CyberParam param : values()) {
                if (normalized.endsWith(param.label())) return param;
            }
            return null;
        }

        static String[] labels() {
            CyberParam[] values = values();
            String[] labels = new String[values.length];
            for (int i = 0; i < values.length; i++) labels[i] = values[i].label();
            return labels;
        }

        @Override public String label() { return Tuils.MINUS + name(); }
        @Override public String onNotArgEnough(ExecutePack pack, int n) { return pack.context.getString(R.string.help_cyber); }
        @Override public String onArgNotFound(ExecutePack pack, int index) { return pack.context.getString(R.string.help_cyber); }
    }

    private interface Diagnostic { String run() throws Exception; }

    @Override protected Param paramForString(MainPack pack, String param) {
        return CyberParam.get(param);
    }

    @Override public String[] params() { return CyberParam.labels(); }

    @Override protected String doThings(ExecutePack pack) { return null; }

    @Override public int priority() { return 3; }

    @Override public int helpRes() { return R.string.help_cyber; }

    private static String readHost(ExecutePack pack) {
        Object value = pack.get(Object.class, 1);
        if (value == null) return null;
        String host = value.toString().trim();
        if (host.length() == 0 || host.length() > 253 || host.matches(".*\\s+.*") || host.startsWith("-")) return null;
        // Hostnames, IPv4 and IPv6 literals only; no shell metacharacters or URL syntax.
        if (!host.matches("[A-Za-z0-9._:%-]+")) return null;
        return host;
    }

    private static String hostError(Context context) {
        return "Invalid host. Enter one hostname or IP address, for example example.com or 1.1.1.1.\n" +
                context.getString(R.string.help_cyber);
    }

    private static void runDiagnostic(final Context context, final Diagnostic diagnostic) {
        new AsyncTask<Void, Void, String>() {
            @Override protected String doInBackground(Void... ignored) {
                try {
                    return diagnostic.run();
                } catch (Exception e) {
                    return "Diagnostic failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                }
            }
            @Override protected void onPostExecute(String result) {
                Tuils.sendOutput(context, result);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private static String localAddresses(Context context) {
        StringBuilder out = new StringBuilder("Local network addresses");
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return out.append(Tuils.NEWLINE).append("No network interfaces found.").toString();
            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;
                List<InetAddress> addresses = Collections.list(networkInterface.getInetAddresses());
                for (InetAddress address : addresses) {
                    if (address.isLoopbackAddress()) continue;
                    out.append(Tuils.NEWLINE).append(networkInterface.getName()).append(": ")
                            .append(address.getHostAddress());
                }
            }
        } catch (Exception e) {
            return "Unable to read local addresses: " + e.getMessage();
        }
        if (out.toString().equals("Local network addresses")) out.append(Tuils.NEWLINE).append("No active non-loopback address found.");
        return out.toString();
    }

    private static String networkSummary(Context context) {
        StringBuilder out = new StringBuilder("Network status");
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return "Connectivity service is unavailable.";
            NetworkInfo active = manager.getActiveNetworkInfo();
            if (active == null || !active.isConnected()) {
                out.append(Tuils.NEWLINE).append("Connection: offline");
                return out.toString();
            }
            out.append(Tuils.NEWLINE).append("Connection: ").append(active.getTypeName())
                    .append(" (").append(active.getSubtypeName()).append(")")
                    .append(Tuils.NEWLINE).append("State: ").append(active.getState());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = manager.getActiveNetwork();
                LinkProperties properties = network == null ? null : manager.getLinkProperties(network);
                if (properties != null) {
                    out.append(Tuils.NEWLINE).append("DNS servers:");
                    if (properties.getDnsServers().isEmpty()) out.append(" none");
                    for (InetAddress dns : properties.getDnsServers()) out.append(Tuils.NEWLINE).append("  ").append(dns.getHostAddress());
                    out.append(Tuils.NEWLINE).append("Routes: ").append(properties.getRoutes().size());
                }
            } else {
                out.append(Tuils.NEWLINE).append("DNS details require Android 6.0 or newer.");
            }
        } catch (SecurityException e) {
            out.append(Tuils.NEWLINE).append("Permission denied while reading network state.");
        } catch (Exception e) {
            out.append(Tuils.NEWLINE).append("Unable to read network state: ").append(e.getMessage());
        }
        return out.toString();
    }
}
