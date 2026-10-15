#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
橙龙影视 图标生成器 —— 写实中国龙
==================================

视角：3/4 侧面，龙头朝画面左下，长吻在剪影上真正伸出，**张口露齿**。

画法要点（这是"写实"与"贴纸拼贴"的分界）：
  · 头 = 头骨 ∪ 下颌 的并集掩膜，描边由 **掩膜腐蚀取边** 得到，因此张口缺口
    与下颌都能并入一个干净剪影，内部一律不描边
  · 描边只用暖褐细线，且只出现在外剪影 / 龙角 / 龙须
  · 主光左上；rim light 勾颅顶与吻梁；眉骨下、颌下、颈侧做 AO
  · 鳞甲只铺颊侧与颈背，向吻部线性淡出（铺满会变松果）

结构：
  飘鬃两层 → 双角 → 头（渐变/鳞/AO/rim）→ 吻梁提亮 → 眉骨 → 张口+舌+獠牙
  → 鼻孔 → 双眼 → 龙须 → 龙髯 → 腐蚀描边压边

工程约定：
  · 几何以 k（头长单位）计，换尺寸不跑版
  · 成品按透明包围盒自动缩放（place()），改比例不必重算留白

用法：
    python design/generate_icons.py                 # 输出 design/ 与全部 app 资源
    python design/generate_icons.py --preview-only  # 只输出 design/ 预览，不碰 app
"""
import math
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter
from scipy import ndimage

try:
    import numpy as np
except ImportError:  # pragma: no cover
    np = None

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ------------------------------------------------------------------ 调色板
BG_IN     = (255, 250, 240, 255)
BG_OUT    = (255, 203, 134, 255)
RIM       = (233, 124, 24, 255)

SKIN_HI   = (255, 192, 92, 255)
SKIN_MID  = (238, 140, 34, 255)
SKIN_LO   = (188, 82, 14, 255)
SKIN_DEEP = (118, 40, 6, 255)

SNOUT_HI  = (255, 210, 140, 255)
SNOUT_MID = (238, 162, 62, 255)
NOSTRIL   = (52, 16, 6, 255)

OUTLINE   = (64, 28, 10, 255)
SCALE_DK  = (162, 58, 4, 100)
SCALE_HI  = (255, 214, 150, 95)

MANE_HI   = (240, 130, 30, 255)
MANE_MID  = (206, 88, 14, 255)
MANE_LO   = (132, 42, 4, 255)

HORN_HI   = (255, 236, 194, 255)
HORN_MID  = (226, 184, 112, 255)
HORN_LO   = (140, 92, 32, 255)

MOUTH     = (54, 10, 12, 255)
TONGUE    = (186, 54, 56, 255)
FANG      = (255, 252, 242, 255)

EYE_IRIS  = (255, 198, 62, 255)
EYE_DEEP  = (176, 92, 4, 255)
PUPIL     = (20, 7, 0, 255)

WHISKER   = (255, 243, 216, 255)
BEARD     = (244, 168, 78, 255)

LEAF_HI   = (108, 198, 102, 255)
LEAF_LO   = (24, 104, 42, 255)
STEM      = (118, 72, 24, 255)

SS = 2048
KF = 0.300


# ================================================================ 基础几何
def arc_pts(cx, cy, rx, ry, a0, a1, n=48):
    out = []
    for i in range(n + 1):
        t = math.radians(a0 + (a1 - a0) * i / n)
        out.append((cx + rx * math.cos(t), cy + ry * math.sin(t)))
    return out


def ellipse_pts(cx, cy, rx, ry, rot=0.0, n=200):
    c, s = math.cos(rot), math.sin(rot)
    out = []
    for i in range(n):
        a = 2 * math.pi * i / n
        x, y = rx * math.cos(a), ry * math.sin(a)
        out.append((cx + x * c - y * s, cy + x * s + y * c))
    return out


def curve(ctrl, n=48):
    """任意阶贝塞尔（De Casteljau），前两点为端点。"""
    out = []
    for i in range(n + 1):
        t = i / n
        p = list(ctrl)
        while len(p) > 1:
            p = [((1 - t) * a[0] + t * b[0], (1 - t) * a[1] + t * b[1])
                 for a, b in zip(p, p[1:])]
        out.append(p[0])
    return out


def ribbon(ctrl, w0, w1, n=52):
    """沿贝塞尔生成变宽度闭合多边形（鬃毛/角/须/髯通用）。"""
    mid = curve(ctrl, n)
    left, right = [], []
    for i in range(n + 1):
        c = mid[i]
        nxt, prv = mid[min(i + 1, n)], mid[max(i - 1, 0)]
        dx, dy = nxt[0] - prv[0], nxt[1] - prv[1]
        ln = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / ln, dx / ln
        w = (w0 + (w1 - w0) * (i / n)) / 2.0
        left.append((c[0] + nx * w, c[1] + ny * w))
        right.append((c[0] - nx * w, c[1] - ny * w))
    return left + right[::-1]


def tapered(p0, ctrl, p1, w0, w1, n=48):
    return ribbon([p0, ctrl, p1], w0, w1, n)


def grow(pts, f, cx, cy):
    return [(cx + (x - cx) * f, cy + (y - cy) * f) for x, y in pts]


def shape(d, pts, fill, outline=None, ow=0.0):
    if outline is not None and ow > 0:
        d.line(list(pts) + [pts[0]], fill=outline,
               width=max(1, int(round(ow))), joint="curve")
    if fill is not None:
        d.polygon(pts, fill=fill)


def radial(size, inner, outer, cx, cy, r):
    if np is not None:
        y, x = np.mgrid[0:size, 0:size].astype("float32")
        dd = np.clip(np.sqrt((x - cx * size) ** 2 + (y - cy * size) ** 2)
                     / max(1.0, r * size), 0.0, 1.0)[..., None]
        a, b = np.array(inner, "float32"), np.array(outer, "float32")
        return Image.fromarray((a * (1 - dd) + b * dd).astype("uint8"), "RGBA")
    img = Image.new("RGBA", (size, size), inner)
    dr = ImageDraw.Draw(img)
    for i in range(200, 0, -1):
        t = i / 200.0
        col = tuple(int(inner[k] * (1 - t) + outer[k] * t) for k in range(4))
        rr = r * size * t
        dr.ellipse([cx * size - rr, cy * size - rr, cx * size + rr, cy * size + rr], fill=col)
    return img


def mask_of(size, pts):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).polygon(pts, fill=255)
    return m


def union_masks(size, polys):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    for p in polys:
        d.polygon(p, fill=255)
    return m


def outline_mask(mask, thickness):
    """掩膜腐蚀取边：高斯模糊后阈值化即近似腐蚀。"""
    s = max(0.6, thickness / 0.23)
    a = mask.filter(ImageFilter.GaussianBlur(s)).point(lambda v: 255 if v >= 150 else 0)
    return ImageChops.subtract(mask, a)


def clip_to(layer, mask):
    out = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    out.paste(layer, (0, 0), mask)
    return out


def tint(size, mask, color):
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(Image.new("RGBA", (size, size), color), (0, 0), mask)
    return out


def soft(size, pts, color, blur):
    lay = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(lay).polygon(pts, fill=color)
    return lay.filter(ImageFilter.GaussianBlur(max(0.5, blur)))


def linear_fade(size, a, b):
    if np is None:
        return Image.new("L", (size, size), 255)
    y, x = np.mgrid[0:size, 0:size].astype("float32")
    dx, dy = b[0] - a[0], b[1] - a[1]
    den = dx * dx + dy * dy or 1.0
    t = np.clip(((x - a[0]) * dx + (y - a[1]) * dy) / den, 0.0, 1.0)
    return Image.fromarray(((1.0 - t) * 255).astype("uint8"), "L")


def inner_rim(mask, dx, dy, blur):
    sh = Image.new("L", mask.size, 0)
    sh.paste(mask, (int(dx), int(dy)))
    return ImageChops.subtract(mask, sh).filter(ImageFilter.GaussianBlur(blur))


# 高度场凸起（单位坐标）：amp>0 隆起、amp<0 凹陷。这是"形体"的定义。
BUMPS = [
    (-0.30, -0.70, 0.52, 0.20,  0.42),   # 眉脊
    (-0.36, -0.62, 0.30, 0.16,  0.22),   # 眉脊内侧加厚
    (-1.00, -0.16, 0.44, 0.24,  0.46),   # 吻梁
    (-1.34,  0.00, 0.24, 0.22,  0.40),   # 鼻头
    ( 0.10, -0.10, 0.50, 0.46,  0.44),   # 颧颊
    ( 0.42,  0.20, 0.34, 0.34,  0.34),   # 颈侧
    (-0.80,  0.46, 0.40, 0.16,  0.26),   # 下颌
    (-0.56, -0.28, 0.30, 0.20, -0.30),   # 眼窝
    ( 0.06, -0.40, 0.22, 0.15, -0.22),   # 远眼窝
    (-0.86,  0.34, 0.34, 0.10, -0.30),   # 口缝
]


def form_light(size, mask, bumps, light=(-0.52, -0.64, 0.58),
               strength=2.6, ambient=0.44, gain=0.78):
    """剪影距离场当高度场 → 法线 → Lambert 漫反射乘子。

    这一层是"写实"的关键：光会顺着吻梁、眉骨、颧颊的起伏走，
    而不是平铺一层径向渐变（那正是之前贴纸感的来源）。
    """
    m = np.asarray(mask, dtype="float32") / 255.0
    dt = ndimage.distance_transform_edt(m > 0.5)
    # 用固定尺度归一化，而不是全局最大值：否则薄部位（下颌）整块都算"边缘"而全暗
    h = np.sqrt(np.clip(dt / (0.13 * size), 0.0, 1.0))

    y, x = np.mgrid[0:size, 0:size].astype("float32")
    for (cx, cy, rx, ry, amp) in bumps:
        h = h + amp * np.exp(-(((x - cx) ** 2) / (2.0 * max(rx, 1.0) ** 2) +
                               ((y - cy) ** 2) / (2.0 * max(ry, 1.0) ** 2)))
    h = np.clip(h, 0.0, 1.6)

    nx, ny, nz = -np.gradient(h, axis=1) * strength, -np.gradient(h, axis=0) * strength, np.ones_like(h)
    ln = np.sqrt(nx * nx + ny * ny + nz * nz)
    nx, ny, nz = nx / ln, ny / ln, nz / ln

    lx, ly, lz = light
    ll = math.sqrt(lx * lx + ly * ly + lz * lz)
    diff = np.clip(nx * (lx / ll) + ny * (ly / ll) + nz * (lz / ll), 0.0, 1.0)
    return (ambient + gain * diff).astype("float32")


def place(drg, w, h, box, cx=0.5, cy=0.5):
    bbox = drg.getbbox()
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    if not bbox:
        return out
    bw, bh = bbox[2] - bbox[0], bbox[3] - bbox[1]
    s = min(w * box / bw, h * box / bh)
    nw, nh = max(1, int(round(bw * s))), max(1, int(round(bh * s)))
    out.alpha_composite(drg.crop(bbox).resize((nw, nh), Image.LANCZOS),
                        (int(round(cx * w - nw / 2)), int(round(cy * h - nh / 2))))
    return out


# ================================================================ 3/4 视角几何
HEAD_C = (0.14, -0.06)      # 缩放/发散中心


def upper_head():
    """短钝宽吻 + 隆眉脊 + 厚颊（jowl 下探，用于与下颌并集连通）。"""
    pts  = [(-0.10, -1.00), (-0.44, -0.96), (-0.74, -0.80), (-0.94, -0.60),
            (-1.06, -0.42), (-1.18, -0.28), (-1.30, -0.16)]
    pts += arc_pts(-1.40, 0.00, 0.20, 0.18, 250, 110, 44)       # 钝鼻头
    pts += [(-1.28, 0.20), (-1.04, 0.28), (-0.72, 0.38), (-0.40, 0.45)]
    pts += [(-0.06, 0.52), (0.12, 0.62), (0.36, 0.60), (0.58, 0.42)]
    pts += [(0.74, 0.14), (0.80, -0.20), (0.66, -0.68), (0.20, -1.00)]
    return pts


def lower_jaw():
    """张口：下颌绕颌关节下旋；后段埋进 jowl，保证并集连通不留孤岛。"""
    pts  = [(-1.22, 0.42), (-0.92, 0.48), (-0.56, 0.52), (-0.18, 0.56), (0.20, 0.58)]
    pts += [(0.30, 0.34), (0.08, 0.28)]
    pts += [(-0.30, 0.46), (-0.72, 0.66), (-1.06, 0.74), (-1.24, 0.60)]
    return pts


def mouth_shape():
    """只在两颌之间填暗腔，不铺到下颌上。"""
    up = [(-1.30, 0.22), (-1.02, 0.30), (-0.72, 0.38), (-0.40, 0.45), (-0.18, 0.49)]
    lo = [(-0.14, 0.56), (-0.44, 0.52), (-0.86, 0.47), (-1.18, 0.42)]
    return up + lo


# 飘鬃：三次贝塞尔 S 形，二层（后层暗长 / 前层亮短）
MANE_STRANDS = [
    ((-0.06, -0.98), (0.02, -1.32), (0.26, -1.50), (0.52, -1.40), 0.17, 0.030),
    (( 0.18, -0.98), (0.32, -1.28), (0.62, -1.38), (0.88, -1.22), 0.19, 0.032),
    (( 0.44, -0.86), (0.62, -1.10), (0.92, -1.10), (1.14, -0.90), 0.20, 0.034),
    (( 0.62, -0.66), (0.86, -0.84), (1.12, -0.74), (1.28, -0.50), 0.21, 0.036),
    (( 0.72, -0.36), (0.98, -0.42), (1.22, -0.24), (1.30,  0.04), 0.21, 0.036),
    (( 0.76,  0.00), (1.00,  0.08), (1.18,  0.32), (1.16,  0.58), 0.20, 0.034),
    (( 0.66,  0.34), (0.86,  0.50), (0.94,  0.76), (0.82,  0.98), 0.18, 0.030),
    (( 0.42,  0.56), (0.56,  0.78), (0.52,  1.00), (0.34,  1.14), 0.16, 0.028),
]
# 双角（三次贝塞尔）；近角在观众左（龙右侧），远角偏右偏小
ANTLER_FAR = [
    ((0.34, -0.80), (0.52, -1.08), (0.70, -1.24), (0.84, -1.34), 0.24, 0.048),
    ((0.48, -1.02), (0.48, -1.26), (0.44, -1.44), (0.39, -1.58), 0.11, 0.026),
    ((0.68, -1.20), (0.86, -1.30), (1.00, -1.36), (1.10, -1.44), 0.10, 0.024),
]
ANTLER_NEAR = [
    ((-0.28, -0.90), (-0.40, -1.30), (-0.38, -1.62), (-0.24, -1.86), 0.30, 0.054),
    ((-0.32, -1.14), (-0.54, -1.34), (-0.68, -1.50), (-0.78, -1.68), 0.13, 0.029),
    ((-0.34, -1.52), (-0.16, -1.66), (-0.04, -1.78), (0.04, -1.92), 0.12, 0.027),
]
# 龙须：细、无描边、末端收尖（贴着头走或太粗都会读成发箍/耳机梁）
WHISKER_FAR = [((-1.16, -0.30), (-0.98, -0.74), (-0.54, -0.92), (-0.18, -0.74), 0.042, 0.018),
               ((-0.18, -0.74), (0.06, -0.60), (0.12, -0.44), (0.02, -0.34), 0.018, 0.005)]
WHISKER_NEAR = [((-1.26, -0.22), (-1.06, -0.80), (-0.50, -1.00), (-0.06, -0.76), 0.060, 0.026),
                ((-0.06, -0.76), (0.22, -0.58), (0.32, -0.40), (0.20, -0.28), 0.026, 0.006)]
# 龙髯
BEARD_SPEC = [((-1.00, 0.68), (-0.86, 1.06), (-0.56, 1.20), (-0.26, 1.12), 0.135, 0.030),
              ((-0.66, 0.80), (-0.46, 1.08), (-0.16, 1.16), (0.10, 1.04), 0.115, 0.026)]

EYE_NEAR = (-0.56, -0.28)
EYE_FAR  = (0.06, -0.40)


def dragon(size, with_leaf=True):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    k = size * KF
    ox = oy = size * 0.5

    def P(u, v):
        return (ox + u * k, oy + v * k)

    def SP(pts):
        return [(ox + x * k, oy + y * k) for x, y in pts]

    def SG(pts, f):
        return SP(grow(pts, f, HEAD_C[0], HEAD_C[1]))

    d = ImageDraw.Draw(img)
    o = k * 0.028
    up_px = SP(upper_head())
    jaw_px = SP(lower_jaw())
    sil = union_masks(size, [up_px, jaw_px])

    # ---- 1) 果梗 + 果叶（最底层，可选）
    if with_leaf:
        d.line([P(-0.02, -0.94), P(-0.06, -1.34)], fill=OUTLINE,
               width=int(max(2, k * 0.090)), joint="curve")
        d.line([P(-0.02, -0.94), P(-0.06, -1.34)], fill=STEM,
               width=int(max(2, k * 0.050)), joint="curve")
        leaf = SP(ellipse_pts(0.34, -1.48, 0.44, 0.155, rot=-0.28))
        shape(d, leaf, LEAF_LO, OUTLINE, o * 0.70)
        shape(d, SP(ellipse_pts(0.34, -1.48, 0.375, 0.105, rot=-0.28)), LEAF_HI)
        d.line([P(0.02, -1.40), P(0.74, -1.56)], fill=STEM,
               width=int(max(2, k * 0.018)), joint="curve")

    # ---- 2) 飘鬃两层（后层暗长、前层亮短）
    for (col, f, wm) in ((MANE_LO, 1.20, 1.10), (MANE_HI, 1.00, 1.00)):
        for (p0, c1, c2, p1, w0, w1) in MANE_STRANDS:
            path = [p0, c1, c2, p1] if f == 1.00 else grow([p0, c1, c2, p1], f, *HEAD_C)
            shape(d, SP(ribbon(path, w0 * wm, w1)), col)
    # 鬃毛根部填缝（贴着头缘，只补缝不露边）
    d.polygon(SP(ellipse_pts(0.26, -0.02, 0.46, 0.80, rot=-0.06)), fill=MANE_MID)

    # ---- 3) 龙角（远角在下层）
    for (p0, c1, c2, p1, w0, w1) in ANTLER_FAR + ANTLER_NEAR:
        shape(d, SP(ribbon([p0, c1, c2, p1], w0, w1)), HORN_MID, OUTLINE, o * 0.32)
        shape(d, SP(ribbon([p0, c1, c2, p1], w0 * 0.34, w1 * 0.26)), HORN_HI)
        shape(d, SP(ellipse_pts(p1[0], p1[1], w1 * 1.6, w1 * 1.6)), HORN_LO)

    # ---- 4) 头：底色 × 形体光照（法线由高度场求出，光贴着形体走）
    base = np.asarray(radial(size, SKIN_HI, SKIN_LO,
                             (ox - k * 0.72) / size, (oy - k * 0.78) / size, k * 2.75),
                      dtype="float32")[..., :3]
    lit = form_light(size, sil, [(ox + u * k, oy + v * k, rx * k, ry * k, amp)
                                 for (u, v, rx, ry, amp) in BUMPS])
    head = np.clip(base * lit[..., None], 0, 255).astype("uint8")
    img.alpha_composite(Image.fromarray(np.dstack([head, np.asarray(sil)]), "RGBA"))

    # ---- 5) 鳞甲：只铺颊侧与颈背，向吻部线性淡出
    sc = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sc)
    spacing, radius = 0.185, 0.100
    row, v = 0, -1.05
    while v <= 0.95:
        u = -0.40 + (spacing * 0.5 if row % 2 else 0.0)     # 只铺颊侧与颈背，吻部留光洁
        while u <= 1.00:
            j = 0.5 + 0.5 * math.sin(u * 5.3 + v * 3.7)
            r = radius * (0.85 + 0.30 * j) * k
            cx, cy = ox + u * k, oy + v * k
            sd.arc([cx - r, cy - r, cx + r, cy + r], 0, 180,
                   fill=SCALE_DK, width=max(1, int(k * 0.015)))
            rr = r * 0.78
            sd.arc([cx - rr, cy - rr, cx + rr, cy + rr], 0, 180,
                   fill=SCALE_HI, width=max(1, int(k * 0.009)))
            u += spacing
        v += spacing * 0.86
        row += 1
    sc.putalpha(ImageChops.multiply(
        sc.getchannel("A"), linear_fade(size, P(0.45, -0.30), P(-1.10, 0.40))))
    img.alpha_composite(clip_to(sc, sil))

    # ---- 6) AO（形体光已压暗凹处，这里只补一点接触阴影）
    for (c, rx, ry) in (((-0.58, -0.16), 0.30, 0.17), ((-0.24, -0.36), 0.22, 0.14),
                        ((0.30, -0.54), 0.20, 0.13), ((0.60, 0.12), 0.22, 0.30)):
        img.alpha_composite(clip_to(
            soft(size, SP(ellipse_pts(c[0], c[1], rx, ry)), (150, 54, 8, 70), k * 0.065), sil))

    # ---- 7) rim light
    rim = inner_rim(sil, k * 0.050, k * 0.058, k * 0.022)
    img.alpha_composite(clip_to(
        Image.merge("RGBA", (Image.new("L", (size, size), 255),
                             Image.new("L", (size, size), 236),
                             Image.new("L", (size, size), 198), rim)), sil))

    # ---- 8) 高光/反光（形体光已给基调，这里只补点睛）
    img.alpha_composite(clip_to(soft(size, SP(ellipse_pts(-1.36, -0.03, 0.15, 0.13)),
                                     (255, 250, 232, 120), k * 0.035), sil))
    img.alpha_composite(clip_to(soft(size, SP(ellipse_pts(-1.00, -0.20, 0.30, 0.10, rot=-0.34)),
                                     (255, 240, 205, 95), k * 0.030), sil))
    img.alpha_composite(clip_to(soft(size, SP(ellipse_pts(-0.52, 0.30, 0.40, 0.16, rot=-0.10)),
                                     (128, 42, 6, 85), k * 0.065), sil))

    # ---- 9) 眉骨（隆起 + 下缘投影）
    brow = tapered((-0.16, -0.54), (-0.60, -0.72), (-0.98, -0.46), 0.30, 0.15)
    img.alpha_composite(clip_to(soft(size, SP(brow), (255, 210, 142, 125), k * 0.030), sil))
    img.alpha_composite(clip_to(soft(size, SP([(x, y + 0.15) for x, y in brow]),
                                     (104, 32, 4, 140), k * 0.045), sil))
    brow2 = tapered((0.00, -0.58), (0.16, -0.66), (0.34, -0.54), 0.16, 0.08)
    img.alpha_composite(clip_to(soft(size, SP(brow2), (255, 200, 130, 95), k * 0.028), sil))

    # ---- 10) 张口：暗腔 + 舌 + 獠牙
    mmask = mask_of(size, SP(mouth_shape()))
    img.paste(Image.new("RGBA", (size, size), MOUTH), (0, 0), mmask)
    img.alpha_composite(clip_to(
        tint(size, mask_of(size, SP(ellipse_pts(-0.80, 0.44, 0.36, 0.12, rot=-0.10))), TONGUE),
        mmask))
    d = ImageDraw.Draw(img)
    for (u, bv, wd, tv) in ((-1.14, 0.27, 0.046, 0.50), (-0.90, 0.33, 0.040, 0.54)):
        shape(d, SP([(u - wd, bv), (u + wd, bv + 0.02), (u + 0.012, tv)]), FANG, OUTLINE, o * 0.16)
    shape(d, SP([(-1.06, 0.46), (-0.84, 0.48), (-0.95, 0.28)]), FANG, OUTLINE, o * 0.14)
    shape(d, SP([(-0.66, 0.54), (-0.48, 0.54), (-0.57, 0.38)]), FANG, OUTLINE, o * 0.12)

    # ---- 11) 鼻孔（钝鼻头侧面）
    shape(d, SP(ellipse_pts(-1.46, -0.02, 0.044, 0.070, rot=0.50)), NOSTRIL)
    shape(d, SP(ellipse_pts(-1.32, 0.08, 0.032, 0.050, rot=0.46)),
          (NOSTRIL[0], NOSTRIL[1], NOSTRIL[2], 170))

    # ---- 12) 双眼
    for (ex, ey, rx, ry, lit) in ((EYE_NEAR[0], EYE_NEAR[1], 0.240, 0.130, 1.0),
                                  (EYE_FAR[0],  EYE_FAR[1],  0.165, 0.095, 0.70)):
        rot = -0.22
        img.alpha_composite(clip_to(soft(size, SP(ellipse_pts(ex, ey, rx * 1.30, ry * 1.45, rot=rot)),
                                         (112, 34, 4, 140), k * 0.040), sil))
        d = ImageDraw.Draw(img)
        shape(d, SP(ellipse_pts(ex, ey, rx, ry, rot=rot)), EYE_DEEP, OUTLINE, o * 0.36)
        shape(d, SP(ellipse_pts(ex, ey, rx * 0.84, ry * 0.76, rot=rot)),
              tuple(int(c * lit) for c in EYE_IRIS[:3]) + (255,))
        shape(d, SP(ellipse_pts(ex - rx * 0.06, ey + ry * 0.06,
                                rx * 0.26, ry * (1.20 if lit > 0.9 else 1.00))), PUPIL)
        if lit > 0.9:
            shape(d, SP(ellipse_pts(ex - rx * 0.40, ey - ry * 0.44, rx * 0.18, ry * 0.22)),
                  (255, 255, 255, 235))

    # ---- 13) 龙须（无描边，靠明度对比读出来）
    for spec in (WHISKER_FAR, WHISKER_NEAR):
        for (p0, c1, c2, p1, w0, w1) in spec:
            shape(d, SP(ribbon([p0, c1, c2, p1], w0, w1)), WHISKER)
            shape(d, SP(ribbon([p0, c1, c2, p1], w0 * 0.40, w1 * 0.30)),
                  (255, 255, 255, 120))

    # ---- 14) 龙髯
    for (p0, c1, c2, p1, w0, w1) in BEARD_SPEC:
        shape(d, SP(ribbon([p0, c1, c2, p1], w0, w1)), BEARD, OUTLINE, o * 0.34)
        shape(d, SP(ribbon([(p0[0], p0[1] - 0.03), c1, c2, p1], w0 * 0.34, w1 * 0.18)),
              (255, 224, 170, 150))

    # ---- 15) 边缘：柔化压暗而非硬黑描边（硬描边是卡通感第一来源）
    edge = outline_mask(sil, o * 2.4).filter(ImageFilter.GaussianBlur(k * 0.009))
    edge = edge.point(lambda a: int(a * 0.72))
    img.alpha_composite(tint(size, edge, OUTLINE))
    return img


# ================================================================ 合成
def compose(size, mode, drg):
    box = {"square": 0.86, "round": 0.80, "fg": 0.62}[mode]
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    if mode != "fg":
        bg = radial(size, BG_IN, BG_OUT, 0.5, 0.26, 1.15)
        m = Image.new("L", (size, size), 0)
        md = ImageDraw.Draw(m)
        if mode == "round":
            md.ellipse([0, 0, size - 1, size - 1], fill=255)
        else:
            md.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.22), fill=255)
        img.paste(bg, (0, 0), m)

        rim = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        rd = ImageDraw.Draw(rim)
        w = max(2, int(size * 0.026))
        bx = [w // 2, w // 2, size - 1 - w // 2, size - 1 - w // 2]
        if mode == "round":
            rd.ellipse(bx, outline=RIM, width=w)
        else:
            rd.rounded_rectangle(bx, radius=int(size * 0.22), outline=RIM, width=w)
        img.alpha_composite(rim.filter(ImageFilter.GaussianBlur(size * 0.003)))

    front = place(drg, size, size, box)
    if mode != "fg":
        shadow = front.filter(ImageFilter.GaussianBlur(size * 0.016))
        shadow.putalpha(shadow.getchannel("A").point(lambda a: int(a * 0.28)))
        img.alpha_composite(shadow, (0, int(size * 0.010)))
    img.alpha_composite(front)
    return img


def banner(w, h, drg):
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bg = radial(max(w, h) * 2, BG_IN, BG_OUT, 0.5, 0.35, 1.25).resize((w, h), Image.LANCZOS)
    img.alpha_composite(bg)
    img.alpha_composite(place(drg, w, h, 0.90, cx=0.30, cy=0.5))
    ImageDraw.Draw(img).rounded_rectangle([0, 0, w - 1, h - 1], radius=int(h * 0.10),
                                          outline=RIM, width=max(2, h // 60))
    return img


def save(img, path, size=None, fmt=None):
    full = os.path.join(ROOT, path)
    out = img if size is None else img.resize((size, size), Image.LANCZOS)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    (out.save(full, "WEBP", lossless=True, quality=100) if fmt == "webp"
     else out.save(full, "PNG"))
    print("  %-58s %sx%s" % (path, out.width, out.height))


def font(sz):
    try:
        from PIL import ImageFont
        return ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", sz)
    except Exception:
        return None


def sheet(drg, path):
    square = compose(SS, "square", drg).resize((1024, 1024), Image.LANCZOS)
    round_ = compose(SS, "round", drg).resize((1024, 1024), Image.LANCZOS)
    fg = compose(SS, "fg", drg).resize((1024, 1024), Image.LANCZOS)

    prev = Image.new("RGBA", (1180, 340), (250, 250, 252, 255))
    x = 24
    for sz in (128, 96, 72, 48):
        prev.alpha_composite(square.resize((sz, sz), Image.LANCZOS), (x, 262 - sz))
        x += sz + 18
    prev.alpha_composite(round_.resize((170, 170), Image.LANCZOS), (x + 16, 80))
    prev.alpha_composite(round_.resize((96, 96), Image.LANCZOS), (x + 36, 264))
    x += 230
    prev.alpha_composite(fg.resize((150, 150), Image.LANCZOS), (x, 90))
    prev.alpha_composite(banner(320, 180, drg), (x + 190, 70))
    ImageDraw.Draw(prev).text((24, 18), "square 128/96/72/48    round 170/96    "
                               "adaptive-fg 150    banner 320x180", fill=(80, 55, 20), font=font(15))
    save(prev, path)


def compare(leaf_drg, plain_drg, path):
    W, H = 1240, 680
    img = Image.new("RGBA", (W, H), (250, 250, 252, 255))
    for i, (label, drg) in enumerate((("A · 带橙叶（保留「橙」梗）", leaf_drg),
                                      ("B · 无叶（纯写实龙）", plain_drg))):
        big = compose(SS, "square", drg).resize((520, 520), Image.LANCZOS)
        rnd = compose(SS, "round", drg).resize((150, 150), Image.LANCZOS)
        x = 30 + i * 610
        img.alpha_composite(big, (x, 60))
        img.alpha_composite(rnd, (x + 540, 60))
        ImageDraw.Draw(img).text((x, 22), label, fill=(60, 40, 16), font=font(22))
        tx = x + 545
        for sz in (96, 72, 64, 48):
            img.alpha_composite(big.resize((sz, sz), Image.LANCZOS), (tx, 232 + (96 - sz) // 2))
            tx += sz + 10
        ImageDraw.Draw(img).text((x, 612), "圆形自适应 + 96 / 72 / 64 / 48 px 实际显示尺寸",
                                 fill=(120, 90, 60), font=font(16))
    save(img, path)


# ================================================================ 主流程
def main():
    preview_only = "--preview-only" in sys.argv
    print("rendering ...  (preview-only)" if preview_only else "rendering ...")

    leaf = dragon(SS, with_leaf=True)
    plain = dragon(SS, with_leaf=False)

    square = compose(SS, "square", leaf).resize((1024, 1024), Image.LANCZOS)
    round_ = compose(SS, "round", leaf).resize((1024, 1024), Image.LANCZOS)
    fg = compose(SS, "fg", leaf).resize((1024, 1024), Image.LANCZOS)

    save(square, "design/icon-master.png")
    save(round_, "design/icon-master-round.png")
    sheet(leaf, "design/icon-preview.png")
    compare(leaf, plain, "design/icon-compare.png")

    if preview_only:
        print("done.  app 资源未改动（--preview-only）")
        return

    for dens, sz in (("mdpi", 128), ("hdpi", 192), ("xhdpi", 256),
                     ("xxhdpi", 384), ("xxxhdpi", 512)):
        save(square, "app/src/main/res/mipmap-%s/ic_launcher.png" % dens, sz)
    for dens, sz in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                     ("xxhdpi", 144), ("xxxhdpi", 192)):
        save(round_, "app/src/main/res/mipmap-%s/ic_launcher_round.webp" % dens, sz, "webp")
    for dens, sz in (("mdpi", 108), ("hdpi", 162), ("xhdpi", 216),
                     ("xxhdpi", 324), ("xxxhdpi", 432)):
        save(fg, "app/src/main/res/drawable-%s/ic_launcher_foreground.png" % dens, sz)

    save(square, "app/src/main/ic_launcher-playstore.png", 512)
    save(round_, "app/src/main/res/drawable-nodpi/ic_logo.png", 600)
    save(banner(320, 180, leaf), "app/src/leanback/res/drawable/ic_banner.png")
    save(banner(640, 360, leaf), "app/src/leanback/res/mipmap-xhdpi/ic_banner.png")
    print("done.")


if __name__ == "__main__":
    main()
