package com.albertolicea00.nativesymbols;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.net.Uri;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String PREFS = "appearance";
    private static final String LAYOUT_KEY = "icon_layout";

    private int ink;
    private int iconNameColor;
    private int muted;
    private int primary;
    private int onPrimary;
    private int accentText;
    private int line;
    private int surface;
    private int background;
    private int codeBackground;
    private boolean darkMode;
    private boolean listLayout;
    private ImageView layoutButton;
    private FrameLayout infoPage;

    private final List<IconItem> items = new ArrayList<>();
    private final List<LinearLayout> filterViews = new ArrayList<>();
    private final List<TextView> filterLabels = new ArrayList<>();
    private final List<TextView> filterCounts = new ArrayList<>();
    private final List<IconItem> visibleItems = new ArrayList<>();
    private GridView results;
    private BaseAdapter resultsAdapter;
    private TextView emptyView;
    private FrameLayout screen;
    private FrameLayout detailOverlay;
    private int resultCellSize;
    private EditText searchView;
    private int filter = 0;

    private static final class IconItem {
        final String name;
        final int resourceId;
        final boolean material;

        IconItem(String name, int resourceId, boolean material) {
            this.name = name;
            this.resourceId = resourceId;
            this.material = material;
        }

        String resourceName() {
            return material ? "@drawable/" + name : "@android:drawable/" + name;
        }

        String javaReference() {
            return material ? "R.drawable." + name : "android.R.drawable." + name;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        listLayout = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(LAYOUT_KEY, false);
        darkMode = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme(darkMode ? R.style.AppThemeDark : R.style.AppThemeLight);
        super.onCreate(state);
        applyPalette();
        WindowCompat.enableEdgeToEdge(getWindow());
        if (state != null) filter = Math.max(0, Math.min(1, state.getInt("filter", 0)));
        loadIcons();
        buildScreen();
        if (state != null) searchView.setText(state.getString("query", ""));
        renderResults();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("filter", filter);
        if (searchView != null) outState.putString("query", searchView.getText().toString());
        super.onSaveInstanceState(outState);
    }

    private void applyPalette() {
        primary = getColor(R.color.primary);
        onPrimary = getColor(R.color.on_primary);
        accentText = getColor(darkMode ? R.color.accent_dark : R.color.accent_light);
        background = getColor(darkMode ? R.color.background_dark : R.color.background_light);
        surface = getColor(darkMode ? R.color.surface_dark : R.color.surface_light);
        codeBackground = getColor(darkMode ? R.color.code_dark : R.color.code_light);
        ink = getColor(darkMode ? R.color.ink_dark : R.color.ink_light);
        iconNameColor = getColor(darkMode ? R.color.ink_dark : R.color.icon_name_light);
        muted = getColor(darkMode ? R.color.muted_dark : R.color.muted_light);
        line = getColor(darkMode ? R.color.line_dark : R.color.line_light);
    }

    private void styleDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(shape(surface, line));
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(accentText);
        }
    }

    private void loadIcons() {
        try {
            String[] files = getAssets().list("vectors");
            if (files != null) {
                for (String file : files) {
                    if (!file.endsWith(".xml")) continue;
                    String name = file.substring(0, file.length() - 4);
                    int id = getResources().getIdentifier(name, "drawable", getPackageName());
                    if (id != 0) items.add(new IconItem(name, id, true));
                }
            }
        } catch (Exception ignored) {
            // The system catalog is still usable if bundled assets are missing.
        }

        for (Field field : android.R.drawable.class.getFields()) {
            if (field.getType() != int.class || !Modifier.isStatic(field.getModifiers())) continue;
            if (!isSystemIconName(field.getName())) continue;
            try {
                int id = field.getInt(null);
                getDrawable(id);
                items.add(new IconItem(field.getName(), id, false));
            } catch (Exception ignored) {
                // Some framework drawables are unavailable on older Android versions.
            }
        }
        Collections.sort(items, new Comparator<IconItem>() {
            @Override public int compare(IconItem a, IconItem b) {
                if (a.material != b.material) return a.material ? -1 : 1;
                return a.name.compareToIgnoreCase(b.name);
            }
        });
    }

    private boolean isSystemIconName(String name) {
        return name.startsWith("ic_") || name.startsWith("stat_")
                || name.startsWith("sym_") || name.startsWith("presence_")
                || name.startsWith("star_");
    }

    private void buildScreen() {
        filterViews.clear();
        filterLabels.clear();
        filterCounts.clear();
        LinearLayout root = pageRoot();

        results = new GridView(this);
        results.setNumColumns(1);
        results.setPadding(dp(8), 0, dp(8), 0);
        results.setClipToPadding(false);
        results.setVerticalScrollBarEnabled(false);
        results.setHorizontalSpacing(dp(2));
        results.setVerticalSpacing(dp(2));
        results.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        resultsAdapter = new BaseAdapter() {
            @Override public int getCount() { return visibleItems.size(); }
            @Override public IconItem getItem(int position) { return visibleItems.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = listLayout ? iconListRow(getItem(position))
                        : iconCard(getItem(position), resultCellSize);
                int rowHeight = dp(Math.max(56,
                        20 + 18 * Math.max(1f, getResources().getConfiguration().fontScale)));
                view.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,
                        listLayout ? rowHeight : resultCellSize));
                return view;
            }
        };
        results.setAdapter(resultsAdapter);
        FrameLayout resultsFrame = new FrameLayout(this);
        root.addView(resultsFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        resultsFrame.addView(results, new FrameLayout.LayoutParams(-1, -1));
        emptyView = text("", 15, muted, false);
        emptyView.setGravity(Gravity.CENTER);
        resultsFrame.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));
        results.setEmptyView(emptyView);
        results.addOnLayoutChangeListener((view, left, top, right, bottom,
                                           oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left > 0 && right - left != oldRight - oldLeft) renderResults();
        });

        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        float fontScale = Math.max(1f, getResources().getConfiguration().fontScale);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        int filterHeight = dp(Math.max(landscape ? 52 : 48, 20 + 16 * fontScale));
        LinearLayout.LayoutParams filtersParams = landscape
                ? new LinearLayout.LayoutParams(0, filterHeight, 2)
                : new LinearLayout.LayoutParams(-1, filterHeight);
        controls.addView(filters, filtersParams);

        String[] labels = { "System", "Material" };
        for (int i = 0; i < labels.length; i++) {
            final int selected = i;
            LinearLayout chip = new LinearLayout(this);
            chip.setGravity(Gravity.CENTER);
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            TextView label = text(labels[i], 13, muted, true);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            chip.addView(label);
            TextView badge = text("0", 12, accentText, true);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(4), 0, dp(4), 0);
            badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            badge.setMinWidth(dp(36));
            badge.setMinHeight(dp(24));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, -2);
            badgeParams.leftMargin = dp(10);
            chip.addView(badge, badgeParams);
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(0, -1, 1);
            filters.addView(chip, chipParams);
            filterViews.add(chip);
            filterLabels.add(label);
            filterCounts.add(badge);
            chip.setOnClickListener(v -> { filter = selected; updateFilters(); renderResults(); results.setSelection(0); });
        }
        updateFilters();

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        int bottomHeight = dp(Math.max(52, 28 + 18 * fontScale));
        LinearLayout.LayoutParams bottomParams = landscape
                ? new LinearLayout.LayoutParams(0, bottomHeight, 3)
                : new LinearLayout.LayoutParams(-1, bottomHeight);
        controls.addView(bottomBar, bottomParams);

        searchView = new EditText(this);
        searchView.setSingleLine(true);
        searchView.setTextSize(15);
        searchView.setTextColor(ink);
        searchView.setHintTextColor(muted);
        searchView.setHint("Search icons");
        searchView.setBackground(shape(surface, line));
        searchView.setPadding(dp(16), 0, dp(16), 0);
        searchView.setCompoundDrawablePadding(dp(8));
        searchView.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0);
        searchView.setCompoundDrawableTintList(ColorStateList.valueOf(muted));
        bottomBar.addView(searchView, new LinearLayout.LayoutParams(0, bottomHeight, 1));
        searchView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderResults();
                results.setSelection(0);
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        layoutButton = new ImageView(this);
        layoutButton.setPadding(dp(12), dp(12), dp(12), dp(12));
        layoutButton.setBackground(shape(surface, line));
        layoutButton.setClickable(true);
        layoutButton.setFocusable(true);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        bottomBar.addView(layoutButton, layoutParams);
        layoutButton.setOnClickListener(v -> toggleLayout());
        updateLayoutButton();

        ImageView infoButton = new ImageView(this);
        infoButton.setImageResource(R.drawable.ic_md_info);
        infoButton.setImageTintList(ColorStateList.valueOf(accentText));
        infoButton.setPadding(dp(14), dp(14), dp(14), dp(14));
        infoButton.setBackground(shape(surface, line));
        infoButton.setClickable(true);
        infoButton.setFocusable(true);
        infoButton.setContentDescription("App information");
        bottomBar.addView(infoButton, new LinearLayout.LayoutParams(dp(52), dp(52)));
        infoButton.setOnClickListener(v -> showInfoPage());
    }

    private LinearLayout pageRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);
        screen = new FrameLayout(this);
        screen.setBackgroundColor(background);
        screen.addView(root, new FrameLayout.LayoutParams(-1, -1));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right,
                    Math.max(bars.bottom, keyboard.bottom));
            return insets;
        });
        setContentView(screen);
        return root;
    }

    @Override
    public void onBackPressed() {
        if (infoPage != null) {
            dismissInfoPage();
        } else if (detailOverlay != null) {
            dismissDetails();
        } else {
            super.onBackPressed();
        }
    }

    private void showInfoPage() {
        if (infoPage != null) return;
        searchView.clearFocus();
        android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(searchView.getWindowToken(), 0);

        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x66000000);
        overlay.setOnClickListener(v -> dismissInfoPage());
        infoPage = overlay;
        screen.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        Runnable dismiss = this::dismissInfoPage;

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        GradientDrawable sheetBackground = new GradientDrawable();
        sheetBackground.setColor(surface);
        sheet.setBackground(sheetBackground);

        FrameLayout header = new SheetHeader(dismiss);
        sheet.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));
        View handle = new View(this);
        GradientDrawable handleBackground = new GradientDrawable();
        handleBackground.setColor(muted);
        handleBackground.setCornerRadius(dp(2));
        FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(
                dp(36), dp(4), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        handleParams.topMargin = dp(8);
        header.addView(handle, handleParams);

        ImageView close = new ImageView(this);
        close.setImageResource(R.drawable.ic_md_close);
        close.setImageTintList(ColorStateList.valueOf(ink));
        close.setPadding(dp(12), dp(12), dp(12), dp(12));
        close.setContentDescription("Close app information");
        close.setClickable(true);
        close.setFocusable(true);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                dp(48), dp(48), Gravity.TOP | Gravity.END);
        closeParams.rightMargin = dp(8);
        header.addView(close, closeParams);
        close.setOnClickListener(v -> dismissInfoPage());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), 0, dp(24), dp(28));
        scroll.addView(content);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.native_symbols_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("Native Symbols app logo");
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(
                dp(landscape ? 96 : 132), dp(landscape ? 96 : 132));
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(logo, logoParams);

        TextView byline = text("by @albertolicea00", 14, accentText, true);
        byline.setGravity(Gravity.CENTER);
        byline.setClickable(true);
        byline.setFocusable(true);
        byline.setMinHeight(dp(48));
        byline.setContentDescription("GitHub profile: albertolicea00");
        byline.setOnClickListener(v -> openExternalUrl("https://github.com/albertolicea00"));
        LinearLayout.LayoutParams bylineParams = new LinearLayout.LayoutParams(-1, -2);
        bylineParams.topMargin = dp(4);
        content.addView(byline, bylineParams);

        TextView description = text("Browse Android system drawables and +2k classic Material Icons. Search by name, preview icons, and copy XML, Kotlin, and Java examples. The catalog works offline.",
                15, ink, false);
        description.setGravity(Gravity.CENTER);
        description.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = dp(24);
        content.addView(description, descriptionParams);

        LinearLayout.LayoutParams linksParams = new LinearLayout.LayoutParams(-1, -2);
        linksParams.topMargin = dp(22);
        LinearLayout links = new LinearLayout(this);
        links.setOrientation(LinearLayout.VERTICAL);
        links.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(links, linksParams);
        addInfoLink(links, "GitHub repository", "https://github.com/albertolicea00/AndroidNativeSymbols");
        addInfoLink(links, "Privacy Policy", "https://github.com/albertolicea00/AndroidNativeSymbols/blob/main/PRIVACY.md");

        TextView build = text("Version " + appVersionName() + "  ·  Build " + appVersionCode(),
                12, muted, false);
        build.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams buildParams = new LinearLayout.LayoutParams(-1, -2);
        buildParams.topMargin = dp(10);
        content.addView(build, buildParams);
        TextView minimum = text("Android 6.0 and later", 11, muted, false);
        minimum.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams minimumParams = new LinearLayout.LayoutParams(-1, -2);
        minimumParams.topMargin = dp(3);
        content.addView(minimum, minimumParams);

        header.setOnTouchListener(sheetDragListener(sheet, dismiss));
        int sheetHeight = Math.round((screen.getHeight() > 0
                ? screen.getHeight() : getResources().getDisplayMetrics().heightPixels) * 0.8f);
        overlay.addView(sheet, new FrameLayout.LayoutParams(-1, sheetHeight, Gravity.BOTTOM));
        ViewCompat.setOnApplyWindowInsetsListener(overlay, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            sheet.setPadding(bars.left, 0, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(overlay);
    }

    private void dismissInfoPage() {
        if (infoPage != null) {
            screen.removeView(infoPage);
            infoPage = null;
        }
    }

    private String appVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "1.0";
        }
    }

    private String appVersionCode() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return String.valueOf(Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode);
        } catch (Exception ignored) {
            return "1";
        }
    }

    private void addInfoLink(LinearLayout parent, String label, String url) {
        TextView link = text(label, 15, accentText, true);
        link.setPadding(dp(14), dp(10), dp(14), dp(10));
        link.setMinHeight(dp(48));
        link.setGravity(Gravity.CENTER);
        link.setBackground(shape(surface, line));
        link.setClickable(true);
        link.setFocusable(true);
        link.setContentDescription(label + ", opens in browser");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(240), -2);
        params.topMargin = parent.getChildCount() == 0 ? 0 : dp(5);
        parent.addView(link, params);
        link.setOnClickListener(v -> openExternalUrl(url));
    }

    private void openExternalUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
            Toast.makeText(this, "No browser available", Toast.LENGTH_SHORT).show();
        }
    }

    private void dismissDetails() {
        if (detailOverlay != null) {
            screen.removeView(detailOverlay);
            detailOverlay = null;
        }
    }

    private void toggleLayout() {
        listLayout = !listLayout;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(LAYOUT_KEY, listLayout).apply();
        updateLayoutButton();
        renderResults();
        results.setSelection(0);
    }

    private void updateLayoutButton() {
        layoutButton.setImageResource(listLayout ? R.drawable.ic_layout_grid : R.drawable.ic_layout_list);
        layoutButton.setImageTintList(ColorStateList.valueOf(accentText));
        layoutButton.setContentDescription(listLayout ? "Switch to grid view" : "Switch to list view");
    }

    private void updateFilters() {
        for (int i = 0; i < filterViews.size(); i++) {
            LinearLayout chip = filterViews.get(i);
            boolean selected = i == filter;
            chip.setSelected(selected);
            filterLabels.get(i).setTextColor(selected ? onPrimary : muted);
            filterCounts.get(i).setTextColor(selected ? primary : accentText);
            filterCounts.get(i).setBackground(shape(selected ? onPrimary : codeBackground,
                    selected ? onPrimary : line));
            chip.setBackground(shape(selected ? primary : surface, selected ? primary : line));
            String matches = filterCounts.get(i).getText().toString();
            chip.setContentDescription((i == 0 ? "System" : "Material") + ", "
                    + matches + ("1".equals(matches) ? " match" : " matches")
                    + (selected ? ", selected" : ""));
        }
    }

    private void renderResults() {
        if (results == null || searchView == null) return;
        String query = searchView.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<IconItem> materialMatches = new ArrayList<>();
        List<IconItem> systemMatches = new ArrayList<>();
        for (IconItem item : items) {
            if (!item.name.toLowerCase(Locale.ROOT).contains(query)
                    && !displayName(item.name).toLowerCase(Locale.ROOT).contains(query)) continue;
            (item.material ? materialMatches : systemMatches).add(item);
        }
        filterCounts.get(0).setText(String.valueOf(systemMatches.size()));
        filterCounts.get(1).setText(String.valueOf(materialMatches.size()));
        updateFilters();
        List<IconItem> visible = filter == 0 ? systemMatches : materialMatches;
        boolean otherHasMatches = filter == 0 ? !materialMatches.isEmpty() : !systemMatches.isEmpty();
        String otherName = filter == 0 ? "Material" : "System";
        emptyView.setText(otherHasMatches
                ? "No icons here. Check " + otherName + "."
                : "No icons found. Try another name.");
        visibleItems.clear();
        visibleItems.addAll(visible);
        int contentWidth = results.getWidth() > 0
                ? results.getWidth() - results.getPaddingLeft() - results.getPaddingRight()
                : getResources().getDisplayMetrics().widthPixels
                        - results.getPaddingLeft() - results.getPaddingRight();
        int columns;
        if (listLayout) {
            int minimumColumns = getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE ? 2 : 1;
            columns = Math.max(minimumColumns, Math.min(4, contentWidth / dp(240)));
            resultCellSize = dp(56);
        } else {
            float fontScale = Math.max(1f, getResources().getConfiguration().fontScale);
            columns = Math.max(2, Math.min(8, contentWidth / dp(84 * fontScale)));
            resultCellSize = (contentWidth - dp(2) * (columns - 1)) / columns;
        }
        results.setNumColumns(columns);
        resultsAdapter.notifyDataSetChanged();
    }

    private View iconCard(IconItem item, int cardSize) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(3), dp(3), dp(3), dp(3));
        card.setBackground(shape(surface, line));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(displayName(item.name)
                + (item.material ? ", Material icon" : ", Android system drawable"));
        ImageView image = new ImageView(this);
        image.setImageResource(item.resourceId);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (!item.material) image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setImageTintList(ColorStateList.valueOf(ink));
        int iconSize = Math.min(dp(44), Math.round(cardSize * 0.42f));
        card.addView(image, new LinearLayout.LayoutParams(iconSize, iconSize));
        TextView name = text(displayName(item.name), 11, iconNameColor, true);
        name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        float fontScale = Math.max(1f, getResources().getConfiguration().fontScale);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, dp(26 * fontScale));
        nameParams.topMargin = dp(3);
        card.addView(name, nameParams);
        card.setOnClickListener(v -> showDetails(item));
        return card;
    }

    private View iconListRow(IconItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setBackground(shape(surface, line));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(displayName(item.name)
                + (item.material ? ", Material icon" : ", Android system drawable"));
        ImageView image = new ImageView(this);
        image.setImageResource(item.resourceId);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (!item.material) image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setImageTintList(ColorStateList.valueOf(ink));
        row.addView(image, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView name = text(displayName(item.name), 15, iconNameColor, true);
        name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1);
        nameParams.leftMargin = dp(16);
        row.addView(name, nameParams);
        row.setOnClickListener(v -> showDetails(item));
        return row;
    }

    private void showDetails(IconItem item) {
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        dismissDetails();
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x66000000);
        overlay.setOnClickListener(v -> dismissDetails());
        screen.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        detailOverlay = overlay;
        Runnable dismiss = this::dismissDetails;
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        GradientDrawable sheetBackground = new GradientDrawable();
        sheetBackground.setColor(surface);
        sheet.setBackground(sheetBackground);

        FrameLayout header = new SheetHeader(dismiss);
        sheet.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));
        View handle = new View(this);
        GradientDrawable handleBackground = new GradientDrawable();
        handleBackground.setColor(muted);
        handleBackground.setCornerRadius(dp(2));
        handle.setBackground(handleBackground);
        FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(dp(36), dp(4), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        handleParams.topMargin = dp(8);
        header.addView(handle, handleParams);

        ImageView close = new ImageView(this);
        close.setImageResource(R.drawable.ic_md_close);
        close.setImageTintList(ColorStateList.valueOf(ink));
        close.setPadding(dp(12), dp(12), dp(12), dp(12));
        close.setContentDescription("Close icon details");
        close.setClickable(true);
        close.setFocusable(true);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END);
        closeParams.rightMargin = dp(8);
        header.addView(close, closeParams);
        close.setOnClickListener(v -> dismissDetails());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), 0, dp(12), dp(24));
        content.setBackgroundColor(surface);
        scroll.addView(content);

        ImageView preview = new ImageView(this);
        preview.setImageResource(item.resourceId);
        if (!item.material) preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        preview.setImageTintList(ColorStateList.valueOf(primary));
        TextView title = text(displayName(item.name), 22, ink, true);
        TextView kind = text(item.material ? "LOCAL MATERIAL VECTOR" : "ANDROID SYSTEM DRAWABLE", 11, accentText, true);
        kind.setLetterSpacing(0.08f);
        LinearLayout identity = new LinearLayout(this);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        identity.setMinimumHeight(dp(56));
        content.addView(identity, new LinearLayout.LayoutParams(-1, -2));
        identity.addView(preview, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, -2, 1);
        labelsParams.leftMargin = dp(16);
        identity.addView(labels, labelsParams);
        title.setMaxLines(landscape ? 1 : 2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(title, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams kindParams = new LinearLayout.LayoutParams(-1, -2);
        kindParams.topMargin = dp(2);
        labels.addView(kind, kindParams);

        TextView note = text(item.material
                ? "Add this vector XML to res/drawable before using the snippets below."
                : "This drawable is supplied by Android. Its appearance may vary across devices.",
                item.material ? 13 : 9, item.material ? muted : ink, false);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(13);
        if (item.material) content.addView(note, noteParams);

        addCodeBlock(content, "XML layout", "<ImageView\n    android:layout_width=\"24dp\"\n"
                + "    android:layout_height=\"24dp\"\n"
                + "    android:contentDescription=\"" + displayName(item.name) + "\"\n"
                + "    android:src=\"" + item.resourceName() + "\" />");
        addCodeBlock(content, "Kotlin", "imageView.setImageResource(" + item.javaReference() + ")");
        addCodeBlock(content, "Java", "imageView.setImageResource(" + item.javaReference() + ");");
        if (item.material) {
            try {
                addCodeBlock(content, "Vector drawable XML", readAsset("vectors/" + item.name + ".xml"));
            } catch (Exception ignored) { }
        } else {
            content.addView(note, noteParams);
        }

        header.setOnTouchListener(sheetDragListener(sheet, dismiss));
        int sheetHeight = Math.round((screen.getHeight() > 0
                ? screen.getHeight() : getResources().getDisplayMetrics().heightPixels) * 0.8f);
        overlay.addView(sheet, new FrameLayout.LayoutParams(-1, sheetHeight, Gravity.BOTTOM));
        ViewCompat.setOnApplyWindowInsetsListener(overlay, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            sheet.setPadding(bars.left, 0, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(overlay);
    }

    private final class SheetHeader extends FrameLayout {
        SheetHeader(Runnable onClick) {
            super(MainActivity.this);
            setClickable(true);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            setOnClickListener(v -> onClick.run());
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    private View.OnTouchListener sheetDragListener(View sheet, Runnable dismiss) {
        return new View.OnTouchListener() {
            private float startY;
            private boolean dragging;

            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getRawY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float distance = event.getRawY() - startY;
                        if (!dragging && distance > dp(12)) dragging = true;
                        if (dragging) {
                            sheet.setTranslationY(Math.max(0, distance));
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (dragging) {
                            finishSheetDrag(sheet, dismiss, event);
                            dragging = false;
                        } else {
                            view.performClick();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        if (dragging) finishSheetDrag(sheet, dismiss, event);
                        dragging = false;
                        return true;
                    default:
                        return false;
                }
            }
        };
    }

    private void finishSheetDrag(View sheet, Runnable dismiss, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP && sheet.getTranslationY() > dp(100)) {
            dismiss.run();
        } else {
            sheet.animate().translationY(0).setDuration(180).start();
        }
    }

    private void addCodeBlock(LinearLayout parent, String heading, String code) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(14), dp(12), dp(14), dp(12));
        block.setBackground(shape(codeBackground, line));
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(-1, -2);
        blockParams.topMargin = dp(8);
        parent.addView(block, blockParams);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        block.addView(header);
        TextView label = text(heading, 12, ink, true);
        header.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView copy = text("COPY", 12, accentText, true);
        copy.setPadding(dp(8), dp(4), dp(2), dp(4));
        copy.setMinHeight(dp(48));
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setFocusable(true);
        copy.setClickable(true);
        copy.setContentDescription("Copy " + heading);
        header.addView(copy);
        copy.setOnClickListener(v -> copyText(heading, code));

        // A horizontal container keeps long resource names readable.
        android.widget.HorizontalScrollView hScroll = new android.widget.HorizontalScrollView(this);
        hScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(-1, -2);
        codeParams.topMargin = dp(8);
        block.addView(hScroll, codeParams);
        TextView codeView = text(code, 12, ink, false);
        codeView.setTypeface(Typeface.MONOSPACE);
        codeView.setText(highlightCode(heading, code));
        codeView.setTextIsSelectable(true);
        hScroll.addView(codeView);
    }

    private SpannableString highlightCode(String heading, String code) {
        SpannableString highlighted = new SpannableString(code);
        int purple = getColor(darkMode ? R.color.syntax_purple_dark : R.color.syntax_purple_light);
        int blue = getColor(darkMode ? R.color.syntax_blue_dark : R.color.syntax_blue_light);
        int orange = getColor(darkMode ? R.color.syntax_orange_dark : R.color.syntax_orange_light);
        if (heading.equals("XML layout") || heading.equals("Vector drawable XML")) {
            colorMatches(highlighted, "</?([A-Za-z][\\w.-]*)", 1, purple);
            colorMatches(highlighted, "\\b([A-Za-z_:][\\w:.-]*)(?=\\s*=)", 1, blue);
            colorMatches(highlighted, "\"[^\"\\n]*\"", 0, orange);
            colorMatches(highlighted, "(?s)<!--.*?-->", 0, muted);
        } else {
            colorMatches(highlighted, "\\b(?:new|val|var|fun|import|class|public|private|static|final|void)\\b", 0, purple);
            colorMatches(highlighted, "\\b(?:ImageView|R|android)\\b", 0, blue);
            colorMatches(highlighted, "\\.([A-Za-z_]\\w*)(?=\\()", 1, purple);
            colorMatches(highlighted, "(?:android\\.)?R\\.drawable\\.[A-Za-z_]\\w*", 0, accentText);
            colorMatches(highlighted, "\"[^\"\\n]*\"", 0, orange);
            colorMatches(highlighted, "(?m)//[^\\n]*", 0, muted);
        }
        return highlighted;
    }

    private void colorMatches(SpannableString text, String regex, int group, int color) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        while (matcher.find()) {
            text.setSpan(new ForegroundColorSpan(color), matcher.start(group), matcher.end(group),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void copyText(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
            Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
        }
    }

    private String readAsset(String path) throws Exception {
        try (InputStream in = getAssets().open(path); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toString("UTF-8");
        }
    }

    private String displayName(String name) {
        String clean = name.startsWith("ic_md_") ? name.substring(6) : name;
        clean = clean.replace("ic_menu_", "").replace("ic_dialog_", "").replace('_', ' ');
        if (clean.isEmpty()) return name;
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable shape(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(0);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
