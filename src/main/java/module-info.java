module io.github.kigo.examdesk {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive javafx.graphics;

    requires java.xml;
    requires java.prefs;

    // javafx.base: PropertyValueFactory と FXML式バインディングが getter を反射で読むため
    opens io.github.kigo.examdesk to javafx.fxml, javafx.base;
    exports io.github.kigo.examdesk;
}
