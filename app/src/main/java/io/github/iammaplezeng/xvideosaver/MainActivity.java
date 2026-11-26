package io.github.iammaplezeng.xvideosaver;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad * 2, pad, pad);
        TextView title = new TextView(this);
        title.setText("XVideoSaver"); title.setTextSize(28); layout.addView(title);
        String host;
        boolean supported = false;
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo("com.twitter.android", 0);
            host = info.versionName;
            supported = HostVersion.supported(info.versionName, info.getLongVersionCode());
        }
        catch (Exception e) { host = "未检测到 X"; }
        TextView body = new TextView(this);
        body.setTextSize(16);
        body.setText("\n模块版本：" + BuildConfig.VERSION_NAME + "\n当前 X：" + host
                + "\n适配版本：" + HostVersion.NAME
                + (supported ? "\n版本匹配，仍需在 LSPosed 中启用。" : "\n版本不匹配，模块不会启用下载 Hook。")
                + "\n\n安装后，在 LSPosed 中启用本模块，作用域只勾选 X。强行停止并重新打开 X，再长按视频、点击下载视频。"
                + "\n\n模块是否加载成功请查看 LSPosed 日志，搜索 XVideoSaver。"
                + "\n\n包名：" + BuildConfig.APPLICATION_ID);
        layout.addView(body);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(layout); setContentView(scroll);
    }
}
