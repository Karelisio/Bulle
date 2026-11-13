# Intégrer une appli à Bulle

Bulle (`com.karelisio.bulle`) peut afficher dans son île du contenu envoyé par **d'autres applis** :
minuteurs, comptes à rebours, listes, état d'un serveur, petits cœurs, exercice de respiration…

Bulle ne lit **jamais** les données des autres applis. C'est l'appli qui décide quoi montrer et qui
le **pousse** vers Bulle par un broadcast explicite. Tout reste sur le téléphone.

---

## 1. Principe

1. L'appli cliente demande la permission `com.karelisio.bulle.permission.SHOW`.
2. Elle envoie un broadcast explicite (`setPackage("com.karelisio.bulle")`) avec l'action
   `SHOW`, `UPDATE` ou `DISMISS` et des extras (titre, modèle, progression…).
3. Bulle valide et borne chaque extra, puis affiche le contenu dans l'île (vue compacte autour de
   la caméra, vue étendue au toucher).
4. Le contenu est identifié par **paquet de l'appli + `id`** : renvoyer le même `id` met à jour le
   contenu, `DISMISS` le retire. Une appli ne peut ni voir ni modifier les contenus d'une autre.

L'utilisateur garde la main : *Réglages de Bulle → Intégrations* liste les applis qui ont envoyé du
contenu, avec un interrupteur « autoriser / bloquer » par appli et un bouton **Tester** qui montre
chaque modèle (en local, sans broadcast).

---

## 2. Permission et signature

La permission est déclarée par Bulle avec :

```xml
<permission
    android:name="com.karelisio.bulle.permission.SHOW"
    android:protectionLevel="signature|knownSigner"
    android:knownCerts="@array/bulle_known_certs" />
```

* **Même clé de signature que Bulle** : la permission est accordée automatiquement. Rien à faire.
* **Autre clé** : ajouter le condensé SHA-256 du certificat de l'appli cliente dans
  `app/src/main/res/values/arrays_known_certs.xml` (vide par défaut), puis recompiler Bulle.
  Le mécanisme `knownSigner` existe depuis Android 12 (minSdk de Bulle).

### Obtenir le condensé SHA-256

Depuis l'APK signé :

```sh
apksigner verify --print-certs mon-appli-release.apk
# Signer #1 certificate SHA-256 digest: 3a7f0c…c2
```

Depuis le magasin de clés :

```sh
keytool -list -v -keystore ma-cle.jks -alias mon-alias
# SHA256: 3A:7F:0C:…:C2
```

Écrire la valeur en **64 caractères hexadécimaux, minuscules, sans deux-points** :

```xml
<string-array name="bulle_known_certs" translatable="false">
    <item>3a7f0c…c2</item>
</string-array>
```

> La permission est accordée à l'installation de l'appli cliente. Si elle a été installée avant
> que son certificat soit ajouté à Bulle, la réinstaller (ou la mettre à jour) après Bulle.
> Vérification : `adb shell dumpsys package com.exemple.appli | grep bulle.permission`.

### Identité de l'appelant

Bulle n'accepte **pas** un nom de paquet passé en texte. Elle identifie l'appli de deux façons sûres :

1. **Extra `sender`** (obligatoire en pratique) : un `PendingIntent` créé par l'appli cliente.
   Son `creatorPackage` est fourni par le système et ne peut pas être falsifié.
2. **Android 14+** : si l'appli envoie le broadcast avec
   `BroadcastOptions.makeBasic().setShareIdentityEnabled(true)`, Bulle lit aussi `sentFromPackage`.

Si les deux sont présents et diffèrent, le broadcast est refusé. L'identité retenue doit aussi
détenir la permission. L'objet `BulleClient` ci-dessous fait tout cela.

---

## 3. Manifeste de l'appli cliente

```xml
<!-- Afficher du contenu dans l'île de Bulle -->
<uses-permission android:name="com.karelisio.bulle.permission.SHOW" />

<!-- Android 11+ : rendre Bulle visible (sinon le broadcast n'est pas délivré) -->
<queries>
    <package android:name="com.karelisio.bulle" />
</queries>
```

---

## 4. Actions

| Action | Effet |
|---|---|
| `com.karelisio.bulle.action.SHOW` | Affiche ou remplace le contenu `id` de l'appli. |
| `com.karelisio.bulle.action.UPDATE` | Identique à `SHOW` (lisibilité côté client). |
| `com.karelisio.bulle.action.DISMISS` | Retire le contenu `id` de l'appli (seul `id` est lu). |

Un `UPDATE` remplace **tout** le contenu : renvoyer tous les extras utiles, pas seulement ceux qui
changent.

---

## 5. Extras

Toute valeur invalide est **ignorée** (valeur par défaut), jamais fatale. Seul `id` est obligatoire.

| Extra | Type | Bornes / valeurs | Défaut |
|---|---|---|---|
| `sender` | `PendingIntent` | créé par l'appli (identité) | — (refus si absent avant Android 14) |
| `id` | `String` | 1 à 64 caractères `A-Z a-z 0-9 . _ : -` | **obligatoire** |
| `template` | `String` | `standard`, `progress`, `countdown`, `chronometer`, `list`, `status`, `heart`, `breathing` | préréglage de l'appli, sinon `standard` |
| `title` | `String` | 80 caractères (tronqué avec « … ») | nom de l'appli |
| `text` | `String` | 240 caractères | — |
| `subtitle` | `String` | 80 caractères ; seconde ligne compacte | — |
| `icon` | `android.graphics.drawable.Icon` | bitmap, bitmap adaptatif, données, ou ressource **de l'appli appelante** ; URI refusées | icône de l'appli |
| `iconRes` | `Int` | identifiant de ressource drawable de l'appli appelante | — |
| `accentColor` | `Int` ARGB (ou `"#RRGGBB"`) | alpha nul ⇒ opaque | préréglage, sinon couleur du modèle |
| `priority` | `String` | `media` < `live` < `alert` (jamais appel ni alarme) | `live` (`alert` pour `heart` et statut `down`) |
| `timeoutMs` | `Long` | 1 000 ms à 12 h | `alert` : 8 s ; `countdown` : échéance + 1 min ; sinon 12 h |
| `autoExpand` | `Boolean` | l'île s'étend 3 s à l'arrivée | `true` pour `alert`, sinon `false` |
| `progress` | `Int` | 0 à `max` | 0 |
| `max` | `Int` | 1 à `Int.MAX_VALUE` | 100 |
| `indeterminate` | `Boolean` | | `false` |
| `targetTime` | `Long` (heure murale, ms) | maintenant − 1 j à maintenant + 400 j | — |
| `startTime` | `Long` (heure murale, ms) | maintenant − 20 ans à maintenant + 1 min ; ≤ `targetTime` | — |
| `items` | `String[]` ou `ArrayList<String>` | 8 lignes max, 80 caractères chacune, lignes vides retirées | — |
| `itemsChecked` | `BooleanArray` | même position que `items` | toutes décochées |
| `status` | `String` | `ok`, `warn`, `down` | `ok` |
| `actionLabels` | `String[]` | 3 boutons max, 24 caractères | — |
| `actionIntents` | `PendingIntent[]` | même position que `actionLabels` (bouton ignoré sans PendingIntent) | — |
| `contentIntent` | `PendingIntent` | envoyé au toucher de la vue étendue | — |
| `showWhenHidden` | `Boolean` | pris en compte **uniquement** pour la priorité `alert` (dont statut `down`) | `false` |
| `breathPattern` | `String` | `478` (4-7-8) ou `box` (4-4-4-4) | `478` |

Limites supplémentaires : **3 contenus simultanés par appli** (le plus ancien cède sa place),
12 au total. Un modèle impossible à rendre retombe sur `standard` (`countdown` sans `targetTime`
valide, `chronometer` sans `startTime`, `list` sans ligne).

Les `PendingIntent` (actions, toucher) sont envoyés par Bulle **au nom de votre appli** :
créez-les `FLAG_IMMUTABLE`, avec un Intent explicite.

---

## 6. `BulleClient` à copier dans l'appli

```kotlin
package com.exemple.appli.bulle

import android.app.BroadcastOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle

/** Client minimal de l'API d'intégration de Bulle (voir docs/INTEGRATION.md). */
object BulleClient {
    private const val BULLE = "com.karelisio.bulle"
    const val PERMISSION = "com.karelisio.bulle.permission.SHOW"
    private const val ACTION_SHOW = "com.karelisio.bulle.action.SHOW"
    private const val ACTION_DISMISS = "com.karelisio.bulle.action.DISMISS"

    /** Bulle est installée et l'appli détient la permission. */
    fun isAvailable(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED &&
            runCatching { context.packageManager.getPackageInfo(BULLE, 0) }.isSuccess

    fun show(context: Context, content: Content) = send(context, ACTION_SHOW, content.extras)

    fun dismiss(context: Context, id: String) = send(context, ACTION_DISMISS, Bundle().apply { putString("id", id) })

    /** Jeton d'identité : Bulle lit son creatorPackage (impossible à falsifier). */
    private fun identity(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0x42B, Intent("bulle.identity").setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun send(context: Context, action: String, extras: Bundle) {
        if (!isAvailable(context)) return
        val intent = Intent(action).setPackage(BULLE).putExtras(extras).putExtra("sender", identity(context))
        if (Build.VERSION.SDK_INT >= 34) {
            val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true)
            context.sendBroadcast(intent, null, options.toBundle())
        } else {
            context.sendBroadcast(intent)
        }
    }

    /** Contenu à afficher : chaque méthode renseigne un extra. */
    class Content(id: String) {
        internal val extras = Bundle().apply { putString("id", id) }
        private val labels = ArrayList<String>()
        private val intents = ArrayList<PendingIntent>()

        fun template(value: String) = apply { extras.putString("template", value) }
        fun title(value: String) = apply { extras.putString("title", value) }
        fun text(value: String) = apply { extras.putString("text", value) }
        fun subtitle(value: String) = apply { extras.putString("subtitle", value) }
        fun icon(value: Icon) = apply { extras.putParcelable("icon", value) }
        fun accent(argb: Int) = apply { extras.putInt("accentColor", argb) }
        fun priority(value: String) = apply { extras.putString("priority", value) }
        fun timeout(ms: Long) = apply { extras.putLong("timeoutMs", ms) }
        fun autoExpand(value: Boolean) = apply { extras.putBoolean("autoExpand", value) }
        fun progress(value: Int, max: Int = 100) = apply { extras.putInt("progress", value); extras.putInt("max", max) }
        fun indeterminate() = apply { extras.putBoolean("indeterminate", true) }
        fun target(wallMillis: Long) = apply { extras.putLong("targetTime", wallMillis) }
        fun start(wallMillis: Long) = apply { extras.putLong("startTime", wallMillis) }
        fun items(lines: List<String>, checked: List<Boolean> = emptyList()) = apply {
            extras.putStringArray("items", lines.toTypedArray())
            extras.putBooleanArray("itemsChecked", checked.toBooleanArray())
        }
        fun status(value: String) = apply { extras.putString("status", value) }
        fun breathPattern(value: String) = apply { extras.putString("breathPattern", value) }
        fun showWhenHidden() = apply { extras.putBoolean("showWhenHidden", true) }
        fun onTap(intent: PendingIntent) = apply { extras.putParcelable("contentIntent", intent) }
        fun action(label: String, intent: PendingIntent) = apply {
            labels += label; intents += intent
            extras.putStringArray("actionLabels", labels.toTypedArray())
            extras.putParcelableArray("actionIntents", intents.toTypedArray())
        }
    }
}
```

Petit utilitaire pour les `PendingIntent` des exemples :

```kotlin
fun Context.openApp(): PendingIntent = PendingIntent.getActivity(
    this, 0, packageManager.getLaunchIntentForPackage(packageName), PendingIntent.FLAG_IMMUTABLE,
)
```

---

## 7. Modèles

Chaque modèle a une vue compacte (de part et d'autre de la caméra) et une vue étendue animée
(ressorts Material 3 Expressive, retour haptique discret, animations coupées si l'utilisateur a
choisi « Supprimer les animations »).

### `standard`

Icône + titre en compact ; icône, nom de l'appli, titre et texte en étendu.

```kotlin
// Trame : nouveau chapitre disponible.
BulleClient.show(
    context,
    BulleClient.Content("chapitre-42")
        .template("standard")
        .title("Nouveau chapitre")
        .text("La Tour des vents — chapitre 42")
        .subtitle("Trame")
        .icon(Icon.createWithResource(context, R.drawable.ic_book))
        .onTap(context.openApp())
        .timeout(60 * 60 * 1000L),
)
```

### `progress`

Anneau de progression autour de l'icône et pourcentage en compact ; barre au ressort en étendu.

```kotlin
// Somna : réveil progressif (lumière qui monte).
BulleClient.show(
    context,
    BulleClient.Content("reveil")
        .template("progress")
        .title("Réveil en douceur")
        .text("Lumière à 65 %")
        .progress(65, 100),
)
// Durée inconnue : .indeterminate()
```

### `countdown`

Temps restant qui défile (chiffres roulants) jusqu'à `targetTime`. Si `startTime` est fourni, un
anneau montre l'avancement. Disparaît une minute après l'échéance (sauf `timeoutMs`).

```kotlin
// Orbit : prochain événement du couple.
val event = System.currentTimeMillis() + 42 * 60_000L
BulleClient.show(
    context,
    BulleClient.Content("evenement-123")
        .template("countdown")
        .title("Dîner au restaurant")
        .text("Pense à réserver un taxi")
        .start(System.currentTimeMillis())
        .target(event)
        .onTap(context.openApp()),
)
```

### `chronometer`

Temps écoulé depuis `startTime` (jusqu'à 20 ans en arrière ; au-delà d'un jour : « 12 j 05 h »).

```kotlin
// Exit : temps sans cigarette.
BulleClient.show(
    context,
    BulleClient.Content("sans-tabac")
        .template("chronometer")
        .title("Sans cigarette")
        .text("Bravo, continue !")
        .start(quitDateMillis)
        .priority("media"), // discret : cède la place à la musique, aux appels…
)
```

### `list`

Compteur « cochées / total » en compact ; jusqu'à 8 lignes à cocher en étendu (deux colonnes au-delà
de 4, lignes cochées barrées).

```kotlin
// Mago : liste de courses.
BulleClient.show(
    context,
    BulleClient.Content("courses")
        .template("list")
        .title("Courses")
        .items(listOf("Pain", "Lait", "Pommes", "Café"), checked = listOf(true, false, false, true))
        .onTap(context.openApp()),
)
```

### `status`

Pastille colorée (verte `ok`, ambre `warn`, rouge `down`, pulsée sauf `ok`). `down` force la
priorité `alert` (l'île s'étend) et autorise `showWhenHidden`.

```kotlin
// Vigie : conteneur en panne.
BulleClient.show(
    context,
    BulleClient.Content("vps-paris")
        .template("status")
        .status("down")
        .title("vps-paris")
        .text("Conteneur « web » arrêté depuis 3 min")
        .showWhenHidden()
        .action("Redémarrer", restartPendingIntent)
        .action("Journal", logsPendingIntent),
)
// Retour à la normale : même id, .status("ok"), ou BulleClient.dismiss(context, "vps-paris").
```

### `heart`

Cœur pulsé (double battement, halo, petits cœurs qui montent : l'animation d'Écho). Priorité
`alert` par défaut.

```kotlin
// Écho : « pense à toi » reçu.
BulleClient.show(
    context,
    BulleClient.Content("pense-a-toi")
        .template("heart")
        .title("Alex pense à toi")
        .text("Un petit cœur rien que pour toi")
        .onTap(context.openApp()),
)
```

### `breathing`

Cercle qui respire en compact ; en étendu, un bouton **Commencer** ouvre l'exercice dans l'île :
le cercle grossit à l'inspiration et rétrécit à l'expiration, avec la consigne « Inspire / Bloque /
Expire ». `478` : 4 cycles de 19 s (≈ 1 min 16) ; `box` : 6 cycles de 16 s (≈ 1 min 36).

```kotlin
// Exit : proposer un exercice quand l'envie arrive.
BulleClient.show(
    context,
    BulleClient.Content("respirer")
        .template("breathing")
        .title("Respirer")
        .text("Une minute pour laisser passer l'envie")
        .breathPattern("478"),
)
```

---

## 8. Préréglages des applis Karelisio

Quand une appli Karelisio n'envoie pas `template`, `accentColor` ou `priority`, Bulle applique son
préréglage :

| Appli | Paquet | Modèle par défaut | Couleur |
|---|---|---|---|
| Écho | `com.karelisio.echo` | `heart` (priorité `alert`) | `#FF6FA5` |
| Somna | `com.karelisio.somna` | `progress` (coucher, réveil progressif) | `#9C8CFF` |
| Exit | `com.karelisio.exit` | `chronometer` (temps sans cigarette) | `#5BD68A` |
| Mago | `com.karelisio.mago` | `list` (courses) | `#FFA24D` |
| Orbit | `com.karelisio.orbit` | `countdown` (prochain événement) | `#6FB6FF` |
| Miroir | `com.karelisio.miroir` | `standard` (question du jour) | `#C7A6FF` |
| Réserve | `com.karelisio.reserve` | `list` (prix du carburant) | `#FFC14D` |
| Vigie | `com.karelisio.vigie` | `status` (serveur, conteneurs) | `#4DD0C4` |
| Trame | `com.karelisio.trame` | `standard` (nouveau chapitre) | `#E8A27C` |

---

## 9. Intentions envoyées par Bulle aux applis Karelisio

Certaines actions de geste de Bulle **demandent** quelque chose à une appli. Bulle envoie un
broadcast explicite (`setPackage`) ; si l'appli n'est pas installée, rien n'est envoyé et l'île
l'indique. Extra commun : `source` = `"com.karelisio.bulle"` (informatif). Sur Android 14+, Bulle
partage son identité : vérifiez `sentFromPackage == "com.karelisio.bulle"`.

| Action | Appli | Attendu de l'appli |
|---|---|---|
| `com.karelisio.echo.action.SEND_THOUGHT` | Écho | Envoyer un « pense-à-toi » au partenaire. |
| `com.karelisio.exit.action.CRAVING` | Exit | Compter une envie de fumer (Bulle ouvre elle-même l'exercice de respiration). |
| `com.karelisio.reserve.action.REQUEST_FUEL` | Réserve | Chercher les prix puis répondre via l'API (`list` ou `standard`). |

Côté appli (exemple Écho) — le receiver peut être protégé par la permission de Bulle, que seules
Bulle et les applis de confiance détiennent :

```xml
<receiver
    android:name=".bulle.BulleRequestReceiver"
    android:exported="true"
    android:permission="com.karelisio.bulle.permission.SHOW">
    <intent-filter>
        <action android:name="com.karelisio.echo.action.SEND_THOUGHT" />
    </intent-filter>
</receiver>
```

```kotlin
class BulleRequestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 34 && sentFromPackage != null && sentFromPackage != "com.karelisio.bulle") return
        when (intent.action) {
            "com.karelisio.echo.action.SEND_THOUGHT" -> ThoughtSender.sendToPartner(context)
        }
    }
}
```

Réserve répond à `REQUEST_FUEL` en poussant un contenu :

```kotlin
BulleClient.show(
    context,
    BulleClient.Content("carburant")
        .template("list")
        .title("Gazole autour de vous")
        .items(listOf("Leclerc — 1,62 €", "Total — 1,68 €", "Esso — 1,71 €"))
        .timeout(10 * 60_000L)
        .onTap(context.openApp()),
)
```

---

## 10. Confidentialité

* Aucune donnée ne quitte le téléphone : pas de réseau, pas de compte, pas de statistiques.
* Bulle ne lit que les extras envoyés ; elle n'accède à aucune donnée des autres applis.
* Les icônes par URI sont refusées (Bulle ne lit pas de contenu au nom d'une autre appli).
* Bulle ne garde que la liste des applis ayant envoyé du contenu (paquet, dernière activité,
  autorisée / bloquée), dans ses préférences locales. Les contenus eux-mêmes ne sont gardés qu'en
  mémoire, jusqu'à expiration ou `DISMISS`.
* Le journal de diagnostic de Bulle n'enregistre que les refus (paquet et motif), jamais le texte.

---

## 11. Dépannage

| Symptôme | Cause probable |
|---|---|
| Rien ne s'affiche | Permission non accordée (`isAvailable` faux), `<queries>` manquant, appli bloquée dans *Intégrations*, module « Applis connectées » désactivé. |
| Refus « identité inconnue » (journal) | Extra `sender` absent ou `PendingIntent` créé par une autre appli. |
| Modèle `standard` au lieu du modèle demandé | Donnée manquante ou hors bornes (`targetTime`, `startTime`, `items`). |
| Un ancien contenu disparaît | Plus de 3 contenus simultanés pour l'appli. |
| Le contenu disparaît trop tôt | `timeoutMs` (8 s par défaut pour une alerte). |
