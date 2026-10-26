# Kotatsu Lunar — desktop (Windows 10/11) port of Kotatsu-Redo

> **Unofficial port.** Kotatsu Lunar is a desktop application derived from
> [Kotatsu-Redo](https://github.com/Kotatsu-Redo/Kotatsu-Redo) (GPL-3.0). It is not affiliated with,
> endorsed by, or supported by the Kotatsu-Redo maintainers. See [`NOTICE.md`](NOTICE.md).

Kotatsu Lunar runs as a normal Windows program: a resizable window with mouse and keyboard control,
a local library of your own CBZ/ZIP archives (or folders of images), four reading modes, offline
copies, reading history and progress, and import/export of **Android Kotatsu backups**.

* **Prefer português?** Veja a seção [Português (Brasil)](#português-brasil) no fim deste arquivo.

| | |
| --- | --- |
| Platform | Windows 10 / 11, x64 (the jar also runs on Linux/macOS with Java 17+) |
| Language / UI toolkit | Kotlin 2.1 / Swing (JDK 17) |
| Runtime dependencies | none beyond the bundled Java runtime |
| Licence | GPL-3.0 (same as upstream) |
| Upstream commit | `d45b7c13de260b6c78de78ccbf968daf2187f1cf` |
| Status | reading flow complete for local content; online sources not yet available — see [`docs/FEATURE_MATRIX.md`](docs/FEATURE_MATRIX.md) |

---

## 1. Install and run

**Downloads:** [Releases](https://github.com/klua007/kotatsu-lunar-exe/releases) —
`KotatsuLunar-0.1.0-windows-x64-portable.zip`, `KotatsuLunar-0.1.0.msi` and `kotatsu-lunar.jar`
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
| Covers / page cache (safe to delete) | `%LOCALAPPDATA%\KotatsuLunar\cache` |
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
* Light/dark theme, English and **Brazilian Portuguese** UI.
* `--smoke-test` self check and a 78-case test suite.

Not implemented yet (and not claimed): online manga sources, cross-device sync, tracking services,
statistics screen, bookmarks UI, WebP/AVIF pages, incognito mode, app lock. The full, honest
breakdown is in [`docs/FEATURE_MATRIX.md`](docs/FEATURE_MATRIX.md).

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
docs/                                                       architecture, matrix, guides
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

The application ships **no content**. It only opens files that the user already has on disk; it does
not bypass DRM or any third-party protection, and it does not include copyrighted material.

---

## Português (Brasil)

**Kotatsu Lunar** é uma versão **não oficial** para desktop (Windows 10/11, 64 bits) do
[Kotatsu-Redo](https://github.com/Kotatsu-Redo/Kotatsu-Redo). Não é afiliada nem endossada pelos
mantenedores originais. Interface traduzida para português do Brasil.

### Instalar e abrir

1. Baixe `KotatsuLunar-<versão>-windows-x64-portable.zip` (aba **Actions** ou **Releases**).
2. Descompacte em qualquer pasta e execute **`KotatsuLunar.exe`** — não é preciso instalar Java nem
   nenhuma outra ferramenta.
3. Alternativa: `KotatsuLunar-<versão>.msi` (instalador) ou `java -jar kotatsu-lunar.jar`.

### Usar

1. **Arquivo → Abrir CBZ/ZIP…** (`Ctrl+O`) ou arraste arquivos/pastas para a janela.
2. Clique duas vezes numa capa, escolha o capítulo e comece a ler.
3. O progresso é salvo automaticamente e aparece em **Histórico**.

* Modos de leitura: padrão, da direita para a esquerda, vertical e Webtoon (`M` alterna).
* Zoom: `+`/`-`/`0`, `Ctrl` + roda do mouse; `W` ajusta à largura, `H` à altura.
* Sumário: `T`. Próximo/anterior capítulo: `N`/`P`. Salvar página: `S`. Tela cheia: `F11`.
* Capítulos podem ser copiados para uso offline (arquivo CBZ em `%APPDATA%\KotatsuLunar\downloads`).
* **Backup Android**: no celular, *Configurações → Backup*; no PC, **Arquivo → Restaurar backup…**.
  A importação nunca apaga dados locais e mostra um relatório.

### O que ainda não existe

Fontes online de mangá, sincronização entre dispositivos, serviços de track (AniList/MAL), tela de
estatísticas, interface de marcadores e páginas em WebP/AVIF. A lista completa e honesta está em
[`docs/FEATURE_MATRIX.md`](docs/FEATURE_MATRIX.md).

### Dados e licença

Dados em `%APPDATA%\KotatsuLunar`; cache em `%LOCALAPPDATA%\KotatsuLunar\cache`; logs em
`%APPDATA%\KotatsuLunar\logs`. Código sob **GPL-3.0** — mantenha esta licença e os créditos
([`NOTICE.md`](NOTICE.md)) em qualquer redistribuição.
