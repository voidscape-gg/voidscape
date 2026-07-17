# World Chronicle

`/world` is Voidscape's public, read-only world ledger. It renders current
highscores, permanent first-title claims, saved-world aggregates, supported live
feed events, the economy, Wilderness activity, and derived Almanac facts from
`GET /api/world`.

## Runtime boundary

The endpoint is public in every portal mode, needs no session, and never mutates
game state. Before the launch instant configured by `PORTAL_LAUNCH_AT`, it returns
a deterministic, fully populated fictional Chronicle. This mode does not require
or read `PORTAL_OPENRSC_DB`. The response carries `demo: true`,
`source: "prelaunch-fiction"`, and the exact `launchAt` cutoff so clients cannot
mistake it for player history.

At the launch instant, the fictional response stops immediately and the portal
begins reading the SQLite database configured by `PORTAL_OPENRSC_DB`. Nothing is
deleted from that database: the two response sources are mutually exclusive. A
successful live response is memoized in the portal process for 45 seconds;
concurrent requests share the same in-flight snapshot build. Prelaunch fiction is
generated without that cache, so a cached demo cannot survive the launch boundary.
HTTP responses remain `Cache-Control: no-store`.

The required game tables are `players`, `maxstats`, `experience`, `player_cache`,
`live_feeds`, `quests`, `bank`, `invitems`, and `itemstatuses`. `equipped`, active
and expired auction tables, auction sales, and avatar files are optional. Missing
optional data removes only the related enhancement. After launch, a missing
database, locked or unreadable database, or missing required schema returns `503`.

The browser checks once per minute and also arms a timer for the precise launch
instant. Prelaunch mode has a prominent fictional-data notice, a fictional status
pill, and a footer reminder. At launch, the browser removes the fiction before
requesting the live ledger. After a successful live reading, a temporary API
failure keeps the last verified snapshot on screen and labels it stale. An initial
post-launch production failure renders explicit unavailable and empty states; it
does not substitute sample numbers. `?preview=1` remains a separate labeled local
design preview.

## Eligibility and privacy

Public player rows use these rules:

- normal players only (`group_id = 10`);
- permanent and still-active timed bans are excluded;
- `player_cache.setting_hide_scores` values `1`, `true`, or `on` remove that
  character from highscores and identifying feed entries;
- a hidden permanent first claim remains claimed but its holder is returned as
  `An unseen adventurer`;
- aggregate totals such as circulation and kill counts can still include a hidden
  character's saved deeds and holdings.

Players control the setting in game with `::hidescores on`, `::hidescores off`,
or `::hidescores status`. `::hidescores` without an argument toggles it. The cache
value is persisted with the normal player save, so the page copy says the change
takes effect after the next world save. A prominent callout beneath the Monument
explains the on/off commands and the aggregate-data limitation.

## Response contract

The top-level response is:

```json
{
  "generatedAt": 1784316672,
  "live": true,
  "demo": false,
  "source": "openrsc-sqlite",
  "refreshAfterSeconds": 45,
  "pulse": {},
  "feed": [],
  "highscores": { "hiddenCount": 0, "overall": [], "skills": {} },
  "records": [],
  "economy": { "totalGp": 0, "gpPerHead": 0, "topItems": [] },
  "wilderness": { "killsWeek": 0, "deadliest": {}, "recent": [] },
  "facts": [],
  "availability": { "auctionHistory": false, "avatars": false }
}
```

Before launch, `live` is `false`, `demo` is `true`, `source` is
`prelaunch-fiction`, and `launchAt` is the configured ISO timestamp. The preview
fills all ten overall ranks, ten ranks for every skill, ten records, twelve feed
events, five economy items, eight Wilderness events, and twelve Almanac facts.
Every identity and number in that mode is invented and disappears from the
response at launch.

`pulse` contains `online`, `totalGp`, `accounts`, `npcKills`, `deaths`, and
`questsCompleted`. `deaths` is the sum of eligible players' saved Wilderness
deaths, not every death source.

`highscores.overall` and each of the eighteen classic skill keys return at most ten
rows. RSC experience is stored in quarter-XP fixed point; the API converts signed
32-bit overflow to unsigned before dividing by four. Every row keeps a title prefix
and suffix separate:

```json
{
  "rank": 1,
  "player": "Example",
  "honorific": "Saint",
  "epithet": "the Founder",
  "level": 99,
  "xp": 14000000,
  "avatar": "/openrsc/avatar/1.png?v=..."
}
```

The feed accepts only known server messages and emits structured `max`, `level`,
`quest`, `pk`, and `rare` events. Unknown messages and embedded markup are dropped.
The Wilderness section is derived from supported `has PKed ...` events from the
last seven days.

The ten record stones are backed by existing permanent player-title unlock keys
(`pt_u_*`) and their global first-date keys (`pt_first_date_*`). They are historical
first claims, not mutable richest/deadliest snapshots. The API returns both claimed
and unclaimed stones so launch goals stay visible.

Economy totals deduplicate player/item ownership references, then sum
`itemstatuses.amount` across eligible banks, inventories, equipment, and available
unclaimed market holdings. Item names come from the server definitions. Almanac
facts are prose derived only from the aggregates in the same snapshot.

## Operations and verification

Run locally against a disposable or quiescent world database:

```bash
PORTAL_OPENRSC_DB=/absolute/path/to/world.db scripts/run-portal.sh
curl http://127.0.0.1:8788/api/world
```

SQLite can briefly reject a snapshot while another process holds an exclusive
lock. The endpoint returns `503` and the browser's stale-state behavior handles
that case; do not copy a live WAL database by copying only its main `.db` file.

Regression coverage lives in the normal portal suites:

```bash
scripts/test-portal-api.sh
scripts/test-portal-static-boundary.sh
scripts/build.sh
```

The API smoke covers full fictional prelaunch data with and without a game
database, the automatic post-launch switch, staff/banned exclusion, fixed-point
XP, title placement, hidden boards and victims, anonymous permanent claims, all
eighteen skill boards, and derived facts. The static-boundary smoke proves `/world`,
its JavaScript/CSS, and representative Chronicle artwork survive production
packaging.
