package com.dragonguard.core.domain.search.client

import com.dragonguard.core.domain.search.client.dto.SearchGitRepoClientResponse
import com.dragonguard.core.domain.search.dto.SearchGitRepoRequest
import com.dragonguard.core.global.exception.RestClientException
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class SearchGitRepoClient(
    private val restClient: RestClient,
) {
    companion object {
        private const val PATH = "search/%repositories?q=%s&per_page=10&page=%d"
        private const val FILTER_DELIMITER = "%%20"
    }

    fun request(
        request: SearchGitRepoRequest,
        githubToken: String,
    ): SearchGitRepoClientResponse =
        restClient
            .get()
            .uri(PATH.format(request.name, request.page) + request.filters?.joinToString { FILTER_DELIMITER })
            .headers { it.setBearerAuth(githubToken) }
            .accept(MediaType.APPLICATION_JSON)
            .acceptCharset(Charsets.UTF_8)
            .retrieve()
            .body(SearchGitRepoClientResponse::class.java)
            ?: throw RestClientException.searchGitRepo()
}