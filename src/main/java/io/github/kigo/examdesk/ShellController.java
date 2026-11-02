package io.github.kigo.examdesk;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;
import javafx.util.Duration;

/** 画面全体の枠。サイドバー（fx:include）と、各画面を差し替えるコンテンツ領域を持つ。 */
public class ShellController {

    public enum View { HOME, SHEET, RESULTS, SETTINGS }

    /** 表示されるたびに最新の状態へ更新したい画面のコントローラが実装する。 */
    public interface Refreshable {
        void refresh();
    }

    @FXML
    private StackPane contentPane;

    @FXML
    private Label toastLabel;

    /** shell.fxml の fx:include fx:id="sidebar" から注入されるネストしたコントローラ。 */
    @FXML
    private SidebarController sidebarController;

    private final Map<View, Node> views = new EnumMap<>(View.class);
    private final Map<View, Object> controllers = new EnumMap<>(View.class);
    private SequentialTransition toastAnimation;

    @FXML
    private void initialize() {
        sidebarController.setOnNavigate(this::show);
        sidebarController.setSheetAvailable(false);
        show(View.HOME);
    }

    public void show(View view) {
        Node node = views.computeIfAbsent(view, this::load);
        if (controllers.get(view) instanceof Refreshable refreshable) refreshable.refresh();
        sidebarController.select(view);
        contentPane.getChildren().setAll(node);

        FadeTransition fade = new FadeTransition(Duration.millis(180), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    public void startExam(String name, int count) {
        openSheet(ExamData.create(name, count), null);
    }

    public void openExam(File file) {
        try {
            ExamData data = ExamData.read(file);
            AppSettings.addRecent(file);
            openSheet(data, file);
        } catch (Exception e) {
            error("ファイルを開けませんでした", e);
        }
    }

    private void openSheet(ExamData data, File file) {
        if (!confirmDiscard()) return;
        views.computeIfAbsent(View.SHEET, this::load);
        ((SheetController) controllers.get(View.SHEET)).load(data, file);
        sidebarController.setSheetAvailable(true);
        show(View.SHEET);
    }

    public void closeExam() {
        sidebarController.setSheetAvailable(false);
        show(View.HOME);
    }

    /** 開いている試験に未保存の変更があれば確認する。続行してよければ true。 */
    public boolean confirmDiscard() {
        return !(controllers.get(View.SHEET) instanceof SheetController sheet) || sheet.confirmDiscard();
    }

    public void applyTheme() {
        List<String> sheets = contentPane.getScene().getRoot().getStylesheets();
        String dark = getClass().getResource("dark.css").toExternalForm();
        sheets.remove(dark);
        if (AppSettings.darkTheme()) sheets.add(dark);
    }

    public void toast(String message) {
        toastLabel.setText(message);
        if (toastAnimation != null) toastAnimation.stop();
        FadeTransition in = new FadeTransition(Duration.millis(160), toastLabel);
        in.setToValue(1);
        FadeTransition out = new FadeTransition(Duration.millis(400), toastLabel);
        out.setToValue(0);
        toastAnimation = new SequentialTransition(in, new PauseTransition(Duration.seconds(2.2)), out);
        toastAnimation.play();
    }

    public Alert alert(Alert.AlertType type, String header, String content) {
        Alert alert = new Alert(type, content);
        alert.setTitle("ExamDesk");
        alert.setHeaderText(header);
        alert.initOwner(window());
        alert.getDialogPane().getStylesheets().setAll(contentPane.getScene().getRoot().getStylesheets());
        return alert;
    }

    public void error(String header, Exception e) {
        e.printStackTrace();
        alert(Alert.AlertType.ERROR, header, e.getMessage()).showAndWait();
    }

    public Window window() {
        return contentPane.getScene().getWindow();
    }

    private Node load(View view) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(view.name().toLowerCase(Locale.ROOT) + ".fxml"));
        loader.setControllerFactory(this::createController);
        try {
            Node node = loader.load();
            controllers.put(view, loader.getController());
            return node;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** ShellController を受け取るコンストラクタがあればそれを使い、画面からシェルを操作できるようにする。 */
    private Object createController(Class<?> type) {
        try {
            try {
                return type.getConstructor(ShellController.class).newInstance(this);
            } catch (NoSuchMethodException e) {
                return type.getConstructor().newInstance();
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("コントローラを生成できません: " + type.getName(), e);
        }
    }
}
