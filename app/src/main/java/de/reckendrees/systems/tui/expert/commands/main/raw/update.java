package de.reckendrees.systems.tui.expert.commands.main.raw;

import de.reckendrees.systems.tui.expert.R;
import de.reckendrees.systems.tui.expert.commands.CommandAbstraction;
import de.reckendrees.systems.tui.expert.commands.ExecutePack;
import de.reckendrees.systems.tui.expert.commands.main.MainPack;

/**
 * Update command for T-UI Expert X.
 *
 * The upstream updater downloaded an APK from the predecessor project and a
 * third-party host. That package has a different application ID, so it must
 * not be offered as an update to this independently packaged fork.
 * Configure an official T-UI Expert X release endpoint before enabling
 * automated update downloads.
 */
public class update implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) {
        MainPack info = (MainPack) pack;
        return info.context.getString(R.string.update_not_configured);
    }

    @Override
    public int[] argType() {
        return new int[0];
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public int helpRes() {
        return R.string.help_update;
    }

    @Override
    public String onArgNotFound(ExecutePack info, int index) {
        return null;
    }

    @Override
    public String onNotArgEnough(ExecutePack info, int nArgs) {
        return null;
    }
}
