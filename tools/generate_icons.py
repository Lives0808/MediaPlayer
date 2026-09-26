#!/usr/bin/env python3
"""生成 MediaPlayer 的启动图标 PNG（纯 Python 实现，不依赖 Pillow）。

- ic_launcher.png        传统方形图标：圆角矩形 + 四周留边距（符合启动器裁切规范）
- ic_launcher_round.png  圆形图标：正圆
"""
import os
import struct
import zlib

BG = (0x10, 0x13, 0x1A)
FG = (0x4E, 0x8C, 0xF5)
SS = 4  # 超采样倍数（抗锯齿）

# 播放三角在内容区域内的归一化坐标
TRI = ((0.34, 0.26), (0.34, 0.74), (0.76, 0.50))


def point_in_rounded_rect(px, py, w, h, r):
    if px < 0 or py < 0 or px > w or py > h:
        return False
    cx = min(max(px, r), w - r)
    cy = min(max(py, r), h - r)
    dx = px - cx
    dy = py - cy
    return dx * dx + dy * dy <= r * r


def point_in_triangle(px, py, inset, inner):
    (ax, ay), (bx, by), (cx, cy) = [
        (inset + x * inner, inset + y * inner) for x, y in TRI
    ]
    d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by)
    d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy)
    d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay)
    has_neg = d1 < 0 or d2 < 0 or d3 < 0
    has_pos = d1 > 0 or d2 > 0 or d3 > 0
    return not (has_neg and has_pos)


def render(size, inset_ratio, radius_ratio):
    inset = size * inset_ratio
    inner = size - 2 * inset
    radius = inner * radius_ratio
    total = SS * SS
    rows = []

    for y in range(size):
        row = []
        for x in range(size):
            shape_hits = 0
            tri_hits = 0
            for sy in range(SS):
                for sx in range(SS):
                    px = x + (sx + 0.5) / SS
                    py = y + (sy + 0.5) / SS
                    if point_in_rounded_rect(px - inset, py - inset, inner, inner, radius):
                        shape_hits += 1
                        if point_in_triangle(px, py, inset, inner):
                            tri_hits += 1
            if shape_hits == 0:
                row.append((0, 0, 0, 0))
                continue
            t = tri_hits / shape_hits
            color = tuple(int(round(BG[i] * (1 - t) + FG[i] * t)) for i in range(3))
            alpha = int(round(255 * shape_hits / total))
            row.append((color[0], color[1], color[2], alpha))
        rows.append(row)
    return rows


def write_png(path, rows):
    height = len(rows)
    width = len(rows[0])
    raw = b""
    for row in rows:
        raw += b"\x00" + b"".join(bytes(px) for px in row)

    def chunk(kind, data):
        return (
            struct.pack(">I", len(data))
            + kind
            + data
            + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
        )

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)


def main():
    base = os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "app", "src", "main", "res",
    )
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in densities.items():
        # 方形图标：留 8% 边距 + 圆角
        write_png(
            os.path.join(base, folder, "ic_launcher.png"),
            render(size, inset_ratio=0.08, radius_ratio=0.20),
        )
        # 圆形图标：留 4% 边距的正圆
        write_png(
            os.path.join(base, folder, "ic_launcher_round.png"),
            render(size, inset_ratio=0.04, radius_ratio=0.5),
        )
        print(f"{folder}: {size}x{size} ok")


if __name__ == "__main__":
    main()
