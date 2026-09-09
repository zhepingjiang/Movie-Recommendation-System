package com.movierec.backend.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.movierec.backend.dto.MovieSummaryDto;
import com.movierec.backend.dto.PagedResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Full-text movie search backed by the Elasticsearch {@code movies} index (see
 * backend/scripts/index_movies_to_elasticsearch.py for the index mapping and how it's
 * populated from postgres). Results are hydrated straight from each hit's stored fields --
 * no postgres round-trip -- and come back sorted by relevance score, highest first, since
 * that's Elasticsearch's default result order.
 */
@Service
@RequiredArgsConstructor
public class MovieSearchService {

    private static final String MOVIES_INDEX = "movies";

    /** Must match the mapping's field names and the boosts agreed for this search feature. */
    private static final List<String> BOOSTED_FIELDS =
            List.of("title^10", "director^4", "cast^3", "genres^2", "overview^1");

    private final ElasticsearchClient elasticsearchClient;

    /**
     * @param query free-text search query
     * @param genre optional exact genre name filter, applied alongside the text query (same role
     *     as {@link com.movierec.backend.repository.MovieSpecifications#hasGenre} for the
     *     postgres-backed browse endpoint)
     * @param minRating optional inclusive lower bound on average rating
     * @param page zero-based page number
     * @param size page size
     * @return a page of matching movies; empty (not an error) if nothing matches
     */
    public PagedResponse<MovieSummaryDto> search(
            String query, String genre, BigDecimal minRating, int page, int size) {
        SearchRequest request =
                SearchRequest.of(
                        r ->
                                r.index(MOVIES_INDEX)
                                        .from(page * size)
                                        .size(size)
                                        .query(buildQuery(query, genre, minRating)));

        try {
            SearchResponse<MovieDocument> response = elasticsearchClient.search(request, MovieDocument.class);

            List<MovieSummaryDto> items =
                    response.hits().hits().stream()
                            .map(Hit::source)
                            .filter(Objects::nonNull)
                            .map(this::toSummaryDto)
                            .toList();

            long totalElements =
                    response.hits().total() == null ? items.size() : response.hits().total().value();
            int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

            return new PagedResponse<>(items, page, size, totalElements, totalPages);
        } catch (IOException e) {
            throw new UncheckedIOException("Elasticsearch search failed for query: " + query, e);
        }
    }

    /**
     * Builds the text-relevance query (must) plus any exact-match filters (genre, minimum
     * rating), so the two behave like the postgres-backed browse endpoint's combinable filters
     * ({@link com.movierec.backend.repository.MovieSpecifications}) rather than the ES text
     * match silently ignoring them.
     */
    private Query buildQuery(String query, String genre, BigDecimal minRating) {
        List<Query> filters = new ArrayList<>();
        if (StringUtils.hasText(genre)) {
            filters.add(Query.of(q -> q.term(t -> t.field("genres.keyword").value(genre))));
        }
        if (minRating != null) {
            filters.add(
                    Query.of(q -> q.range(r -> r.number(n -> n.field("averageRating").gte(minRating.doubleValue())))));
        }

        BoolQuery.Builder boolQuery =
                new BoolQuery.Builder()
                        .must(m -> m.multiMatch(mm -> mm.query(query).fields(BOOSTED_FIELDS).fuzziness("AUTO")))
                        .filter(filters);

        return Query.of(q -> q.bool(boolQuery.build()));
    }

    /**
     * Maps an ES hit straight to the same DTO {@code /api/movies} returns, so the frontend can
     * treat search results identically to a browse page. {@code durationMin} isn't indexed (it's
     * unpopulated for every movie in this dataset today) so it's always null here.
     */
    private MovieSummaryDto toSummaryDto(MovieDocument doc) {
        return new MovieSummaryDto(
                doc.movieId(),
                doc.title(),
                doc.posterUrl(),
                doc.overview(),
                doc.releaseDate(),
                null,
                doc.averageRating(),
                doc.genres());
    }

    /** Mirrors the ES document shape written by index_movies_to_elasticsearch.py. */
    private record MovieDocument(
            Long movieId,
            String title,
            String overview,
            String director,
            List<String> cast,
            List<String> genres,
            LocalDate releaseDate,
            BigDecimal averageRating,
            String posterUrl) {}
}
