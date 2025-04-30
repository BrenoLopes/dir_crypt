# 7‑Zip Encryptor App

A tiny, cross‑platform CLI written in **Java 21** that walks through any directory tree, encrypts every file individually into an **AES‑256** 7‑Zip archive (`file.ext.7z`), and can later verify the integrity of those archives.  The tool is designed for people who want to store documents in the cloud (OneDrive, Google Drive, Dropbox, etc.) or on external drives while keeping zero‑knowledge privacy—each file is separately protected, so only changed items need to sync.

---

## ✨ Features

- **Per‑file encryption** – no multi‑GB monolithic containers.
- **Recursive** – traverses sub‑folders automatically.
- **Pause / Resume** – run it again and it skips archives that are already OK.
- **Integrity check** – `verify` command runs `7z t` on every archive.
- **ACID‑ish safety** – writes to a `*.tmp` file, verifies, then atomically moves → no half‑written data.
- **Auto‑detects 7‑Zip** on Windows / macOS / Linux, or accept a custom path.

---

## Requirements

| Tool      | Version                                                                               |
| --------- | ------------------------------------------------------------------------------------- |
| JDK       | 17 (works on 17 LTS+)                                                                 |
| 7‑Zip CLI | 19+ (comes pre‑installed on many Linux distros; Windows users install from 7‑Zip.org) |
| Gradle    | Not required – the wrapper is included                                                |

---

## Build

```bash
# clone & enter
git clone https://github.com/your‑org/encrypt7z.git
cd encrypt7z

# build a runnable fat JAR
./gradlew shadowJar
```

The self‑contained JAR appears at `build/libs/encryptor-all.jar`.

---

## Usage

```bash
java -jar encryptor-all.jar <command> [options]
```

### Commands

| Name      | Description                                                                                                                           |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `encrypt` | Encrypts all regular files under *input* into `.7z` archives in *output*. Can be run multiple times; already‑OK archives are skipped. |
| `verify`  | Verifies every `.7z` file under *output* and prints a summary.                                                                        |
| `--help`  | Prints usage info.                                                                                                                    |

### Options

| Flag             | Default       | Purpose                                                                                                                    |
| ---------------- | ------------- | -------------------------------------------------------------------------------------------------------------------------- |
| `--7zip <path>`  | *(auto)*      | Path to the `7z` executable. If omitted, the program tries common locations (Windows Program Files, Homebrew, `/usr/bin`). |
| `--input <dir>`  | `.`           | Source directory that contains plaintext files.                                                                            |
| `--output <dir>` | `./encrypted` | Destination root for encrypted `.7z` archives.                                                                             |

---

## Examples

Encrypt a folder and place encrypted copies in `secure/`:

```bash
java -jar encryptor-all.jar encrypt \
     --input ~/Documents/tax \
     --output ~/secure/tax
```

Later, verify the backups:

```bash
java -jar encryptor-all.jar verify --output ~/secure/tax
```

Use a custom 7‑Zip binary:

```bash
java -jar encryptor-all.jar encrypt --7zip /opt/7z/7zz --input data --output vault
```

---

## How it works

1. The program asks for a **master pass‑phrase** once per run (hidden input).
2. Each file is archived via `7z a -t7z -mhe=on -pPASSWORD file.ext.7z file.ext`.
3. The temp archive is verified with `7z t`. Only on success is it moved into place atomically.
4. Re‑running the tool calls `7z t` on existing archives; exit codes `0` and `1` are accepted as “OK”.

---

## License

MIT – see [LICENSE](LICENSE) for details.
