# HomeGlass — spécificité technique

Document de référence de tout ce qui se trouve dans ce dépôt : application Android, add-on
Home Assistant, contrat de mise en page, design system, réseau, sécurité, présence, tests et
limites connues.

- Application : `com.homeglass.app`, version **0.1.0** (`versionCode 1`)
- Add-on : `homeglass_server`, version **0.2.0**
- Contrat de mise en page : **schéma v1**
- Langue de l'interface : français (textes dans `strings.xml`)

---

## 1. Vue d'ensemble

```text
┌──────────────────────────────┐        ┌──────────────────────────────┐
│  Application Android         │        │  HomeGlass Server (add-on)   │
│  Compose, une activité       │        │  FastAPI, port 8765          │
│                              │        │  Ingress HA sur 8099         │
│  ui/  écrans, tuiles, thème  │        │                              │
│  data/  REST, WS, DataStore  │◀──────▶│  /api/pair/*   appairage     │
│  domain/  règles pures       │  JSON  │  /api/layout   contrat v1    │
└──────────────┬───────────────┘        │  /api/ha/*     passerelle    │
               │                        │  /internal/*   éditeur       │
      REST + WebSocket                  └──────────────┬───────────────┘
               │                                       │ REST + WS
               ▼                                       ▼
        ┌──────────────────────────────────────────────────┐
        │              Home Assistant Core                 │
        └──────────────────────────────────────────────────┘
```

L'application peut fonctionner **sans l'add-on** (jeton longue durée, REST/WS directs) ou
**avec** lui (appairage, jeton d'appareil, passerelle). L'add-on est aussi le support du
futur éditeur de dashboard et des profils par appareil.

Principes directeurs :

1. **Une seule activité**, une seule coquille Compose ; la navigation est un état, pas un
   graphe de destinations.
2. **Aucune donnée sensible en clair** : jetons chiffrés, pas d'URL par défaut, pas de
   sauvegarde automatique.
3. **Le domaine est pur** : parsing, mapping, planification, génération ne touchent ni
   Android ni le réseau, et sont couverts par des tests JVM.
4. **Tolérance aux documents** : le parseur de layout ignore les champs inconnus, applique
   les valeurs par défaut et refuse proprement une version future.
5. **Design system explicite** : couleurs, dimensions, typographie, formes et animations
   sont des jetons, jamais des valeurs recopiées dans les écrans.

---

## 2. Application Android — build

| Élément | Valeur |
| --- | --- |
| `namespace` / `applicationId` | `com.homeglass.app` |
| `compileSdk` / `targetSdk` | 35 |
| `minSdk` | 26 (Android 8.0) |
| JVM | 17 (`jvmTarget`, `compileOptions`) |
| Build features | `compose = true`, `buildConfig = true` |
| Release | `isMinifyEnabled = true`, `isShrinkResources = true` |
| Langues gardées | `resourceConfigurations += ["fr", "en"]` |
| Dépendances test | JUnit 4, `kotlinx-coroutines-test` |

`gradle.properties` porte les propriétés OAuth, lues dans `buildConfigField` et **vides par
défaut** (aucune URL codée en dur) :

```properties
homeglass.oauthClientId=
homeglass.oauthRedirectUri=homeglass://auth-callback
```

`HaAuthManager.isConfigured` vaut `true` seulement si `clientId` et `redirectUri` sont
non vides ; sinon le bouton OAuth n'est pas proposé.

### Dépendances principales

| Dépendance | Usage |
| --- | --- |
| Compose BOM + Material 3 | socle technique uniquement (aucun composant Material utilisé tel quel) |
| `kotlinx-serialization-json` | contrat de layout, cache, requêtes |
| `kotlinx-coroutines` | flux d'états, appels, throttling |
| `androidx.datastore:datastore-preferences` | caches et réglages |
| `androidx.security:security-crypto` | `EncryptedSharedPreferences` |
| OkHttp | REST et WebSocket |
| `androidx.browser` | onglet personnalisé pour OAuth |
| `androidx.camera` (camera2) + `lifecycle-service` | analyse caméra du service de présence |
| `mlkit:face-detection` | détection de visage (voir limites) |
| `dev.chrisbanes.haze` | flou du verre dépoli |
| Polices Inter (fichiers de police embarqués) | substitut de SF Pro |

---

## 3. Architecture de l'application

```text
com.homeglass.app
├── MainActivity.kt            activité unique, OAuth, réveil, service de présence
├── core/
│   ├── haptics/GlassHaptics.kt        retours haptiques
│   └── system/ReduceMotion.kt         ANIMATOR_DURATION_SCALE == 0
├── ui/
│   ├── AppRoot.kt             coquille, onglets, feuilles, toast, lecteur
│   ├── components/            GlassSurface, GlassBackground, GlassHaze, DeviceShell, StaggeredAppearance
│   ├── navigation/            GlassBottomNavBar, NavTab
│   ├── home/                  Maison (modèle, écran, mapping des états, placement, widgets)
│   ├── rooms/                 Pièces
│   ├── settings/              Réglages
│   ├── tiles/                 FreeTileGrid, DeviceTile, LightTile, CoverTile, MediaTile,
│   │                          SensorTile, ClockTile, PhotoTile, CalendarTile, MediaControls…
│   ├── media/                 lecteur plein écran et déclencheur « now playing »
│   ├── web/                   SpotifyWebScreen (Web Player intégré)
│   ├── detail/                MoreInfoSheet, ColorWheel
│   ├── custom/                éditeur du tableau de bord personnel
│   ├── welcome/               écran de bienvenue / connexion / appairage
│   ├── theme/                 Color, Dimens, Type, Shape, Motion, ThemeConfig, HomeGlassTheme
│   └── showcase/              DesignSystemShowcase (aperçu interne)
├── domain/
│   ├── ha/                    EntityState, EntityPresentation, HaProtocol, BaseUrlChooser,
│   │                          ExponentialBackoff, Registries, MediaInfo, PairingProtocol
│   ├── layout/                Layout, LayoutItems, LayoutParser, LayoutGenerator, LayoutState
│   ├── dashboard/             CustomDashboard, PlacedTile, DashboardLayout, Widget
│   └── presence/              PresenceTuning, PresencePlanner, PresenceSource
└── data/
    ├── ha/                    HaController, HaRealtime, HaHttpClient, HaAuthManager,
    │                          HaDiscovery, AddonDiscovery, SessionStore, NetworkMonitor,
    │                          HttpClientFactory, TileCommands, GeneratedLayoutSource
    ├── layout/                LayoutRepository, LayoutCache, LayoutSources
    ├── custom/                CustomDashboardStore
    ├── widgets/               BingPhotoRepository (fonds photo du jour)
    └── presence/              PresenceService, PresenceStore
```

Règles de dépendance : `ui` et `data` dépendent de `domain`, jamais l'inverse ; `data` ne
dépend jamais de `ui`. Les règles de décision (présentation d'entité, parsing, planification
de réveil, génération de layout) sont des fonctions pures testables sans Android.

### Cycle de vie

1. `MainActivity.onCreate` crée `HaController` (une instance par activité, conservée par
   `remember`) avec un `CoroutineScope` lié à l'activité.
2. `HaController.start()` relance la session mémorisée : ouverture REST + WebSocket,
   chargement du cache de layout, génération dès que les registres arrivent.
3. `AppRoot` observe `layoutRepository`, `session`, `status` et `states`, puis rend la
   coquille ou l'écran de bienvenue.
4. Au lancement, un `LaunchedEffect` démarre `PresenceService` si le réglage est actif ;
   l'activité ne le relance pas à chaque `onResume`.
5. Au réveil par présence, l'intent porte `EXTRA_WAKE_UP` : `setShowWhenLocked(true)` et
   `setTurnScreenOn(true)` sont appliqués (`onCreate`/`onNewIntent`) avant l'affichage.

---

## 4. Navigation et écrans

La coquille `AppRoot` empile, de bas en haut :

- le contenu de l'onglet courant ;
- la barre de navigation flottante (3 onglets : Maison, Pièces, Réglages) ;
- la feuille d'informations (`MoreInfoSheet`) ;
- le lecteur plein écran, quand une enceinte joue ;
- le toast d'erreur de commande, en bas.

Les onglets proviennent du layout (`tabs`) ; le type détermine l'écran rendu
(`home`, `rooms`, `settings`). L'ancien type `automations` est retiré avant décodage ; un
type inconnu retombe sur `home`, et l'onglet sélectionné est le premier onglet valide :
l'application ne peut pas se retrouver sur un écran inexistant.

| Écran | Contenu |
| --- | --- |
| **Maison** | grand titre, grille libre personnalisée ou sections générées, cartes de scènes |
| **Pièces** | une section par pièce (nom, icône, tuiles) |
| **Réglages** | compte, statut de connexion, personnalisation, présence, à propos |
| **Bienvenue** | découverte d'instances, jeton, OAuth, appairage add-on (`ui/welcome`) |
| **Lecteur** | plein écran paysage, pochette, transport, volume (`ui/media`) |
| **Tableau personnel** | grille libre : déplacement, redimensionnement, cases déco, renommage (`ui/custom`, `ui/home/CustomDashboard.kt`) |
| **Showcase** | planche du design system (écran interne, `ui/showcase`) |

### Chips de synthèse

`SummaryChipUi` est une énumération fermée : `Lights`, `Climate`, `Security`, `Media`,
`Energy`, chacune avec une icône et un libellé de `strings.xml`. Elles synthétisent l'état
de la maison en un coup d'œil, sans interaction.

### Feuille d'informations

Un appui long ouvre une feuille basse listant les attributs utiles de l'entité (fermeture
par glissement ou appui sur le fond). Exceptions : un appui long sur une enceinte ouvre le
lecteur plein écran ; une tuile sans action (`MoreInfo`, `None`, ou `toggle` sans entité)
ouvre la même feuille au simple appui ; un appui simple sur une enceinte met en pause au
lieu de l'éteindre.

---

## 5. Design system

Tout passe par `HomeGlassTheme` et l'objet `GlassTheme` (`colors`, `dimens`, `typography`,
`shapes`, `motion`). Material 3 n'est là que pour alimenter les primitives de texte/icône :
`glassColorScheme()` ne mappe que `primary`, `background`, `surface`, `onBackground`,
`onSurface`.

### 5.1 Configuration

```kotlin
data class ThemeConfig(
    val mode: ThemeMode = ThemeMode.Auto,       // Auto | Light | Dark
    val accentColor: Color? = null,             // sinon AccentOrange
    val tileCornerRadius: Dp? = null,           // sinon 22 dp
    val densityScale: Float = 1f,               // borné à 0.85 … 1.30
    val wallpaper: WallpaperConfig? = null,     // url, scrim 0.35, flou 0 dp
)
```

`densityScale` dilate la mise en page : la tablette murale peut être réglée plus dense sans
toucher au code. Le mode `Auto` suit le réglage système (`isSystemInDarkTheme`), pas l'heure.
`wallpaper` est prévu pour l'image de fond fournie par l'add-on (`assetId`), avec scrim et
flou ; il n'est pas encore appliqué par les écrans.

### 5.2 Palette

Couleurs de fond, en dégradé vertical :

| Jeton | Clair | Sombre |
| --- | --- | --- |
| `LightGradientStart` / `DarkGradientStart` | `#F2F2F7` | `#000000` |
| `LightGradientEnd` / `DarkGradientEnd` | `#E5E5EA` | `#1C1C1E` |
| Accent par défaut | `#FF9500` | `#FF9500` |

Surfaces et textes (les valeurs sombres des tuiles sont **inversées** : une tuile active est
claire, le texte doit donc être sombre) :

| Jeton | Clair | Sombre |
| --- | --- | --- |
| `tileActiveBackground` | `#FFFFFF` | `#F5F5F7` |
| `tileInactiveBackground` | `#8CFFFFFF` (blanc 55 %) | `#B32C2C2E` (gris 70 %) |
| `screenTextPrimary` | `#1C1C1E` | `#F2F2F7` |
| `screenTextSecondary` | `#6C6C70` | `#AEAEB2` |
| `textPrimary` | `#000000` | `#1C1C1E` |
| `textSecondary` | `#6C6C70` | `#6C6C70` |
| `textOnInactivePrimary` | `#3C3C43` | `#F2F2F7` |
| `textOnInactiveSecondary` | `#54545A` | `#C7C7CC` |
| `iconOnInactive` | `#8A8A8E` | `#AEAEB2` |
| `iconNeutral` | `#6C6C70` | `#AEAEB2` |
| `navBarBackground` | `#B8FFFFFF` | `#B81C1C1E` |
| `chipBackground` | `#A6FFFFFF` | `#A62C2C2E` |
| `sheetBackground` | `#EBF2F2F7` | `#EB1C1C1E` |
| `sheetHandle` | `#593C3C43` | `#8E8E93` |

Couleurs sémantiques (identiques dans les deux thèmes, elles portent l'icône de la tuile
active) : lumière `#FFB800`, chauffage `#FF9500`, froid `#0A84FF`, interrupteur `#34C759`,
sécurité `#34C759`, avertissement `#FF9500`, danger `#FF3B30`, média `#FF375F`,
volet `#64D2FF`, capteur `#8E8E93`, générique `#8E8E93`.

### 5.3 Typographie

Police **Inter** (5 graisses embarquées), substitut de SF Pro. Échelle en `sp` :

| Style | Taille / interligne | Graisse | Usage |
| --- | --- | --- | --- |
| `largeTitle` | 34 / 41 | Bold | titre d'écran |
| `sectionTitle` | 22 / 28 | Bold | titre de section |
| `tileName` | 15 / 19 | SemiBold | nom d'appareil |
| `tileStatus` | 13 / 16 | Regular | état sous le nom |
| `sensorValue` | 26 / 30 | Bold | valeur de capteur |
| `caption` | 12 / 15 | Medium | chips, notes |

### 5.4 Dimensions

| Jeton | Valeur |
| --- | --- |
| `tileHeight` | 96 dp |
| `tileCornerRadius` | 22 dp (surchargeable) |
| `sheetTopCornerRadius` | 28 dp |
| `screenPadding` | 16 dp |
| `tilePadding` | 14 dp |
| `gridSpacing` | 12 dp |
| `tileIconSize` | 28 dp |
| `navBarHeight` | 64 dp |
| `navBarMargin` | 12 dp |
| `touchTarget` | 48 dp |

`GlassDimens().scaled(densityScale)` ne dilate que `screenPadding`, `gridSpacing`,
`tileHeight` et `tilePadding` ; rayons, icônes et barre de navigation gardent leur taille
(densif sans casser les proportions).

### 5.5 Formes et animations

Formes : tuile (rayon configurable), feuille (haut arrondi 28 dp), pilule, chip et barre de
navigation (50 %).

Animations (`GlassMotion`) : uniquement des ressorts, jamais de `tween` linéaire.

| Usage | Spec |
| --- | --- |
| changement d'état d'une tuile | `spring(dampingRatio = 0.8, stiffness = 900)` ≈ 250 ms |
| enfoncement | `spring(dampingRatio = 0.6, stiffness = 1400)` |
| apparition | `spring(dampingRatio = 0.85, stiffness = 620)` |
| échelonnement | 30 ms entre deux tuiles |

Si l'échelle d'animation système vaut 0 (`ANIMATOR_DURATION_SCALE`), `rememberReduceMotion`
renvoie `true` et toutes les transitions deviennent `snap()`. L'observateur de réglage
réagit au changement sans recréer l'activité.

### 5.6 Grille

| Largeur disponible | Colonnes |
| --- | --- |
| < 600 dp | 2 |
| 600 – 839 dp | 3 |
| ≥ 840 dp | 4 |

Deux grilles coexistent :

- `TileGrid` pour l'accueil **généré** (sans tableau personnel). Il n'utilise pas
  `LazyVerticalGrid` : un algorithme de placement maison garantit que toutes les tuiles
d'une rangée partagent **exactement** `tileHeight`, y compris les tuiles larges (2 × 1) et
grandes (2 × 2). Les tuiles `wide` occupent 2 colonnes, les `large` 2 × 2 ; le placement
comble les trous dans l'ordre du document.
- `FreeTileGrid` pour l'accueil **personnalisé**. Chaque case porte sa position
  (`gridCol`, `gridRow`) et sa portée (`spanX`, `spanY`) ; l'ordre du document n'impose
  rien. Une case peut donc être placée entre deux autres, sur une seule cellule, ou
  occuper plusieurs colonnes et rangées.

`DashboardLayout` (domaine pur, testé) fournit `overlaps`, `firstFree`, `autoPlace` et
`settle` : après un déplacement ou un redimensionnement, les cases qui se chevauchent sont
recalées vers le premier emplacement libre, avec une animation de ressort. Une case peut
être déplacée jusqu'à la largeur de l'écran et redimensionnée sur autant de rangées que
nécessaire.

---

## 6. Tuiles

### 6.1 Modèle d'affichage

`TileUiModel` (immuable) transporte tout ce qu'une tuile doit afficher :

| Champ | Type | Rôle |
| --- | --- | --- |
| `id` | `String` | identifiant dans le document |
| `name` | `String` | libellé |
| `stateText` | `String` | état déjà localisé (« Allumé », « 72 % »), omis s'il est vide |
| `category` | `TileCategory` | `Light`, `Climate`, `Switch`, `Cover`, `Lock`, `Alarm`, `Media`, `Sensor`, `Camera`, `Vacuum`, `Scene`, `Generic`, `Clock`, `Photo`, `Calendar` — pilote icône et couleur |
| `isActive`, `isAvailable` | `Boolean` | fond actif / entité disponible |
| `size` | `TileSize` | `Small` (1×1), `Wide` (2×1), `Large` (2×2) |
| `level` | `Float?` | 0…1 : luminosité ou position |
| `tint` | `Color?` | accent surchargé par la configuration |
| `display` | `TileDisplay` | `Auto`, `Button`, `Slider`, `Value`, `Graph` |
| `tap`, `longPress` | `TileAction` | `None`, `Toggle`, `MoreInfo`, `CallService`, `Navigate` |
| `entityId` | `String?` | entité pilotée (`null` pour une tuile locale) |
| `media` | `MediaInfo?` | piste en cours pour une enceinte |

Le mapping état Home Assistant → modèle est centralisé dans `EntityPresentation` (et
`LiveStateMapping` pour fusionner les états) : les tuiles ne connaissent aucun domaine Home
Assistant.

### 6.2 Rendu

- `TileShell` : fond animé entre `tileActive` et `tileInactive`, rayon, ombre douce,
  `scale = 0.96` à l'appui, retour haptique, `alpha = 0.4` si l'entité est indisponible,
  `contentDescription` fusionnée pour TalkBack, appui simple + appui long.
- `LightTile` : si `level` est non nul, affiche une glissière de luminosité intégrée.
- `CoverTile` : glissière de position (0–100 %).
- `MediaTile` : pochette, titre, artiste et état (`mediaStatusText`) ; un appui met la
  lecture en pause sans couper l'alimentation, un appui long ouvre le lecteur plein écran.
  Dès **2 × 2**, la case passe en disposition « grande » : pochette carrée en grand à
  gauche, et à droite uniquement précédent / lecture-pause / suivant. La disposition reste
  affichée en pause ; l'icône du bouton bascule dès le toucher (état optimiste) puis se
  recale sur l'état réel renvoyé par Home Assistant. Les commandes passent par
  `LocalMediaControls` (`MediaControlHandlers`), fourni à la racine, pour ne pas traverser
  toutes les grilles.
- `SensorTile` : valeur en grand (`sensorValue`), nom en dessous, icône masquée
  (`showIcon = false`) pour laisser la place au chiffre. À partir de **2 × 2**, la grande
  valeur et le nom passent à gauche et une pastille circulaire avec l'icône du capteur
  occupe la droite ; les formats large (2 × 1) et haut (1 × 2) gardent une disposition
  côte à côte ou empilée.
- `ClockTile`, `PhotoTile`, `CalendarTile` : cases décoratives locales (horloge 3 thèmes,
  photo du jour Bing 2 thèmes, calendrier 3 thèmes). Elles n'ont aucune entité et
  n'exécutent aucune action. La case « texte » a été retirée : `WidgetKind.Title` n'est
  conservé que pour relire d'anciens documents, et les cases correspondantes sont
  supprimées au chargement.
- Cartes de scènes : dégradé propre à la scène, nom, action `scene.turn_on`.

### 6.3 Règle de contraste (correctif important)

Les tuiles actives sont claires en thème sombre, et sombres en thème clair. Le texte ne peut
donc pas être figé : il est choisi d'après la **luminance perçue** du fond effectif.

```kotlin
private fun effectiveBackground(isOn: Boolean, available: Boolean): Color // active / inactive
private fun Color.luminance(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue
// texte clair si luminance < 0.5, sombre sinon
```

Appliqué dans `DeviceTile`, `MediaTile` et `SensorTile`. Piège corrigé : en Compose,
`Color.red/green/blue` sont des **Float 0…1** ; un test du type `(r + g + b) < 384` est donc
toujours vrai (somme maximale 3) et rendait le texte blanc sur fond blanc, y compris pour
les capteurs qui codaient en dur `textOnInactive*`.

---

## 7. Mapping des états Home Assistant

`EntityPresentation` traduit `(domaine, état, attributs)` en présentation :

| Domaine | Actif si | Statut affiché | Catégorie |
| --- | --- | --- | --- |
| `light` | `on` | luminosité en % | lumière |
| `switch`, `input_boolean`, `automation`, `fan` | `on` | on/off | interrupteur |
| `cover` | `open`/`opening` | position en % | volet |
| `lock` | `locked` | verrouillé / déverrouillé | sécurité |
| `climate` | mode ≠ `off` | mode + température courante | chauffage/froid |
| `media_player` | `playing`/`paused` | titre ou source | média |
| `sensor` | — | valeur + unité | capteur |
| `binary_sensor` | `on` | ouvert/fermé/détecté selon `device_class` | capteur |
| `vacuum` | `cleaning` | état traduit | générique |
| `scene`, `script` | — | action ponctuelle | générique |

Les valeurs numériques sont formatées avec l'unité de l'attribut `unit_of_measurement` ; les
états inconnus retombent sur une présentation générique plutôt que de faire disparaître la
tuile.

### Commandes (`TileCommands`)

| Action | Service |
| --- | --- |
| bascule | `homeassistant.toggle` (light, switch, fan, input_boolean, automation, media_player) |
| volet | `cover.open_cover` / `cover.close_cover` |
| serrure | `lock.lock` / `lock.unlock` |
| scène / script | `scene.turn_on` / `script.turn_on` |
| aspirateur | `vacuum.start` / `vacuum.return_to_base` |
| luminosité | `light.turn_on` avec `brightness_pct` ; 0 % → `light.turn_off` |
| couleur | `light.turn_on` avec `rgb_color` |
| température de blanc | `light.turn_on` avec `color_temp_kelvin` (1000–10000 K) |
| position de volet | `cover.set_cover_position` |
| volume | `media_player.volume_set` (0…1) |
| lecture | `media_player.media_play_pause` / `media_next_track` / `media_previous_track` |

Les glissières sont **throttlées à 150 ms** (`LEVEL_THROTTLE_MILLIS`) par entité : la
dernière valeur est mémorisée et envoyée en fin de geste, ce qui évite d'inonder le réseau
sans sacrifier la fluidité. Un échec de commande remonte `CommandError` et affiche un toast
discret ; l'état réel reste celui du WebSocket.

---

## 8. Connexion à Home Assistant

### 8.1 Découverte

- `HaDiscovery` : mDNS `_home-assistant._tcp` (6 s), verrou multicast
  (`CHANGE_WIFI_MULTICAST_STATE`), repli sur les adresses usuelles.
- `AddonDiscovery` : pour chaque instance repérée, test de `GET /api/ping` sur le port
  **8765** ; la réponse `{"app": "homeglass"}` identifie l'add-on. Saisie manuelle possible.
- `HaHttpClient.isHomeAssistantReachable` teste `GET /api/` pour valider une URL.

### 8.2 Transports

| Transport | Config | Appels de service | WebSocket | Auth |
| --- | --- | --- | --- | --- |
| `Direct` | `/api/config` | `POST /api/services/{domain}/{service}` | `/api/websocket` | jeton HA |
| `Addon` | `/api/ha/config` | `POST /api/ha/services/{domain}/{service}` | `/api/ha/ws` | jeton d'appareil |

Le transport est stocké dans la session ; l'application ne mélange jamais les deux. En
`Addon`, le jeton Home Assistant n'existe pas côté app : l'add-on s'authentifie avec son
`SUPERVISOR_TOKEN`.

### 8.3 URL interne / externe

Une instance peut porter deux adresses. Sur Wi-Fi ou Ethernet, l'URL interne est testée puis
privilégiée ; sinon l'externe est utilisée. `NetworkMonitor` expose les changements et
`HaController.ensureBaseUrl()` réévalue le choix, puis redémarre le temps réel. Sur un
changement de réseau, la reconnexion est immédiate (le backoff est réinitialisé).

### 8.4 Temps réel

`HaRealtime` gère la connexion WebSocket : `auth_required` → `auth` → `auth_ok`, puis
abonnement `subscribe_events` (`state_changed`). Les abonnements sont rejoués à chaque
reconnexion. Le statut est exposé en `Flow` (`Idle`, `Connecting`, `Connected`, `Failed`)
avec la cause (`Unauthorized`, `Network`, `Protocol`, `Server`). Un `Unauthorized` sans
rafraîchissement possible provoque l'expiration de la session et le retour à l'appairage,
plutôt qu'un écran bloqué.

Backoff : 1 s → 30 s, facteur 2, jitter de 20 %, remise à zéro après une connexion réussie.

### 8.5 Session et stockage

| Donnée | Emplacement | Chiffrement |
| --- | --- | --- |
| Instance, URL interne/externe, transport, jeton | `EncryptedSharedPreferences` (`SessionStore`) | AES-256, clé dans Android Keystore |
| Cache de layout | DataStore `homeglass_layout`, clé `layout_json` | non sensible |
| Tableau personnel | DataStore `homeglass_dashboard`, clés `dashboard` / `entity_ids` (héritage) | non sensible |
| Réglages et journal de présence | DataStore `homeglass_presence` | non sensible |

`allowBackup="false"` : les jetons ne partent ni dans une sauvegarde cloud ni dans un
transfert d'appareil.

### 8.6 Sécurité réseau

- `CleartextPolicy` n'autorise le HTTP que vers 10/8, 172.16/12, 192.168/16, 169.254/16,
  127/8, `localhost`, `*.local` et les IPv6 locales.
- `HttpClientFactory` applique la même règle à l'exécution : une redirection ou une URL
  publique en clair est refusée, même si elle vient d'un document.
- `network_security_config.xml` laisse le clair autorisé au niveau plateforme
  (`cleartextTrafficPermitted="true"`) : Android n'accepte pas de CIDR ni de liste d'hôtes
  dynamiques. Le filtrage est donc fait **dans l'application** (même règle à la saisie et
  avant chaque appel) ; HTTPS garde les autorités de certification du système.
- OAuth : PKCE `S256`, `state` aléatoire, Custom Tab, capture du retour par deep link
  `homeglass://auth-callback` (intent-filter dans `MainActivity`).
- Appairage : le code à 6 chiffres n'est jamais renvoyé à l'application ; il est lu dans
  Home Assistant. Le jeton d'appareil est révocable et seule son empreinte SHA-256 est
  stockée côté serveur.

---

## 9. Réveil de présence

### 9.1 Service

`PresenceService` est un service au premier plan (`foregroundServiceType="camera"`),
démarré au lancement de l'application si le réglage est actif (`LaunchedEffect` dans
`MainActivity`) et à l'activation depuis les Réglages ; il s'arrête de lui-même quand le
réglage est coupé. Une notification permanente explique la surveillance ; la permission
`POST_NOTIFICATIONS` est demandée sur Android 13+.

### 9.2 Deux sources

| Source | Détection | Réglage |
| --- | --- | --- |
| **Proximité** (`TYPE_PROXIMITY`, variante « wake-up ») | valeur ≤ seuil, rafraîchie à `SENSOR_DELAY_FASTEST` | seuil 20–200 cm |
| **Caméra** (frontale) | visage détecté, une frame toutes les **400 ms**, uniquement écran éteint | aucune |

La proximité est enregistrée avec `getDefaultSensor(type, true)` (variante wake-up) et
`SensorManager.registerListener(…, SENSOR_DELAY_FASTEST)`. Le seuil effectif est
`min(maxRange, thresholdCm)` lorsque `maxRange > 0`.

### 9.3 Anti-rebond et réveil

- `PresenceTuning` : seuil **80 cm** par défaut (20–200), anti-rebond **3 s** par défaut
  (1–15 s).
- `PresencePlanner` (pur, testé) décide si un événement doit déclencher : source autorisée,
  valeur sous le seuil, dernier déclenchement plus ancien que l'anti-rebond.
- Réveil : `WakeLock` `SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP | ON_AFTER_RELEASE`
  avec un timeout de **10 s**, puis intent `EXTRA_WAKE_UP` traité par `MainActivity`
  (`setShowWhenLocked` / `setTurnScreenOn`, API 27+).

### 9.4 Journal

Les **20** derniers événements sont conservés dans `PresenceStore` (DataStore), les **6**
derniers affichés dans les Réglages avec heure, source et valeur ; un bouton « Vider »
efface l'historique. Les images ne sont **jamais** enregistrées ni transmises.

### 9.5 Matériel réel et limites

Le capteur de la tablette de test (`tmf8801`) est **binaire** : 0 quand quelque chose est
proche, 260 sinon. Conséquences assumées :

- la distance réglée n'a pas d'effet fin sur ce matériel ; le seuil de 80 cm suffit à
  déclencher, et l'anti-rebond évite les rafales ;
- l'interface affiche une aide (« capteur binaire ») quand `maxRange ≤ 10 cm`, condition qui
  ne se présente pas sur ce capteur (maxRange 260) : l'aide reste donc invisible ici, même
  si le comportement est bien binaire ;
- **le détecteur de visage n'est pas encore instancié** dans `PresenceService` : la caméra
  est bien ouverte une frame toutes les 400 ms, mais `detector` reste `null` et aucun réveil
  n'est déclenché par ce chemin. En pratique, le réveil repose sur le capteur de proximité.
  C'est la première chose à corriger pour activer le mode caméra.

---



---

## 10. Tableau de bord personnel

`CustomDashboard` est un document local (pas un layout serveur) : une **grille libre** de
`PlacedTile`. Le modèle vit dans `domain/dashboard/` et ne dépend ni d'Android ni du réseau.

| Champ | Rôle |
| --- | --- |
| `id` | identifiant stable de la case |
| `entityId` | entité Home Assistant pilotée (exclusif avec `widget`) |
| `widget` | case décorative locale (`WidgetItem`) |
| `col`, `row` | position sur la grille, en cellules |
| `spanX`, `spanY` | portée en cellules, au moins 1 |
| `label` | nom affiché personnalisé (`null` : nom Home Assistant) |

`normalized()` assainit le document à la lecture comme à l'écriture : géométrie ramenée à
au moins une cellule, identifiants dédoublonnés, libellés vides retirés, cases sans entité
ni widget ignorées, et cases `Title` supprimées. L'ancien format (zones `sections` et
`widgets`) est migré une seule fois vers des cases auto-placées par
`DashboardLayout.autoPlace`. L'ancien format `entity_ids` (simple liste) est également
migré.

Persistance : DataStore `homeglass_dashboard`, clé `dashboard` (JSON). Un document
illisible retombe sur « aucun tableau personnalisé » et la page générée s'affiche, sans
casser l'écran. Un tableau vide est traité comme « non configuré ».

### 10.1 Rendu

`withCustomLayout()` remplace les sections d'entités par une section `freeform` unique ;
les scènes restent affichées dessous. Le nom affiché vient de `label` quand il existe,
sinon du nom Home Assistant. Une case dont l'entité a disparu du layout courant est
ignorée : l'accueil continue de s'afficher après une modification côté Home Assistant.

### 10.2 Éditeur (`ui/custom/CustomDashboardScreen`)

- **Canevas** : chaque case est un aperçu réel de la tuile, remesurée à chaque changement
  de portée (le contenu se réorganise, ce n'est pas un zoom). Une surcouche de gestes capte
  le glissement et le redimensionnement.
- **Déplacement** : glisser la case ; les positions sont animées et `DashboardLayout.settle`
  recale les cases en collision.
- **Redimensionnement** : poignée ronde en bas à droite, jusqu'à `columns` de large et
  autant de rangées que voulu.
- **Palette** : chips Horloge / Photo / Calendrier, champ de recherche, et **liste des
  appareils toujours visible** (triée par nom) ; la recherche filtre cette liste.
- **Renommage** : le nom saisi n'est mémorisé comme `label` que s'il diffère réellement du
  nom Home Assistant (`storedLabel`). La comparaison utilise les noms d'origine, pas la
  liste déjà renommée — sinon l'étiquette était effacée à chaque enregistrement.
- **Stabilité** : l'éditeur reçoit un instantané de la disposition pris à l'ouverture
  (`editorSeed` dans `AppRoot`) et ne réindexe plus son état sur les listes vivantes. Les
  relevés d'état Home Assistant ne réinitialisent donc plus le canevas en cours de
  modification, et un enregistrement ne peut plus écraser le tableau par la page générée.
- **Thème** : l'éditeur suit le thème clair/sombre de l'application (plus de mode sombre
  forcé) ; le canevas reprend le dégradé de fond.
- **Actions** : « Enregistrer » sauvegarde la grille ; « Tableau généré » efface le tableau
  personnel et rend la main à la génération automatique.
- **Édition stable** : la liaison temps réel est coupée pendant l'édition
  (`HaController.setLiveUpdatesPaused`) et le brouillon (positions, renommages) est
  sauvegardé par `rememberSaveable`, donc une recréation d'activité ne referme pas
  l'éditeur et ne perd pas les modifications. Seule l'interface vinyle maintient l'écran
  allumé (`KeepScreenOn`) ; l'éditeur et le Web Player suivent le délai de veille.
- **Appuis cloisonnés** : l'éditeur et le Web Player consomment les touches de leurs zones
  vides (`Modifier.consumeTouches()`) ; un appui ne traverse plus vers les cases du tableau
  en dessous (une lumière ne peut plus s'éteindre par accident).

### 10.3 Diagnostic local

- **Journal des commandes** : `CommandLogStore` (DataStore `homeglass_commands`) conserve
  les 50 dernières commandes envoyées (heure, service, entité, succès/échec). Les commandes
  identiques à moins d'une seconde sont fusionnées pour ne pas noyer un glissement de
  curseur. `HaController.recordCommand` écrit aussi une ligne Logcat (tag
  `HomeGlassCommands`). Réglages affiche les six dernières et permet de vider le journal.
- **Tuile hors ligne** : le texte devient « Hors ligne depuis 14:32 » (ou depuis le jour
  concerné), avec une icône de rafraîchissement. Un appui relance la liaison
  (`HaController.retryConnection`) sans envoyer de service ; le reste de la tuile est
  désactivé. Cela distingue une panne de l'appli d'une entité qui a décroché côté Home
  Assistant.

---

## 11. Lecteur plein écran

- S'ouvre automatiquement quand une lecture **démarre** (l'entité passe à `playing` alors
  qu'elle était `off`/`idle`/absente) ; au lancement de l'app, une référence est prise sans
  ouvrir l'écran, même si de la musique tourne déjà. Reprendre une pause ou changer de
  titre ne le rouvre pas si l'utilisateur l'a fermé.
- Reste affiché tant que l'état est `playing`, `paused` ou `buffering`, puis se ferme.
- Contenu : pochette (attribut `entity_picture`), titre, artiste, album, barre de
  progression, lecture/pause, piste précédente/suivante, volume ; plusieurs départs
  simultanés → priorité à l'enceinte qui a un titre identifié.
- Plein écran paysage, `keepScreenOn`, retour par bouton ou geste.

### 11.1 Spotify (Web Player intégré)

L'intégration est un **navigateur intégré** : aucune API Spotify, aucun OAuth, aucun Client
ID. La case Spotify et le bouton du lecteur vinyle ouvrent `open.spotify.com` dans une
fenêtre plein écran, avec une barre « Spotify Web Player ». Le retour ferme la fenêtre et
revient au tableau de bord HomeGlass (il ne remonte pas l'historique Spotify).

```text
ui/web/SpotifyWebScreen.kt   GeckoView, barre, session, erreur et reprise
ui/tiles/SpotifyTile.kt      case logo (WidgetKind.Spotify, locale)
res/drawable/ic_spotify.xml  logo vectoriel
```

**GeckoView** — la page est rendue par le **moteur Firefox embarqué** (`geckoview-arm64-v8a`,
~90 Mo, dépôt `maven.mozilla.org`). C'est indispensable : la WebView d'Android n'a pas de
chaîne DRM/codecs exploitable par Spotify (« la lecture de contenus protégés est
désactivée »). GeckoView lit le Web Player comme Firefox. Le `PermissionDelegate` accorde
`PERMISSION_MEDIA_KEY_SYSTEM_ACCESS` (Widevine) et refuse les autres permissions de site ;
les permissions Android sont accordées. Le profil par défaut du moteur conserve les cookies
et la connexion d'une ouverture à l'autre. Le clavier est repoussé par `imePadding` pour ne
pas recouvrir le formulaire.

**Version de bureau** — la session et la vue utilisent le mode bureau, et l'User-Agent est
posé par la préférence `general.useragent.override` (`GeckoPreferenceController`, runtime
avec `aboutConfigEnabled`). La version mobile du Web Player remplace « Bibliothèque » par
une invite « Installez l'appli » ; le mode bureau donne la bibliothèque complète. L'override
d'User-Agent de session est proscrit : il ajoute un en-tête `User-Agent` aux préflights CORS
que Spotify refuse (bug GeckoView 1629921), ce qui casse pochettes et lecture.

**Lecture** — aucune piste ne démarre sans geste dans la page. Le son sort de la tablette
ou d'une enceinte choisie dans Connect, comme dans Firefox ; ce n'est plus une télécommande
Spotify Connect native.

**Case Spotify** — logo et « Ouvrir le Web Player », deux colonnes par défaut ; entièrement
locale (`WidgetKind.Spotify`), sans entité Home Assistant. Le lecteur vinyle ajoute un bouton
Spotify à côté de la fermeture, et Réglages un bouton « Spotify ».

---

## 12. Accessibilité et localisation

- Tous les textes visibles viennent de `strings.xml` (aucune chaîne en dur dans les écrans).
- Icônes décoratives : `contentDescription = null` ; tuiles et boutons : description
  explicite (`"Lampe cuisine, allumée"`).
- `mergeDescendants = true` sur les tuiles pour que TalkBack énonce la tuile d'un bloc.
- Cibles tactiles ≥ 48 dp, contraste texte/fond garanti par la règle de luminance.
- Animations supprimées si le système le demande.
- Polices en `sp`, dimensions en `dp`, densité réglable de 0,85 à 1,30.

---

## 13. Contrat de mise en page v1

Le document décrit une interface complète : thème, onglets, accueil, pièces, profil
d'appareil. Il est défini par des modèles Pydantic côté add-on (JSON Schema généré dans
`schema/layout.v1.json` du dépôt [HomeGlass-Addon](https://github.com/boubalouf/HomeGlass-Addon))
et par `domain/layout/Layout.kt` côté application.

Sur le fil, les clés sont en **camelCase** (les modèles Python sont en snake_case avec un
`alias_generator = to_camel`). Tous les champs optionnels ont un défaut explicite et l'API
renvoie la totalité des champs : l'application ne dépend jamais d'un champ absent.

```json
{
  "schemaVersion": 1,
  "revision": 12,
  "publishedAt": "2026-02-11T18:00:00Z",
  "theme": {
    "mode": "auto",
    "accent": "#FF9500",
    "tileRadiusDp": 22,
    "density": "standard",
    "background": { "type": "gradient", "assetId": null, "blurDp": 0.0, "dim": 0.0 }
  },
  "tabs": [
    { "id": "home", "type": "home", "label": "Maison", "icon": "home", "enabled": true },
    { "id": "rooms", "type": "rooms", "label": "Pièces", "icon": "meeting_room", "enabled": true },
    { "id": "settings", "type": "settings", "label": "Réglages", "icon": "settings", "enabled": true }
  ],
  "home": {
    "title": "Maison",
    "summaryChips": ["lights", "climate", "security", "media"],
    "sections": [
      {
        "id": "sec_lights",
        "kind": "tiles",
        "title": "Lumières",
        "items": [
          {
            "id": "tile_salon",
            "entityId": "light.salon",
            "name": "Salon",
            "icon": "lightbulb",
            "accent": "#FFB800",
            "size": "small",
            "display": "auto",
            "hidden": false,
            "tap": { "type": "toggle" },
            "longPress": { "type": "more_info" }
          }
        ]
      },
      {
        "id": "sec_scenes",
        "kind": "scenes",
        "title": "Scènes",
        "items": [
          {
            "id": "scene_soir",
            "name": "Soir",
            "icon": "nightlight",
            "gradient": ["#FF9500", "#FF375F"],
            "actions": [
              { "type": "call_service", "service": "scene.turn_on",
                "target": { "entity_id": "scene.soir" } }
            ]
          }
        ]
      }
    ]
  },
  "rooms": [
    {
      "id": "room_salon",
      "name": "Salon",
      "icon": "weekend",
      "areaId": "salon",
      "sections": [
        { "id": "sec_room_salon", "kind": "tiles", "title": "Salon", "items": [] }
      ]
    }
  ]
}
```

| Élément | Champs (camelCase sur le fil) | Notes |
| --- | --- | --- |
| racine | `schemaVersion`, `revision`, `publishedAt`, `theme`, `tabs`, `home`, `rooms` | `publishedAt` en ISO 8601 UTC (`…Z`), `revision` croissante |
| `theme` | `mode` (`auto`/`light`/`dark`), `accent` (`#RRGGBB` ou `#AARRGGBB`), `tileRadiusDp` (0–60), `density` (`compact`/`standard`/`comfortable`), `background` | `background` : `type` (`gradient`/`solid`/`image`), `assetId`, `blurDp`, `dim` |
| `tab` | `id`, `type` (`home`/`rooms`/`automations`/`settings`), `label` (1–40), `icon`, `enabled` | `automations` retiré par l'application |
| `section` | `id`, `kind` (`tiles`/`scenes`), `title` (1–120), `items` | union discriminée sur `kind`, donc items typés |
| `tile` | `id`, `entityId`, `name`, `icon`, `accent?`, `size` (`small`/`wide`/`large`), `display` (`auto`/`button`/`slider`/`value`/`graph`), `hidden`, `tap`, `longPress` | `icon` = nom Material Symbols en snake_case |
| `scene` | `id`, `name`, `icon`, `gradient` (exactement 2 couleurs), `actions` | **pas** d'`entityId` : la scène agit par ses actions |
| `action` | `type` (`toggle`/`more_info`/`call_service`/`navigate`/`none`), `service`, `data`, `target`, `roomId` | `service` obligatoire pour `call_service`, `roomId` pour `navigate` |
| `room` | `id`, `name`, `icon`, `areaId?`, `sections` | `areaId` relie la pièce à Home Assistant |

`GET /api/layout` renvoie le document **résolu pour l'appareil** : les surcharges de profil
(`profile`) sont appliquées à la volée, sans modifier la version publiée. L'application
ignore ce champ supplémentaire.

Règles de validation côté add-on : identifiants en minuscules (`StableId`, max 64),
`entityId` au format `domaine.objet`, couleurs hexadécimales, icônes `snake_case`,
`extra="forbid"` (un champ inconnu est refusé en `422`). Une évolution additive garde
`schemaVersion: 1`, une évolution cassante passerait en v2.

### Tolérance du parseur (`LayoutParser`)

- `ignoreUnknownKeys = true` : un champ ajouté par un serveur plus récent ne casse rien.
- `coerceInputValues = true` : `null` sur un champ non nullable et valeur d'énumération
  inconnue retombent sur le défaut (taille inconnue → `small`, mode inconnu → `auto`).
- **version future** : `schemaVersion > 1` produit `TooNew`, mappé sur `Stale(UpdateApp)` si
  un cache exploitable existe, sinon `Error(UpdateApp)` — le cache n'est jamais écrasé par un
  document qu'on ne sait pas lire.
- **onglet obsolète** : `dropObsoleteTabs()` retire uniquement `type: "automations"` avant
  décodage (sinon l'énumération retomberait sur Maison et créerait un onglet fantôme) ;
  l'onglet sélectionné retombe sur le premier onglet valide.
- **items** : chaque item de section est décodé individuellement — un item illisible est
  ignoré, le reste de la section s'affiche.
- **erreurs** : un JSON illisible produit `Invalid`, mappé sur `Stale(CorruptedLayout)` si le
  cache est exploitable, sinon `Error(CorruptedLayout)`.

### Chargement (`LayoutRepository`)

Ordre de résolution : **cache DataStore → source distante si elle est branchée → source
locale**. Le cache (JSON brut, clé `layout_json`) est parsé en premier et affiché
immédiatement, puis remplacé par le document frais ; il n'est réécrit qu'après un parsing
réussi.

`LayoutState` : `Loading`, `Ready(layout)`, `Stale(layout, issue)`, `Error(issue)`, avec
`issue` ∈ `Cached`, `Offline`, `NoData`, `UpdateApp`, `CorruptedLayout`. Un état `Stale`
garde l'écran utile pendant un rafraîchissement ou une panne.

> État actuel : `HaController` construit le dépôt avec `GeneratedLayoutSource` comme source
> **locale** et **aucune source distante** ; la page est donc générée depuis les registres Home
> Assistant et mise en cache. `AssetLayoutSource` (`assets/layout_sample.json`) sert au
développement sans Home Assistant. Le contrat v1 et son parseur sont prêts, mais
> l'application ne consomme pas encore `/api/layout` de l'add-on (branchement prévu à l'étape
> suivante).

### Génération locale (`LayoutGenerator`)

Utilisée quand aucun document n'existe :

1. filtre les domaines exclus et les entités `hidden` / invisibles du registre ;
2. trie par priorité de famille puis par nom ;
3. place les `scene`/`script` dans une section « Scènes » ;
4. construit une section par famille (lumières, climat, sécurité, volets, médias, caméras,
   interrupteurs, capteurs, nettoyage, autres — libellés tirés de `strings.xml`) dans la
   limite de **12 tuiles** pour l'accueil (`homeTileLimit`) ;
5. crée une pièce par aire du registre, triée par nom, avec ses entités ;
6. produit trois onglets fixes : Maison, Pièces, Réglages.

Le document généré est sérialisé au format v1 (camelCase) et repasse par le même parseur et
les mêmes mappers que ceux prévus pour l'add-on ; `revision` vaut l'horodatage courant en
secondes.

Le layout généré n'est publié nulle part : il alimente l'affichage et le cache.

---

## 14. Add-on HomeGlass Server

### 14.1 Options

| Option | Défaut | Rôle |
| --- | --- | --- |
| `log_level` | `info` | verbosité |
| `api_port` | `8765` | port de l'API utilisée par l'application |
| `allow_remote_access` | `false` | autorise l'accès à l'API hors du réseau local (à laisser désactivé) |

`ingress_port: 8099`, `ingress_entry: /`, `panel_icon: mdi:view-dashboard`,
`panel_admin: false`, `arch` : `amd64`, `aarch64`, `armv7`, `startup: application`,
`boot: auto`, `init: false`, `homeassistant_api: true`, `host_network: false`, montage
`addon_config:rw`.

### 14.2 Deux surfaces

- **Ingress** (session Home Assistant) : panneau web de l'add-on — appairage, aperçu,
  éditeur (page d'accueil pour l'instant).
- **API** (port 8765) : utilisée par l'application Android. Authentification par jeton
  d'appareil, sauf pour les routes publiques.

### 14.3 API de l'application

| Méthode | Route | Auth | Rôle |
| --- | --- | --- | --- |
| `GET` | `/api/ping` | publique | `{"app": "homeglass", "version": …}` — découverte, aucune donnée sensible |
| `POST` | `/api/pair/request` | publique | crée une demande ; le code s'affiche dans Home Assistant, jamais dans la réponse |
| `POST` | `/api/pair/claim` | publique | valide le code, renvoie le jeton d'appareil **une seule fois** |
| `POST` | `/api/devices/register` | jeton | enregistre l'appareil, renvoie son profil et la révision courante ; déclenche la génération si rien n'est publié |
| `GET` | `/api/layout` | jeton | layout résolu pour l'appareil (`?device_id=`), `ETag` + `304`, `Vary: Authorization` |
| `GET` | `/api/assets/{asset_id}` | jeton | image du thème ou d'une tuile |
| `GET` | `/api/ha/config` | jeton | configuration Core relayée |
| `POST` | `/api/ha/services/{domain}/{service}` | jeton | appel de service relayé vers Core |
| `WS` | `/api/ha/ws` | jeton | passerelle temps réel vers Core |
| `WS` | `/api/ws` | jeton | notifications `layout_updated` à chaque publication (ping applicatif toutes les 30 s) |

Appairage : code à **6 chiffres**, durée de vie **600 s**, **5 tentatives** maximum, un seul
appareil par demande. Le jeton n'est renvoyé qu'une fois ; le serveur ne garde que son
empreinte SHA-256, avec nom d'appareil, version d'application et date. Révocation immédiate
depuis le panneau.

Toutes les routes `/api/*` sauf `ping` et `pair/*` exigent un jeton Bearer. Limitation de
débit : **120 requêtes/minute** par jeton ; au-delà, `429` avec `Retry-After`. Les erreurs
sont des JSON `{"detail": "..."}`. Le port Ingress n'est pas limité (déjà protégé par la
session Home Assistant).

### 14.4 Passerelle WebSocket

`/api/ha/ws` ouvre une connexion vers Core, s'authentifie avec le jeton superviseur,
rejoue les messages de l'application et renvoie les événements. Les abonnements sont suivis
par identifiant pour permettre la reprise ; les erreurs Core sont traduites en messages
`result` avec `success: false`.

### 14.5 API de l'éditeur (Ingress)

| Méthode | Route | Rôle |
| --- | --- | --- |
| `GET` | `/internal/status` | état complet (layout, appareils, statut) |
| `GET` | `/internal/entities` | entités connues du cache |
| `GET` | `/internal/areas` | pièces connues du cache |
| `POST` | `/internal/layout/autogenerate` | régénère une mise en page depuis les registres |
| `GET` | `/internal/layout/draft` | brouillon en cours d'édition |
| `PUT` | `/internal/layout/draft` | valide et enregistre le brouillon |
| `GET` | `/internal/layout/published` | version publiée |
| `POST` | `/internal/layout/publish` | publie le brouillon (révision + horodatage) |
| `GET` | `/internal/layout/history` | 20 dernières versions publiées |
| `GET` | `/internal/layout/versions/{revision}` | contenu d'une version |
| `POST` | `/internal/layout/restore/{revision}` | restaure une version (nouvelle révision) |
| `GET` | `/internal/pairing` | demandes en cours et appareils appairés |
| `DELETE` | `/internal/pairing/pending/{id}` | annule une demande |
| `DELETE` | `/internal/pairing/devices/{id}` | révoque un appareil |
| `GET` | `/internal/devices` | appareils connus et profil par défaut |
| `PUT` | `/internal/devices/{id}/profile` | profil d'un appareil (densité, futur : onglets) |
| `POST` | `/internal/assets` | envoi d'une image (10 Mio max) |
| `GET` | `/internal/assets` | ressources stockées |

Toute écriture passe par les modèles Pydantic : un document invalide est refusé en `422`
avec le détail des champs, jamais écrit sur disque.

### 14.6 Stockage

Répertoire `/data` (persistant entre redémarrages) :

```text
/data/
  layout/draft.json             brouillon de l'éditeur
  layout/published.json         version servie à l'application
  layout/history/000042.json    20 dernières versions publiées
  devices.json                  appareils appairés (empreintes, jamais les jetons)
  pairing.json                  demandes en cours (expirées au démarrage)
  assets/index.json + fichiers  images importées
```

Aucune donnée n'est écrite hors de `/data`. Une écriture interrompue ne peut pas corrompre
le layout publié grâce au renommage atomique.

### 14.7 Sécurité de l'add-on

- Routes `/api/*` (hors `ping` et `pair/*`) : jeton obligatoire, comparaison à temps
  constant sur l'empreinte.
- `allow_remote_access` désactivé par défaut ; l'API écoute sur le réseau local.
- Le jeton superviseur n'est jamais exposé à l'application.
- Aucun `eval`, aucun chemin construit depuis une entrée utilisateur ; les assets sont
  contrôlés par type MIME et taille.
- Journalisation sans secret (jetons tronqués).

### 14.8 Tests

Les tests Python de l'add-on (**103 fonctions** `test_`, **171 cas** avec les
paramétrages : contrat, routes, appairage, passerelle, stockage, sécurité) se trouvent dans
le dépôt [HomeGlass-Addon](https://github.com/boubalouf/HomeGlass-Addon), avec le schéma
généré depuis les modèles et comparé en test. Exécution : dans ce dépôt,
`cd tests && pytest` (voir son README).

---

## 15. Flux de bout en bout

**Appairage**

1. L'app teste `GET /api/ping` sur l'hôte HA, port 8765.
2. `POST /api/pair/request` → `pairingId`, `deviceId`, `expiresIn`, `codeLength` (6) ; le
   code lui-même n'est pas dans la réponse.
3. L'utilisateur lit le code dans le panneau Home Assistant.
4. `POST /api/pair/claim` avec `pairingId` + code → jeton d'appareil (une seule fois).
5. Session enregistrée chiffrée, transport `Addon`, `GET /api/ha/config`, ouverture du WS.

**Chargement de l'écran**

1. Cache DataStore parsé immédiatement et affiché en `Stale(Cached)` → écran utile sans
   attente.
2. `HaRealtime` se connecte ; les registres arrivent (`registry` non vide).
3. `LayoutRepository.refresh()` régénère le layout et met le cache à jour.
4. Les états sont fusionnés dans `states` ; chaque tuile se recompose indépendamment.

**Commande**

1. Appui → `HaController.toggle()` ou `setLevel()`.
2. Throttle éventuel (150 ms), construction du `ServiceCall`.
3. `HaHttpClient.callService` (direct ou via l'add-on).
4. Succès : rien à faire, le WebSocket confirme. Échec : `CommandError` → toast.

**Réveil**

1. Capteur sous le seuil → `PresencePlanner` valide (source, seuil, anti-rebond).
2. `WakeLock` 10 s + intent `EXTRA_WAKE_UP` vers `MainActivity`, qui applique
   `setTurnScreenOn` / `setShowWhenLocked`.
3. Événement ajouté au journal (20 max).
4. Le service étant au premier plan, il survit à la destruction de l'activité et peut la
   relancer au passage suivant.

---

## 16. Tests

| Suite | Emplacement | Volume | Commande |
| --- | --- | --- | --- |
| Kotlin (JVM) | `app/src/test/java` | 140 `@Test` | `./gradlew :app:testDebugUnitTest` |

Couverture Kotlin : parsing et tolérance du layout, génération, présentation d'entités,
planification de présence, politique de cleartext, backoff, normalisation et migration du
tableau personnel, grille libre (`DashboardLayout`), étiquettes de renommage, mapping des
commandes, formatage des valeurs.

Les tests Python de l'add-on sont dans le dépôt séparé (voir §14.8).

Build complet : `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Les deux passent
actuellement (140 tests, 0 échec) et l'APK s'installe sur la tablette de test.

---

## 17. Limites connues

1. **Layout de l'add-on non consommé** : l'application génère sa page localement ; le
   contrat v1 et son parseur sont prêts, mais `HaController` ne branche pas de source
   distante. L'éditeur de l'add-on n'a donc pas encore d'effet visible sur la tablette.
2. **Éditeur web incomplet** : l'Ingress sert une page d'accueil ; l'édition visuelle est
   l'étape suivante (les routes `/internal/*` existent déjà).
3. **Découverte mDNS de l'add-on non implémentée** côté add-on (`_homeglass._tcp`) : le
   port 8765 est testé sur les hôtes HA découverts, ou saisi à la main.
4. **Caméra de présence inactive** : `FaceDetector` n'est jamais créé dans
   `PresenceService` (voir §9.5) ; le réveil fonctionne par proximité.
5. **Capteur de proximité binaire** sur le matériel de test : le seuil en centimètres n'a
   pas d'effet progressif, et l'aide « capteur binaire » (condition `maxRange ≤ 10`) ne
   s'affiche pas sur ce capteur.
6. **Pas de changement de thème dans l'app** : le mode vient de `ThemeConfig` (layout ou
   valeur par défaut) ; il n'existe pas d'écran pour basculer clair/sombre/densité à la
   main (les jetons sont prêts pour un futur panneau d'apparence).
7. **OAuth nécessite une page HTTPS à vous** : sans `homeglass.oauthClientId`, seul le
   jeton longue durée est proposé — c'est volontaire, pour ne coder aucune URL en dur.
8. **Une seule langue de contenu** : les textes sont français dans `values/strings.xml`
   (les ressources sont limitées à `fr`/`en`, une traduction anglaise reste à écrire).
9. **Pas d'historique ni de graphiques** : courbes de capteurs, statistiques et énergie ne
   sont pas affichées.
10. **Grandes dispositions réservées au 2 × 2** : la grande case média (pochette +
    commandes) et la grande case capteur (valeur géante + pastille) ne s'activent qu'à
    partir de 2 colonnes × 2 rangées ; en dessous, la tuile garde sa disposition compacte.
11. **Spotify dépend de GeckoView** : la fenêtre charge `open.spotify.com` via le moteur
    Firefox embarqué, indispensable pour le DRM (la WebView système ne lit pas Spotify).
    L'APK ne contient que l'ABI `arm64-v8a` et grossit d'environ 90 Mo ; les mises à jour de
    l'interface Spotify s'appliquent sans mise à jour de l'application.
12. **Spotify joue sur la tablette** : le Web Player n'est pas une télécommande Connect ;
    lancer une lecture fait sortir le son de l'appareil (ou de l'enceinte choisie dans
    Connect), et un compte Premium peut être demandé (voir §11.1).

---

## 18. Dépannage

| Symptôme | Piste |
| --- | --- |
| « Serveur introuvable » | l'add-on tourne-t-il ? `curl http://<ip>:8765/api/ping` doit renvoyer `homeglass` |
| Instance HA non listée | Wi-Fi invité (multicast bloqué) → saisir l'URL à la main |
| Pastille rouge, `Unauthorized` | jeton révoqué → réappairer ; l'app revient d'elle-même à l'écran de connexion |
| Tuiles vides | registres pas encore reçus ; cache de layout conservé, la page se régénère à l'arrivée du registre |
| Le texte d'une tuile est illisible | vérifier qu'un nouveau composant respecte la règle de luminance (§6.3) plutôt que de coder `textOnInactive*` |
| Écran ne s'allume pas | réglage de présence actif ? capteur présent ? consulter le journal (6 dernières entrées) |
| Code d'appairage refusé | 600 s écoulées ou 5 tentatives atteintes → relancer une demande |

Commandes utiles :

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.homeglass.app/.MainActivity
adb logcat -s HomeGlass:V
```

---

## 19. Glossaire

| Terme | Définition |
| --- | --- |
| **Add-on** | module complémentaire Home Assistant (ici `homeglass_server`) |
| **Contrat de layout** | document JSON versionné décrivant l'interface à afficher |
| **Ingress** | proxy Home Assistant qui sert le panneau de l'add-on dans son interface |
| **Instance** | serveur Home Assistant (URL interne/externe + identité) |
| **Profil** | réglages propres à un appareil (densité, futur : onglets) |
| **Tuile** | carte d'appareil de la grille (`small`, `wide`, `large`) |
| **Transport** | chemin d'accès aux données : `Direct` (Core) ou `Addon` (passerelle) |
| **Wake-up sensor** | variante d'un capteur qui continue de délivrer des événements écran éteint |
