package de.reckendrees.systems.tui.expert.managers;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.reckendrees.systems.tui.expert.managers.xml.XMLPrefsManager;
import de.reckendrees.systems.tui.expert.tuils.Tuils;

/**
 * Installs the user's T-UI Expert X terminal profile from APK assets.
 * Missing files are seeded automatically. Only recognized legacy stock defaults
 * for the requested app suggestion strip are migrated, after backing up the
 * original suggestions.xml. Explicit application backs up files first and never
 * overwrites expert.xml or alias.txt, preserving remote credentials and aliases.
 */
public final class ProfileInstaller {
    private static final String ASSET_ROOT = "default_profile";
    private static final String[] PROFILE_EXPERT_VALUES = new String[]{
            "custom_command", "custom_command_timeout", "custom_command_size",
            "custom_command_index", "show_custom_command"
    };
    private static final String[] PROFILE_EXPERT_DEFAULTS = new String[]{
            "head -n 11 /sdcard/t-ui/header", "600", "5", "4", "true"
    };

    private ProfileInstaller() {}

    /** Seed absent files and safely migrate only the known legacy home-screen defaults. */
    public static int seedMissing(Context context, File destination) {
        if (context == null || destination == null) return 0;
        if (!destination.exists() && !destination.mkdirs()) return 0;
        int copied = copyTree(context.getAssets(), ASSET_ROOT, destination, null, true, true);
        copied += seedNerdFontIfNoCustomFont(context, destination);
        migrateRequestedHomeBehaviour(destination);
        return copied;
    }

    /**
     * Restore the reference APK's Nerd Font only when the user has no font of
     * their own. T-UI renders alias names as text, so existing Nerd Font glyph
     * shortcuts need the font file to be available in the user T-UI folder.
     */
    private static int seedNerdFontIfNoCustomFont(Context context, File destination) {
        File[] files = destination.listFiles();
        if (files == null) return 0;
        for (File file : files) {
            if (file != null && file.isFile() && file.getName().toLowerCase(Locale.US).matches(".+\\.(ttf|otf)$")) {
                return 0; // Preserve every font already selected by the user.
            }
        }
        File target = new File(destination, "NerdFont.ttf");
        if (target.exists()) return 0;
        try (InputStream input = context.getAssets().open("NerdFont.ttf");
             FileOutputStream output = new FileOutputStream(target)) {
            copy(input, output);
            return 1;
        } catch (Exception e) {
            Tuils.log(e);
            return 0;
        }
    }

    /**
     * Migrate only the old bundled defaults that conflict with the requested
     * T-UI home-screen app strip. Any non-default values chosen by the user are
     * retained. expert.xml and alias.txt are intentionally never accessed here.
     */
    private static void migrateRequestedHomeBehaviour(File destination) {
        File behavior = new File(destination, "behavior.xml");
        if (readXmlValue(behavior, "clear_before_command") == null) {
            XMLPrefsManager.set(behavior, "clear_before_command",
                    new String[]{XMLPrefsManager.VALUE_ATTRIBUTE}, new String[]{"true"});
        }

        File suggestions = new File(destination, "suggestions.xml");
        String emptyOrder = readXmlValue(suggestions, "noinput_suggestions_order");
        String hideEmpty = readXmlValue(suggestions, "hide_suggestions_when_empty");
        // The previous Phase-5 profile accidentally set the alias count to zero,
        // which hid the user's custom icon/alias row. Restore the stock alias-first
        // suggestion strip only for known bundled defaults; preserve user ordering.
        boolean migrateOrder = emptyOrder == null
                || "0(8)1(0)2(0)3(0)".equals(emptyOrder.trim());
        boolean migrateVisibility = hideEmpty == null || "always".equalsIgnoreCase(hideEmpty.trim());
        if ((migrateOrder || migrateVisibility) && suggestions.isFile()) {
            File backup = new File(destination, "phase5-home-suggestions-original.xml");
            if (!backup.exists()) {
                try {
                    copyFile(suggestions, backup);
                } catch (Exception e) {
                    Tuils.log(e);
                    return; // Do not modify a user's existing settings if backup failed.
                }
            }
        }
        if (migrateOrder) {
            XMLPrefsManager.set(suggestions, "noinput_suggestions_order",
                    new String[]{XMLPrefsManager.VALUE_ATTRIBUTE}, new String[]{"0(0)1(15)2(3)3(5)"});
        }
        if (migrateVisibility) {
            XMLPrefsManager.set(suggestions, "hide_suggestions_when_empty",
                    new String[]{XMLPrefsManager.VALUE_ATTRIBUTE}, new String[]{"false"});
        }
    }

    /**
     * Explicitly apply the screenshot profile. Existing profile files are backed
     * up. expert.xml is never copied/replaced; only five visual custom-command
     * entries are updated via XMLPrefsManager so all remote fields stay intact.
     */
    public static String applyScreenshotProfile(Context context, File destination) {
        if (context == null || destination == null) return "T-UI folder is unavailable.";
        if (!destination.exists() && !destination.mkdirs()) return "Unable to create the T-UI folder.";

        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
        File backup = new File(destination, "phase5-profile-backup-" + stamp);
        if (!backup.mkdirs()) return "Unable to create a profile backup. No files were changed.";

        // Preserve an existing weather API key without ever displaying it or shipping it in the APK.
        String existingWeatherKey = readXmlValue(new File(destination, "behavior.xml"), "weather_key");

        int copied;
        try {
            // Back up all files that will be replaced, plus expert.xml locally because
            // it contains remote-access credentials. This backup is not exported.
            backupExistingAssets(context.getAssets(), ASSET_ROOT, destination, backup);
            File currentExpert = new File(destination, "expert.xml");
            if (currentExpert.isFile()) copyFile(currentExpert, new File(backup, "expert.xml"));
            copied = copyTree(context.getAssets(), ASSET_ROOT, destination, null, false, false);
        } catch (Exception e) {
            return "Profile backup/copy failed. No credentials were exported; inspect: " + backup.getAbsolutePath();
        }

        if (existingWeatherKey != null && existingWeatherKey.length() > 0) {
            String error = XMLPrefsManager.set(new File(destination, "behavior.xml"), "weather_key",
                    new String[]{XMLPrefsManager.VALUE_ATTRIBUTE}, new String[]{existingWeatherKey});
            if (error != null && error.length() > 0) {
                return "Profile files copied, but existing weather-key preservation needs review. Backup: " + backup.getAbsolutePath();
            }
        }

        File expert = new File(destination, "expert.xml");
        if (!expert.isFile()) {
            // Seed a minimal non-secret expert file only when none exists.
            try (InputStream in = context.getAssets().open(ASSET_ROOT + "/expert.xml");
                 FileOutputStream out = new FileOutputStream(expert)) {
                copy(in, out);
            } catch (Exception e) {
                return "Visual profile copied, but a fresh expert.xml could not be initialized. Backup: " + backup.getAbsolutePath();
            }
        }

        for (int i = 0; i < PROFILE_EXPERT_VALUES.length; i++) {
            String result = XMLPrefsManager.set(expert, PROFILE_EXPERT_VALUES[i],
                    new String[]{XMLPrefsManager.VALUE_ATTRIBUTE}, new String[]{PROFILE_EXPERT_DEFAULTS[i]});
            if (result != null && result.length() > 0) {
                return "Profile copied, but custom status-banner settings could not be saved. Remote settings were not targeted. Backup: " + backup.getAbsolutePath();
            }
        }

        return "Screenshot profile applied: " + copied + " files." + Tuils.NEWLINE +
                "Backup: " + backup.getAbsolutePath() + Tuils.NEWLINE +
                "Remote-access values in expert.xml and existing alias.txt were left untouched." + Tuils.NEWLINE +
                "Run 'restart' to reload the layout.";
    }

    private static int copyTree(AssetManager assets, String assetPath, File destination,
                                File backupRoot, boolean onlyMissing, boolean includeExpertOnSeed) {
        int copied = 0;
        try {
            String[] children = assets.list(assetPath);
            if (children == null) return 0;
            if (children.length > 0) {
                for (String child : children) {
                    if (child == null || child.length() == 0 || child.contains("..")) continue;
                    if (!includeExpertOnSeed && "expert.xml".equals(child)) continue;
                    if ("PROFILE_INFO.txt".equals(child)) continue;
                    copied += copyTree(assets, assetPath + "/" + child,
                            new File(destination, child), backupRoot, onlyMissing, includeExpertOnSeed);
                }
                return copied;
            }

            // Asset files and destination paths are restricted to the bundled profile tree.
            String relative = assetPath.substring(ASSET_ROOT.length()).replaceFirst("^/", "");
            if (relative.length() == 0 || relative.contains("..") || relative.startsWith("/") ||
                    "alias.txt".equals(relative) || "crash.txt".equals(relative)) return 0;
            if (!includeExpertOnSeed && "expert.xml".equals(relative)) return 0;
            if ("PROFILE_INFO.txt".equals(relative)) return 0;

            File target = destination;
            if (onlyMissing && target.exists()) return 0;
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return 0;

            if (!onlyMissing && target.exists() && backupRoot != null) {
                String relativeTarget = relative;
                File backup = new File(backupRoot, relativeTarget);
                File backupParent = backup.getParentFile();
                if (backupParent != null && !backupParent.exists() && !backupParent.mkdirs()) {
                    throw new IllegalStateException("Unable to create backup folder");
                }
                copyFile(target, backup);
            }

            try (InputStream in = assets.open(assetPath); FileOutputStream out = new FileOutputStream(target)) {
                copy(in, out);
            }
            if ("script/sysinfo".equals(relative)) target.setReadable(true, false);
            copied++;
        } catch (Exception e) {
            Tuils.log(e);
            if (!onlyMissing) throw new IllegalStateException("Unable to apply bundled profile asset: " + assetPath, e);
        }
        return copied;
    }

    private static int backupExistingAssets(AssetManager assets, String assetPath,
                                            File destination, File backupRoot) throws Exception {
        int backedUp = 0;
        String[] children = assets.list(assetPath);
        if (children == null) return 0;
        if (children.length > 0) {
            for (String child : children) {
                if (child == null || child.length() == 0 || child.contains("..") ||
                        "expert.xml".equals(child) || "PROFILE_INFO.txt".equals(child)) continue;
                backedUp += backupExistingAssets(assets, assetPath + "/" + child,
                        new File(destination, child), backupRoot);
            }
            return backedUp;
        }
        String relative = assetPath.substring(ASSET_ROOT.length()).replaceFirst("^/", "");
        if (relative.length() == 0 || relative.contains("..") || relative.startsWith("/") ||
                "alias.txt".equals(relative) || "crash.txt".equals(relative)) return 0;
        File target = destination;
        if (!target.isFile()) return 0;
        File backup = new File(backupRoot, relative);
        File parent = backup.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("Unable to create backup directory");
        copyFile(target, backup);
        return 1;
    }

    private static String readXmlValue(File file, String tag) {
        if (file == null || !file.isFile() || tag == null || !tag.matches("[A-Za-z0-9_]+")) return null;
        try {
            byte[] bytes = new byte[(int) Math.min(file.length(), 1024L * 1024L)];
            try (FileInputStream in = new FileInputStream(file)) {
                int offset = 0, count;
                while (offset < bytes.length && (count = in.read(bytes, offset, bytes.length - offset)) > 0) offset += count;
                String xml = new String(bytes, 0, offset, "UTF-8");
                Pattern pattern = Pattern.compile("<" + Pattern.quote(tag) + "\\b[^>]*\\bvalue\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
                Matcher matcher = pattern.matcher(xml);
                return matcher.find() ? matcher.group(1) : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static void copyFile(File source, File destination) throws Exception {
        try (FileInputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(destination)) {
            copy(in, out);
        }
    }

    private static void copy(InputStream in, FileOutputStream out) throws Exception {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        out.flush();
    }
}
