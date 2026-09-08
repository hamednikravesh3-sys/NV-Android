import unittest

from release_readiness import ReleaseConfig, validate_https_service_url, validate_release_config


class ReleaseReadinessTest(unittest.TestCase):
    def test_accepts_distinct_https_services(self):
        errors = validate_release_config(
            ReleaseConfig(
                "https://cloud.nv.example/api",
                "https://codes.nv.example/api",
            )
        )
        self.assertEqual([], errors)

    def test_rejects_http_and_local_hosts(self):
        errors = validate_https_service_url("NV_CLOUD_API_URL", "http://localhost:8787")
        self.assertIn("NV_CLOUD_API_URL must use HTTPS", errors)
        self.assertIn("NV_CLOUD_API_URL must not point to a local-only host", errors)

    def test_rejects_embedded_credentials(self):
        errors = validate_https_service_url(
            "NV_CODE_REGISTRY_URL", "https://user:pass@codes.nv.example/api"
        )
        self.assertIn("NV_CODE_REGISTRY_URL must not embed credentials", errors)

    def test_rejects_same_cloud_and_registry_endpoint(self):
        errors = validate_release_config(
            ReleaseConfig("https://api.nv.example", "https://api.nv.example/")
        )
        self.assertIn(
            "NV_CLOUD_API_URL and NV_CODE_REGISTRY_URL must be distinct services",
            errors,
        )

    def test_requires_both_services(self):
        errors = validate_release_config(ReleaseConfig("", ""))
        self.assertIn("NV_CLOUD_API_URL is required", errors)
        self.assertIn("NV_CODE_REGISTRY_URL is required", errors)


if __name__ == "__main__":
    unittest.main()
