package com.brp;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.concurrent.Worker;
import netscape.javascript.JSObject;

/**
 * Ne pas oublier de rajouter la main class dans le fichier MANIFEST.MF
 * Exemple :
 * Manifest-Version: 1.0
 * Created-By: Maven JAR Plugin 3.4.1
 * Build-Jdk-Spec: 17
 * Main-Class: com.brp.SimpleSearchApp
 *
 */

public class SimpleSearchApp extends Application {

    private static final String HOME_URL = "https://www.google.com";

    @Override
    public void start(Stage stage) {
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();
        WebHistory history = engine.getHistory();

        // Pont Java ↔ JavaScript pour contourner les limitations de WebView
        // (ouverture de fichier et téléchargement) — voir FileBridge.java.
        // "window" est recréé à chaque chargement de page, donc le pont
        // doit être réinjecté après chaque navigation réussie.
        FileBridge fileBridge = new FileBridge(stage, engine);
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                try {
                    JSObject window = (JSObject) engine.executeScript("window");
                    window.setMember("javaApp", fileBridge);
                } catch (Exception ignored) {
                    // Page sans contexte JS exploitable (ex: about:blank) — sans conséquence.
                }
            }
        });

        TextField addressBar = new TextField(HOME_URL);

        ComboBox<String> favoritesBox = new ComboBox<>(
                FXCollections.observableArrayList(
                        "https://www.google.com",
                        "https://canal-chat.onrender.com/",
                        "https://canal-ai.onrender.com/",
                        "https://nexutil.netlify.app",
                        "https://claude.ai",
                        "https://chat.z.ai"
                )
        );

        favoritesBox.setPromptText("Sites");
        favoritesBox.setPrefWidth(220);

        addressBar.setPromptText("Entrez une URL");
        HBox.setHgrow(addressBar, Priority.ALWAYS);

        Button backButton = new Button("←");
        Button forwardButton = new Button("→");
        Button reloadButton = new Button("⟳");
        Button homeButton = new Button("Accueil");
        Button goButton = new Button("Aller");
        Button favoriteButton = new Button("Favoris");
        Button fullScreenButton = new Button("Plein écran");
        Button exitButton = new Button("Quitter");

        Runnable loadAddress = () -> {
            String url = addressBar.getText().trim();
            if (url.isEmpty()) {
                return;
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            engine.load(url);
        };

        backButton.setOnAction(e -> {
            if (history.getCurrentIndex() > 0) {
                history.go(-1);
            }
        });

        forwardButton.setOnAction(e -> {
            if (history.getCurrentIndex() < history.getEntries().size() - 1) {
                history.go(1);
            }
        });

        reloadButton.setOnAction(e -> engine.reload());

        homeButton.setOnAction(e -> {
            addressBar.setText(HOME_URL);
            engine.load(HOME_URL);
        });

        goButton.setOnAction(e -> loadAddress.run());

        favoriteButton.setOnAction(e -> {
            String selectedUrl = favoritesBox.getValue();

            if (selectedUrl != null && !selectedUrl.isBlank()) {
                addressBar.setText(selectedUrl);
                engine.load(selectedUrl);
            }
        });

        addressBar.setOnAction(e -> loadAddress.run());

        fullScreenButton.setOnAction(e -> stage.setFullScreen(!stage.isFullScreen()));
        exitButton.setOnAction(e -> Platform.exit());

        HBox topBar = new HBox(8,
                backButton,
                forwardButton,
                reloadButton,
                homeButton,
                addressBar,
                goButton,
                favoritesBox,
                favoriteButton,
                fullScreenButton,
                exitButton
        );


        topBar.setPadding(new Insets(8));
        topBar.setAlignment(Pos.CENTER_LEFT);

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(webView);

        Scene scene = new Scene(root, 1280, 800);

        backButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> history.getCurrentIndex() <= 0,
                history.currentIndexProperty(),
                history.getEntries()
        ));

        forwardButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> history.getCurrentIndex() >= history.getEntries().size() - 1,
                history.currentIndexProperty(),
                history.getEntries()
        ));

        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.F11) {
                stage.setFullScreen(!stage.isFullScreen());
            } else if (event.getCode() == KeyCode.ESCAPE && stage.isFullScreen()) {
                stage.setFullScreen(false);
            } else if (event.isAltDown() && event.getCode() == KeyCode.LEFT) {
                if (history.getCurrentIndex() > 0) history.go(-1);
            } else if (event.isAltDown() && event.getCode() == KeyCode.RIGHT) {
                if (history.getCurrentIndex() < history.getEntries().size() - 1) history.go(1);
            } else if (event.isControlDown() && event.getCode() == KeyCode.R) {
                engine.reload();
            }
        });

        engine.getLoadWorker().exceptionProperty().addListener((obs, oldEx, newEx) -> {
            if (newEx != null) {
                addressBar.setText(engine.getLocation());
            }
        });

        stage.setTitle("Navigateur simple");
        stage.setScene(scene);
        stage.setFullScreen(true);
        stage.show();

        engine.load(HOME_URL);
    }

    public static void main(String[] args) {
        launch(args);
    }
}