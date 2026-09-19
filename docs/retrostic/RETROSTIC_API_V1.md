# Retrostic API v1 Specification

## 1. Overview

This document specifies the official Retrostic REST API (`v1`). It serves as the authoritative contract for the `RetrosticApiResolver` within the `games-tvbox` importer architecture, enabling low-latency, deterministic catalog synchronization and download ticket generation without scraping.

- **Base URL**: `https://api.retrostic.com/v1` (or local development mirror `http://127.0.0.1:5000/api/v1`)
- **Protocol**: HTTPS / HTTP 1.1 & HTTP/2
- **Data Format**: `application/json; charset=utf-8`
- **Authentication**: Optional Bearer API Key via `Authorization: Bearer <token>` or public guest rate-limited access.

---

## 2. API Endpoints

### 2.1. List Platforms
Retrieves all gaming platforms supported by the catalog.

- **Method**: `GET`
- **Path**: `/platforms`
- **Query Parameters**: None
- **Response**: `200 OK`
```json
{
  "platforms": [
    {
      "id": "ps1",
      "slug": "playstation",
      "name": "Sony PlayStation",
      "category": "Console",
      "supportedExtensions": [".cue", ".bin", ".iso", ".chd"],
      "canonicalFormat": "chd",
      "gameCount": 1640
    },
    {
      "id": "snes",
      "slug": "super-nintendo",
      "name": "Super Nintendo Entertainment System",
      "category": "Console",
      "supportedExtensions": [".smc", ".sfc"],
      "canonicalFormat": "sfc",
      "gameCount": 1757
    },
    {
      "id": "nes",
      "slug": "nintendo",
      "name": "Nintendo Entertainment System",
      "category": "Console",
      "supportedExtensions": [".nes"],
      "canonicalFormat": "nes",
      "gameCount": 1820
    },
    {
      "id": "megadrive",
      "slug": "genesis",
      "name": "Sega Genesis / Mega Drive",
      "category": "Console",
      "supportedExtensions": [".md", ".gen", ".bin"],
      "canonicalFormat": "md",
      "gameCount": 915
    },
    {
      "id": "gba",
      "slug": "gameboy-advance",
      "name": "Game Boy Advance",
      "category": "Handheld",
      "supportedExtensions": [".gba"],
      "canonicalFormat": "gba",
      "gameCount": 1498
    }
  ]
}
```

---

### 2.2. Search Games
Searches catalog titles across platforms with fuzzy matching and filtering.

- **Method**: `GET`
- **Path**: `/search`
- **Query Parameters**:
  - `q` (string, required): Search terms (e.g. `crash bandicoot`)
  - `platform` (string, optional): Platform ID filter (e.g. `ps1`)
  - `page` (int, default: 1): Page number
  - `pageSize` (int, default: 25, max: 100): Results per page
- **Response**: `200 OK`
```json
{
  "page": 1,
  "pageSize": 25,
  "total": 3,
  "results": [
    {
      "id": "ps1-crash-bandicoot-usa",
      "slug": "crash-bandicoot",
      "platformId": "ps1",
      "title": "Crash Bandicoot",
      "region": "USA",
      "releaseYear": 1996,
      "thumbnailUrl": "https://images.retrostic.com/covers/ps1/crash-bandicoot.jpg",
      "archiveSizeBytes": 45612346,
      "sourceArchiveFormat": "7z"
    },
    {
      "id": "ps1-crash-bandicoot-2-cortex-strikes-back-usa",
      "slug": "crash-bandicoot-2-cortex-strikes-back",
      "platformId": "ps1",
      "title": "Crash Bandicoot 2: Cortex Strikes Back",
      "region": "USA",
      "releaseYear": 1997,
      "thumbnailUrl": "https://images.retrostic.com/covers/ps1/crash-bandicoot-2.jpg",
      "archiveSizeBytes": 128456120,
      "sourceArchiveFormat": "7z"
    }
  ]
}
```

---

### 2.3. Get Game Details
Retrieves complete game metadata, media assets, and file descriptors.

- **Method**: `GET`
- **Path**: `/games/{gameId}`
- **Response**: `200 OK`
```json
{
  "id": "ps1-crash-bandicoot-usa",
  "slug": "crash-bandicoot",
  "platformId": "ps1",
  "title": "Crash Bandicoot",
  "region": "USA",
  "releaseYear": 1996,
  "publisher": "Sony Computer Entertainment",
  "developer": "Naughty Dog",
  "description": "Crash Bandicoot is a platform video game developed by Naughty Dog and published by Sony Computer Entertainment for the PlayStation.",
  "coverUrl": "https://images.retrostic.com/covers/ps1/crash-bandicoot.jpg",
  "screenshots": [
    "https://images.retrostic.com/screens/ps1/crash-bandicoot-1.jpg",
    "https://images.retrostic.com/screens/ps1/crash-bandicoot-2.jpg"
  ],
  "sourceArchive": {
    "filename": "Crash Bandicoot (USA).7z",
    "sizeBytes": 45612346,
    "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "format": "7z"
  }
}
```

---

### 2.4. Request Download Ticket
Generates a signed, temporary direct download ticket with resumption parameters.

- **Method**: `POST`
- **Path**: `/games/{gameId}/download-ticket`
- **Request Body**:
```json
{
  "clientType": "desktop-importer",
  "clientVersion": "2.0.0"
}
```
- **Response**: `200 OK`
```json
{
  "ticketId": "tkt_01hxyz890abcd",
  "gameId": "ps1-crash-bandicoot-usa",
  "downloadUrl": "https://downloads.retrostic.com/roms/ps1/Crash%20Bandicoot%20(USA).7z?ticket=tkt_01hxyz890abcd&token=sig_987654321",
  "filename": "Crash Bandicoot (USA).7z",
  "sizeBytes": 45612346,
  "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "supportsRange": true,
  "expiresAt": 1726750000,
  "headers": {
    "User-Agent": "JogosRetroImporter/2.0",
    "Referer": "https://www.retrostic.com/roms/ps1/crash-bandicoot"
  }
}
```

- **Error Responses**:
  - `404 Not Found`: Game does not exist.
  - `429 Too Many Requests`: Rate limit reached.
  - `503 Service Unavailable`: Upstream storage maintenance.

---

## 3. Client Implementation Guidelines

1. **Ticket Renewal on HTTP 403 / 410**:
   - If a download transfer fails with `403 Forbidden` or `410 Gone`, the `DownloadManager` calls `/download-ticket` again to refresh `downloadUrl`.
   - Transfer is resumed at the current file length using the standard `Range: bytes={offset}-` request header.

2. **Integrity Validation**:
   - The returned `sha256` corresponds to the compressed archive (`sourceArtifactSha256`).
   - The client verifies this hash prior to unpacking and launching `IntakePipeline`.
