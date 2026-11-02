package io.github.kigo.examdesk;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import io.github.kigo.examdesk.ExamData.Phase;
import io.github.kigo.examdesk.ExamData.Question;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.stage.FileChooser;
import javafx.util.Duration;

/**
 * 解答用紙。解答 → 答え合わせ → 採点結果 の3段階で進む。
 * progress / progressText / nextText / nextDisabled / graded は sheet.fxml から ${controller.xxx} で参照される。
 */
public class SheetController {

    private static final KeyCombination SAVE = new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN);

    private final ShellController shell;

    @FXML
    private TextField nameField;

    @FXML
    private Label timerLabel;

    @FXML
    private Label stepAnswer;

    @FXML
    private Label stepCheck;

    @FXML
    private Label stepResult;

    @FXML
    private ScrollPane scrollPane;

    @FXML
    private VBox rowsBox;

    @FXML
    private Arc scoreArc;

    @FXML
    private Label scoreLabel;

    @FXML
    private Label scoreDetailLabel;

    @FXML
    private Label hintLabel;

    private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper();
    private final ReadOnlyStringWrapper progressText = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper nextText = new ReadOnlyStringWrapper();
    private final ReadOnlyBooleanWrapper nextDisabled = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper graded = new ReadOnlyBooleanWrapper();

    private final List<QuestionRowController> rows = new ArrayList<>();
    private final Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick()));
    private Phase phase = Phase.ANSWERING;
    private long elapsedSeconds;
    private File file;
    private boolean dirty;
    private boolean loading;

    public SheetController(ShellController shell) {
        this.shell = shell;
        clock.setCycleCount(Animation.INDEFINITE);
    }

    @FXML
    private void initialize() {
        nameField.textProperty().addListener((obs, old, now) -> markDirty());
    }

    public void load(ExamData data, File file) {
        loading = true;
        this.file = file;
        nameField.setText(data.name());
        elapsedSeconds = data.elapsedSeconds();

        rows.clear();
        rowsBox.getChildren().clear();
        for (Question q : data.questions()) {
            QuestionRowController row = createRow();
            row.answerField().setText(q.answer());
            row.correctField().setText(q.correct());
            if (data.phase() == Phase.GRADED) row.resultProperty().set(q.isCorrect());
        }
        setPhase(data.phase());
        scrollPane.setVvalue(0);
        loading = false;
        dirty = false;
    }

    private QuestionRowController createRow() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("question-row.fxml"));
        try {
            loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        QuestionRowController row = loader.getController();
        int index = rows.size();
        row.setNumber(index + 1);

        row.answerField().textProperty().addListener((obs, old, now) -> contentChanged());
        row.correctField().textProperty().addListener((obs, old, now) -> contentChanged());
        row.resultProperty().addListener((obs, old, now) -> {
            markDirty();
            updateScore();
        });
        // Enter で次の問題の同じ欄へ
        row.answerField().setOnAction(e -> focusRow(index + 1, QuestionRowController::answerField));
        row.correctField().setOnAction(e -> focusRow(index + 1, QuestionRowController::correctField));

        rows.add(row);
        rowsBox.getChildren().add(row.root());
        return row;
    }

    private void setPhase(Phase phase) {
        this.phase = phase;
        boolean answering = phase == Phase.ANSWERING;
        for (QuestionRowController row : rows) {
            row.answerField().setEditable(answering);
            row.correctField().setDisable(answering);
            row.correctField().setEditable(phase == Phase.CHECKING);
        }

        setStep(stepAnswer, phase.ordinal(), Phase.ANSWERING);
        setStep(stepCheck, phase.ordinal(), Phase.CHECKING);
        setStep(stepResult, phase.ordinal(), Phase.GRADED);

        graded.set(phase == Phase.GRADED);
        nextText.set(switch (phase) {
            case ANSWERING -> "解答終了 → 答え合わせへ";
            case CHECKING -> "採点する";
            case GRADED -> "結果を保存";
        });
        hintLabel.setText(switch (phase) {
            case ANSWERING -> "解答を入力してください。Enter で次の問題へ移動します。";
            case CHECKING -> "正解を入力してください。全問入力すると採点できます。";
            case GRADED -> "採点が完了しました。○ / × をクリックすると正誤を修正できます。";
        });

        if (answering) {
            clock.play();
        } else {
            clock.stop();
        }
        updateTimer();
        updateProgress();
        updateScore();
    }

    private static void setStep(Label label, int current, Phase step) {
        label.getStyleClass().removeAll("active", "done");
        if (current == step.ordinal()) {
            label.getStyleClass().add("active");
        } else if (current > step.ordinal()) {
            label.getStyleClass().add("done");
        }
    }

    private void contentChanged() {
        markDirty();
        updateProgress();
    }

    private void markDirty() {
        if (!loading) dirty = true;
    }

    private void updateProgress() {
        int total = rows.size();
        long filled = switch (phase) {
            case ANSWERING -> rows.stream().filter(r -> !r.answerField().getText().isBlank()).count();
            case CHECKING -> rows.stream().filter(r -> !r.correctField().getText().isBlank()).count();
            case GRADED -> total;
        };
        progress.set(total == 0 ? 0 : (double) filled / total);
        progressText.set(switch (phase) {
            case ANSWERING -> "解答 " + filled + " / " + total;
            case CHECKING -> "正解入力 " + filled + " / " + total;
            case GRADED -> "採点済み " + total + " 問";
        });
        nextDisabled.set(phase == Phase.CHECKING && filled < total);
    }

    private void updateScore() {
        if (phase != Phase.GRADED) return;
        long correct = rows.stream().filter(r -> Boolean.TRUE.equals(r.resultProperty().get())).count();
        double rate = rows.isEmpty() ? 0 : (double) correct / rows.size();
        scoreLabel.setText(Math.round(rate * 100) + "%");
        scoreDetailLabel.setText(correct + " / " + rows.size() + " 問正解");
        scoreArc.setVisible(correct > 0);
        new Timeline(new KeyFrame(Duration.millis(700),
                new KeyValue(scoreArc.lengthProperty(), -360 * rate, Interpolator.EASE_OUT))).play();
    }

    private void tick() {
        elapsedSeconds++;
        updateTimer();
    }

    private void updateTimer() {
        long h = elapsedSeconds / 3600, m = elapsedSeconds / 60 % 60, s = elapsedSeconds % 60;
        timerLabel.setText(h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s));
    }

    private void focusRow(int index, Function<QuestionRowController, TextField> column) {
        if (index >= rows.size()) return;
        column.apply(rows.get(index)).requestFocus();
        ensureVisible(rows.get(index));
    }

    private void ensureVisible(QuestionRowController row) {
        double viewport = scrollPane.getViewportBounds().getHeight();
        double scrollable = rowsBox.getHeight() - viewport;
        if (scrollable <= 0) return;
        Bounds bounds = row.root().getBoundsInParent();
        double top = scrollPane.getVvalue() * scrollable;
        if (bounds.getMinY() < top) {
            scrollPane.setVvalue(bounds.getMinY() / scrollable);
        } else if (bounds.getMaxY() > top + viewport) {
            scrollPane.setVvalue((bounds.getMaxY() - viewport) / scrollable);
        }
    }

    @FXML
    private void onNext() {
        switch (phase) {
            case ANSWERING -> finishAnswering();
            case CHECKING -> grade();
            case GRADED -> save();
        }
    }

    private void finishAnswering() {
        long blanks = rows.stream().filter(r -> r.answerField().getText().isBlank()).count();
        Alert confirm = shell.alert(Alert.AlertType.CONFIRMATION, "解答を終了しますか？",
                (blanks > 0 ? "未解答が " + blanks + " 問あります。" : "全問解答済みです。")
                        + "\n終了すると解答は編集できなくなります。");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        setPhase(Phase.CHECKING);
        markDirty();
        focusRow(0, QuestionRowController::correctField);
    }

    private void grade() {
        boolean lenient = AppSettings.lenientMatch();
        loading = true; // 判定をまとめてセットし、最後に1回だけ dirty にする
        for (QuestionRowController row : rows) {
            row.resultProperty().set(matches(row.answerField().getText(), row.correctField().getText(), lenient));
        }
        loading = false;
        setPhase(Phase.GRADED);
        markDirty();
    }

    private static boolean matches(String answer, String correct, boolean lenient) {
        return lenient ? normalize(answer).equals(normalize(correct)) : answer.strip().equals(correct.strip());
    }

    /** 全角/半角・大文字/小文字・空白の違いを吸収する。 */
    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    @FXML
    private void onSave() {
        save();
    }

    private boolean save() {
        File target = file;
        if (target == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("試験を保存");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("試験ファイル (*.xml)", "*.xml"));
            chooser.setInitialFileName(nameField.getText().strip().replaceAll("[\\\\/:*?\"<>|]", "_") + ".xml");
            chooser.setInitialDirectory(AppSettings.defaultFolder());
            target = chooser.showSaveDialog(shell.window());
            if (target == null) return false;
        }
        try {
            snapshot().write(target);
        } catch (Exception e) {
            shell.error("保存に失敗しました", e);
            return false;
        }
        file = target;
        dirty = false;
        AppSettings.addRecent(target);
        shell.toast("保存しました: " + target.getName());
        return true;
    }

    private ExamData snapshot() {
        List<Question> questions = rows.stream()
                .map(r -> new Question(r.answerField().getText(), r.correctField().getText(),
                        Boolean.TRUE.equals(r.resultProperty().get())))
                .toList();
        return new ExamData(nameField.getText().strip(), phase, LocalDateTime.now(), elapsedSeconds, questions);
    }

    @FXML
    private void onClose() {
        if (!confirmDiscard()) return;
        clock.stop();
        dirty = false;
        shell.closeExam();
    }

    @FXML
    private void onKeyPressed(KeyEvent e) {
        if (SAVE.match(e)) {
            save();
            e.consume();
        }
    }

    /** 未保存の変更があれば保存するか確認する。続行してよければ true。 */
    public boolean confirmDiscard() {
        if (!dirty) return true;
        ButtonType saveButton = new ButtonType("保存する", ButtonBar.ButtonData.YES);
        ButtonType discardButton = new ButtonType("保存しない", ButtonBar.ButtonData.NO);
        Alert confirm = shell.alert(Alert.AlertType.CONFIRMATION,
                "「" + nameField.getText().strip() + "」の変更を保存しますか？", "保存しないと、入力内容は失われます。");
        confirm.getButtonTypes().setAll(saveButton, discardButton, ButtonType.CANCEL);
        ButtonType choice = confirm.showAndWait().orElse(ButtonType.CANCEL);
        if (choice == saveButton) return save();
        return choice == discardButton;
    }

    public ReadOnlyDoubleProperty progressProperty() {
        return progress.getReadOnlyProperty();
    }

    public double getProgress() {
        return progress.get();
    }

    public ReadOnlyStringProperty progressTextProperty() {
        return progressText.getReadOnlyProperty();
    }

    public String getProgressText() {
        return progressText.get();
    }

    public ReadOnlyStringProperty nextTextProperty() {
        return nextText.getReadOnlyProperty();
    }

    public String getNextText() {
        return nextText.get();
    }

    public ReadOnlyBooleanProperty nextDisabledProperty() {
        return nextDisabled.getReadOnlyProperty();
    }

    public boolean isNextDisabled() {
        return nextDisabled.get();
    }

    public ReadOnlyBooleanProperty gradedProperty() {
        return graded.getReadOnlyProperty();
    }

    public boolean isGraded() {
        return graded.get();
    }
}
