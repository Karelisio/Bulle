# Changelog

## 0.8.10
- Volet, éditeur de tuiles : la zone d'édition prend toute la hauteur et défile (longue liste de tuiles d'applis).
- Sortie audio : le sélecteur système de sortie (MediaRouter2) est essayé en premier, puis le panneau de Paramètres, puis SystemUI — depuis la bulle Volume et la carte média ; chaque essai est noté dans le Journal de l'île.
- Notification « Changement de périphérique audio » : un toucher ouvre le choix de la sortie audio.

## 0.8.9
- Volet, fluidité : une notification balayée sort aussitôt et celles du dessous remontent au ressort ; « Tout effacer » en cascade ; ouverture plus fluide (liste préparée hors du fil principal, icônes en cache).
- Volet, taille des cartes (nouvelle section des réglages) : densité Compacte / Normale / Aérée, taille du texte (80–120 %), arrondi, nombre de lignes en replié, aperçu en direct ; aussi pour la carte média.
- Conversations plus compactes : un seul avatar en 1-à-1 (avec la pastille de l'appli), nom · appli · heure sur une ligne ; texte sans bulle en Compacte.

## 0.8.8
- Paysage natif revu : la bulle reste autour de la caméra (sur le côté de l'écran), invisible au repos mais active, et s'ouvre depuis la caméra vers l'intérieur de l'écran.

## 0.8.7
- Volet, éditeur de tuiles : deux onglets « Mes tuiles » / « Ajouter (n) » — les tuiles des applis (et système) à ajouter sont accessibles sans faire défiler toute la grille.
- Journal : un plantage n'est affiché qu'une fois, avec sa date et la version (l'ancien plantage du Volet réapparaissait à chaque démarrage).

## 0.8.6
- Volet, tuiles des applis : découverte élargie (déclaration explicite pour MagicOS, tuiles activées à l'exécution prises en compte) ; l'éditeur indique « Recherche… » puis, si rien n'est trouvé, le nombre de services détectés (aussi noté dans le Journal de l'île).

## 0.8.5
- Gestes « Notifications » et « Réglages rapides » : ouvrent le Volet de Bulle quand il est activé (sinon celui du système).
- Volet en paysage : deux colonnes (en-tête compact, petites tuiles et luminosité à gauche ; média et notifications à droite).
- Volet : tuiles Réglages rapides des applis (section « Applis » de l'éditeur) — avec Shizuku, un toucher les active vraiment ; sinon, ouvre leurs réglages ou l'appli.
- Volet : les notifications silencieuses (et celles des services de premier plan non permanentes) se suppriment, y compris avec « Tout effacer ».
- Volet : plus de trou entre la bulle et les zones de déclenchement (bords de l'écran couverts, hauteur minimale = barre d'état) — on ne retombe plus sur le volet système en partant près de la bulle.
- Deck : les raccourcis bascules (Wi-Fi, Bluetooth, rotation, lampe, économie d'énergie, mode focus, silencieux) montrent s'ils sont activés (pastille colorée) ou non, mis à jour en direct.

## 0.8.4
- Deck : les outils qui ouvrent un autre écran (scanner QR, enregistreur, mémo, applis, réglages, capture d'écran…) referment d'abord le Deck ; les réglages instantanés (lampe, volume, média…) le laissent ouvert.
- Deck : les raccourcis se réordonnent aussi en glissant (appui long sur la poignée, ou « Monter / Descendre » avec TalkBack), comme les favoris et les widgets.

## 0.8.3
- Volet : correction du plantage à l'ouverture (animation lancée sans horloge d'images).
- Karaoké : après l'affichage des paroles, Bulle cherche en arrière-plan une version synchronisée mot à mot sur LRCLIB ; si elle existe, elle remplace l'estimation (aucune attente en plus, gardée en cache).

## 0.8.2
- Diagnostic : en cas de plantage, la trace de l'erreur est gardée sur le téléphone (jamais envoyée) et apparaît au redémarrage dans Réglages › Fiabilité › Journal de l'île (bouton Copier).
- Volet : test d'ouverture complet ajouté (fenêtre, demi-ouverture, ouverture complète, fermeture).

## 0.8.1
- Mise en veille : le Deck, les panneaux et les bulles dépliées se referment quand l'écran s'éteint.
- Paysage : nouveau mode paysage natif (activé par défaut) — la bulle reste active en haut au centre, même en plein écran (vidéo, jeu). Réglable dans Masquage.
- Messages : l'état de verrouillage est relu à chaque message (un déverrouillage manqué laissait le contenu masqué) ; chaque message est noté dans le Journal de l'île (appli, verrouillé, contenu) pour diagnostiquer.

## 0.8.0
Outils sur l'île (V3)
- **Recherche étendue** (panneau de recherche) : calculatrice, conversions d'unités, devises (taux BCE, utilisables hors ligne), contacts, raccourcis vers les Réglages.
- **Traduction du presse-papiers hors ligne** (nouvelle action de geste « Traduire le presse-papiers ») : langue détectée automatiquement, modèles téléchargés à la demande en Wi-Fi.
- **Scanner QR** (action de geste) : liens, Wi-Fi, contacts, téléphone, e-mail, lieux — résultat directement dans l'île.
- **Mémo vocal** (action de geste) : enregistrement avec chrono et niveau sonore dans l'île, liste des mémos (lecture, renommer, partager).
- **Enregistreur d'écran** (action de geste) : point rouge et chrono dans l'île, vidéo dans Films/Bulle.
- **Compte à rebours** vers tes événements (anniversaires, voyages) : « J-12 » au repos, confettis le jour J, rappel la veille.
- **Temps d'écran** (désactivé par défaut) : temps du jour, top des applis, rappel de pause, limites par appli.
- **Mode focus** : seulement l'essentiel dans l'île selon un créneau, des applis, une règle ou un geste ; le reste revient à la fin.

Personnalisation avancée (V5)
- **Packs de styles** : 8 styles intégrés, « Mes styles », partage par QR code, fichier ou texte.
- **Animations d'entrée** au choix pour chaque type de contenu.
- **Vibrations et sons** propres à chaque alerte (8 motifs, sons originaux ou système), aussi pour Écho.
- **Accessibilité** : île lisible et pilotable avec TalkBack (résumé, actions, annonces réglables), textes agrandis respectés, contraste élevé (suit le réglage système), écran Réglages › Accessibilité.
- **Règles avancées** : batterie, appareil Bluetooth précis, thème sombre, lieu, écran verrouillé, appel en cours, jours ; conditions ET/OU/PAS ; plusieurs actions ; activer/désactiver un module.

Deck
- Barre de recherche intégrée en haut du Deck (applis, calculs, conversions, devises, contacts, réglages), désactivable.
- Favoris et widgets réordonnables dans chaque onglet : appui long sur la poignée puis glisser (ou « Monter / Descendre » avec TalkBack).

Corrections
- Volume : maintenir une touche volume fait de nouveau monter ou descendre le son en continu.
- Karaoké : sans paroles mot à mot, le remplissage avance mot par mot selon les syllabes (au lieu de suivre la ligne lettre par lettre), sans ralentir la recherche des paroles.

Divers
- APK optimisé pour les téléphones ARM 64 bits, bibliothèques natives compressées.

## 0.6.0
Nouveautés (V2)
- **Messages** : un nouveau message s'affiche dans l'île (avatar, nom, début du message) ; vue étendue avec les derniers messages, **réponse rapide** au clavier, réponses suggérées, « Marquer comme lu » et « Ouvrir ». Pas d'expiration pendant la saisie ; contenu masqué quand le téléphone est verrouillé.
- **Paroles plein écran « karaoké »** depuis la carte musique : très grandes paroles synchronisées remplies au fil du chant (mot à mot si le fichier le permet), fond aux couleurs de la pochette, toucher une ligne pour y aller, écran gardé allumé, fermeture en glissant vers le bas. L'île se masque pendant cette vue.
- **Visualiseur audio réel** (option dans Musique, désactivé par défaut) : l'égaliseur et une onde suivent le vrai son. Demande l'accès au micro à l'activation — rien n'est enregistré ni envoyé ; repli automatique sur l'animation simulée si indisponible.
- **Réseau** (désactivé par défaut) : débit ↓/↑ dans l'île pendant un gros transfert, vue étendue avec graphe des 60 dernières secondes, type de connexion et consommation du jour Wi-Fi / mobile (sans permission spéciale).
- **Batterie détaillée** (désactivé par défaut) : pendant la charge, temps jusqu'à la charge complète ; vue étendue avec autonomie estimée, température, tension, santé, puissance de charge et batteries des montres et écouteurs Bluetooth.

## 0.5.0
Écosystème Karelisio (V4)
- **API d'intégration** : les applis autorisées (même signature ou certificat déclaré) affichent leurs propres contenus dans l'île par simple broadcast — modèles standard, progression, compte à rebours, chronomètre, liste, statut, cœur et respiration. Documentation : `docs/INTEGRATION.md`.
- Nouvel écran **Applis connectées** (Contenus) : autoriser ou bloquer chaque appli, voir et fermer ses contenus, bouton « Tester » qui enchaîne tous les modèles.
- Préréglages pour les applis Karelisio (couleurs, modèles, priorités).
- Trois nouvelles actions de geste : « Envoyer un pense-à-toi » (Écho), « Envie de fumer » (Exit, ouvre un exercice de respiration guidé 4-7-8 même sans l'appli), « Prix du carburant » (Réserve).

Corrections
- Volume : l'île ne se replie plus pendant qu'on fait glisser un curseur ni juste après.

## 0.4.1
- Paroles : décalage de synchronisation réglable pour chaque sortie audio (casque Bluetooth, filaire, haut-parleur), directement dans la carte avec − / + ; paroles plus grandes et plus lisibles, taille du texte et nombre de lignes visibles réglables, « mode paroles » qui leur laisse toute la place.
- Téléchargements : tous les téléchargements en cours réunis dans l'île, avec leurs pourcentages ; l'île ne se replie plus quand le téléchargement au premier plan change.
- Volume : la vue étendue affiche un curseur par source disponible (appel, média avec l'appli qui joue, sonnerie, notifications, alarme).
- Île vide invisible mais toujours touchable (toucher, appui long pour le Deck, glissements) — réglable dans Style.
- Recherche d'applis (depuis le Deck ou un geste) : ne se ferme plus quand on tape une lettre.

## 0.4.0
Nouveaux modules
- **Agenda** : prochain rendez-vous, rappel quelques minutes avant avec compte à rebours, boutons Itinéraire et Ouvrir, temps restant pendant l'événement.
- **Météo** (Open-Meteo, sans compte) : alerte « pluie dans N min », température au repos en option, position approximative ou ville choisie, cache hors ligne.
- **Minuteur / Pomodoro** lancé depuis l'île (nouvelle action de geste « Minuteur ») : anneau de progression, phases travail/pause enchaînées, vibration et son en fin de phase.
- **Volume sur l'île** (désactivé par défaut) : les touches volume affichent un curseur sur l'île à la place du panneau système, choix du flux et de la sortie audio.

Améliorations et corrections
- Alertes : une permission accordée après coup démarre l'alerte sans la recocher ; un seul message en branchant un ordinateur (USB + charge) ; heure de l'alarme qui avance.
- Appels : la durée ne repart plus de zéro quand l'appli reconstruit sa notification.
- Seconde ligne compacte remplie (artiste, étape suivante, état de l'appel) ; tailles de texte qui suivent le réglage de l'île ; interrupteur « Retour haptique » respecté partout (île, modules, Volet).
- Volet : grille de tuiles en pages au-delà de 8, plage de luminosité réelle de l'appareil, un second doigt ne fait plus sauter le volet.
- Sauvegarde : import vérifié (valeurs bornées), widgets et favoris du Deck absents de l'appareil retirés avec un message.
- Fiabilité : l'alerte balayée ne sonne plus à chaque contrôle (pas avant 12 h sauf problème différent).

## 0.3.1
- Scintillement : nouveau « rendu stable ». L'île est dessinée dans une fenêtre fixe qui n'est plus jamais redimensionnée pendant les animations ; une petite zone tactile invisible la suit pour les touchers (désactivable dans Style).
- Taille : largeur maximale réglable en compact et pour la carte étendue (Style › Taille). Une alerte large (5G…) ne prend plus tout le haut de l'écran.
- Paroles : pendant un morceau avec paroles synchronisées, l'île reste affichée même en plein écran ou dans une appli exclue, et la carte des paroles ne se ferme plus au toucher extérieur (réglables dans Musique).
- Masquage : l'écran d'accueil ne masque plus l'île (« Jamais sur l'écran d'accueil »), et une liste d'applis où le plein écran ne la masque jamais.

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
