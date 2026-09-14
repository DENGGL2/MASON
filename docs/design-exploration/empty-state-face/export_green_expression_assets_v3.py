from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image


REFERENCE = Path(
    r"C:\Users\Administrator\AppData\Local\Temp\codex-clipboard-cb3cb67c-906c-4132-bb12-c8613780dabd.png"
)
OUTPUT_ROOT = Path(__file__).with_name("green-expression-assets-v3")


def alpha_from_region(region: tuple[int, int, int, int]) -> np.ndarray:
    image = np.asarray(Image.open(REFERENCE).convert("RGB"), dtype=np.float32)
    left, top, right, bottom = region
    crop = image[top:bottom, left:right]
    luma = 0.2126 * crop[:, :, 0] + 0.7152 * crop[:, :, 1] + 0.0722 * crop[:, :, 2]
    alpha = np.uint8(np.clip((245.0 - luma) / 225.0 * 255.0, 0.0, 255.0))
    alpha[alpha < 10] = 0
    ys, xs = np.nonzero(alpha)
    if len(xs) == 0:
        raise ValueError(f"No face pixels found in {region}")
    padding = 6
    return alpha[
        max(0, int(ys.min()) - padding) : min(alpha.shape[0], int(ys.max()) + padding + 1),
        max(0, int(xs.min()) - padding) : min(alpha.shape[1], int(xs.max()) + padding + 1),
    ]


def components(alpha: np.ndarray) -> list[np.ndarray]:
    visited = np.zeros(alpha.shape, dtype=bool)
    result: list[tuple[tuple[int, int], np.ndarray]] = []
    height, width = alpha.shape
    for y in range(height):
        for x in range(width):
            if visited[y, x] or alpha[y, x] == 0:
                continue
            queue = deque([(x, y)])
            visited[y, x] = True
            points: list[tuple[int, int]] = []
            while queue:
                current_x, current_y = queue.popleft()
                points.append((current_x, current_y))
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
            layer = np.zeros_like(alpha)
            for point_x, point_y in points:
                layer[point_y, point_x] = alpha[point_y, point_x]
            result.append(((min(point_y for _, point_y in points), min(point_x for point_x, _ in points)), layer))
    return [layer for _, layer in sorted(result, key=lambda item: item[0])]


def to_image(alpha: np.ndarray) -> Image.Image:
    image = Image.new("RGBA", (alpha.shape[1], alpha.shape[0]), (255, 255, 255, 255))
    image.putalpha(Image.fromarray(alpha, "L"))
    return image


def main() -> None:
    faces = {
        "tilted-mouth": (80, 195, 170, 286),
        "x-eyes": (380, 195, 470, 286),
        "square-eye-smile": (280, 300, 370, 394),
        "frown": (176, 505, 274, 610),
        "question-marks": (370, 505, 490, 610),
    }
    OUTPUT_ROOT.mkdir(exist_ok=True)
    contact = Image.new("RGBA", (5 * 180, 180), (236, 236, 234, 255))
    screen_contact = Image.new("RGBA", (5 * 180, 180), (84, 84, 84, 255))
    for index, (name, region) in enumerate(faces.items()):
        alpha = alpha_from_region(region)
        to_image(alpha).save(OUTPUT_ROOT / f"{name}-full.png")
        for part_index, part_alpha in enumerate(components(alpha)):
            to_image(part_alpha).save(OUTPUT_ROOT / f"{name}-part-{part_index}.png")
        proof = to_image(alpha).resize((156, 156), Image.Resampling.NEAREST)
        contact.alpha_composite(proof, (12 + index * 180, 12))
        screen = Image.new("RGBA", (156, 156), (3, 3, 4, 255))
        preview_face = to_image(alpha).resize((112, round(alpha.shape[0] * 112 / alpha.shape[1])), Image.Resampling.LANCZOS)
        screen.alpha_composite(
            preview_face,
            ((156 - preview_face.width) // 2, (156 - preview_face.height) // 2),
        )
        screen_contact.alpha_composite(screen, (12 + index * 180, 12))
        print(f"{name}: size={alpha.shape[1]}x{alpha.shape[0]} parts={len(components(alpha))}")
    contact.convert("RGB").save(OUTPUT_ROOT / "green-expression-static-proof-v3.png")
    screen_contact.convert("RGB").save(OUTPUT_ROOT / "green-expression-screen-proof-v3.png")


if __name__ == "__main__":
    main()
