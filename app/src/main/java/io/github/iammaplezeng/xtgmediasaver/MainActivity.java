package io.github.iammaplezeng.xtgmediasaver;

import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;

public final class MainActivity extends AppCompatActivity {
    private SharedPreferences prefs;

    private MaterialCardView statusCard;
    private ImageView statusIcon;
    private TextView statusTitle;
    private TextView statusSubtitle;
    private TextView moduleVersionValue;

    private TextView badgeXStatus;
    private TextView valueXVersion;
    private MaterialSwitch switchXEnabled;
    private MaterialSwitch switchXVideo;
    private MaterialSwitch switchXAllImages;

    private TextView badgeTgStatus;
    private TextView valueTgVersion;
    private MaterialSwitch switchTgEnabled;
    private MaterialSwitch switchTgSaveRestricted;

    @Override
    protected void onCreate(Bundle state) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        prefs = ModulePrefs.openForUi(this);

        View appBar = findViewById(R.id.app_bar);
        View contentContainer = findViewById(R.id.content_container);
        final int initialBottomPadding = contentContainer.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_coordinator), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            appBar.setPadding(bars.left, bars.top, bars.right, 0);
            contentContainer.setPadding(
                    contentContainer.getPaddingLeft(),
                    contentContainer.getPaddingTop(),
                    contentContainer.getPaddingRight(),
                    initialBottomPadding + bars.bottom
            );
            return insets;
        });

        statusCard = findViewById(R.id.status_card);
        statusIcon = findViewById(R.id.status_icon);
        statusTitle = findViewById(R.id.status_title);
        statusSubtitle = findViewById(R.id.status_subtitle);
        moduleVersionValue = findViewById(R.id.value_module_version);

        badgeXStatus = findViewById(R.id.badge_x_status);
        valueXVersion = findViewById(R.id.value_x_version);
        switchXEnabled = findViewById(R.id.switch_x_enabled);
        switchXVideo = findViewById(R.id.switch_x_video);
        switchXAllImages = findViewById(R.id.switch_x_all_images);

        badgeTgStatus = findViewById(R.id.badge_tg_status);
        valueTgVersion = findViewById(R.id.value_tg_version);
        switchTgEnabled = findViewById(R.id.switch_tg_enabled);
        switchTgSaveRestricted = findViewById(R.id.switch_tg_save_restricted);

        moduleVersionValue.setText("v" + BuildConfig.VERSION_NAME);

        bindSwitches();
        refreshStatuses();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatuses();
    }

    private void bindSwitches() {
        boolean xEnabled = prefs.getBoolean(ModulePrefs.KEY_X_ENABLED, true);
        boolean xVideo = prefs.getBoolean(ModulePrefs.KEY_X_VIDEO, true);
        boolean xAllImages = prefs.getBoolean(ModulePrefs.KEY_X_ALL_IMAGES, true);

        switchXEnabled.setChecked(xEnabled);
        switchXVideo.setChecked(xVideo);
        switchXAllImages.setChecked(xAllImages);
        switchXVideo.setEnabled(xEnabled);
        switchXAllImages.setEnabled(xEnabled);

        switchXEnabled.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(ModulePrefs.KEY_X_ENABLED, checked).apply();
            switchXVideo.setEnabled(checked);
            switchXAllImages.setEnabled(checked);
        });
        switchXVideo.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(ModulePrefs.KEY_X_VIDEO, checked).apply());
        switchXAllImages.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(ModulePrefs.KEY_X_ALL_IMAGES, checked).apply());

        boolean tgEnabled = prefs.getBoolean(ModulePrefs.KEY_TG_ENABLED, true);
        boolean tgSaveRestricted = prefs.getBoolean(ModulePrefs.KEY_TG_SAVE_RESTRICTED, true);

        switchTgEnabled.setChecked(tgEnabled);
        switchTgSaveRestricted.setChecked(tgSaveRestricted);
        switchTgSaveRestricted.setEnabled(tgEnabled);

        switchTgEnabled.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(ModulePrefs.KEY_TG_ENABLED, checked).apply();
            switchTgSaveRestricted.setEnabled(checked);
        });
        switchTgSaveRestricted.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(ModulePrefs.KEY_TG_SAVE_RESTRICTED, checked).apply());
    }

    private void refreshStatuses() {
        boolean activated = ModuleStatus.isActivated();
        int containerColor;
        int onContainerColor;
        if (activated) {
            containerColor = MaterialColors.getColor(statusCard, com.google.android.material.R.attr.colorPrimaryContainer);
            onContainerColor = MaterialColors.getColor(statusCard, com.google.android.material.R.attr.colorOnPrimaryContainer);
            statusIcon.setImageResource(R.drawable.ic_status_check);
            statusTitle.setText("模块已激活");
            statusSubtitle.setText("已在 LSPosed 中激活，可分别配置 X 与 Telegram。");
        } else {
            containerColor = MaterialColors.getColor(statusCard, com.google.android.material.R.attr.colorErrorContainer);
            onContainerColor = MaterialColors.getColor(statusCard, com.google.android.material.R.attr.colorOnErrorContainer);
            statusIcon.setImageResource(R.drawable.ic_status_warning);
            statusTitle.setText("模块未激活");
            statusSubtitle.setText("请在 LSPosed 中启用本模块，作用域勾选 X / Telegram。");
        }
        statusCard.setCardBackgroundColor(containerColor);
        statusIcon.setImageTintList(ColorStateList.valueOf(onContainerColor));
        statusTitle.setTextColor(onContainerColor);
        statusSubtitle.setTextColor(onContainerColor);
        moduleVersionValue.setTextColor(onContainerColor);

        refreshPlatformBadge(
                HostVersions.X_PACKAGE,
                HostVersions.X_NAME,
                HostVersions::xSupported,
                badgeXStatus,
                valueXVersion
        );

        refreshPlatformBadge(
                HostVersions.TG_PACKAGE,
                HostVersions.TG_NAME,
                HostVersions::telegramSupported,
                badgeTgStatus,
                valueTgVersion
        );
    }

    private interface VersionChecker {
        boolean supported(String name, long code);
    }

    private void refreshPlatformBadge(
            String pkg,
            String targetName,
            VersionChecker checker,
            TextView badge,
            TextView versionView
    ) {
        String installedName = null;
        boolean supported = false;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(pkg, 0);
            installedName = info.versionName != null ? info.versionName : "未知";
            supported = checker.supported(info.versionName, info.getLongVersionCode());
        } catch (Exception ignored) {
        }

        if (installedName == null) {
            versionView.setText("未安装 / " + targetName);
            applyBadgeStyle(badge, "未安装", false);
        } else {
            versionView.setText(installedName + " / " + targetName);
            applyBadgeStyle(badge, supported ? "已适配" : "版本不匹配", supported);
        }
    }

    private void applyBadgeStyle(TextView badge, String text, boolean ok) {
        int bg = MaterialColors.getColor(badge, ok
                ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorErrorContainer);
        int fg = MaterialColors.getColor(badge, ok
                ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorOnErrorContainer);
        badge.setText(text);
        badge.setBackgroundTintList(ColorStateList.valueOf(bg));
        badge.setTextColor(fg);
    }
}
