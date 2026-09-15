package com.kiver.fireretro;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String PLAYLIST = "/sdcard/RetroArch/playlists/builtin/content_favorites.lpl";
    private static final String RETROARCH = "com.retroarch.ra32";
    private static final String RETROARCH_CONFIG = "/sdcard/Android/data/com.retroarch.ra32/files/retroarch.cfg";
    private static final String THEME_DIRECTORY = "/sdcard/Android/data/com.kiver.fireretro/files/theme";
    private static final String COVER_DIRECTORY = "/sdcard/Android/data/com.kiver.fireretro/files/covers";
    private static final String PREFS = "fireretro_state";
    private static final String LAST_GAME = "last_game";
    private static final String LAST_SLIDE = "last_slide";

    private final List<Game> games = new ArrayList<>();
    private final List<Slide> slides = new ArrayList<>();
    private final Map<String, View> platformAnchors = new LinkedHashMap<>();
    private final Handler carouselHandler = new Handler();
    private final Runnable carouselTask = new Runnable() {
        @Override public void run() {
            if (!slides.isEmpty()) {
                showSlide(ThemeState.nextSlide(activeSlide, slides.size()), true);
                scheduleCarousel();
            }
        }
    };

    private int screenWidth;
    private int screenHeight;
    private int responsiveColumns;
    private int headerHeight;
    private int overlayOpacity = 70;
    private int autoAdvanceSeconds = 5;
    private int activeSlide;
    private float uiScale;
    private ScrollView homeScroll;
    private LinearLayout sections;
    private TextView controllerStatusView;
    private TextView stickyPlatformView;
    private TextView slideTitleView;
    private TextView slideCaptionView;
    private TextView slideCounterView;
    private TextView dotsView;
    private ImageView heroArt;
    private View heroView;
    private EditText searchView;
    private List<Game> allGames = new ArrayList<>();
    private String selectedPlatform = "TODOS";
    private String searchQuery = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(1024, 1024);
        buildScreen();
    }

    @Override protected void onResume() {
        super.onResume();
        updateControllerStatus();
        scheduleCarousel();
    }

    @Override protected void onPause() {
        carouselHandler.removeCallbacks(carouselTask);
        super.onPause();
    }

    @Override protected void onDestroy() {
        carouselHandler.removeCallbacks(carouselTask);
        super.onDestroy();
    }

    private void buildScreen() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        uiScale = Math.max(0.62f, Math.min(1.15f, Math.min(screenWidth / 1920f, metrics.heightPixels / 1080f)));
        responsiveColumns = screenWidth >= scaled(1350) ? 5 : (screenWidth >= scaled(900) ? 4 : (screenWidth >= scaled(700) ? 3 : 2));
        headerHeight = Math.max(scaled(174), Math.round(screenHeight * 0.35f));
        allGames = readGames();
        readTheme();
        activeSlide = ThemeState.normalizeIndex(getSharedPreferences(PREFS, MODE_PRIVATE).getInt(LAST_SLIDE, 0), slides.size());
        final int restoredIndex = LauncherState.restoreIndex(getSharedPreferences(PREFS, MODE_PRIVATE).getInt(LAST_GAME, 0), allGames.size());

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(scaled(38), scaled(14), scaled(38), scaled(12));
        page.setBackgroundColor(Color.rgb(6, 14, 43));
        page.addView(createHero(), new LinearLayout.LayoutParams(-1, headerHeight));
        page.addView(createLibraryControls(restoredIndex), new LinearLayout.LayoutParams(-1, scaled(122)));

        stickyPlatformView = new TextView(this);
        stickyPlatformView.setText("TODOS OS JOGOS");
        stickyPlatformView.setTextColor(Color.WHITE);
        stickyPlatformView.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled(22));
        stickyPlatformView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        stickyPlatformView.setGravity(Gravity.CENTER_VERTICAL);
        stickyPlatformView.setPadding(scaled(20), 0, scaled(20), 0);
        stickyPlatformView.setBackground(panelBackground(0xEE102750, 0xFF45D7EC, scaled(2), scaled(13)));
        page.addView(stickyPlatformView, new LinearLayout.LayoutParams(-1, scaled(46)));

        sections = new LinearLayout(this);
        sections.setOrientation(LinearLayout.VERTICAL);
        sections.setPadding(0, scaled(8), 0, scaled(12));
        renderSections(restoredIndex);
        homeScroll = new ScrollView(this);
        homeScroll.setFillViewport(true);
        homeScroll.setFocusable(true);
        homeScroll.setFocusableInTouchMode(true);
        homeScroll.addView(sections);
        homeScroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override public void onScrollChange(View v, int x, int y, int oldX, int oldY) { updateStickyPlatform(y); }
        });
        homeScroll.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK && homeScroll.getScrollY() > 0) {
                    returnHome();
                    return true;
                }
                return false;
            }
        });
        page.addView(homeScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(page);
        showSlide(activeSlide, false);
        if (heroView != null) heroView.requestFocus();
        scheduleCarousel();
    }

    private View createHero() {
        final FrameLayout hero = new FrameLayout(this);
        hero.setFocusable(true);
        hero.setFocusableInTouchMode(true);
        heroView = hero;
        hero.setContentDescription("Carrossel de destaques. Use esquerda e direita para trocar o slide.");
        hero.setBackground(panelBackground(0xFF0C1D51, 0xFF5B7DBB, scaled(2), scaled(18)));
        hero.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View view, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { previousSlide(); return true; }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { nextSlide(); return true; }
                if (keyCode == KeyEvent.KEYCODE_MENU) { showPersonalizeHint(); return true; }
                return false;
            }
        });

        heroArt = new ImageView(this);
        heroArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroArt.setBackgroundColor(0xFF0B1F4E);
        hero.addView(heroArt, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackgroundColor(Color.argb(ThemeState.overlayAlpha(overlayOpacity), 3, 13, 42));
        hero.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        TextView brand = heroText("JOGOS RETRO", 16, 0xFF8FEFFF, true);
        FrameLayout.LayoutParams brandLp = new FrameLayout.LayoutParams(-2, scaled(34), Gravity.TOP | Gravity.LEFT);
        brandLp.setMargins(scaled(28), scaled(20), 0, 0);
        hero.addView(brand, brandLp);
        slideTitleView = heroText("", 38, Color.WHITE, true);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(-2, scaled(58), Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        titleLp.setMargins(scaled(24), scaled(8), scaled(24), 0);
        hero.addView(slideTitleView, titleLp);
        slideCaptionView = heroText("", 19, 0xFFE5EEFF, false);
        FrameLayout.LayoutParams captionLp = new FrameLayout.LayoutParams(-2, scaled(38), Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        captionLp.setMargins(scaled(24), scaled(62), scaled(24), 0);
        hero.addView(slideCaptionView, captionLp);

        TextView personalize = heroText("☰  PERSONALIZAR", 15, Color.WHITE, true);
        personalize.setGravity(Gravity.CENTER);
        personalize.setPadding(scaled(12), 0, scaled(12), 0);
        personalize.setFocusable(true);
        personalize.setBackground(panelBackground(0xCC132E62, 0xFF85E8F7, scaled(2), scaled(12)));
        personalize.setContentDescription("Personalizar. Abra Slides e aparência no FireRetro Manager do computador.");
        personalize.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { showPersonalizeHint(); } });
        FrameLayout.LayoutParams personalizeLp = new FrameLayout.LayoutParams(scaled(214), scaled(42), Gravity.TOP | Gravity.RIGHT);
        personalizeLp.setMargins(0, scaled(18), scaled(24), 0);
        hero.addView(personalize, personalizeLp);

        TextView left = carouselButton("‹", "Slide anterior");
        left.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { previousSlide(); } });
        FrameLayout.LayoutParams leftLp = new FrameLayout.LayoutParams(scaled(52), scaled(76), Gravity.LEFT | Gravity.CENTER_VERTICAL);
        leftLp.setMargins(scaled(18), 0, 0, 0);
        hero.addView(left, leftLp);
        TextView right = carouselButton("›", "Próximo slide");
        right.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { nextSlide(); } });
        FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(scaled(52), scaled(76), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        rightLp.setMargins(0, 0, scaled(18), 0);
        hero.addView(right, rightLp);

        dotsView = heroText("", 16, Color.WHITE, false);
        dotsView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams dotsLp = new FrameLayout.LayoutParams(-1, scaled(30), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dotsLp.setMargins(scaled(120), 0, scaled(120), scaled(13));
        hero.addView(dotsView, dotsLp);
        TextView shortcuts = heroText("← →  SLIDES / ABAS     ↑ ↓  NAVEGAR     A  ABRIR     B  VOLTAR     MENU  PERSONALIZAR", 13, 0xFFE7F2FF, true);
        shortcuts.setGravity(Gravity.CENTER);
        shortcuts.setContentDescription("Atalhos: esquerda e direita trocam slides ou abas; cima e baixo navegam; A abre; B volta; Menu personaliza.");
        FrameLayout.LayoutParams shortcutsLp = new FrameLayout.LayoutParams(-1, scaled(27), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        shortcutsLp.setMargins(scaled(235), 0, scaled(170), scaled(39));
        hero.addView(shortcuts, shortcutsLp);
        slideCounterView = heroText("", 13, 0xFFBBD2FF, true);
        slideCounterView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams counterLp = new FrameLayout.LayoutParams(scaled(124), scaled(30), Gravity.BOTTOM | Gravity.RIGHT);
        counterLp.setMargins(0, 0, scaled(25), scaled(13));
        hero.addView(slideCounterView, counterLp);
        controllerStatusView = heroText("", 13, 0xFF68F5C0, true);
        controllerStatusView.setGravity(Gravity.CENTER);
        controllerStatusView.setPadding(scaled(10), 0, scaled(10), 0);
        controllerStatusView.setBackground(panelBackground(0xBB0B234F, 0x995B7DBB, scaled(1), scaled(10)));
        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(scaled(210), scaled(32), Gravity.BOTTOM | Gravity.LEFT);
        statusLp.setMargins(scaled(25), 0, 0, scaled(12));
        hero.addView(controllerStatusView, statusLp);
        updateControllerStatus();
        return hero;
    }

    private LinearLayout createLibraryControls(final int restoredIndex) {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(scaled(6), scaled(8), scaled(6), scaled(4));
        HorizontalScrollView platformScroll = new HorizontalScrollView(this);
        platformScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout platforms = new LinearLayout(this);
        platforms.setGravity(Gravity.CENTER_VERTICAL);
        for (String platform : new String[]{"TODOS", "FAVORITOS", "NES", "SNES", "Mega Drive", "GBA", "PlayStation"}) platforms.addView(platformButton(platform, restoredIndex));
        platformScroll.addView(platforms);
        controls.addView(platformScroll, new LinearLayout.LayoutParams(-1, scaled(48)));
        searchView = new EditText(this);
        searchView.setSingleLine(true);
        searchView.setHint("⌕  BUSCAR JOGO");
        searchView.setHintTextColor(0xFFC5D4F5);
        searchView.setTextColor(Color.WHITE);
        searchView.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled(18));
        searchView.setPadding(scaled(17), 0, scaled(17), 0);
        searchView.setBackground(panelBackground(0xCC102B5C, 0xFF45D7EC, scaled(2), scaled(13)));
        searchView.setContentDescription("Buscar jogo. Pressione selecionar para digitar.");
        searchView.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { searchQuery = s.toString(); renderSections(restoredIndex); }
            public void afterTextChanged(android.text.Editable s) { }
        });
        controls.addView(searchView, new LinearLayout.LayoutParams(-1, scaled(54)));
        return controls;
    }

    private TextView platformButton(final String platform, final int restoredIndex) {
        TextView button = new TextView(this);
        button.setText(platform);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled(15));
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setPadding(scaled(17), 0, scaled(17), 0);
        button.setContentDescription("Abrir categoria " + platform);
        button.setBackground(panelBackground("TODOS".equals(platform) ? 0xFF1975A8 : 0xCC142E60, 0xFF39D8EA, scaled(2), scaled(13)));
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                selectedPlatform = "FAVORITOS".equals(platform) ? "TODOS" : LauncherState.normalizePlatform(platform);
                if (homeScroll != null) homeScroll.scrollTo(0, 0);
                renderSections(restoredIndex);
                if ("FAVORITOS".equals(platform)) Toast.makeText(MainActivity.this, "Seus jogos recentes aparecem primeiro.", Toast.LENGTH_SHORT).show();
            }
        });
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean focused) {
                v.setBackground(panelBackground(focused ? 0xFF278FC0 : 0xCC142E60, focused ? 0xFFFFFFFF : 0xFF39D8EA, scaled(focused ? 4 : 2), scaled(13)));
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, scaled(40));
        lp.setMargins(0, 0, scaled(8), 0);
        button.setLayoutParams(lp);
        return button;
    }

    private void renderSections(int restoredIndex) {
        if (sections == null) return;
        sections.removeAllViews();
        platformAnchors.clear();
        Map<String, List<Game>> grouped = new LinkedHashMap<>();
        for (String platform : new String[]{"NES", "SNES", "Mega Drive", "GBA", "PlayStation"}) grouped.put(platform, new ArrayList<Game>());
        for (Game game : allGames) {
            if (LauncherState.matchesFilter(game.label, game.platform, selectedPlatform, searchQuery)) {
                if (!grouped.containsKey(game.platform)) grouped.put(game.platform, new ArrayList<Game>());
                grouped.get(game.platform).add(game);
            }
        }
        for (Map.Entry<String, List<Game>> section : grouped.entrySet()) {
            if (section.getValue().isEmpty()) continue;
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(scaled(10), scaled(10), scaled(10), scaled(10));
            panel.setBackground(panelBackground(0x99112658, 0x8844C9FF, scaled(2), scaled(18)));
            platformAnchors.put(section.getKey(), panel);
            TextView title = new TextView(this);
            title.setText(section.getKey().toUpperCase());
            title.setTextColor(Color.WHITE);
            title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled(22));
            title.setGravity(Gravity.CENTER);
            panel.addView(title, new LinearLayout.LayoutParams(-1, scaled(45)));
            LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); panel.addView(rows);
            List<Game> gamesForPlatform = section.getValue();
            for (int i = 0; i < gamesForPlatform.size(); i += responsiveColumns) {
                LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
                rows.addView(row, new LinearLayout.LayoutParams(-1, scaled(340)));
                int end = Math.min(i + responsiveColumns, gamesForPlatform.size());
                for (int j = i; j < end; j++) {
                    Game game = gamesForPlatform.get(j);
                    addCard(row, game, allGames.indexOf(game), allGames.indexOf(game) == restoredIndex);
                }
                for (int j = end; j < i + responsiveColumns; j++) row.addView(new View(this), new LinearLayout.LayoutParams(0, scaled(330), 1f));
            }
            LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(-1, -2);
            panelLp.setMargins(0, scaled(6), 0, scaled(8));
            sections.addView(panel, panelLp);
        }
        if (sections.getChildCount() == 0) {
            TextView empty = heroText("Nenhum jogo encontrado. Ajuste a busca ou escolha outra aba.", 20, Color.WHITE, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(scaled(16), scaled(32), scaled(16), scaled(32));
            sections.addView(empty, new LinearLayout.LayoutParams(-1, scaled(150)));
        }
        updateStickyPlatform(0);
    }

    private void addCard(LinearLayout row, final Game game, final int gameIndex, boolean focusFirst) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setPadding(scaled(6), scaled(6), scaled(6), scaled(5));
        card.setBackground(cardBackground(false));
        card.setContentDescription(game.label + ", " + game.platform + ". Pressione selecionar para abrir.");
        FrameLayout coverArea = new FrameLayout(this);
        coverArea.setBackground(panelBackground(0xFF0D214A, 0xFF386DB2, scaled(1), scaled(11)));
        ImageView cover = new ImageView(this);
        Bitmap externalCover = loadGameCover(game.image);
        int drawable = TextUtils.isEmpty(game.image) ? 0 : getResources().getIdentifier(game.image, "drawable", getPackageName());
        if (externalCover != null) {
            cover.setImageBitmap(externalCover);
            cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
            coverArea.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        } else if (drawable != 0) {
            cover.setImageResource(drawable);
            cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
            coverArea.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        } else coverArea.addView(createMissingCover(game), new FrameLayout.LayoutParams(-1, -1));
        card.addView(coverArea, new LinearLayout.LayoutParams(-1, scaled(277)));
        TextView label = new TextView(this);
        label.setText(game.label); label.setTextColor(Color.WHITE); label.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled(15)); label.setGravity(Gravity.CENTER);
        label.setIncludeFontPadding(false); label.setMaxLines(2); label.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(label, new LinearLayout.LayoutParams(-1, scaled(43)));
        card.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { rememberGame(gameIndex); launch(game); } });
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) { view.setBackground(cardBackground(focused)); if (focused) rememberGame(gameIndex); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, scaled(332), 1f);
        lp.setMargins(scaled(5), scaled(4), scaled(5), scaled(4));
        row.addView(card, lp);
        if (focusFirst) card.requestFocus();
    }

    private void updateStickyPlatform(int scrollY) {
        if (stickyPlatformView == null) return;
        if (scrollY <= 0) {
            stickyPlatformView.setText("TODOS".equals(selectedPlatform) ? "TODOS OS JOGOS" : selectedPlatform.toUpperCase());
            return;
        }
        String current = "TODOS OS JOGOS";
        for (Map.Entry<String, View> entry : platformAnchors.entrySet()) if (entry.getValue().getTop() <= scrollY + scaled(8)) current = entry.getKey().toUpperCase();
        stickyPlatformView.setText("TODOS".equals(selectedPlatform) ? current : selectedPlatform.toUpperCase());
    }

    private void showSlide(int index, boolean animate) {
        if (slides.isEmpty() || heroArt == null) return;
        activeSlide = ThemeState.normalizeIndex(index, slides.size());
        Slide slide = slides.get(activeSlide);
        Bitmap bitmap = loadSlideBitmap(slide.image);
        if (bitmap != null) heroArt.setImageBitmap(bitmap);
        heroArt.setAlpha(animate ? 0.25f : 1f);
        if (animate) heroArt.animate().alpha(1f).setDuration(240).start();
        slideTitleView.setText(slide.title); slideCaptionView.setText(slide.caption);
        slideCounterView.setText("SLIDE " + (activeSlide + 1) + " DE " + slides.size());
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < slides.size(); i++) dots.append(i == activeSlide ? "●  " : "○  ");
        dotsView.setText(dots.toString().trim());
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(LAST_SLIDE, activeSlide).apply();
    }

    private void nextSlide() { showSlide(ThemeState.nextSlide(activeSlide, slides.size()), true); scheduleCarousel(); }
    private void previousSlide() { showSlide(ThemeState.previousSlide(activeSlide, slides.size()), true); scheduleCarousel(); }
    private void scheduleCarousel() {
        carouselHandler.removeCallbacks(carouselTask);
        if (slides.size() > 1) carouselHandler.postDelayed(carouselTask, ThemeState.autoAdvanceMilliseconds(autoAdvanceSeconds));
    }
    private void showPersonalizeHint() { Toast.makeText(this, "No computador, abra Slides e aparência no FireRetro Manager.", Toast.LENGTH_LONG).show(); }

    @Override public void onBackPressed() {
        if (homeScroll != null && (homeScroll.getScrollY() > 0 || !"TODOS".equals(selectedPlatform) || !searchQuery.trim().isEmpty())) { returnHome(); return; }
        super.onBackPressed();
    }
    private void returnHome() {
        selectedPlatform = "TODOS";
        if (searchView != null && !searchQuery.isEmpty()) searchView.setText(""); else renderSections(0);
        if (homeScroll != null) { homeScroll.stopNestedScroll(); homeScroll.scrollTo(0, 0); homeScroll.requestFocus(); }
    }

    private TextView createMissingCover(Game game) {
        TextView fallback = new TextView(this);
        fallback.setText(game.platform.toUpperCase() + "\n\n" + game.label + "\n\nCAPA EM BREVE"); fallback.setTextColor(Color.WHITE);
        fallback.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled(16)); fallback.setGravity(Gravity.CENTER); fallback.setPadding(scaled(12), scaled(12), scaled(12), scaled(12));
        fallback.setMaxLines(5); fallback.setEllipsize(TextUtils.TruncateAt.END); fallback.setBackground(panelBackground(0xFF173A73, 0xFF66D5FF, scaled(2), scaled(10)));
        return fallback;
    }
    private TextView heroText(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(text); view.setTextColor(color); view.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled(size));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL); view.setSingleLine(true); view.setEllipsize(TextUtils.TruncateAt.END); return view;
    }
    private TextView carouselButton(String text, String description) {
        TextView button = heroText(text, 45, Color.WHITE, false); button.setGravity(Gravity.CENTER); button.setFocusable(true); button.setFocusableInTouchMode(true); button.setContentDescription(description);
        button.setBackground(panelBackground(0xB0183970, 0xFF8DEBFF, scaled(2), scaled(20)));
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) { view.setBackground(panelBackground(focused ? 0xFF268EC0 : 0xB0183970, focused ? Color.WHITE : 0xFF8DEBFF, scaled(focused ? 4 : 2), scaled(20))); }
        }); return button;
    }
    private void rememberGame(int gameIndex) { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(LAST_GAME, gameIndex).apply(); }
    private void updateControllerStatus() {
        if (controllerStatusView == null) return;
        boolean connected = controllerConnected(); controllerStatusView.setText(LauncherState.controllerStatus(connected)); controllerStatusView.setTextColor(connected ? 0xFF68F5C0 : 0xFFFFC35A);
    }
    private boolean controllerConnected() {
        for (int deviceId : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(deviceId); if (device == null || device.isVirtual()) continue; int sources = device.getSources();
            if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) return true;
        } return false;
    }
    private GradientDrawable panelBackground(int color, int strokeColor, int strokeWidth, int radius) { return drawable(color, strokeColor, strokeWidth, radius); }
    private GradientDrawable cardBackground(boolean focused) { return drawable(focused ? 0xEE267DAE : 0xBB132D60, focused ? 0xFFFFFFFF : 0xCC39D8EA, scaled(focused ? 5 : 2), scaled(15)); }
    private int scaled(int value) { return Math.max(1, Math.round(value * uiScale)); }
    private GradientDrawable drawable(int color, int strokeColor, int strokeWidth, int radius) { GradientDrawable background = new GradientDrawable(); background.setColor(color); background.setCornerRadius(radius); background.setStroke(strokeWidth, strokeColor); return background; }

    private List<Game> readGames() {
        games.clear();
        for (CatalogStore.CatalogGame catalogGame : CatalogStore.load(this, new File("/sdcard/Android/data/com.kiver.fireretro/files/catalog/games.json"))) {
            games.add(new Game(catalogGame.label, catalogGame.path, catalogGame.corePath, catalogGame.platform, catalogGame.image));
        }
        return games;
    }
    private void readTheme() {
        slides.clear(); File customTheme = new File(THEME_DIRECTORY, "slides.json");
        if (customTheme.isFile()) try { parseTheme(new BufferedReader(new FileReader(customTheme))); } catch (Exception ignored) { }
        if (slides.isEmpty()) try { InputStream stream = getAssets().open("slides.json"); parseTheme(new BufferedReader(new InputStreamReader(stream, "UTF-8"))); stream.close(); } catch (Exception ignored) { }
        if (slides.isEmpty()) slides.add(new Slide("retro.png", "JOGOS RETRO", "Clássicos prontos para jogar"));
    }
    private void parseTheme(BufferedReader reader) throws Exception {
        StringBuilder text = new StringBuilder(); String line; while ((line = reader.readLine()) != null) text.append(line); reader.close(); JSONObject root = new JSONObject(text.toString());
        autoAdvanceSeconds = root.optInt("autoAdvanceSeconds", 5); overlayOpacity = root.optInt("overlayOpacity", 70); JSONArray items = root.optJSONArray("slides"); if (items == null) return;
        for (int i = 0; i < items.length(); i++) { JSONObject item = items.optJSONObject(i); if (item == null) continue; String image = item.optString("image", ""); if (!image.isEmpty()) slides.add(new Slide(image, item.optString("title", "JOGOS RETRO"), item.optString("caption", "Escolha um jogo e divirta-se"))); }
    }
    private Bitmap loadSlideBitmap(String imageName) {
        try { File customImage = new File(THEME_DIRECTORY, imageName); if (customImage.isFile()) return BitmapFactory.decodeFile(customImage.getAbsolutePath()); InputStream stream = getAssets().open("slides/" + imageName); Bitmap bitmap = BitmapFactory.decodeStream(stream); stream.close(); return bitmap; } catch (Exception ignored) { return null; }
    }
    private Bitmap loadGameCover(String imageName) {
        if (TextUtils.isEmpty(imageName)) return null;
        String fileName = new File(imageName).getName();
        if (!fileName.equals(imageName)) return null;
        File externalCover = new File(COVER_DIRECTORY, fileName);
        if (!externalCover.isFile()) return null;
        return BitmapFactory.decodeFile(externalCover.getAbsolutePath());
    }
    private void launch(Game game) {
        Intent intent = new Intent(); intent.setComponent(new ComponentName(RETROARCH, "com.retroarch.browser.retroactivity.RetroActivityFuture")); intent.putExtra("ROM", game.path); intent.putExtra("LIBRETRO", game.core); intent.putExtra("CONFIGFILE", RETROARCH_CONFIG); startActivity(intent);
    }
    private static class Slide { final String image, title, caption; Slide(String image, String title, String caption) { this.image = image; this.title = title; this.caption = caption; } }
    private static class Game { final String label, path, core, platform, image; Game(String label, String path, String core, String platform, String image) { this.label = label; this.path = path; this.core = core; this.platform = platform; this.image = image; } }
}
