from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parent
FACE_ROOT = ROOT / "green-expression-assets-v3"
LIGHT_BASE = Path(
    r"D:\Administrator\Documents\MASON\design-exploration\faceted-stone-screen"
    r"\home-cement-gray-correct-design-front-left-three-quarter-final-v1.png"
)
DARK_BASE = (
    Path(r"D:\CodexWork\MASON\outputs\design-exploration\faceted-stone-screen")
    / "mason-front-left-three-quarter-dark-v1-20260730-101318-1.png"
)

VIRTUAL_SCREEN = (244, 239)
FACE_WIDTH = 120
LIGHT_SCREEN = [(443.0, 449.0), (725.0, 449.0), (720.0, 755.0), (441.0, 755.0)]
DARK_SCREEN = [(448.0, 484.0), (730.0, 486.0), (727.0, 795.0), (449.0, 793.0)]

CROP = (80, 180, 950, 1050)
PANEL_SIZE = (480, 480)
PAIR_SIZE = (PANEL_SIZE[0] * 2, PANEL_SIZE[1])

EXPRESSIONS = [
    (
        "tilted-mouth",
        [[(0, 0), (0, 0), (0, 0)], [(0, 0), (0, 0), (4, 0)], [(0, 0), (0, 0), (8, 1)], [(0, 0), (0, 0), (4, 0)], [(0, 0), (0, 0), (0, 0)]],
        [1800, 160, 420, 160, 1700],
    ),
    (
        "x-eyes-surprised",
        [[(0, 0), (0, 0), (0, 0)], [(0, -5), (0, -5), (0, -3)], [(0, -10), (0, -10), (0, -6)], [(0, -4), (0, -4), (0, -2)], [(0, 0), (0, 0), (0, 0)]],
        [1700, 110, 230, 160, 1900],
    ),
    (
        "square-eye-smile",
        [[(0, 0), (0, 0), (0, 0)], [(0, 0), (0, 0), (0, -4)], [(0, 0), (0, 0), (0, -8)], [(0, 0), (0, 0), (0, -4)], [(0, 0), (0, 0), (0, 0)]],
        [1600, 150, 460, 150, 1700],
    ),
    (
        "frown",
        [[(0, 0), (0, 0), (0, 0)], [(0, 0), (0, 0), (0, 3)], [(0, 0), (0, 0), (0, 6)], [(0, 0), (0, 0), (0, 3)], [(0, 0), (0, 0), (0, 0)]],
        [1700, 400, 600, 400, 1700],
    ),
    (
        "question-marks",
        [[(0, 0), (0, 0), (0, 0)], [(0, -7), (0, 0), (0, 0)], [(0, -11), (0, 0), (0, 0)], [(0, -7), (0, 0), (0, 0)], [(0, 0), (0, 0), (0, 0)]],
        [1600, 140, 480, 140, 1800],
    ),
]


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


def clear_screen(image: Image.Image, quad: list[tuple[float, float]]) -> Image.Image:
    result = image.convert("RGBA")
    draw = ImageDraw.Draw(result)
    draw.polygon([(round(x), round(y)) for x, y in quad], fill=(2, 2, 3, 255))
    return result


def animated_face(name: str, translations: list[tuple[int, int]]) -> Image.Image:
    source_name = "x-eyes" if name == "x-eyes-surprised" else name
    full = Image.open(FACE_ROOT / f"{source_name}-full.png").convert("RGBA")
    face = Image.new("RGBA", full.size, (0, 0, 0, 0))
    for index, (offset_x, offset_y) in enumerate(translations):
        part = Image.open(FACE_ROOT / f"{source_name}-part-{index}.png").convert("RGBA")
        face.alpha_composite(part, (offset_x, offset_y))
    return face


def project_face(
    base: Image.Image,
    face: Image.Image,
    quad: list[tuple[float, float]],
) -> Image.Image:
    virtual = Image.new("RGBA", VIRTUAL_SCREEN, (0, 0, 0, 0))
    face_height = round(face.height * FACE_WIDTH / face.width)
    face = face.resize((FACE_WIDTH, face_height), Image.Resampling.LANCZOS)
    virtual.alpha_composite(
        face,
        (
            (VIRTUAL_SCREEN[0] - face.width) // 2,
            (VIRTUAL_SCREEN[1] - face.height) // 2 - 1,
        ),
    )
    source = [
        (0.0, 0.0),
        (VIRTUAL_SCREEN[0] - 1.0, 0.0),
        (VIRTUAL_SCREEN[0] - 1.0, VIRTUAL_SCREEN[1] - 1.0),
        (0.0, VIRTUAL_SCREEN[1] - 1.0),
    ]
    projected = virtual.transform(
        base.size,
        Image.Transform.PERSPECTIVE,
        perspective_coefficients(quad, source),
        resample=Image.Resampling.BICUBIC,
        fillcolor=(0, 0, 0, 0),
    )
    result = clear_screen(base, quad)
    result.alpha_composite(projected)
    return result


def panel(image: Image.Image) -> Image.Image:
    return image.crop(CROP).resize(PANEL_SIZE, Image.Resampling.LANCZOS).convert("RGB")


def paired_frame(name: str, translations: list[tuple[int, int]]) -> Image.Image:
    face = animated_face(name, translations)
    light = panel(project_face(Image.open(LIGHT_BASE), face, LIGHT_SCREEN))
    dark = panel(project_face(Image.open(DARK_BASE), face, DARK_SCREEN))
    pair = Image.new("RGB", PAIR_SIZE, "black")
    pair.paste(light, (0, 0))
    pair.paste(dark, (PANEL_SIZE[0], 0))
    return pair


def save_gif(name: str, frames: list[Image.Image], durations: list[int]) -> Path:
    output = ROOT / f"mason-stone-light-dark-{name}-motion-v1.gif"
    paletted = [frame.convert("P", palette=Image.Palette.ADAPTIVE, colors=256) for frame in frames]
    paletted[0].save(
        output,
        save_all=True,
        append_images=paletted[1:],
        duration=durations,
        loop=0,
        disposal=2,
        optimize=True,
    )
    return output


def main() -> None:
    peak_frames: list[Image.Image] = []
    for name, motions, durations in EXPRESSIONS:
        frames = [paired_frame(name, translations) for translations in motions]
        output = save_gif(name, frames, durations)
        peak_frames.append(frames[len(frames) // 2])
        print(f"saved={output}")

    proof = Image.new("RGB", (PAIR_SIZE[0], PAIR_SIZE[1] * len(peak_frames)), (18, 18, 18))
    for index, frame in enumerate(peak_frames):
        proof.paste(frame, (0, index * PAIR_SIZE[1]))
    proof_path = ROOT / "mason-stone-light-dark-expression-proof-v1.png"
    proof.save(proof_path, optimize=True)
    print(f"saved={proof_path}")


if __name__ == "__main__":
    main()
