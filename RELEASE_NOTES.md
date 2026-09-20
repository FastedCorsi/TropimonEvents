# Tropimon Events 0.2.0

By FastedCorsi

- Icônes pixel art en couleurs dans le HUD. F6 libère le curseur pour les infobulles, sans tableau de bord ni UI de combat.
- Cristaux rouge/violet avec le Pokémon rendu par Cobblemon lorsqu'un titre de barre de boss l'identifie explicitement ; point d'interrogation en cas d'absence, ambiguïté ou forme inconnue.
- États d'arène issus des vrais paquets du navigateur/terminal : ouvert/fermé, champion, défi pour le titre distinct du badge/maîtrise, statut observé et ancienneté.
- Conservation des boosts, nettoyages et données d'événement saisonnier ; invalidation des données à la reconnexion et au changement de serveur.
- Installation locale et auto-update adaptés au stockage géré du launcher, avec vérification des deux copies et du suivi.

Validation : 21 tests unitaires réussis ; tests d'installation locale, de sauvegarde, d'empreinte et de cible modifiée ; compilation contre Cobblemon 1.8.0 et le JAR actif 1.8.1. Essais Minecraft isolés et inspection des captures sur les deux versions, avec paquets synthétiques de liste d'arènes, terminal compressé, événement et barres de boss. Confidentialité contrôlée sur les sources, accompagnements et deux JAR finaux.

Limites : les annonces de déclenchement connues ne contiennent pas le Pokémon du raid. Les formats explicites de barre de boss reconnus sont décrits dans le README ; leur émission sur un raid Tropimon réel reste non confirmée. Les arènes sont des instantanés reçus à l'ouverture des menus officiels, pas un suivi global continu. Aucun test ne rejoint une partie réelle ; aucune durée ou activité n'est inventée.

Les JAR LOCAL et partageable sont identiques. Cette Release contient uniquement le JAR partageable et son SHA-256. Les anciennes copies distribuées et leur historique ne sont pas modifiés.
