# Changelog

## 0.3.0
- Appli : nouvelle navigation à onglets (Accueil, Style, Contenus, Réglages) avec une barre flottante Material 3 Expressive qui suit au ressort.
- Alertes : chaque alerte se personnalise (icône, couleur, animation d'entrée, durée) avec un aperçu animé ; écran rangé par catégories avec compteurs « n sur m activées ».
- Nouvelles alertes (désactivées par défaut) : perte et retour du réseau mobile, 5G, VPN, point d'accès, mode silencieux/vibreur/sonnerie, volume média, câble USB et accessoires, caméra et micro utilisés (pastilles verte et orange).
- Île : sens d'ouverture (centré, vers la gauche, vers la droite), angles lissés façon « squircle », typographie (police, taille, défilement des textes longs, seconde ligne compacte), style d'animation « Accentué », interrupteurs flou et retour haptique, réglage fin au pixel avec appui maintenu.

## 0.2.2
- Île : correction du scintillement. La détection du plein écran ignorait mal les changements des propres fenêtres de Bulle et pouvait masquer puis réafficher l'île en boucle. Elle ignore maintenant ces changements, ne masque l'île qu'après deux contrôles espacés, et se coupe d'elle-même si elle oscille encore (l'île reste alors affichée).
- Nouveau « Journal de l'île » dans Réglages › Fiabilité : apparitions, masquages et leur raison, mises à jour de la fenêtre (en mémoire uniquement), à copier pour diagnostiquer un problème d'affichage.

## 0.2.1
Version de correctifs issue d'une relecture complète de l'appli.

- Île : cycle de vie du service fiabilisé (plus de fenêtre orpheline ni de doublon à la reconnexion), la fenêtre suit un déplacement de l'île, échange des bulles dès le premier toucher, moins de recompositions pendant les animations, lampe torche correcte au premier geste, tuile Réglages rapides qui ne désactive plus l'île en la réactivant, l'île ne réapparaît plus à l'ouverture du clavier dans une appli masquée.
- Réglages : une valeur inconnue (version plus récente, sauvegarde) ne réinitialise plus tous les réglages ; curseurs Style et Position fluides (écritures regroupées).
- Volet : réponse directe visible au-dessus du clavier, plus de résumé de groupe en double, « Tout effacer » complet, mise en avant depuis l'île appliquée une seule fois, zones de réglage plus teintées après Accueil, volet refermé au verrouillage, alerte de fiabilité sur Android 12 et sans fausse alerte au démarrage, historique sans doublons, commandes Shizuku avec délai maximal.
- Contenus : un minuteur n'apparaît plus aussi comme alarme qui sonne, paroles trouvées pour les morceaux sans album, ouverture des notifications sur Android 16, fuite de l'écoute des sessions média corrigée.
- Appli : catalogue d'applis mis à jour à l'installation/désinstallation, widgets du Deck plus robustes (rotation, configuration, suppression), règle de nuit qui ne se déclenche plus au démarrage, mise à jour in-app qui ne reste plus bloquée après annulation, plusieurs plantages évités sur les surcouches sans certains écrans système.
- Fiabilité : réactivation forcée de l'accessibilité plus sûre, pas de reliaison forcée pendant le démarrage.

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
