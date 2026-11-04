# Changelog

## 0.2.0
Nouveau module **Volet** : un volet de notifications et de réglages rapides Material You qui remplace visuellement celui de MagicOS, sans root (désactivé par défaut, à activer dans Réglages › Volet).

- Ouverture en glissant depuis le haut de l'écran : à gauche les notifications, à droite les réglages rapides (répartition configurable). Le volet suit le doigt, s'arrête à demi puis s'ouvre en entier au second glissement ; fermeture en glissant vers le haut, par Retour (prédictif) ou en touchant à côté.
- Notifications reconstruites en natif : texte, grande image, conversations avec avatars, listes, progression ; groupes par appli repliables, sections Conversations / Alertes / Silencieuses, réponse directe, balayage pour effacer, répétition, réglages du canal, « Tout effacer », contenu masqué sur l'écran verrouillé.
- Carte média aux couleurs de la pochette, barre ondulée, choix de la sortie audio.
- Réglages rapides en grille M3 Expressive réorganisable, morphing des tuiles, curseur de luminosité épais ; Shizuku facultatif pour basculer Wi-Fi, données, NFC et mode avion.
- Fond flouté (si le système le permet), cascade des cartes, haptique subtile.
- Appui long sur un contenu de l'île issu d'une notification : ouvre le Volet dessus.
- Historique des notifications sur 7 jours (facultatif), alertes de fiabilité si MagicOS coupe un service, nouvelles pages d'autorisation.

Correctifs : île plus stable (anti-rebond du plein écran, détection via la liste des fenêtres), taille réglable plus petite.

## 0.1.0
Première version de Bulle : une Dynamic Island pour Android, 100 % locale.

- L'île calée automatiquement sur le trou de caméra, avec réglage fin, taille, forme (pilule, goutte, arrondie), couleur, contour et ombre.
- Gonflement au ressort, scission liquide de la bulle secondaire, éclat de bulles irisées aux alertes, reflet irisé au changement de contenu.
- Musique : pochette qui respire, égaliseur coloré, scrubber ondulé, lecture/pause en morphing, paroles synchronisées (LRCLIB en option).
- Activités en cours : minuteurs, chronos, navigation, livraisons, VTC, téléchargements.
- Appels entrants et en cours (décrocher, raccrocher, durée).
- Alertes système activables une à une, et le cœur pulsé d'Écho.
- Gestes configurables, recherche d'applis sur l'île, Deck (favoris, raccourcis, widgets), règles automatiques.
- Masquage automatique (plein écran, paysage, verrouillage, applis choisies).
- Fiabilité : service de maintien discret, surveillance, redémarrage au boot, tuile Réglages rapides, écran Fiabilité (commande adb copiable).
- Onboarding animé qui demande chaque autorisation, avec les étapes MagicOS et paramètres restreints ; écran Autorisations en direct.
- Mise à jour in-app depuis les releases GitHub, sauvegarde/restauration des réglages en JSON (manuelle et automatique), écran À propos.
