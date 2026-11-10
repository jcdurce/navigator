package com.brp;

import javafx.application.Platform;
import javafx.scene.web.WebEngine;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;

/**
 * Pont Java ↔ JavaScript exposé dans la page via window.javaApp.
 *
 * JavaFX WebView (moteur WebKit embarqué) ne gère nativement ni
 * l'ouverture de la boîte de dialogue "Choisir un fichier" sur un
 * <input type="file"> déclenché par script, ni le téléchargement
 * (Content-Disposition: attachment). Ce pont contourne les deux
 * limitations en s'appuyant sur FileChooser (natif JavaFX, fiable)
 * et sur java.net.http.HttpClient pour parler directement au
 * serveur CANAL, sans passer par le moteur de rendu.
 *
 * Toutes les méthodes publiques ici sont appelables depuis le
 * JavaScript de la page une fois le pont enregistré (voir
 * SimpleSearchApp.registerBridge).
 */
public class FileBridge {

    private final Stage stage;
    private final WebEngine engine;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public FileBridge(Stage stage, WebEngine engine) {
        this.stage = stage;
        this.engine = engine;
    }

    /**
     * Ouvre un sélecteur de fichier natif, puis envoie le fichier choisi
     * au serveur CANAL via POST /upload, exactement comme le ferait le
     * navigateur en JavaScript. Appelé depuis la page par :
     *   window.javaApp.pickAndUploadFile(fromPseudo, toPseudo, baseOrigin)
     */
    public void pickAndUploadFile(String fromPseudo, String toPseudo, String baseOrigin) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir un fichier à envoyer");
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return; // annulé par l'utilisateur
        }

        new Thread(() -> doUpload(file, fromPseudo, toPseudo, baseOrigin), "canal-upload").start();
    }

    private void doUpload(File file, String fromPseudo, String toPseudo, String baseOrigin) {
        try {
            long size = file.length();
            if (size == 0) {
                notifyJs("uploadError", "Le fichier « " + file.getName() + " » est vide.");
                return;
            }

            String filenameParam = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8);
            String fromParam = URLEncoder.encode(fromPseudo, StandardCharsets.UTF_8);
            String toParam = URLEncoder.encode(toPseudo, StandardCharsets.UTF_8);

            String uploadUrl = baseOrigin + "/upload?from=" + fromParam
                    + "&to=" + toParam
                    + "&filename=" + filenameParam;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofFile(file.toPath()))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                notifyJs("uploadError", "Échec de l'envoi (code " + response.statusCode() + ") : " + response.body());
            }
            // En cas de succès (200), le serveur notifie déjà la page via
            // WebSocket (événement "file_sent") — rien d'autre à faire ici.

        } catch (Exception e) {
            notifyJs("uploadError", "Erreur pendant l'envoi : " + e.getMessage());
        }
    }

    /**
     * Télécharge un fichier depuis /download/{transferId} et propose un
     * "Enregistrer sous..." natif pour l'écrire sur disque. Appelé depuis
     * la page par :
     *   window.javaApp.downloadFile(downloadUrl, suggestedFilename)
     */
    public void downloadFile(String downloadUrl, String suggestedFilename) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Enregistrer le fichier sous…");
        chooser.setInitialFileName(suggestedFilename == null || suggestedFilename.isBlank() ? "fichier" : suggestedFilename);
        File target = chooser.showSaveDialog(stage);
        if (target == null) {
            return; // annulé par l'utilisateur
        }

        new Thread(() -> doDownload(downloadUrl, target), "canal-download").start();
    }

    private void doDownload(String downloadUrl, File target) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                Files.write(target.toPath(), response.body());
                notifyJs("downloadDone", target.getAbsolutePath());
            } else {
                String detail = response.body() != null ? new String(response.body(), StandardCharsets.UTF_8) : "";
                notifyJs("downloadError", "Échec du téléchargement (code " + response.statusCode() + ") : " + detail);
            }
        } catch (Exception e) {
            notifyJs("downloadError", "Erreur pendant le téléchargement : " + e.getMessage());
        }
    }

    /**
     * Relaie un événement vers la page, si elle a défini
     * window.onJavaBridgeEvent(type, detail). Toujours exécuté sur le
     * thread JavaFX (obligatoire pour appeler WebEngine.executeScript).
     */
    private void notifyJs(String type, String detail) {
        Platform.runLater(() -> {
            try {
                String safeType = jsStringLiteral(type);
                String safeDetail = jsStringLiteral(detail == null ? "" : detail);
                engine.executeScript(
                        "window.onJavaBridgeEvent && window.onJavaBridgeEvent(" + safeType + ", " + safeDetail + ")"
                );
            } catch (Exception ignored) {
                // La page a pu naviguer ailleurs entre-temps — sans conséquence.
            }
        });
    }

    private String jsStringLiteral(String s) {
        String escaped = s
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
        return "'" + escaped + "'";
    }
}
