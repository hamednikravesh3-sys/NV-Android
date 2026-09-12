#!/usr/bin/env python3
"""Extract one Iranian province boundary from the pinned 31-province GeoJSON source."""

from __future__ import annotations

import argparse
import json
import re
import unicodedata
from pathlib import Path

PROVINCES = {
    "alborz": ("البرز", "Alborz"),
    "ardabil": ("اردبیل", "Ardabil"),
    "bushehr": ("بوشهر", "Bushehr"),
    "chaharmahal-bakhtiari": ("چهارمحال و بختیاری", "Chaharmahal and Bakhtiari"),
    "east-azerbaijan": ("آذربایجان شرقی", "East Azerbaijan"),
    "fars": ("فارس", "Fars"),
    "gilan": ("گیلان", "Gilan"),
    "golestan": ("گلستان", "Golestan"),
    "hamadan": ("همدان", "Hamadan"),
    "hormozgan": ("هرمزگان", "Hormozgan"),
    "ilam": ("ایلام", "Ilam"),
    "isfahan": ("اصفهان", "Isfahan"),
    "kerman": ("کرمان", "Kerman"),
    "kermanshah": ("کرمانشاه", "Kermanshah"),
    "khuzestan": ("خوزستان", "Khuzestan"),
    "kohgiluyeh-boyer-ahmad": ("کهگیلویه و بویراحمد", "Kohgiluyeh and Boyer-Ahmad"),
    "kordestan": ("کردستان", "Kurdistan"),
    "lorestan": ("لرستان", "Lorestan"),
    "markazi": ("مرکزی", "Markazi"),
    "mazandaran": ("مازندران", "Mazandaran"),
    "north-khorasan": ("خراسان شمالی", "North Khorasan"),
    "qazvin": ("قزوین", "Qazvin"),
    "qom": ("قم", "Qom"),
    "razavi-khorasan": ("خراسان رضوی", "Razavi Khorasan"),
    "semnan": ("سمنان", "Semnan"),
    "sistan-baluchestan": ("سیستان و بلوچستان", "Sistan and Baluchestan"),
    "south-khorasan": ("خراسان جنوبی", "South Khorasan"),
    "tehran": ("تهران", "Tehran"),
    "west-azerbaijan": ("آذربایجان غربی", "West Azerbaijan"),
    "yazd": ("یزد", "Yazd"),
    "zanjan": ("زنجان", "Zanjan"),
}


def normalize(value: str) -> str:
    value = unicodedata.normalize("NFKC", value or "")
    value = value.replace("ي", "ی").replace("ك", "ک").replace("‌", " ")
    value = re.sub(r"\bprovince\b", "", value, flags=re.IGNORECASE)
    value = value.replace("استان", "")
    value = value.replace("&", " and ").replace("–", "-").replace("—", "-")
    value = re.sub(r"[^\w\u0600-\u06ff]+", " ", value.lower(), flags=re.UNICODE)
    return " ".join(value.split())


def feature_names(feature: dict) -> set[str]:
    props = feature.get("properties") or {}
    values = {
        str(props.get("name", "")),
        str(props.get("name:fa", "")),
        str(props.get("name:en", "")),
        str(props.get("NAME_1", "")),
    }
    return {normalize(value) for value in values if value}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--province-id", choices=sorted(PROVINCES), required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    document = json.loads(args.input.read_text(encoding="utf-8"))
    features = document.get("features") or []
    if len(features) < 31:
        raise SystemExit(f"province boundary source is incomplete: {len(features)} features")

    fa_name, en_name = PROVINCES[args.province_id]
    aliases = {
        normalize(args.province_id.replace("-", " ")),
        normalize(fa_name),
        normalize(en_name),
        normalize(en_name.replace("Kurdistan", "Kordestan")),
        normalize(en_name.replace("Boyer-Ahmad", "Boyer Ahmad")),
        normalize(en_name.replace("Baluchestan", "Baluchistan")),
    }
    matches = [feature for feature in features if feature_names(feature) & aliases]
    if len(matches) != 1:
        available = sorted({name for feature in features for name in feature_names(feature) if name})
        raise SystemExit(
            f"expected exactly one boundary for {args.province_id}, found {len(matches)}; "
            f"known names={available}"
        )

    feature = matches[0]
    geometry_type = (feature.get("geometry") or {}).get("type")
    if geometry_type not in {"Polygon", "MultiPolygon"}:
        raise SystemExit(f"unsupported province geometry: {geometry_type}")

    output = {
        "type": "FeatureCollection",
        "features": [
            {
                "type": "Feature",
                "properties": {
                    "province_id": args.province_id,
                    "name:fa": fa_name,
                    "name:en": en_name,
                },
                "geometry": feature["geometry"],
            }
        ],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False), encoding="utf-8")
    print(f"prepared boundary for {args.province_id}: {fa_name} / {en_name}")


if __name__ == "__main__":
    main()
