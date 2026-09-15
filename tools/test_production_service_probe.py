import unittest

from production_service_probe import ProbeResponse, health_url, validate_production_services


class ProductionServiceProbeTest(unittest.TestCase):
    def test_health_url_preserves_base_path(self):
        self.assertEqual(
            "https://api.nv.example/base/health",
            health_url("https://api.nv.example/base/"),
        )

    def test_accepts_expected_cloud_and_registry_contracts(self):
        def fetcher(url):
            common = {
                "content-type": "application/json; charset=utf-8",
                "cache-control": "no-store",
                "x-content-type-options": "nosniff",
            }
            if "cloud" in url:
                body = b'{"ok":true,"service":"nv-cloud-sync"}'
            else:
                body = b'{"service":"NV Code Registry","status":"ok","allocation":"online-unique"}'
            return ProbeResponse(200, url, common, body)

        self.assertEqual(
            [],
            validate_production_services(
                "https://cloud.nv.example",
                "https://codes.nv.example",
                fetcher,
            ),
        )

    def test_rejects_redirected_origin_and_bad_headers(self):
        def fetcher(url):
            body = b'{"ok":true,"service":"nv-cloud-sync"}' if "cloud" in url else b'{"service":"NV Code Registry","status":"ok","allocation":"online-unique"}'
            return ProbeResponse(
                200,
                "https://redirected.example/health",
                {"content-type": "text/plain"},
                body,
            )

        errors = validate_production_services(
            "https://cloud.nv.example",
            "https://codes.nv.example",
            fetcher,
        )
        self.assertTrue(any("different origin" in error for error in errors))
        self.assertTrue(any("application/json" in error for error in errors))
        self.assertTrue(any("no-store" in error for error in errors))
        self.assertTrue(any("nosniff" in error for error in errors))

    def test_rejects_wrong_health_payload_and_status(self):
        def fetcher(url):
            return ProbeResponse(
                503,
                url,
                {
                    "content-type": "application/json",
                    "cache-control": "no-store",
                    "x-content-type-options": "nosniff",
                },
                b'{"status":"starting"}',
            )

        errors = validate_production_services(
            "https://cloud.nv.example",
            "https://codes.nv.example",
            fetcher,
        )
        self.assertTrue(any("HTTP 503" in error for error in errors))
        self.assertTrue(any("field" in error for error in errors))

    def test_rejects_invalid_json(self):
        def fetcher(url):
            return ProbeResponse(
                200,
                url,
                {
                    "content-type": "application/json",
                    "cache-control": "no-store",
                    "x-content-type-options": "nosniff",
                },
                b'not-json',
            )

        errors = validate_production_services(
            "https://cloud.nv.example",
            "https://codes.nv.example",
            fetcher,
        )
        self.assertEqual(2, sum("invalid JSON" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
