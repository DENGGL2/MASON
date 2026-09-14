from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image


REFERENCE = Path(
    r"C:\Users\Administrator\AppData\Local\Temp\codex-clipboard-cb3cb67c-906c-4132-bb12-c8613780dabd.png"
)
BASE = Path(
    r"D:\Administrator\Documents\MASON\design-exploration\faceted-stone-screen"
    r"\home-faceted-cement-gray-pixel-locked-v5.png"
)
OUTPUT_ROOT = Path(__file__).parent
SCREEN_BOX = (394, 622, 638, 861)
SCREEN_PREVIEW_BOX = (365, 590, 668, 891)
FACE_COLOR = (255, 248, 214, 255)
FACE_WIDTH = 120


def raw_alpha(region: tuple[int, int, int, int]) -> np.ndarray:
    source = np.asarray(Image.open(REFERENCE).convert("RGB"), dtype=np.float32)
    left, top, right, bottom = region
    crop = source[top:bottom, left:right]
    luminance = 0.2126 * crop[:, :, 0] + 0.7152 * crop[:, :, 1] + 0.0722 * crop[:, :, 2]
    alpha = np.uint8(np.clip((245.0 - luminance) / 225.0 * 255.0, 0.0, 255.0))
    alpha[alpha < 10] = 0
    ys, xs = np.nonzero(alpha)
    if len(xs) == 0:
        raise ValueError(f"No expression pixels in {region}")
    # Regions intentionally contain generous safety space. This trims only white
    # paper around the original black glyphs, so no face stroke can be clipped.
    padding = 4
    left = max(int(xs.min()) - padding, 0)
    top = max(int(ys.min()) - padding, 0)
    right = min(int(xs.max()) + padding + 1, alpha.shape[1])
    bottom = min(int(ys.max()) + padding + 1, alpha.shape[0])
    return alpha[top:bottom, left:right]


def component_boxes(alpha: np.ndarray) -> list[tuple[int, int, int, int]]:
    visited = np.zeros(alpha.shape, dtype=bool)
    result: list[tuple[int, int, int, int]] = []
    height, width = alpha.shape
    for y in range(height):
        for x in range(width):
            if visited[y, x] or alpha[y, x] == 0:
                continue
            queue = deque([(x, y)])
            visited[y, x] = True
            pixels: list[tuple[int, int]] = []
            while queue:
                point_x, point_y = queue.popleft()
                pixels.append((point_x, point_y))
                for next_x, next_y in (
                    (point_x - 1, point_y),
                    (point_x + 1, point_y),
                    (point_x, point_y - 1),
                    (point_x, point_y + 1),
                ):
                    if (
                        0 <= next_x < width
                        and 0 <= next_y < height
                        and not visited[next_y, next_x]
                        and alpha[next_y, next_x] > 0
                    ):
                        visited[next_y, next_x] = True
                        queue.append((next_x, next_y))
            xs, ys = zip(*pixels)
            result.append((min(xs), min(ys), max(xs) + 1, max(ys) + 1))
    return sorted(result, key=lambda box: (box[1], box[0]))


def image_from_alpha(alpha: np.ndarray) -> Image.Image:
    image = Image.new("RGBA", (alpha.shape[1], alpha.shape[0]), FACE_COLOR)
    image.putalpha(Image.fromarray(alpha, "L"))
    return image


def animated_face(alpha: np.ndarray, translations: list[tuple[int, int]]) -> Image.Image:
    boxes = component_boxes(alpha)
    if len(boxes) != len(translations):
        raise ValueError(f"Expected {len(translations)} components, received {len(boxes)}")
    pad = 12
    canvas = Image.new("RGBA", (alpha.shape[1] + pad * 2, alpha.shape[0] + pad * 2), (0, 0, 0, 0))
    for (left, top, right, bottom), (offset_x, offset_y) in zip(boxes, translations):
        part = image_from_alpha(alpha[top:bottom, left:right])
        canvas.alpha_composite(part, (left + pad + offset_x, top + pad + offset_y))
    return canvas


def compose_screen_preview(face: Image.Image) -> Image.Image:
    canvas = Image.open(BASE).convert("RGBA")
    left, top, right, bottom = SCREEN_BOX
    canvas.alpha_composite(
        Image.new("RGBA", (right - left, bottom - top), (3, 3, 4, 255)), (left, top)
    )
    scaled_height = round(face.height * FACE_WIDTH / face.width)
    face = face.resize((FACE_WIDTH, scaled_height), Image.Resampling.LANCZOS)
    face_left = round((left + right - face.width) / 2)
    face_top = round((top + bottom - face.height) / 2) - 1
    canvas.alpha_composite(face, (face_left, face_top))
    return canvas.crop(SCREEN_PREVIEW_BOX).resize((720, 714), Image.Resampling.NEAREST).convert(
        "P", palette=Image.Palette.ADAPTIVE
    )


def save_preview(name: str, alpha: np.ndarray, motions: list[list[tuple[int, int]]], durations: list[int]) -> None:
    frames = [compose_screen_preview(animated_face(alpha, positions)) for positions in motions]
    output = OUTPUT_ROOT / f"mason-empty-state-{name}-motion-preview-v2.gif"
    frames[0].save(
        output,
        save_all=True,
        append_images=frames[1:],
        duration=durations,
        loop=0,
        disposal=2,
        optimize=False,
    )
    print(f"saved={output}")


def main() -> None:
    expressions = [
        # Both eyes stay still while the slanted mouth makes a readable sideward nudge.
        ("tilted-mouth", (80, 195, 170, 286), [[(0, 0), (0, 0), (0, 0)], [(0, 0), (0, 0), (4, 0)], [(0, 0), (0, 0), (8, 1)], [(0, 0), (0, 0), (4, 0)], [(0, 0), (0, 0), (0, 0)]], [1800, 160, 420, 160, 1700]),
        # X eyes and the O mouth perform one contained double-take, then settle.
        ("x-eyes-surprised", (380, 195, 470, 286), [[(0, 0), (0, 0), (0, 0)], [(0, -5), (0, -5), (0, -3)], [(0, -10), (0, -10), (0, -6)], [(0, -4), (0, -4), (0, -2)], [(0, 0), (0, 0), (0, 0)]], [1700, 110, 230, 160, 1900]),
        # The smile's mouth rises once, like a restrained acknowledgement.
        ("square-eye-smile", (280, 300, 370, 394), [[(0, 0), (0, 0), (0, 0)], [(0, 0), (0, 0), (0, -4)], [(0, 0), (0, 0), (0, -8)], [(0, 0), (0, 0), (0, -4)], [(0, 0), (0, 0), (0, 0)]], [1600, 150, 460, 150, 1700]),
        # The frown breathes downward only; it never bounces like a notification.
        ("frown", (176, 505, 274, 610), [[(0, 0), (0, 0), (0, 0)], [(0, 0), (0, 0), (0, 3)], [(0, 0), (0, 0), (0, 6)], [(0, 0), (0, 0), (0, 3)], [(0, 0), (0, 0), (0, 0)]], [1700, 400, 600, 400, 1700]),
        # The reference joins the punctuation stems into one shape; its two dots remain anchored.
        ("question-marks", (370, 505, 490, 610), [[(0, 0), (0, 0), (0, 0)], [(0, -7), (0, 0), (0, 0)], [(0, -11), (0, 0), (0, 0)], [(0, -7), (0, 0), (0, 0)], [(0, 0), (0, 0), (0, 0)]], [1600, 140, 480, 140, 1800]),
    ]
    for name, region, motions, durations in expressions:
        save_preview(name, raw_alpha(region), motions, durations)


if __name__ == "__main__":
    main()
