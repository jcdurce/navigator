Problème
javafx.scene.web.WebView embarque un moteur WebKit indépendant de Chrome, avec deux limitations connues :

Upload : un clic JS (.click()) sur un <input type="file"> n'ouvre pas la boîte de dialogue native du système.
Download : WebView n'a pas de gestionnaire de téléchargement — un lien vers une ressource avec Content-Disposition: attachment ne déclenche aucun "Enregistrer sous", WebView tente simplement de naviguer vers la ressource.
Cela explique pourquoi CANAL (https://github.com/jcdurce/canal-chat) fonctionne pour la messagerie (WebSocket, JS moderne — tous deux bien supportés) mais pas pour le partage de fichiers, dans ce navigateur spécifiquement.

Solution
Ajoute FileBridge.java, injecté dans window.javaApp après chaque chargement de page réussi (SimpleSearchApp.java, écouteur sur loadWorker.stateProperty()). Expose deux méthodes appelables depuis le JavaScript d'une page :

pickAndUploadFile(from, to, baseOrigin) — ouvre un FileChooser natif JavaFX (fiable, indépendant de WebView), puis envoie le fichier choisi en HTTP via java.net.http.HttpClient directement vers /upload
downloadFile(url, suggestedFilename) — ouvre un FileChooser en mode "Enregistrer sous", puis récupère le fichier en HTTP et l'écrit sur disque
Les erreurs sont relayées à la page via window.onJavaBridgeEvent(type, detail) (appelé par engine.executeScript(...) sur le thread JavaFX).

Changements
FileBridge.java (nouveau fichier) : le pont
SimpleSearchApp.java : enregistrement du pont dans window à chaque Worker.State.SUCCEEDED
module-info.java : ajout de requires java.net.http;
Dépendance
Cette PR seule ne change rien visible sans la contrepartie côté page : jcdurce/canal-chat#2, qui détecte window.javaApp et y délègue le bouton 📎 et le bouton Télécharger.

⚠️ Compilation non vérifiée dans cette session
Maven Central n'est pas accessible depuis le bac à sable où j'ai travaillé (hors liste blanche réseau), donc je n'ai pas pu exécuter mvn compile pour confirmer que ça compile. Le code suit le patron standard documenté de JavaFX (netscape.javascript.JSObject, WebEngine.executeScript("window"), setMember), et j'ai relu attentivement types et imports, mais merci de valider avec un mvn clean javafx:run local avant de fusionner.

Le glisser-déposer de fichier depuis le système d'exploitation reste non supporté par WebView, même avec ce pont — à utiliser avec le bouton 📎 plutôt.
