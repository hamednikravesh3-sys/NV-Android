#!/usr/bin/env python3
"""Fail-closed health probes for NV production services before signing a release."""

from __future__ import annotations

import json
import os
import ssl
import sys
from dataclasses import dataclass
from typing import Callable
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit, urlunsplit
from urllib.request import Request, build_opener, HTTPSHandler, HTTPRedirectHandler


@dataclass(frozen=True)
class ProbeResponse:
    status: int
    url: str
    headers: dict[str, str]
    body: bytes


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: ANN001
        return None


def health_url(base_url: str) -> str:
    parsed = urlsplit(base_url.strip())
    path = parsed.path.rstrip("/") + "/health"
    return urlunsplit((parsed.scheme, parsed.netloc, path, "", ""))


def _default_fetch(url: str, timeout: float = 8.0) -> ProbeResponse:
    opener = build_opener(NoRedirect(), HTTPSHandler(context=ssl.create_default_context()))
    request = Request(
        url,
        method="GET",
        headers={
            "accept": "application/json",
            "user-agent": "NV-Release-Probe/1.0",
        },
    )
    try:
        with opener.open(request, timeout=timeout) as response:
            return ProbeResponse(
                status=int(response.status),
                url=response.geturl(),
                headers={k.lower(): v for k, v in response.headers.items()},
                body=response.read(64 * 1024),
            )
    except HTTPError as error:
        return ProbeResponse(
            status=int(error.code),
            url=error.geturl(),
            headers={k.lower(): v for k, v in error.headers.items()},
            body=error.read(64 * 1024),
        )


def _same_origin(left: str, right: str) -> bool:
    a = urlsplit(left)
    b = urlsplit(right)
    return (a.scheme.lower(), a.hostname and a.hostname.lower(), a.port or 443) == (
        b.scheme.lower(),
        b.hostname and b.hostname.lower(),
        b.port or 443,
    )


def probe_service(
    name: str,
    base_url: str,
    expected_fields: dict[str, object],
    fetcher: Callable[[str], ProbeResponse] = _default_fetch,
) -> list[str]:
    errors: list[str] = []
    target = health_url(base_url)
    try:
        response = fetcher(target)
    except (TimeoutError, URLError, OSError) as error:
        return [f"{name} health probe failed: {type(error).__name__}"]

    if response.status != 200:
        errors.append(f"{name} /health returned HTTP {response.status}")
    if not _same_origin(target, response.url):
        errors.append(f"{name} /health redirected to a different origin")

    content_type = response.headers.get("content-type", "").lower()
    if "application/json" not in content_type:
        errors.append(f"{name} /health must return application/json")
    cache_control = response.headers.get("cache-control", "").lower()
    if "no-store" not in cache_control:
        errors.append(f"{name} /health must be no-store")
    if response.headers.get("x-content-type-options", "").lower() != "nosniff":
        errors.append(f"{name} /health must set X-Content-Type-Options: nosniff")

    try:
        payload = json.loads(response.body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        errors.append(f"{name} /health returned invalid JSON")
        return errors

    if not isinstance(payload, dict):
        errors.append(f"{name} /health JSON must be an object")
        return errors
    for key, expected in expected_fields.items():
        if payload.get(key) != expected:
            errors.append(f"{name} /health field {key!r} must equal {expected!r}")
    return errors


def validate_production_services(
    cloud_url: str,
    registry_url: str,
    fetcher: Callable[[str], ProbeResponse] = _default_fetch,
) -> list[str]:
    errors: list[str] = []
    errors.extend(
        probe_service(
            "NV Cloud Sync",
            cloud_url,
            {"ok": True, "service": "nv-cloud-sync"},
            fetcher,
        )
    )
    errors.extend(
        probe_service(
            "NV Code Registry",
            registry_url,
            {
                "service": "NV Code Registry",
                "status": "ok",
                "allocation": "online-unique",
            },
            fetcher,
        )
    )
    return errors


def main() -> int:
    cloud_url = os.getenv("NV_CLOUD_API_URL", "").strip()
    registry_url = os.getenv("NV_CODE_REGISTRY_URL", "").strip()
    errors = validate_production_services(cloud_url, registry_url)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print("NV production service health probes passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
