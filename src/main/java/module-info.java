module com.asosiaciondeasis.animalesdeasis {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;

    opens com.asosiaciondeasis.animalesdeasis to javafx.fxml;
    exports com.asosiaciondeasis.animalesdeasis;
}