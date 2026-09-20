# Tropimon Events

By FastedCorsi — 0.2.8

Des icônes Pokémon/Cobblemon directement dans le HUD. Aucune touche ni ouverture de menu n'est nécessaire : le raccourci F6 et l'écran d'inspection ont été retirés.

L'affichage est automatique : aucune touche nécessaire. Les icônes mesurent au plus 24 pixels d'interface et apparaissent après réception d'une observation pertinente, ou cinq minutes avant un raid prévu. Elles restent visibles pendant le chat : cliquer sur une arène ouverte envoie la même demande de téléportation visiteur que le bouton officiel Tropimon. Les autres clics restent disponibles pour le chat. Le HUD est masqué dans les autres menus, dont le navigateur d'arènes.

## Icônes et données

- Raids : cristal rouge ; Méga Raids : cristal violet. Le Pokémon identifié est rendu à l'intérieur par Cobblemon. Une forme Méga n'est sélectionnée que si son nom explicite et sa forme installée sont reconnus. Sinon, un point d'interrogation remplace le modèle inconnu.
- Miracles : étoiles shiny Cobblemon, bonbon XP XL, bonbon IV et Super Baie Enigma pour le talent caché. Les durées restent lisibles sous les icônes (heures, minutes, secondes). Les changements de région conservent les états connus. Une reconnexion conserve en mémoire les miracles non expirés du même joueur et les restaure après reconnaissance de Tropimon ; une nouvelle annonce ou une liste vide fait autorité. Aucun fichier de session n'est écrit.
- Arènes ouvertes : badge officiel correspondant, sans bâtiment ni carte de type. Le clic depuis le chat conserve la téléportation visiteur officielle. Les badges et la Super Baie Enigma sont lus dans les ressources installées, sans redistribution. Une arène fermée disparaît dès réception de son nouvel état.

Le mod demande automatiquement la liste des arènes avec la même commande que le navigateur officiel, cinq secondes après l'arrivée puis au plus une fois par minute lorsque le joueur n'est pas dans un menu. La réponse de cette demande actualise le HUD sans ouvrir le navigateur. Les ouvertures manuelles restent prioritaires ; une demande sans réponse n'est pas répétée en boucle. Les données des terminaux restent observées passivement. Les annonces d'ouverture/fermeture contenant explicitement un type reconnu sont aussi prises en compte en français et en anglais. Les arènes déjà ouvertes à la connexion sont ainsi récupérées sans ouverture manuelle. Chaque état reste un **instantané**, pas une surveillance globale en temps réel ; une nouvelle liste invalide l'ancien état de match. Seule la consultation de la liste est automatique ; aucune téléportation ni inscription n'est déclenchée.

Les annonces sont lues avant le filtrage du chat. L'heure ajoutée par les mods de présentation est facultative ; elle n'est pas exigée dans les paquets bruts. Les séparateurs de discussion des joueurs sont conservés pour rejeter les annonces citées dans le chat.

L'annonce textuelle de déclenchement de raid connue ne fournit pas son Pokémon. Cette version reconnaît les titres explicites de barre de boss `Raid : <Pokémon>`, `Mega Raid : <Pokémon>` ou `Méga Raid : <Pokémon>` ; ces formats sont testés sur un serveur synthétique, mais leur émission sur Tropimon réel n'est pas confirmée. Un nom de Pokémon générique, un combat proche ou une arène occupée ne suffisent jamais. Plusieurs barres concurrentes du même type sont considérées ambiguës. Une nouvelle annonce, une disparition de barre ou un changement de session invalide l'identité précédente. Aucune durée de raid ni fin de combat n'est inventée. Le rappel du prochain raid apparaît cinq minutes avant les horaires observés dans Hunter Board : 00:00, 04:00, 10:30, 15:30, 18:30, 20:00 et 21:30, fuseau Europe/Paris (heure d'été comprise). L'horaire ne prouve pas le démarrage : seule une annonce reçue produit un raid en cours. Sa durée est aléatoire ; les messages système explicites « Raid ends in … » / « Le Raid se termine dans … » fournissent un temps restant, sinon le HUD indique « En cours » sans compte à rebours de fin. Ces formats de durée sont couverts par les tests mais leur émission sur le serveur réel reste à confirmer.

## Barons et Xaero

Les Pokémon sauvages dont le statut Alpha est transmis par Cobblemon sont signalés visuellement, sans aucun son. Le HUD affiche les quatre plus proches avec portrait, niveau et distance. Les Pokémon possédés et les clones de combat sont exclus. Un Pokémon déchargé disparaît du HUD.

Avec Xaero, un contour rouge entoure l'icône déjà sélectionnée par la minimap et la carte du monde, sans remplacer son image ni ses couleurs. Si Xaero affiche des points, celui du Baron devient rouge. Les réglages de visibilité et les restrictions du radar restent respectés ; Events n'active pas des icônes désactivées dans Xaero. Sans Xaero, le HUD reste disponible. Seuls les Pokémon déjà transmis au client sont détectables.

## Vérification et compatibilité

Java 21, Minecraft 1.21.1, Fabric et Cobblemon >= 1.8.0, sans borne supérieure mineure artificielle.

```text
gradlew build remapSmokeJar
gradlew build -PofficialDependenciesOnly
gradlew build -PcobblemonJar=<jar-du-minimum>
gradlew prepareReleaseDelivery
```

Le build sélectionne l'unique Cobblemon actif par son identifiant Fabric, y compris les noms de fichiers hachés du launcher. Avec plusieurs profils, préciser l'instance via `TROPIMON_HOME`, ou utiliser la matrice explicite. Le minimum officiel est Cobblemon 1.8.0.

Tests unitaires, vérifications d'installation différée et contrôle de confidentialité des sources/JAR, archives imbriquées comprises. `tools/VerifyClient.ps1` réalise les essais réseau et visuels dans un monde local isolé ; ses paquets sont des fixtures synthétiques. Aucun essai ne pilote une arène ou un raid réel. Les données et la diffusion réelles restent à valider en situation.

## Livraison

Deux JAR identiques dans `build/release/0.2.8/local` et `build/release/0.2.8/shareable`, accompagnés de SHA-256. Ne pas charger les deux exemplaires. La livraison LOCAL inclut un installateur autonome pour le stockage géré : `mods-user`, `mods` et `user-mods-tracked.json`. Il préserve les autres mods et leur désactivation, attend la fermeture du jeu, vérifie les empreintes et sauvegarde les anciens fichiers hors des dossiers chargés. Le launcher peut rester ouvert.

L'auto-update autonome consulte uniquement la Release officielle de ce dépôt, avec vérification SHA-256/id/version, préparation hors des mods et remplacement différé sous Windows. Sur un profil géré, il met à jour les deux copies sous leur nom déjà suivi, vérifie que le suivi reste inchangé et refuse une copie divergente ou une cible modifiée. Il ne répare pas rétroactivement les anciennes versions distribuées. Vérification espacée d'au moins six heures ; désactivation locale dans `config/tropimon_events-updater.json`.

Attribution : By FastedCorsi. Crédits tiers dans THIRD_PARTY.md. Aucun autre mod Tropimon n'est requis. Les journaux, profils, captures d'une partie réelle et outils d'installation locale ne sont pas inclus dans le JAR public.


## Mises à jour avec consentement

Aucun téléchargement de mise à jour sans accord. Le premier écran propose uniquement d'autoriser la consultation des métadonnées GitHub (au démarrage, au plus toutes les six heures). Une seconde confirmation montre la version et demande explicitement le téléchargement du JAR et de son SHA-256. L'ancien réglage `enabled: true` ne donne aucune autorisation.

Après accord et vérification, un installateur local utilise le Java de Minecraft, attend la fermeture du jeu, sauvegarde l'ancien JAR hors des mods chargés et remplace uniquement ce mod. Aucun autre mod Tropimon ni changement de launcher n'est requis. Le dossier `mods` classique et le stockage géré Tropimon reconnu sont pris en charge ; une disposition inconnue, un fichier modifié/verrouillé ou une incompatibilité bloque l'installation sans forcer. Le nom du JAR installé est conservé pour rester enregistré par le launcher ; la version réelle se lit dans les métadonnées Fabric.

Pour modifier le choix en jeu : `/tropimonupdates tropimon_events`. Refuser laisse le mod utilisable. Les anciennes versions dont l'updater est défectueux nécessitent un premier remplacement manuel, jeu fermé. L'accord donné pour ce mod ne s'applique pas aux autres mods. Les tests automatisés sont exécutés sous Windows ; les autres systèmes doivent encore être validés en situation réelle.

Les versions à consentement utilisent un canal de releases distinct du lien GitHub « latest » historique : sélectionner la version par son tag. Cela évite de déclencher les anciens updaters sans accord.
