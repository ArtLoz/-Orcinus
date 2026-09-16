"""Compares G-code sliced on Android with the official desktop OrcaSlicer build.

prepare  Flattens the selected system profiles for the desktop command line and
         writes the test models from OrcaSlicer's test data, centred on the bed.
compare  Checks each pair of G-code files against the acceptance criteria and
         prints a report. Exits with 1 when a criterion fails.

Standard library only; scripts/golden.ps1 runs both commands.
"""

import argparse
import json
import re
import struct
import sys
from collections import Counter
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
PROFILES = REPO / 'upstream/OrcaSlicer/resources/profiles'
TEST_DATA = REPO / 'upstream/OrcaSlicer/tests/data'

# Results may differ in the last bits of floating-point math (the Windows and
# Android libm disagree on atan2, sin, cos) and in the order of equally good
# choices. Observed maxima across the cases: 0.24 % extrusion in one layer,
# 0.022 % total filament, 2 s of print time.
LAYER_EXTRUSION_TOLERANCE = 0.01
FILAMENT_TOLERANCE = 0.001
PRINT_TIME_TOLERANCE = 0.01

# Config differences that come from the desktop command line, not from slicing.
CONFIG_VALUE_DIFFERENCES = {
    'filament_colour': 'the command line assigns its default colour to loaded filaments',
    'extruder_colour': 'follows filament_colour',
    'flush_volumes_matrix': 'the command line sizes the purge table for several filaments',
    'flush_volumes_vector': 'the command line sizes the purge table for several filaments',
}
DESKTOP_ONLY_KEYS = {'compatible_printers_condition', 'different_settings_to_system', 'inherits_group'}
ANDROID_ONLY_KEYS = {
    'bbl_use_printhost', 'default_bed_type', 'default_nozzle_volume_type', 'extruder_variant_list',
    'filament_colour_type', 'filament_multi_colour', 'flashforge_serial_number', 'ironing_expansion',
    'pellet_flow_coefficient', 'pellet_modded_printer', 'printhost_authorization_type',
    'printhost_ssl_ignore_revoke', 'upward_compatible_machine',
}

FLOAT32 = struct.Struct('<f')


def f32(value):
    return FLOAT32.unpack(FLOAT32.pack(value))[0]


def load_cases(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


# --- prepare -----------------------------------------------------------------

def flatten_profile(kind, name, vendors):
    """Resolves the inherits chain of a system profile, as PresetBundle does."""
    index = {}
    for vendor in vendors:
        for file in sorted((PROFILES / vendor / kind).rglob('*.json')):
            profile = json.loads(file.read_text(encoding='utf-8'))
            index.setdefault(profile['name'], profile)
    chain = []
    current = name
    while current:
        if current not in index:
            raise SystemExit(f'{kind} profile "{current}" not found in {vendors}')
        chain.append(index[current])
        current = index[current].get('inherits')
    flat = {}
    for profile in reversed(chain):
        flat.update(profile)
    flat.update({'name': name, 'from': 'system', 'inherits': '', 'instantiation': 'true'})
    return flat


def read_triangles(path):
    """Returns triangles as float32 vertex tuples from an STL or OBJ file."""
    data = path.read_bytes()
    if path.suffix.lower() == '.obj':
        vertices, triangles = [], []
        for line in data.decode('utf-8', 'replace').splitlines():
            parts = line.split()
            if parts and parts[0] == 'v':
                vertices.append(tuple(f32(float(c)) for c in parts[1:4]))
            elif parts and parts[0] == 'f':
                ids = [int(p.split('/')[0]) for p in parts[1:]]
                ids = [i - 1 if i > 0 else len(vertices) + i for i in ids]
                triangles += [(vertices[ids[0]], vertices[ids[k]], vertices[ids[k + 1]]) for k in range(1, len(ids) - 1)]
        return triangles
    if data[:5] == b'solid' and b'facet' in data[:512]:
        vertices = [tuple(f32(float(c)) for c in line.split()[1:4])
                    for line in data.decode('ascii', 'replace').splitlines() if line.split()[:1] == ['vertex']]
        return [tuple(vertices[i:i + 3]) for i in range(0, len(vertices), 3)]
    count = struct.unpack_from('<I', data, 80)[0]
    triangles = []
    for i in range(count):
        values = struct.unpack_from('<12f', data, 84 + i * 50)
        triangles.append((values[3:6], values[6:9], values[9:12]))
    return triangles


def write_placed_model(source, destination, bed_center):
    """Writes the model with its bounding box centred on the bed and resting on it.

    The app places a model like the desktop GUI: the mesh is centred around the
    origin and the instance stands on the bed centre. The desktop command line
    cannot set an instance offset, so the mesh itself is moved here. When the
    bounding box centre is exactly the bed centre, the app's two translations
    cancel exactly and both sides slice identical coordinates.
    """
    triangles = read_triangles(source)
    points = [p for t in triangles for p in t]
    low = [min(p[k] for p in points) for k in range(3)]
    high = [max(p[k] for p in points) for k in range(3)]
    shift = (bed_center[0] - (low[0] + high[0]) / 2, bed_center[1] - (low[1] + high[1]) / 2, -low[2])
    placed = [tuple(tuple(f32(p[k] + shift[k]) for k in range(3)) for p in t) for t in triangles]
    points = [p for t in placed for p in t]
    low = [min(p[k] for p in points) for k in range(3)]
    high = [max(p[k] for p in points) for k in range(3)]
    if ((low[0] + high[0]) / 2, (low[1] + high[1]) / 2, low[2]) != (bed_center[0], bed_center[1], 0.0):
        raise SystemExit(f'{source.name}: float32 rounding prevents exact placement on the bed centre')
    # ASCII with 9 significant digits keeps float32 exactly. Binary STL is not
    # used: admesh reads a small binary file near 175 mm as ASCII, because it
    # detects binary files by bytes above 127.
    lines = [f'solid {destination.stem}']
    for triangle in placed:
        lines += ['facet normal 0 0 0', 'outer loop']
        lines += ['vertex %.9g %.9g %.9g' % vertex for vertex in triangle]
        lines += ['endloop', 'endfacet']
    lines.append(f'endsolid {destination.stem}')
    destination.write_text('\n'.join(lines) + '\n', encoding='ascii', newline='\n')


def prepare(args):
    cases = load_cases(args.cases)
    out = Path(args.out)
    (out / 'profiles').mkdir(parents=True, exist_ok=True)
    (out / 'models').mkdir(parents=True, exist_ok=True)
    profiles = cases['profiles']
    flat = {}
    for kind, key in (('machine', 'printer'), ('process', 'process'), ('filament', 'filament')):
        flat[kind] = flatten_profile(kind, profiles[key], profiles['vendors'])
        (out / 'profiles' / f'{kind}.json').write_text(json.dumps(flat[kind], ensure_ascii=False, indent=2), encoding='utf-8')
    area = [tuple(float(c) for c in p.split('x')) for p in flat['machine']['printable_area']]
    bed_center = ((min(p[0] for p in area) + max(p[0] for p in area)) / 2,
                  (min(p[1] for p in area) + max(p[1] for p in area)) / 2)
    for name, relative in cases['models'].items():
        write_placed_model(TEST_DATA / relative, out / 'models' / f'{name}.stl', bed_center)
    print(f'prepared {len(cases["models"])} models and 3 profiles in {out}')


# --- compare -----------------------------------------------------------------

DATE = re.compile(r' on \d{4}-\d{1,2}-\d{1,2} at \d{1,2}:\d{1,2}:\d{1,2}')


def parse_gcode(path):
    config, layers, stats = {}, [], {}
    header = {'lines': [], 'segments': Counter(), 'types': set(), 'extrusion': 0.0}
    layer = header
    in_config = False
    x = y = None
    for raw in path.read_text(encoding='utf-8', errors='replace').split('\n'):
        if raw.startswith('; CONFIG_BLOCK_START'):
            in_config = True
            continue
        if raw.startswith('; CONFIG_BLOCK_END'):
            in_config = False
            continue
        if in_config:
            match = re.match(r'; (\S+) = (.*)', raw)
            if match:
                config[match.group(1)] = match.group(2)
            continue
        if raw.startswith(';LAYER_CHANGE'):
            layer = {'lines': [], 'segments': Counter(), 'types': set(), 'extrusion': 0.0}
            layers.append(layer)
        elif raw.startswith(';Z:'):
            layer['z'] = raw[3:]
        elif raw.startswith(';HEIGHT:'):
            layer['height'] = raw[8:]
        elif raw.startswith(';TYPE:'):
            layer['types'].add(raw[6:])
        elif raw.startswith('; filament used [mm] = '):
            stats['filament'] = float(raw.split('=')[1])
        elif raw.startswith('; estimated printing time (normal mode) = '):
            stats['time'] = sum(int(n) * {'d': 86400, 'h': 3600, 'm': 60, 's': 1}[u]
                                for n, u in re.findall(r'(\d+)([dhms])', raw.split('=')[1]))
        layer['lines'].append(DATE.sub(' on <date>', raw))
        words = raw.split(';')[0].split()
        if not words or words[0] not in ('G0', 'G1', 'G2', 'G3'):
            continue
        values = {w[0]: float(w[1:]) for w in words[1:] if w[0] in 'XYE' and len(w) > 1}
        nx, ny = values.get('X', x), values.get('Y', y)
        extrusion = values.get('E', 0.0)
        # Printing moves only: retractions and their restores follow travel
        # choices, which may differ by one per layer.
        if extrusion > 0 and ('X' in values or 'Y' in values):
            layer['extrusion'] += extrusion
            if words[0] == 'G1' and x is not None:
                a, b = (x, y), (nx, ny)
                layer['segments'][(min(a, b), max(a, b), extrusion)] += 1
        x, y = nx, ny
    return {'config': config, 'header': header, 'layers': layers, 'stats': stats}


def relative_difference(a, b):
    return abs(a - b) / max(abs(b), 1e-9)


def compare_pair(android, desktop):
    failures = []
    ca, cd = android['config'], desktop['config']
    only_android = set(ca) - set(cd) - ANDROID_ONLY_KEYS
    only_desktop = set(cd) - set(ca) - DESKTOP_ONLY_KEYS
    if only_android:
        failures.append(f'config keys only on Android: {sorted(only_android)}')
    if only_desktop:
        failures.append(f'config keys only on desktop: {sorted(only_desktop)}')
    changed = sorted(k for k in set(ca) & set(cd) if ca[k] != cd[k] and k not in CONFIG_VALUE_DIFFERENCES)
    for key in changed:
        failures.append(f'config {key}: android={ca[key][:60]!r} desktop={cd[key][:60]!r}')

    la, ld = android['layers'], desktop['layers']
    if len(la) != len(ld):
        failures.append(f'layer count {len(la)} vs {len(ld)}')
    identical = reordered = different = 0
    worst = (0.0, None)
    for index, (a, d) in enumerate(zip(la, ld)):
        if (a.get('z'), a.get('height')) != (d.get('z'), d.get('height')):
            failures.append(f'layer {index}: Z {a.get("z")}/{a.get("height")} vs {d.get("z")}/{d.get("height")}')
        if a['types'] != d['types']:
            failures.append(f'layer {index}: path types {sorted(a["types"] ^ d["types"])} on one side only')
        deviation = relative_difference(a['extrusion'], d['extrusion'])
        if deviation > worst[0]:
            worst = (deviation, index)
        if a['lines'] == d['lines']:
            identical += 1
        elif a['segments'] == d['segments']:
            reordered += 1
        else:
            different += 1
    if worst[0] > LAYER_EXTRUSION_TOLERANCE:
        failures.append(f'layer {worst[1]}: extrusion differs by {worst[0]:.3%}')

    sa, sd = android['stats'], desktop['stats']
    filament = relative_difference(sa.get('filament', 0), sd.get('filament', 0))
    time = relative_difference(sa.get('time', 0), sd.get('time', 0))
    if filament > FILAMENT_TOLERANCE:
        failures.append(f'filament {sa.get("filament")} vs {sd.get("filament")} mm')
    if time > PRINT_TIME_TOLERANCE:
        failures.append(f'print time {sa.get("time")} vs {sd.get("time")} s')
    return {
        'layers': len(ld), 'identical': identical, 'reordered': reordered, 'different': different,
        'worst_layer': worst[0], 'filament': filament, 'time': (sa.get('time', 0) - sd.get('time', 0)),
        'failures': failures,
    }


def compare(args):
    cases = load_cases(args.cases)
    out = Path(args.out)
    names = args.models or list(cases['models'])
    print(f'{"model":16} {"layers":>6} {"same":>5} {"order":>5} {"other":>5} {"max layer E":>11} {"filament":>9} {"time":>6}  result')
    failed = False
    for name in names:
        android_path = out / 'android' / f'{name}.gcode'
        desktop_path = out / 'desktop' / name / 'plate_1.gcode'
        missing = [str(p) for p in (android_path, desktop_path) if not p.is_file()]
        if missing:
            print(f'{name:16} missing G-code: {", ".join(missing)}')
            failed = True
            continue
        result = compare_pair(parse_gcode(android_path), parse_gcode(desktop_path))
        status = 'ok' if not result['failures'] else 'FAIL'
        failed |= bool(result['failures'])
        print(f'{name:16} {result["layers"]:6} {result["identical"]:5} {result["reordered"]:5} {result["different"]:5} '
              f'{result["worst_layer"]:10.3%} {result["filament"]:8.4%} {result["time"]:+5}s  {status}')
        for failure in result['failures'][:20]:
            print(f'    {failure}')
    print('\nsame: identical text; order: same extrusion moves in another order; other: moves differ within tolerance')
    return 1 if failed else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('command', choices=('prepare', 'compare'))
    parser.add_argument('--cases', default=str(Path(__file__).with_name('cases.json')))
    parser.add_argument('--out', required=True)
    parser.add_argument('--models', nargs='*')
    args = parser.parse_args()
    if args.command == 'prepare':
        prepare(args)
        return 0
    return compare(args)


if __name__ == '__main__':
    sys.exit(main())
