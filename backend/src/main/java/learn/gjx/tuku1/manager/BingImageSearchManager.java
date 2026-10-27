package learn.gjx.tuku1.manager;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bing 图片搜索适配器，负责页面请求和候选原图地址解析。
 */
@Component
@Slf4j
public class BingImageSearchManager {

    private static final String SEARCH_URL = "https://cn.bing.com/images/async?q=%s&mmasync=1";

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    public List<String> search(String searchText) {
        String searchUrl = String.format(SEARCH_URL, encode(searchText));
        try {
            Document document = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .referrer("https://cn.bing.com/images/search")
                    .timeout(10000)
                    .get();
            return extractImageUrls(document);
        } catch (IOException e) {
            log.error("获取 Bing 图片搜索结果失败, searchText: {}", searchText, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取图片搜索结果失败");
        }
    }

    /**
     * 优先读取 Bing data-m 中的 murl 原图地址，缺失时回退到页面缩略图地址。
     */
    List<String> extractImageUrls(Document document) {
        Element container = document.getElementsByClass("dgControl").first();
        if (container == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未获取到图片搜索结果");
        }

        Set<String> candidateUrls = new LinkedHashSet<>();
        Elements metadataElements = container.select("[data-m], [m]");
        for (Element element : metadataElements) {
            String metadata = StrUtil.blankToDefault(element.attr("data-m"), element.attr("m"));
            String originalUrl = parseOriginalUrl(metadata);
            addNormalized(candidateUrls, originalUrl);
        }

        for (Element imageElement : container.select("img.mimg")) {
            addNormalized(candidateUrls, imageElement.attr("src"));
        }
        if (candidateUrls.isEmpty()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未获取到图片搜索结果");
        }
        return new ArrayList<>(candidateUrls);
    }

    private String parseOriginalUrl(String metadata) {
        if (StrUtil.isBlank(metadata) || !JSONUtil.isTypeJSON(metadata)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(metadata).getStr("murl");
        } catch (Exception e) {
            log.debug("忽略无法解析的 Bing 图片元数据");
            return null;
        }
    }

    private void addNormalized(Set<String> urls, String imageUrl) {
        String normalized = normalizeImageUrl(imageUrl);
        if (normalized != null) {
            urls.add(normalized);
        }
    }

    String normalizeImageUrl(String imageUrl) {
        if (StrUtil.isBlank(imageUrl)) {
            return null;
        }
        String normalized = imageUrl.trim();
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        String lowerUrl = normalized.toLowerCase(Locale.ROOT);
        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
            return null;
        }
        return normalized;
    }

    private String encode(String searchText) {
        try {
            return URLEncoder.encode(searchText, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "搜索关键词编码失败");
        }
    }
}
