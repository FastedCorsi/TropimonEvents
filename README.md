# Tropimon Events

By FastedCorsi — 0.1.0

**F6** : console Events (touche reconfigurable).

Barre de huit pictogrammes originaux : raid, Méga Raid, chromatique, XP, IV, talent caché, nettoyage au sol et événement saisonnier. Les annonces confirmées apparaissent dans le HUD ; les détails sont accessibles au survol lorsque le curseur est disponible, ou dans la console.

Les durées de boosts viennent des annonces reconnues FR/EN. Un raid annoncé sans durée reste « état actuel inconnu » : aucun horaire fixe ne fabrique un raid actif. Les événements saisonniers utilisent le nom, la description, la fin et les objectifs du paquet officiel ; les points, monnaie et rang viennent du paquet de progression du compte connecté. Ouvrir le menu d'événement officiel déclenche normalement l'envoi de sa définition.

Observation passive des paquets, sans les enregistrer, les remplacer ou les bloquer. Tailles bornées, SHA côté mise à jour, données par session, invalidation à la reconnexion/changement de sous-serveur. Les messages de joueurs ne sont pas interprétés comme annonces officielles.

Limites : seuls les formats vérifiés sont reconnus ; une nouvelle formulation du serveur nécessite un ajustement. Les objectifs affichés sont les valeurs annoncées, pas une progression inventée objectif par objectif.

## Compilation et vérification

Java 21, Minecraft 1.21.1, Fabric et Cobblemon >= 1.8.0. Sans borne supérieure mineure artificielle.

```text
gradlew build remapSmokeJar
gradlew build -PcobblemonJar=<jar-de-la-version-minimale>
gradlew build -PofficialDependenciesOnly
gradlew prepareReleaseDelivery
```

Le build local exige un unique JAR Cobblemon actif. `TROPIMON_HOME` permet de choisir une instance ; la matrice utilise `-PcobblemonJar`. Un exemple de CI utilisant le minimum officiel est fourni sous tools ; aucun workflow distant n'est activé dans cette livraison. Tests unitaires, contrôle de confidentialité des sources et des JAR (archives imbriquées comprises), tests d'installation sous Windows, puis test hors ligne isolé avec `tools/VerifyClient.ps1`. Aucun journal, sauvegarde ou profil réel n'est publié.

## Distribution

Deux exemplaires identiques sont produits dans `build/release/0.1.0/local` et `build/release/0.1.0/shareable`, avec SHA-256. Ne jamais charger les deux exemplaires. Le script du dossier local attend l'arrêt de Minecraft, vérifie les empreintes, conserve l'ancien JAR hors des mods et refuse une cible modifiée depuis la préparation. Le launcher peut rester ouvert.

L'auto-update est autonome : uniquement la Release du dépôt de ce mod, SHA-256, identifiant et version exacts, préparation hors des mods, remplacement différé après arrêt du jeu sous Windows. Vérification asynchrone au démarrage, espacée d'au moins six heures entre les sessions. Désactivation locale possible dans le fichier `config/<mod_id>-updater.json`.

## Périmètre de la première version

Cette version n'est pas une copie complète de HunterBoard. Pas d'interface de combat, pas de dépendance à un autre mod développé par By FastedCorsi. Les crédits tiers figurent dans THIRD_PARTY.md. Les préférences persistantes sont conservées dans AGENTS.md.

Les tests réseau sont réalisés avec des paquets synthétiques dans un vrai client Minecraft isolé. Ils ne remplacent pas une validation connectée à un événement Tropimon en cours. Aucune partie réelle n'est pilotée automatiquement.
