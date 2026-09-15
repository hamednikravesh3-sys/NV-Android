from pathlib import Path

path = Path('app/src/main/java/ir/nv/navigation/map/OnlineIranMap.kt')
text = path.read_text(encoding='utf-8')
old = '''        if (!navigationActive && !mustRecenter) {
            val cameraTarget = readyMap.cameraPosition.target
            val cameraCoordinate = Coordinate(cameraTarget.latitude, cameraTarget.longitude)
            if (coordinateDistanceMeters(cameraCoordinate, location) < HOME_CAMERA_JITTER_METERS) return
        }
'''
new = '''        if (!navigationActive && !mustRecenter) {
            val cameraTarget = readyMap.cameraPosition.target
            if (cameraTarget != null) {
                val cameraCoordinate = Coordinate(cameraTarget.latitude, cameraTarget.longitude)
                if (coordinateDistanceMeters(cameraCoordinate, location) < HOME_CAMERA_JITTER_METERS) return
            }
        }
'''
if old not in text:
    raise SystemExit('expected nullable camera block not found')
path.write_text(text.replace(old, new, 1), encoding='utf-8')
print('nullable MapLibre camera target fixed')
