#!/usr/bin/env python3
"""Serve the production Web/Wasm bundle with deterministic catalog preview data."""

from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse
import json
import os


ROOT = Path(__file__).resolve().parents[1] / "composeApp" / "build" / "dist" / "wasmJs" / "productionExecutable"


def title(identifier: int, name: str, media_type: str = "movie", rating: float = 8.0, year: str = "2024") -> dict:
    return {
        "id": identifier,
        "media_type": media_type,
        "title": name,
        "original_title": name,
        "overview": "A vivid story about memory, identity, and the movies that stay with us.",
        "poster_path": None,
        "backdrop_path": None,
        "release_date": f"{year}-01-01",
        "vote_average": rating,
        "genre_ids": [18],
    }


TITLES = [
    title(550, "Fight Club", rating=8.4, year="1999"),
    title(680, "Pulp Fiction", rating=8.5, year="1994"),
    title(155, "The Dark Knight", rating=8.5, year="2008"),
    title(13, "Forrest Gump", rating=8.5, year="1994"),
    title(1396, "Breaking Bad", "tv", 8.9, "2008"),
    title(100088, "The Last of Us", "tv", 8.6, "2023"),
    title(27205, "Inception", rating=8.4, year="2010"),
    title(238, "The Godfather", rating=8.7, year="1972"),
]


def unique_titles(items: list[dict]) -> list[dict]:
    return list({f'{item["media_type"]}:{item["id"]}': item for item in items}.values())


class PreviewHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def end_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path.startswith("/v1/"):
            self.send_catalog(parsed.path, parse_qs(parsed.query))
            return
        super().do_GET()

    def send_catalog(self, path: str, query: dict):
        media_type = query.get("media_type", ["movie"])[0]
        filtered = [item for item in TITLES if item["media_type"] == media_type]
        if path == "/v1/configuration":
            payload = {"secure_base_url": "", "poster_sizes": [], "backdrop_sizes": [], "profile_sizes": []}
        elif path == "/v1/home":
            payload = {
                "media_type": media_type,
                "hero": filtered[0] if filtered else TITLES[0],
                "sections": [
                    {"id": "trending", "title": "Trending now", "items": unique_titles(filtered + TITLES[:4])},
                    {"id": "top-rated", "title": "Modern classics", "items": list(reversed(TITLES))},
                ],
            }
        elif path.startswith("/v1/genres/"):
            payload = {"genres": [{"id": 18, "name": "Drama"}, {"id": 28, "name": "Action"}, {"id": 53, "name": "Thriller"}]}
        elif path.startswith("/v1/discover/"):
            route_type = path.rsplit("/", 1)[-1]
            results = [item for item in TITLES if item["media_type"] == route_type]
            payload = {"page": 1, "total_pages": 1, "results": unique_titles(results + TITLES)}
        elif path == "/v1/search":
            term = query.get("query", [""])[0].lower()
            results = [item for item in TITLES if term in item["title"].lower()]
            payload = {"page": 1, "total_pages": 1, "results": results}
        elif path.startswith("/v1/titles/"):
            identifier = int(path.rsplit("/", 1)[-1])
            summary = next((item for item in TITLES if item["id"] == identifier), TITLES[0])
            payload = {
                **summary,
                "genres": [{"id": 18, "name": "Drama"}, {"id": 53, "name": "Thriller"}],
                "runtime_minutes": 139,
                "status": "Released",
                "cast": [
                    {"id": 1, "name": "Alex Morgan", "character": "The Narrator", "profile_path": None},
                    {"id": 2, "name": "Jamie Lee", "character": "Mara", "profile_path": None},
                ],
                "videos": [{"id": "trailer", "key": "SUXWAEX2jlg", "name": "Official Trailer", "site": "YouTube", "type": "Trailer", "official": True, "language": "en"}],
                "similar": TITLES[1:6],
            }
        else:
            self.send_error(404)
            return
        encoded = json.dumps(payload).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


if __name__ == "__main__":
    port = int(os.environ.get("SMARTMOVIE_PREVIEW_PORT", "8099"))
    print(f"Serving SmartMovie preview at http://127.0.0.1:{port}")
    ThreadingHTTPServer(("127.0.0.1", port), PreviewHandler).serve_forever()
