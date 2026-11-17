# HomeGlass

Tableau de bord domotique **Android natif** au style « Apple Maison », piloté par
**Home Assistant**. Pensé pour une tablette murale : tuiles pleines, typographie Inter,
transparence façon verre dépoli, barre d'onglets flottante, réveil automatique de l'écran
au passage d'une personne.

Ce dépôt contient **l'application Android** (Kotlin, Jetpack Compose). L'add-on Home
Assistant « **HomeGlass Server** » (Python, FastAPI) vit dans un dépôt séparé :
[HomeGlass-Addon](https://github.com/boubalouf/HomeGlass-Addon).

| Dossier | Contenu |
| --- | --- |
| `app/` | Application Android (Kotlin, Jetpack Compose) |

---

## Ce que fait l'application

- **Affiche un tableau de bord** : un accueil composable librement, un onglet **Pièces**
  (une section par pièce Home Assistant) et un onglet **Réglages**, avec une barre
  d'onglets en bas.
- **Pilote les appareils en temps réel** : lumières (allumage, luminosité, couleur,
  température de blanc), volets, serrures, prises, ventilateurs, aspirateurs, scènes,
  enceintes.
- **Suit Home Assistant** via WebSocket : chaque changement d'état met la tuile à jour en
  moins d'une seconde, sans recharger l'écran.
- **Personnalise l'accueil directement sur la tablette** : une **grille libre** où chaque
  case se déplace au doigt et se redimensionne par sa poignée. Les cases se recalent
  automatiquement en cas de chevauchement, et la disposition est conservée après
  redémarrage. L'éditeur suit le thème clair/sombre de l'application.
- **Ajoute des cases décoratives** : horloge (3 thèmes), photo du jour (fonds Bing,
  2 thèmes) et calendrier (3 thèmes). La case « texte » a été retirée.
- **Renomme les appareils** : le nom affiché sur la tablette peut remplacer celui de Home
  Assistant, sans modifier l'entité. Les pseudos sont conservés d'un enregistrement à
  l'autre.
- **Grandes cases média** : dès 2 × 2, une enceinte affiche la pochette en grand à gauche
  et les commandes précédent / lecture-pause / suivant à droite. Le bouton bascule
  immédiatement entre lecture et pause, et la grande disposition reste affichée en pause.
- **Grandes cases capteur** : à partir de 2 × 2, la valeur s'affiche en très grand avec le
  nom et une pastille d'icône décorative pour occuper l'espace.
- **Ouvre un lecteur plein écran** dès qu'une enceinte démarre (pochette, titre, artiste,
  lecture/pause, piste suivante/précédente, volume), en mode paysage.
- **Web Player Spotify intégré** : une case au logo Spotify ouvre `open.spotify.com` en
  plein écran dans l'application, avec la session conservée d'une ouverture à l'autre. Le
  même accès est disponible depuis le lecteur vinyle et les Réglages. Aucun Client ID ni
  API Spotify : tout se passe dans la page web.
- **Rallume l'écran au passage d'une personne** : capteur de proximité (seuil, anti-rebond
  et journal dans les Réglages). Le réveil par caméra frontale est amorcé mais pas encore
  actif — voir les limites connues dans [Spécificité.md](Spécificité.md).
- **Se connecte de trois façons** : appairage par code avec l'add-on (recommandé), jeton
  d'accès longue durée, ou OAuth2/PKCE (optionnel, voir plus bas).
- **Aide au diagnostic** : un journal local des dernières commandes envoyées (heure,
  service, entité, succès ou échec) est visible dans Réglages ; les tuiles hors ligne
  indiquent depuis quand et relancent la connexion d'un appui.

---

## Démarrage rapide

### Prérequis

- JDK 17, Android SDK (compileSdk 35), un appareil en **Android 8.0 (API 26)** minimum.

### Construire et installer l'application

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Lancer les tests

```bash
./gradlew :app:testDebugUnitTest          # 140 tests Kotlin
```

### Installer l'add-on (optionnel)

L'add-on et ses instructions se trouvent dans
[HomeGlass-Addon](https://github.com/boubalouf/HomeGlass-Addon) : ajouter ce dépôt dans
**Paramètres → Modules complémentaires → Boutique → ⋮ → Dépôts**, installer
**HomeGlass Server**, puis ouvrir le panneau **HomeGlass**. Le port `8765` expose l'API
utilisée par l'application.

---

## Se connecter

Trois chemins sont proposés dans l'écran de bienvenue. L'appairage est le plus rapide :
aucun jeton à copier.

### 1. Appairage par code (recommandé, avec l'add-on)

1. Installer et démarrer **HomeGlass Server**.
2. L'écran de bienvenue affiche « HomeGlass Server vX » : le serveur est cherché sur l'hôte
   des instances Home Assistant découvertes, sur le port 8765 (ou saisir son adresse, par
   exemple `http://192.168.1.20:8765`).
3. Appuyer dessus : l'application annonce sa présence et l'add-on affiche un **code à six
   chiffres** dans le panneau Home Assistant → HomeGlass.
4. Recopier ce code dans l'application. L'appareil reçoit alors un **jeton qui lui est
   propre**, révocable depuis le même panneau.

Le code n'est jamais renvoyé à l'application : il faut le lire dans Home Assistant, donc
être déjà autorisé sur l'instance. Une fois appairée, l'application n'a plus besoin d'un
jeton Home Assistant — elle parle à l'add-on (`/api/ha/config`,
`/api/ha/services/…`, `WS /api/ha/ws`), qui se charge de Core avec son
`SUPERVISOR_TOKEN`. Le jeton d'appareil est stocké chiffré côté application, seule son
empreinte SHA-256 est conservée côté serveur, et la révocation est immédiate.

### 2. Jeton d'accès longue durée (sans l'add-on)

1. Home Assistant : **Profil → Sécurité → Jetons d'accès longue durée → Créer un jeton**.
2. Dans HomeGlass : choisir l'instance découverte (mDNS `_home-assistant._tcp`) ou saisir
   l'adresse (`http://homeassistant.local:8123`, `http://192.168.1.x:8123`…).
3. Coller le jeton, se connecter.

Ce chemin fonctionne sans add-on. Home Assistant identifie une application par l'URL de son
site (IndieAuth), un `redirect_uri` personnalisé doit donc être déclaré sur une page que
vous contrôlez — d'où le troisième chemin.

### 3. OAuth2 / PKCE (optionnel, nécessite une page à vous)

D'après la [documentation officielle de l'API d'authentification](https://developers.home-assistant.io/docs/auth_api/) :

- le `client_id` est **l'URL de la page de l'application** (HTTPS) ;
- le `redirect_uri` doit avoir le même hôte et le même port, **ou** être déclaré dans cette
  page :

```html
<link rel="redirect_uri" href="homeglass://auth-callback" />
```

Home Assistant lit les 10 premiers Ko de la page. Une alternative existe : un document JSON
`{ "client_id": "https://…/oauth/metadata.json", "redirect_uris": ["homeglass://auth-callback"] }`
servi directement en 200 à l'URL du `client_id`.

#### Ce que vous devez faire

1. Héberger **une** page statique HTTPS, par exemple `https://votre-domaine.tld/homeglass/`,
   contenant la balise ci-dessus dans son `<head>`.
2. Renseigner les deux valeurs dans `gradle.properties` (ou
   `~/.gradle/gradle.properties`) :

```properties
homeglass.oauthClientId=https://votre-domaine.tld/homeglass/
homeglass.oauthRedirectUri=homeglass://auth-callback
```

3. Reconstruire l'application. Le bouton « Se connecter avec Home Assistant (OAuth) »
   apparaît alors dans l'écran de connexion. Sans ces propriétés, l'app n'affiche que le
   jeton longue durée : aucune URL n'est codée en dur.

Le flux utilise PKCE (`S256`), un `state` anti-CSRF, un Custom Tab pour l'autorisation, puis
`/auth/token` pour l'échange et le rafraîchissement automatique. Le `redirect_uri` est capté
par la `MainActivity` (`homeglass://auth-callback`).

---

## Personnaliser l'accueil

Le bouton **Modifier** ouvre un éditeur plein écran :

- **Déplacer** : glisser une case ; les autres se recalent pour éviter tout chevauchement.
- **Redimensionner** : tirer la poignée ronde en bas à droite (de 1 × 1 jusqu'à la largeur
  de l'écran).
- **Ajouter un appareil** : la liste des appareils disponibles est **toujours visible**
  sous le champ de recherche, triée par nom ; la recherche ne fait que la filtrer.
- **Ajouter une case déco** : boutons Horloge, Photo, Calendrier.
- **Renommer** : sélectionner un appareil puis saisir un nom affiché.
- **Enregistrer** conserve la disposition ; **Tableau généré** revient à la page produite
  automatiquement par Home Assistant.

Pendant l'édition, les relevés Home Assistant sont suspendus (aucune requête ne vient
modifier le canevas) et le brouillon survit à une recréation d'activité. L'écran suit le
délai de veille de la tablette, comme partout sauf sur l'interface vinyle.
Les panneaux superposés absorbent les appuis : une zone vide de l'éditeur ou du Web Player
ne peut pas toucher une case du tableau en dessous.

La grille compte 2, 3 ou 4 colonnes selon la largeur disponible. L'éditeur utilise
l'instantané de la disposition pris à son ouverture : les mises à jour d'état Home
Assistant ne réinitialisent pas le travail en cours.

---

## Spotify (Web Player intégré)

L'application n'utilise ni OAuth Spotify, ni Client ID, ni l'API Web : un appui sur la case
**Spotify** de l'accueil (ou sur le logo Spotify du lecteur plein écran) ouvre
`open.spotify.com` dans une fenêtre plein écran, à l'intérieur de l'application. La page est
rendue par **GeckoView**, le moteur de Firefox embarqué : c'est le seul moteur Android qui
permet la lecture des contenus protégés (Widevine) de Spotify, la WebView système en étant
incapable. La connexion au compte se fait une seule fois dans cette fenêtre ; la session est
conservée d'une ouverture à l'autre. Rien n'est lu automatiquement : une piste ne démarre
que sur un appui dans la page.

### Fonctionnalités

- case **Spotify** dans l'éditeur d'accueil : logo Spotify, un appui ouvre le Web Player ;
- même accès depuis le bouton Spotify du lecteur vinyle et depuis Réglages ;
- **bibliothèque complète** (playlists, titres likés, artistes) : la page est servie en
  version de bureau, la version mobile remplaçant la bibliothèque par une invite
  d'installation de l'application ;
- retour vers le tableau de bord HomeGlass (le bouton ne remonte pas l'historique Spotify),
  barre de chargement et message d'erreur avec « Réessayer » ;
- lecture du Web Player, enceintes Spotify Connect incluses, sans quitter l'application ;
- la fenêtre n'impose pas l'écran allumé : la tablette suit son délai de veille et la
  fenêtre est restaurée au réveil ; seule l'interface vinyle maintient l'écran allumé ;
- le clavier du formulaire de connexion ne recouvre pas les champs.

### Configuration

Aucune clé : il suffit d'un accès Internet et d'un compte Spotify pour se connecter dans la
page. Le moteur est tiré depuis `maven.mozilla.org` (artefact `geckoview-arm64-v8a`) ; il
ajoute une centaine de mégaoctets à l'APK (le debug atteint ~225 Mo, la release minifiée est
plus légère). Le profil GeckoView est propre à l'application : la session Spotify ne partage
pas les cookies de Firefox. L'User-Agent de bureau est écrit dans la préférence moteur
`general.useragent.override` (GeckoView `about:config`), et non dans la session : l'override
de session déclenche un préflight CORS contenant `User-Agent`, refusé par Spotify (bug
GeckoView), ce qui casse les pochettes et l'API de lecture.

### Limites à connaître

- **ARM64 uniquement** : l'artefact GeckoView ajouté ne contient que les bibliothèques
  `arm64-v8a` (la tablette cible). Une version x86/armv7 demanderait l'artefact universel.
- La session vit dans le **profil GeckoView** ; « Se déconnecter » se fait depuis le
  Web Player.
- La lecture fait sortir le son de la **tablette** (ou d'une enceinte choisie dans Connect,
  comme dans Firefox) ; il n'y a plus de télécommande Connect native.
- Sans réseau, la page ne charge pas ; le bouton « Réessayer » recharge `open.spotify.com`.
- Spotify peut demander un compte **Premium** pour la lecture intégrale.

---

## Réseau et sécurité (résumé)

- **Jetons** : `EncryptedSharedPreferences` (AES-256, clé dans Android Keystore). Aucun jeton
  ni URL écrit en clair, `allowBackup="false"`.
- **HTTP en clair** : uniquement vers une adresse privée (10/8, 172.16/12, 192.168/16,
  169.254/16, 127/8, `*.local`, IPv6 locales). `CleartextPolicy` le vérifie à la saisie et
  `HttpClientFactory` refuse tout appel sortant en clair vers une adresse publique.
- **URL interne / externe** : sur Wi-Fi/Ethernet l'URL interne est privilégiée (test de
  joignabilité), sinon l'externe ; le choix est réévalué à chaque changement de réseau.
- **Reconnexion** : backoff exponentiel (1 s → 30 s, avec jitter), abonnements rejoués à
  chaque connexion, état exposé en Flow (pastille verte / orange / rouge dans les Réglages).
- **Présence** : aucune image n'est conservée ni transmise ; l'analyse est embarquée.

## Arborescence

```text
app/                          application Android
  src/main/java/com/homeglass/app/
    ui/                       écrans, tuiles, thème, composants
      tiles/                  FreeTileGrid, DeviceTile, MediaTile, SensorTile,
                              ClockTile, PhotoTile, CalendarTile, MediaControls
      custom/                 éditeur de la grille libre
      home/                   modèle d'accueil, placement et widgets
    data/                     Home Assistant (REST, WebSocket), cache, présence,
                              tableau personnel, fonds photo
    domain/                   contrat de layout, états, commandes, règles pures,
                              CustomDashboard et DashboardLayout (grille libre)
  src/test/                   140 tests JVM
```

## Documentation détaillée

Tout le reste — architecture interne, grille libre et éditeur, contrat de mise en page,
tables de couleurs, règles de contraste, constantes du réveil de présence, API de l'add-on,
sécurité, tests et limites connues — est décrit dans **[Spécificité.md](Spécificité.md)**.
