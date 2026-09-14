from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).parent
FACE_ROOT = ROOT / "green-expression-assets-v3"
DRAWABLE_ROOT = ROOT.parents[2] / "app" / "src" / "main" / "res" / "drawable-nodpi"
STONE_BASES = {
    "light": ROOT / "stone-light-base-v1.webp",
    "dark": ROOT / "stone-dark-base-v1.webp",
}
STONE_OUTPUTS = {
    "light": "mason_empty_chat_computer.png",
    "dark": "mason_empty_chat_stone_dark.png",
}
STONE_POLYGONS = {
    "light": [
        (37, 354),
        (58, 186),
        (89, 123),
        (183, 51),
        (249, 47),
        (421, 64),
        (505, 134),
        (550, 201),
        (566, 379),
        (541, 437),
        (504, 481),
        (443, 512),
        (282, 521),
        (214, 514),
        (151, 492),
        (99, 463),
        (68, 425),
    ],
    "dark": [
        (37, 390),
        (59, 190),
        (91, 128),
        (185, 76),
        (250, 69),
        (423, 90),
        (518, 165),
        (552, 212),
        (565, 390),
        (543, 450),
        (505, 498),
        (444, 525),
        (279, 527),
        (211, 516),
        (146, 489),
        (97, 457),
        (62, 423),
    ],
}
FACE_CENTERS = {
    "light": (0.579, 0.485),
    "dark": (0.583, 0.528),
}
EXPRESSIONS = {
    "tilted": "tilted-mouth",
    "surprised": "x-eyes",
    "smile": "square-eye-smile",
    "frown": "frown",
    "question": "question-marks",
}


def antialiased_polygon_mask(
    size: tuple[int, int],
    points: list[tuple[int, int]],
    scale: int = 4,
) -> Image.Image:
    mask = Image.new("L", (size[0] * scale, size[1] * scale), 0)
    ImageDraw.Draw(mask).polygon(
        [(x * scale, y * scale) for x, y in points],
        fill=255,
    )
    return mask.resize(size, Image.Resampling.LANCZOS)


def export_stone(theme: str) -> Path:
    source = Image.open(STONE_BASES[theme]).convert("RGBA")
    mask = antialiased_polygon_mask(source.size, STONE_POLYGONS[theme])
    if theme == "dark":
        # Pull the matte inside the photographed rim so its gray backdrop cannot halo.
        mask = mask.filter(ImageFilter.MinFilter(size=9))
    source.putalpha(mask)
    output = DRAWABLE_ROOT / STONE_OUTPUTS[theme]
    source.save(output, optimize=True)
    return output


def export_faces() -> None:
    for resource_name, source_name in EXPRESSIONS.items():
        for part_index in range(3):
            source = FACE_ROOT / f"{source_name}-part-{part_index}.png"
            output = DRAWABLE_ROOT / f"mason_empty_face_{resource_name}_{part_index}.png"
            Image.open(source).convert("RGBA").save(output, optimize=True)


def export_shadow() -> Path:
    shadow = Image.new("RGBA", (600, 600), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).ellipse((128, 486, 474, 531), fill=(0, 0, 0, 72))
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=12))
    output = DRAWABLE_ROOT / "mason_empty_chat_shadow.png"
    shadow.save(output, optimize=True)
    return output


def add_shadow(canvas: Image.Image, left: int) -> None:
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    draw.ellipse((left + 128, 486, left + 474, 531), fill=(0, 0, 0, 60))
    layer = layer.filter(ImageFilter.GaussianBlur(radius=12))
    canvas.alpha_composite(layer)


def add_smile(canvas: Image.Image, theme: str, left: int) -> None:
    center_x, center_y = FACE_CENTERS[theme]
    face_width = 96
    for part_index in range(3):
        part = Image.open(
            FACE_ROOT / f"square-eye-smile-part-{part_index}.png"
        ).convert("RGBA")
        face_height = round(part.height * face_width / part.width)
        part = part.resize((face_width, face_height), Image.Resampling.LANCZOS)
        x = left + round(600 * center_x - face_width / 2)
        y = round(600 * center_y - face_height / 2)
        canvas.alpha_composite(part, (x, y))


def export_proof(stones: dict[str, Path]) -> None:
    proof = Image.new("RGBA", (1200, 600), (255, 255, 255, 255))
    ImageDraw.Draw(proof).rectangle((600, 0, 1200, 600), fill=(24, 25, 27, 255))
    for index, theme in enumerate(("light", "dark")):
        left = index * 600
        add_shadow(proof, left)
        proof.alpha_composite(Image.open(stones[theme]).convert("RGBA"), (left, 0))
        add_smile(proof, theme, left)
    output = ROOT / "android-empty-chat-stone-proof.png"
    proof.convert("RGB").save(output, optimize=True)


def main() -> None:
    DRAWABLE_ROOT.mkdir(parents=True, exist_ok=True)
    stones = {theme: export_stone(theme) for theme in STONE_BASES}
    export_faces()
    shadow = export_shadow()
    export_proof(stones)
    for path in stones.values():
        print(f"saved={path.name}")
    print("saved=animated face layers")
    print(f"saved={shadow.name}")
    print("saved=android-empty-chat-stone-proof.png")


if __name__ == "__main__":
    main()
