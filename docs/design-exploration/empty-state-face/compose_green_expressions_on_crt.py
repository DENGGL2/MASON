from pathlib import Path

import numpy as np
from PIL import Image


REFERENCE = Path(
    r"C:\Users\Administrator\AppData\Local\Temp\codex-clipboard-34fbcd3a-4a95-4ca5-b544-79d7606a942b.png"
)
ASSET_ROOT = Path(__file__).with_name("green-expression-assets-v3")
OUTPUT_ROOT = Path(__file__).parent


def perspective_coefficients(
    destination: list[tuple[float, float]],
    source: list[tuple[float, float]],
) -> tuple[float, ...]:
    rows: list[list[float]] = []
    values: list[float] = []
    for (x, y), (u, v) in zip(destination, source):
        rows.append([x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y])
        values.append(u)
        rows.append([0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y])
        values.append(v)
    return tuple(np.linalg.solve(np.asarray(rows), np.asarray(values)))


def project_face(board: Image.Image, face: Image.Image) -> Image.Image:
    virtual_width, virtual_height = 390, 405
    virtual = Image.new("RGBA", (virtual_width, virtual_height), (0, 0, 0, 0))
    face_width = 158
    face_height = round(face.height * face_width / face.width)
    face = face.resize((face_width, face_height), Image.Resampling.LANCZOS)
    virtual.alpha_composite(
        face,
        ((virtual_width - face.width) // 2, (virtual_height - face.height) // 2 - 5),
    )

    # Four points along the actual visible CRT glass, measured from the supplied photo.
    destination = [(429.0, 445.0), (787.0, 420.0), (815.0, 828.0), (439.0, 865.0)]
    source = [
        (0.0, 0.0),
        (virtual_width - 1.0, 0.0),
        (virtual_width - 1.0, virtual_height - 1.0),
        (0.0, virtual_height - 1.0),
    ]
    overlay = virtual.transform(
        board.size,
        Image.Transform.PERSPECTIVE,
        perspective_coefficients(destination, source),
        resample=Image.Resampling.BICUBIC,
        fillcolor=(0, 0, 0, 0),
    )
    result = board.copy()
    result.alpha_composite(overlay)
    return result


def main() -> None:
    expressions = [
        "tilted-mouth",
        "x-eyes",
        "square-eye-smile",
        "frown",
        "question-marks",
    ]
    reference = Image.open(REFERENCE).convert("RGBA")
    rendered: list[Image.Image] = []
    for name in expressions:
        face = Image.open(ASSET_ROOT / f"{name}-full.png").convert("RGBA")
        result = project_face(reference, face)
        result.save(OUTPUT_ROOT / f"crt-{name}-v1.png")
        rendered.append(result)
        print(f"saved=crt-{name}-v1.png")

    # A 3 by 2 board keeps every CRT large enough for proportion review.
    tile_width, tile_height = 360, 478
    contact = Image.new("RGB", (tile_width * 3, tile_height * 2), (232, 232, 229))
    for index, result in enumerate(rendered):
        tile = result.convert("RGB").resize((tile_width, tile_height), Image.Resampling.LANCZOS)
        contact.paste(tile, ((index % 3) * tile_width, (index // 3) * tile_height))
    contact.save(OUTPUT_ROOT / "crt-green-expression-contact-sheet-v1.png")
    print("saved=crt-green-expression-contact-sheet-v1.png")


if __name__ == "__main__":
    main()
