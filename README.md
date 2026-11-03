# Bulle

Une « Dynamic Island » pour Android : une bulle animée posée autour du trou de la caméra, qui affiche la musique, les activités en cours et les alertes système, et qui réagit à des gestes.

100 % locale : sans compte, sans serveur, sans analytics. Toutes les fonctions sont gratuites.

- Package : `com.karelisio.bulle`
- Kotlin + Jetpack Compose, Material 3 Expressive
- minSdk 31 · targetSdk 36

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

- **V1** (en cours) : île sur le cutout, musique + paroles, live updates, appels, alertes système, Écho, gestes, Deck, règles, bulle secondaire, styles, sauvegarde.
- **V2 — Contenus utiles** : messages + réponse rapide, volume, indicateurs caméra/micro, calendrier, météo, Pomodoro, vitesse réseau, batterie détaillée, visualiseur audio, paroles karaoké.
- **V3 — Outils sur l'île** : recherche étendue, traduction hors ligne, scanner QR, mémo vocal, enregistreur d'écran, compte à rebours, temps d'écran, mode focus.
- **V4 — Écosystème Karelisio** : API publique (permission de signature), Écho, Somna, Exit, Mago, Orbit, Miroir, Réserve, Vigie, Trame.
- **V5 — Personnalisation avancée** : packs de styles, haptique, animations d'entrée, règles combinées, accessibilité complète.
