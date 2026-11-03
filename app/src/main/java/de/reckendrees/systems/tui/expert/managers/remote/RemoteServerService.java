package de.reckendrees.systems.tui.expert.managers.remote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.webkit.MimeTypeMap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.InterfaceAddress;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import de.reckendrees.systems.tui.expert.BuildConfig;
import de.reckendrees.systems.tui.expert.LauncherActivity;
import de.reckendrees.systems.tui.expert.MainManager;
import de.reckendrees.systems.tui.expert.R;
import de.reckendrees.systems.tui.expert.managers.xml.XMLPrefsManager;
import de.reckendrees.systems.tui.expert.managers.xml.options.Expert;
import de.reckendrees.systems.tui.expert.tuils.PrivateIOReceiver;
import de.reckendrees.systems.tui.expert.tuils.Tuils;

import fi.iki.elonen.NanoHTTPD;

/**
 * Authenticated, Wi-Fi-only remote T-UI console and file-sharing service.
 * The file browser is intentionally confined to Download/TUI-Share; it never
 * exposes expert.xml, alias.txt, or the rest of shared storage through HTTP.
 */
public class RemoteServerService extends Service {
    public static final String ACTION_STOP = BuildConfig.APPLICATION_ID + ".REMOTE_SERVER_STOP";
    private static final String PREFS = "tuix_remote_server";
    private static final String CHANNEL_ID = "tuix_lan_remote";
    private static final int NOTIFICATION_ID = 8461;
    private static final long MAX_FILE_BYTES = 50L * 1024L * 1024L;
    private static final int MAX_LIST_ITEMS = 500;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RemoteHttpServer server;
    private File shareRoot;
    private String hostAddress;
    private String username;
    private String password;
    private int port;
    private BroadcastReceiver outputReceiver;
    private BroadcastReceiver networkReceiver;
    private final ArrayDeque<OutputLine> outputLines = new ArrayDeque<>();
    private final AtomicLong outputSequence = new AtomicLong(0L);

    private static final class OutputLine {
        final long sequence;
        final String text;
        OutputLine(long sequence, String text) { this.sequence = sequence; this.text = text; }
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, RemoteServerService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static void stop(Context context) {
        // stopService is safe from both foreground and background contexts and
        // avoids starting a new background service merely to stop the old one.
        context.stopService(new Intent(context, RemoteServerService.class));
    }

    public static void startIfEnabled(Context context) {
        try {
            if (XMLPrefsManager.getBoolean(Expert.remote_autostart) && isWifiConnected(context)) start(context);
        } catch (Exception ignored) {
            // The launcher must remain usable even if a network service cannot start.
        }
    }

    public static boolean isWifiConnected(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = manager == null ? null : manager.getActiveNetworkInfo();
            return info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI;
        } catch (SecurityException e) {
            return false;
        }
    }

    public static String credentialsText(Context context) {
        String user = safeConfig(XMLPrefsManager.get(Expert.remote_user), "tuiadmin");
        String pass = getRemotePassword(context);
        String portText = safeConfig(XMLPrefsManager.get(Expert.remote_port), "8765");
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String host = prefs.getString("host", "");
        int actualPort = prefs.getInt("port", parsePort(portText));
        String shareLabel = prefs.getString("share_label", "Download/TUI-Share");
        if (pass.length() == 0) return "Remote password is not initialized yet. Run remote -start while connected to Wi-Fi.";
        return "T-UI LAN REMOTE CREDENTIALS\nURL: " + (host.length() == 0 ? "Connect to Wi-Fi and run remote -start" : "http://" + host + ":" + actualPort)
                + "\nUsername: " + user + "\nPassword: " + pass
                + "\nShare folder: " + shareLabel + "\nUse only on a trusted Wi-Fi network.";
    }

    public static String statusText(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean running = prefs.getBoolean("running", false);
        String host = prefs.getString("host", "");
        int port = prefs.getInt("port", 8765);
        if (running && host.length() > 0) return "LAN remote server: RUNNING\nURL: http://" + host + ":" + port
                + "\nShare folder: " + prefs.getString("share_label", "Download/TUI-Share")
                + "\nAuthentication: username + private access password";
        return "LAN remote server: STOPPED\nAuto-start: " + XMLPrefsManager.get(Expert.remote_autostart)
                + "\nConnect to Wi-Fi, then run remote -start.";
    }

    @Override public void onCreate() {
        super.onCreate();
        startForeground(NOTIFICATION_ID, buildNotification("Starting T-UI LAN remote server…"));
        outputReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                CharSequence text = intent.getCharSequenceExtra(PrivateIOReceiver.TEXT);
                if (text == null) return;
                synchronized (outputLines) {
                    outputLines.addLast(new OutputLine(outputSequence.incrementAndGet(), text.toString()));
                    while (outputLines.size() > 250) outputLines.removeFirst();
                    outputLines.notifyAll();
                }
            }
        };
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(
                outputReceiver, new IntentFilter(PrivateIOReceiver.ACTION_OUTPUT));

        networkReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!isWifiConnected(RemoteServerService.this)) {
                    stopSelf();
                    return;
                }
                try {
                    String current = findWifiIpv4(RemoteServerService.this);
                    if (server != null && current != null && !current.equals(hostAddress)) {
                        try { server.stop(); } catch (Exception ignored) {}
                        server = null;
                        startServer();
                    }
                } catch (Exception ignored) {
                    stopSelf();
                }
            }
        };
        IntentFilter networkFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver, networkFilter);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            XMLPrefsManager.XMLPrefsRoot.EXPERT.write(Expert.remote_autostart, "false");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (server != null) return START_STICKY;
        startServer();
        return START_STICKY;
    }

    private void startServer() {
        if (!isWifiConnected(this)) {
            Tuils.sendOutput(this, "LAN remote server needs an active Wi-Fi connection. Connect to Wi-Fi and run remote -start.");
            stopSelf();
            return;
        }
        try {
            hostAddress = findWifiIpv4(this);
            if (hostAddress == null) throw new IllegalStateException("Could not determine the Wi-Fi IPv4 address.");
            username = safeConfig(XMLPrefsManager.get(Expert.remote_user), "tuiadmin");
            password = getRemotePassword(this);
            if (configuredRemotePassword().length() == 0) {
                Tuils.sendOutput(this, "A strong LAN remote password is stored privately in this app. Run remote -credentials to view it.");
            }
            port = parsePort(XMLPrefsManager.get(Expert.remote_port));
            shareRoot = createShareRoot();
            if (!shareRoot.isDirectory() && !shareRoot.mkdirs()) throw new IllegalStateException("Cannot create the TUI-Share folder.");
            String shareLabel = shareRoot.getAbsolutePath().contains("/Download/")
                    ? "Download/TUI-Share" : "App Documents/TUI-Share";

            server = new RemoteHttpServer(hostAddress, port, this);
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().putBoolean("running", true).putString("host", hostAddress)
                    .putString("share_label", shareLabel).putInt("port", server.getListeningPort()).apply();
            updateNotification("T-UI LAN remote: http://" + hostAddress + ":" + server.getListeningPort());
            Tuils.sendOutput(this, "T-UI LAN REMOTE SERVER READY\nhttp://" + hostAddress + ":" + server.getListeningPort()
                    + "\nUsername: " + username + "\nUse remote -credentials to view the password.\nFiles: " + shareLabel);
        } catch (Exception e) {
            Tuils.sendOutput(this, "LAN remote server failed: " + safeMessage(e));
            stopSelf();
        }
    }

    private File createShareRoot() {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File preferred = new File(downloads, "TUI-Share");
        if (preferred.isDirectory() || (!preferred.exists() && preferred.mkdirs())) return preferred;

        // Some Android builds deny creation in public Downloads. Keep the same
        // logical share name in app-specific storage rather than failing startup.
        File appFiles = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (appFiles == null) appFiles = getFilesDir();
        File fallback = new File(appFiles, "TUI-Share");
        if (fallback.isDirectory() || (!fallback.exists() && fallback.mkdirs())) return fallback;
        return new File(getFilesDir(), "TUI-Share");
    }

    @SuppressWarnings("deprecation")
    private static String findWifiIpv4(Context context) throws Exception {
        // First use Android's Wi-Fi connection info; this is more reliable than
        // guessing a vendor-specific network-interface name.
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null && wifi.getConnectionInfo() != null) {
                int ip = wifi.getConnectionInfo().getIpAddress();
                if (ip != 0) {
                    String address = (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "."
                            + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
                    InetAddress parsed = InetAddress.getByName(address);
                    if (parsed instanceof Inet4Address && !parsed.isLoopbackAddress()
                            && !parsed.isLinkLocalAddress()) return address;
                }
            }
        } catch (Throwable ignored) {
            // The interface scan below handles devices where Wi-Fi info is hidden.
        }

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces != null && interfaces.hasMoreElements()) {
            NetworkInterface network = interfaces.nextElement();
            String name = network.getName() == null ? "" : network.getName().toLowerCase(Locale.US);
            if (!(name.startsWith("wlan") || name.startsWith("wifi") || name.startsWith("ap"))) continue;
            if (!network.isUp() || network.isLoopback()) continue;
            Enumeration<InetAddress> addresses = network.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress()) {
                    return address.getHostAddress();
                }
            }
        }
        return null;
    }

    private static String configuredRemotePassword() {
        String configured = XMLPrefsManager.get(Expert.remote_pass);
        return configured == null ? "" : configured;
    }

    private static String getRemotePassword(Context context) {
        String configured = configuredRemotePassword();
        if (configured.length() > 0) return configured; // Preserve existing remote credentials byte-for-byte.
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String generated = prefs.getString("generated_password", "");
        if (generated.length() == 0) {
            generated = createPassword();
            prefs.edit().putString("generated_password", generated).apply();
        }
        return generated;
    }

    private static String createPassword() {
        final char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789-_".toCharArray();
        char[] result = new char[20];
        for (int i = 0; i < result.length; i++) result[i] = alphabet[RANDOM.nextInt(alphabet.length)];
        return new String(result);
    }

    private static int parsePort(String value) {
        try {
            int valueInt = Integer.parseInt(value);
            return valueInt >= 1024 && valueInt <= 65535 ? valueInt : 8765;
        } catch (Exception e) { return 8765; }
    }

    private static String safeConfig(String value, String fallback) {
        return value == null || value.trim().length() == 0 ? fallback : value.trim();
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private Notification buildNotification(String text) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "T-UI LAN Remote", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Shows when the local Wi-Fi remote console is active");
                manager.createNotificationChannel(channel);
            }
        }
        Intent open = new Intent(this, LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent openPending = PendingIntent.getActivity(this, 8461, open, flags);
        Intent stop = new Intent(this, RemoteServerService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = Build.VERSION.SDK_INT >= 26
                ? PendingIntent.getForegroundService(this, 8462, stop, flags)
                : PendingIntent.getService(this, 8462, stop, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(R.mipmap.ic_launcher).setContentTitle("T-UI Expert X — LAN Remote")
                .setContentText(text).setContentIntent(openPending).setOngoing(true)
                .addAction(R.mipmap.ic_launcher, "Stop", stopPending);
        return builder.build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    long currentOutputSequence() { return outputSequence.get(); }

    String waitForOutput(long after, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (outputLines) {
            while (System.currentTimeMillis() < deadline) {
                String text = collectOutput(after);
                if (text.length() > 0) return text;
                try { outputLines.wait(Math.min(150L, Math.max(1L, deadline - System.currentTimeMillis()))); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            return collectOutput(after);
        }
    }

    private String collectOutput(long after) {
        StringBuilder out = new StringBuilder();
        for (OutputLine line : outputLines) {
            if (line.sequence <= after) continue;
            if (out.length() > 0) out.append('\n');
            out.append(line.text);
        }
        return out.toString();
    }

    void executeTuiCommand(String command) {
        Intent intent = new Intent(MainManager.ACTION_EXEC);
        intent.putExtra(MainManager.CMD, command);
        intent.putExtra(MainManager.CMD_COUNT, MainManager.commandCount);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    File safeFile(String relativePath) throws Exception {
        String clean = relativePath == null ? "" : relativePath.trim();
        clean = clean.replace('\\', '/');
        while (clean.startsWith("/")) clean = clean.substring(1);
        File candidate = clean.length() == 0 ? shareRoot : new File(shareRoot, clean);
        String base = shareRoot.getCanonicalPath();
        String resolved = candidate.getCanonicalPath();
        if (!resolved.equals(base) && !resolved.startsWith(base + File.separator)) {
            throw new SecurityException("Path is outside the TUI-Share folder.");
        }
        return candidate;
    }

    File getShareRoot() { return shareRoot; }
    String getHostAddress() { return hostAddress; }
    String getUsername() { return username; }
    String getPassword() { return password; }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        if (server != null) {
            try { server.stop(); } catch (Exception ignored) {}
            server = null;
        }
        if (outputReceiver != null) {
            try { LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(outputReceiver); }
            catch (Exception ignored) {}
        }
        if (networkReceiver != null) {
            try { unregisterReceiver(networkReceiver); } catch (Exception ignored) {}
            networkReceiver = null;
        }
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("running", false).remove("host").apply();
        stopForeground(true);
        super.onDestroy();
    }

    private static final class RemoteHttpServer extends NanoHTTPD {
        private final RemoteServerService service;
        private final Map<String, Long> tokens = Collections.synchronizedMap(new HashMap<String, Long>());
        private long failedWindowStart;
        private int failedLogins;

        RemoteHttpServer(String hostname, int port, RemoteServerService service) {
            super(hostname, port);
            this.service = service;
        }

        @Override public Response serve(IHTTPSession session) {
            String uri = session.getUri() == null ? "/" : session.getUri();
            Method method = session.getMethod();
            try {
                if (!isSameWifiSubnet(session)) {
                    return json(Response.Status.FORBIDDEN, new JSONObject().put("error", "This service accepts devices on the same Wi-Fi subnet only."));
                }
                if ("/".equals(uri) && method == Method.GET) return htmlResponse();
                Map<String, String> files = new HashMap<>();
                Map<String, String> params = session.getParms();

                // Do not parse or store request bodies until the caller is authenticated,
                // except for the small credential form used by the login endpoint.
                if ("/api/login".equals(uri) && method == Method.POST) {
                    if (!validBodyLength(session, 8192L)) return json(Response.Status.BAD_REQUEST, new JSONObject().put("error", "Login request is too large."));
                    session.parseBody(files);
                    params = session.getParms();
                    return login(params);
                }
                if (!isAuthorized(session)) return json(Response.Status.UNAUTHORIZED, new JSONObject().put("error", "Login required"));
                if ("/api/logout".equals(uri) && method == Method.POST) {
                    tokens.remove(bearer(session));
                    return json(Response.Status.OK, new JSONObject().put("ok", true));
                }
                if (method == Method.POST) {
                    long bodyLimit = "/api/upload".equals(uri) ? MAX_FILE_BYTES + 65536L : 16384L;
                    if (!validBodyLength(session, bodyLimit)) return json(Response.Status.BAD_REQUEST, new JSONObject().put("error", "Request body is too large."));
                    session.parseBody(files);
                    params = session.getParms();
                }
                if ("/api/status".equals(uri) && method == Method.GET) return statusResponse();
                if ("/api/command".equals(uri) && method == Method.POST) return commandResponse(params);
                if ("/api/files".equals(uri) && method == Method.GET) return listFiles(params.get("path"));
                if ("/api/upload".equals(uri) && method == Method.POST) return uploadFile(params, files);
                if ("/api/download".equals(uri) && method == Method.GET) return downloadFile(params.get("path"));
                if ("/api/action".equals(uri) && method == Method.POST) return fileAction(params);
                return json(Response.Status.NOT_FOUND, new JSONObject().put("error", "Endpoint not found"));
            } catch (SecurityException e) {
                // JSONObject.put() declares JSONException on this legacy Android API.
                // Do not construct it inside a catch block where the checked exception
                // would otherwise escape the request handler.
                return jsonError(Response.Status.FORBIDDEN, e.getMessage());
            } catch (Exception e) {
                return jsonError(Response.Status.INTERNAL_ERROR, safeMessage(e));
            }
        }

        private Response jsonError(Response.Status status, String message) {
            JSONObject object = new JSONObject();
            try {
                object.put("error", message == null ? "Request failed." : message);
            } catch (org.json.JSONException ignored) {
                // The fallback response remains valid JSON even if insertion fails.
            }
            return json(status, object);
        }

        private boolean validBodyLength(IHTTPSession session, long maximum) {
            String length = session.getHeaders().get("content-length");
            if (length == null) return false; // Require a declared length to prevent unbounded chunked bodies.
            try { return Long.parseLong(length) >= 0L && Long.parseLong(length) <= maximum; }
            catch (Exception e) { return false; }
        }

        private boolean isSameWifiSubnet(IHTTPSession session) {
            try {
                String remoteIp = session.getRemoteIpAddress();
                if (remoteIp == null || service.hostAddress == null) return false;
                InetAddress remote = InetAddress.getByName(remoteIp);
                InetAddress local = InetAddress.getByName(service.hostAddress);
                NetworkInterface network = NetworkInterface.getByInetAddress(local);
                if (network == null) return false;
                for (InterfaceAddress address : network.getInterfaceAddresses()) {
                    InetAddress candidate = address.getAddress();
                    if (candidate instanceof Inet4Address && candidate.equals(local)) {
                        short prefix = address.getNetworkPrefixLength();
                        if (prefix < 0 || prefix > 32) return false;
                        byte[] left = local.getAddress();
                        byte[] right = remote.getAddress();
                        if (left.length != right.length) return false;
                        for (int bit = 0; bit < prefix; bit++) {
                            int mask = 1 << (7 - (bit % 8));
                            if ((left[bit / 8] & mask) != (right[bit / 8] & mask)) return false;
                        }
                        return true;
                    }
                }
            } catch (Exception ignored) {}
            return false;
        }

        private Response login(Map<String, String> params) throws Exception {
            long now = System.currentTimeMillis();
            synchronized (this) {
                if (now - failedWindowStart > 60000L) { failedWindowStart = now; failedLogins = 0; }
                if (failedLogins >= 8) return json(Response.Status.FORBIDDEN, new JSONObject().put("error", "Too many login attempts. Wait one minute."));
            }
            String user = params.get("username");
            String pass = params.get("password");
            boolean validUser = constantEquals(service.username, user);
            boolean validPass = constantEquals(service.password, pass);
            if (!validUser || !validPass) {
                synchronized (this) { failedLogins++; }
                return json(Response.Status.UNAUTHORIZED, new JSONObject().put("error", "Invalid username or password"));
            }
            synchronized (this) { failedLogins = 0; }
            String token = randomToken();
            tokens.put(token, System.currentTimeMillis() + 12L * 60L * 60L * 1000L);
            return json(Response.Status.OK, new JSONObject().put("token", token));
        }

        private Response statusResponse() throws Exception {
            Intent battery = service.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int percent = -1;
            if (battery != null) {
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) percent = Math.round(level * 100f / scale);
            }
            String shareLabel = service.shareRoot != null && service.shareRoot.getAbsolutePath().contains("/Download/")
                    ? "Download/TUI-Share" : "App Documents/TUI-Share";
            return json(Response.Status.OK, new JSONObject().put("battery", percent)
                    .put("host", service.hostAddress).put("port", service.port)
                    .put("share", shareLabel));
        }

        private Response commandResponse(Map<String, String> params) throws Exception {
            String command = params.get("command");
            if (command == null || command.trim().length() == 0) return json(Response.Status.BAD_REQUEST, new JSONObject().put("error", "Enter a T-UI command."));
            if (command.length() > 2000) return json(Response.Status.BAD_REQUEST, new JSONObject().put("error", "Command is too long."));
            long sequence = service.currentOutputSequence();
            service.executeTuiCommand(command.trim());
            String output = service.waitForOutput(sequence, 1400L);
            if (output.length() == 0) output = "Command submitted. No terminal output was received; keep T-UI active on the phone.";
            return json(Response.Status.OK, new JSONObject().put("output", output));
        }

        private Response listFiles(String path) throws Exception {
            File directory = service.safeFile(path);
            if (!directory.isDirectory()) return json(Response.Status.BAD_REQUEST, new JSONObject().put("error", "Not a folder."));
            File[] children = directory.listFiles();
            JSONArray array = new JSONArray();
            if (children != null) {
                java.util.Arrays.sort(children, (a, b) -> {
                    if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                int count = 0;
                for (File child : children) {
                    if (count++ >= MAX_LIST_ITEMS) break;
                    if (child.getName().equals(".") || child.getName().equals("..")) continue;
                    JSONObject item = new JSONObject();
                    item.put("name", child.getName());
                    item.put("directory", child.isDirectory());
                    item.put("size", child.isFile() ? child.length() : 0L);
                    item.put("modified", child.lastModified());
                    array.put(item);
                }
            }
            return json(Response.Status.OK, new JSONObject().put("path", path == null ? "" : path)
                    .put("parent", parentOf(path)).put("items", array));
        }

        private Response uploadFile(Map<String, String> params, Map<String, String> files) throws Exception {
            String temp = files.get("file");
            if (temp == null) temp = files.get("postData");
            if (temp == null) return json(Response.Status.BAD_REQUEST, new JSONObject().put("error", "No upload file received."));
            File incoming = new File(temp);
            if (!incoming.isFile() || incoming.length() > MAX_FILE_BYTES) return json(Response.Status.BAD_REQUEST, new JSONObject().put("error", "Maximum upload size is 50 MB."));
            File targetDir = service.safeFile(params.get("path"));
            if (!targetDir.isDirectory()) return json(Response.Status.BAD_REQUEST, new JSONObject().put("error", "Destination folder not found."));
            String requestedName = params.get("name");
            if (requestedName == null || requestedName.trim().length() == 0) requestedName = incoming.getName();
            String name = new File(requestedName).getName().replaceAll("[^A-Za-z0-9._ -]", "_");
            if (name.length() == 0 || name.equals(".") || name.equals("..")) return json(Response.Status.BAD_REQUEST, new JSONObject().put("error", "Invalid file name."));
            File target = new File(targetDir, name);
            if (target.exists()) return json(Response.Status.CONFLICT, new JSONObject().put("error", "A file with that name already exists."));
            copyFile(incoming, target);
            incoming.delete();
            return json(Response.Status.OK, new JSONObject().put("ok", true).put("name", name));
        }

        private Response downloadFile(String path) throws Exception {
            File file = service.safeFile(path);
            if (!file.isFile() || file.length() > MAX_FILE_BYTES) return json(Response.Status.NOT_FOUND, new JSONObject().put("error", "File not found or larger than 50 MB."));
            String extension = "";
            int dot = file.getName().lastIndexOf('.');
            if (dot >= 0) extension = file.getName().substring(dot + 1).toLowerCase(Locale.US);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime == null) mime = "application/octet-stream";
            Response response = newFixedLengthResponse(Response.Status.OK, mime, new BufferedInputStream(new FileInputStream(file)), file.length());
            String safeName = file.getName().replaceAll("[^A-Za-z0-9._ -]", "_");
            response.addHeader("Content-Disposition", "attachment; filename=\"" + safeName + "\"");
            response.addHeader("Cache-Control", "no-store");
            return response;
        }

        private Response fileAction(Map<String, String> params) throws Exception {
            String action = params.get("action");
            if ("mkdir".equals(action)) {
                File folder = service.safeFile(params.get("path"));
                if (folder.exists() || !folder.mkdirs()) return json(Response.Status.CONFLICT, new JSONObject().put("error", "Could not create folder."));
                return json(Response.Status.OK, new JSONObject().put("ok", true));
            }
            if ("delete".equals(action)) {
                File item = service.safeFile(params.get("path"));
                if (item.equals(service.getShareRoot())) return json(Response.Status.FORBIDDEN, new JSONObject().put("error", "The TUI-Share root cannot be deleted."));
                if (!item.exists()) return json(Response.Status.NOT_FOUND, new JSONObject().put("error", "Item not found."));
                if (item.isDirectory() && item.list() != null && item.list().length > 0) return json(Response.Status.CONFLICT, new JSONObject().put("error", "Folder is not empty."));
                if (!item.delete()) return json(Response.Status.INTERNAL_ERROR, new JSONObject().put("error", "Delete failed."));
                return json(Response.Status.OK, new JSONObject().put("ok", true));
            }
            if ("rename".equals(action)) {
                File source = service.safeFile(params.get("path"));
                File destination = service.safeFile(params.get("newPath"));
                if (source.equals(service.getShareRoot()) || destination.equals(service.getShareRoot())) return json(Response.Status.FORBIDDEN, new JSONObject().put("error", "The TUI-Share root cannot be renamed."));
                if (!source.exists() || destination.exists() || !source.renameTo(destination)) return json(Response.Status.CONFLICT, new JSONObject().put("error", "Rename failed."));
                return json(Response.Status.OK, new JSONObject().put("ok", true));
            }
            if ("copy".equals(action) || "move".equals(action)) {
                File source = service.safeFile(params.get("path"));
                File destination = service.safeFile(params.get("destination"));
                if (source.equals(service.getShareRoot()) || destination.equals(service.getShareRoot())) return json(Response.Status.FORBIDDEN, new JSONObject().put("error", "Invalid file operation."));
                if (!source.isFile() || source.length() > MAX_FILE_BYTES) return json(Response.Status.BAD_REQUEST, new JSONObject().put("error", "Only files up to 50 MB can be copied or moved."));
                if (destination.exists()) return json(Response.Status.CONFLICT, new JSONObject().put("error", "Destination already exists."));
                copyFile(source, destination);
                if ("move".equals(action) && !source.delete()) {
                    destination.delete();
                    return json(Response.Status.INTERNAL_ERROR, new JSONObject().put("error", "Move failed; original file was kept."));
                }
                return json(Response.Status.OK, new JSONObject().put("ok", true));
            }
            return json(Response.Status.BAD_REQUEST, new JSONObject().put("error", "Unknown file action."));
        }

        private static void copyFile(File source, File destination) throws Exception {
            File parent = destination.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("Cannot create destination folder.");
            byte[] buffer = new byte[8192];
            try (InputStream input = new FileInputStream(source); OutputStream output = new FileOutputStream(destination)) {
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            }
        }

        private static String parentOf(String path) {
            if (path == null || path.length() == 0) return "";
            String clean = path.replace('\\', '/');
            int index = clean.lastIndexOf('/');
            return index <= 0 ? "" : clean.substring(0, index);
        }

        private boolean isAuthorized(IHTTPSession session) {
            String token = bearer(session);
            if (token.length() == 0) return false;
            Long expires = tokens.get(token);
            if (expires == null) return false;
            if (expires < System.currentTimeMillis()) { tokens.remove(token); return false; }
            return true;
        }

        private static String bearer(IHTTPSession session) {
            String header = session.getHeaders().get("authorization");
            if (header == null) return "";
            String prefix = "Bearer ";
            return header.regionMatches(true, 0, prefix, 0, prefix.length()) ? header.substring(prefix.length()).trim() : "";
        }

        private static boolean constantEquals(String expected, String actual) throws Exception {
            if (expected == null || actual == null) return false;
            return MessageDigest.isEqual(expected.getBytes("UTF-8"), actual.getBytes("UTF-8"));
        }

        private static String randomToken() {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
        }

        private Response htmlResponse() {
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", PAGE);
        }

        private static Response json(Response.Status status, JSONObject object) {
            Response response = newFixedLengthResponse(status, "application/json; charset=utf-8", object.toString());
            response.addHeader("Cache-Control", "no-store");
            response.addHeader("X-Content-Type-Options", "nosniff");
            return response;
        }

        private static final String PAGE = "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>T-UI Expert X Remote</title><style>"
                + "*{box-sizing:border-box}body{margin:0;background:#050505;color:#d8ffe1;font:14px system-ui,Arial}header{padding:12px 16px;background:#101610;border-bottom:1px solid #1c7d3a;color:#32ff72;font-weight:700;display:flex;justify-content:space-between;gap:10px;flex-wrap:wrap}main{padding:14px;max-width:1200px;margin:auto}.card{background:#0b100d;border:1px solid #285337;border-radius:10px;padding:12px;margin-bottom:12px}.hidden{display:none!important}input,button{font:inherit}input{background:#020402;color:#d8ffe1;border:1px solid #366d48;border-radius:6px;padding:10px;min-width:0}button{background:#12351d;color:#8cffad;border:1px solid #2a9850;border-radius:6px;padding:9px 12px;cursor:pointer}button:active{transform:scale(.98)}.row{display:flex;gap:8px;flex-wrap:wrap}.row input{flex:1}.grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.pane{min-width:0}.path{color:#a8ffbf;overflow-wrap:anywhere;margin:6px 0}.files{max-height:360px;overflow:auto;border:1px solid #233d2b;border-radius:6px}.file{display:flex;justify-content:space-between;gap:8px;padding:9px;border-bottom:1px solid #1b2a20;cursor:pointer}.file.selected{background:#17341f}.file small{color:#90ae99}.terminal{height:230px;overflow:auto;white-space:pre-wrap;word-break:break-word;background:#010301;border:1px solid #1c4128;border-radius:6px;padding:10px;color:#72ff9a}.muted{color:#93a99a;font-size:12px}.danger{border-color:#a44242;color:#ffaaaa}.login{max-width:390px;margin:12vh auto;padding:20px}.login input{width:100%;margin:7px 0}.status{color:#99ffb5}.footer{font-size:11px;color:#829387;margin-top:10px}@media(max-width:720px){.grid{grid-template-columns:1fr}.files{max-height:250px}}</style></head><body>"
                + "<header><span>T-UI EXPERT X / LAN REMOTE</span><span id='status'>AUTHENTICATION REQUIRED</span></header><main>"
                + "<section id='login' class='card login'><h2>Establish local link</h2><p class='muted'>This server is available only on the phone's Wi-Fi address.</p><input id='user' autocomplete='username' placeholder='Username'><input id='pass' type='password' autocomplete='current-password' placeholder='Access password'><button onclick='login()'>CONNECT</button><p id='loginerr' class='danger'></p></section>"
                + "<section id='app' class='hidden'><div class='card'><div class='row'><b class='status' id='deviceStatus'>Connected</b><span class='muted' id='deviceInfo'></span><button class='danger' onclick='logout()'>LOGOUT</button></div><p class='muted'>Remote commands are executed by T-UI on the phone. File sharing is confined to the TUI-Share folder shown below. Use a trusted Wi-Fi network only.</p></div>"
                + "<div class='card'><h3>Remote T-UI terminal</h3><div id='terminal' class='terminal'>[ T-UI Expert X remote session ]</div><form class='row' onsubmit='runCommand(event)'><input id='command' placeholder='Enter a T-UI command, e.g. monitor -battery' autocomplete='off'><button>EXECUTE</button></form></div>"
                + "<div class='card'><h3>File sharing</h3><div class='row'><button onclick='refreshPanes()'>REFRESH</button><span class='muted' id='shareInfo'></span></div><div class='grid'><div class='pane'><b>Pane 1</b><div class='path' id='path1'></div><div class='files' id='files1'></div><div class='row'><button onclick='upload(1)'>UPLOAD</button><button onclick='newFolder(1)'>+ FOLDER</button><button onclick='renameSelected(1)'>RENAME</button><button class='danger' onclick='deleteSelected(1)'>DELETE</button><input id='upload1' type='file' class='hidden' onchange='sendUpload(1)'></div></div><div class='pane'><b>Pane 2</b><div class='path' id='path2'></div><div class='files' id='files2'></div><div class='row'><button onclick='upload(2)'>UPLOAD</button><button onclick='newFolder(2)'>+ FOLDER</button><button onclick='renameSelected(2)'>RENAME</button><button class='danger' onclick='deleteSelected(2)'>DELETE</button><input id='upload2' type='file' class='hidden' onchange='sendUpload(2)'></div></div></div><div class='row'><button onclick='transfer(1,&quot;copy&quot;)'>COPY 1 → 2</button><button onclick='transfer(1,&quot;move&quot;)'>MOVE 1 → 2</button><button onclick='transfer(2,&quot;copy&quot;)'>COPY 2 → 1</button><button onclick='transfer(2,&quot;move&quot;)'>MOVE 2 → 1</button></div></div></section>"
                + "</main><script>let token=sessionStorage.getItem('tuix_remote_token')||'';let paths={1:'',2:''};let selected={1:null,2:null};"
                + "const el=id=>document.getElementById(id);async function api(url,opt={}){opt.headers=Object.assign({'Authorization':'Bearer '+token},opt.headers||{});const r=await fetch(url,opt);if(r.status===401){logout();throw new Error('Session expired. Please log in again.')}if(!r.ok){let d={};try{d=await r.json()}catch(e){}throw new Error(d.error||('HTTP '+r.status))}return r;}function form(data){return new URLSearchParams(data)}"
                + "function showApp(){el('login').classList.add('hidden');el('app').classList.remove('hidden');el('status').textContent='AUTHENTICATED';refreshPanes();refreshStatus()}async function logout(){const old=token;token='';sessionStorage.removeItem('tuix_remote_token');if(old){try{await fetch('/api/logout',{method:'POST',headers:{'Authorization':'Bearer '+old}})}catch(e){}}el('app').classList.add('hidden');el('login').classList.remove('hidden');el('status').textContent='AUTHENTICATION REQUIRED'}async function login(){try{let r=await fetch('/api/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:form({username:el('user').value,password:el('pass').value})});let d=await r.json();if(!r.ok)throw new Error(d.error||'Login failed');token=d.token;sessionStorage.setItem('tuix_remote_token',token);el('loginerr').textContent='';el('pass').value='';showApp()}catch(e){el('loginerr').textContent=e.message}}"
                + "async function refreshStatus(){try{let d=await (await api('/api/status')).json();el('deviceInfo').textContent='Battery: '+(d.battery<0?'--':d.battery+'%')+' • '+d.host+':'+d.port;el('shareInfo').textContent='Share root: '+d.share}catch(e){el('deviceStatus').textContent=e.message}}function appendOutput(s){let t=el('terminal');t.textContent+='\\n$ '+s;t.scrollTop=t.scrollHeight}async function runCommand(e){e.preventDefault();let c=el('command').value.trim();if(!c)return;appendOutput(c);el('command').value='';try{let r=await api('/api/command',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:form({command:c})});let d=await r.json();appendOutput(d.output||'(no output)')}catch(err){appendOutput('ERROR: '+err.message)}}"
                + "async function listPane(n){try{let d=await (await api('/api/files?path='+encodeURIComponent(paths[n]))).json();paths[n]=d.path||'';selected[n]=null;el('path'+n).textContent='/TUI-Share/'+paths[n];let box=el('files'+n);box.textContent='';if(paths[n]){let up=document.createElement('div');up.className='file';up.textContent='.. (Parent)';up.onclick=()=>{paths[n]=d.parent||'';listPane(n)};box.appendChild(up)}(d.items||[]).forEach(it=>{let row=document.createElement('div');row.className='file';let name=document.createElement('span');name.textContent=(it.directory?'📁 ':'📄 ')+it.name;let size=document.createElement('small');size.textContent=it.directory?'FOLDER':(it.size+' bytes');row.appendChild(name);row.appendChild(size);row.onclick=()=>{if(it.directory){paths[n]=paths[n]?paths[n]+'/'+it.name:it.name;listPane(n)}else{selected[n]=it;Array.from(box.children).forEach(x=>x.classList.remove('selected'));row.classList.add('selected')}};row.ondblclick=()=>{if(!it.directory)downloadFile(paths[n]?paths[n]+'/'+it.name:it.name)};box.appendChild(row)})}catch(e){appendOutput('FILE ERROR: '+e.message)}}async function refreshPanes(){await Promise.all([listPane(1),listPane(2)])}"
                + "async function upload(n){el('upload'+n).click()}async function sendUpload(n){let f=el('upload'+n).files[0];if(!f)return;if(f.size>52428800){alert('Maximum file size is 50 MB');return}let fd=new FormData();fd.append('file',f);fd.append('name',f.name);fd.append('path',paths[n]);try{await api('/api/upload',{method:'POST',body:fd});appendOutput('Uploaded '+f.name);listPane(n)}catch(e){alert(e.message)}el('upload'+n).value=''}async function newFolder(n){let name=prompt('Folder name:');if(!name)return;try{await api('/api/action',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:form({action:'mkdir',path:(paths[n]?paths[n]+'/':'')+name})});listPane(n)}catch(e){alert(e.message)}}async function renameSelected(n){let it=selected[n];if(!it)return alert('Select an item first');let name=prompt('New name:',it.name);if(!name||name===it.name)return;let oldPath=(paths[n]?paths[n]+'/':'')+it.name;let newPath=(paths[n]?paths[n]+'/':'')+name;try{await api('/api/action',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:form({action:'rename',path:oldPath,newPath:newPath})});listPane(n)}catch(e){alert(e.message)}}async function deleteSelected(n){let it=selected[n];if(!it)return alert('Select a file or empty folder first');if(!confirm('Delete '+it.name+'?'))return;try{await api('/api/action',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:form({action:'delete',path:(paths[n]?paths[n]+'/':'')+it.name})});listPane(n)}catch(e){alert(e.message)}}"
                + "async function transfer(n,action){let it=selected[n];if(!it)return alert('Select a file in the source pane first');if(it.directory)return alert('Folder transfer is not enabled; choose a file.');let dst=n===1?2:1;let src=(paths[n]?paths[n]+'/':'')+it.name;let dest=(paths[dst]?paths[dst]+'/':'')+it.name;try{await api('/api/action',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:form({action:action,path:src,destination:dest})});listPane(n);listPane(dst)}catch(e){alert(e.message)}}async function downloadFile(path){try{let r=await api('/api/download?path='+encodeURIComponent(path));let b=await r.blob();let a=document.createElement('a');a.href=URL.createObjectURL(b);a.download=path.split('/').pop();a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1500)}catch(e){alert(e.message)}}"
                + "if(token)showApp();setInterval(()=>{if(token)refreshStatus()},10000);</script></body></html>";
    }
}
