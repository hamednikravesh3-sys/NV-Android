import unittest

from release_readiness import (
    ReleaseConfig,
    validate_api_key,
    validate_https_service_url,
    validate_release_config,
)


VALID_KEY = "AIzaSyNVProductionKey1234567890"
VALID_VALHALLA = "https://routing.nv-navigation.ir"


class ReleaseReadinessTest(unittest.TestCase):
    def config(self, cloud="https://cloud.nv-navigation.ir/api", registry="https://codes.nv-navigation.ir/api", key=VALID_KEY, valhalla=VALID_VALHALLA):
        return ReleaseConfig(cloud, registry, key, valhalla)

    def test_accepts_complete_production_configuration(self):
        self.assertEqual([], validate_release_config(self.config()))

    def test_accepts_osm_only_production_configuration(self):
        self.assertEqual([], validate_release_config(self.config(key="")))

    def test_accepts_local_map_matching_fallback_without_valhalla(self):
        self.assertEqual([], validate_release_config(self.config(valhalla="")))

    def test_accepts_osm_and_local_map_matching_fallback(self):
        self.assertEqual([], validate_release_config(self.config(key="", valhalla="")))

    def test_rejects_http_and_local_hosts(self):
        errors = validate_https_service_url("NV_CLOUD_API_URL", "http://localhost:8787")
        self.assertIn("NV_CLOUD_API_URL must use HTTPS", errors)
        self.assertIn("NV_CLOUD_API_URL must not point to a local-only host", errors)

    def test_rejects_private_ip_addresses(self):
        errors = validate_https_service_url("NV_CLOUD_API_URL", "https://10.0.0.8/api")
        self.assertIn(
            "NV_CLOUD_API_URL must not point to a private or non-routable IP address",
            errors,
        )

    def test_rejects_embedded_credentials(self):
        errors = validate_https_service_url(
            "NV_CODE_REGISTRY_URL", "https://user:pass@codes.nv-navigation.ir/api"
        )
        self.assertIn("NV_CODE_REGISTRY_URL must not embed credentials", errors)

    def test_rejects_reserved_and_placeholder_hosts(self):
        for value in (
            "https://example.com/api",
            "https://codes.example/api",
            "https://codes.invalid/api",
            "https://codes.test/api",
        ):
            with self.subTest(value=value):
                errors = validate_https_service_url("NV_CODE_REGISTRY_URL", value)
                self.assertIn(
                    "NV_CODE_REGISTRY_URL must not use a placeholder or reserved hostname",
                    errors,
                )

    def test_rejects_query_and_fragment(self):
        query_errors = validate_https_service_url(
            "NV_CLOUD_API_URL", "https://cloud.nv-navigation.ir/api?token=secret"
        )
        self.assertIn("NV_CLOUD_API_URL must not include a query string", query_errors)

        fragment_errors = validate_https_service_url(
            "NV_CLOUD_API_URL", "https://cloud.nv-navigation.ir/api#prod"
        )
        self.assertIn("NV_CLOUD_API_URL must not include a URL fragment", fragment_errors)

    def test_rejects_same_service_origin_even_with_different_paths(self):
        errors = validate_release_config(
            self.config(
                cloud="https://api.nv-navigation.ir/cloud",
                registry="https://api.nv-navigation.ir/codes",
            )
        )
        self.assertIn(
            "NV_CLOUD_API_URL and NV_CODE_REGISTRY_URL must use distinct service origins",
            errors,
        )

    def test_requires_essential_production_services(self):
        errors = validate_release_config(ReleaseConfig("", "", "", ""))
        self.assertIn("NV_CLOUD_API_URL is required", errors)
        self.assertIn("NV_CODE_REGISTRY_URL is required", errors)
        self.assertNotIn("NV_GOOGLE_MAPS_API_KEY is required", errors)
        self.assertNotIn("NV_VALHALLA_API_URL is required", errors)

    def test_rejects_truncated_google_key_when_configured(self):
        errors = validate_release_config(self.config(key="short"))
        self.assertIn("NV_GOOGLE_MAPS_API_KEY looks invalid or truncated", errors)
        self.assertIn(
            "NV_GOOGLE_MAPS_API_KEY looks invalid or truncated",
            validate_api_key("NV_GOOGLE_MAPS_API_KEY", "short"),
        )

    def test_rejects_local_valhalla_when_configured(self):
        errors = validate_release_config(self.config(valhalla="http://localhost:8002"))
        self.assertIn("NV_VALHALLA_API_URL must use HTTPS", errors)
        self.assertIn("NV_VALHALLA_API_URL must not point to a local-only host", errors)


if __name__ == "__main__":
    unittest.main()
