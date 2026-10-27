# Kotatsu Lunar — desktop (Windows 10/11) port of Kotatsu-Redo

> **Unofficial port.** Kotatsu Lunar is a desktop application derived from
> [Kotatsu-Redo](https://github.com/Kotatsu-Redo/Kotatsu-Redo) (GPL-3.0). It is not affiliated with,
> endorsed by, or supported by the Kotatsu-Redo maintainers. See [`NOTICE.md`](NOTICE.md).

Kotatsu Lunar runs as a normal Windows program: a resizable window with mouse and keyboard control,
a library of your own CBZ/ZIP archives (or folders of images) **and of online titles**, four reading
modes, offline copies, reading history and progress, and import/export of **Android Kotatsu backups**.

Online content comes from two real sources: **MangaDex** (public API) and **OPDS catalogues**
(Komga, Kavita, self-hosted servers). The `Explore` screen is the Hub: enable/pin sources, check
whether they answer, search, filter, add a title and read it. The other ~900 sources of the Android
app are listed there as unavailable *with the reason* — nothing is faked.

**0.2.1** fixes the MangaDex `HTTP 400` of 0.2.0 (the chapter-feed request used
`contentRating[]=porn`, which the API rejects: the enumerated value is `pornographic`) and the dark
theme, whose controls were painted by the native Look and Feel with their own colours. Details in
[`CHANGELOG.md`](CHANGELOG.md).

* **Prefer português?** Veja a seção [Português (Brasil)](#português-brasil) no fim deste arquivo.

| | |
| --- | --- |
| Platform | Windows 10 / 11, x64 (the jar also runs on Linux/macOS with Java 17+) |
| Language / UI toolkit | Kotlin 2.1 / Swing (JDK 17) |
| Runtime dependencies | none beyond the bundled Java runtime |
| Licence | GPL-3.0 (same as upstream) |
| Version | **0.2.1** — single source of truth: `desktop/…/core/BuildInfo.kt` (`APP_VERSION`), read back by the build scripts, Gradle, the `User-Agent` and CI |
| Changelog | [`CHANGELOG.md`](CHANGELOG.md) |
| Upstream commit | `d45b7c13de260b6c78de78ccbf968daf2187f1cf` |
| Online sources | MangaDex + OPDS implemented and tested offline; the Android parser pack is not bundled ([`docs/SOURCES.md`](docs/SOURCES.md)) |
| Status | reading flow complete for local **and** online content — see [`docs/FEATURE_MATRIX.md`](docs/FEATURE_MATRIX.md) |

---

## 1. Install and run

**Downloads:** [Releases](https://github.com/klua007/kotatsu-lunar-exe/releases) —
`KotatsuLunar-0.2.1-windows-x64-portable.zip`, `KotatsuLunar-0.2.1.msi` and `kotatsu-lunar.jar`
built and smoke-tested on a Windows runner by
[this workflow](https://github.com/klua007/kotatsu-lunar-exe/actions/workflows/build-windows.yml).

### Option A — portable (no installation)

1. Download `KotatsuLunar-<version>-windows-x64-portable.zip` (from the GitHub **Releases** page or
   the **Actions** artifacts).
2. Unzip it anywhere (for example `C:\Users\<you>\Apps\KotatsuLunar`).
3. Run **`KotatsuLunar.exe`**.

The portable package contains its own Java runtime, so nothing else needs to be installed.
Create a file named `portable.marker` next to `KotatsuLunar.exe` if you want the data stored inside
that folder instead of `%APPDATA%`.

### Option B — installer

Run `KotatsuLunar-<version>.msi` (built when the WiX toolset is available in CI). It installs the
application, a Start-menu entry and a shortcut; the data still lives in `%APPDATA%`.

### Option C — runnable jar

```powershell
java -jar kotatsu-lunar.jar        # needs Java 17 or newer
```

### Where your data lives

| What | Path |
| --- | --- |
| Library, history, favourites, settings | `%APPDATA%\KotatsuLunar` (`library.json`, `activity.json`, `settings.json`) |
| Enabled/pinned sources and OPDS catalogues | `%APPDATA%\KotatsuLunar\sources.json` |
| Covers / online page cache (safe to delete) | `%LOCALAPPDATA%\KotatsuLunar\cache` (`covers`, `remote/pages`, `remote/archives`) |
| Offline copies (CBZ) | `%APPDATA%\KotatsuLunar\downloads` |
| Backups | `%APPDATA%\KotatsuLunar\backups` |
| Logs | `%APPDATA%\KotatsuLunar\logs\kotatsu.log` |

Writes are atomic (temp file + rename); a corrupted file is renamed to `*.corrupt-<time>` instead of
being deleted, and the application starts with defaults.

---

## 2. First steps

1. **File → Open CBZ/ZIP…** (or drag files onto the window) and pick archives, folders of images or
   a folder that contains several titles. `Ctrl+O` does the same.
2. Double-click a cover to open the details screen and its chapter list.
3. Double-click a chapter (or press **Continue reading**) to open the reader.
4. Reading position is saved automatically; the title appears under **History** with its percentage.

### Reading something online

1. Open **Explore** (`Ctrl+E`).
2. **MangaDex** is enabled by default; press **Check** to see whether it answers (latency is shown).
   Use **Pin** to keep a source at the top and **Mirror** to point MangaDex at another API base.
3. Type a term and press **Search** (or **Popular now** to browse). Failures are shown per source,
   under the results, instead of hiding the results of the sources that worked.
4. Click a result to add it to the library and open its details; **Refresh chapters** fetches the
   current chapter list without losing read/offline flags.
5. Reading works like local content. Pages are cached as you read; **Download** stores a CBZ under
   `downloads/` so the chapter opens with no network.

For your own server, **Add OPDS catalogue…** accepts the catalogue URL, e.g. Komga's
`http://localhost:25600/opds/v1.2/catalog`. Only CBZ/ZIP chapters are offered (EPUB/PDF are skipped
because this build cannot render them).

Import layouts that are understood (details in [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md)):

```
Manga.cbz                                   -> one title, one chapter
Some folder\ with images                    -> one title, one chapter
Library\Alpha\1.png, Library\Beta\1.png     -> two titles
Series\Chapter 1\1.png, Series\Chapter 2\…  -> one title, several chapters
Folder\One.cbz, Folder\Two.cbz              -> two titles
```

## 3. What works today

* Library grid with search, filters (all / favourites / downloaded) and sorting.
* Details screen with chapter list, favourite toggle, per-chapter offline copy, remove title.
* **Reader**: standard (LTR), right-to-left, vertical and Webtoon modes; fit width/height, zoom
  50–400 %, Ctrl+wheel zoom, drag panning, page-number overlay, table of contents, page save.
* **Keyboard**: arrows / Space / PageUp/PageDown / Home / End, `+`/`-`/`0`, `W`/`H`, `M` (mode),
  `T` (contents), `N`/`P` (chapter), `S` (save page), `F11`, `Esc`. Full list in **Help → Keyboard
  shortcuts**.
* History (continue, remove, clear) and automatic progress saving.
* Offline copies of chapters as CBZ under the application data folder.
* **Backup export/import in the Android Kotatsu format** (favourites, categories, history, bookmarks,
  settings, sources) plus merging rules that never delete local data.
* **Online sources**: MangaDex (search, status filter, adult-content switch, details, chapters,
  pages, downloads, mirror failover) and OPDS catalogues; Hub with enable/disable, pinning,
  availability checks, per-source error messages and a "load more" page step.
* Light/dark theme with **WCAG AA contrast**, an interface scale setting (100–200 %) and a visible
  keyboard focus ring on every control; English and **Brazilian Portuguese** UI.
* `--smoke-test` self check (22 checks) and a **150-case** test suite, including the source layer and
  the MangaDex request contract against a local HTTP server (no internet needed), plus a read-only
  live check script (`scripts/check_mangadex.py`) that CI also runs.

Not implemented yet (and not claimed): the ~900 Android parsers (the Hub explains why for each
listed source), cross-device sync, tracking services, statistics screen, bookmarks UI, WebP/AVIF
pages, incognito mode, app lock. The full, honest breakdown is in
[`docs/FEATURE_MATRIX.md`](docs/FEATURE_MATRIX.md); the source-by-source detail is in
[`docs/SOURCES.md`](docs/SOURCES.md).

## 4. Build it yourself

Every step is reproducible offline; no Maven artifacts are downloaded by the desktop build.

```powershell
# Windows (PowerShell), needs a JDK 17+ and kotlinc
$env:JAVA_HOME   = "C:\Program Files\Eclipse Adoptium\jdk-17..."
$env:KOTLINC_HOME = "C:\tools\kotlin-compiler-2.1.21"
pwsh scripts/build-windows.ps1
```

Outputs: `build\dist\kotatsu-lunar.jar`, `build\dist\KotatsuLunar-*-portable.zip`,
`build\dist\KotatsuLunar-*.msi`, and the app image at `build\package\KotatsuLunar\KotatsuLunar.exe`.

```bash
# Linux/macOS
KOTLINC_HOME=/opt/kotlin JAVA_HOME=/usr/lib/jvm/temurin-17 scripts/build.sh
```

Gradle users can also run `gradle jar`, `gradle desktopTests`, `gradle packageImage`,
`gradle packageMsi` or `gradle packagePortable` from `desktop/`. Details, including how the CI job
works and how to sign the installer, are in [`docs/BUILD.md`](docs/BUILD.md).

The version is **not** typed into the scripts: change `APP_VERSION` in
`desktop/src/main/kotlin/org/koitharu/kotatsu/desktop/core/BuildInfo.kt` and every artefact, the
`User-Agent` and the CI workflow follow. To verify the MangaDex source against the live API without
opening the app (GET only, no account, nothing written to disk):

```bash
python3 scripts/check_mangadex.py            # 11 read-only checks + 3 regression guards
```

## 5. Migrating from Android

1. On the phone: **Settings → Backup → create backup** (a `.zip`).
2. Copy it to the PC.
3. In Kotatsu Lunar: **File → Restore backup…** and choose the file.

What is migrated, what is only preserved, and why downloaded chapters are not included is explained
in [`docs/BACKUP_FORMAT.md`](docs/BACKUP_FORMAT.md). Nothing is deleted on import; a report is shown
with per-section counts.

## 6. Project layout

```
app/, gradle/, metadata/, build.gradle, settings.gradle …   upstream Android project (unmodified)
desktop/                                                    the Windows desktop application
  src/main/kotlin/org/koitharu/kotatsu/desktop/…
  src/test/kotlin/org/koitharu/kotatsu/desktop/test/…       dependency-free test suite
  resources/icon.ico, icon.png, tools/make_icon.py
scripts/                                                    offline build + packaging scripts
docs/                                                       architecture, sources, matrix, guides
.github/workflows/build-windows.yml                         Windows CI (build, test, package, release)
```

## 7. Credits and licence

* Original application: **Kotatsu-Redo** by Koitharu and contributors —
  <https://github.com/Kotatsu-Redo/Kotatsu-Redo>, GPL-3.0.
* Backup format, entity/column names and reading-progress semantics are ported from upstream; the
  files that contain ported code say so in their header.
* The desktop port itself (Swing UI, storage, packaging) was written for this repository.

Everything here is distributed under the **GNU General Public License v3.0** — see [`LICENSE`](LICENSE).
If you redistribute the application you must keep it under the GPL, keep the credits above, and make
the corresponding source available.

The application ships **no content** and no scraping bypass. It opens files that the user already has
on disk and talks to public, documented endpoints (MangaDex API, OPDS catalogues). It does not bypass
DRM, paywalls, logins or Cloudflare challenges, and it does not include copyrighted material.

---

## Português (Brasil)

**Kotatsu Lunar** é uma versão **não oficial** para desktop (Windows 10/11, 64 bits) do
[Kotatsu-Redo](https://github.com/Kotatsu-Redo/Kotatsu-Redo). Não é afiliada nem endossada pelos
mantenedores originais. Interface traduzida para português do Brasil.

Ele lê seus arquivos locais (CBZ/ZIP e pastas de imagens) **e também títulos online** de duas fontes
reais: **MangaDex** (API pública) e **catálogos OPDS** (Komga, Kavita, servidor próprio). A tela
**Explorar** é o Hub: ativar/fixar fontes, testar disponibilidade, pesquisar, filtrar, abrir detalhes,
ler e baixar capítulos. As outras ~900 fontes do app Android aparecem listadas como indisponíveis,
**com o motivo** — nada é simulado.

### Instalar e abrir

1. Baixe `KotatsuLunar-<versão>-windows-x64-portable.zip` (aba **Actions** ou **Releases**).
2. Descompacte em qualquer pasta e execute **`KotatsuLunar.exe`** — não é preciso instalar Java nem
   nenhuma outra ferramenta.
3. Alternativa: `KotatsuLunar-<versão>.msi` (instalador) ou `java -jar kotatsu-lunar.jar`.

### Usar

1. **Arquivo → Abrir CBZ/ZIP…** (`Ctrl+O`) ou arraste arquivos/pastas para a janela.
2. Clique duas vezes numa capa, escolha o capítulo e comece a ler.
3. O progresso é salvo automaticamente e aparece em **Histórico**.

* **0.2.1**: corrigido o `HTTP 400` do MangaDex (a lista de capítulos enviava `contentRating[]=porn`;
  o valor enumerado pela API é `pornographic`) e o tema escuro, cujos controles eram pintados pelo
  Look and Feel nativo com as próprias cores. Detalhes em [`CHANGELOG.md`](CHANGELOG.md).
* Modos de leitura: padrão, da direita para a esquerda, vertical e Webtoon (`M` alterna).
* Zoom: `+`/`-`/`0`, `Ctrl` + roda do mouse; `W` ajusta à largura, `H` à altura.
* Sumário: `T`. Próximo/anterior capítulo: `N`/`P`. Salvar página: `S`. Tela cheia: `F11`.
* **Explorar** (`Ctrl+E`): MangaDex já vem ativada; use **Testar** para ver se responde, **Fixar**
  para manter no topo e **Espelho** para trocar a URL base. Pesquise por título ou use **Populares
  agora**. Clique num resultado para adicionar à biblioteca e abrir os detalhes; **Atualizar
  capítulos** busca a lista atual sem perder o que já foi lido/baixado.
* Para o seu servidor, use **Adicionar catálogo OPDS…** com a URL do catálogo (Komga:
  `http://localhost:25600/opds/v1.2/catalog`). Só capítulos CBZ/ZIP são oferecidos — EPUB/PDF são
  ignorados porque esta versão não os renderiza.
* Capítulos podem ser copiados para uso offline (arquivo CBZ em `%APPDATA%\KotatsuLunar\downloads`),
  inclusive os que vêm da internet.
* **Backup Android**: no celular, *Configurações → Backup*; no PC, **Arquivo → Restaurar backup…**.
  A importação nunca apaga dados locais e mostra um relatório.

### O que funciona nas fontes online

* **MangaDex**: busca por título, filtro por status de publicação, interruptor de conteúdo adulto,
  filtro por tags, capítulos em inglês e português, lista de capítulos com volume/número/grupo,
  páginas, capas, downloads e troca de espelho com fallback automático.
* **OPDS**: catálogo do usuário, navegação biblioteca → série, busca OpenSearch, capítulos em
  CBZ/ZIP, download e leitura offline.
* Mensagens de erro específicas para: sem rede, tempo esgotado, HTTP 4xx/5xx, limite de requisições
  (429, respeitando `Retry-After`), título/capítulo não encontrado, resposta inválida e fonte
  indisponível.

### O que ainda não existe

As ~900 fontes do app Android (o pacote de parsers não é incluído; cada uma aparece no Hub com o
motivo), sincronização entre dispositivos, serviços de track (AniList/MAL), tela de estatísticas,
interface de marcadores, páginas em WebP/AVIF e resolução de desafios Cloudflare/JavaScript. A lista
completa e honesta está em [`docs/FEATURE_MATRIX.md`](docs/FEATURE_MATRIX.md) e o detalhamento por
fonte em [`docs/SOURCES.md`](docs/SOURCES.md).

### Dados e licença

Dados em `%APPDATA%\KotatsuLunar` (biblioteca, histórico, configurações e `sources.json` com as
fontes ativas); cache em `%LOCALAPPDATA%\KotatsuLunar\cache` (capas e páginas online, pode ser
apagado); logs em `%APPDATA%\KotatsuLunar\logs`. Código sob **GPL-3.0** — mantenha esta licença e os créditos
([`NOTICE.md`](NOTICE.md)) em qualquer redistribuição.
