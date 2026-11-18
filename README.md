# Bulle

Une « Dynamic Island » pour Android : une bulle animée posée autour du trou de la caméra, qui affiche la musique, les activités en cours et les alertes système, et qui réagit à des gestes.

100 % locale : sans compte, sans serveur, sans analytics. Toutes les fonctions sont gratuites.

- Package : `com.karelisio.bulle`
- Kotlin + Jetpack Compose, Material 3 Expressive
- minSdk 31 · targetSdk 36

## Fonctionnalités

- **L'île** : bulle noire calée automatiquement sur le trou de caméra (pilule du Honor Magic7 Pro comprise), réglage fin au demi-dp, taille, forme (pilule, goutte, arrondie), couleur, contour, ombre.
- **Gonflement** au ressort, contenu en fondu décalé, rebond au repli ; **scission liquide** (shader AGSL) de la bulle secondaire ; **éclat** de bulles irisées à chaque alerte ; reflet irisé au changement de contenu.
- **Musique** : pochette qui respire, égaliseur aux couleurs de la pochette, carte avec scrubber ondulé, lecture/pause en morphing, paroles synchronisées (lecteur ou LRCLIB en option, cache local).
- **Activités en cours** : minuteurs, chronos, navigation, livraisons, VTC, téléchargements (dont ProgressStyle d'Android 16).
- **Appels** : entrant (décrocher / raccrocher) et en cours avec la durée.
- **Alertes** activables une à une : batterie faible, charge, casque Bluetooth, Wi-Fi, alarme, lampe torche (+ variateur), mode avion, Ne pas déranger, capture d'écran, appli installée/désinstallée, presse-papiers. **Écho** : cœur pulsé.
- **Gestes** configurables (toucher, double toucher, appui long, glissements) vers plus de 25 actions, dont la recherche d'applis directement sur l'île.
- **Deck** : onglets d'applis favorites (icônes thématiques), raccourcis et widgets.
- **Règles** : « Quand <déclencheur>, <action> » (appli, casque, charge, heure, Wi-Fi).
- **Masquage auto** : plein écran, paysage, écran verrouillé, applis choisies.
- **Volet** (0.2.0) : volet de notifications et de réglages rapides Material You à la place de celui de MagicOS, sans root (glisser depuis le haut, à gauche ou à droite de l'île). Shizuku facultatif pour basculer Wi-Fi, données, NFC et mode avion.
- **Halo** (0.9.0) : à chaque notification, un halo lumineux fait le tour de l'écran (coins arrondis réels, caméra contournée), synchronisé avec l'île ; styles signature, trait, pulsation, vague, dégradé, étincelles ; couleur de l'icône, de la pochette, Material You ou fixe ; rose pulsé pour Écho ; option écran éteint et halo de charge.
- **Bords** (0.9.0) : zones invisibles le long des bords (glisser le long du bord, double toucher, appui long, glisser vers l'intérieur en option) vers les actions de geste ; curseur luminosité / volume avec jauge ; goutte liquide ; geste Retour préservé.
- **Veille** (0.9.0) : mode StandBy en charge à l'horizontale (économiseur d'écran ou activité de secours) : horloges, widgets, musique et paroles, diaporama, Écho, scènes animées (lave, bulles, aurore, vagues, constellation, horloge liquide) en plein écran ou en vue scindée ; mode nuit rouge, anti-marquage OLED.
- **Fiabilité** : service au premier plan discret, surveillance WorkManager, redémarrage au boot, tuiles Réglages rapides (île, Halo, Bords, Veille), tests des modules et Journal de l'île.

## Architecture

```
core/island    IslandContent, IslandModule + ModuleRegistry, Priority, ContentHub, IslandArbiter
core/settings  BulleSettings (JSON, DataStore) — un sous-objet par fonctionnalité
core/actions   Gestes → actions (ActionExecutor)
island/        Service d'accessibilité, fenêtre d'overlay, contrôleur d'état, rendu Compose
modules/       Un dossier par fonctionnalité : music, live, call, alerts, echo, deck, rules, search, volet,
               halo, edges, standby…
notifications/ NotificationListenerService partagé
system/        Maintien en vie, surveillance, boot, tuiles
```

Chaque contenu de l'île est un **module indépendant** (source + priorité + vue compacte + vue étendue) enregistré dans `modules/Modules.kt` : ajouter un module ne touche pas au cœur.

Les modules **hors île** (Halo, Bords, Veille) implémentent `IslandAddon` et sont enregistrés au même endroit : le service d'accessibilité de l'île les démarre à sa connexion et leur prête ses overlays — aucun autre service n'est créé.

## Installation

Télécharger le dernier APK depuis les [Releases](https://github.com/karelisio/bulle/releases/latest). L'appli se met ensuite à jour toute seule.

### Étapes manuelles

1. **Paramètres restreints (Android 13+)** : un APK installé hors Play Store ne peut pas activer l'accessibilité ni l'accès aux notifications tant que ces paramètres sont bloqués.
   Réglages → Applis → Bulle → menu ⋮ → **Autoriser les paramètres restreints**.
2. **MagicOS (Honor)** : Réglages → Batterie → **Lancement d'applis** → Bulle → désactiver « Gérer automatiquement » et activer les trois options (démarrage auto, démarrage secondaire, exécution en arrière-plan).
3. **Optionnel : réactivation automatique de l'accessibilité** (si le système coupe le service) :
   ```
   adb shell pm grant com.karelisio.bulle android.permission.WRITE_SECURE_SETTINGS
   ```

## Publier une version

1. Modifier le fichier `VERSION` (format `X.Y.Z`) et ajouter une section `## X.Y.Z` dans `CHANGELOG.md`.
2. Pousser sur `main`.

GitHub Actions lit `VERSION` : si le tag `vX.Y.Z` n'existe pas, il construit l'APK signé, crée le tag et publie la release. Sinon, il fait seulement un build de vérification. `versionCode` = numéro de run de la CI.

### Secrets GitHub requis

| Secret | Contenu |
|---|---|
| `BULLE_KEYSTORE_BASE64` | keystore `.jks` encodé en base64 (`base64 -w0 bulle.jks`) |
| `BULLE_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `BULLE_KEY_ALIAS` | alias de la clé |
| `BULLE_KEY_PASSWORD` | mot de passe de la clé |

Toujours le même keystore, sinon les mises à jour ne s'installent pas par-dessus.

## Feuille de route

Les versions V1 à V5 sont livrées (île, contenus utiles, outils sur l'île, API d'intégration, personnalisation avancée), ainsi que Halo, Bords et Veille (0.9.0). Reste à venir :

- **Écosystème Karelisio** : intégrations dédiées de Somna, Mago, Orbit, Miroir, Vigie et Trame (au-delà des préréglages et de l'API publique).
