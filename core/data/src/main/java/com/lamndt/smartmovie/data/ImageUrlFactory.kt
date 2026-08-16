package com.lamndt.smartmovie.data

import com.lamndt.smartmovie.model.ImageConfiguration
import com.lamndt.smartmovie.model.ImageKind

class ImageUrlFactory(private val configuration: () -> ImageConfiguration) {
    fun url(path: String?, kind: ImageKind): String? {
        if (path.isNullOrBlank()) return null
        val config = configuration()
        val available = when (kind) {
            ImageKind.POSTER -> config.posterSizes
            ImageKind.BACKDROP -> config.backdropSizes
            ImageKind.PROFILE -> config.profileSizes
        }
        val preferred = when (kind) {
            ImageKind.POSTER -> listOf("w500", "w342")
            ImageKind.BACKDROP -> listOf("w1280", "w780")
            ImageKind.PROFILE -> listOf("w185", "h632")
        }.firstOrNull(available::contains) ?: available.firstOrNull() ?: "original"
        return config.secureBaseUrl.trimEnd('/') + "/" + preferred + "/" + path.trimStart('/')
    }
}
