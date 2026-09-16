from pathlib import Path
p = Path('app/src/main/java/ir/nv/navigation/ui/NvReferenceV14.kt')
s = p.read_text(encoding='utf-8')
old = '    NearbyCategory.EV -> Icons.Rounded.EvStation\n    NearbyCategory.PARKS -> Icons.Rounded.Park'
new = '    NearbyCategory.EV -> Icons.Rounded.EvStation\n    NearbyCategory.METRO -> Icons.Rounded.Subway\n    NearbyCategory.PARKS -> Icons.Rounded.Park'
if old not in s:
    raise RuntimeError('NearbyCategory icon insertion point not found')
p.write_text(s.replace(old, new), encoding='utf-8')
print('v0.19.1 metro icon exhaustiveness fix applied')
