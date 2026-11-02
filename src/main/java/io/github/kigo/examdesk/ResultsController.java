package io.github.kigo.examdesk;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import io.github.kigo.examdesk.ExamData.Phase;
import io.github.kigo.examdesk.ExamData.Question;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

public class ResultsController implements ShellController.Refreshable {

    /** 表の1行。results.fxml の PropertyValueFactory が getter を反射で読む。 */
    public static class AnswerRow {
        private final int number;
        private final String answer;
        private final String correct;
        private final boolean isCorrect;
        private final boolean graded;

        AnswerRow(int number, Question q, boolean graded) {
            this.number = number;
            this.answer = q.answer();
            this.correct = q.correct();
            this.isCorrect = q.isCorrect();
            this.graded = graded;
        }

        public int getNumber() { return number; }
        public String getAnswer() { return answer; }
        public String getCorrect() { return correct; }
        public String getMark() { return isCorrect ? "○" : "×"; }
    }

    private record Entry(File file, ExamData data, String location) {
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static final PseudoClass CORRECT = PseudoClass.getPseudoClass("correct");
    private static final PseudoClass WRONG = PseudoClass.getPseudoClass("wrong");

    private final ShellController shell;

    @FXML
    private Label folderLabel;

    @FXML
    private ListView<Entry> filesList;

    @FXML
    private VBox detailPane;

    @FXML
    private Label emptyLabel;

    @FXML
    private Label detailTitle;

    @FXML
    private Label detailMeta;

    @FXML
    private PieChart pieChart;

    @FXML
    private TableView<AnswerRow> answerTable;

    @FXML
    private LineChart<String, Number> trendChart;

    @FXML
    private Label statCount;

    @FXML
    private Label statAverage;

    @FXML
    private Label statBest;

    @FXML
    private Label statLatest;

    private File folder;

    public ResultsController(ShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void initialize() {
        filesList.setCellFactory(lv -> new EntryCell());
        filesList.getSelectionModel().selectedItemProperty().addListener((obs, old, now) -> showDetail(now));
        filesList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) onOpenSelected();
        });
        detailPane.visibleProperty().bind(filesList.getSelectionModel().selectedItemProperty().isNotNull());
        emptyLabel.visibleProperty().bind(detailPane.visibleProperty().not());

        answerTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(AnswerRow row, boolean empty) {
                super.updateItem(row, empty);
                boolean judged = !empty && row != null && row.graded;
                pseudoClassStateChanged(CORRECT, judged && row.isCorrect);
                pseudoClassStateChanged(WRONG, judged && !row.isCorrect);
            }
        });
    }

    @Override
    public void refresh() {
        if (folder == null) folder = AppSettings.resultsFolder();
        reload();
    }

    @FXML
    private void onReload() {
        reload();
    }

    @FXML
    private void onChangeFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("結果が入ったフォルダを選択");
        chooser.setInitialDirectory(folder);
        File dir = chooser.showDialog(shell.window());
        if (dir == null) return;
        folder = dir;
        AppSettings.setLastResultsFolder(dir);
        reload();
    }

    @FXML
    private void onOpenSelected() {
        Entry entry = filesList.getSelectionModel().getSelectedItem();
        if (entry != null) shell.openExam(entry.file());
    }

    private void reload() {
        Entry selected = filesList.getSelectionModel().getSelectedItem();
        List<Entry> entries = folder == null ? List.of() : scan(folder);
        folderLabel.setText(folder == null
                ? "フォルダが選択されていません"
                : folder.getAbsolutePath() + "  ·  " + entries.size() + " 件");
        filesList.getItems().setAll(entries);
        if (selected != null) {
            entries.stream().filter(e -> e.file().equals(selected.file())).findFirst()
                    .ifPresent(e -> filesList.getSelectionModel().select(e));
        }
        updateTrend(entries);
    }

    private List<Entry> scan(File dir) {
        List<Entry> entries = new ArrayList<>();
        Path base = dir.toPath();
        try (Stream<Path> paths = Files.walk(base, 4)) {
            paths.filter(p -> Files.isRegularFile(p) && p.toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
                    .forEach(p -> {
                        try {
                            entries.add(new Entry(p.toFile(), ExamData.read(p.toFile()),
                                    base.relativize(p.getParent()).toString()));
                        } catch (Exception ignored) {
                            // 試験ファイル以外のXMLは読み飛ばす
                        }
                    });
        } catch (IOException e) {
            shell.error("フォルダを読み込めませんでした", e);
        }
        entries.sort(Comparator.comparing((Entry e) -> e.data().savedAt()).reversed());
        return entries;
    }

    private void showDetail(Entry entry) {
        if (entry == null) return;
        ExamData data = entry.data();
        boolean isGraded = data.phase() == Phase.GRADED;
        detailTitle.setText(data.name());
        detailMeta.setText(String.join("  ·  ", data.savedAt().format(DATE), data.questions().size() + " 問",
                "解答時間 " + formatDuration(data.elapsedSeconds()), entry.file().getName()));

        int correct = data.correctCount();
        int wrong = data.questions().size() - correct;
        pieChart.setVisible(isGraded);
        pieChart.setManaged(isGraded);
        pieChart.setTitle(Math.round(data.rate() * 100) + "%");
        pieChart.getData().setAll(new PieChart.Data("正解 " + correct, correct), new PieChart.Data("不正解 " + wrong, wrong));

        List<AnswerRow> rows = new ArrayList<>();
        for (int i = 0; i < data.questions().size(); i++) rows.add(new AnswerRow(i + 1, data.questions().get(i), isGraded));
        answerTable.getItems().setAll(rows);
        answerTable.getColumns().getLast().setVisible(isGraded);
    }

    private void updateTrend(List<Entry> entries) {
        List<Entry> graded = entries.stream()
                .filter(e -> e.data().phase() == Phase.GRADED && !e.data().questions().isEmpty())
                .sorted(Comparator.comparing(e -> e.data().savedAt()))
                .toList();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        trendChart.getData().setAll(List.of(series));
        for (int i = 0; i < graded.size(); i++) {
            ExamData data = graded.get(i).data();
            XYChart.Data<String, Number> point = new XYChart.Data<>((i + 1) + "回目", Math.round(data.rate() * 1000) / 10.0);
            series.getData().add(point);
            Tooltip.install(point.getNode(), new Tooltip(
                    data.name() + "\n" + data.savedAt().format(DATE) + "\n正答率 " + percent(data.rate())));
        }

        double average = graded.stream().mapToDouble(e -> e.data().rate()).average().orElse(0);
        double best = graded.stream().mapToDouble(e -> e.data().rate()).max().orElse(0);
        statCount.setText(graded.size() + " 回");
        statAverage.setText(graded.isEmpty() ? "-" : percent(average));
        statBest.setText(graded.isEmpty() ? "-" : percent(best));
        statLatest.setText(graded.isEmpty() ? "-" : percent(graded.getLast().data().rate()));
    }

    private static String percent(double rate) {
        return Math.round(rate * 100) + "%";
    }

    private static String formatDuration(long seconds) {
        return seconds >= 3600
                ? String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private static final class EntryCell extends ListCell<Entry> {
        private final Label name = new Label();
        private final Label meta = new Label();
        private final Label badge = new Label();
        private final HBox box;

        EntryCell() {
            name.getStyleClass().add("entry-name");
            meta.getStyleClass().add("entry-meta");
            VBox text = new VBox(2, name, meta);
            HBox.setHgrow(text, Priority.ALWAYS);
            box = new HBox(10, text, badge);
            box.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(Entry entry, boolean empty) {
            super.updateItem(entry, empty);
            setText(null);
            if (empty || entry == null) {
                setGraphic(null);
                return;
            }
            ExamData data = entry.data();
            name.setText(data.name());
            meta.setText(data.savedAt().format(DATE) + "  ·  " + data.questions().size() + " 問"
                    + (entry.location().isEmpty() ? "" : "  ·  " + entry.location()));
            if (data.phase() == Phase.GRADED) {
                double rate = data.rate();
                badge.setText(percent(rate));
                badge.getStyleClass().setAll("badge", rate >= 0.8 ? "high" : rate >= 0.5 ? "mid" : "low");
            } else {
                badge.setText(data.phase() == Phase.ANSWERING ? "解答中" : "未採点");
                badge.getStyleClass().setAll("badge", "pending");
            }
            setGraphic(box);
        }
    }
}
