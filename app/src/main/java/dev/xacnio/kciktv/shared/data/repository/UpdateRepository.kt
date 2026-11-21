/**
 * File: UpdateRepository.kt
 *
 * Description: Implementation of Update Repository functionality.
 *
 * Author: Xacnio
 *
 */
package dev.xacnio.kciktv.shared.data.repository

import dev.xacnio.kciktv.shared.data.api.RetrofitClient
import dev.xacnio.kciktv.shared.data.model.GithubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateRepository {
    private val githubService = RetrofitClient.githubService

    suspend fun getLatestRelease(
        channel: String,
        source: UpdateSource = UpdateSource.FORK
    ): Result<GithubRelease?> = withContext(Dispatchers.IO) {
        try {
            // Fetch list of releases (default page size is 30, sufficient for finding latest)
            val response = githubService.getReleases(source.owner, source.repo)

            if (response.isSuccessful) {
                val releases = response.body() ?: emptyList()
                Result.success(UpdateReleaseSelector.selectLatest(releases, channel))
            } else {
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllReleases(
        source: UpdateSource = UpdateSource.FORK
    ): Result<List<GithubRelease>?> = withContext(Dispatchers.IO) {
        try {
            val response = githubService.getReleases(source.owner, source.repo)
            if (response.isSuccessful) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
