// Ported from NewPipeExtractor (GPL-3.0-or-later), YoutubeChartsBaseKioskExtractor.java.
package org.tubepipe.core.service.kiosk;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.DISABLE_PRETTY_PRINT_PARAMETER;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.fixThumbnailUrl;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getOriginReferrerHeaders;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getValidJsonResponseBody;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.utils.JsonUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Base class parsing responses from YouTube Charts for all trending video charts.
 *
 * <p>
 * YouTube Charts is not offered in every country YouTube is: the set below comes from the
 * {@code LAUNCHED_CHART_COUNTRIES} array in the YouTube Charts HTML. Bangladesh is not in it.
 * </p>
 */
public abstract class YoutubeChartsBaseKioskExtractor extends KioskExtractor<StreamInfoItem> {

    public static final Set<String> YT_CHARTS_SUPPORTED_COUNTRY_CODES = Set.of(
            "AE", "AR", "AT", "AU", "BE", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CZ", "DE",
            "DK", "DO", "EC", "EE", "EG", "ES", "FI", "FR", "GB", "GT", "HN", "HU", "ID", "IE",
            "IL", "IN", "IS", "IT", "JP", "KE", "KR", "LU", "MX", "NG", "NI", "NL", "NO", "NZ",
            "PA", "PE", "PL", "PT", "PY", "RO", "RS", "RU", "SA", "SE", "SV", "TR", "TZ", "UA",
            "UG", "US", "UY", "ZA", "ZW");

    protected static final String YT_CHARTS_ENDPOINT =
            "https://charts.youtube.com/youtubei/v1/browse?alt=json&"
                    + DISABLE_PRETTY_PRINT_PARAMETER;

    // PipePipeExtractor keeps these constants package-private in ClientsConstants and has no
    // WEB_MUSIC_ANALYTICS InnertubeClientRequestInfo, so the values are repeated here.
    private static final String CHARTS_CLIENT_ID = "31";
    private static final String CHARTS_CLIENT_NAME = "WEB_MUSIC_ANALYTICS";
    private static final String CHARTS_CLIENT_VERSION = "2.0";

    protected final String chartType;
    protected JsonObject browseResponse;

    protected YoutubeChartsBaseKioskExtractor(final StreamingService streamingService,
                                              final ListLinkHandler linkHandler,
                                              final String kioskId,
                                              final String chartType) {
        super(streamingService, linkHandler, kioskId);
        this.chartType = chartType;
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        final Localization localization = getExtractorLocalization();
        final ContentCountry contentCountry = getExtractorContentCountry();

        // Same fields as upstream's prepareJsonBuilder, written out because the
        // InnertubeClientRequestInfo constructors are private in PipePipeExtractor.
        final byte[] body = JsonWriter.string()
                .object()
                .object("context")
                .object("client")
                .value("clientName", CHARTS_CLIENT_NAME)
                .value("clientVersion", CHARTS_CLIENT_VERSION)
                .value("hl", localization.getLocalizationCode())
                .value("gl", contentCountry.getCountryCode())
                .value("utcOffsetMinutes", 0)
                .end()
                .object("request")
                .array("internalExperimentFlags")
                .end()
                .value("useSsl", true)
                .end()
                .object("user")
                .value("lockedSafetyMode", false)
                .end()
                .end()
                .value("browseId", "FEmusic_analytics_charts_home")
                .value("query", "perspective=CHART_DETAILS&chart_params_country_code="
                        + contentCountry.getCountryCode() + "&chart_params_chart_type="
                        + chartType)
                .end()
                .done()
                .getBytes(StandardCharsets.UTF_8);

        // PipePipeExtractor has no postWithContentTypeJson, so the header is set by hand, and
        // getClientHeaders is package-private there, so those two headers are built here too.
        final Map<String, List<String>> headers =
                new HashMap<>(getOriginReferrerHeaders("https://charts.youtube.com"));
        headers.put("X-YouTube-Client-Name", List.of(CHARTS_CLIENT_ID));
        headers.put("X-YouTube-Client-Version", List.of(CHARTS_CLIENT_VERSION));
        headers.put("Content-Type", List.of("application/json"));

        browseResponse = JsonUtils.toJsonObject(getValidJsonResponseBody(
                getDownloader().post(YT_CHARTS_ENDPOINT, headers, body, localization)));
    }

    @Nonnull
    @Override
    public abstract String getName() throws ParsingException;

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        final JsonArray videos = browseResponse.getObject("contents")
                .getObject("sectionListRenderer")
                .getArray("contents")
                .getObject(0)
                .getObject("musicAnalyticsSectionRenderer")
                .getObject("content")
                .getArray("videos")
                .getObject(0)
                .getArray("videoViews");

        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());

        videos.stream().filter(JsonObject.class::isInstance).map(JsonObject.class::cast)
                .forEachOrdered(video -> collector.commit(
                        new YoutubeChartsVideoInfoItemExtractor(video)));

        return new InfoItemsPage<>(collector, null);
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page) {
        // There is no continuation in charts
        return InfoItemsPage.emptyPage();
    }

    static final class YoutubeChartsVideoInfoItemExtractor implements StreamInfoItemExtractor {

        @Nonnull
        private final JsonObject videoObject;

        YoutubeChartsVideoInfoItemExtractor(@Nonnull final JsonObject videoObject) {
            this.videoObject = videoObject;
        }

        @Override
        public StreamType getStreamType() {
            // There are only video streams in YouTube Charts, at least for now
            return StreamType.VIDEO_STREAM;
        }

        @Override
        public boolean isAd() {
            return false;
        }

        @Override
        public long getDuration() {
            return videoObject.getInt("videoDuration", -1);
        }

        @Override
        public long getViewCount() {
            // View counts aren't returned, at least for now
            return -1;
        }

        @Override
        public String getUploaderName() {
            return videoObject.getString("channelName");
        }

        @Override
        public String getUploaderUrl() throws ParsingException {
            final String channelId = videoObject.getString("externalChannelId");

            if (isNullOrEmpty(channelId)) {
                throw new ParsingException("Could not get channel ID");
            }

            return YoutubeChannelLinkHandlerFactory.getInstance().getUrl("channel/" + channelId);
        }

        @Override
        public boolean isUploaderVerified() {
            // We don't have any info on this, at least for now
            return false;
        }

        @Nullable
        @Override
        public String getTextualUploadDate() {
            return null;
        }

        @Nonnull
        @Override
        public DateWrapper getUploadDate() {
            final JsonObject releaseDate = videoObject.getObject("releaseDate");
            final LocalDate localDate = LocalDate.of(releaseDate.getInt("year"),
                    releaseDate.getInt("month"), releaseDate.getInt("day"));
            // The response is meant to use a 0 offset to UTC, but YouTube Charts only says so in
            // its HTTP headers, so the offset is applied here.
            // We don't have more than the release day, hence isApproximate=true.
            return new DateWrapper(localDate.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime(), true);
        }

        @Override
        public String getName() {
            return videoObject.getString("title");
        }

        @Override
        public String getUrl() throws ParsingException {
            return YoutubeStreamLinkHandlerFactory.getInstance().getUrl(
                    videoObject.getString("id"));
        }

        @Override
        public String getThumbnailUrl() throws ParsingException {
            // PipePipeExtractor's InfoItemExtractor returns a single URL, not a list of Images
            // as upstream does, and has no getThumbnailsFromInfoItem; take the last (largest).
            final JsonArray thumbnails = videoObject.getObject("thumbnail").getArray("thumbnails");
            if (thumbnails.isEmpty()) {
                throw new ParsingException("Could not get thumbnails from InfoItem");
            }
            final String url = thumbnails.getObject(thumbnails.size() - 1).getString("url");
            if (isNullOrEmpty(url)) {
                throw new ParsingException("Could not get thumbnail URL from InfoItem");
            }
            return fixThumbnailUrl(url);
        }
    }
}
