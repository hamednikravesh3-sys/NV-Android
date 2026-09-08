#!/usr/bin/env python3
"""Fail-closed validation for NV production release configuration."""

from __future__ import annotations

import argparse
import os
from dataclasses import dataclass
from urllib.parse import urlparse


@dataclass(frozen=True)
class ReleaseConfig:
    cloud_api_url: str
    nv_code_registry_url: str


def validate_https_service_url(name: str, value: str) -> list[str]:
    errors: list[str] = []
    if not value.strip():
        return [f"{name} is required"]

    parsed = urlparse(value.strip())
    if parsed.scheme.lower() != "https":
        errors.append(f"{name} must use HTTPS")
    if not parsed.hostname:
        errors.append(f"{name} must include a hostname")
        return errors

    host = parsed.hostname.lower()
    if host in {"localhost", "127.0.0.1", "::1"} or host.endswith(".local"):
        errors.append(f"{name} must not point to a local-only host")
    if parsed.username or parsed.password:
        errors.append(f"{name} must not embed credentials")
    if parsed.fragment:
        errors.append(f"{name} must not include a URL fragment")
    return errors


def validate_release_config(config: ReleaseConfig) -> list[str]:
    errors = []
    errors.extend(validate_https_service_url("NV_CLOUD_API_URL", config.cloud_api_url))
    errors.extend(validate_https_service_url("NV_CODE_REGISTRY_URL", config.nv_code_registry_url))

    cloud = config.cloud_api_url.strip().rstrip("/")
    registry = config.nv_code_registry_url.strip().rstrip("/")
    if cloud and registry and cloud == registry:
        errors.append("NV_CLOUD_API_URL and NV_CODE_REGISTRY_URL must be distinct services")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate NV production release endpoints")
    parser.add_argument("--cloud-url", default=os.getenv("NV_CLOUD_API_URL", ""))
    parser.add_argument("--registry-url", default=os.getenv("NV_CODE_REGISTRY_URL", ""))
    args = parser.parse_args()

    config = ReleaseConfig(args.cloud_url, args.registry_url)
    errors = validate_release_config(config)
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1

    print("NV production service configuration passed release-readiness validation")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
