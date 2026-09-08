import unittest

from release_readiness import ReleaseConfig, validate_https_service_url, validate_release_config


class ReleaseReadinessTest(unittest.TestCase):
    def test_accepts_distinct_https_services(self):
        errors = validate_release_config(
            ReleaseConfig(
                "https://cloud.nv-navigation.ir/api",
                "https://codes.nv-navigation.ir/api",
            )
        )
        self.assertEqual([], errors)

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
            ReleaseConfig(
                "https://api.nv-navigation.ir/cloud",
                "https://api.nv-navigation.ir/codes",
            )
        )
        self.assertIn(
            "NV_CLOUD_API_URL and NV_CODE_REGISTRY_URL must use distinct service origins",
            errors,
        )

    def test_requires_both_services(self):
        errors = validate_release_config(ReleaseConfig("", ""))
        self.assertIn("NV_CLOUD_API_URL is required", errors)
        self.assertIn("NV_CODE_REGISTRY_URL is required", errors)


if __name__ == "__main__":
    unittest.main()
