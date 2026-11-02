package io.github.kigo.examdesk;

import java.util.function.Consumer;

import io.github.kigo.examdesk.ShellController.View;
import javafx.fxml.FXML;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

public class SidebarController {

    /** sidebar.fxml の fx:define で宣言したトグルグループ。 */
    @FXML
    private ToggleGroup navGroup;

    @FXML
    private ToggleButton sheetButton;

    private Consumer<View> onNavigate = view -> {
    };
    private boolean selecting;

    @FXML
    private void initialize() {
        navGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null) {
                // 選択中のボタンを再クリックしても選択を外させない
                selecting = true;
                old.setSelected(true);
                selecting = false;
            } else if (!selecting) {
                onNavigate.accept(View.valueOf((String) now.getUserData()));
            }
        });
    }

    public void setOnNavigate(Consumer<View> handler) {
        onNavigate = handler;
    }

    public void setSheetAvailable(boolean available) {
        sheetButton.setDisable(!available);
    }

    public void select(View view) {
        selecting = true;
        navGroup.getToggles().stream()
                .filter(t -> view.name().equals(t.getUserData()))
                .findFirst()
                .ifPresent(t -> t.setSelected(true));
        selecting = false;
    }
}
