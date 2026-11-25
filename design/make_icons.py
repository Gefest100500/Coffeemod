from PIL import Image
import os

root = r"C:\Users\Gefes\Projects\CoffeMod\app\src\main\res"
src = Image.open(r"C:\Users\Gefes\Projects\CoffeMod\design\icon-source.webp").convert("RGBA")
cream = (247, 243, 236, 255)


def flatten_base(size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), cream)
    inner = max(1, int(size * 0.84))
    icon = src.resize((inner, inner), Image.LANCZOS)
    off = (size - inner) // 2
    canvas.alpha_composite(icon, (off, off))
    return canvas


def foreground(size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    inner = int(size * 0.66)
    icon = src.resize((inner, inner), Image.LANCZOS)
    off = (size - inner) // 2
    canvas.alpha_composite(icon, (off, off))
    return canvas


densities = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
for name, factor in densities.items():
    folder = os.path.join(root, "mipmap-" + name)
    os.makedirs(folder, exist_ok=True)
    legacy = flatten_base(int(48 * factor))
    legacy.save(os.path.join(folder, "ic_launcher.png"))
    legacy.save(os.path.join(folder, "ic_launcher_round.png"))
    foreground(int(108 * factor)).save(os.path.join(folder, "ic_launcher_foreground.png"))

os.makedirs(r"C:\Users\Gefes\Projects\CoffeMod\design", exist_ok=True)
flatten_base(512).save(r"C:\Users\Gefes\Projects\CoffeMod\design\icon-512.png")
print("ok")
