# Tropimon Events 0.2.1

By FastedCorsi

<!-- tropimon-consent-updater:2 -->

- Mise à jour autonome avec consentement explicite avant consultation et téléchargement ; installation différée par Java, stockage classique et Tropimon géré pris en charge. Le canal historique des anciens updaters reste inchangé.
- Correction des annonces de raid et de boost ignorées : le détecteur accepte les messages réseau bruts, sans exiger l'horodatage ajouté par le chat côté client. Annonces de raid françaises et anglaises reconnues ; les citations des joueurs restent rejetées.
- HUD automatique sans appuyer sur F6. Le survol utilise les mêmes icônes que le jeu ; aucune catégorie vide n'est affichée.
- Icônes réduites de 32 à 24 pixels d'interface, décalées sous la première ligne du HUD et masquées dans les menus. F6 n'interrompt plus les menus officiels.
- Seules les arènes observées ouvertes apparaissent. Leur porte affiche la carte de type du navigateur officiel, réduite à l'exécution. Aucune image tierce n'est redistribuée ; un libellé sert de repli si la ressource manque.
- Prise en compte des annonces officielles d'ouverture et de fermeture avec type explicite, en complément des paquets du navigateur et des terminaux.

Validation : 37 tests automatisés réussis, dont 16 tests de sécurité de l'updater et du helper Java réellement exporté. Confidentialité contrôlée sur les deux JAR finaux et leurs sources/accompagnements. Tests d'installation locale standard et gérée réussis.

Validation HUD/détection : tests des messages bruts, variantes linguistiques, citations rejetées, ouvertures/fermetures et paquets d'arène. Compilation Cobblemon 1.8.0 et 1.8.1 ; essais Minecraft isolés sur les deux versions. Vérification du HUD sans écran F6, du survol et de l'absence d'icônes sur un état vide. Observation ponctuelle d'une session réelle : liste des 18 arènes reçue après ouverture du navigateur et deux arènes ouvertes ; annonces de raid présentes dans le chat.

Limites : une annonce de raid sans nom de Pokémon ne permet pas d'en dessiner l'espèce. Aucune barre de boss exploitable n'était présente lors de l'observation réelle ; les portraits restent vérifiés par des paquets synthétiques explicites. Les arènes déjà ouvertes avant connexion peuvent nécessiter l'ouverture du navigateur pour recevoir leur état. Les instantanés ne prouvent pas une activité permanente et aucune durée de raid n'est inventée.

Les JAR LOCAL et partageable sont identiques. Cette Release contient uniquement le JAR partageable et son SHA-256. Les anciennes copies distribuées et leur historique ne sont pas modifiés.
