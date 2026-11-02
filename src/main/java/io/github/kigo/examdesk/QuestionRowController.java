package io.github.kigo.examdesk;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

/** question-row.fxml 1行分（1問）の部品。 */
public class QuestionRowController {

    private static final PseudoClass CORRECT = PseudoClass.getPseudoClass("correct");
    private static final PseudoClass WRONG = PseudoClass.getPseudoClass("wrong");

    @FXML
    private HBox root;

    @FXML
    private Label numberLabel;

    @FXML
    private TextField answerField;

    @FXML
    private TextField correctField;

    @FXML
    private Button markButton;

    /** 判定結果。null は未採点。 */
    private final ObjectProperty<Boolean> result = new SimpleObjectProperty<>();

    @FXML
    private void initialize() {
        result.addListener((obs, old, now) -> {
            root.pseudoClassStateChanged(CORRECT, Boolean.TRUE.equals(now));
            root.pseudoClassStateChanged(WRONG, Boolean.FALSE.equals(now));
            markButton.setText(now == null ? "" : now ? "○" : "×");
        });
        markButton.disableProperty().bind(result.isNull());
        markButton.setOnAction(e -> result.set(!result.get()));
    }

    public void setNumber(int number) {
        numberLabel.setText(String.valueOf(number));
    }

    public HBox root() {
        return root;
    }

    public TextField answerField() {
        return answerField;
    }

    public TextField correctField() {
        return correctField;
    }

    public ObjectProperty<Boolean> resultProperty() {
        return result;
    }
}
