from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def patch(path: str, replacements: dict[str, str]) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    for old, new in replacements.items():
        if old not in text:
            raise SystemExit(f"{path}: missing expected token: {old}")
        text = text.replace(old, new)
    target.write_text(text, encoding="utf-8")


patch(
    "tools/test_android16_completion.py",
    {
        "r'versionCode\\s*=\\s*21'": "r'versionCode\\s*=\\s*22'",
        "'versionName = \"0.18.2\"'": "'versionName = \"0.18.5\"'",
        '"versionCode=\'21\'"': '"versionCode=\'22\'"',
        '"versionName=\'0.18.2\'"': '"versionName=\'0.18.5\'"',
        "'MAX_CURRENT_LOCATION_ACCURACY_METERS = 18f'": "'MAX_CURRENT_LOCATION_ACCURACY_METERS = 10f'",
        "'MAX_NAVIGATION_ACCURACY_METERS = 35f'": "'MAX_NAVIGATION_ACCURACY_METERS = 20f'",
    },
)

patch(
    ".github/workflows/release.yml",
    {
        "versionCode='21'": "versionCode='22'",
        "versionName='0.18.2'": "versionName='0.18.5'",
        "version_code=21": "version_code=22",
        "version_name=0.18.2": "version_name=0.18.5",
        '"version_code": 21': '"version_code": 22',
        '"version_name": "0.18.2"': '"version_name": "0.18.5"',
        'm["version_code"] == 21': 'm["version_code"] == 22',
        'm["version_name"] == "0.18.2"': 'm["version_name"] == "0.18.5"',
    },
)

print("Synced v0.18.5 CI/release expectations")
