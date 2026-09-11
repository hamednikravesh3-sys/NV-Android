#!/usr/bin/env python3
"""Fail-closed validation for NV production release configuration."""

from __future__ import annotations

import argparse
import ipaddress
import os
from dataclasses import dataclass
from urllib.parse import urlparse


@dataclass(frozen=True)
class ReleaseConfig:
    cloud_api_url: str
    nv_code_registry_url: str
    google_maps_api_key: str = ""
    valhalla_api_url: str = ""


_RESERVED_SUFFIXES = (
    ".example",
    ".invalid",
    ".localhost",
    ".test",
)


def validate_https_service_url(name: str, value: str) -> list[str]:
    errors: list[str] = []
    raw = value.strip()
    if not raw:
        return [f"{name} is required"]

    parsed = urlparse(raw)
    if parsed.scheme.lower() != "https":
        errors.append(f"{name} must use HTTPS")
    if not parsed.hostname:
        errors.append(f"{name} must include a hostname")
        return errors

    host = parsed.hostname.lower().rstrip(".")
    if host in {"localhost", "127.0.0.1", "::1"} or host.endswith(".local"):
        errors.append(f"{name} must not point to a local-only host")

    try:
        address = ipaddress.ip_address(host)
    except ValueError:
        address = None
    if address and (address.is_private or address.is_loopback or address.is_link_local or address.is_unspecified):
        errors.append(f"{name} must not point to a private or non-routable IP address")

    if host == "example.com" or any(host.endswith(suffix) for suffix in _RESERVED_SUFFIXES):
        errors.append(f"{name} must not use a placeholder or reserved hostname")

    if parsed.username or parsed.password:
        errors.append(f"{name} must not embed credentials")
    if parsed.fragment:
        errors.append(f"{name} must not include a URL fragment")
    if parsed.query:
        errors.append(f"{name} must not include a query string")
    return errors


def validate_api_key(name: str, value: str) -> list[str]:
    raw = value.strip()
    if not raw:
        return [f"{name} is required"]
    if len(raw) < 20:
        return [f"{name} looks invalid or truncated"]
    if any(character.isspace() for character in raw):
        return [f"{name} must not contain whitespace"]
    return []


def _normalized_service_origin(value: str) -> tuple[str, str, int | None] | None:
    raw = value.strip()
    if not raw:
        return None
    parsed = urlparse(raw)
    if not parsed.hostname:
        return None
    try:
        port = parsed.port
    except ValueError:
        return None
    return (parsed.scheme.lower(), parsed.hostname.lower().rstrip("."), port)


def validate_release_config(config: ReleaseConfig) -> list[str]:
    errors: list[str] = []
    errors.extend(validate_https_service_url("NV_CLOUD_API_URL", config.cloud_api_url))
    errors.extend(validate_https_service_url("NV_CODE_REGISTRY_URL", config.nv_code_registry_url))
    errors.extend(validate_api_key("NV_GOOGLE_MAPS_API_KEY", config.google_maps_api_key))
    errors.extend(validate_https_service_url("NV_VALHALLA_API_URL", config.valhalla_api_url))

    cloud_origin = _normalized_service_origin(config.cloud_api_url)
    registry_origin = _normalized_service_origin(config.nv_code_registry_url)
    if cloud_origin and registry_origin and cloud_origin == registry_origin:
        errors.append("NV_CLOUD_API_URL and NV_CODE_REGISTRY_URL must use distinct service origins")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate NV production release endpoints")
    parser.add_argument("--cloud-url", default=os.getenv("NV_CLOUD_API_URL", ""))
    parser.add_argument("--registry-url", default=os.getenv("NV_CODE_REGISTRY_URL", ""))
    parser.add_argument("--google-maps-api-key", default=os.getenv("NV_GOOGLE_MAPS_API_KEY", ""))
    parser.add_argument("--valhalla-url", default=os.getenv("NV_VALHALLA_API_URL", ""))
    args = parser.parse_args()

    config = ReleaseConfig(
        args.cloud_url,
        args.registry_url,
        args.google_maps_api_key,
        args.valhalla_url,
    )
    errors = validate_release_config(config)
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1

    print("NV production service configuration passed release-readiness validation")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
