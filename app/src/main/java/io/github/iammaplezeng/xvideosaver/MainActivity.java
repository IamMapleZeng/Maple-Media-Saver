package io.github.iammaplezeng.xvideosaver;

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

public final class MainActivity extends AppCompatActivity {
    private static final String HOST_PACKAGE = "com.twitter.android";

    private MaterialCardView statusCard;
    private ImageView statusIcon;
    private TextView statusTitle;
    private TextView statusSubtitle;
    private TextView moduleVersionValue;
    private TextView hostVersionValue;
    private TextView targetVersionValue;

    @Override
    protected void onCreate(Bundle state) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

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
        hostVersionValue = findViewById(R.id.value_host_version);
        targetVersionValue = findViewById(R.id.value_target_version);

        moduleVersionValue.setText(BuildConfig.VERSION_NAME);
        targetVersionValue.setText(HostVersion.NAME);

        refreshHostStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHostStatus();
    }

    private void refreshHostStatus() {
        String host;
        boolean installed = false;
        boolean supported = false;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(HOST_PACKAGE, 0);
            installed = true;
            host = info.versionName != null ? info.versionName : "未知版本";
            supported = HostVersion.supported(info.versionName, info.getLongVersionCode());
        } catch (Exception e) {
            host = "未检测到 X";
        }

        hostVersionValue.setText(host);

        int containerColor;
        int onContainerColor;
        if (supported) {
            containerColor = MaterialColors.getColor(statusCard, com.google.android.material.R.attr.colorPrimaryContainer);
            onContainerColor = MaterialColors.getColor(statusCard, com.google.android.material.R.attr.colorOnPrimaryContainer);
            statusIcon.setImageResource(R.drawable.ic_status_check);
            statusTitle.setText("版本已适配");
            statusSubtitle.setText("版本匹配，仍需在 LSPosed 中启用。");
        } else {
            containerColor = MaterialColors.getColor(statusCard, com.google.android.material.R.attr.colorErrorContainer);
            onContainerColor = MaterialColors.getColor(statusCard, com.google.android.material.R.attr.colorOnErrorContainer);
            statusIcon.setImageResource(R.drawable.ic_status_warning);
            statusTitle.setText(installed ? "版本不匹配" : "未检测到 X");
            statusSubtitle.setText("版本不匹配，模块不会启用下载 Hook。");
        }

        statusCard.setCardBackgroundColor(containerColor);
        statusIcon.setImageTintList(ColorStateList.valueOf(onContainerColor));
        statusTitle.setTextColor(onContainerColor);
        statusSubtitle.setTextColor(onContainerColor);
    }
}
