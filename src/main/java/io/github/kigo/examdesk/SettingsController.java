package io.github.kigo.examdesk;

import java.io.File;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.stage.DirectoryChooser;

/** 設定画面。変更はその場で保存する。 */
public class SettingsController implements ShellController.Refreshable {

    private final ShellController shell;

    @FXML
    private Label folderLabel;

    @FXML
    private CheckBox lenientCheck;

    @FXML
    private CheckBox darkCheck;

    public SettingsController(ShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void initialize() {
        lenientCheck.selectedProperty().addListener((obs, old, now) -> AppSettings.setLenientMatch(now));
        darkCheck.selectedProperty().addListener((obs, old, now) -> {
            AppSettings.setDarkTheme(now);
            shell.applyTheme();
        });
    }

    @Override
    public void refresh() {
        File dir = AppSettings.defaultFolder();
        folderLabel.setText(dir == null ? "（未設定）" : dir.getAbsolutePath());
        lenientCheck.setSelected(AppSettings.lenientMatch());
        darkCheck.setSelected(AppSettings.darkTheme());
    }

    @FXML
    private void onChooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("既定のフォルダを選択");
        chooser.setInitialDirectory(AppSettings.defaultFolder());
        File dir = chooser.showDialog(shell.window());
        if (dir == null) return;
        AppSettings.setDefaultFolder(dir);
        refresh();
    }

    @FXML
    private void onClearFolder() {
        AppSettings.setDefaultFolder(null);
        refresh();
    }
}
