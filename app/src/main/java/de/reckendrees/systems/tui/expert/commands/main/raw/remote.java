package de.reckendrees.systems.tui.expert.commands.main.raw;

import de.reckendrees.systems.tui.expert.R;
import de.reckendrees.systems.tui.expert.commands.ExecutePack;
import de.reckendrees.systems.tui.expert.commands.main.MainPack;
import de.reckendrees.systems.tui.expert.commands.main.Param;
import de.reckendrees.systems.tui.expert.commands.main.specific.ParamCommand;
import de.reckendrees.systems.tui.expert.managers.remote.RemoteServerService;
import de.reckendrees.systems.tui.expert.managers.xml.XMLPrefsManager;
import de.reckendrees.systems.tui.expert.managers.xml.options.Expert;
import de.reckendrees.systems.tui.expert.tuils.Tuils;

import java.util.Locale;

/** Controls the authenticated local Wi-Fi T-UI console and private share folder. */
public class remote extends ParamCommand {
    private enum RemoteParam implements Param {
        start {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) {
                XMLPrefsManager.XMLPrefsRoot.EXPERT.write(Expert.remote_autostart, "true");
                RemoteServerService.start(pack.context);
                return "Starting the T-UI LAN remote server. Connect to Wi-Fi, then use remote -status and remote -credentials.";
            }
        },
        stop {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) {
                XMLPrefsManager.XMLPrefsRoot.EXPERT.write(Expert.remote_autostart, "false");
                RemoteServerService.stop(pack.context);
                return "LAN remote server stopped. Auto-start disabled.";
            }
        },
        status {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return RemoteServerService.statusText(pack.context); }
        },
        credentials {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return RemoteServerService.credentialsText(pack.context); }
        },
        autoon {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) {
                XMLPrefsManager.XMLPrefsRoot.EXPERT.write(Expert.remote_autostart, "true");
                RemoteServerService.start(pack.context);
                return "LAN remote auto-start enabled.";
            }
        },
        autooff {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) {
                XMLPrefsManager.XMLPrefsRoot.EXPERT.write(Expert.remote_autostart, "false");
                return "LAN remote auto-start disabled. The current server remains active until remote -stop.";
            }
        },
        help {
            @Override public int[] args() { return new int[0]; }
            @Override public String exec(ExecutePack pack) { return pack.context.getString(R.string.help_remote); }
        };

        static RemoteParam get(String value) {
            if (value == null) return null;
            String normalized = value.toLowerCase(Locale.US);
            for (RemoteParam param : values()) if (normalized.endsWith(param.label())) return param;
            return null;
        }
        static String[] labels() {
            RemoteParam[] values = values();
            String[] labels = new String[values.length];
            for (int i = 0; i < values.length; i++) labels[i] = values[i].label();
            return labels;
        }
        @Override public String label() { return Tuils.MINUS + name(); }
        @Override public String onNotArgEnough(ExecutePack pack, int index) { return pack.context.getString(R.string.help_remote); }
        @Override public String onArgNotFound(ExecutePack pack, int index) { return pack.context.getString(R.string.help_remote); }
    }

    @Override protected Param paramForString(MainPack pack, String param) { return RemoteParam.get(param); }
    @Override public String[] params() { return RemoteParam.labels(); }
    @Override protected String doThings(ExecutePack pack) { return null; }
    @Override public int priority() { return 5; }
    @Override public int helpRes() { return R.string.help_remote; }
}
