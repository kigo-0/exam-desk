package io.github.kigo.examdesk;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class EntryPoint extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("shell.fxml"));
        Parent root = loader.load();
        ShellController shell = loader.getController();

        stage.setScene(new Scene(root));
        shell.applyTheme();
        stage.setTitle("ExamDesk");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setOnCloseRequest(e -> {
            if (!shell.confirmDiscard()) e.consume();
        });
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
