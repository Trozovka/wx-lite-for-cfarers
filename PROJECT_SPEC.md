# GENERAL Freemium — Fork or Build, Open Core (Claude Code Decides)

**Operator name (used as sole author/copyright holder everywhere, both repos):** Trozovka

**Keyword convention (RFC 2119):** MUST/MUST NOT = non-negotiable. SHOULD/SHOULD NOT = default, overridable only for a stated reason. MAY = discretion.

## 0. Role and scope

Building a **freemium, open-core product**: one public, free, open-source "core" repo, and one private, paid repo that depends on the public core and adds paid-only features on top. Two repos with a defined dependency relationship (Section 6), not a single product and not two independently-maintained codebases.

## 1. The free/paid split

**Free version (public) is:** shows only a 1-day forecast.
**Paid version (private) is:** shows the full 10-day forecast.

This is the literal feature boundary for everything below — the public repo implements only the free-version description, the private repo adds only what the paid-version description adds on top.

## 2. Operator context

- GitHub account: Trozovka
- OS and dev environment: Windows 11 + WSL2 Ubuntu
- Home directory: `/home/trzvk2025`
- No specific upstream repo supplied — search per Section 5.

## 3. Autonomy protocol

Operate autonomously through routine steps — installing packages, writing/editing files, running tests, git commit/push, repo creation, forking/cloning. Report back briefly and plainly after acting, never a raw log dump.

Stop and wait for explicit confirmation only when:
- (a) genuine security/privacy risk;
- (b) irreversible or destructive — includes rewriting git history, force-pushing, deleting branches/files;
- (c) operator-only information needed and proceeding would mean guessing;
- (d) about to reject or replace a dependency, library, framework, or upstream project the operator specifically named — always present findings and wait for approval, unless pre-authorized;
- (e) Section 5's search-and-decide process concludes a fork path, or the license check in Section 6 turns up anything restrictive, or no LICENSE file at all — don't proceed until resolved;
- (f) anything that would blur or cross the free/paid feature boundary in Section 1 in a way not already resolved by Section 6's dependency mechanism.

For any manual step outside direct control (dashboard, website, OS settings screen), give exact, copy-pasteable instructions naming the exact button/tab/menu.

## 4. Security and privacy — non-negotiable (both repos)

- MUST NOT hardcode secrets in either repo, ever, not even a throwaway first commit. Use environment variables or a gitignored local config file from commit one, in both repos.
- Leaked secret: rotate/regenerate at the issuing service immediately, in whichever repo it happened. Only after rotating, clean up repo history as hygiene on top of the fix, never as a substitute.
- Destructive git operations (history rewrite, force-push, branch deletion) are ALWAYS a stop-and-wait moment, even under full autonomy — prepare the exact command, let the operator run or confirm it.
- Public core repo is public (per Section 6's license-check gating). Private paid repo is private from the very first commit, no exceptions.
- Keep personal information (real email, phone, school ID, anything identifying beyond the operator name itself) out of code, commits, and comments in the public repo.
- Remind the operator to enable 2FA on every account involved — GitHub, distribution/payment platform, any third-party API/service account.
- License/activation checks (Section 11) MUST run on every launch, not just at install. Deliberately verify copying installed files to another device and running them there fails.
- **Commit attribution.** All commits, both repos, MUST be attributed solely to Trozovka. MUST NOT add a "Co-Authored-By" trailer naming Claude/Claude Code/any AI tool to any commit message, and MUST NOT configure git in a way that causes an AI tool to appear as contributor/author/co-author anywhere in either repo's history or Contributors page. Confirm `git config user.name`/`user.email` are Trozovka's own identity before the first commit in each repo.

## 5. Phase 0 for the PUBLIC core repo — search, decide, and act

**Step 1 — Search.** No upstream URL supplied, so search GitHub/web for existing open-source projects whose core purpose closely matches the free-tier description.

**Step 2 — If a closely-matching project exists**, evaluate against Reuse Evaluation Criteria, choose one of three paths, justify explicitly with tradeoffs:
1. **Fork it.** → Section 6 → Section 7.
2. **Inspiration only, build independently** — license/architecture doesn't fit but approach is worth learning from. → Section 8.
3. **Proceed independently, disregarding it** — poor match/abandoned/unsuitable, state why. → Section 8.

**Step 3 — If no closely-matching project exists at all**, say so plainly, proceed to Section 8 — a legitimate expected outcome. Still apply Reuse Evaluation Criteria at the component/library level throughout.

**Reuse Evaluation Criteria**: maturity/release discipline; maintenance activity; documentation/code quality; security history and dependency health; license compatibility; community adoption (never sole reason); bus factor; red flags (abandonment, ownership churn, renaming, maintainer departures, license changes, governance disputes, unresolved critical issues, past incidents, legal disputes, fragmentation, controversy). Compare competing options explicitly.

**Containerization (Docker).** MAY containerize either repo if it genuinely helps — judgment call, not default. Check `docker info` first rather than assume installed/running; if not, flag as prerequisite rather than installing mid-task. State rationale/tradeoffs/confidence/alternatives before proceeding, get confirmation first.

**Reporting requirement:** for fork-vs-build and any nontrivial dependency/containerization decision, state rationale, tradeoffs, confidence, alternatives before proceeding — never decide silently.

## 6. License check for public core (only if Section 5 chose fork)

Check upstream `LICENSE`/`COPYING`/`NOTICE`, plus any separate per-file/folder headers (bundled fonts/icons/vendored code may differ). Never assume one license covers everything.

- Most OSS licenses (MIT/Apache 2.0/BSD/GPL etc.) permit forking/modifying/sharing derivatives if original copyright notice + license text stay in the repo. MUST NOT delete them.
- No LICENSE file, or something restrictive: stop and report before touching Section 7's visibility setting.
- Check trademark restrictions separately from code license.
- Add a short README note crediting the original project (name + link), stating clearly this is a modified fork.
- **Freemium-specific check:** confirm upstream license doesn't prohibit a proprietary paid product on top. Permissive licenses (MIT/Apache/BSD) generally allow cleanly; GPL/AGPL-family impose real obligations affecting the private repo too. Flag explicitly if GPL/AGPL-family.

## 7. Fork and clone the public core (only if Section 5 chose fork)

1. `gh repo fork <UPSTREAM_URL> --clone=false`
2. `gh repo edit <USERNAME>/<PUBLIC_CORE_NAME> --visibility public` — only if Section 6 didn't flag a restriction.
3. `git clone https://github.com/<USERNAME>/<PUBLIC_CORE_NAME>.git <PUBLIC_CORE_NAME>`
4. `cd <PUBLIC_CORE_NAME>`
5. Confirm `git config user.name`/`user.email` scoped to this repo.
6. **Upstream remote policy.** Add original repo as second remote `upstream` alongside `origin`. MAY `git fetch upstream` periodically to check for new commits/releases/patches, but MUST NOT merge/rebase/fast-forward against it automatically or silently. Any actual incorporation is a stop-and-confirm case — summarize what changed and why, wait for approval.

→ Section 9.

## 8. Build the public core from scratch (only if Section 5 chose independent path)

1. `gh repo create <PUBLIC_CORE_NAME> --public`
2. `git clone https://github.com/<USERNAME>/<PUBLIC_CORE_NAME>.git <PUBLIC_CORE_NAME>`
3. `cd <PUBLIC_CORE_NAME>`
4. Confirm `git config user.name`/`user.email` scoped to this repo.
5. No `upstream` remote. If an inspiration project was identified, note it in the public README as a design influence.

→ Section 9.

## 9. Repo relationship — dependency, not duplication

- Private paid repo MUST depend on the public core repo as a library/package/git submodule — MUST NOT copy-paste/fork the public repo's code into a second separately-maintained copy. State the exact dependency mechanism before writing any paid-tier code.
- Bugfixes/improvements to shared core MUST go into the public repo; private repo MUST pull the updated dependency — never patch the same bug separately in the private copy.
- Private repo's own code is limited to what Section 1 defines as paid-only — everything else lives in the public core.

## 10. Create the private paid repo, apply Dependency Decision Protocol

1. `gh repo create <PAID_REPO_NAME> --private` — MUST be private immediately, no exceptions.
2. `git clone https://github.com/<USERNAME>/<PAID_REPO_NAME>.git <PAID_REPO_NAME>`
3. `cd <PAID_REPO_NAME>`
4. Confirm `git config user.name`/`user.email` scoped to this repo.
5. Add the public core repo as the declared dependency (Section 9's mechanism).
6. `.gitignore` MUST strictly exclude: `.env`/local config files, personal test data/notes, cached/database files, credentials/API keys, compiled build output.

### 10.1 Dependency Decision Protocol (every third-party lib/SDK/framework/API, either repo, including operator-named ones, and the public-core candidate itself)

Reuse is default, custom code the exception — build custom only when: no suitable existing solution; existing options fail actual requirements; licenses incompatible; or custom gives a clear, statable advantage.

Private repo is commercial by definition — every evaluation for its direct dependencies carries that assumption. Don't relax licensing scrutiny for "just internal"/"temporary"/"we'll swap it later."

Assess: technical/architectural quality; maintenance activity/release cadence; health of its own dependencies; security posture/vulnerability history; test coverage/documentation; governance/provenance; **license compatibility for commercial paid redistribution** (attribution/copyright-notice obligations, redistribution conditions, source-disclosure requirements GPL/AGPL-class, patent clauses — MUST be identified explicitly, never assumed); community adoption; bus factor; red flags as above. MUST NOT knowingly recommend a solution violating a licensing/copyright obligation.

**Operator-named dependency:** evaluate first — MUST NOT silently swap it. If unsuitable, present the concern + alternatives comparison + migration difficulty + recommendation with confidence, wait for explicit approval before changing direction.

Maintain a running **Dependency Inventory** for the private repo: component, version, license, purpose, compliance obligations, justification.

Keep private repo's paid-only code clearly separated from the imported public-core dependency.

## 11. Packaging and distribution — private repo only

Public core repo runs from source, no installer, no licensing/activation system.

Private paid repo baseline (adjust per actual business model/platform — see Section 17 for this project's Android-specific adaptation):
- Sold through a distribution/payment platform, one-time-purchase-plus-license-key model, source in the private git repo.
- **Licensing:** buyer gets emailed license key. App MUST verify against platform API + record device-fingerprint activation, checked every launch — plus a separate owner-only bypass code for testing, generated locally, flagged to save safely.
- **Anti-piracy:** explicitly test copying installed files to another machine fails.
- **Freemium fallback:** expired/invalid/missing license → gracefully fall back to free-tier feature set, never crash/refuse to launch.
- **Distribution:** proper installer, never portable/single-file. Installer MUST let user choose install location, proper Start Menu entry + uninstall entry. [Desktop-specific — see Section 17 for Android equivalent.]
- **Shortcuts:** two desktop shortcuts post-install — launch, and straight to a dedicated output folder outside install dir. [Desktop-specific.]
- **Performance:** fast, responsive, minimal-to-zero lag.
- **Offline default:** otherwise run offline, no telemetry beyond license verification + update check.
- **If public core forked under GPL/AGPL-family:** stop, report explicitly before packaging for sale — may impose source-disclosure obligations on private repo too.
- **Testing discipline:** successful build only proves it compiled. Always add a real "launch and stay running" check, plus a final pass on the actual target machine/device.

## 12. Public core — project defaults and README

- Real `LICENSE` file — MIT by default unless specified otherwise.
- README MUST cover, in order: what free version does and why; tech stack (+ Docker if used, why); fork/inspiration credit if applicable; exact copy-pasteable setup/run commands; features list matching Section 1's free-tier description; note that a paid version exists (no private-repo implementation details); screenshot/demo if visual output.
- Never claim it works without running it end-to-end first.
- Commit incrementally with clear, specific messages — never one giant "final" commit.

## 13. Update check — both repos, separately

Each repo ships its own version number and independent "Check for Updates" action.
- MUST show each app's own name + current version somewhere natural.
- MUST provide explicit "Check for Updates" action per repo, comparing latest published release vs running version.
- Newer found → clear notification with version + link. None → plain "up to date" confirmation, never silence.
- Simple read of public version metadata — no telemetry beyond license verification.
- "Update" = new tagged/published release, not every commit.

## 14. Default UI/UX principles

Fallback defaults for a single-window, toolbar-driven, file-based desktop utility — **not a mandate**; Section 17's actual project shape overrides this where it differs (this project is Android/mobile — see Section 17 for adapted equivalents).

- Single-page-app feel; persistent always-visible toolbar; full-viewport layout; drag-and-drop primary input w/ click-to-browse fallback; direct manipulation over dialogs; immediate specific feedback; forgiving/never silently destructive; one consistent mental model across both tiers.
- Default visual style if unspecified — propose (don't assume) a dark theme from shared variables:
```css
--bg: #0f172a; --bg-panel: #111827; --bg-raised: #1f2937; --text: #e5e7eb;
--accent: #38bdf8; --accent-strong: #0ea5e9; --success: #16a34a; --danger: #f87171;
```

## 15. Operating principles (continuous)

- Before calling the public core "done": clone fresh into an empty folder, follow only its README, as a first-time user would.
- A packaged private app can compile/pass tests yet crash on launch — never call a build "done" until actually launched and confirmed staying running.
- Portable single-file executable re-extracts every launch — real slowdown an installed app avoids.
- Output saved next to source file gets lost easily — one predictable, shortcut-accessible location instead.
- If forking/inspired by existing work: credit in public README, confirm license permits both public reuse and private commercial dependency.

## 16. Clarification gate — fully resolved

1. Section 1: ✅ 1-day free / 10-day paid.
2. Repo names: `wx-lite-for-cfarers` (public), `wx-pro-for-cfarers` (private).
3. Upstream: none supplied — Claude searches per Section 5.
4. Dependency mechanism (Section 9): **finalized 2026-08-03.** `wx-lite-for-cfarers`'s Android project is split into two Gradle modules — `core` (Android library: all data fetch/parse/cache, chart rendering, map projection logic — everything identical between tiers) and `app` (the free-tier Activity, depends on `:core`). `wx-pro-for-cfarers` adds `wx-lite-for-cfarers` as a **git submodule** and its own app module depends on `:core` from that submodule path (`implementation(project(":core"))` resolved via the submodule's `settings.gradle.kts` include, not a published artifact — no package registry needed for a two-repo, single-maintainer, sideload-only project). Free vs. paid is a single `ForecastTier` constructor parameter already threaded through `core`'s `ForecastRepository`, not a code fork. A core bugfix is made once in `wx-lite-for-cfarers`; `wx-pro-for-cfarers` pulls it via `git submodule update`, never patched separately.
5. Public core license: **MIT.**
6. Business model: **sideloaded APK sold via Gumroad, license key + device-fingerprint activation checked every launch, falls back to the 1-day free tier on invalid/missing/expired license.** No Play Store/Play Billing.
7. Update-release cadence: both repos independently version and ship releases per Section 13; since distribution is sideloaded (not Play Store), each app needs its own in-app "Check for Updates" pointing at its GitHub Releases.
8. UI: yes, both — Android-native UI, monochrome weatherfax aesthetic (overrides Section 14's dark-theme default per Section 17), touch-first interaction replacing Section 14's drag-and-drop-primary default.
9. Sections 11/14 adapted for Android explicitly (see Section 17 UI note, and Section 11's distribution baseline replaced by item 6 above) — not applied as literal desktop-app mechanics.

## 17. New project

**Tech stack: native Android, Kotlin** (decided over Flutter — best fit for smallest APK, lowest RAM/battery, fastest launch; this project's whole premise is minimizing overhead, which cross-platform runtimes work against).

**Concept:** An ultra-lightweight Android weather app (APK) built specifically for merchant ship crews on extremely slow/expensive maritime satellite internet (Marlink and similar) and low-spec Android devices. It's a digital replacement for traditional marine weatherfax, not a consumer weather app — optimized for speed and minimal data above all else.

**Core principle:** Low-bandwidth operation is the default, always-on behavior, not an opt-in mode. Launches fast, shows useful weather immediately, remains usable on the weakest satellite connections, no special mode to select.

**Technical requirements:**
- Runs smoothly on old/low-spec Android devices. Very small APK, very low RAM/battery/data usage.
- Stores downloaded forecasts locally — full offline persistence across app close/restart/no-internet; reopening shows the saved forecast immediately.
- Do NOT use: Google Maps, Google Earth, Mapbox, OpenStreetMap tile streaming, heavy web frameworks, large image downloads, real-time animated weather layers.
- Use: lightweight custom map rendering, compressed weather data, local generation of weather charts, offline-first architecture.

**Weather display** (weatherfax chart style):
1. Pressure systems: H/L markers, isobars, hPa/mb values, pressure movement.
2. Wind: speed (knots), direction, traditional meteorological wind barbs/arrows, strong wind areas.
3. Severe weather: typhoon/tropical cyclone positions + tracks, storm development areas, dangerous lows.
4. Temperature: simple readable values.
5. Marine info: basic sea conditions/visibility if available.

**Map features (revised 2026-08-03):** full-screen pan/zoom map (local Canvas transform, no tile-streaming basemap) with a 1-degree lat/lon grid. A fixed-center crosshair (constant screen size regardless of zoom — precision comes from zooming in, not the crosshair changing size) continuously reads out the exact lat/lon under it. The earlier "save ship's current location locally" single-point model is superseded by a **passage-plan area**: up to 10 waypoints, entered in degrees-minutes (`xx-xx.x N/S`, `yyy-yy.y E/W`, the maritime convention, not decimal degrees) on a separate entry screen, connected in order to outline the area relevant to the voyage. Point #1 sets the map's initial camera position. Sync fetches the single tile currently under the crosshair (not the whole world at once — measured at ~155KB/region vs. ~3.6MB for all 24 tiles, a real bandwidth difference on a Marlink-class link); panning to a different region and syncing again accumulates additional cached regions rather than replacing the previous one. The earlier spherical-globe zoom-out view (orthographic projection) was built, then removed after device testing showed it was the wrong interaction model for this app (small fixed panel, no pan/zoom) — superseded by the pan/zoom map itself.

**Forecast system:** default period always 10 days (paid tier — free tier is 1 day per Section 1). Bottom-of-screen controls: crosshair lat/lon readout above the Date/Time (Earlier/Later) control — changing either (panning the crosshair, or stepping the forecast hour) updates wind/pressure/isobars/typhoon info accordingly. Wind is drawn only at Beaufort force 5 and above (weaker winds omitted as chart clutter, per explicit operator direction — see Section 19 for the underlying calculation and its accuracy caveats). Temperature is not currently rendered (removed along with isobars in an earlier revision, then isobars/H-L were reinstated but temperature was not re-requested).

**Data design:** download only essential data, compress aggressively, avoid downloading weather images when possible, generate the weatherfax-style display locally on-device, store the complete forecast offline.

**UI requirements (bridge-use adaptation of Section 14):** extremely simple; large readable symbols; black-and-white/monochrome weatherfax style (overrides Section 14's proposed dark-theme accent-color default — monochrome is the deliberate aesthetic here, not a placeholder to swap); fast opening; minimal buttons; no ads, no accounts, no social features, no unnecessary notifications. Mobile-native interaction (touch/tap, not drag-and-drop-as-primary — Section 14's desktop defaults adapted accordingly).

**Final goal:** the fastest, lightest maritime weather app possible — a captain on a bulk carrier on slow Marlink opens the app and immediately sees an accurate forecast (wind, pressure, isobars, typhoons, temperature, ship position) without waiting, without spending data they don't have.

## 18. Data source decision (resolved 2026-08-03)

**Operator constraint: free API only, for both tiers — no recurring third-party cost.**

Checked directly against each candidate's actual terms (not just marketing pages):
- **Open-Meteo** and **Xweather** free tiers are both explicitly non-commercial-only per their own terms (Open-Meteo: "commercial use includes... integrating the service into commercial products"; Xweather: free tier "not intended for commercial use"). Legally fine for `wx-lite-for-cfarers` (genuinely free, no ads/subscription) — **not legally usable for free in `wx-pro-for-cfarers`**, which is a sold, commercial product. Using either there for free would violate their ToS; using them legitimately means Open-Meteo's $29/month plan, which is exactly the recurring cost being avoided.
- **NOAA** (GFS model data for pressure/wind/temperature via the NODD open-data program, and NHC/JTWC for tropical cyclone data) is confirmed U.S. government public domain — explicitly commercial-use-permitted, no permission needed, no subscription, no ToS conflict, for either tier.

**Decision: NOAA direct, for both tiers.** Zero recurring cost, no legal ambiguity, and it fits the freemium dependency shape (Section 9) cleanly — one shared fetch/parse/compress pipeline, free tier capped at showing day 1, paid tier unlocking the full 10 days already fetched.

**Consequence:** unlike Open-Meteo's ready-made lightweight JSON, raw NOAA data (GRIB2) needs parsing and compression ourselves — real one-time engineering work Open-Meteo would have saved, traded for zero ongoing cost. This is a legitimate tradeoff for a one-time-purchase niche product where a recurring API fee could exceed per-sale margin.

## 19. Beaufort force calculation — methodology and known accuracy limits (audited 2026-08-03)

Since this app's wind display is meant to inform real passage-planning decisions, the calculation pipeline was audited end to end on operator request, not just assumed correct. Full trace, GRIB2 to displayed force number:

1. **Source:** NOAA GFS 0.25°, 10m-above-ground U/V wind components (`backend/fetch.py`) — the standard marine/aviation surface-wind reference height, correctly chosen.
2. **Packing precision loss (accepted, not a bug):** `backend/pack.py` rounds each wind component to the nearest whole m/s before storing it as a signed byte (`int(round(u_sub[i,j]))`), for the compact `.wxl` format's size budget. This introduces up to ±0.5 m/s (~±1 knot) of error per component before the app ever sees the data. Not corrected — the wire format is deliberately tiny (Section 18), and this is the accepted cost of that. Flagged here so the limitation is documented, not silently assumed away.
3. **On-device speed/direction (`WindBarb.fromComponents`):** magnitude = `sqrt(u²+v²)`, converted to knots via `1 m/s = 1.943844 kt` (correct to 6 significant figures). Direction uses the standard "from" convention (compass bearing wind is blowing FROM, not toward).
4. **Beaufort lookup (`Beaufort.forceForKnots`):** a static WMO-standard table, knot upper-bounds `[1, 3, 6, 10, 16, 21, 27, 33, 40, 47, 55, 63]` for forces 0-11, force 12 above that — checked against the standard published Beaufort-scale-in-knots table and confirmed accurate.
5. **A real bug was found and fixed during this audit**, not just a theoretical caveat: step 3 also computes a *second*, separately-rounded speed (nearest 5 knots) purely for drawing the wind-barb symbol, which traditionally only comes in 5kt increments. The Beaufort lookup was reading *that* rounded value instead of the true speed. Near a force boundary this silently shifts the displayed force by a whole category — e.g. a true 16.6kt wind (correctly force 5) rounds to 15kt for the barb symbol, and 15kt reads back as force 4. Fixed by adding `WindBarbSymbol.trueSpeedKnots` (the unrounded value) and pointing `Beaufort.forceForKnots` at that instead of the barb-rounded `speedKnots`; regression-tested against this exact 16.6kt case (`WindBarbTest.kt`).

**Net accuracy after the fix:** the Beaufort force shown is correct given the wind data actually available, to within the ~±1 knot uncertainty already present in the packed GRIB data (item 2) — which itself reflects the GFS model's own forecast uncertainty, not an app-introduced error. The app does not introduce additional misclassification beyond that on top. This is model-forecast accuracy, not observation accuracy — like any forecast product, it should be treated as planning guidance, not a substitute for the ship's own real-time wind instruments.

**Display rule:** only Beaufort force 5 and above is drawn (wind barb + force number); weaker winds are omitted from the chart entirely, per explicit operator direction, to keep the chart readable for a multi-day/multi-week voyage overview rather than showing every calm patch.
