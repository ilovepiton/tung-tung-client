import Foundation
import ImageIO
import UniformTypeIdentifiers

enum MediaFiles {
    static func skyPNG(_ source: URL) throws -> Data {
        let size = try source.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard size > 0, size <= 32 * 1024 * 1024,
              let image = CGImageSourceCreateWithURL(source as CFURL, [kCGImageSourceShouldCache: false] as CFDictionary),
              let properties = CGImageSourceCopyPropertiesAtIndex(image, 0, nil) as? [CFString: Any],
              let width = properties[kCGImagePropertyPixelWidth] as? Int,
              let height = properties[kCGImagePropertyPixelHeight] as? Int,
              width > 0, height > 0, width <= 16384, height <= 16384, width * height <= 64_000_000 else {
            throw NSError(domain: "TungMedia", code: 1, userInfo: [NSLocalizedDescriptionKey: "Choose a PNG or JPG image under 32 MB and 64 megapixels."])
        }
        let options: [CFString: Any] = [kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceThumbnailMaxPixelSize: 2048, kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true]
        guard let thumbnail = CGImageSourceCreateThumbnailAtIndex(image, 0, options as CFDictionary) else {
            throw NSError(domain: "TungMedia", code: 2, userInfo: [NSLocalizedDescriptionKey: "This image could not be opened."])
        }
        let data = NSMutableData()
        guard let output = CGImageDestinationCreateWithData(data, UTType.png.identifier as CFString, 1, nil) else {
            throw NSError(domain: "TungMedia", code: 3)
        }
        CGImageDestinationAddImage(output, thumbnail, nil)
        guard CGImageDestinationFinalize(output) else { throw NSError(domain: "TungMedia", code: 4) }
        return data as Data
    }
    static func saveSky(_ data: Data, directory: URL) throws {
        let fm = FileManager.default
        let nested = directory.appendingPathComponent("tun-tun-26.3", isDirectory: true)
        let profile = fm.fileExists(atPath: nested.appendingPathComponent("config").path) ? nested : directory
        let media = profile.appendingPathComponent("config/tun-tun-media", isDirectory: true)
        try fm.createDirectory(at: media, withIntermediateDirectories: true)
        let image = media.appendingPathComponent("sky.png")
        if fm.fileExists(atPath: image.path) {
            try Data(contentsOf: image).write(to: media.appendingPathComponent("previous-sky.png"), options: .atomic)
        }
        try data.write(to: image, options: .atomic)
    }
}
