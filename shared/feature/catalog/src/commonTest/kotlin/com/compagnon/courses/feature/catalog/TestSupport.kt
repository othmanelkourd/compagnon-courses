package com.compagnon.courses.feature.catalog

import app.cash.turbine.ReceiveTurbine
import com.compagnon.courses.core.data.CatalogRepository
import com.compagnon.courses.core.database.createDatabase
import com.compagnon.courses.core.network.CatalogApi
import com.compagnon.courses.core.testing.createInMemoryDriver
import com.compagnon.courses.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

internal const val VERSION_JSON: String = """
{
  "country": "FR",
  "version": 1,
  "publishedAt": "2026-10-24T08:30:00Z",
  "locales": ["fr-FR", "fr", "en"],
  "categoryCount": 18,
  "productCount": 120
}
"""

internal val loadedVersion1 = CatalogStatusUiState.Loaded(
    country = "FR",
    version = 1,
    publishedAt = "2026-10-24T08:30:00Z",
    categoryCount = 18,
    productCount = 120,
    locales = listOf("fr-FR", "fr", "en"),
)

internal const val COUNTRY_PROBLEM_JSON: String = """
{
  "title": "Country not available",
  "status": 404,
  "detail": "No catalog for country 'ZZ'",
  "code": "COUNTRY_NOT_AVAILABLE"
}
"""

internal fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

internal fun MockRequestHandleScope.respondProblem(body: String, status: HttpStatusCode): HttpResponseData =
    respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.ProblemJson.toString()))

/**
 * A [CatalogRepository] over an in-memory database and a [CatalogApi] backed by a [MockEngine], with the
 * production client configuration.
 */
internal fun mockCatalogApi(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): Pair<CatalogRepository, MockEngine> {
    val engine = MockEngine(handler)
    val api = CatalogApi(createHttpClient(platform = "test", appVersion = "0.0.0", engine = engine), "http://api.test")
    return CatalogRepository(createDatabase(createInMemoryDriver()), api) to engine
}

/** The `/v1/catalog/version` requests received by [this] engine. */
internal val MockEngine.versionRequests: Int
    get() = requestHistory.count { it.url.encodedPath == "/v1/catalog/version" }

/** Skips [CatalogStatusUiState.Loading] items and returns the first settled state. */
internal suspend fun ReceiveTurbine<CatalogStatusUiState>.awaitSettled(): CatalogStatusUiState {
    while (true) {
        val item = awaitItem()
        if (item != CatalogStatusUiState.Loading) return item
    }
}
