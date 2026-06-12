package com.phisher

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class CineHDProvider : MainAPI() {
    override var mainUrl = "https://cinehd.app"
    override var name = "CineHD"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override var lang = "en"
    override val hasMainPage = true

    // Fetches the Homepage list of Movies and Tv Shows
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(mainUrl).document
        val homePages = mutableListOf<HomePageList>()

        // 1. Scrape latest movies section
        val movies = document.select("div.items article, div.post-item, .ml-item, div.movie-item").mapNotNull {
            it.toSearchResult()
        }
        if (movies.isNotEmpty()) {
            homePages.add(HomePageList("Latest Movies", movies))
        }

        // 2. Scrape latest TV series if exists
        val series = document.select("div.tv-servers article, div.tvseries-item, .tv-item").mapNotNull {
            it.toSearchResult()
        }
        if (series.isNotEmpty()) {
            homePages.add(HomePageList("Latest TV Series", series))
        }

        return HomePageResponse(homePages)
    }

    // Convert DOM Article / Element into Cloudstream Search Result Model
    private fun Element.toSearchResult(): SearchResult? {
        val title = this.select("h3, h2, .entry-title, .title, a").firstOrNull()?.text() ?: return null
        val href = this.select("a").firstOrNull()?.attr("href") ?: return null
        
        // Grab thumbnail / poster
        val posterUrl = this.select("img").firstOrNull()?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        } ?: ""

        val quality = this.select(".quality, .fea, .grade").firstOrNull()?.text() ?: ""
        
        // Determine whether this is a TV Show or Movie card
        val isTv = href.contains("/series/") || href.contains("/tvshows/") || this.text().contains("Season", ignoreCase = true)
        
        return if (isTv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.quality = getQualityFromString(quality)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = getQualityFromString(quality)
            }
        }
    }

    // Handle searching query (e.g. site_url/?s=Avengers)
    override suspend fun search(query: String): List<SearchResult> {
        val searchUrl = "$mainUrl/?s=${query}"
        val response = app.get(searchUrl).text
        val document = Jsoup.parse(response)

        return document.select("div.result-item article, .post-item, .ml-item, div.movie-shadow").mapNotNull {
            it.toSearchResult()
        }
    }

    // Scrapes movie or series details page
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        
        val title = document.select("h1, .single-title, .entry-title").firstOrNull()?.text()?.trim() ?: return null
        val poster = document.select(".poster img, .cover img, meta[property=og:image]").firstOrNull()?.let {
            it.attr("src").ifEmpty { it.attr("content") }
        } ?: ""
        
        val plot = document.select(".plot, .entry-content p, .description, #info-movie-desc").text().trim()
        val rating = document.select(".rating, .num, .imdb-rating").firstOrNull()?.text()?.trim()
        val year = document.select(".date, .year, .release-date").firstOrNull()?.text()?.trim()?.toIntOrNull()

        // Detect if webpage contains series/season content, or simple movie
        val isTv = url.contains("/series/") || url.contains("/tvshows/") || document.select(".episodes, .season").isNotEmpty()

        return if (isTv) {
            // Retrieve all episodes for TV shows
            val episodes = mutableListOf<Episode>()
            document.select(".episodes a, ul.episodes li, .ep-item").forEachIndexed { index, element ->
                val epHref = element.attr("href").ifEmpty { element.select("a").attr("href") }
                val epTitle = element.text().trim().ifEmpty { "Episode ${index + 1}" }
                if (epHref.isNotEmpty()) {
                    episodes.add(Episode(
                        data = epHref,
                        name = epTitle,
                        episode = index + 1
                    ))
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.rating = rating?.toIntOrNull()
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.rating = rating?.toIntOrNull()
            }
        }
    }

    // Extract stream source from iframe links on details page
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // Find iframe URLs embedded in player slots or tabs
        val iframeLinks = mutableListOf<String>()
        
        // Scrape typical source tags inside video frames
        document.select("iframe[src], iframe[data-src], iframe[data-lazy-src], a.play-btn").forEach {
            var src = it.attr("src").ifEmpty { it.attr("data-src") }
            if (src.isEmpty()) {
                src = it.attr("href")
            }
            if (src.startsWith("//")) {
                src = "https:" + src
            }
            if (src.isNotEmpty() && !src.contains("about:blank") && !src.contains("googletagmanager")) {
                iframeLinks.add(src)
            }
        }

        // Loop through extracted iframe/stream URLs and pass them to Cloudstream's native resolver
        iframeLinks.forEach { link ->
            // Use Cloudstream native resolvers (e.g. DoodStream, Streamtape, Filemoon etc.)
            loadExtractor(link, subtitleCallback, callback)
        }

        return true
    }
}
