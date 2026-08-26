package com.lamndt.smartmovie.multiplatform.model

fun presentedReviews(values: List<Review>, limit: Int = 4): List<Review> =
    values.filter { it.content.isNotBlank() }.distinctBy(Review::id).take(limit)

fun presentedEditorialTitles(
    values: List<TitleSummary>,
    currentLibraryKey: String? = null,
    includeAdult: Boolean = false,
    limit: Int = 20,
): List<TitleSummary> = values.filter { (includeAdult || !it.adult) && it.libraryKey != currentLibraryKey }
    .distinctBy(TitleSummary::libraryKey)
    .take(limit)
