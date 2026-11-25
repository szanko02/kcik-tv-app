/**
 * File: GithubRelease.kt
 *
 * Description: Implementation of Github Release functionality.
 *
 * Author: Xacnio
 *
 */
package dev.xacnio.kciktv.shared.data.model

import com.google.gson.annotations.SerializedName

data class GithubRelease(
    @SerializedName("tag_name") val tagName: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("prerelease") val prerelease: Boolean = false,
    @SerializedName("published_at") val publishedAt: String? = null,
    @SerializedName("assets") val assets: List<GithubAsset>? = null,
    @SerializedName("body") val body: String? = null
) {
    val safeTagName: String get() = tagName.orEmpty()
    val safeName: String get() = name?.takeIf { it.isNotBlank() } ?: safeTagName
    val safePublishedAt: String get() = publishedAt.orEmpty()
    val safeAssets: List<GithubAsset> get() = assets.orEmpty()
    val safeBody: String get() = body.orEmpty()
}

data class GithubAsset(
    @SerializedName("name") val name: String? = null,
    @SerializedName("browser_download_url") val downloadUrl: String? = null,
    @SerializedName("size") val size: Long = 0L
) {
    val safeName: String get() = name.orEmpty()
    val safeDownloadUrl: String get() = downloadUrl.orEmpty()
}
