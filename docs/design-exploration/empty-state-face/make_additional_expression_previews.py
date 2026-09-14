from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image


SOURCE_ROOT = Path(r"D:\Administrator\Documents\MASON\design-exploration\faceted-stone-screen")
REFERENCE = Path(
    r"C:\Users\Administrator\AppData\Local\Temp\codex-clipboard-cb3cb67c-906c-4132-bb12-c8613780dabd.png"
)
BASE = SOURCE_ROOT / "home-faceted-cement-gray-pixel-locked-v5.png"
OUTPUT_ROOT = Path(__file__).parent

SCREEN_BOX = (394, 622, 638, 861)
FACE_WIDTH = 120
FACE_COLOR = (255, 248, 214, 255)


def source_alpha(box: tuple[int, int, int, int]) -> np.ndarray:
    image = np.asarray(Image.open(REFERENCE).convert("RGB"), dtype=np.float32)
    left, top, right, bottom = box
    crop = image[top:bottom, left:right]
    luma = 0.2126 * crop[:, :, 0] + 0.7152 * crop[:, :, 1] + 0.0722 * crop[:, :, 2]
    alpha = np.uint8(np.clip((245.0 - luma) / 225.0 * 255.0, 0.0, 255.0))
    alpha[alpha < 10] = 0
    return alpha


def components(alpha: np.ndarray) -> list[tuple[int, int, int, int]]:
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
    return sorted(result, key=lambda bounds: bounds[1])


def image_from_alpha(alpha: np.ndarray) -> Image.Image:
    image = Image.new("RGBA", (alpha.shape[1], alpha.shape[0]), FACE_COLOR)
    image.putalpha(Image.fromarray(alpha, "L"))
    return image


def smiling_face(alpha: np.ndarray, eye_scale: float) -> Image.Image:
    parts = components(alpha)
    eyes, mouth = parts[:2], parts[2]
    result = Image.new("RGBA", (alpha.shape[1], alpha.shape[0]), (0, 0, 0, 0))
    for left, top, right, bottom in eyes:
        eye = image_from_alpha(alpha[top:bottom, left:right])
        height = max(2, round(eye.height * eye_scale))
        eye = eye.resize((eye.width, height), Image.Resampling.LANCZOS)
        result.alpha_composite(eye, (left, round((top + bottom - height) / 2)))
    left, top, right, bottom = mouth
    result.alpha_composite(image_from_alpha(alpha[top:bottom, left:right]), (left, top))
    return result


def compose_frame(face: Image.Image, offset_x: int = 0, offset_y: int = 0) -> Image.Image:
    canvas = Image.open(BASE).convert("RGBA")
    left, top, right, bottom = SCREEN_BOX
    canvas.alpha_composite(
        Image.new("RGBA", (right - left, bottom - top), (3, 3, 4, 255)), (left, top)
    )
    height = round(face.height * FACE_WIDTH / face.width)
    face = face.resize((FACE_WIDTH, height), Image.Resampling.LANCZOS)
    face_left = round((left + right - face.width) / 2) + offset_x
    face_top = round((top + bottom - face.height) / 2) - 1 + offset_y
    canvas.alpha_composite(face, (face_left, face_top))
    return canvas.convert("P", palette=Image.Palette.ADAPTIVE)


def save_animation(name: str, frames: list[Image.Image], durations: list[int]) -> None:
    output = OUTPUT_ROOT / name
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
        (
            "tilted-mouth",
            (94, 211, 154, 270),
            [(0, 0), (0, 0), (-2, 0), (-3, 1), (-2, 0), (0, 0), (0, 0)],
            [1600, 1200, 130, 180, 130, 1200, 1300],
        ),
        (
            "x-eyes-surprised",
            (394, 211, 454, 270),
            [(0, 0), (0, 0), (0, -2), (0, -4), (0, -2), (0, 0), (0, 0)],
            [1700, 1000, 100, 180, 100, 1100, 1500],
        ),
        (
            "square-eye-smile",
            (294, 316, 354, 377),
            [(0, 0), (0, 0), (0, -1), (0, -2), (0, -1), (0, 0), (0, 0)],
            [1500, 1300, 220, 350, 220, 1200, 1200],
        ),
        (
            "frown",
            (194, 526, 254, 585),
            [(0, 0), (0, 0), (0, 1), (0, 2), (0, 1), (0, 0), (0, 0)],
            [1600, 1400, 250, 500, 250, 1100, 1100],
        ),
        (
            "question-marks",
            (394, 526, 454, 585),
            [(0, 0), (0, 0), (1, -1), (2, -2), (1, -1), (0, 0), (0, 0)],
            [1700, 1200, 140, 320, 140, 1200, 1200],
        ),
    ]
    for name, box, offsets, durations in expressions:
        face = image_from_alpha(source_alpha(box))
        frames = [compose_frame(face, offset_x, offset_y) for offset_x, offset_y in offsets]
        save_animation(f"mason-empty-state-{name}-preview-v1.gif", frames, durations)


if __name__ == "__main__":
    main()
