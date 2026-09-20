# Tropimon Events 0.2.2

By FastedCorsi

<!-- tropimon-consent-updater:2 -->

- Correction de l'écran de consentement aux mises à jour : texte dessiné après le fond, sans flou superposé, et accents français rétablis en UTF-8.
- Les boutons de refus, report et fermeture ne déclenchent aucun téléchargement. L'ancien réglage enabled=true ne vaut toujours pas consentement.
- Conservation du HUD automatique compact : événements détectés uniquement, arènes ouvertes avec carte du type sur leur porte et aucun affichage dans les menus. F6 sert au survol.
- Conservation de la correction des annonces réseau sans horodatage et de l'installation différée autonome avec sauvegarde.

Validation : compilation contre Cobblemon 1.8.0 et 1.8.1, 38 tests automatisés dont 17 tests de sécurité de l'updater. Vérification visuelle isolée de l'écran de consentement en français à 960 × 600, de l'écran de téléchargement et du défilement ; refus et fermeture sans initialisation réseau ni fichiers de mise à jour. Confidentialité contrôlée sur les sources, accompagnements et deux JAR finaux.

Limites conservées : le Pokémon d'un raid reste inconnu si aucun signal explicite ne l'identifie. Les arènes déjà ouvertes avant connexion peuvent nécessiter le navigateur officiel pour recevoir leur état ; chaque observation est un instantané. Les essais isolés ne constituent pas une validation de cette version dans une partie Tropimon réelle.

Deux JAR LOCAL/partageable identiques. Cette Release contient uniquement le JAR partageable et son SHA-256. La 0.2.1 déjà publiée reste inchangée ; cette correction utilise le canal à consentement et ne devient pas la release latest historique.
