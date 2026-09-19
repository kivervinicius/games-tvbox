package com.kiver.fireretro;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String PLAYLIST = "/sdcard/RetroArch/playlists/builtin/content_favorites.lpl";
    private static final String RETROARCH = "com.retroarch.ra32";
    private static final String RETROARCH_CONFIG = "/sdcard/Android/data/com.retroarch.ra32/files/retroarch.cfg";
    private static final String THEME_DIRECTORY = "/sdcard/Android/data/com.kiver.fireretro/files/theme";
    private static final String COVER_DIRECTORY = "/sdcard/Android/data/com.kiver.fireretro/files/covers";
    private static final File EXTERNAL_CATALOG_FILE = new File("/sdcard/Android/data/com.kiver.fireretro/files/catalog/games.json");
    private static final File EXTERNAL_APPS_FILE = new File("/sdcard/Android/data/com.kiver.fireretro/files/catalog/apps.json");
    private static final String PREFS = "fireretro_state";
    private static final String LAST_GAME = "last_game";
    private static final String LAST_SLIDE = "last_slide";
    private static final String SELECTED_THEME = "selected_theme";
    private static final String THEME_TITLE = "theme_title";
    private static final String THEME_SUBTITLE = "theme_subtitle";

    private final List<Game> games = new ArrayList<>();
    private final List<AndroidAppEntry> androidApps = new ArrayList<>();
    private final List<Slide> slides = new ArrayList<>();
    private final Map<String, View> platformAnchors = new LinkedHashMap<>();
    private final Map<String, Bitmap> themeArtworkCache = new LinkedHashMap<>();
    private final Handler carouselHandler = new Handler();
    private final Handler cloudSyncHandler = new Handler();
    private final Runnable periodicCloudSync = new Runnable() {
        @Override public void run() { if (cloudSync == null || !cloudSync.isRunning()) startRemoteSync(); cloudSyncHandler.postDelayed(this, 30L * 60L * 1000L); }
    };
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
    private View heroShade;
    private View heroView;
    private View appearanceButtonView;
    private TextView searchView;
    private LinearLayout galleryNavigation;
    private List<Game> allGames = new ArrayList<>();
    private String selectedPlatform = "TODOS";
    private String searchQuery = "";
    private SettingsNavigation.Screen activeSettingsScreen = SettingsNavigation.Screen.HOME;
    private boolean searchEditorOpen;
    private boolean remoteSettingsOpen;
    private boolean appearanceTextEditorOpen;
    private boolean controllerTestOpen;
    private String selectedControllerModel = "";
    private String appearanceFocusTag = "";
    private long catalogLastModified = -1L;
    private long catalogLength = -1L;
    private long appsLastModified = -1L;
    private long appsLength = -1L;
    private CloudLibrarySync cloudSync;
    private TextView remoteStatusView;
    private View platformFilterView;
    private final ThemeCatalog themeCatalog = ThemeCatalog.builtIns();
    private String selectedThemeId = "arcade-moderno";
    private ThemeCustomization themeCustomization = ThemeCustomization.defaults("Arcade moderno");
    private final TvNavigationState navigationState = new TvNavigationState();
    private final ControllerInputRouter controllerInputRouter = new ControllerInputRouter();
    private final List<View> gameCardViews = new ArrayList<>();
    private String lastFocusedGamePath = "";
    private SafeAreaProfile safeAreaProfile = new SafeAreaProfile();
    private LibraryUiState libraryUiState = new LibraryUiState(LibraryUiState.Status.IDLE, 0, "", "", "");
    private ThemeProfile activeThemeProfile = ThemeProfile.arcade();
    private LinearLayout shellRoot;
    private LinearLayout sidebarRoot;
    private TextView shellTitleView;
    private TextView shellSubtitleView;
    private TextView shellStatusView;
    private View lastContentFocus;
    private int sidebarFirstId;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(1024, 1024);
        buildScreen();
    }

    @Override protected void onResume() {
        super.onResume();
        boolean appsChanged = externalAppsChanged();
        if (appsChanged || "APPS".equals(selectedPlatform)) { readAndroidApps(); if (appsChanged || "APPS".equals(selectedPlatform)) buildScreen(); }
        if (externalCatalogChanged()) buildScreen();
        updateControllerStatus();
        ControllerRegistry.observe(this);
        requestStoragePermission();
        ensureControllerProfile();
        scheduleCarousel();
        startRemoteSync();
        cloudSyncHandler.removeCallbacks(periodicCloudSync);
        cloudSyncHandler.postDelayed(periodicCloudSync, 30L * 60L * 1000L);
    }

    @Override protected void onPause() {
        carouselHandler.removeCallbacks(carouselTask);
        cloudSyncHandler.removeCallbacks(periodicCloudSync);
        if (cloudSync != null) cloudSync.stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        carouselHandler.removeCallbacks(carouselTask);
        cloudSyncHandler.removeCallbacks(periodicCloudSync);
        if (cloudSync != null) cloudSync.close();
        super.onDestroy();
    }

    private void buildScreen() {
        activeSettingsScreen = SettingsNavigation.Screen.HOME;
        searchEditorOpen = false;
        remoteSettingsOpen = false;
        appearanceTextEditorOpen = false;
        controllerTestOpen = false;
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        uiScale = Math.max(0.62f, Math.min(1.15f, Math.min(screenWidth / 1920f, metrics.heightPixels / 1080f)));
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        safeAreaProfile = new SafeAreaProfile(preferences.getInt("safe_horizontal", 5), preferences.getInt("safe_vertical", 4));
        responsiveColumns = Math.min(themeCustomization.cardDensity, screenWidth >= scaled(1350) ? 4 : (screenWidth >= scaled(900) ? 3 : 2));
        headerHeight = scaled(110);
        allGames = readGames();
        readAndroidApps();
        rememberExternalCatalogVersion();
        loadRemoteThemes();
        readTheme();
        selectedThemeId = getSharedPreferences(PREFS, MODE_PRIVATE).getString(SELECTED_THEME, selectedThemeId);
        loadThemeCustomization();
        activeSlide = ThemeState.normalizeIndex(getSharedPreferences(PREFS, MODE_PRIVATE).getInt(LAST_SLIDE, 0), slides.size());
        final int restoredIndex = LauncherState.restoreIndex(getSharedPreferences(PREFS, MODE_PRIVATE).getInt(LAST_GAME, 0), allGames.size());

        shellRoot = new LinearLayout(this);
        shellRoot.setOrientation(LinearLayout.HORIZONTAL);
        shellRoot.setBackgroundColor(0xFF050B18);
        shellRoot.setPadding(safeAreaProfile.horizontalInset(screenWidth), safeAreaProfile.verticalInset(screenHeight), safeAreaProfile.horizontalInset(screenWidth), safeAreaProfile.verticalInset(screenHeight));
        sidebarRoot = createSidebar();
        shellRoot.addView(sidebarRoot, new LinearLayout.LayoutParams(scaled(250), -1));
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(scaled(24), 0, 0, 0);
        main.addView(createCompactTopBar(), new LinearLayout.LayoutParams(-1, scaled(94)));
        main.addView(createLibraryControls(restoredIndex), new LinearLayout.LayoutParams(-1, scaled(126)));
        if (searchView != null) { searchView.setNextFocusDownId(searchView.getId()); if (appearanceButtonView != null) appearanceButtonView.setNextFocusDownId(searchView.getId()); }
        stickyPlatformView = new TextView(this);
        stickyPlatformView.setText("TODOS OS JOGOS"); stickyPlatformView.setTextColor(Color.WHITE); stickyPlatformView.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled(20)); stickyPlatformView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); stickyPlatformView.setGravity(Gravity.CENTER_VERTICAL); stickyPlatformView.setPadding(scaled(18), 0, scaled(18), 0); stickyPlatformView.setBackground(panelBackground(0xEE102750, 0xFF45D7EC, scaled(2), scaled(13)));
        main.addView(stickyPlatformView, new LinearLayout.LayoutParams(-1, scaled(44)));
        sections = new LinearLayout(this);
        sections.setOrientation(LinearLayout.VERTICAL);
        sections.setPadding(scaled(16), scaled(14), scaled(16), scaled(28));
        sections.setClipToPadding(false);
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
        main.addView(homeScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        main.addView(createArcadeFooter(), new LinearLayout.LayoutParams(-1, scaled(64)));
        shellRoot.addView(main, new LinearLayout.LayoutParams(0, -1, 1f));
        shellRoot.setOnKeyListener((v, keyCode, event) -> event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK && returnHomeKey());
        setContentView(shellRoot);
        applySelectedTheme();
        if (lastContentFocus != null) lastContentFocus.requestFocus();
        else if (searchView != null) searchView.requestFocus();
    }

    private boolean returnHomeKey() { if (homeScroll != null && homeScroll.getScrollY() > 0) { homeScroll.smoothScrollTo(0, 0); return true; } returnHome(); return true; }

    private LinearLayout createCompactTopBar() {
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(0, scaled(8), 0, scaled(8));
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL); titles.setGravity(Gravity.CENTER_VERTICAL);
        shellTitleView = heroText(themeCustomization.title, 27, Color.WHITE, true); shellSubtitleView = heroText(themeCustomization.subtitle, 14, 0xFFBBD2FF, false);
        titles.addView(shellTitleView, new LinearLayout.LayoutParams(-1, scaled(44))); titles.addView(shellSubtitleView, new LinearLayout.LayoutParams(-1, scaled(28))); bar.addView(titles, new LinearLayout.LayoutParams(0, -1, 1f));
        shellStatusView = heroText("●  ONLINE", 14, 0xFF42D392, true); shellStatusView.setGravity(Gravity.CENTER); shellStatusView.setPadding(scaled(14), 0, scaled(14), 0); shellStatusView.setBackground(panelBackground(0x99203D3B, 0x6642D392, scaled(1), scaled(12))); bar.addView(shellStatusView, new LinearLayout.LayoutParams(scaled(170), scaled(44)));
        return bar;
    }

    private LinearLayout createSidebar() {
        LinearLayout nav = new LinearLayout(this); nav.setOrientation(LinearLayout.VERTICAL); nav.setPadding(0, scaled(8), scaled(10), scaled(8)); nav.setBackground(panelBackground(0xCC091426, 0x3345D7EC, scaled(1), scaled(18)));
        TextView brand = heroText("JOGOS\nRETRO", 20, 0xFF38D9FF, true); brand.setGravity(Gravity.CENTER); nav.addView(brand, new LinearLayout.LayoutParams(-1, scaled(82)));
        String[] labels = {"▦  JOGOS", "★  FAVORITOS", "◆  JOGOS ANDROID", "▣  APLICATIVOS", "⌁  CONTROLES", "✦  APARÊNCIA", "⚙  BIBLIOTECA"};
        final List<View> buttons = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            final int index = i; TextView button = heroText(labels[i], 16, Color.WHITE, true); button.setGravity(Gravity.CENTER_VERTICAL); button.setPadding(scaled(18), 0, scaled(8), 0); button.setFocusable(true); button.setFocusableInTouchMode(true); button.setId(View.generateViewId()); button.setContentDescription(labels[i]);
            button.setOnClickListener(v -> selectSection(index));
            button.setOnFocusChangeListener((v, focused) -> { v.setBackground(panelBackground(focused ? 0xFF38D9FF : 0x00112233, focused ? 0xFFFFFFFF : 0x0045D7EC, scaled(focused ? 3 : 0), scaled(12))); if (focused) navigationState.rememberFocus(String.valueOf(v.getId())); });
            buttons.add(button); nav.addView(button, new LinearLayout.LayoutParams(-1, scaled(62)));
        }
        sidebarFirstId = buttons.get(0).getId();
        for (int i = 0; i < buttons.size(); i++) { View b = buttons.get(i); b.setNextFocusUpId(buttons.get((i + buttons.size() - 1) % buttons.size()).getId()); b.setNextFocusDownId(buttons.get((i + 1) % buttons.size()).getId()); }
        buttons.get(Math.max(0, Math.min(navigationState.section(), buttons.size() - 1))).requestFocus();
        return nav;
    }

    private void selectSection(int index) {
        navigationState.setSection(index);
        String section = navigationState.sectionId();
        if ("controles".equals(section)) { showSettingsScreen(SettingsNavigation.Screen.CONTROLS); return; }
        if ("aparencia".equals(section)) { showSettingsScreen(SettingsNavigation.Screen.APPEARANCE); return; }
        if ("biblioteca".equals(section)) { showSettingsScreen(SettingsNavigation.Screen.LIBRARY); return; }
        selectedPlatform = "favoritos".equals(section) ? "FAVORITOS" : ("android".equals(section) ? "ANDROID" : ("apps".equals(section) ? "APPS" : "TODOS"));
        buildScreen();
    }

    private void selectPlatform(int delta) {
        if (!("jogos".equals(navigationState.sectionId()) || "favoritos".equals(navigationState.sectionId()))) return;
        navigationState.movePlatform(delta); selectedPlatform = "favoritos".equals(navigationState.sectionId()) ? "FAVORITOS" : navigationState.platformId(); buildScreen();
    }

    private void showQuickActions() { new AlertDialog.Builder(this).setTitle("Ações rápidas").setItems(new String[]{"Abrir Aparência", "Abrir Controles", "Sincronizar biblioteca", "Sair para Android TV"}, (d, which) -> { if (which == 0) showSettingsScreen(SettingsNavigation.Screen.APPEARANCE); else if (which == 1) showSettingsScreen(SettingsNavigation.Screen.CONTROLS); else if (which == 2) startRemoteSync(); else finish(); }).show(); }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        boolean routedKey = key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_BUTTON_L1 || key == KeyEvent.KEYCODE_BUTTON_R1 || key == KeyEvent.KEYCODE_BUTTON_L2 || key == KeyEvent.KEYCODE_BUTTON_R2 || key == KeyEvent.KEYCODE_PAGE_UP || key == KeyEvent.KEYCODE_PAGE_DOWN;
        if (routedKey) {
            boolean accepted = controllerInputRouter.shouldMove(event.getDeviceId(), key, event.getAction(), event.getRepeatCount(), event.getEventTime());
            if (event.getAction() == KeyEvent.ACTION_DOWN && !accepted) return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && !event.isCanceled()) {
            if (key == KeyEvent.KEYCODE_BUTTON_L1) { selectSection(navigationState.section() - 1); return true; }
            if (key == KeyEvent.KEYCODE_BUTTON_R1 || key == KeyEvent.KEYCODE_PAGE_DOWN) { selectSection(navigationState.section() + 1); return true; }
            if (key == KeyEvent.KEYCODE_BUTTON_L2 || key == KeyEvent.KEYCODE_PAGE_UP) { selectPlatform(-1); return true; }
            if (key == KeyEvent.KEYCODE_BUTTON_R2) { selectPlatform(1); return true; }
            if (key == KeyEvent.KEYCODE_MENU || key == KeyEvent.KEYCODE_BUTTON_START) { showQuickActions(); return true; }
            if (key == KeyEvent.KEYCODE_BUTTON_X && activeSettingsScreen == SettingsNavigation.Screen.HOME) { showSearchEditor(); return true; }
        }
        return super.dispatchKeyEvent(event);
    }

    private View createHero() {
        final FrameLayout hero = new FrameLayout(this);
        hero.setFocusable(true);
        hero.setFocusableInTouchMode(true);
        heroView = hero;
        hero.setContentDescription("Tema atual. Pressione selecionar em Aparência para escolher outro tema.");
        hero.setBackground(panelBackground(0xFF0C1D51, 0xFF5B7DBB, scaled(2), scaled(18)));
        hero.setOnClickListener(v -> showSettingsScreen(SettingsNavigation.Screen.APPEARANCE));
        hero.setOnKeyListener((view, keyCode, event) -> event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU && openAppearance());

        heroArt = new ImageView(this);
        heroArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroArt.setBackgroundColor(0xFF0B1F4E);
        hero.addView(heroArt, new FrameLayout.LayoutParams(-1, -1));
        heroShade = new View(this);
        heroShade.setBackgroundColor(Color.argb(ThemeState.overlayAlpha(overlayOpacity), 3, 13, 42));
        hero.addView(heroShade, new FrameLayout.LayoutParams(-1, -1));

        TextView brand = heroText("TEMA ATUAL", 13, 0xFF27E5F3, true);
        FrameLayout.LayoutParams brandLp = new FrameLayout.LayoutParams(-2, scaled(30), Gravity.TOP | Gravity.LEFT); brandLp.setMargins(scaled(26), scaled(18), 0, 0); hero.addView(brand, brandLp);
        slideTitleView = heroText("", 34, Color.WHITE, true); slideTitleView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(-1, scaled(54), Gravity.CENTER);
        titleLp.setMargins(scaled(180), 0, scaled(180), scaled(18));
        hero.addView(slideTitleView, titleLp);
        slideCaptionView = heroText("", 17, 0xFFE5EEFF, false); slideCaptionView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams captionLp = new FrameLayout.LayoutParams(-1, scaled(36), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL); captionLp.setMargins(scaled(180), 0, scaled(180), scaled(28));
        hero.addView(slideCaptionView, captionLp);

        TextView personalize = heroText("APARÊNCIA", 14, Color.WHITE, true); personalize.setGravity(Gravity.CENTER); personalize.setPadding(scaled(12), 0, scaled(12), 0); personalize.setFocusable(true); personalize.setBackground(panelBackground(0xCC132E62, 0xFF85E8F7, scaled(2), scaled(12)));
        personalize.setId(View.generateViewId()); appearanceButtonView = personalize;
        personalize.setContentDescription("Aparência. Escolha tema, cores e fundo.");
        personalize.setOnClickListener(v -> showSettingsScreen(SettingsNavigation.Screen.APPEARANCE)); FrameLayout.LayoutParams personalizeLp = new FrameLayout.LayoutParams(scaled(180), scaled(42), Gravity.TOP | Gravity.RIGHT); personalizeLp.setMargins(0, scaled(18), scaled(22), 0); hero.addView(personalize, personalizeLp);
        dotsView = null; slideCounterView = null; controllerStatusView = null;
        return hero;
    }

    private boolean openAppearance() { showSettingsScreen(SettingsNavigation.Screen.APPEARANCE); return true; }

    private View createGalleryNavigation(final int restoredIndex) {
        LinearLayout nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER_VERTICAL); nav.setPadding(scaled(64), 0, scaled(64), 0); nav.setBackgroundColor(0xFF091426);
        final List<View> navButtons = new ArrayList<>();
        for (String item : new String[]{"JOGOS", "PLATAFORMAS", "FAVORITOS", "APPS", "CONTROLES", "APARÊNCIA", "BIBLIOTECA"}) {
            boolean active = ("JOGOS".equals(item) && "TODOS".equals(selectedPlatform)) || ("PLATAFORMAS".equals(item) && !"TODOS".equals(selectedPlatform) && !"APPS".equals(selectedPlatform) && !"FAVORITOS".equals(selectedPlatform)) || ("FAVORITOS".equals(item) && "FAVORITOS".equals(selectedPlatform)) || ("APPS".equals(item) && "APPS".equals(selectedPlatform));
            TextView button = heroText(item, 16, active ? 0xFF050B18 : 0xFF94A3B8, true); button.setId(View.generateViewId()); button.setGravity(Gravity.CENTER); button.setFocusable(true); button.setPadding(scaled(16), 0, scaled(16), 0);
            if (active) button.setBackground(panelBackground(0xFF27E5F3, 0xFF27E5F3, 0, scaled(11))); else button.setBackgroundColor(Color.TRANSPARENT);
            button.setOnClickListener(v -> { SettingsNavigation.Screen destination = SettingsNavigation.destination(item); if (destination != SettingsNavigation.Screen.HOME) { showSettingsScreen(destination); return; }
                if ("FAVORITOS".equals(item)) selectedPlatform = "FAVORITOS";
                else if ("APPS".equals(item)) selectedPlatform = "APPS";
                else if ("PLATAFORMAS".equals(item)) { selectedPlatform = "TODOS"; if (platformFilterView != null) { platformFilterView.requestFocus(); if (homeScroll != null) homeScroll.smoothScrollTo(0, platformFilterView.getTop()); } }
                else selectedPlatform = "TODOS";
                if (homeScroll != null && !"PLATAFORMAS".equals(item)) homeScroll.scrollTo(0, 0); renderSections(restoredIndex);
            });
            button.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN || navButtons.isEmpty()) return false;
                int index = navButtons.indexOf(v);
                if (keyCode == KeyEvent.KEYCODE_BUTTON_R1 || keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
                    navButtons.get((index + 1) % navButtons.size()).requestFocus(); return true;
                }
                if (keyCode == KeyEvent.KEYCODE_BUTTON_R2 || keyCode == KeyEvent.KEYCODE_PAGE_UP) {
                    navButtons.get((index - 1 + navButtons.size()) % navButtons.size()).requestFocus(); return true;
                }
                return false;
            });
            navButtons.add(button);
            nav.addView(button, new LinearLayout.LayoutParams(-2, scaled(44)));
        }
        for (int i = 0; i < navButtons.size(); i++) {
            View current = navButtons.get(i);
            current.setNextFocusLeftId(navButtons.get((i - 1 + navButtons.size()) % navButtons.size()).getId());
            current.setNextFocusRightId(navButtons.get((i + 1) % navButtons.size()).getId());
        }
        return nav;
    }

    private LinearLayout createLibraryControls(final int restoredIndex) {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(0, scaled(3), 0, scaled(3));
        remoteStatusView = new TextView(this);
        remoteStatusView.setText("SINCRONIZAÇÃO ONLINE: desativada · selecione para configurar");
        remoteStatusView.setTextColor(0xFFBBD2FF); remoteStatusView.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled(13));
        remoteStatusView.setGravity(Gravity.CENTER_VERTICAL); remoteStatusView.setPadding(scaled(12), 0, scaled(12), 0);
        remoteStatusView.setFocusable(true); remoteStatusView.setContentDescription("Sincronização online. Pressione selecionar para configurar.");
        remoteStatusView.setBackground(panelBackground(0x99102750, 0x6645D7EC, scaled(1), scaled(10)));
        remoteStatusView.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showRemoteSettings(); } });
        controls.addView(remoteStatusView, new LinearLayout.LayoutParams(-1, scaled(28)));
        HorizontalScrollView platformScroll = new HorizontalScrollView(this);
        platformFilterView = platformScroll;
        platformScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout platforms = new LinearLayout(this);
        platforms.setGravity(Gravity.CENTER_VERTICAL);
        for (String platform : new String[]{"TODOS", "NES", "SNES", "Mega Drive", "GBA", "PlayStation"}) platforms.addView(platformButton(platform, restoredIndex));
        platformScroll.addView(platforms);
        controls.addView(platformScroll, new LinearLayout.LayoutParams(-1, scaled(42)));
        searchView = heroText("⌕  " + (searchQuery.isEmpty() ? "BUSCAR JOGO" : searchQuery), 18, Color.WHITE, true);
        searchView.setId(View.generateViewId());
        searchView.setPadding(scaled(17), 0, scaled(17), 0);
        searchView.setFocusable(true); searchView.setFocusableInTouchMode(true);
        searchView.setBackground(panelBackground(0xCC102B5C, 0xFF45D7EC, scaled(2), scaled(13)));
        searchView.setContentDescription("Buscar jogo. Pressione selecionar para abrir o teclado na tela.");
        searchView.setOnClickListener(v -> showSearchEditor());
        searchView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A)) { showSearchEditor(); return true; }
            return false;
        });
        controls.addView(searchView, new LinearLayout.LayoutParams(-1, scaled(44)));
        return controls;
    }

    private void startRemoteSync() {
        if (remoteStatusView == null) return;
        if (cloudSync != null && cloudSync.isRunning()) return;
        String cloudOrigin = CloudApiEndpoint.configuredOrigin();
        if (!cloudOrigin.isEmpty()) {
            String deviceToken = RemoteLibrarySettings.loadCloudDeviceToken(this);
            if (deviceToken.isEmpty()) { libraryUiState = LibraryUiState.error("Pareie esta TV para sincronizar"); remoteStatusView.setText("BIBLIOTECA PRIVADA: pareie esta TV para sincronizar"); return; }
            if (cloudSync != null) cloudSync.close();
            cloudSync = new CloudLibrarySync(new CloudDeviceClient(this, cloudOrigin), StoragePaths.romRoot(), EXTERNAL_CATALOG_FILE, new File(COVER_DIRECTORY));
            libraryUiState = LibraryUiState.syncing(0, "catálogo"); remoteStatusView.setText("BIBLIOTECA PRIVADA: verificando catálogo…");
            cloudSync.start(deviceToken, 350L * 1024L * 1024L, new CloudLibrarySync.Listener() {
                @Override public void onProgress(final String id, final String label, final long done, final long total, final int remaining) { runOnUiThread(() -> { libraryUiState = LibraryUiState.syncing(total > 0 ? (int) (done * 100 / total) : 0, label); if (remoteStatusView != null) remoteStatusView.setText("BAIXANDO: " + label + "  " + libraryUiState.progress + "%  ·  " + remaining + " pendente(s)"); }); }
                @Override public void onItemInstalled(final String id, final String label) { runOnUiThread(() -> { libraryUiState = LibraryUiState.updated("agora"); if (remoteStatusView != null) remoteStatusView.setText("NOVO JOGO: " + label + "  ·  catálogo atualizado"); if (externalCatalogChanged()) buildScreen(); }); }
                @Override public void onError(final String id, final String label, final String message) { runOnUiThread(() -> { libraryUiState = LibraryUiState.error(message); if (remoteStatusView != null) remoteStatusView.setText("BIBLIOTECA PRIVADA: " + libraryUiState.message); }); }
                @Override public void onIdle(final String revision, final int pending) { runOnUiThread(() -> { libraryUiState = LibraryUiState.updated("agora"); loadRemoteThemes(); applyCloudThemeAssignment(); boolean gamesChanged = externalCatalogChanged(); if (externalAppsChanged()) readAndroidApps(); if (activeSettingsScreen == SettingsNavigation.Screen.HOME && gamesChanged) buildScreen(); if (remoteStatusView != null) remoteStatusView.setText("BIBLIOTECA PRIVADA: " + (pending > 0 ? pending + " jogo(s) disponível(is) para instalar" : "sincronização verificada")); }); }
            });
            return;
        }
        remoteStatusView.setText("BIBLIOTECA PRIVADA: serviço ainda não publicado");
    }

    private void showSettingsScreen(SettingsNavigation.Screen screen) {
        if (screen == SettingsNavigation.Screen.CONTROLS) { showControllerDashboard(); return; }
        if (screen == SettingsNavigation.Screen.APPEARANCE) { showAppearanceDashboard(); return; }
        searchEditorOpen = false; remoteSettingsOpen = false; appearanceTextEditorOpen = false; controllerTestOpen = false;
        activeSettingsScreen = screen;
        ControllerRegistry.observe(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(scaled(38), scaled(24), scaled(38), scaled(20));
        page.setBackgroundColor(Color.rgb(6, 14, 43));

        TextView back = heroText("‹  VOLTAR PARA JOGOS", 16, 0xFF8FEFFF, true);
        back.setFocusable(true); back.setGravity(Gravity.CENTER_VERTICAL); back.setPadding(scaled(14), 0, scaled(14), 0);
        back.setBackground(panelBackground(0xCC142E60, 0xFF39D8EA, scaled(2), scaled(12)));
        back.setOnClickListener(v -> buildScreen());
        page.addView(back, new LinearLayout.LayoutParams(scaled(290), scaled(48)));

        String title = screen == SettingsNavigation.Screen.CONTROLS ? "CONTROLES" : (screen == SettingsNavigation.Screen.APPEARANCE ? "APARÊNCIA" : "BIBLIOTECA ONLINE");
        String subtitle = screen == SettingsNavigation.Screen.CONTROLS ? "Dispositivos, perfis e teste de comandos" : (screen == SettingsNavigation.Screen.APPEARANCE ? "Tema, fundo e texto desta TV" : "Catálogo, sincronização e espaço local");
        TextView heading = heroText(title, 34, Color.WHITE, true); heading.setPadding(0, scaled(25), 0, 0); page.addView(heading, new LinearLayout.LayoutParams(-1, scaled(72)));
        TextView description = heroText(subtitle, 19, 0xFFC5D4F5, false); page.addView(description, new LinearLayout.LayoutParams(-1, scaled(42)));

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(0, scaled(14), 0, scaled(18));
        if (screen == SettingsNavigation.Screen.CONTROLS) populateControlsScreen(content);
        else if (screen == SettingsNavigation.Screen.APPEARANCE) populateAppearanceScreen(content);
        else populateLibraryScreen(content);
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        page.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) { buildScreen(); return true; }
            return false;
        });
        page.setFocusableInTouchMode(true);
        setContentView(page);
        back.requestFocus();
    }

    private void showControllerDashboard() {
        controllerTestOpen = false;
        activeSettingsScreen = SettingsNavigation.Screen.CONTROLS;
        ControllerRegistry.observe(this);
        final List<ControllerRegistry.DeviceInfo> devices = ControllerRegistry.devices(this);
        ControllerRegistry.DeviceInfo selected = null;
        for (ControllerRegistry.DeviceInfo device : devices) if (device.model.equals(selectedControllerModel)) { selected = device; break; }
        if (selected == null) for (ControllerRegistry.DeviceInfo device : devices) if (device.connected && device.name.toLowerCase().contains("wireless")) { selected = device; break; }
        if (selected == null) for (ControllerRegistry.DeviceInfo device : devices) if (device.connected) { selected = device; break; }
        if (selected == null && !devices.isEmpty()) selected = devices.get(0);
        if (selected != null) selectedControllerModel = selected.model;

        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFF050B18);
        root.addView(createArcadeHeader(), new LinearLayout.LayoutParams(-1, scaled(82)));
        root.addView(createArcadeNavigation("CONTROLES"), new LinearLayout.LayoutParams(-1, scaled(68)));

        LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.HORIZONTAL); main.setPadding(scaled(64), scaled(24), scaled(64), scaled(18));
        LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL); left.setPadding(0, 0, scaled(22), 0);
        TextView leftTitle = heroText("DISPOSITIVOS ENCONTRADOS", 20, Color.WHITE, true); left.addView(leftTitle, new LinearLayout.LayoutParams(-1, scaled(42)));
        ScrollView deviceScroll = new ScrollView(this); LinearLayout deviceList = new LinearLayout(this); deviceList.setOrientation(LinearLayout.VERTICAL);
        if (devices.isEmpty()) deviceList.addView(controllerListCard(null, true), new LinearLayout.LayoutParams(-1, scaled(118)));
        for (ControllerRegistry.DeviceInfo device : devices) {
            boolean isSelected = selected != null && selected.model.equals(device.model);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, scaled(118)); lp.setMargins(0, 0, 0, scaled(10));
            View card = controllerListCard(device, isSelected);
            card.setOnClickListener(v -> { selectedControllerModel = device.model; showControllerDashboard(); });
            deviceList.addView(card, lp);
        }
        deviceScroll.addView(deviceList); left.addView(deviceScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        main.addView(left, new LinearLayout.LayoutParams(scaled(540), -1));

        LinearLayout detail = createControllerDetail(selected, devices); main.addView(detail, new LinearLayout.LayoutParams(0, -1, 1f));
        root.addView(main, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(createArcadeFooter(), new LinearLayout.LayoutParams(-1, scaled(72)));
        root.setFocusableInTouchMode(true);
        root.setOnKeyListener((v, keyCode, event) -> { if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) { buildScreen(); return true; } return false; });
        setContentView(root);
        View first = deviceList.getChildAt(0); if (first != null) first.requestFocus();
    }

    private void showControllerProfilesDashboard() {
        activeSettingsScreen = SettingsNavigation.Screen.CONTROLS;
        String config = readFileText(new File(RETROARCH_CONFIG)); boolean safe = ControllerProfile.healthy(config);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFF050B18);
        root.addView(createArcadeHeader(), new LinearLayout.LayoutParams(-1, scaled(82))); root.addView(createArcadeNavigation("PERFIS"), new LinearLayout.LayoutParams(-1, scaled(68)));
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(scaled(64), scaled(34), scaled(64), scaled(26));
        body.addView(heroText("PERFIL GLOBAL DO RETROARCH", 26, Color.WHITE, true), new LinearLayout.LayoutParams(-1, scaled(46)));
        body.addView(heroText(safe ? "ATALHOS E ANALÓGICOS CONFERIDOS" : "COMPATIBILIDADE DETECTADA — PERFIL INCOMPLETO", 18, safe ? 0xFF42D392 : 0xFFF6B84A, true), new LinearLayout.LayoutParams(-1, scaled(38)));
        LinearLayout binds = new LinearLayout(this); binds.setOrientation(LinearLayout.HORIZONTAL); binds.setPadding(scaled(18), scaled(16), scaled(18), scaled(16)); binds.setBackground(panelBackground(0xFF0D1B31, 0xFF24364F, scaled(1), scaled(18)));
        String[] keys = {"input_player1_a_btn", "input_player1_b_btn", "input_player1_x_btn", "input_player1_y_btn", "input_player1_analog_dpad_mode", "input_menu_toggle_gamepad_combo", "input_quit_gamepad_combo"}; String[] labels = {"A", "B", "X", "Y", "ANALÓGICO", "MENU", "SAIR"};
        for (int i = 0; i < keys.length; i++) { LinearLayout item = new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER); item.addView(heroText(labels[i], 15, 0xFF94A3B8, true), new LinearLayout.LayoutParams(-1, scaled(34))); item.addView(heroText(configValue(config, keys[i]), 18, Color.WHITE, true), new LinearLayout.LayoutParams(-1, scaled(42))); binds.addView(item, new LinearLayout.LayoutParams(0, scaled(86), 1f)); }
        body.addView(binds, new LinearLayout.LayoutParams(-1, scaled(120)));
        body.addView(heroText("A gravação do perfil de botões é feita no RetroArch em Configurações > Entrada > Controles da porta 1 > Definir todos os controles. Este app verifica atalhos, preserva backup e mostra aqui os valores efetivamente lidos; não inventa mapeamentos para um controle que ainda não foi testado.", 17, 0xFFCBD5E1, false), new LinearLayout.LayoutParams(-1, scaled(88)));
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(controllerAction("CORRIGIR ATALHOS COM BACKUP", 0xFF42D392, 0xFF050B18, v -> { ensureControllerProfile(); boolean okay = ControllerProfile.healthy(readFileText(new File(RETROARCH_CONFIG))); Toast.makeText(this, okay ? "Perfil lido de volta e confirmado." : "Correção não confirmada; veja a permissão de armazenamento.", Toast.LENGTH_LONG).show(); showControllerProfilesDashboard(); }), new LinearLayout.LayoutParams(0, scaled(58), 1.4f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, scaled(58), 1f); lp.setMargins(scaled(12), 0, 0, 0); actions.addView(controllerAction("CONFIGURAR NO RETROARCH", 0xFF13243D, Color.WHITE, v -> showManualMappingGuide()), lp);
        LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(0, scaled(58), 1f); testLp.setMargins(scaled(12), 0, 0, 0); actions.addView(controllerAction("TESTAR INPUTS", 0xFF13243D, Color.WHITE, v -> showControllerTestScreen(false)), testLp);
        body.addView(actions, new LinearLayout.LayoutParams(-1, scaled(72)));
        List<ControllerRegistry.DeviceInfo> devices = ControllerRegistry.devices(this); StringBuilder history = new StringBuilder("CONTROLES LEMBRADOS\n"); boolean foundGamepad = false;
        for (ControllerRegistry.DeviceInfo device : devices) { history.append("• ").append(friendlyControllerName(device.name)).append(" — ").append(device.connected ? "conectado" : "desconectado").append(" · ").append(device.model).append('\n'); if (isGamepadDevice(device)) foundGamepad = true; }
        if (!foundGamepad) history.append("Nenhum controle Bluetooth de jogo está conectado agora. Faça o pareamento nas configurações do Fire TV e volte para testar os botões e analógicos.");
        TextView remembered = heroText(history.toString(), 16, 0xFFBBD2FF, false); remembered.setSingleLine(false); remembered.setMaxLines(8); remembered.setPadding(scaled(18), scaled(12), scaled(18), scaled(12)); remembered.setBackground(panelBackground(0xFF091426, 0xFF24364F, scaled(1), scaled(14))); body.addView(remembered, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(createArcadeFooter(), new LinearLayout.LayoutParams(-1, scaled(72))); root.setFocusableInTouchMode(true); root.setOnKeyListener((v, keyCode, event) -> { if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) { showControllerDashboard(); return true; } return false; }); setContentView(root); actions.getChildAt(0).requestFocus();
    }

    private String configValue(String config, String key) {
        if (config == null) return "não salvo";
        for (String line : config.split("\\r?\\n")) { String value = line.trim(); if (value.startsWith(key + " =")) return value.substring(value.indexOf('=') + 1).trim().replace("\"", ""); }
        return "não salvo";
    }

    private void showAppearanceDashboard() {
        appearanceTextEditorOpen = false;
        activeSettingsScreen = SettingsNavigation.Screen.APPEARANCE;
        ThemeCatalog.Theme current = themeCatalog.get(selectedThemeId); if (current == null) current = themeCatalog.get("arcade-moderno");
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFF050B18);
        root.addView(createArcadeHeader(), new LinearLayout.LayoutParams(-1, scaled(82)));
        root.addView(createSettingsNavigation("APARÊNCIA"), new LinearLayout.LayoutParams(-1, scaled(68)));
        LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.HORIZONTAL); main.setPadding(scaled(64), scaled(24), scaled(64), scaled(20));

        LinearLayout themes = new LinearLayout(this); themes.setOrientation(LinearLayout.VERTICAL); themes.setPadding(0, 0, scaled(24), 0);
        themes.addView(heroText("ESCOLHA UM TEMA", 21, Color.WHITE, true), new LinearLayout.LayoutParams(-1, scaled(42)));
        themes.addView(heroText("A escolha fica salva somente nesta TV.", 15, 0xFF94A3B8, false), new LinearLayout.LayoutParams(-1, scaled(34)));
        String[] ids = themeCatalog.ids();
        for (int i = 0; i < ids.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            for (int j = i; j < Math.min(i + 2, ids.length); j++) { ThemeCatalog.Theme theme = themeCatalog.get(ids[j]); boolean active = ids[j].equals(selectedThemeId); View card = themeChoiceCard(theme, active); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, scaled(142), 1f); lp.setMargins(0, 0, scaled(12), scaled(12)); row.addView(card, lp); }
            themes.addView(row, new LinearLayout.LayoutParams(-1, scaled(154)));
        }
        main.addView(themes, new LinearLayout.LayoutParams(scaled(760), -1));

        LinearLayout detail = new LinearLayout(this); detail.setOrientation(LinearLayout.VERTICAL); detail.setPadding(scaled(26), scaled(22), scaled(26), scaled(20)); detail.setBackground(panelBackground(0xFF0D1B31, 0xFF24364F, scaled(1), scaled(22)));
        detail.addView(heroText("PRÉVIA E PERSONALIZAÇÃO", 14, 0xFF94A3B8, true), new LinearLayout.LayoutParams(-1, scaled(28)));
        FrameLayout frame = new FrameLayout(this); frame.setBackgroundColor(0xFF07183A); ImageView image = new ImageView(this); image.setImageBitmap(themeArtwork(selectedThemeId)); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setAlpha(.72f); frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout center = new LinearLayout(this); center.setOrientation(LinearLayout.VERTICAL); center.setGravity(Gravity.CENTER); TextView currentTitle = heroText(themeCustomization.title, themeCustomization.textSize, parseColor(themeCustomization.textColor, Color.WHITE), true); currentTitle.setGravity(Gravity.CENTER); currentTitle.setShadowLayer(scaled(themeCustomization.shadow / 18), 0, scaled(3), 0xFF000000); TextView currentSubtitle = heroText(themeCustomization.subtitle, Math.max(15, themeCustomization.textSize / 2), parseColor(themeCustomization.textColor, 0xFFE5EEFF), true); currentSubtitle.setGravity(Gravity.CENTER); currentSubtitle.setShadowLayer(scaled(themeCustomization.shadow / 22), 0, scaled(2), 0xFF000000); center.addView(currentTitle, new LinearLayout.LayoutParams(-1, scaled(58))); center.addView(currentSubtitle, new LinearLayout.LayoutParams(-1, scaled(40))); frame.addView(center, new FrameLayout.LayoutParams(-1, -1)); detail.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView activeTheme = heroText("TEMA ATUAL  ·  " + current.name, 17, 0xFF27E5F3, true); activeTheme.setPadding(0, scaled(12), 0, 0); detail.addView(activeTheme, new LinearLayout.LayoutParams(-1, scaled(48)));
        LinearLayout actions = new LinearLayout(this); TextView edit = controllerAction("EDITAR TÍTULO E SUBTÍTULO", 0xFF27E5F3, 0xFF050B18, v -> showAppearanceTextEditor()); TextView reset = controllerAction("RESTAURAR PADRÃO", 0xFF13243D, Color.WHITE, v -> { ThemeCatalog.Theme theme = themeCatalog.get(selectedThemeId); themeCustomization = defaultThemeCustomization(theme); saveThemeCustomization(); showAppearanceDashboard(); }); actions.addView(edit, new LinearLayout.LayoutParams(0, scaled(54), 1.4f)); LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(0, scaled(54), 1f); resetLp.setMargins(scaled(12), 0, 0, 0); actions.addView(reset, resetLp); detail.addView(actions, new LinearLayout.LayoutParams(-1, scaled(62)));
        detail.addView(createAppearanceControls(), new LinearLayout.LayoutParams(-1, scaled(166)));
        main.addView(detail, new LinearLayout.LayoutParams(0, -1, 1f)); root.addView(main, new LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(createArcadeFooter(), new LinearLayout.LayoutParams(-1, scaled(72))); setContentView(root);
        View target = appearanceFocusTag.isEmpty() ? null : root.findViewWithTag(appearanceFocusTag); appearanceFocusTag = "";
        if (target != null) target.requestFocus(); else { View first = themes.getChildAt(2); if (first instanceof LinearLayout && ((LinearLayout) first).getChildCount() > 0) ((LinearLayout) first).getChildAt(0).requestFocus(); }
    }

    private LinearLayout createAppearanceControls() {
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(0, scaled(8), 0, 0);
        LinearLayout first = new LinearLayout(this); first.setGravity(Gravity.CENTER_VERTICAL);
        String[] labels = {"TEXTO −", "TEXTO +", "SOMBRA −", "SOMBRA +"}; String[] fields = {"size", "size", "shadow", "shadow"}; int[] deltas = {-4, 4, -10, 10};
        for (int i = 0; i < labels.length; i++) { final String field = fields[i], label = labels[i]; final int delta = deltas[i]; TextView button = controllerAction(label, 0xFF13243D, Color.WHITE, v -> adjustAppearance(field, delta, label)); button.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled(13)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, scaled(42), 1f); lp.setMargins(0, 0, scaled(6), 0); first.addView(button, lp); }
        panel.addView(first, new LinearLayout.LayoutParams(-1, scaled(46)));
        LinearLayout second = new LinearLayout(this); second.setGravity(Gravity.CENTER_VERTICAL);
        String[] moreLabels = {"CONTRASTE −", "CONTRASTE +", "CAPAS −", "CAPAS +"}; String[] moreFields = {"overlay", "overlay", "density", "density"}; int[] moreDeltas = {-6, 6, -1, 1};
        for (int i = 0; i < moreLabels.length; i++) { final String field = moreFields[i], label = moreLabels[i]; final int delta = moreDeltas[i]; TextView button = controllerAction(label, 0xFF13243D, Color.WHITE, v -> adjustAppearance(field, delta, label)); button.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled(13)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, scaled(42), 1f); lp.setMargins(0, 0, scaled(6), 0); second.addView(button, lp); }
        panel.addView(second, new LinearLayout.LayoutParams(-1, scaled(46)));
        LinearLayout colors = new LinearLayout(this); colors.setGravity(Gravity.CENTER_VERTICAL); colors.addView(heroText("COR", 13, 0xFF94A3B8, true), new LinearLayout.LayoutParams(scaled(50), scaled(36)));
        String[] palette = {"#FFFFFF", "#27E5F3", "#FFCF4A", "#FF70A5", "#78E08F", "#A88BFF"};
        for (String color : palette) { TextView swatch = heroText("●", 26, Color.parseColor(color), true); swatch.setGravity(Gravity.CENTER); swatch.setContentDescription("Usar cor " + color); swatch.setFocusable(true); swatch.setTag("COR " + color); swatch.setOnClickListener(v -> { appearanceFocusTag = "COR " + color; updateThemeCustomization(themeCustomization.title, themeCustomization.subtitle, color, themeCustomization.textSize, themeCustomization.shadow, themeCustomization.overlay, themeCustomization.cardDensity); }); colors.addView(swatch, new LinearLayout.LayoutParams(scaled(48), scaled(38))); }
        panel.addView(colors, new LinearLayout.LayoutParams(-1, scaled(40))); return panel;
    }

    private void adjustAppearance(String field, int delta, String label) {
        int size = themeCustomization.textSize, shadow = themeCustomization.shadow, overlay = themeCustomization.overlay, density = themeCustomization.cardDensity;
        if ("size".equals(field)) size += delta; else if ("shadow".equals(field)) shadow += delta; else if ("overlay".equals(field)) overlay += delta; else if ("density".equals(field)) density += delta;
        appearanceFocusTag = label; updateThemeCustomization(themeCustomization.title, themeCustomization.subtitle, themeCustomization.textColor, size, shadow, overlay, density);
    }

    private void updateThemeCustomization(String title, String subtitle, String color, int size, int shadow, int overlay, int density) {
        themeCustomization = new ThemeCustomization(title, subtitle, color, size, shadow, overlay, density); saveThemeCustomization(); showAppearanceDashboard();
    }

    private Bitmap themeArtwork(String themeId) {
        Bitmap cached = themeArtworkCache.get(themeId); if (cached != null) return cached;
        Bitmap image;
        ThemeCatalog.Theme remote = themeCatalog.get(themeId);
        File remoteArtwork = remote == null || remote.background.isEmpty() ? null : new File(COVER_DIRECTORY, new File(remote.background).getName());
        if (remoteArtwork != null && remoteArtwork.isFile()) image = BitmapFactory.decodeFile(remoteArtwork.getAbsolutePath());
        else if ("kalel-kath".equals(themeId)) image = BitmapFactory.decodeResource(getResources(), R.drawable.fireretro_banner);
        else if ("aventura".equals(themeId)) image = loadBundledThemeArtwork("aventura.png");
        else if ("corrida".equals(themeId)) image = loadBundledThemeArtwork("corrida.png");
        else if ("pixel-space".equals(themeId)) image = generatedThemeArtwork(0xFF11103D, 0xFF6039AD, 0xFFA88BFF);
        else if ("minimalista".equals(themeId)) image = generatedThemeArtwork(0xFF101820, 0xFF253447, 0xFFFFFFFF);
        else if (remote != null && !"arcade-moderno".equals(themeId)) image = generatedThemeArtwork(parseColor(remote.backgroundColor, 0xFF102447), parseColor(remote.accent, 0xFF38D9FF), parseColor(remote.accent, 0xFF38D9FF));
        else image = BitmapFactory.decodeResource(getResources(), R.drawable.jogos_retro_banner);
        if (image == null) image = BitmapFactory.decodeResource(getResources(), R.drawable.jogos_retro_banner);
        themeArtworkCache.put(themeId, image); return image;
    }

    private Bitmap loadBundledThemeArtwork(String name) {
        try (InputStream stream = getAssets().open("slides/" + name)) { return BitmapFactory.decodeStream(stream); }
        catch (Exception ignored) { return null; }
    }

    private Bitmap generatedThemeArtwork(int start, int end, int accent) {
        Bitmap bitmap = Bitmap.createBitmap(1200, 420, Bitmap.Config.ARGB_8888); Canvas canvas = new Canvas(bitmap); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, 1200, 420, start, end, Shader.TileMode.CLAMP)); canvas.drawRect(0, 0, 1200, 420, paint); paint.setShader(null);
        for (int i = 0; i < 34; i++) { int x = (i * 197 + 71) % 1200, y = (i * 89 + 29) % 420; int size = i % 4 == 0 ? 7 : 3; paint.setColor(i % 3 == 0 ? accent : 0xAAFFFFFF); canvas.drawRect(x, y, x + size, y + size, paint); }
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(10); paint.setColor(accent); canvas.drawRoundRect(50, 45, 480, 375, 34, 34, paint); canvas.drawCircle(955, 210, 138, paint); paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER); paint.setFakeBoldText(true); paint.setTextSize(80); paint.setColor(0xEEFFFFFF); canvas.drawText("JOGOS RETRO", 625, 230, paint); return bitmap;
    }

    private View createSettingsNavigation(String active) {
        LinearLayout nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER_VERTICAL); nav.setPadding(scaled(64), 0, scaled(64), 0); nav.setBackgroundColor(0xFF091426);
        for (String item : new String[]{"JOGOS", "CONTROLES", "APARÊNCIA", "BIBLIOTECA"}) { TextView button = heroText(item, 17, item.equals(active) ? 0xFF050B18 : 0xFF94A3B8, true); button.setGravity(Gravity.CENTER); button.setFocusable(true); button.setPadding(scaled(18), 0, scaled(18), 0); if (item.equals(active)) button.setBackground(panelBackground(0xFF27E5F3, 0xFF27E5F3, 0, scaled(11))); else button.setBackgroundColor(Color.TRANSPARENT); button.setOnClickListener(v -> { SettingsNavigation.Screen destination = SettingsNavigation.destination(item); if (destination == SettingsNavigation.Screen.HOME) buildScreen(); else showSettingsScreen(destination); }); nav.addView(button, new LinearLayout.LayoutParams(-2, scaled(44))); }
        return nav;
    }

    private View themeChoiceCard(ThemeCatalog.Theme theme, boolean active) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(scaled(12), scaled(10), scaled(12), scaled(10)); card.setFocusable(true); card.setFocusableInTouchMode(true); int accent = parseColor(theme.accent, 0xFF27E5F3); card.setBackground(panelBackground(active ? 0xFF173552 : 0xFF0D1B31, active ? accent : 0xFF24364F, scaled(2), scaled(15)));
        ImageView artwork = new ImageView(this); artwork.setImageBitmap(themeArtwork(theme.id)); artwork.setScaleType(ImageView.ScaleType.CENTER_CROP); LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(scaled(126), scaled(104)); imageLp.setMargins(0, 0, scaled(14), 0); card.addView(artwork, imageLp);
        LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL); labels.setGravity(Gravity.CENTER_VERTICAL); labels.addView(heroText(theme.name.toUpperCase(), 17, Color.WHITE, true), new LinearLayout.LayoutParams(-1, scaled(38))); labels.addView(heroText(active ? "✓  Em uso nesta TV" : "A  Aplicar tema", 14, active ? 0xFF42D392 : 0xFF94A3B8, true), new LinearLayout.LayoutParams(-1, scaled(28))); card.addView(labels, new LinearLayout.LayoutParams(0, -1, 1f));
        card.setOnClickListener(v -> { selectedThemeId = theme.id; getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(SELECTED_THEME, selectedThemeId).apply(); themeCustomization = defaultThemeCustomization(theme); saveThemeCustomization(); showAppearanceDashboard(); }); card.setOnFocusChangeListener((v, focused) -> v.setBackground(panelBackground(focused ? 0xFF173552 : (active ? 0xFF173552 : 0xFF0D1B31), focused ? 0xFF27E5F3 : (active ? accent : 0xFF24364F), scaled(2), scaled(15)))); return card;
    }

    private View createArcadeHeader() {
        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(scaled(64), 0, scaled(64), 0); header.setBackgroundColor(0xFF091426);
        TextView mark = heroText("🎮", 28, 0xFF050B18, true); mark.setGravity(Gravity.CENTER); mark.setBackground(panelBackground(0xFF27E5F3, 0xFF27E5F3, 0, scaled(12))); header.addView(mark, new LinearLayout.LayoutParams(scaled(50), scaled(50)));
        LinearLayout brand = new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL); brand.setPadding(scaled(18), scaled(7), 0, 0);
        brand.addView(heroText("ARCADE CASA", 24, Color.WHITE, true), new LinearLayout.LayoutParams(-2, scaled(30)));
        brand.addView(heroText("CENTRAL DA FAMÍLIA", 12, 0xFF27E5F3, true), new LinearLayout.LayoutParams(-2, scaled(24)));
        header.addView(brand, new LinearLayout.LayoutParams(0, -1, 1f));
        boolean onlineNow = false; try { ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE); NetworkInfo network = manager == null ? null : manager.getActiveNetworkInfo(); onlineNow = network != null && network.isConnected(); } catch (Exception ignored) { }
        String clock = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date());
        TextView online = heroText((onlineNow ? "●  REDE CONECTADA" : "●  OFFLINE") + "     " + clock, 16, Color.WHITE, true); online.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT); header.addView(online, new LinearLayout.LayoutParams(scaled(350), -1));
        return header;
    }

    private View createArcadeNavigation(String active) {
        LinearLayout nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER_VERTICAL); nav.setPadding(scaled(64), 0, scaled(64), 0); nav.setBackgroundColor(0xFF091426);
        for (String item : new String[]{"INÍCIO", "JOGOS", "CONTROLES", "PERFIS", "SISTEMA"}) {
            TextView button = heroText(item, 17, item.equals(active) ? 0xFF050B18 : 0xFF94A3B8, true); button.setGravity(Gravity.CENTER); button.setFocusable(true); button.setPadding(scaled(18), 0, scaled(18), 0);
            if (item.equals(active)) button.setBackground(panelBackground(0xFF27E5F3, 0xFF27E5F3, 0, scaled(11))); else button.setBackgroundColor(Color.TRANSPARENT);
            if ("INÍCIO".equals(item) || "JOGOS".equals(item)) button.setOnClickListener(v -> buildScreen());
            else if ("CONTROLES".equals(item)) button.setOnClickListener(v -> showControllerDashboard());
            else if ("PERFIS".equals(item)) button.setOnClickListener(v -> showControllerProfilesDashboard());
            else if ("SISTEMA".equals(item)) button.setOnClickListener(v -> showSettingsScreen(SettingsNavigation.Screen.LIBRARY));
            nav.addView(button, new LinearLayout.LayoutParams(-2, scaled(44)));
        }
        return nav;
    }

    private View controllerListCard(ControllerRegistry.DeviceInfo device, boolean selected) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(scaled(18), scaled(14), scaled(18), scaled(12)); card.setFocusable(true); card.setFocusableInTouchMode(true);
        int border = selected ? 0xFF27E5F3 : 0xFF24364F; card.setBackground(panelBackground(selected ? 0xFF13243D : 0xFF0D1B31, border, scaled(2), scaled(16)));
        String name = device == null ? "Nenhum controle" : friendlyControllerName(device.name);
        String state = device == null ? "Conecte um controle Bluetooth" : (device.connected ? "●  Conectado" : "●  Desconectado");
        card.addView(heroText(name, 21, device != null && device.connected ? Color.WHITE : 0xFFCBD5E1, true), new LinearLayout.LayoutParams(-1, scaled(38)));
        TextView status = heroText(state, 15, device != null && device.connected ? 0xFF42D392 : 0xFF94A3B8, true); card.addView(status, new LinearLayout.LayoutParams(-1, scaled(28)));
        String lowerName = device == null || device.name == null ? "" : device.name.toLowerCase();
        String meta = lowerName.contains("wireless") ? (device.connected ? "Controle de jogo · verifique os binds no RetroArch" : "Reconecte para testar e configurar no RetroArch") : (lowerName.contains("amzkeyboard") ? "Entrada de texto do Fire OS" : "Somente navegação do sistema");
        card.addView(heroText(meta, 14, 0xFF94A3B8, false), new LinearLayout.LayoutParams(-1, scaled(27)));
        card.setOnFocusChangeListener((v, focused) -> v.setBackground(panelBackground(focused ? 0xFF173552 : (selected ? 0xFF13243D : 0xFF0D1B31), focused ? 0xFF27E5F3 : border, scaled(2), scaled(16))));
        return card;
    }

    private LinearLayout createControllerDetail(ControllerRegistry.DeviceInfo selected, List<ControllerRegistry.DeviceInfo> devices) {
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(scaled(26), scaled(22), scaled(26), scaled(20)); panel.setBackground(panelBackground(0xFF0D1B31, 0xFF24364F, scaled(1), scaled(22)));
        panel.addView(heroText("CONTROLE SELECIONADO", 14, 0xFF94A3B8, true), new LinearLayout.LayoutParams(-1, scaled(28)));
        panel.addView(heroText(selected == null ? "Nenhum controle" : friendlyControllerName(selected.name), 31, Color.WHITE, true), new LinearLayout.LayoutParams(-1, scaled(48)));
        final boolean gamepad = isGamepadDevice(selected);
        String retroConfig = readFileText(new File(RETROARCH_CONFIG)); boolean safeProfile = ControllerProfile.healthy(retroConfig);
        TextView tags = heroText((gamepad ? (safeProfile ? "✓  Atalhos de saída ativos" : "⚠  Atalhos precisam de correção") + "       Analógicos para direcional: " + (retroConfig.contains("input_player1_analog_dpad_mode = \"3\"") ? "ativo" : "inativo") : "Este dispositivo navega no Fire TV; ele não é um controle de jogo."), 17, gamepad && safeProfile ? 0xFF42D392 : 0xFFF6B84A, true); panel.addView(tags, new LinearLayout.LayoutParams(-1, scaled(48)));

        LinearLayout middle = new LinearLayout(this); middle.setOrientation(LinearLayout.HORIZONTAL); middle.setPadding(0, scaled(8), 0, 0);
        LinearLayout map = new LinearLayout(this); map.setOrientation(LinearLayout.VERTICAL); map.setPadding(scaled(18), scaled(14), scaled(18), scaled(14)); map.setBackground(panelBackground(0xFF091426, 0xFF24364F, scaled(1), scaled(15)));
        map.addView(heroText("MAPA RÁPIDO     Perfil Geral", 18, Color.WHITE, true), new LinearLayout.LayoutParams(-1, scaled(36)));
        String mapDescription = gamepad ? "MAPEAMENTO DO RETROARCH\n\nA  Confirmar      B  Voltar\n\nX  Opção           Y  Favorito\n\nD-pad / analógicos\n\nMenu + voltar: sair do jogo" : "CONTROLE DO SISTEMA\n\nD-pad: navegar na TV\nSelecionar: abrir item\nVoltar: tela anterior\n\nEste dispositivo não envia botões de jogo ao RetroArch.";
        TextView mapping = heroText(mapDescription, 17, 0xFFE2E8F0, true); mapping.setSingleLine(false); mapping.setMaxLines(8); mapping.setGravity(Gravity.TOP); mapping.setPadding(0, scaled(12), 0, 0); map.addView(mapping, new LinearLayout.LayoutParams(-1, 0, 1f));
        middle.addView(map, new LinearLayout.LayoutParams(0, -1, 1.05f));
        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.VERTICAL); actions.setPadding(scaled(14), 0, 0, 0);
        actions.addView(controllerAction("TESTAR BOTÕES E ANALÓGICOS", 0xFF27E5F3, 0xFF050B18, v -> showControllerTestScreen(false)));
        TextView apply = controllerAction("APLICAR PERFIL", gamepad ? 0xFF42D392 : 0xFF111827, gamepad ? 0xFF050B18 : 0xFF64748B, v -> { if (!gamepad) { Toast.makeText(this, "Pareie primeiro um controle de jogo Bluetooth.", Toast.LENGTH_LONG).show(); return; } ensureControllerProfile(); Toast.makeText(this, ControllerProfile.healthy(readFileText(new File(RETROARCH_CONFIG))) ? "Atalhos conferidos e perfil salvo com backup." : "Não foi possível confirmar a correção. Veja o estado abaixo.", Toast.LENGTH_LONG).show(); showControllerDashboard(); }); apply.setEnabled(gamepad); actions.addView(apply);
        boolean canCopy = false; if (selected != null) for (ControllerRegistry.DeviceInfo other : devices) if (other != selected && ControllerProfile.canCopy(selected.model, other.model)) canCopy = true;
        TextView copy = controllerAction("COPIAR PERFIL", canCopy ? 0xFF13243D : 0xFF111827, canCopy ? Color.WHITE : 0xFF64748B, v -> Toast.makeText(this, "Selecione outro controle do mesmo modelo.", Toast.LENGTH_LONG).show()); copy.setEnabled(canCopy); actions.addView(copy);
        actions.addView(controllerAction("CONFIGURAR MANUALMENTE", 0xFF13243D, Color.WHITE, v -> showManualMappingGuide()));
        actions.addView(controllerAction("CORRIGIR RETROARCH", 0xFF13243D, Color.WHITE, v -> { ensureControllerProfile(); Toast.makeText(this, ControllerProfile.healthy(readFileText(new File(RETROARCH_CONFIG))) ? "Atalhos do RetroArch corrigidos e lidos de volta." : "Correção não confirmada. Abra o RetroArch uma vez e tente novamente.", Toast.LENGTH_LONG).show(); showControllerDashboard(); }));
        middle.addView(actions, new LinearLayout.LayoutParams(0, -1, .95f));
        panel.addView(middle, new LinearLayout.LayoutParams(-1, 0, 1f));

        String config = readFileText(new File(RETROARCH_CONFIG));
        boolean healthy = ControllerProfile.healthy(config);
        TextView warning = heroText(healthy ? "✓  ATALHOS DO RETROARCH CONFERIDOS" : "⚠  ATALHOS DO RETROARCH — CORRIGIR AGORA", 17, healthy ? 0xFF42D392 : 0xFFF6B84A, true); warning.setGravity(Gravity.CENTER_VERTICAL); warning.setPadding(scaled(18), 0, scaled(18), 0); warning.setFocusable(!healthy); warning.setBackground(panelBackground(healthy ? 0x2233CC88 : 0x22F6B84A, healthy ? 0x5542D392 : 0x88F6B84A, scaled(1), scaled(14))); if (!healthy) warning.setOnClickListener(v -> { ensureControllerProfile(); showControllerDashboard(); }); panel.addView(warning, new LinearLayout.LayoutParams(-1, scaled(58)));
        return panel;
    }

    private TextView controllerAction(String label, int background, int foreground, View.OnClickListener click) {
        TextView button = heroText(label, 15, foreground, true); button.setGravity(Gravity.CENTER); button.setFocusable(true); button.setFocusableInTouchMode(true); button.setBackground(panelBackground(background, background, 0, scaled(11))); button.setOnClickListener(click); button.setOnFocusChangeListener((v, focused) -> v.setBackground(panelBackground(focused ? 0xFF27E5F3 : background, focused ? 0xFF27E5F3 : background, 0, scaled(11)))); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, scaled(50)); lp.setMargins(0, 0, 0, scaled(9)); button.setLayoutParams(lp); return button;
    }

    private View createArcadeFooter() {
        LinearLayout footer = new LinearLayout(this); footer.setGravity(Gravity.CENTER_VERTICAL); footer.setPadding(scaled(64), 0, scaled(64), 0); footer.setBackgroundColor(0xFF091426);
        footer.addView(heroText("↕↔  Navegar    R1  Próxima aba    R2  Aba anterior    A  Selecionar    B  Voltar", 17, 0xFFCBD5E1, true), new LinearLayout.LayoutParams(0, -1, 1f));
        TextView system = heroText("●  Sistema pronto", 15, 0xFF42D392, true); system.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL); footer.addView(system, new LinearLayout.LayoutParams(scaled(240), -1)); return footer;
    }

    private String friendlyControllerName(String name) {
        String lower = name == null ? "" : name.toLowerCase();
        if (lower.contains("wireless")) return "Controle sem fio";
        if (lower.contains("amzkeyboard")) return "Teclado do Fire TV";
        if (lower.contains("woble") || lower.contains("amz")) return "Controle remoto Fire TV";
        if (lower.contains("hdmi")) return "Controle HDMI-CEC";
        return name == null || name.trim().isEmpty() ? "Controle sem nome" : name;
    }

    private boolean isGamepadDevice(ControllerRegistry.DeviceInfo device) {
        if (device == null || !device.connected) return false;
        String name = ((device.name == null ? "" : device.name) + " " + (device.model == null ? "" : device.model)).toLowerCase();
        return !(name.contains("remote") || name.contains("amazon") || name.contains("fire-tv") || name.contains("fire tv") || name.contains("amzkeyboard") || name.contains("keyboard") || name.contains("hdmi"));
    }

    private String controllerDisplayNameForModel(String model) {
        for (ControllerRegistry.DeviceInfo device : ControllerRegistry.devices(this)) if (device.model.equals(model)) return friendlyControllerName(device.name);
        return model == null || model.isEmpty() ? "Nenhum controle" : model;
    }

    private void populateControlsScreen(LinearLayout content) {
        List<ControllerRegistry.DeviceInfo> devices = ControllerRegistry.devices(this);
        if (devices.isEmpty()) addSettingsCard(content, "NENHUM CONTROLE DETECTADO", "Pareie um controle Bluetooth nas configurações do Fire TV e volte a esta tela.", "ATUALIZAR", v -> { ControllerRegistry.observe(this); showSettingsScreen(SettingsNavigation.Screen.CONTROLS); });
        for (ControllerRegistry.DeviceInfo device : devices) addControllerDeviceCard(content, device, devices);
        String config = readFileText(new File(RETROARCH_CONFIG));
        boolean profileHealthy = ControllerProfile.healthy(config);
        addSettingsCard(content, "PERFIL GERAL", profileHealthy ? "Configurado: analógico como direcional, menu e saída rápida ativos." : "Compatibilidade detectada. O perfil precisa ser reaplicado.", profileHealthy ? "REAPLICAR PERFIL" : "CORRIGIR AGORA", v -> { ensureControllerProfile(); showSettingsScreen(SettingsNavigation.Screen.CONTROLS); });
        addSettingsCard(content, "TESTE DE COMANDOS", "Botões, códigos recebidos e eixos dos dois analógicos serão mostrados em tela cheia.", "INICIAR TESTE", v -> showControllerTestScreen(false));
        addSettingsCard(content, "RETROARCH", "Antes de abrir um jogo, o launcher confere os atalhos essenciais. Um backup da configuração é criado antes de qualquer correção.", "ABRIR RETROARCH", v -> { Intent intent = getPackageManager().getLaunchIntentForPackage(RETROARCH); if (intent != null) startActivity(intent); else Toast.makeText(this, "RetroArch não encontrado.", Toast.LENGTH_LONG).show(); });
    }

    private void addControllerDeviceCard(LinearLayout content, ControllerRegistry.DeviceInfo device, List<ControllerRegistry.DeviceInfo> allDevices) {
        boolean compatible = false;
        for (ControllerRegistry.DeviceInfo other : allDevices) if (other != device && ControllerProfile.canCopy(other.model, device.model)) compatible = true;
        final boolean profileCompatible = compatible;
        String state = device.connected ? "CONECTADO" : "DESCONECTADO";
        boolean safeProfile = ControllerProfile.healthy(readFileText(new File(RETROARCH_CONFIG)));
        String details = state + (safeProfile ? " · Atalhos essenciais ativos" : " · Atalhos precisam de correção") + "\nO perfil de botões deve ser conferido no RetroArch.\nModelo: " + device.model;
        String action = device.connected ? (profileCompatible ? "COPIAR PERFIL COMPATÍVEL" : "TESTAR ESTE CONTROLE") : "AGUARDAR RECONEXÃO";
        addSettingsCard(content, device.name.toUpperCase(), details, action, v -> {
            if (!device.connected) { Toast.makeText(this, "Reconecte este controle para aplicar ou testar o perfil.", Toast.LENGTH_LONG).show(); return; }
            if (profileCompatible) Toast.makeText(this, "Perfil compatível disponível para este modelo.", Toast.LENGTH_LONG).show(); else beginControllerTest(content);
        });
    }

    private void beginControllerTest(LinearLayout content) {
        TextView result = heroText("AGUARDANDO COMANDO…", 20, 0xFF8FFFE0, true);
        result.setGravity(Gravity.CENTER); result.setFocusable(true); result.setFocusableInTouchMode(true);
        result.setBackground(panelBackground(0xCC102B5C, 0xFF68F5C0, scaled(2), scaled(14)));
        result.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) { result.setText("RECEBIDO: " + KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", "")); return true; }
            return false;
        });
        content.addView(result, new LinearLayout.LayoutParams(-1, scaled(82)));
        result.requestFocus();
    }

    private void showControllerTestScreen(final boolean mappingMode) {
        searchEditorOpen = false; remoteSettingsOpen = false; appearanceTextEditorOpen = false; controllerTestOpen = true;
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFF050B18);
        root.addView(createArcadeHeader(), new LinearLayout.LayoutParams(-1, scaled(82)));
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL); heading.setPadding(scaled(64), 0, scaled(64), 0); heading.setBackgroundColor(0xFF091426);
        TextView back = heroText("‹  CONTROLES", 17, 0xFF27E5F3, true); back.setFocusable(true); back.setOnClickListener(v -> showControllerDashboard()); heading.addView(back, new LinearLayout.LayoutParams(scaled(300), -1));
        heading.addView(heroText(mappingMode ? "CONFIGURAR CONTROLE" : "TESTAR CONTROLE", 23, Color.WHITE, true), new LinearLayout.LayoutParams(0, -1, 1f)); root.addView(heading, new LinearLayout.LayoutParams(-1, scaled(68)));
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setGravity(Gravity.CENTER); body.setPadding(scaled(100), scaled(30), scaled(100), scaled(28));
        TextView model = heroText("Dispositivo: " + controllerDisplayNameForModel(selectedControllerModel) + "\n\nPressione os botões e mova os dois analógicos. Os comandos recebidos aparecerão abaixo.", 20, 0xFFCAD7E8, true); model.setSingleLine(false); model.setMaxLines(3); model.setGravity(Gravity.CENTER); body.addView(model, new LinearLayout.LayoutParams(-1, scaled(150)));
        TextView received = heroText("AGUARDANDO ENTRADA DO CONTROLE…", 34, 0xFF27E5F3, true); received.setGravity(Gravity.CENTER); received.setFocusable(true); received.setFocusableInTouchMode(true); received.setBackground(panelBackground(0xFF102448, 0xFF27E5F3, scaled(2), scaled(20))); body.addView(received, new LinearLayout.LayoutParams(-1, 0, 1f));
        received.setOnKeyListener((v, keyCode, event) -> { if (event.getAction() != KeyEvent.ACTION_DOWN) return true; received.setText("BOTÃO: " + KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", "") + "\nCódigo Android: " + keyCode + (event.getScanCode() > 0 ? " · scan " + event.getScanCode() : "")); return true; });
        received.setOnGenericMotionListener((v, event) -> { if ((event.getSource() & (InputDevice.SOURCE_JOYSTICK | InputDevice.SOURCE_GAMEPAD)) == 0) return false; String axes = String.format(java.util.Locale.ROOT, "ANALÓGICOS\nEsquerdo: X %.2f · Y %.2f\nDireito: X %.2f · Y %.2f", event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y), event.getAxisValue(MotionEvent.AXIS_Z), event.getAxisValue(MotionEvent.AXIS_RZ)); received.setText(axes); return true; });
        TextView finish = controllerAction("VOLTAR AOS CONTROLES", 0xFF13243D, Color.WHITE, v -> showControllerDashboard()); body.addView(finish, new LinearLayout.LayoutParams(-1, scaled(60)));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(createArcadeFooter(), new LinearLayout.LayoutParams(-1, scaled(72))); root.setFocusableInTouchMode(true); root.setOnKeyListener((v, keyCode, event) -> { if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) { showControllerDashboard(); return true; } return false; }); setContentView(root); received.requestFocus();
    }

    private void showManualMappingGuide() {
        new AlertDialog.Builder(this).setTitle("Mapeamento do RetroArch")
                .setMessage("O RetroArch guarda o mapeamento de botões por controle. Para configurar: abra Configurações > Entrada > Controles da porta 1 > Definir todos os controles. Salve o perfil do controle no RetroArch.\n\nDepois volte aqui e use ‘Testar botões e analógicos’ para conferir o que o Fire TV envia. O botão ‘Corrigir RetroArch’ restaura apenas os atalhos de menu/saída e o direcional analógico, preservando o restante da configuração.")
                .setPositiveButton("ABRIR RETROARCH", (dialog, which) -> { Intent intent = getPackageManager().getLaunchIntentForPackage(RETROARCH); if (intent != null) startActivity(intent); else Toast.makeText(this, "RetroArch não encontrado.", Toast.LENGTH_LONG).show(); })
                .setNegativeButton("VOLTAR", null).show();
    }

    private void populateAppearanceScreen(LinearLayout content) {
        ThemeCatalog.Theme current = themeCatalog.get(selectedThemeId);
        addSettingsCard(content, "TEMA ATUAL", current == null ? "Arcade moderno" : current.name, "ESCOLHER TEMA", v -> showThemeChooser());
        addSettingsCard(content, "TÍTULO CENTRAL", themeCustomization.title + "\n" + themeCustomization.subtitle, "EDITAR TEXTO", v -> showAppearanceTextEditor());
        addSettingsCard(content, "FUNDO E CONTRASTE", "O tema controla a arte, as cores e o contraste. As escolhas ficam salvas só nesta TV.", "RESTAURAR PADRÃO", v -> { ThemeCatalog.Theme theme = themeCatalog.get(selectedThemeId); themeCustomization = defaultThemeCustomization(theme); saveThemeCustomization(); showSettingsScreen(SettingsNavigation.Screen.APPEARANCE); });
    }

    private void populateLibraryScreen(LinearLayout content) {
        String cloudOrigin = CloudApiEndpoint.configuredOrigin();
        String deviceToken = RemoteLibrarySettings.loadCloudDeviceToken(this);
        String status = cloudOrigin.isEmpty() ? "Biblioteca privada ainda não publicada. Os jogos locais continuam disponíveis." : deviceToken.isEmpty() ? "Esta TV ainda não foi pareada com a biblioteca privada." : "TV pareada. A biblioteca sincroniza ao abrir Jogos Retro.";
        addSettingsCard(content, "BIBLIOTECA PRIVADA", status, cloudOrigin.isEmpty() ? "DETALHES" : deviceToken.isEmpty() ? "PAREAR TV" : "SINCRONIZAR AGORA", v -> { if (!cloudOrigin.isEmpty() && !deviceToken.isEmpty()) { buildScreen(); startRemoteSync(); } else showRemoteSettings(); });
        addSettingsCard(content, "JOGOS LOCAIS", allGames.size() + " jogos no catálogo atual. Nenhum save ou ROM existente será removido.", "ATUALIZAR CATÁLOGO", v -> { buildScreen(); Toast.makeText(this, "Catálogo local atualizado.", Toast.LENGTH_SHORT).show(); });
        addSettingsCard(content, "ARMAZENAMENTO", "Os downloads são verificados antes de iniciar e nunca substituem jogos, saves ou configurações existentes.", "VER DOWNLOADS", v -> Toast.makeText(this, "Não há download em andamento.", Toast.LENGTH_SHORT).show());
        JSONObject release = readLauncherUpdate();
        if (release != null) {
            try {
                long current = getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
                long available = release.optLong("versionCode", 0);
                if (available > current) addSettingsCard(content, "ATUALIZAÇÃO DO JOGOS RETRO", "Versão " + release.optString("version", String.valueOf(available)) + " disponível\n" + release.optString("notes", ""), "BAIXAR E ATUALIZAR", v -> installLauncherUpdate(release));
            } catch (Exception ignored) { }
        }
    }

    private JSONObject readLauncherUpdate() {
        File file = new File(EXTERNAL_CATALOG_FILE.getParentFile(), "launcher-update.json");
        if (!file.isFile()) return null;
        try { return new JSONObject(readFileText(file)); } catch (Exception ignored) { return null; }
    }

    private void installLauncherUpdate(final JSONObject release) {
        final String token = RemoteLibrarySettings.loadCloudDeviceToken(this);
        if (token.isEmpty()) { showRemoteSettings(); return; }
        final int minApi = release.optInt("minApi", 28);
        JSONArray abis = release.optJSONArray("abis"); boolean abiSupported = abis == null || abis.length() == 0;
        if (abis != null) for (int i = 0; i < abis.length(); i++) for (String supported : Build.SUPPORTED_ABIS) if (supported.equals(abis.optString(i))) abiSupported = true;
        if (Build.VERSION.SDK_INT < minApi || !abiSupported) { Toast.makeText(this, "Esta atualização não é compatível com o Android ou processador deste aparelho.", Toast.LENGTH_LONG).show(); return; }
        final long targetCode = release.optLong("versionCode", 0);
        String notes = release.optString("notes", "Correções e melhorias");
        new AlertDialog.Builder(this).setTitle("Atualizar Jogos Retro?")
                .setMessage("Versão " + release.optString("version", String.valueOf(targetCode)) + "\n\n" + notes + "\n\nO Android pedirá confirmação antes de instalar. Seus jogos e configurações serão mantidos.")
                .setPositiveButton("BAIXAR ATUALIZAÇÃO", (dialog, which) -> downloadLauncherUpdate(release, token, targetCode))
                .setNegativeButton("AGORA NÃO", null).show();
    }

    private void downloadLauncherUpdate(final JSONObject release, final String token, final long targetCode) {
        if (remoteStatusView != null) remoteStatusView.setText("BAIXANDO ATUALIZAÇÃO DO JOGOS RETRO…");
        new Thread(() -> {
            try {
                JSONObject ticket = new CloudDeviceClient(this, CloudApiEndpoint.configuredOrigin()).downloadTicket(token, release.optString("id", ""));
                AndroidAppInstaller.download(this, ticket.optString("url", ""), "", ticket.optLong("size", -1), ticket.optString("sha256", ""), new AndroidAppInstaller.Callback() {
                    @Override public void onProgress(long done, long total) { if (remoteStatusView != null) remoteStatusView.setText("BAIXANDO ATUALIZAÇÃO: " + (total > 0 ? done * 100 / total : 0) + "%"); }
                    @Override public void onReady(File apk) {
                        try {
                            String packageName = release.optString("packageName", "");
                            String certificate = release.optString("certificateSha256", "");
                            AndroidAppInstaller.verifyPrivateApk(MainActivity.this, apk, packageName, certificate);
                            if (!AndroidAppInstaller.installedSignerMatches(MainActivity.this, certificate)) throw new Exception("O certificado da instalação atual não corresponde ao certificado confiável");
                            if (AndroidAppInstaller.archiveVersionCode(MainActivity.this, apk) != targetCode || targetCode <= getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode()) throw new Exception("A versão do APK não corresponde à versão publicada");
                            AndroidAppInstaller.install(MainActivity.this, apk);
                        } catch (Exception error) { Toast.makeText(MainActivity.this, "Atualização rejeitada: " + error.getMessage(), Toast.LENGTH_LONG).show(); }
                    }
                    @Override public void onError(String message) { if (remoteStatusView != null) remoteStatusView.setText("ATUALIZAÇÃO: " + message); }
                });
            } catch (Exception error) { runOnUiThread(() -> { if (remoteStatusView != null) remoteStatusView.setText("ATUALIZAÇÃO: " + (error.getMessage() == null ? "falha de download" : error.getMessage())); }); }
        }, "fireretro-launcher-update").start();
    }

    private void addSettingsCard(LinearLayout content, String title, String body, String action, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(scaled(22), scaled(18), scaled(22), scaled(18)); card.setBackground(panelBackground(0xCC102750, 0xFF45D7EC, scaled(2), scaled(16)));
        TextView heading = heroText(title, 18, 0xFF8FEFFF, true); card.addView(heading, new LinearLayout.LayoutParams(-1, scaled(32)));
        TextView text = heroText(body, 17, Color.WHITE, false); text.setGravity(Gravity.CENTER_VERTICAL); text.setMaxLines(4); text.setEllipsize(TextUtils.TruncateAt.END); card.addView(text, new LinearLayout.LayoutParams(-1, scaled(88)));
        TextView button = heroText(action, 16, Color.WHITE, true); button.setGravity(Gravity.CENTER); button.setFocusable(true); button.setFocusableInTouchMode(true); button.setBackground(panelBackground(0xCC17477C, 0xFF8FEFFF, scaled(2), scaled(11))); button.setOnClickListener(listener); button.setOnFocusChangeListener((v, focused) -> v.setBackground(panelBackground(focused ? 0xFF2B77B6 : 0xCC17477C, 0xFF8FEFFF, scaled(2), scaled(11)))); card.addView(button, new LinearLayout.LayoutParams(scaled(280), scaled(44)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, scaled(14)); content.addView(card, lp);
    }

    private String readFileText(File file) {
        if (!file.isFile()) return "";
        StringBuilder text = new StringBuilder();
        try { BufferedReader reader = new BufferedReader(new FileReader(file)); String line; while ((line = reader.readLine()) != null) text.append(line).append('\n'); reader.close(); } catch (Exception ignored) { }
        return text.toString();
    }

    private void showRemoteSettings() { showCloudPairingScreen(); }

    private void showCloudPairingScreen() {
        searchEditorOpen = false; remoteSettingsOpen = true; appearanceTextEditorOpen = false; controllerTestOpen = false;
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFF050B18);
        root.addView(createArcadeHeader(), new LinearLayout.LayoutParams(-1, scaled(82)));
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL); heading.setPadding(scaled(64), 0, scaled(64), 0); heading.setBackgroundColor(0xFF091426);
        TextView back = heroText("‹  BIBLIOTECA", 17, 0xFF27E5F3, true); back.setFocusable(true); back.setOnClickListener(v -> showSettingsScreen(SettingsNavigation.Screen.LIBRARY)); heading.addView(back, new LinearLayout.LayoutParams(scaled(300), -1));
        heading.addView(heroText("PAREAR ESTA TV", 23, Color.WHITE, true), new LinearLayout.LayoutParams(0, -1, 1f)); root.addView(heading, new LinearLayout.LayoutParams(-1, scaled(68)));
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setGravity(Gravity.CENTER_HORIZONTAL); body.setPadding(scaled(110), scaled(28), scaled(110), scaled(24));
        String origin = CloudApiEndpoint.configuredOrigin();
        TextView info = heroText(origin.isEmpty() ? "A biblioteca privada ainda não foi publicada. Quando o serviço estiver pronto, esta TV mostrará um código para aprovação no painel. Não é necessário colar token do GitHub." : "Cada TV recebe uma autorização individual, que pode ser revogada sem afetar os outros aparelhos.", 18, 0xFFE2ECFF, false);
        info.setGravity(Gravity.CENTER); info.setPadding(scaled(26), scaled(16), scaled(26), scaled(16)); info.setBackground(panelBackground(0xFF0D1B31, 0xFF24364F, scaled(1), scaled(14))); body.addView(info, new LinearLayout.LayoutParams(-1, scaled(126)));
        TextView pairing = heroText("", 18, Color.WHITE, true); pairing.setGravity(Gravity.CENTER); pairing.setSingleLine(false); body.addView(pairing, new LinearLayout.LayoutParams(-1, scaled(110)));
        TextView pairButton = editorKey("INICIAR PAREAMENTO", () -> beginCloudPairing(pairing)); pairButton.setFocusable(true); pairButton.setEnabled(!origin.isEmpty()); pairButton.setAlpha(origin.isEmpty() ? .45f : 1f);
        LinearLayout.LayoutParams action = new LinearLayout.LayoutParams(scaled(430), scaled(58)); action.setMargins(0, scaled(18), 0, scaled(12)); body.addView(pairButton, action);
        TextView openLink = editorKey("ABRIR PAINEL DE APROVAÇÃO", () -> { String url = pairing.getTag() == null ? "" : pairing.getTag().toString(); if (!url.isEmpty()) { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) { Toast.makeText(this, "Abra o link exibido em um computador ou celular.", Toast.LENGTH_LONG).show(); } } }); openLink.setFocusable(true);
        body.addView(openLink, new LinearLayout.LayoutParams(scaled(430), scaled(58)));
        TextView paired = heroText(RemoteLibrarySettings.loadCloudDeviceToken(this).isEmpty() ? "Esta TV ainda não está pareada." : "Esta TV já tem autorização privada salva neste aparelho.", 16, 0xFF9FB2D1, false); paired.setGravity(Gravity.CENTER); body.addView(paired, new LinearLayout.LayoutParams(-1, scaled(44)));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(createArcadeFooter(), new LinearLayout.LayoutParams(-1, scaled(72)));
        root.setFocusableInTouchMode(true); root.setOnKeyListener((v, keyCode, event) -> { if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) { showSettingsScreen(SettingsNavigation.Screen.LIBRARY); return true; } return false; }); setContentView(root); pairButton.requestFocus();
    }

    private void beginCloudPairing(final TextView status) {
        if (CloudApiEndpoint.configuredOrigin().isEmpty()) { Toast.makeText(this, "O serviço de nuvem ainda não foi publicado.", Toast.LENGTH_LONG).show(); return; }
        status.setText("Solicitando aprovação…");
        new Thread(() -> {
            try {
                CloudDeviceClient client = new CloudDeviceClient(this, CloudApiEndpoint.configuredOrigin());
                JSONObject request = client.startPairing();
                final String pairId = request.optString("pairId", ""), code = request.optString("code", ""), approvalUrl = request.optString("pairingUrl", "");
                final long expiresAt = request.optLong("expiresAt", System.currentTimeMillis());
                runOnUiThread(() -> { status.setText("Pedido enviado. Aprove esta TV no painel.\nCódigo: " + code + "\n" + approvalUrl); status.setTag(approvalUrl); });
                while (!isFinishing() && System.currentTimeMillis() < expiresAt) {
                    JSONObject response = client.completePairing(pairId, code);
                    if ("paired".equals(response.optString("status"))) {
                        String deviceToken = response.optString("deviceToken", "");
                        if (deviceToken.isEmpty()) throw new Exception("A aprovação não retornou uma credencial válida");
                        RemoteLibrarySettings.saveCloudDeviceToken(this, deviceToken);
                        if (!deviceToken.equals(RemoteLibrarySettings.loadCloudDeviceToken(this))) throw new Exception("O Android Keystore não confirmou o armazenamento protegido da credencial");
                        runOnUiThread(() -> { status.setText("TV pareada. Sincronizando a biblioteca…"); buildScreen(); startRemoteSync(); });
                        return;
                    }
                    Thread.sleep(4000L);
                }
                runOnUiThread(() -> status.setText("O pedido expirou. Inicie um novo pareamento."));
            } catch (final Exception error) {
                runOnUiThread(() -> status.setText("Pareamento indisponível: " + (error.getMessage() == null ? "falha de conexão" : error.getMessage())));
            }
        }, "fireretro-cloud-pairing").start();
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
        boolean active = platform.equals(selectedPlatform);
        button.setBackground(panelBackground(active ? 0xFF1975A8 : 0xCC142E60, 0xFF39D8EA, scaled(2), scaled(13)));
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                SettingsNavigation.Screen destination = SettingsNavigation.destination(platform);
                if (destination != SettingsNavigation.Screen.HOME) { showSettingsScreen(destination); return; }
                selectedPlatform = LauncherState.normalizePlatform(platform);
                if (homeScroll != null) homeScroll.scrollTo(0, 0);
                renderSections(restoredIndex);
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
        gameCardViews.clear();
        platformAnchors.clear();
        if ("APPS".equals(selectedPlatform)) { renderAndroidApps(); updateStickyPlatform(0); return; }
        if ("ANDROID".equals(selectedPlatform)) { renderAndroidGames(); updateStickyPlatform(0); return; }
        Map<String, List<Game>> grouped = new LinkedHashMap<>();
        for (String platform : new String[]{"NES", "SNES", "Mega Drive", "GBA", "PlayStation"}) grouped.put(platform, new ArrayList<Game>());
        for (Game game : allGames) {
            boolean visible = "FAVORITOS".equals(selectedPlatform) ? LauncherState.matchesFavorite(isFavorite(game), game.label, searchQuery) : LauncherState.matchesFilter(game.label, game.platform, selectedPlatform, searchQuery);
            if (visible) {
                if (!grouped.containsKey(game.platform)) grouped.put(game.platform, new ArrayList<Game>());
                grouped.get(game.platform).add(game);
            }
        }
        for (Map.Entry<String, List<Game>> section : grouped.entrySet()) {
            if (section.getValue().isEmpty()) continue;
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(scaled(18), scaled(16), scaled(18), scaled(16));
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
                rows.addView(row, new LinearLayout.LayoutParams(-1, scaled(418)));
                int end = Math.min(i + responsiveColumns, gamesForPlatform.size());
                for (int j = i; j < end; j++) {
                    Game game = gamesForPlatform.get(j);
                    addCard(row, game, allGames.indexOf(game), allGames.indexOf(game) == restoredIndex);
                }
                for (int j = end; j < i + responsiveColumns; j++) row.addView(new View(this), new LinearLayout.LayoutParams(0, scaled(410), 1f));
            }
            LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(-1, -2);
            panelLp.setMargins(0, scaled(6), 0, scaled(8));
            sections.addView(panel, panelLp);
        }
        if ("TODOS".equals(selectedPlatform)) renderAndroidGames();
        if (sections.getChildCount() == 0) {
            TextView empty = heroText("Nenhum jogo encontrado. Ajuste a busca ou escolha outra aba.", 20, Color.WHITE, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(scaled(16), scaled(32), scaled(16), scaled(32));
            sections.addView(empty, new LinearLayout.LayoutParams(-1, scaled(150)));
        }
        configureGameCardFocus();
        updateStickyPlatform(0);
    }

    private void configureGameCardFocus() {
        for (int i = 0; i < gameCardViews.size(); i++) {
            View card = gameCardViews.get(i);
            int left = TvFocusCoordinator.nextCard(i, TvFocusCoordinator.LEFT, responsiveColumns, gameCardViews.size());
            int right = TvFocusCoordinator.nextCard(i, TvFocusCoordinator.RIGHT, responsiveColumns, gameCardViews.size());
            int up = TvFocusCoordinator.nextCard(i, TvFocusCoordinator.UP, responsiveColumns, gameCardViews.size());
            int down = TvFocusCoordinator.nextCard(i, TvFocusCoordinator.DOWN, responsiveColumns, gameCardViews.size());
            card.setNextFocusLeftId(left == TvFocusCoordinator.SIDEBAR ? sidebarFirstId : gameCardViews.get(left).getId());
            card.setNextFocusRightId(gameCardViews.get(right).getId());
            card.setNextFocusUpId(gameCardViews.get(up).getId());
            card.setNextFocusDownId(gameCardViews.get(down).getId());
        }
    }

    private void renderAndroidApps() {
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(scaled(10), scaled(10), scaled(10), scaled(10)); panel.setBackground(panelBackground(0x99112658, 0x8844C9FF, scaled(2), scaled(18)));
        List<AndroidAppEntry> appItems = new ArrayList<>(); for (AndroidAppEntry entry : androidApps) if (!"game".equalsIgnoreCase(entry.category)) appItems.add(entry);
        renderAndroidEntryCards(panel, "APLICATIVOS ANDROID", appItems);
        sections.addView(panel, new LinearLayout.LayoutParams(-1, -2));
    }

    private void renderAndroidGames() {
        List<AndroidAppEntry> gameItems = new ArrayList<>();
        for (AndroidAppEntry entry : androidApps) if ("game".equalsIgnoreCase(entry.category) && (searchQuery.trim().isEmpty() || entry.title.toLowerCase().contains(searchQuery.trim().toLowerCase()))) gameItems.add(entry);
        if (gameItems.isEmpty()) return;
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(scaled(10), scaled(10), scaled(10), scaled(10)); panel.setBackground(panelBackground(0x99112658, 0x8844C9FF, scaled(2), scaled(18)));
        renderAndroidEntryCards(panel, "JOGOS ANDROID", gameItems); sections.addView(panel, new LinearLayout.LayoutParams(-1, -2));
    }

    private void renderAndroidEntryCards(LinearLayout panel, String heading, List<AndroidAppEntry> entries) {
        TextView title = heroText(heading, 22, Color.WHITE, true); title.setGravity(Gravity.CENTER); panel.addView(title, new LinearLayout.LayoutParams(-1, scaled(45)));
        for (final AndroidAppEntry app : entries) {
            TextView card = heroText(app.title + "  ·  " + app.statusLabel() + "\n" + app.sourceLabel(), 18, Color.WHITE, true); card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(scaled(18), 0, scaled(18), 0); card.setFocusable(true); card.setFocusableInTouchMode(true); card.setBackground(cardBackground(false)); card.setContentDescription(app.title + ". " + app.statusLabel());
            card.setOnFocusChangeListener((view, focused) -> view.setBackground(cardBackground(focused)));
            card.setOnClickListener(view -> launchAndroidApp(app)); panel.addView(card, new LinearLayout.LayoutParams(-1, scaled(78)));
        }
        if (entries.isEmpty()) { TextView empty = heroText("Nenhum aplicativo publicado no catálogo privado.", 18, Color.WHITE, false); empty.setGravity(Gravity.CENTER); panel.addView(empty, new LinearLayout.LayoutParams(-1, scaled(120))); }
    }

    private void launchAndroidApp(AndroidAppEntry app) {
        try { if (getPackageManager().getLaunchIntentForPackage(app.packageName) != null) { startActivity(getPackageManager().getLaunchIntentForPackage(app.packageName)); return; } } catch (Exception ignored) { }
        if ("store".equalsIgnoreCase(app.sourceType) && app.source.startsWith("https://")) {
            String market = "google".equalsIgnoreCase(app.storeType) ? "market://details?id=" + app.packageName : "amzn://apps/android?p=" + app.packageName;
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(market))); }
            catch (Exception ignored) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(app.source))); }
            return;
        }
        if ("private-apk".equalsIgnoreCase(app.sourceType) && app.source.matches("[A-Fa-f0-9-]{36}")) {
            String token = RemoteLibrarySettings.loadCloudDeviceToken(this);
            if (token.isEmpty()) { showRemoteSettings(); return; }
            if (remoteStatusView != null) remoteStatusView.setText("BAIXANDO APP: " + app.title + "…");
            new Thread(() -> {
                try {
                    JSONObject ticket = new CloudDeviceClient(this, CloudApiEndpoint.configuredOrigin()).downloadTicket(token, app.source);
                    JSONObject metadata = ticket.optJSONObject("item");
                    final String trustedCertificate = metadata == null ? "" : metadata.optString("certificateSha256", "");
                    AndroidAppInstaller.download(this, ticket.optString("url", ""), "", ticket.optLong("size", -1), ticket.optString("sha256", ""), new AndroidAppInstaller.Callback() {
                        @Override public void onProgress(long done, long total) { if (remoteStatusView != null) remoteStatusView.setText("BAIXANDO APP: " + app.title + "  " + (total > 0 ? done * 100 / total : 0) + "%"); }
                        @Override public void onReady(File apk) { try { AndroidAppInstaller.verifyPrivateApk(MainActivity.this, apk, app.packageName, trustedCertificate); AndroidAppInstaller.install(MainActivity.this, apk); } catch (Exception error) { Toast.makeText(MainActivity.this, "APK rejeitado: " + error.getMessage(), Toast.LENGTH_LONG).show(); } }
                        @Override public void onError(String message) { if (remoteStatusView != null) remoteStatusView.setText("APP: " + message); }
                    });
                } catch (Exception error) { runOnUiThread(() -> { if (remoteStatusView != null) remoteStatusView.setText("APP: " + (error.getMessage() == null ? "falha de download" : error.getMessage())); }); }
            }, "fireretro-private-apk").start();
            return;
        }
        if (AndroidAppSource.isAllowed(app.source)) {
            RemoteLibrarySettings.Settings settings = RemoteLibrarySettings.load(this);
            if (remoteStatusView != null) remoteStatusView.setText("BAIXANDO APP: " + app.title + "…");
            AndroidAppInstaller.download(this, app.source, settings.token, -1, "", new AndroidAppInstaller.Callback() {
                @Override public void onProgress(long done, long total) { if (remoteStatusView != null) remoteStatusView.setText("BAIXANDO APP: " + app.title + "  " + (total > 0 ? done * 100 / total : 0) + "%"); }
                @Override public void onReady(File apk) { try { AndroidAppInstaller.install(MainActivity.this, apk); } catch (Exception error) { Toast.makeText(MainActivity.this, "Não foi possível iniciar a instalação: " + error.getMessage(), Toast.LENGTH_LONG).show(); } }
                @Override public void onError(String message) { if (remoteStatusView != null) remoteStatusView.setText("APP: " + message); }
            });
            return;
        }
        Toast.makeText(this, "Fonte de APK inválida ou ausente.", Toast.LENGTH_LONG).show();
    }

    private void readAndroidApps() {
        androidApps.clear(); if (!EXTERNAL_APPS_FILE.isFile()) return;
        try { StringBuilder text = new StringBuilder(); BufferedReader reader = new BufferedReader(new FileReader(EXTERNAL_APPS_FILE)); String line; while ((line = reader.readLine()) != null) text.append(line); reader.close(); JSONArray items = new JSONObject(text.toString()).optJSONArray("items"); if (items == null) return; for (int i = 0; i < items.length(); i++) { JSONObject item = items.optJSONObject(i); if (item == null) continue; AndroidAppEntry app = new AndroidAppEntry(item.optString("title", "Aplicativo"), item.optString("package", ""), item.optString("source", ""), item.optString("sourceType", "apk"), item.optString("category", "Outros"), item.optString("storeType", "")); try { app.setInstalled(getPackageManager().getLaunchIntentForPackage(app.packageName) != null); } catch (Exception ignored) { } androidApps.add(app); } } catch (Exception ignored) { }
    }

    private boolean externalAppsChanged() {
        long modified = EXTERNAL_APPS_FILE.isFile() ? EXTERNAL_APPS_FILE.lastModified() : -1L;
        long length = EXTERNAL_APPS_FILE.isFile() ? EXTERNAL_APPS_FILE.length() : -1L;
        boolean changed = modified != appsLastModified || length != appsLength; appsLastModified = modified; appsLength = length; return changed;
    }

    private void addCard(LinearLayout row, final Game game, final int gameIndex, boolean focusFirst) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setId(View.generateViewId());
        card.setPadding(scaled(12), scaled(10), scaled(12), scaled(10));
        card.setBackground(cardBackground(false));
        card.setContentDescription(game.label + ", " + game.platform + (isFavorite(game) ? ", favorito" : "") + ". A abre; Y alterna favorito.");
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
        card.addView(coverArea, new LinearLayout.LayoutParams(-1, scaled(335)));
        TextView label = new TextView(this);
        boolean isNew = game.downloadedAt > 0 && (System.currentTimeMillis() - game.downloadedAt) < 7L * 24L * 60L * 60L * 1000L;
        label.setText((isFavorite(game) ? "★  " : "") + (isNew ? "NOVO  ·  " : "") + (game.remoteAvailable ? "ONLINE  ·  " : "") + game.label); label.setTextColor(isNew ? 0xFF8FFFE0 : (game.remoteAvailable ? 0xFF8FEFFF : Color.WHITE)); label.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled(15)); label.setGravity(Gravity.CENTER);
        label.setIncludeFontPadding(false); label.setMaxLines(2); label.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(label, new LinearLayout.LayoutParams(-1, scaled(48)));
        card.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { rememberGame(gameIndex); launch(game); } });
        card.setOnLongClickListener(v -> { toggleFavorite(game, gameIndex); return true; });
        card.setOnKeyListener((v, keyCode, event) -> { if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BUTTON_Y) { toggleFavorite(game, gameIndex); return true; } return false; });
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) { view.setBackground(cardBackground(focused)); if (focused) { rememberGame(gameIndex); lastContentFocus = view; lastFocusedGamePath = game.path; navigationState.rememberFocus(game.path); } }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, scaled(410), 1f);
        lp.setMargins(scaled(5), scaled(4), scaled(5), scaled(4));
        row.addView(card, lp);
        gameCardViews.add(card);
        if (focusFirst || (!lastFocusedGamePath.isEmpty() && lastFocusedGamePath.equals(game.path))) { card.requestFocus(); lastContentFocus = card; }
    }

    private void updateStickyPlatform(int scrollY) {
        if (stickyPlatformView == null) return;
        if ("FAVORITOS".equals(selectedPlatform)) { stickyPlatformView.setText("JOGOS FAVORITOS"); return; }
        if (scrollY <= 0) {
            stickyPlatformView.setText("TODOS".equals(selectedPlatform) ? "TODOS OS JOGOS" : selectedPlatform.toUpperCase());
            return;
        }
        String current = "TODOS OS JOGOS";
        for (Map.Entry<String, View> entry : platformAnchors.entrySet()) if (entry.getValue().getTop() <= scrollY + scaled(8)) current = entry.getKey().toUpperCase();
        stickyPlatformView.setText("TODOS".equals(selectedPlatform) ? current : selectedPlatform.toUpperCase());
    }

    private boolean isFavorite(Game game) { return favoriteIds().contains(game.path); }

    private Set<String> favoriteIds() { return new HashSet<>(getSharedPreferences(PREFS, MODE_PRIVATE).getStringSet("favorite_games", new HashSet<String>())); }

    private void toggleFavorite(Game game, int gameIndex) {
        Set<String> favorites = favoriteIds(); boolean added;
        if (favorites.contains(game.path)) { favorites.remove(game.path); added = false; } else { favorites.add(game.path); added = true; }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putStringSet("favorite_games", favorites).apply();
        Toast.makeText(this, added ? "Adicionado aos favoritos." : "Removido dos favoritos.", Toast.LENGTH_SHORT).show(); renderSections(gameIndex);
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
        // Themes are static. Keep the old callback cancelled for compatibility with existing state files.
    }
    private void showPersonalizeHint() { showThemeChooser(); }

    private void showThemeChooser() {
        final String[] ids = themeCatalog.ids();
        final String[] names = new String[ids.length];
        for (int i = 0; i < ids.length; i++) names[i] = themeCatalog.get(ids[i]).name + (ids[i].equals(selectedThemeId) ? "  ✓" : "");
        new AlertDialog.Builder(this).setTitle("Aparência da TV")
                .setItems(names, (dialog, which) -> {
                    selectedThemeId = ids[which];
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(SELECTED_THEME, selectedThemeId).apply();
                    themeCustomization = defaultThemeCustomization(themeCatalog.get(selectedThemeId));
                    saveThemeCustomization();
                    dialog.dismiss();
                    if (activeSettingsScreen == SettingsNavigation.Screen.APPEARANCE) showSettingsScreen(SettingsNavigation.Screen.APPEARANCE); else applySelectedTheme();
                }).setNeutralButton("Editar texto", (dialog, which) -> showAppearanceTextEditor()).setNegativeButton("Fechar", null).show();
    }

    private void showAppearanceTextEditor() {
        searchEditorOpen = false; remoteSettingsOpen = false; appearanceTextEditorOpen = true; controllerTestOpen = false;
        activeSettingsScreen = SettingsNavigation.Screen.APPEARANCE;
        final StringBuilder titleValue = new StringBuilder(themeCustomization.title);
        final StringBuilder subtitleValue = new StringBuilder(themeCustomization.subtitle);
        final int[] selectedField = {0};

        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFF050B18);
        root.addView(createArcadeHeader(), new LinearLayout.LayoutParams(-1, scaled(82)));

        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL); heading.setPadding(scaled(64), 0, scaled(64), 0); heading.setBackgroundColor(0xFF091426);
        TextView back = heroText("‹  APARÊNCIA", 17, 0xFF27E5F3, true); back.setGravity(Gravity.CENTER_VERTICAL); back.setFocusable(true); back.setOnClickListener(v -> showSettingsScreen(SettingsNavigation.Screen.APPEARANCE));
        heading.addView(back, new LinearLayout.LayoutParams(scaled(280), -1));
        TextView editTitle = heroText("EDITAR TEXTO DO TEMA", 23, Color.WHITE, true); editTitle.setGravity(Gravity.CENTER); heading.addView(editTitle, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView local = heroText("SALVO SOMENTE NESTA TV", 13, 0xFF94A3B8, true); local.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL); heading.addView(local, new LinearLayout.LayoutParams(scaled(320), -1));
        root.addView(heading, new LinearLayout.LayoutParams(-1, scaled(68)));

        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.HORIZONTAL); content.setPadding(scaled(64), scaled(28), scaled(64), scaled(24));
        LinearLayout preview = new LinearLayout(this); preview.setOrientation(LinearLayout.VERTICAL); preview.setGravity(Gravity.CENTER); preview.setPadding(scaled(28), scaled(28), scaled(28), scaled(28)); preview.setBackground(panelBackground(0xFF102448, 0xFF27E5F3, scaled(2), scaled(22)));
        preview.addView(heroText("PRÉVIA NA TV", 14, 0xFF27E5F3, true), new LinearLayout.LayoutParams(-1, scaled(34)));
        ImageView art = new ImageView(this); art.setImageBitmap(themeArtwork(selectedThemeId)); art.setScaleType(ImageView.ScaleType.CENTER_CROP); art.setAlpha(.72f);
        FrameLayout previewFrame = new FrameLayout(this); previewFrame.setBackgroundColor(0xFF07183A); previewFrame.addView(art, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout previewText = new LinearLayout(this); previewText.setOrientation(LinearLayout.VERTICAL); previewText.setGravity(Gravity.CENTER); previewText.setPadding(scaled(24), 0, scaled(24), 0);
        final TextView titlePreview = heroText(titleValue.toString(), themeCustomization.textSize, parseColor(themeCustomization.textColor, Color.WHITE), true); titlePreview.setGravity(Gravity.CENTER); titlePreview.setShadowLayer(scaled(themeCustomization.shadow / 18), 0, scaled(3), 0xFF000000);
        final TextView subtitlePreview = heroText(subtitleValue.toString(), Math.max(15, themeCustomization.textSize / 2), parseColor(themeCustomization.textColor, 0xFFE5EEFF), true); subtitlePreview.setGravity(Gravity.CENTER); subtitlePreview.setShadowLayer(scaled(themeCustomization.shadow / 22), 0, scaled(2), 0xFF000000);
        previewText.addView(titlePreview, new LinearLayout.LayoutParams(-1, scaled(58))); previewText.addView(subtitlePreview, new LinearLayout.LayoutParams(-1, scaled(42))); previewFrame.addView(previewText, new FrameLayout.LayoutParams(-1, -1));
        preview.addView(previewFrame, new LinearLayout.LayoutParams(-1, 0, 1f));

        final TextView titleField = heroText(titleValue.toString(), 20, Color.WHITE, true); titleField.setGravity(Gravity.CENTER_VERTICAL); titleField.setPadding(scaled(18), 0, scaled(18), 0); titleField.setFocusable(true);
        final TextView subtitleField = heroText(subtitleValue.toString(), 18, Color.WHITE, false); subtitleField.setGravity(Gravity.CENTER_VERTICAL); subtitleField.setPadding(scaled(18), 0, scaled(18), 0); subtitleField.setFocusable(true);
        final Runnable refresh = () -> {
            titlePreview.setText(titleValue.length() == 0 ? "Título do tema" : titleValue.toString());
            subtitlePreview.setText(subtitleValue.length() == 0 ? "Subtítulo do tema" : subtitleValue.toString());
            titleField.setText("TÍTULO     " + (titleValue.length() == 0 ? "_" : titleValue.toString()));
            subtitleField.setText("SUBTÍTULO  " + (subtitleValue.length() == 0 ? "_" : subtitleValue.toString()));
            titleField.setBackground(panelBackground(selectedField[0] == 0 ? 0xFF173552 : 0xFF0D1B31, selectedField[0] == 0 ? 0xFF27E5F3 : 0xFF24364F, scaled(2), scaled(12)));
            subtitleField.setBackground(panelBackground(selectedField[0] == 1 ? 0xFF173552 : 0xFF0D1B31, selectedField[0] == 1 ? 0xFF27E5F3 : 0xFF24364F, scaled(2), scaled(12)));
        };
        titleField.setOnClickListener(v -> { selectedField[0] = 0; refresh.run(); });
        subtitleField.setOnClickListener(v -> { selectedField[0] = 1; refresh.run(); });
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(-1, scaled(58)); fieldParams.setMargins(0, scaled(14), 0, 0); preview.addView(titleField, fieldParams);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, scaled(58)); subtitleParams.setMargins(0, scaled(10), 0, 0); preview.addView(subtitleField, subtitleParams);
        content.addView(preview, new LinearLayout.LayoutParams(scaled(660), -1));

        LinearLayout editor = new LinearLayout(this); editor.setOrientation(LinearLayout.VERTICAL); editor.setPadding(scaled(28), 0, 0, 0);
        editor.addView(heroText("USE O DIRECIONAL E A PARA DIGITAR", 18, Color.WHITE, true), new LinearLayout.LayoutParams(-1, scaled(46)));
        editor.addView(heroText("O teclado permanece na tela e funciona com o controle.", 15, 0xFF94A3B8, false), new LinearLayout.LayoutParams(-1, scaled(34)));
        LinearLayout keyboard = new LinearLayout(this); keyboard.setOrientation(LinearLayout.VERTICAL);
        final String[] keys = {"A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z","0","1","2","3","4","5","6","7","8","9","-","&","."};
        TextView firstKey = null;
        for (int rowIndex = 0; rowIndex < 6; rowIndex++) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            for (int column = 0; column < 7; column++) {
                int index = rowIndex * 7 + column; if (index >= keys.length) break;
                String key = keys[index]; TextView keyView = editorKey(key, () -> { StringBuilder target = selectedField[0] == 0 ? titleValue : subtitleValue; if (target.length() < 80) target.append(key); refresh.run(); });
                if (firstKey == null) firstKey = keyView;
                LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(0, scaled(50), 1f); keyParams.setMargins(0, 0, scaled(8), scaled(8)); row.addView(keyView, keyParams);
            }
            keyboard.addView(row, new LinearLayout.LayoutParams(-1, scaled(58)));
        }
        LinearLayout actions = new LinearLayout(this);
        actions.addView(editorKey("ESPAÇO", () -> { StringBuilder target = selectedField[0] == 0 ? titleValue : subtitleValue; if (target.length() < 80) target.append(' '); refresh.run(); }), new LinearLayout.LayoutParams(0, scaled(54), 1.5f));
        LinearLayout.LayoutParams actionGap = new LinearLayout.LayoutParams(0, scaled(54), 1f); actionGap.setMargins(scaled(9), 0, 0, 0);
        actions.addView(editorKey("APAGAR", () -> { StringBuilder target = selectedField[0] == 0 ? titleValue : subtitleValue; if (target.length() > 0) target.deleteCharAt(target.length() - 1); refresh.run(); }), actionGap);
        LinearLayout.LayoutParams clearGap = new LinearLayout.LayoutParams(0, scaled(54), 1f); clearGap.setMargins(scaled(9), 0, 0, 0);
        actions.addView(editorKey("LIMPAR", () -> { (selectedField[0] == 0 ? titleValue : subtitleValue).setLength(0); refresh.run(); }), clearGap);
        keyboard.addView(actions, new LinearLayout.LayoutParams(-1, scaled(62))); editor.addView(keyboard, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout saveRow = new LinearLayout(this); saveRow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        TextView cancel = controllerAction("CANCELAR", 0xFF13243D, Color.WHITE, v -> showSettingsScreen(SettingsNavigation.Screen.APPEARANCE));
        TextView save = controllerAction("SALVAR TEXTO", 0xFF42D392, 0xFF050B18, v -> { themeCustomization = new ThemeCustomization(titleValue.toString(), subtitleValue.toString(), themeCustomization.textColor, themeCustomization.textSize, themeCustomization.shadow, themeCustomization.overlay, themeCustomization.cardDensity); saveThemeCustomization(); showSettingsScreen(SettingsNavigation.Screen.APPEARANCE); Toast.makeText(this, "Texto salvo nesta TV.", Toast.LENGTH_SHORT).show(); });
        saveRow.addView(cancel, new LinearLayout.LayoutParams(scaled(210), scaled(54))); LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(scaled(260), scaled(54)); saveParams.setMargins(scaled(12), 0, 0, 0); saveRow.addView(save, saveParams); editor.addView(saveRow, new LinearLayout.LayoutParams(-1, scaled(62)));
        content.addView(editor, new LinearLayout.LayoutParams(0, -1, 1f)); root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(createArcadeFooter(), new LinearLayout.LayoutParams(-1, scaled(72)));
        root.setFocusableInTouchMode(true); root.setOnKeyListener((v, keyCode, event) -> { if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) { showSettingsScreen(SettingsNavigation.Screen.APPEARANCE); return true; } return false; });
        setContentView(root); refresh.run(); if (firstKey != null) firstKey.requestFocus();
    }

    /** Fire TV's IME covers the editor on several Fire OS builds; keep search input in-app. */
    private void showSearchEditor() {
        searchEditorOpen = true; remoteSettingsOpen = false; appearanceTextEditorOpen = false; controllerTestOpen = false;
        final StringBuilder value = new StringBuilder(searchQuery);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFF050B18);
        root.addView(createArcadeHeader(), new LinearLayout.LayoutParams(-1, scaled(82)));
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL); heading.setPadding(scaled(64), 0, scaled(64), 0); heading.setBackgroundColor(0xFF091426);
        TextView back = heroText("‹  JOGOS", 17, 0xFF27E5F3, true); back.setFocusable(true); back.setOnClickListener(v -> buildScreen()); heading.addView(back, new LinearLayout.LayoutParams(scaled(260), -1));
        heading.addView(heroText("BUSCAR JOGO", 23, Color.WHITE, true), new LinearLayout.LayoutParams(0, -1, 1f)); root.addView(heading, new LinearLayout.LayoutParams(-1, scaled(68)));
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(scaled(120), scaled(38), scaled(120), scaled(28));
        TextView preview = heroText("⌕  " + (value.length() == 0 ? "Digite o nome do jogo" : value.toString()), 29, Color.WHITE, true); preview.setPadding(scaled(22), 0, scaled(22), 0); preview.setBackground(panelBackground(0xFF102448, 0xFF27E5F3, scaled(2), scaled(15))); body.addView(preview, new LinearLayout.LayoutParams(-1, scaled(72)));
        String[] rows = {"1234567890", "QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM-_."};
        for (String rowText : rows) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER);
            for (int i = 0; i < rowText.length(); i++) { final String key = rowText.substring(i, i + 1); TextView keyView = editorKey(key, () -> { if (value.length() < 60) value.append(key.toLowerCase()); preview.setText("⌕  " + value); }); LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(0, scaled(58), 1f); keyParams.setMargins(scaled(3), 0, scaled(3), scaled(8)); row.addView(keyView, keyParams); }
            body.addView(row, new LinearLayout.LayoutParams(-1, scaled(66)));
        }
        LinearLayout actions = new LinearLayout(this);
        actions.addView(editorKey("ESPAÇO", () -> { if (value.length() < 60) value.append(' '); preview.setText("⌕  " + value); }), new LinearLayout.LayoutParams(0, scaled(58), 1.25f));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(0, scaled(58), 1f); gap.setMargins(scaled(8), 0, 0, 0);
        actions.addView(editorKey("APAGAR", () -> { if (value.length() > 0) value.deleteCharAt(value.length() - 1); preview.setText("⌕  " + (value.length() == 0 ? "Digite o nome do jogo" : value)); }), gap);
        LinearLayout.LayoutParams clear = new LinearLayout.LayoutParams(0, scaled(58), 1f); clear.setMargins(scaled(8), 0, 0, 0);
        actions.addView(editorKey("LIMPAR", () -> { value.setLength(0); preview.setText("⌕  Digite o nome do jogo"); }), clear);
        LinearLayout.LayoutParams apply = new LinearLayout.LayoutParams(0, scaled(58), 1.4f); apply.setMargins(scaled(8), 0, 0, 0);
        actions.addView(editorKey("APLICAR BUSCA", () -> { searchQuery = value.toString().trim(); buildScreen(); }), apply);
        body.addView(actions, new LinearLayout.LayoutParams(-1, scaled(66))); root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(createArcadeFooter(), new LinearLayout.LayoutParams(-1, scaled(72)));
        root.setFocusableInTouchMode(true); root.setOnKeyListener((v, keyCode, event) -> { if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) { buildScreen(); return true; } return false; }); setContentView(root); body.getChildAt(1).requestFocus();
    }

    private TextView editorKey(String label, Runnable action) {
        TextView key = heroText(label, label.length() > 2 ? 14 : 18, Color.WHITE, true); key.setGravity(Gravity.CENTER); key.setFocusable(true); key.setFocusableInTouchMode(true); key.setBackground(panelBackground(0xFF13243D, 0xFF31445F, scaled(1), scaled(10))); key.setOnClickListener(v -> action.run()); key.setOnFocusChangeListener((v, focused) -> { key.setTextColor(focused ? 0xFF050B18 : Color.WHITE); key.setBackground(panelBackground(focused ? 0xFF27E5F3 : 0xFF13243D, focused ? 0xFF27E5F3 : 0xFF31445F, scaled(1), scaled(10))); }); return key;
    }

    private int indexOfTheme(String[] ids, String selected) {
        for (int i = 0; i < ids.length; i++) if (ids[i].equals(selected)) return i;
        return 0;
    }

    private void showControllerDialog() {
        ControllerRegistry.observe(this);
        String text = ControllerRegistry.summary(this);
        new AlertDialog.Builder(this).setTitle("Controles disponíveis").setMessage(text.toString())
                .setPositiveButton("Testar no RetroArch", (dialog, which) -> Toast.makeText(this, "Abra Port 1 Controls > Set All Controls no RetroArch e salve o perfil.", Toast.LENGTH_LONG).show())
                .setNegativeButton("Fechar", null).show();
    }

    private void applySelectedTheme() {
        selectedThemeId = getSharedPreferences(PREFS, MODE_PRIVATE).getString(SELECTED_THEME, selectedThemeId);
        ThemeCatalog.Theme theme = themeCatalog.get(selectedThemeId);
        if (theme == null) theme = themeCatalog.get("arcade-moderno");
        activeThemeProfile = new ThemeProfile(theme.id, theme.name, theme.background, parseColor(theme.backgroundColor, 0xFF07183A), 0xFF102750, 0xFF132F59, parseColor(themeCustomization.textColor, Color.WHITE), parseColor(theme.accent, 0xFF38D9FF), parseColor(theme.accent, 0xFF38D9FF), themeCustomization.overlay, themeCustomization.textSize, 16, themeCustomization.title, themeCustomization.subtitle);
        if (themeCustomization == null) themeCustomization = defaultThemeCustomization(theme);
        responsiveColumns = Math.min(themeCustomization.cardDensity, screenWidth >= scaled(1350) ? 4 : (screenWidth >= scaled(900) ? 3 : 2));
        int accent = parseColor(theme.accent, 0xFF38D9FF);
        int base = parseColor(theme.backgroundColor, Color.rgb(7, 24, 58));
        if (shellRoot != null) shellRoot.setBackgroundColor(base);
        if (sidebarRoot != null) sidebarRoot.setBackground(panelBackground(darken(base, .35f), accent, scaled(1), scaled(18)));
        if (shellTitleView != null) { shellTitleView.setText(themeCustomization.title); shellTitleView.setTextColor(parseColor(themeCustomization.textColor, Color.WHITE)); shellTitleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled(themeCustomization.textSize)); shellTitleView.setShadowLayer(themeCustomization.shadow / 4f, 0, scaled(3), 0xDD000000); }
        if (shellSubtitleView != null) { shellSubtitleView.setText(themeCustomization.subtitle); shellSubtitleView.setTextColor(parseColor(themeCustomization.textColor, 0xFFE5EEFF)); }
        if (shellStatusView != null) { shellStatusView.setTextColor(accent); shellStatusView.setBackground(panelBackground(0x33203D3B, accent, scaled(1), scaled(12))); }
        if (stickyPlatformView != null) stickyPlatformView.setBackground(panelBackground(darken(base, .18f), accent, scaled(2), scaled(13)));
        if (heroArt != null) {
            heroArt.setVisibility(View.VISIBLE);
            heroArt.setImageBitmap(themeArtwork(theme.id));
            heroArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
            heroArt.setAlpha(0.58f);
        }
        if (heroShade != null) {
            int overlay = themeCustomization.overlay;
            heroShade.setBackgroundColor(Color.argb(ThemeState.overlayAlpha(overlay), 3, 13, 42));
        }
        if (slideTitleView != null) { slideTitleView.setText(themeCustomization.title); slideTitleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled(themeCustomization.textSize)); slideTitleView.setTextColor(parseColor(themeCustomization.textColor, accent)); slideTitleView.setShadowLayer(themeCustomization.shadow / 4f, 0, scaled(3), 0xDD000000); }
        if (slideCaptionView != null) { slideCaptionView.setText(themeCustomization.subtitle); slideCaptionView.setTextColor(parseColor(themeCustomization.textColor, 0xFFE5EEFF)); }
        if (slideCounterView != null) {
            slideCounterView.setVisibility(View.VISIBLE);
            slideCounterView.setText(slides.isEmpty() ? "TEMA LOCAL" : "SLIDE " + (activeSlide + 1) + " DE " + slides.size());
        }
        if (dotsView != null) {
            dotsView.setVisibility(View.VISIBLE);
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < Math.max(1, slides.size()); i++) dots.append(i == activeSlide ? "●  " : "○  ");
            dotsView.setText(dots.toString().trim());
        }
    }

    private int darken(int color, float amount) {
        float factor = Math.max(0f, Math.min(1f, 1f - amount));
        return Color.rgb(Math.round(Color.red(color) * factor), Math.round(Color.green(color) * factor), Math.round(Color.blue(color) * factor));
    }

    private int parseColor(String value, int fallback) { try { return Color.parseColor(value); } catch (Exception ignored) { return fallback; } }
    private void loadThemeCustomization() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        ThemeCatalog.Theme theme = themeCatalog.get(selectedThemeId);
        ThemeCustomization defaults = defaultThemeCustomization(theme);
        String prefix = "theme." + selectedThemeId + ".";
        themeCustomization = new ThemeCustomization(p.getString(prefix + "title", p.getString(THEME_TITLE, defaults.title)), p.getString(prefix + "subtitle", p.getString(THEME_SUBTITLE, defaults.subtitle)), p.getString(prefix + "color", p.getString("theme_color", defaults.textColor)), p.getInt(prefix + "size", p.getInt("theme_size", defaults.textSize)), p.getInt(prefix + "shadow", p.getInt("theme_shadow", defaults.shadow)), p.getInt(prefix + "overlay", p.getInt("theme_overlay", defaults.overlay)), p.getInt(prefix + "density", p.getInt("theme_density", defaults.cardDensity)));
    }
    private ThemeCustomization defaultThemeCustomization(ThemeCatalog.Theme theme) {
        if (theme == null) return ThemeCustomization.defaults("Arcade moderno");
        return theme.customization == null ? ThemeCustomization.defaults(theme.name) : theme.customization;
    }

    private void applyCloudThemeAssignment() {
        File assignment = new File(EXTERNAL_CATALOG_FILE.getParentFile(), "theme-assignment.txt");
        if (!assignment.isFile()) return;
        try {
            String id = readFileText(assignment).trim();
            if (id.isEmpty() || !themeCatalog.contains(id)) return;
            SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
            if (id.equals(preferences.getString("cloud_assigned_theme", ""))) return;
            selectedThemeId = id;
            preferences.edit().putString(SELECTED_THEME, id).putString("cloud_assigned_theme", id).apply();
            String customKey = "theme." + id + ".title";
            themeCustomization = preferences.contains(customKey) ? loadCustomizationFromPreferences(id) : defaultThemeCustomization(themeCatalog.get(id));
            saveThemeCustomization();
            if (activeSettingsScreen == SettingsNavigation.Screen.HOME) buildScreen();
        } catch (Exception ignored) { }
    }

    private ThemeCustomization loadCustomizationFromPreferences(String id) {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE); String key = "theme." + id + ".";
        ThemeCustomization fallback = defaultThemeCustomization(themeCatalog.get(id));
        return new ThemeCustomization(p.getString(key + "title", fallback.title), p.getString(key + "subtitle", fallback.subtitle), p.getString(key + "color", fallback.textColor), p.getInt(key + "size", fallback.textSize), p.getInt(key + "shadow", fallback.shadow), p.getInt(key + "overlay", fallback.overlay), p.getInt(key + "density", fallback.cardDensity));
    }
    private void saveThemeCustomization() {
        String prefix = "theme." + selectedThemeId + ".";
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(prefix + "title", themeCustomization.title).putString(prefix + "subtitle", themeCustomization.subtitle).putString(prefix + "color", themeCustomization.textColor).putInt(prefix + "size", themeCustomization.textSize).putInt(prefix + "shadow", themeCustomization.shadow).putInt(prefix + "overlay", themeCustomization.overlay).putInt(prefix + "density", themeCustomization.cardDensity).putString(THEME_TITLE, themeCustomization.title).putString(THEME_SUBTITLE, themeCustomization.subtitle).putString("theme_color", themeCustomization.textColor).putInt("theme_size", themeCustomization.textSize).putInt("theme_shadow", themeCustomization.shadow).putInt("theme_overlay", themeCustomization.overlay).putInt("theme_density", themeCustomization.cardDensity).apply();
    }

    private void loadRemoteThemes() {
        File file = new File("/sdcard/Android/data/com.kiver.fireretro/files/catalog/themes.json");
        if (!file.isFile()) return;
        try {
            StringBuilder text = new StringBuilder(); BufferedReader reader = new BufferedReader(new FileReader(file)); String line;
            while ((line = reader.readLine()) != null) text.append(line); reader.close();
            JSONArray themes = new JSONArray(text.toString());
            for (int i = 0; i < themes.length(); i++) { JSONObject t = themes.optJSONObject(i); if (t != null) { ThemeCustomization custom = new ThemeCustomization(t.optString("title", t.optString("name", "Jogos Retro")), t.optString("subtitle", "Clássicos para jogar"), t.optString("textColor", "#FFFFFF"), t.optInt("textSize", 38), t.optInt("shadow", 70), t.optInt("overlay", 68), t.optInt("density", 4)); themeCatalog.addRemote(t.optString("id", ""), t.optString("name", ""), t.optString("background", ""), t.optString("accent", "#38D9FF"), t.optInt("overlay", 68), t.optString("cardLayout", "compact"), t.optString("scale", "contain"), custom, t.optString("backgroundColor", "#07183A")); } }
            themeArtworkCache.clear();
        } catch (Exception ignored) { }
    }

    @Override public void onBackPressed() {
        if (searchEditorOpen) { backFromEditor(SettingsNavigation.Editor.SEARCH); return; }
        if (remoteSettingsOpen) { backFromEditor(SettingsNavigation.Editor.REMOTE_SETTINGS); return; }
        if (appearanceTextEditorOpen) { backFromEditor(SettingsNavigation.Editor.APPEARANCE_TEXT); return; }
        if (controllerTestOpen) { backFromEditor(SettingsNavigation.Editor.CONTROLLER_TEST); return; }
        if (activeSettingsScreen != SettingsNavigation.Screen.HOME) { buildScreen(); return; }
        if (homeScroll != null && (homeScroll.getScrollY() > 0 || !"TODOS".equals(selectedPlatform) || !searchQuery.trim().isEmpty())) { returnHome(); return; }
        returnHome();
    }
    private void backFromEditor(SettingsNavigation.Editor editor) {
        searchEditorOpen = false; remoteSettingsOpen = false; appearanceTextEditorOpen = false; controllerTestOpen = false;
        SettingsNavigation.Screen destination = SettingsNavigation.backDestination(editor);
        if (destination == SettingsNavigation.Screen.HOME) buildScreen(); else showSettingsScreen(destination);
    }
    private void returnHome() {
        selectedPlatform = "TODOS";
        if (searchView != null && !searchQuery.isEmpty()) { searchQuery = ""; searchView.setText("⌕  BUSCAR JOGO"); renderSections(0); } else renderSections(0);
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

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7401);
        }
    }

    /** Reapplies the safe navigation/exit defaults when RetroArch lost them. */
    private void ensureControllerProfile() {
        File config = new File(RETROARCH_CONFIG);
        if (!config.isFile()) { setControllerMessage("RetroArch não criou a configuração ainda. Abra o RetroArch uma vez e tente novamente."); return; }
        try {
            String original = readFileText(config);
            String corrected = ControllerProfile.mergeSafeSettings(original);
            if (ControllerProfile.healthy(original)) { setControllerMessage("Perfil do RetroArch confirmado."); return; }
            File backup = new File(config.getAbsolutePath() + ".fireretro-backup-" + System.currentTimeMillis()); copyFile(config, backup);
            File temporary = new File(config.getAbsolutePath() + ".fireretro-tmp");
            try (FileOutputStream output = new FileOutputStream(temporary, false)) { output.write(corrected.getBytes("UTF-8")); output.getFD().sync(); }
            if (config.delete() && temporary.renameTo(config) && ControllerProfile.healthy(readFileText(config))) { setControllerMessage("Perfil corrigido e confirmado. Backup: " + backup.getName()); return; }
            if (config.exists()) config.delete(); temporary.renameTo(config); copyFile(backup, config);
            setControllerMessage("A correção não foi confirmada; a configuração anterior foi restaurada.");
        } catch (Exception error) {
            setControllerMessage("Não foi possível corrigir o RetroArch: " + error.getMessage());
        }
    }

    private void setControllerMessage(String message) { if (controllerStatusView != null) controllerStatusView.setText(message); }

    private void copyFile(File source, File destination) throws Exception {
        FileInputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(destination);
        try { byte[] buffer = new byte[8192]; int read; while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read); }
        finally { input.close(); output.close(); }
    }
    private boolean controllerConnected() {
        for (int deviceId : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(deviceId); if (device == null || device.isVirtual()) continue; int sources = device.getSources();
            if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) return true;
        } return false;
    }
    private GradientDrawable panelBackground(int color, int strokeColor, int strokeWidth, int radius) { return drawable(color, strokeColor, strokeWidth, radius); }
    private GradientDrawable cardBackground(boolean focused) {
        ThemeCatalog.Theme theme = themeCatalog.get(selectedThemeId);
        int accent = theme == null ? 0xFF39D8EA : parseColor(theme.accent, 0xFF39D8EA);
        int base = theme == null ? 0xFF132D60 : parseColor(theme.backgroundColor, 0xFF132D60);
        return drawable(focused ? blend(base, accent, .55f) : blend(base, Color.BLACK, .18f), focused ? Color.WHITE : accent, scaled(focused ? 5 : 2), scaled(15));
    }
    private int blend(int first, int second, float amount) { float a = Math.max(0f, Math.min(1f, amount)); return Color.rgb(Math.round(Color.red(first) * (1f - a) + Color.red(second) * a), Math.round(Color.green(first) * (1f - a) + Color.green(second) * a), Math.round(Color.blue(first) * (1f - a) + Color.blue(second) * a)); }
    private int scaled(int value) { return Math.max(1, Math.round(value * uiScale)); }
    private GradientDrawable drawable(int color, int strokeColor, int strokeWidth, int radius) { GradientDrawable background = new GradientDrawable(); background.setColor(color); background.setCornerRadius(radius); background.setStroke(strokeWidth, strokeColor); return background; }

    private List<Game> readGames() {
        games.clear();
        for (CatalogStore.CatalogGame catalogGame : CatalogStore.load(this, new File("/sdcard/Android/data/com.kiver.fireretro/files/catalog/games.json"))) {
            games.add(new Game(catalogGame.label, StoragePaths.remapRomPath(catalogGame.path), catalogGame.corePath, catalogGame.platform, catalogGame.image, catalogGame.downloadedAt, catalogGame.cloudId, catalogGame.remoteAvailable, catalogGame.size));
        }
        return games;
    }
    private boolean externalCatalogChanged() {
        long modified = EXTERNAL_CATALOG_FILE.isFile() ? EXTERNAL_CATALOG_FILE.lastModified() : -1L;
        long length = EXTERNAL_CATALOG_FILE.isFile() ? EXTERNAL_CATALOG_FILE.length() : -1L;
        return modified != catalogLastModified || length != catalogLength;
    }
    private void rememberExternalCatalogVersion() {
        catalogLastModified = EXTERNAL_CATALOG_FILE.isFile() ? EXTERNAL_CATALOG_FILE.lastModified() : -1L;
        catalogLength = EXTERNAL_CATALOG_FILE.isFile() ? EXTERNAL_CATALOG_FILE.length() : -1L;
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
        if (game.remoteAvailable || !new File(game.path).isFile()) {
            if (game.cloudId.matches("[A-Fa-f0-9-]{36}")) { installRemoteGame(game); return; }
            Toast.makeText(this, "O arquivo deste jogo não foi encontrado.", Toast.LENGTH_LONG).show(); return;
        }
        Intent intent = new Intent(); intent.setComponent(new ComponentName(RETROARCH, "com.retroarch.browser.retroactivity.RetroActivityFuture")); intent.putExtra("ROM", game.path); intent.putExtra("LIBRETRO", game.core); intent.putExtra("CONFIGFILE", RETROARCH_CONFIG); startActivity(intent);
    }
    private void installRemoteGame(final Game game) {
        String size = game.size > 0 ? String.format(java.util.Locale.getDefault(), "%.1f MB", game.size / 1048576d) : "tamanho não informado";
        new AlertDialog.Builder(this).setTitle("Instalar " + game.label + "?").setMessage("Download: " + size + "\nO jogo será verificado e salvo preferencialmente no pendrive.")
                .setNegativeButton("CANCELAR", null).setPositiveButton("INSTALAR", (dialog, which) -> {
                    String token = RemoteLibrarySettings.loadCloudDeviceToken(this); if (token.isEmpty()) { showRemoteSettings(); return; }
                    if (cloudSync == null) cloudSync = new CloudLibrarySync(new CloudDeviceClient(this, CloudApiEndpoint.configuredOrigin()), StoragePaths.romRoot(), EXTERNAL_CATALOG_FILE, new File(COVER_DIRECTORY));
                    if (remoteStatusView != null) remoteStatusView.setText("PREPARANDO DOWNLOAD: " + game.label);
                    cloudSync.installItem(token, game.cloudId, 350L * 1024L * 1024L, new CloudLibrarySync.Listener() {
                        @Override public void onProgress(String id, String label, long done, long total, int remaining) { runOnUiThread(() -> { if (remoteStatusView != null) remoteStatusView.setText("BAIXANDO: " + label + "  " + (total > 0 ? done * 100 / total : 0) + "%"); }); }
                        @Override public void onItemInstalled(String id, String label) { runOnUiThread(() -> { if (remoteStatusView != null) remoteStatusView.setText("INSTALADO: " + label); buildScreen(); }); }
                        @Override public void onError(String id, String label, String message) { runOnUiThread(() -> { if (remoteStatusView != null) remoteStatusView.setText("DOWNLOAD: " + message); Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show(); }); }
                        @Override public void onIdle(String revision, int pendingCount) { }
                    });
                }).show();
    }
    private static class Slide { final String image, title, caption; Slide(String image, String title, String caption) { this.image = image; this.title = title; this.caption = caption; } }
    private static class Game { final String label, path, core, platform, image, cloudId; final long downloadedAt, size; final boolean remoteAvailable; Game(String label, String path, String core, String platform, String image, long downloadedAt, String cloudId, boolean remoteAvailable, long size) { this.label = label; this.path = path; this.core = core; this.platform = platform; this.image = image; this.downloadedAt = downloadedAt; this.cloudId = cloudId == null ? "" : cloudId; this.remoteAvailable = remoteAvailable; this.size = size; } }
}
