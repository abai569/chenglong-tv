package com.fongmi.android.tv;

import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.UpdateDialog;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Github;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.utils.Path;

import org.json.JSONObject;

import java.io.File;
import java.util.List;

/**
 * 在线更新。
 *
 * <p>更新信息来自自有仓库的 {@code update.json}（由 CI 在每次发版时写入），
 * 格式：
 * <pre>
 * {
 *   "name": "1.0.5",
 *   "code": 10005,
 *   "desc": "更新说明",
 *   "arm64_v8a": "https://github.com/.../chenglong_1.0.5_arm64_v8a.apk",
 *   "armeabi_v7a": "https://github.com/.../chenglong_1.0.5_armeabi_v7a.apk"
 * }
 * </pre>
 *
 * <p>所有 GitHub 请求都走 {@link Github#candidates} 的代理列表，逐个降级重试；
 * 失败会明确提示用户，不再静默吞异常。
 */
public class Updater implements Download.Callback, UpdateListener {

    /** update.json 所在分支。 */
    private static final String BRANCH = "fongmi";
    private static final String MANIFEST = "update.json";

    private final File file;
    private UpdateDialog dialog;
    private Download download;

    /** 当前尝试的下载地址序号（用于换代理重试）。 */
    private List<String> urls;
    private int urlIndex;
    private String versionName;

    private Updater() {
        this.file = Path.cache("update.apk");
    }

    public static Updater create() {
        return new Updater();
    }

    /** 当前设备的 ABI 标识，与 Release 产物后缀一致。 */
    private String abi() {
        return android.os.Process.is64Bit() ? "arm64_v8a" : "armeabi_v7a";
    }

    public Updater force() {
        Notify.show(R.string.update_check);
        Setting.putUpdate(true);
        return this;
    }

    public void start(FragmentActivity activity) {
        if (!Setting.getUpdate()) return;
        Task.execute(() -> doInBackground(activity));
    }

    private void doInBackground(FragmentActivity activity) {
        try {
            String body = Github.raw(BRANCH, MANIFEST);
            if (body == null || body.trim().isEmpty()) {
                // 所有代理都拿不到清单：明确告知，不再静默
                App.post(() -> Notify.show(R.string.update_none));
                return;
            }
            JSONObject object = new JSONObject(body);
            int code = object.optInt("code");
            String name = object.optString("name");
            String desc = object.optString("desc");
            if (code <= BuildConfig.VERSION_CODE) {
                App.post(() -> Notify.show(R.string.update_latest));
                return;
            }
            String url = object.optString(abi());
            if (url == null || url.trim().isEmpty()) {
                App.post(() -> Notify.show(R.string.update_none));
                return;
            }
            versionName = name;
            App.post(() -> show(activity, name, desc, url));
        } catch (Exception e) {
            e.printStackTrace();
            App.post(() -> Notify.show(R.string.update_none));
        }
    }

    private void show(FragmentActivity activity, String version, String desc, String url) {
        dismiss();
        this.urls = Github.candidates(url);
        this.urlIndex = 0;
        dialog = UpdateDialog.create()
                .title(ResUtil.getString(R.string.update_version, version))
                .desc(desc)
                .listener(this)
                .show(activity);
    }

    @Override
    public void onConfirm(View view) {
        view.setEnabled(false);
        startDownload();
    }

    /** 用当前序号的地址启动下载；失败时由 {@link #error} 换下一个代理。 */
    private void startDownload() {
        if (urls == null || urlIndex >= urls.size()) {
            Notify.show(R.string.update_none);
            dismiss();
            return;
        }
        download = Download.create(urls.get(urlIndex), file);
        download.start(this);
    }

    @Override
    public void onCancel(View view) {
        Setting.putUpdate(false);
        if (download != null) download.cancel();
        dismiss();
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void progress(int progress) {
        if (dialog != null) dialog.setProgress(progress);
    }

    @Override
    public void error(String msg) {
        // 换下一个代理重试；全部失败才提示
        if (urls != null && ++urlIndex < urls.size()) {
            startDownload();
            return;
        }
        Notify.show(ResUtil.getString(R.string.update_none) + (msg == null ? "" : " (" + msg + ")"));
        dismiss();
    }

    @Override
    public void success(File file) {
        FileUtil.openFile(file);
        dismiss();
    }
}
