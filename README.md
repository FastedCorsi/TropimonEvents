# Tropimon Events

By FastedCorsi — 0.2.0

Des icônes Pokémon/Cobblemon dans le HUD, sans tableau de bord ni interface de combat. **F6** libère le curseur pour consulter les informations au survol ; F6 ou Échap rend la main au jeu. La touche est reconfigurable. La molette parcourt les longues infobulles.

## Icônes et données

- Raids : cristal rouge ; Méga Raids : cristal violet. Le Pokémon identifié est rendu à l'intérieur par Cobblemon. Une forme Méga n'est sélectionnée que si son nom explicite et sa forme installée sont reconnus. Sinon, un point d'interrogation remplace le modèle inconnu.
- Boosts chromatique, XP, IV et talent caché, nettoyage au sol et événement saisonnier : pictogrammes originaux en couleurs. Les durées viennent des annonces serveur ; nom, description, objectifs, points, monnaie et rang de l'événement restent consultables au survol.
- Arènes : bâtiment ouvert ou fermé, type, champion, dernière observation et état du défi lorsque transmis. Le défi pour le titre reste distinct des défis de badge et de maîtrise. Confirmation, préparation, match en cours, fin et annulation ne sont pas confondus.

Les données d'arène sont observées passivement dans les paquets officiels du navigateur et des terminaux. Ouvrir ces menus peut être nécessaire. Chaque état reste un **instantané**, pas une surveillance globale en temps réel ; une nouvelle liste invalide l'ancien état de match. Aucune commande, téléportation ou inscription automatique.

L'annonce textuelle de déclenchement de raid connue ne fournit pas son Pokémon. Cette version reconnaît les titres explicites de barre de boss `Raid : <Pokémon>`, `Mega Raid : <Pokémon>` ou `Méga Raid : <Pokémon>` ; ces formats sont testés sur un serveur synthétique, mais leur émission sur Tropimon réel n'est pas confirmée. Un nom de Pokémon générique, un combat proche ou une arène occupée ne suffisent jamais. Plusieurs barres concurrentes du même type sont considérées ambiguës. Une nouvelle annonce, une disparition de barre ou un changement de session invalide l'identité précédente. Aucune durée de raid ni fin de combat n'est inventée.

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

Deux JAR identiques dans `build/release/0.2.0/local` et `build/release/0.2.0/shareable`, accompagnés de SHA-256. Ne pas charger les deux exemplaires. La livraison LOCAL inclut un installateur autonome pour le stockage géré : `mods-user`, `mods` et `user-mods-tracked.json`. Il préserve les autres mods et leur désactivation, attend la fermeture du jeu, vérifie les empreintes et sauvegarde les anciens fichiers hors des dossiers chargés. Le launcher peut rester ouvert.

L'auto-update autonome consulte uniquement la Release officielle de ce dépôt, avec vérification SHA-256/id/version, préparation hors des mods et remplacement différé sous Windows. Sur un profil géré, il met à jour les deux copies sous leur nom déjà suivi, vérifie que le suivi reste inchangé et refuse une copie divergente ou une cible modifiée. Il ne répare pas rétroactivement les anciennes versions distribuées. Vérification espacée d'au moins six heures ; désactivation locale dans `config/tropimon_events-updater.json`.

Attribution : By FastedCorsi. Crédits tiers dans THIRD_PARTY.md. Aucun autre mod Tropimon n'est requis. Les journaux, profils, captures d'une partie réelle et outils d'installation locale ne sont pas inclus dans le JAR public.
