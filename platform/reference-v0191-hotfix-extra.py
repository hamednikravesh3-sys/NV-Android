from pathlib import Path

# Keep every UI renderer exhaustive after adding the METRO category.
p = Path('app/src/main/java/ir/nv/navigation/ui/NvReferenceV14.kt')
s = p.read_text(encoding='utf-8')
old = '    NearbyCategory.EV -> Icons.Rounded.EvStation\n    NearbyCategory.PARKS -> Icons.Rounded.Park'
new = '    NearbyCategory.EV -> Icons.Rounded.EvStation\n    NearbyCategory.METRO -> Icons.Rounded.Subway\n    NearbyCategory.PARKS -> Icons.Rounded.Park'
if old not in s:
    raise RuntimeError('NearbyCategory icon insertion point not found')
p.write_text(s.replace(old, new), encoding='utf-8')

# DeviceLocationProvider now collects for up to 20 seconds in precision-first mode.
# Do not cancel it at 12 seconds from the ViewModel; give it enough time to obtain
# a <=8 m fix instead of falling back to a weak/approximate coordinate.
vm = Path('app/src/main/java/ir/nv/navigation/ui/NvViewModel.kt')
text = vm.read_text(encoding='utf-8')
needle = 'withTimeoutOrNull(12_000L) { locationProvider.currentLocation() }'
count = text.count(needle)
if count != 2:
    raise RuntimeError(f'Expected exactly 2 current-location timeouts, found {count}')
text = text.replace(needle, 'withTimeoutOrNull(22_000L) { locationProvider.currentLocation() }')
vm.write_text(text, encoding='utf-8')

print('v0.19.1 metro exhaustiveness and precision timeout fixes applied')
