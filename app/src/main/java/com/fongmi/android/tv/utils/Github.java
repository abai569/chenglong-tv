package com.fongmi.android.tv.utils;

import androidx.annotation.NonNull;

import com.github.catvod.net.OkHttp;

import java.util.Arrays;
import java.util.List;

/**
 * GitHub 资源代理。
 *
 * <p>国内直连 raw.githubusercontent.com / github.com 极不稳定，这里按顺序依次降级：
 * 自建代理 → 公共加速 → 直连。任一环节拿到内容即返回，全部失败才返回空串。
 *
 * <p>代理规则：
 * <ul>
 *   <li>raw 文件：{@code https://raw.githubusercontent.com/a/b/c} → {@code https://代理/https://raw.githubusercontent.com/a/b/c}</li>
 *   <li>Release 附件：{@code https://github.com/a/b/releases/download/...} → {@code https://代理/https://github.com/...}</li>
 * </ul>
 */
public class Github {

    public static final String REPO = "abai569/chenglong-tv";

    /** 按优先级排列的加速前缀，空串表示直连。 */
    private static final List<String> PROXIES = Arrays.asList(
            "https://git-proxy.abai.eu.org/",
            "https://gh-proxy.com/",
            "https://ghfast.top/",
            ""
    );

    private static final String RAW_BASE = "https://raw.githubusercontent.com/" + REPO + "/";
    private static final String RELEASE_BASE = "https://github.com/" + REPO + "/releases/";

    /**
     * 依次用每个代理请求，返回第一个非空响应。
     *
     * @param rawUrl 原始 GitHub 地址（不带代理前缀）
     * @return 响应正文；全部失败返回空串
     */
    public static String fetch(String rawUrl) {
        for (String proxy : PROXIES) {
            String body = OkHttp.string(proxy + rawUrl);
            if (body != null && !body.trim().isEmpty()) return body;
        }
        return "";
    }

    /** 取指定分支上的 raw 文件。 */
    public static String raw(String branch, String path) {
        return fetch(RAW_BASE + branch + "/" + path);
    }

    /**
     * 生成 Release 附件的候选 URL 列表（含各代理）。
     *
     * <p>下载场景不能像 {@link #fetch} 那样先探测再取（会下两遍），
     * 因此返回候选列表交给下载器逐个尝试。
     */
    @NonNull
    public static List<String> releaseCandidates(String tag, String assetName) {
        String raw = RELEASE_BASE + "download/" + tag + "/" + assetName;
        return candidates(raw);
    }

    /** 给任意 GitHub 地址套上全部代理前缀，末尾附加直连。 */
    @NonNull
    public static List<String> candidates(String rawUrl) {
        java.util.ArrayList<String> list = new java.util.ArrayList<>(PROXIES.size());
        for (String proxy : PROXIES) list.add(proxy + rawUrl);
        return list;
    }

    /** 当前是否有任何可用代理配置（用于 UI 提示）。 */
    public static boolean hasProxy() {
        return PROXIES.size() > 1;
    }
}
