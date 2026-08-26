package com.lamndt.smartmovie.multiplatform.model

fun presentedImages(values: List<ImageAsset>, limit: Int = 20): List<ImageAsset> =
    values.filter { it.filePath.isNotBlank() }.distinctBy(ImageAsset::filePath).take(limit)

fun playableVideos(values: List<Video>, limit: Int = 12): List<Video> =
    values.filter { it.site.equals("YouTube", ignoreCase = true) && it.key.isNotBlank() }
        .distinctBy(Video::key)
        .take(limit)

fun presentedExternalIds(values: Map<String, String>, limit: Int = 8): List<Pair<String, String>> =
    values.filterValues(String::isNotBlank).toList().sortedBy { it.first }.take(limit)
