package io.github.kigo.examdesk;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public class HomeController implements ShellController.Refreshable {

    private final ShellController shell;

    @FXML
    private TextField nameField;

    @FXML
    private Spinner<Integer> countSpinner;

    @FXML
    private ListView<File> recentList;

    public HomeController(ShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void initialize() {
        recentList.setCellFactory(lv -> new ListCell<>() {
            private final Label name = new Label();
            private final Label path = new Label();
            private final VBox box = new VBox(2, name, path);

            {
                name.getStyleClass().add("entry-name");
                path.getStyleClass().add("entry-meta");
                box.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(File file, boolean empty) {
                super.updateItem(file, empty);
                setText(null);
                if (empty || file == null) {
                    setGraphic(null);
                } else {
                    name.setText(file.getName());
                    path.setText(file.getParent());
                    setGraphic(box);
                }
            }
        });
        recentList.setOnMouseClicked(e -> {
            File file = recentList.getSelectionModel().getSelectedItem();
            if (file != null) shell.openExam(file);
        });
    }

    @Override
    public void refresh() {
        nameField.setText("試験 " + LocalDate.now().format(DateTimeFormatter.ofPattern("M/d")));
        recentList.getItems().setAll(AppSettings.recentFiles());
        recentList.getSelectionModel().clearSelection();
    }

    @FXML
    private void onStart() {
        // 直接入力された値は Enter かフォーカス移動まで確定しないので、ここで読み取る
        try {
            countSpinner.getValueFactory().setValue(Integer.parseInt(countSpinner.getEditor().getText().trim()));
        } catch (NumberFormatException e) {
            countSpinner.getEditor().setText(String.valueOf(countSpinner.getValue()));
        }
        String name = nameField.getText().isBlank() ? "無題の試験" : nameField.getText().strip();
        shell.startExam(name, countSpinner.getValue());
    }

    @FXML
    private void onOpen() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("試験ファイルを開く");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("試験ファイル (*.xml)", "*.xml"));
        chooser.setInitialDirectory(AppSettings.defaultFolder());
        File file = chooser.showOpenDialog(shell.window());
        if (file != null) shell.openExam(file);
    }

    @FXML
    private void onResults() {
        shell.show(ShellController.View.RESULTS);
    }
}
