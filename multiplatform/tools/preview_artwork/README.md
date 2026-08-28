# Documentation-only demo artwork

Generated on 2026-08-28 for deterministic local UI captures. The abstract
illustrations are not TMDb movie posters, actor photographs or store artwork.
They are served only by `tools/preview_server.py`, not bundled as production
catalog fallbacks. The atlas supplies a backdrop; poster variants supply cards.

A successful preview image request proves local image transport/rendering only.
Production still requires a working Worker origin, TMDb image configuration and
valid TMDb file paths. Missing production artwork must remain visibly unavailable,
not silently replaced with these illustrations.
