// 生成一个用于测试播放器的 MP4 视频（macOS 自带 AVFoundation，无需 ffmpeg）
// 用法: swift tools/make_test_video.swift <输出路径> [秒数]

import AVFoundation
import CoreGraphics
import CoreVideo
import Foundation

let args = CommandLine.arguments
let outputPath = args.count > 1 ? args[1] : "test.mp4"
let seconds = args.count > 2 ? (Int(args[2]) ?? 10) : 10

let width = 1280
let height = 720
let fps: Int32 = 30
let totalFrames = Int(fps) * seconds

let outputURL = URL(fileURLWithPath: outputPath)
try? FileManager.default.removeItem(at: outputURL)
try? FileManager.default.createDirectory(
    at: outputURL.deletingLastPathComponent(),
    withIntermediateDirectories: true
)

guard let writer = try? AVAssetWriter(outputURL: outputURL, fileType: .mp4) else {
    FileHandle.standardError.write("无法创建 AVAssetWriter\n".data(using: .utf8)!)
    exit(1)
}

let videoSettings: [String: Any] = [
    AVVideoCodecKey: AVVideoCodecType.h264,
    AVVideoWidthKey: width,
    AVVideoHeightKey: height,
    AVVideoCompressionPropertiesKey: [
        AVVideoAverageBitRateKey: 2_000_000,
        AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel,
    ],
]

let input = AVAssetWriterInput(mediaType: .video, outputSettings: videoSettings)
input.expectsMediaDataInRealTime = false

let adaptor = AVAssetWriterInputPixelBufferAdaptor(
    assetWriterInput: input,
    sourcePixelBufferAttributes: [
        kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
        kCVPixelBufferWidthKey as String: width,
        kCVPixelBufferHeightKey as String: height,
    ]
)

guard writer.canAdd(input) else {
    FileHandle.standardError.write("无法添加视频轨道\n".data(using: .utf8)!)
    exit(1)
}
writer.add(input)

guard writer.startWriting() else {
    FileHandle.standardError.write("startWriting 失败: \(writer.error?.localizedDescription ?? "?")\n".data(using: .utf8)!)
    exit(1)
}
writer.startSession(atSourceTime: .zero)

func makeFrame(_ frame: Int) -> CVPixelBuffer? {
    var pixelBuffer: CVPixelBuffer?
    let status = CVPixelBufferCreate(
        kCFAllocatorDefault, width, height, kCVPixelFormatType_32BGRA, nil, &pixelBuffer
    )
    guard status == kCVReturnSuccess, let buffer = pixelBuffer else { return nil }

    CVPixelBufferLockBaseAddress(buffer, [])
    defer { CVPixelBufferUnlockBaseAddress(buffer, []) }

    guard let context = CGContext(
        data: CVPixelBufferGetBaseAddress(buffer),
        width: width,
        height: height,
        bitsPerComponent: 8,
        bytesPerRow: CVPixelBufferGetBytesPerRow(buffer),
        space: CGColorSpaceCreateDeviceRGB(),
        bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue
    ) else { return nil }

    let progress = Double(frame) / Double(max(totalFrames - 1, 1))

    // 背景渐变
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let colors = [
        CGColor(red: 0.05, green: 0.09, blue: 0.22, alpha: 1),
        CGColor(red: 0.10 + 0.45 * progress, green: 0.15, blue: 0.55 - 0.35 * progress, alpha: 1),
    ] as CFArray
    if let gradient = CGGradient(colorsSpace: colorSpace, colors: colors, locations: [0, 1]) {
        context.drawLinearGradient(
            gradient,
            start: CGPoint(x: 0, y: 0),
            end: CGPoint(x: width, y: height),
            options: []
        )
    }

    // 移动的方块，用来直观判断画面是否在动
    context.setFillColor(CGColor(red: 0.30, green: 0.55, blue: 0.96, alpha: 1))
    let boxSize = 160.0
    let travel = Double(width) - boxSize
    context.fill(CGRect(x: travel * progress, y: Double(height) / 2 - boxSize / 2,
                        width: boxSize, height: boxSize))

    // 每秒变换一次的进度条，便于确认时间轴
    context.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 0.9))
    context.fill(CGRect(x: 0, y: 0, width: Double(width) * progress, height: 24))

    return buffer
}

var frameIndex = 0
while frameIndex < totalFrames {
    if !input.isReadyForMoreMediaData {
        Thread.sleep(forTimeInterval: 0.005)
        continue
    }
    guard let buffer = makeFrame(frameIndex) else { break }
    let time = CMTime(value: CMTimeValue(frameIndex), timescale: fps)
    if !adaptor.append(buffer, withPresentationTime: time) {
        FileHandle.standardError.write("append 失败于第 \(frameIndex) 帧\n".data(using: .utf8)!)
        break
    }
    frameIndex += 1
}

input.markAsFinished()
let semaphore = DispatchSemaphore(value: 0)
writer.finishWriting { semaphore.signal() }
semaphore.wait()

if writer.status == .completed {
    let attributes = try? FileManager.default.attributesOfItem(atPath: outputPath)
    let size = (attributes?[.size] as? Int) ?? 0
    print("生成成功: \(outputPath) (\(size) bytes, \(seconds)s, \(width)x\(height))")
} else {
    FileHandle.standardError.write("写入失败: \(writer.error?.localizedDescription ?? "?")\n".data(using: .utf8)!)
    exit(1)
}
