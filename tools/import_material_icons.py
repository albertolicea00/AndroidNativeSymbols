"""Import the filled Material Icons SVG package as local Android vectors.

Usage: python3 tools/import_material_icons.py path/to/svg-0.14.15.tgz
Source: @material-design-icons/svg 0.14.15 (Apache-2.0).
"""

from pathlib import Path
import sys
import tarfile
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
DRAWABLES = ROOT / "app/src/main/res/drawable"
ASSETS = ROOT / "app/src/main/assets/vectors"
ANDROID = "http://schemas.android.com/apk/res/android"
SVG = "http://www.w3.org/2000/svg"


def shape_path(element):
    kind = element.tag.rsplit("}", 1)[-1]
    if kind == "path":
        return element.attrib["d"]
    cx, cy = element.attrib["cx"], element.attrib["cy"]
    rx = element.attrib["r"] if kind == "circle" else element.attrib["rx"]
    ry = element.attrib["r"] if kind == "circle" else element.attrib["ry"]
    left = float(cx) - float(rx)
    right = float(cx) + float(rx)
    return f"M{left:g},{cy} A{rx},{ry} 0 1,0 {right:g},{cy} A{rx},{ry} 0 1,0 {left:g},{cy} Z"


def convert(svg_bytes):
    svg = ET.fromstring(svg_bytes)
    if svg.attrib.get("viewBox") != "0 0 24 24":
        raise ValueError("Only 24 × 24 Material icons are supported")
    ET.register_namespace("android", ANDROID)
    vector = ET.Element("vector", {
        f"{{{ANDROID}}}width": "24dp",
        f"{{{ANDROID}}}height": "24dp",
        f"{{{ANDROID}}}viewportWidth": "24",
        f"{{{ANDROID}}}viewportHeight": "24",
    })
    for shape in svg:
        kind = shape.tag.rsplit("}", 1)[-1]
        if kind not in ("path", "circle", "ellipse"):
            raise ValueError(f"Unsupported SVG element: {kind}")
        attributes = {
            f"{{{ANDROID}}}fillColor": "#FF000000",
            f"{{{ANDROID}}}pathData": shape_path(shape),
        }
        if shape.attrib.get("fill-rule") == "evenodd":
            attributes[f"{{{ANDROID}}}fillType"] = "evenOdd"
        ET.SubElement(vector, "path", attributes)
    ET.indent(vector, space="    ")
    return '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(vector, encoding="unicode") + "\n"


def main(archive_path):
    count = 0
    with tarfile.open(archive_path, "r:gz") as archive:
        for member in archive:
            if not (member.name.startswith("package/filled/") and member.name.endswith(".svg")):
                continue
            name = Path(member.name).stem
            if not name.replace("_", "").isalnum() or name.lower() != name:
                raise ValueError(f"Invalid Android resource name: {name}")
            svg = archive.extractfile(member)
            if svg is None:
                raise ValueError(f"Missing SVG: {member.name}")
            xml = convert(svg.read())
            filename = f"ic_md_{name}.xml"
            (DRAWABLES / filename).write_text(xml, encoding="utf-8")
            (ASSETS / filename).write_text(xml, encoding="utf-8")
            count += 1
    print(f"Imported {count} filled Material Icons")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main(sys.argv[1])
