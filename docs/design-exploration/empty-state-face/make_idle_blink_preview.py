from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image


SOURCE_ROOT = Path(r"D:\Administrator\Documents\MASON\design-exploration\faceted-stone-screen")
FACE_REFERENCE = Path(
    r"C:\Users\Administrator\AppData\Local\Temp\codex-clipboard-cb3cb67c-906c-4132-bb12-c8613780dabd.png"
)
BASE = SOURCE_ROOT / "home-faceted-cement-gray-pixel-locked-v5.png"
OUTPUT = Path(__file__).with_name("mason-empty-state-idle-blink-preview-v1.gif")

SCREEN_BOX = (394, 622, 638, 861)
FACE_SOURCE_BOX = (294, 211, 350, 270)
FACE_WIDTH = 120
FACE_COLOR = (255, 248, 214, 255)


def alpha_from_reference() -> np.ndarray:
    image = np.asarray(Image.open(FACE_REFERENCE).convert("RGB"), dtype=np.float32)
    left, top, right, bottom = FACE_SOURCE_BOX
    crop = image[top:bottom, left:right]
    luma = 0.2126 * crop[:, :, 0] + 0.7152 * crop[:, :, 1] + 0.0722 * crop[:, :, 2]
    alpha = np.uint8(np.clip((245.0 - luma) / 225.0 * 255.0, 0.0, 255.0))
    alpha[alpha < 10] = 0
    return alpha


def components(alpha: np.ndarray) -> list[tuple[int, int, int, int]]:
    visited = np.zeros(alpha.shape, dtype=bool)
    found: list[tuple[int, int, int, int]] = []
    height, width = alpha.shape
    for y in range(height):
        for x in range(width):
            if visited[y, x] or alpha[y, x] == 0:
                continue
            queue = deque([(x, y)])
            visited[y, x] = True
            pixels: list[tuple[int, int]] = []
            while queue:
                current_x, current_y = queue.popleft()
                pixels.append((current_x, current_y))
                for next_x, next_y in (
                    (current_x - 1, current_y),
                    (current_x + 1, current_y),
                    (current_x, current_y - 1),
                    (current_x, current_y + 1),
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
            found.append((min(xs), min(ys), max(xs) + 1, max(ys) + 1))
    return sorted(found, key=lambda bounds: bounds[1])


def colored_mask(alpha: np.ndarray) -> Image.Image:
    image = Image.new("RGBA", (alpha.shape[1], alpha.shape[0]), FACE_COLOR)
    image.putalpha(Image.fromarray(alpha, "L"))
    return image


def compose_face(face_alpha: np.ndarray, eye_scale: float) -> Image.Image:
    component_bounds = components(face_alpha)
    eyes = component_bounds[:2]
    mouth = component_bounds[2]
    face = Image.new("RGBA", (face_alpha.shape[1], face_alpha.shape[0]), (0, 0, 0, 0))

    for left, top, right, bottom in eyes:
        eye_alpha = face_alpha[top:bottom, left:right]
        eye = colored_mask(eye_alpha)
        scaled_height = max(2, round(eye.height * eye_scale))
        eye = eye.resize((eye.width, scaled_height), Image.Resampling.LANCZOS)
        # Keep the eyelid anchored to the original eye center while it closes.
        eye_top = round((top + bottom - scaled_height) / 2)
        face.alpha_composite(eye, (left, eye_top))

    left, top, right, bottom = mouth
    face.alpha_composite(colored_mask(face_alpha[top:bottom, left:right]), (left, top))
    return face


def build_frame(face: Image.Image) -> Image.Image:
    base = Image.open(BASE).convert("RGBA")
    left, top, right, bottom = SCREEN_BOX
    screen = Image.new("RGBA", (right - left, bottom - top), (3, 3, 4, 255))
    base.alpha_composite(screen, (left, top))
    face_height = round(face.height * FACE_WIDTH / face.width)
    face = face.resize((FACE_WIDTH, face_height), Image.Resampling.LANCZOS)
    face_left = round((left + right - face.width) / 2)
    face_top = round((top + bottom - face.height) / 2) - 1
    base.alpha_composite(face, (face_left, face_top))
    return base.convert("P", palette=Image.Palette.ADAPTIVE)


def main() -> None:
    face_alpha = alpha_from_reference()
    # The pause makes the animation read as a calm idle state; the short center
    # frames are the only visible motion.
    scales = [1.0, 1.0, 1.0, 0.62, 0.18, 0.62, 1.0, 1.0]
    durations = [1500, 1500, 1200, 90, 100, 90, 1200, 1200]
    frames = [build_frame(compose_face(face_alpha, scale)) for scale in scales]
    frames[0].save(
        OUTPUT,
        save_all=True,
        append_images=frames[1:],
        duration=durations,
        loop=0,
        disposal=2,
        optimize=False,
    )
    print(f"saved={OUTPUT}")
    print(f"frames={len(frames)}")
    print(f"duration_ms={sum(durations)}")


if __name__ == "__main__":
    main()
