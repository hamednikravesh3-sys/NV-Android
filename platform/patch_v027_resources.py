#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'organicmaps')


def patch_strings(path: Path, replacements: dict[str, str]):
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n', encoding='utf-8')
    text = path.read_text(encoding='utf-8')
    for key, value in replacements.items():
        replacement = f'<string name="{key}">{value}</string>'
        pattern = re.compile(rf'<string\s+name="{re.escape(key)}"[^>]*>.*?</string>', re.S)
        if pattern.search(text):
            text = pattern.sub(replacement, text, count=1)
        else:
            text = text.replace('</resources>', f'    {replacement}\n</resources>', 1)
    path.write_text(text, encoding='utf-8')

# Remove temporary override files created by patch_v027.py. Android does not allow
# the same resource key twice within the same locale/source set.
for rel in [
    'android/libs/routing/src/main/res/values-fa/nv_strings.xml',
    'android/sdk/src/main/res/values-fa/nv_strings.xml',
    'android/app/src/main/res/values-fa/nv_strings.xml',
]:
    p = root / rel
    if p.exists():
        p.unlink()

patch_strings(root / 'android/libs/routing/src/main/res/values-fa/strings.xml', {
    'transit_not_found': 'مسیریابی مترو در این منطقه در دسترس نیست',
    'dialog_pedestrian_route_is_long_header': 'مسیر مترو پیدا نشد',
    'dialog_pedestrian_route_is_long_message': 'مبدا یا مقصد را به یک ایستگاه مترو نزدیک‌تر انتخاب کنید',
    'dialog_routing_check_gps': 'سیگنال GPS را بررسی کنید',
    'dialog_routing_cant_build_route': 'امکان ساخت مسیر وجود ندارد.',
    'dialog_routing_change_start_or_end': 'مبدا یا مقصد را اصلاح کنید.',
    'navigation_stop_button': 'توقف',
    'ok': 'تأیید',
})

patch_strings(root / 'android/sdk/src/main/res/values-fa/strings.xml', {
    'core_my_position': 'موقعیت من',
    'core_placepage_unknown_place': 'نقطه روی نقشه',
    'subway_data_unavailable': 'اطلاعات مترو در دسترس نیست',
    'm': 'متر',
    'km': 'کیلومتر',
})

patch_strings(root / 'android/app/src/main/res/values-fa/strings.xml', {
    'search': 'جستجو',
    'search_map': 'جستجو روی نقشه',
    'download': 'دانلود',
    'download_resources': 'برای شروع، نقشه کلی جهان را دانلود کنید.\\nاین فایل %s از حافظه را استفاده می‌کند.',
    'download_resources_continue': 'رفتن به نقشه',
    'download_country_ask': 'نقشه %s دانلود شود؟',
    'update_country_ask': 'نقشه %s به‌روزرسانی شود؟',
    'placepage_add_stop': 'افزودن توقف',
    'p2p_from_here': 'انتخاب مبدا',
    'p2p_to_here': 'انتخاب مقصد',
})

print('Persian resource overrides patched in-place')
