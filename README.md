# HomeGlass

Tableau de bord domotique **Android natif** au style « Apple Maison », piloté par
**Home Assistant**. Pensé pour une tablette murale : tuiles pleines, typographie Inter,
transparence façon verre dépoli, barre d'onglets flottante, réveil automatique de l'écran
au passage d'une personne.

Le dépôt contient **deux livrables** :

| Dossier | Contenu |
| --- | --- |
| `app/` | Application Android (Kotlin, Jetpack Compose) |
| `homeglass-server/` | Add-on Home Assistant « **HomeGlass Server** » (Python, FastAPI) |

---

## Ce que fait l'application

- **Affiche un tableau de bord** : un accueil (favoris, scènes, sections par famille
  d'appareils), un onglet **Pièces** (une section par pièce Home Assistant) et un onglet
  **Réglages**, avec une barre d'onglets en bas.
- **Pilote les appareils en temps réel** : lumières (allumage, luminosité, couleur,
  température de blanc), volets, serrures, prises, ventilateurs, aspirateurs, scènes,
  enceintes.
- **Suit Home Assistant** via WebSocket : chaque changement d'état met la tuile à jour en
  moins d'une seconde, sans recharger l'écran.
- **Ouvre un lecteur plein écran** dès qu'une enceinte démarre (pochette, titre, artiste,
  lecture/pause, piste suivante/précédente, volume), en mode paysage.
- **Personnalise l'accueil sans l'add-on** : un éditeur sur la tablette permet de composer
  des zones titrées et d'y placer les appareils de son choix.
- **Rallume l'écran au passage d'une personne** : capteur de proximité (seuil, anti-rebond
  et journal dans les Réglages). Le réveil par caméra frontale est amorcé mais pas encore
  actif — voir les limites connues dans [Spécificité.md](Spécificité.md).
- **Se connecte de trois façons** : appairage par code avec l'add-on (recommandé), jeton
  d'accès longue durée, ou OAuth2/PKCE (optionnel, voir plus bas).

## Ce que fait l'add-on

**HomeGlass Server** est le futur poste de conception du dashboard : pièces, sections,
tuiles, scènes, thème, onglets et profils par appareil. Il expose une **API JSON** à
l'application, relaie les appels de service et le WebSocket de Home Assistant, et sert un
panneau dans la barre latérale de Home Assistant (Ingress). Il produit le **contrat de mise
en page v1** (schéma versionné, JSON Schema généré depuis les modèles Pydantic).

> État actuel : l'application **génère encore sa mise en page localement** à partir des
> registres Home Assistant. Le contrat v1 et son parseur tolérant sont en place des deux
> côtés, et l'add-on sert déjà `/api/layout` ; le branchement de l'application sur ce
> document est l'étape suivante. L'éditeur web de l'add-on est encore une page d'accueil.

---

## Démarrage rapide

### Prérequis

- JDK 17, Android SDK (compileSdk 35), un appareil en **Android 8.0 (API 26)** minimum.
- Pour l'add-on : Home Assistant OS/Supervised, ou Python 3.12 pour le développement.

### Construire et installer l'application

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Lancer les tests

```bash
./gradlew :app:testDebugUnitTest          # 132 tests Kotlin
cd homeglass-server && .venv/bin/pytest   # 103 fonctions de test Python
```

### Installer l'add-on

1. Home Assistant → **Paramètres → Modules complémentaires → Boutique → ⋮ → Dépôts**,
   ajouter l'URL du dépôt d'add-ons : ``` https://github.com/boubalouf/HomeGlass-Addon ```.
2. Installer **HomeGlass Server**, démarrer, ouvrir le panneau **HomeGlass**.
3. Le port `8765` expose l'API utilisée par l'application (option `api_port`).

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
    data/                     Home Assistant (REST, WebSocket), cache, présence
    domain/                   contrat de layout, états, commandes, règles pures
  src/test/                   132 tests JVM
homeglass-server/             add-on Home Assistant (FastAPI)
  homeglass_server/           API, modèles Pydantic, client HA, stockage
  schema/layout.v1.json       JSON Schema du contrat v1
  tests/                      103 tests pytest
```

## Documentation détaillée

Tout le reste — architecture interne, contrat de mise en page, tables de couleurs, règles
de contraste, constantes du réveil de présence, API de l'add-on, sécurité, tests et limites
connues — est décrit dans **[Spécificité.md](Spécificité.md)**.
