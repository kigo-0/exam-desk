package io.github.kigo.examdesk;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/** 設定値の読み書き。旧バージョンと同じノード・キー（defaultPath）を引き継ぐ。 */
public final class AppSettings {

    private static final Preferences PREFS = Preferences.userRoot().node("quiz.settings");
    private static final String KEY_DEFAULT_PATH = "defaultPath";
    private static final String KEY_LAST_RESULTS = "lastResultsFolder";
    private static final String KEY_LENIENT = "lenientMatch";
    private static final String KEY_DARK = "darkTheme";
    private static final String KEY_RECENT = "recentFiles";
    private static final int RECENT_MAX = 6;

    private AppSettings() {
    }

    public static File defaultFolder() {
        return folder(PREFS.get(KEY_DEFAULT_PATH, ""));
    }

    public static void setDefaultFolder(File dir) {
        PREFS.put(KEY_DEFAULT_PATH, dir == null ? "" : dir.getAbsolutePath());
    }

    /** 結果一覧で最初に開くフォルダ。既定フォルダ → 前回開いたフォルダの順に探す。 */
    public static File resultsFolder() {
        File dir = defaultFolder();
        return dir != null ? dir : folder(PREFS.get(KEY_LAST_RESULTS, ""));
    }

    public static void setLastResultsFolder(File dir) {
        PREFS.put(KEY_LAST_RESULTS, dir.getAbsolutePath());
    }

    public static boolean lenientMatch() {
        return PREFS.getBoolean(KEY_LENIENT, true);
    }

    public static void setLenientMatch(boolean value) {
        PREFS.putBoolean(KEY_LENIENT, value);
    }

    public static boolean darkTheme() {
        return PREFS.getBoolean(KEY_DARK, false);
    }

    public static void setDarkTheme(boolean value) {
        PREFS.putBoolean(KEY_DARK, value);
    }

    public static List<File> recentFiles() {
        List<File> files = new ArrayList<>();
        for (String path : PREFS.get(KEY_RECENT, "").split("\n")) {
            if (!path.isBlank() && new File(path).isFile()) files.add(new File(path));
        }
        return files;
    }

    public static void addRecent(File file) {
        File target = file.getAbsoluteFile();
        List<File> files = recentFiles();
        files.removeIf(f -> f.getAbsoluteFile().equals(target));
        files.add(0, target);
        PREFS.put(KEY_RECENT, files.stream().limit(RECENT_MAX).map(File::getPath).collect(Collectors.joining("\n")));
    }

    private static File folder(String path) {
        if (path == null || path.isBlank()) return null;
        File dir = new File(path);
        return dir.isDirectory() ? dir : null;
    }
}
