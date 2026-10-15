#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
橙龙影视 图标资源导出器
========================
从豆包生成的无水印原图（C 正面龙脸 + A 侧龙盘橙）导出完整 Android 图标资源。

用法：python design/export_icons.py
"""
import os
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageChops

try:
    import numpy as np
except ImportError:
    np = None

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_C = os.path.join(ROOT, "design", "source", "dragon-frontal.jpg")   # C 正面龙脸
SRC_A = os.path.join(ROOT, "design", "source", "dragon-side.jpg")      # A 侧龙盘橙


def extract_foreground(img):
    """C 图本身就是带圆角方框的成品图标，直接返回整图作为前景。"""
    return img.convert("RGBA")


def make_square_icon(src_img, size):
    """方形图标：整图缩放放入圆角矩形底（保留原图圆角）。"""
    canvas = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    mask = Image.new("L", (size, size), 0)
    r = int(size * 0.22)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=255)

    # 缩放原图使其适配画布
    scale = min(size / src_img.width, size / src_img.height)
    nw = max(1, int(round(src_img.width * scale)))
    nh = max(1, int(round(src_img.height * scale)))
    resized = src_img.resize((nw, nh), Image.LANCZOS)
    x = (size - nw) // 2
    y = (size - nh) // 2
    canvas.alpha_composite(resized, (x, y))
    return canvas


def make_round_icon(src_img, size):
    """圆形图标：整图缩放放入圆形白色底。"""
    canvas = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)

    scale = min(size / src_img.width, size / src_img.height)
    nw = max(1, int(round(src_img.width * scale)))
    nh = max(1, int(round(src_img.height * scale)))
    resized = src_img.resize((nw, nh), Image.LANCZOS)
    x = (size - nw) // 2
    y = (size - nh) // 2
    canvas.alpha_composite(resized, (x, y))
    return canvas


def make_adaptive_fg(src_img, size=432):
    """自适应图标前景：整图缩放到安全区 66%。"""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    target = int(size * 0.66)
    scale = target / max(src_img.width, src_img.height)
    nw = max(1, int(round(src_img.width * scale)))
    nh = max(1, int(round(src_img.height * scale)))
    resized = src_img.resize((nw, nh), Image.LANCZOS)
    x = (size - nw) // 2
    y = (size - nh) // 2
    canvas.alpha_composite(resized, (x, y))
    return canvas


def make_banner(side_img, w, h):
    """用 A 图生成 TV banner：直接缩放整图到目标尺寸（保留完整构图）。"""
    # 原图是 1536×1536，直接缩放到目标宽高比
    # 先缩放到高度匹配，再居中裁剪宽度
    scale = h / side_img.height
    nw = max(1, int(round(side_img.width * scale)))
    nh = h
    resized = side_img.resize((nw, nh), Image.LANCZOS)
    
    # 如果宽度超出，居中裁剪
    if nw > w:
        x = (nw - w) // 2
        return resized.crop((x, 0, x + w, h))
    else:
        # 如果宽度不足，填充橙色背景
        canvas = Image.new("RGBA", (w, h), (245, 140, 50, 255))
        x = (w - nw) // 2
        canvas.alpha_composite(resized, (x, 0))
        return canvas


def save_png(img, path):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full, "PNG")
    print(f"  {path:<55s} {img.width}x{img.height}")


def save_webp(img, path):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full, "WEBP", lossless=True, quality=100)
    print(f"  {path:<55s} {img.width}x{img.height}")


def main():
    print("\nloading source images ...")
    frontal = Image.open(SRC_C).convert("RGBA")
    side = Image.open(SRC_A).convert("RGBA")
    print(f"  C (frontal): {frontal.size}")
    print(f"  A (side):    {side.size}")

    fg = frontal  # C 图本身就是成品图标，直接使用

    # 保存主稿
    print("\n--- master copies ---")
    save_png(fg, "design/icon-master-fg.png")
    square_master = make_square_icon(fg, 1024)
    round_master = make_round_icon(fg, 1024)
    save_png(square_master, "design/icon-master-square.png")
    save_png(round_master, "design/icon-master-round.png")

    # mipmap ic_launcher.png（方形）
    print("\n--- mipmap ic_launcher.png ---")
    for dens, sz in (("mdpi", 128), ("hdpi", 192), ("xhdpi", 256),
                     ("xxhdpi", 384), ("xxxhdpi", 512)):
        icon = make_square_icon(fg, sz)
        save_png(icon, f"app/src/main/res/mipmap-{dens}/ic_launcher.png")

    # mipmap ic_launcher_round.webp（圆形）
    print("\n--- mipmap ic_launcher_round.webp ---")
    for dens, sz in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                     ("xxhdpi", 144), ("xxxhdpi", 192)):
        icon = make_round_icon(fg, sz)
        save_webp(icon, f"app/src/main/res/mipmap-{dens}/ic_launcher_round.webp")

    # adaptive foreground PNG
    print("\n--- adaptive foreground ---")
    for dens, sz in (("mdpi", 108), ("hdpi", 162), ("xhdpi", 216),
                     ("xxhdpi", 324), ("xxxhdpi", 432)):
        af = make_adaptive_fg(fg, sz)
        save_png(af, f"app/src/main/res/drawable-{dens}/ic_launcher_foreground.png")

    # playstore & logo
    print("\n--- playstore & logo ---")
    save_png(make_square_icon(fg, 512), "app/src/main/ic_launcher-playstore.png")
    save_png(make_round_icon(fg, 600), "app/src/main/res/drawable-nodpi/ic_logo.png")

    # TV banners from A
    print("\n--- TV banners (from A) ---")
    save_png(make_banner(side, 320, 180), "app/src/leanback/res/drawable/ic_banner.png")
    save_png(make_banner(side, 640, 360), "app/src/leanback/res/mipmap-xhdpi/ic_banner.png")

    # adaptive icon background color: sample the orange gradient from C
    arr = np.asarray(frontal, dtype="float32")
    # 取中心区域（龙脸周围）的橙色作为背景色
    h, w = arr.shape[:2]
    center_region = arr[h//3:2*h//3, w//3:2*w//3, :3]
    bg_sample = center_region.mean(axis=(0, 1))
    bg_hex = "#{:02X}{:02X}{:02X}".format(int(bg_sample[0]), int(bg_sample[1]), int(bg_sample[2]))
    print(f"\n  sampled background color: {bg_hex}")

    # update adaptive icon XMLs
    xml_bg = f'    <background android:drawable="@color/{bg_hex.replace("#", "")}" />\n'
    # Actually better to use a color resource. Let me just write inline drawable.
    # For simplicity, create a color.xml entry and reference it.
    # But actually the simplest is to use a solid color drawable inline.
    # Let's update the XML to use a solid color via @color/orange_icon_bg
    # First, ensure colors.xml has the entry.

    colors_path = os.path.join(ROOT, "app", "src", "main", "res", "values", "colors.xml")
    if os.path.exists(colors_path):
        with open(colors_path, "r", encoding="utf-8") as f:
            content = f.read()
        if "orange_icon_bg" not in content:
            # insert before </resources>
            content = content.replace("</resources>",
                f'    <color name="orange_icon_bg">{bg_hex}</color>\n</resources>')
            with open(colors_path, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"  added <color name=\"orange_icon_bg\">{bg_hex}</color> to colors.xml")
    else:
        # create colors.xml
        os.makedirs(os.path.dirname(colors_path), exist_ok=True)
        with open(colors_path, "w", encoding="utf-8") as f:
            f.write(f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                    f'    <color name="orange_icon_bg">{bg_hex}</color>\n</resources>\n')
        print(f"  created colors.xml with orange_icon_bg={bg_hex}")

    # update adaptive icon XMLs
    for xml_path in [
        "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
        "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
    ]:
        full = os.path.join(ROOT, xml_path)
        if os.path.exists(full):
            with open(full, "r", encoding="utf-8") as f:
                content = f.read()
            # replace background line
            import re
            content = re.sub(
                r'<background[^/]*/>',
                '    <background android:drawable="@color/orange_icon_bg" />',
                content
            )
            with open(full, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"  updated {xml_path}")

    # also update leanback banner adaptive icon if exists
    banner_xml = os.path.join(ROOT, "app/src/leanback/res/mipmap-anydpi-v26/ic_banner.xml")
    if os.path.exists(banner_xml):
        with open(banner_xml, "r", encoding="utf-8") as f:
            content = f.read()
        content = re.sub(
            r'<background[^/]*/>',
            '    <background android:drawable="@color/orange_icon_bg" />',
            content
        )
        with open(banner_xml, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"  updated {banner_xml}")

    print("\ndone.")


if __name__ == "__main__":
    main()
