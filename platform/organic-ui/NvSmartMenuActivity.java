package app.organicmaps;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * NV Persian smart-navigation shell placed on top of the proven Organic Maps core.
 * The screens mirror the user's 22-screen reference while all map/search/navigation
 * hand-offs are delegated to Organic Maps rather than reimplementing its engine.
 */
public class NvSmartMenuActivity extends Activity {
  private static final int NAVY = Color.rgb(5, 20, 36);
  private static final int NAVY_2 = Color.rgb(7, 31, 52);
  private static final int PANEL = Color.rgb(11, 43, 70);
  private static final int PANEL_2 = Color.rgb(14, 57, 88);
  private static final int CYAN = Color.rgb(23, 217, 255);
  private static final int BLUE = Color.rgb(38, 134, 255);
  private static final int GREEN = Color.rgb(44, 220, 106);
  private static final int AMBER = Color.rgb(255, 184, 46);
  private static final int RED = Color.rgb(255, 64, 85);
  private static final int WHITE = Color.rgb(246, 251, 255);
  private static final int MUTED = Color.rgb(164, 187, 204);
  private static final int OUTLINE = Color.rgb(32, 101, 139);

  private SharedPreferences prefs;
  private int activeScreen = 0;

  private static final String[] TITLES = {
      "صفحه اصلی", "اطراف من", "اورژانس", "جزئیات مکان", "حالت مسیریابی", "هشدارهای مسیر",
      "داروخانه", "پارک و تفریح", "جستجوی هوشمند", "مقایسه مسیرها", "محدوده جستجو", "حالت اضطراری",
      "چت هوشمند سفر", "حالت عجله دارم", "مسیر ترکیبی", "تعویض هوشمند ایستگاه", "حرکت زنده مترو",
      "هماهنگی تاکسی", "اطمینان زمان رسیدن", "مقایسه زمان و هزینه", "راهنمای پیاده", "ترجیحات سفر هوشمند"
  };

  private static final String[] SUBTITLES = {
      "نقشه، جستجو، مسیر و سرویس‌های سریع", "دسته‌بندی و انتخاب سریع خدمات نزدیک", "نزدیک‌ترین خدمات درمانی و امدادی",
      "اطلاعات کامل، تماس، ذخیره و شروع مسیر", "راهنمای فعال مسیر و مانور بعدی", "تصادف، ترافیک، عملیات و محدودیت‌ها",
      "جستجوی داروخانه و خدمات سلامت", "فضای سبز، پارک و تفریح اطراف", "پیشنهادهای مقصد و جستجوی فارسی",
      "سریع، کم‌ترافیک و اقتصادی", "شعاع جستجو در اطراف شما یا مسیر", "SOS و دسترسی فوری به خدمات ضروری",
      "گفتگوی راهنما برای انتخاب بهترین شیوه سفر", "تمرکز روی کمترین زمان رسیدن", "ترکیب خودرو، تاکسی، مترو و پیاده",
      "پیشنهاد بهترین خروجی و ادامه مسیر", "وضعیت خطوط و برنامه ادامه سفر", "اتصال بخش پایانی مسیر به تاکسی",
      "ETA، بازه اطمینان و ریسک تأخیر", "انتخاب بر اساس زمان، هزینه و راحتی", "راهنمای قدم‌به‌قدم پیاده‌روی",
      "شخصی‌سازی تصمیم‌گیری مسیر"
  };

  private static final String[] ICONS = {
      "🗺", "📍", "🚑", "🏢", "🧭", "⚠", "💊", "🌳", "🔎", "🛣", "◎", "🆘",
      "💬", "⚡", "🔀", "🚉", "🚇", "🚕", "⏱", "💰", "🚶", "⚙"
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    prefs = getSharedPreferences("nv_smart_ui", MODE_PRIVATE);
    Window w = getWindow();
    w.setStatusBarColor(NAVY);
    w.setNavigationBarColor(NAVY);
    getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    showHome();
  }

  @Override
  public void onBackPressed() {
    if (activeScreen != 0) showHome();
    else super.onBackPressed();
  }

  private void showHome() {
    activeScreen = 0;
    LinearLayout page = pageRoot();
    page.addView(topHeader("NV", "مسیریاب هوشمند ایران", false));

    LinearLayout body = bodyColumn();
    body.addView(searchBar("کجا می‌خواهید بروید؟", () -> showScreen(9)));
    body.addView(heroCard());

    sectionTitle(body, "دسترسی سریع", "سرویس‌های پرکاربرد");
    addTwoColumnActions(body, new String[][] {
        {"🚑", "اورژانس", "خدمات درمانی", "3"}, {"💊", "داروخانه", "داروخانه‌های نزدیک", "7"},
        {"🌳", "پارک", "فضای سبز", "8"}, {"🚇", "مترو", "حمل‌ونقل عمومی", "17"},
        {"🚕", "تاکسی", "هماهنگی سفر", "18"}, {"🆘", "SOS", "حالت اضطراری", "12"}
    });

    sectionTitle(body, "۲۲ منوی مرجع", "همه بخش‌های تصویر مرجع در این نسخه قرار گرفته‌اند");
    for (int i = 0; i < TITLES.length; i += 2) {
      LinearLayout row = new LinearLayout(this);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(Gravity.CENTER);
      row.setLayoutParams(matchWrap());
      int left = i;
      row.addView(menuCard(left + 1, ICONS[left], TITLES[left], SUBTITLES[left], () -> showScreen(left + 1)), weightCard());
      if (i + 1 < TITLES.length) {
        int right = i + 1;
        row.addView(menuCard(right + 1, ICONS[right], TITLES[right], SUBTITLES[right], () -> showScreen(right + 1)), weightCard());
      } else {
        View spacer = new View(this);
        row.addView(spacer, weightCard());
      }
      body.addView(row);
    }

    body.addView(attributionCard());
    page.addView(scroll(body), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    page.addView(bottomDock());
    setContentView(page);
  }

  private void showScreen(int id) {
    if (id <= 0) { showHome(); return; }
    activeScreen = id;
    int idx = id - 1;
    LinearLayout page = pageRoot();
    page.addView(topHeader(ICONS[idx] + "  " + TITLES[idx], SUBTITLES[idx], true));
    LinearLayout body = bodyColumn();
    body.addView(screenHero(id, TITLES[idx], SUBTITLES[idx]));
    renderScreenBody(id, body);
    if (id != 12) {
      body.addView(primaryButton("باز کردن نقشه و ادامه", BLUE, this::openMap));
    }
    page.addView(scroll(body), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    page.addView(bottomDock());
    setContentView(page);
  }

  private void renderScreenBody(int id, LinearLayout body) {
    switch (id) {
      case 1: renderMainMap(body); break;
      case 2: renderNearby(body); break;
      case 3: renderEmergencyResults(body); break;
      case 4: renderPlaceDetails(body); break;
      case 5: renderNavigation(body); break;
      case 6: renderRouteAlerts(body); break;
      case 7: renderPharmacy(body); break;
      case 8: renderParks(body); break;
      case 9: renderSmartSearch(body); break;
      case 10: renderRouteCompare(body); break;
      case 11: renderRadius(body); break;
      case 12: renderSOS(body); break;
      case 13: renderTravelChat(body); break;
      case 14: renderHurry(body); break;
      case 15: renderCombinedRoute(body); break;
      case 16: renderSmartTransfer(body); break;
      case 17: renderMetro(body); break;
      case 18: renderTaxi(body); break;
      case 19: renderEta(body); break;
      case 20: renderCostCompare(body); break;
      case 21: renderWalk(body); break;
      case 22: renderPreferences(body); break;
      default: info(body, "بخش آماده است", "برای ادامه از نقشه اصلی استفاده کنید.", CYAN);
    }
  }

  private void renderMainMap(LinearLayout body) {
    sectionTitle(body, "کنترل سریع نقشه", "موتور واقعی Organic Maps در زیر این رابط اجرا می‌شود");
    chipRow(body, new String[] {"موقعیت من", "نقشه آفلاین", "جستجو", "مسیر", "نشانک‌ها"}, new Runnable[] {
        this::openMap, this::openMap, () -> showScreen(9), () -> showScreen(10), this::openMap
    });
    info(body, "موقعیت و GPS", "موقعیت واقعی، قطب‌نما و دنبال‌کردن حرکت توسط هسته Organic Maps مدیریت می‌شود.", GREEN);
    info(body, "نقشه آفلاین", "پس از دانلود منطقه، جستجو و مسیریابی بدون اینترنت نیز در دسترس است.", CYAN);
    info(body, "لایه هوشمند NV", "این صفحه و تمام ۲۲ منوی مرجع روی موتور نقشه پایدار سوار شده‌اند.", BLUE);
  }

  private void renderNearby(LinearLayout body) {
    sectionTitle(body, "اطراف من", "یک دسته را انتخاب کنید تا جستجوی واقعی روی نقشه باز شود");
    String[][] cats = {
        {"🚑","اورژانس","اورژانس"},{"🏥","بیمارستان","بیمارستان"},{"💊","داروخانه","داروخانه"},
        {"👮","پلیس","پلیس"},{"⛽","پمپ بنزین","پمپ بنزین"},{"🅿","پارکینگ","پارکینگ"},
        {"🍽","رستوران","رستوران"},{"☕","کافه","کافه"},{"🌳","پارک","پارک"},
        {"🏨","هتل","هتل"},{"🏧","خودپرداز","خودپرداز"},{"🛍","فروشگاه","فروشگاه"}
    };
    addSearchGrid(body, cats);
    info(body, "نتایج واقعی", "فاصله و جزئیات مکان‌ها از پایگاه داده نقشه نمایش داده می‌شود؛ این صفحه عدد ساختگی نشان نمی‌دهد.", GREEN);
  }

  private void renderEmergencyResults(LinearLayout body) {
    sectionTitle(body, "خدمات درمانی", "برای دریافت نزدیک‌ترین نتیجه، دسته را روی نقشه باز کنید");
    placeActionCard(body, "🚑", "اورژانس و آمبولانس", "جستجو در اطراف موقعیت فعلی", RED, () -> openSearch("اورژانس"));
    placeActionCard(body, "🏥", "بیمارستان", "بیمارستان‌های اطراف", BLUE, () -> openSearch("بیمارستان"));
    placeActionCard(body, "💊", "داروخانه", "داروخانه‌های فعال اطراف", GREEN, () -> openSearch("داروخانه"));
    placeActionCard(body, "🩺", "درمانگاه", "کلینیک و درمانگاه", CYAN, () -> openSearch("درمانگاه"));
    body.addView(primaryButton("نمایش خدمات درمانی روی نقشه", RED, () -> openSearch("بیمارستان")));
  }

  private void renderPlaceDetails(LinearLayout body) {
    sectionTitle(body, "جزئیات مکان", "پس از انتخاب مقصد، اطلاعات کامل Organic Maps نمایش داده می‌شود");
    info(body, "نام و نوع مکان", "نام فارسی/محلی، نوع مکان، آدرس و اطلاعات OSM", CYAN);
    addMiniStats(body, new String[][] {{"📍","آدرس"},{"☎","تماس"},{"🔖","ذخیره"},{"↗","مسیر"}});
    info(body, "اطلاعات تکمیلی", "ساعت کاری، وب‌سایت، شماره تماس و سایر داده‌ها در صورت وجود در نقشه قابل مشاهده است.", GREEN);
    body.addView(primaryButton("انتخاب یک مکان روی نقشه", BLUE, this::openMap));
  }

  private void renderNavigation(LinearLayout body) {
    sectionTitle(body, "راهنمای مسیر", "نمایش مانور بعدی، فاصله و زمان باقی‌مانده در نقشه");
    navigationBanner(body, "↱", "مانور بعدی", "راهنمای زنده پس از شروع مسیر در Organic Maps فعال می‌شود", CYAN);
    addMiniStats(body, new String[][] {{"⏱","زمان باقی‌مانده"},{"📏","فاصله"},{"🚗","سرعت"},{"🧭","جهت"}});
    info(body, "مسیریابی صوتی", "راهنمای صوتی، بازطراحی مسیر هنگام خروج از مسیر و نمایش تقاطع‌ها توسط موتور اصلی انجام می‌شود.", GREEN);
    body.addView(primaryButton("شروع انتخاب مقصد", BLUE, () -> openSearch("")));
  }

  private void renderRouteAlerts(LinearLayout body) {
    sectionTitle(body, "هشدارهای مسیر", "پنل مرجع برای رخدادهای مهم مسیر");
    alertCard(body, "🚗", "ترافیک سنگین", "در صورت وجود داده ترافیکی، روی مسیر بررسی می‌شود.", RED);
    alertCard(body, "🚧", "عملیات عمرانی", "محدودیت‌های ثبت‌شده در داده نقشه در انتخاب مسیر لحاظ می‌شوند.", AMBER);
    alertCard(body, "⛔", "بسته بودن مسیر", "از مسیرهای بسته یا غیرقابل عبور اجتناب می‌شود.", RED);
    alertCard(body, "🚘", "خودروی متوقف", "این مورد به داده زنده وابسته است و در صورت نبود منبع معتبر نمایش داده نمی‌شود.", CYAN);
    info(body, "اصل عدم جعل داده", "NV رویداد لحظه‌ای ساختگی تولید نمی‌کند؛ هشدارها فقط وقتی منبع واقعی داشته باشند معتبرند.", GREEN);
  }

  private void renderPharmacy(LinearLayout body) {
    sectionTitle(body, "داروخانه", "فیلتر سریع برای پیدا کردن داروخانه");
    chipRow(body, new String[] {"نزدیک‌ترین", "شبانه‌روزی", "در مسیر", "اطراف مقصد"}, new Runnable[] {
        () -> openSearch("داروخانه"), () -> openSearch("داروخانه شبانه روزی"), () -> openSearch("داروخانه"), () -> openSearch("داروخانه")
    });
    placeActionCard(body, "💊", "جستجوی داروخانه", "نمایش روی نقشه + فاصله + مسیر", GREEN, () -> openSearch("داروخانه"));
    info(body, "ساعت کاری", "اگر ساعت کاری در داده مکان ثبت شده باشد، در صفحه جزئیات Organic Maps نمایش داده می‌شود.", CYAN);
  }

  private void renderParks(LinearLayout body) {
    sectionTitle(body, "پارک و تفریح", "فضای سبز و گزینه‌های تفریحی اطراف");
    String[][] cats = {{"🌳","پارک","پارک"},{"🛝","زمین بازی","زمین بازی"},{"🏞","فضای سبز","فضای سبز"},{"🏟","ورزش","ورزشگاه"}};
    addSearchGrid(body, cats);
    info(body, "مناسب خانواده", "برای جزئیات، مسیر، فاصله و امکانات هر مکان آن را روی نقشه انتخاب کنید.", GREEN);
  }

  private void renderSmartSearch(LinearLayout body) {
    sectionTitle(body, "جستجوی هوشمند", "نام، نوع مکان یا عبارت فارسی را وارد کنید");
    EditText edit = new EditText(this);
    edit.setHint("مثلاً بیمارستان، پارک ملت، میدان آزادی…");
    edit.setHintTextColor(MUTED);
    edit.setTextColor(WHITE);
    edit.setSingleLine(true);
    edit.setTextDirection(View.TEXT_DIRECTION_RTL);
    edit.setBackground(rounded(PANEL_2, OUTLINE, 18));
    edit.setPadding(dp(14), dp(12), dp(14), dp(12));
    LinearLayout.LayoutParams ep = matchWrap(); ep.setMargins(dp(6), dp(8), dp(6), dp(10)); edit.setLayoutParams(ep);
    body.addView(edit);
    chipRow(body, new String[] {"رستوران در مسیر", "بیمارستان", "پمپ بنزین", "پارکینگ", "مترو"}, new Runnable[] {
        () -> openSearch("رستوران"), () -> openSearch("بیمارستان"), () -> openSearch("پمپ بنزین"), () -> openSearch("پارکینگ"), () -> openSearch("مترو")
    });
    body.addView(primaryButton("جستجو روی نقشه", BLUE, () -> openSearch(edit.getText().toString().trim())));
    info(body, "جستجوی فارسی", "هسته Organic Maps نام مکان، دسته‌بندی و داده آفلاین دانلودشده را جستجو می‌کند.", CYAN);
  }

  private void renderRouteCompare(LinearLayout body) {
    sectionTitle(body, "مقایسه مسیرها", "نمای مرجع برای انتخاب بین گزینه‌ها");
    routeOption(body, "۱", "سریع‌ترین", "اولویت زمان کمتر", "⚡", CYAN);
    routeOption(body, "۲", "متعادل", "تعادل زمان و مسیر", "⚖", GREEN);
    routeOption(body, "۳", "جایگزین", "گزینه جایگزین در صورت وجود", "↗", AMBER);
    info(body, "انتخاب نهایی", "مسیرهای واقعی فقط پس از تعیین مبدأ و مقصد توسط موتور مسیریابی محاسبه می‌شوند.", GREEN);
    body.addView(primaryButton("تعیین مقصد و محاسبه مسیر", BLUE, () -> openSearch("")));
  }

  private void renderRadius(LinearLayout body) {
    sectionTitle(body, "محدوده جستجو", "شعاع را انتخاب کنید و سپس دسته را باز کنید");
    final TextView selected = label("شعاع انتخابی: ۲ کیلومتر", 16, WHITE, true);
    selected.setBackground(rounded(PANEL_2, OUTLINE, 16)); selected.setPadding(dp(12), dp(12), dp(12), dp(12));
    body.addView(selected);
    String[] radii = {"۵۰۰ متر", "۱ km", "۲ km", "۵ km", "۱۰ km"};
    Runnable[] acts = new Runnable[radii.length];
    for (int i = 0; i < radii.length; i++) { final String r = radii[i]; acts[i] = () -> selected.setText("شعاع انتخابی: " + r); }
    chipRow(body, radii, acts);
    chipRow(body, new String[] {"اورژانس", "در مسیر", "نزدیک مقصد", "همه دسته‌ها"}, new Runnable[] {
        () -> openSearch("اورژانس"), this::openMap, this::openMap, () -> showScreen(2)
    });
    info(body, "شعاع هوشمند", "این انتخاب در رابط NV نگهداری می‌شود؛ نتیجه واقعی توسط جستجوی نقشه ارائه می‌شود.", CYAN);
  }

  private void renderSOS(LinearLayout body) {
    sectionTitle(body, "حالت اضطراری", "برای تماس مستقیم از شماره‌های رسمی محل خود استفاده کنید");
    TextView sos = label("SOS", 42, WHITE, true);
    sos.setGravity(Gravity.CENTER); sos.setMinHeight(dp(150)); sos.setBackground(rounded(RED, Color.rgb(255,100,115), 75));
    LinearLayout.LayoutParams sp = matchWrap(); sp.setMargins(dp(50), dp(12), dp(50), dp(16)); sos.setLayoutParams(sp);
    sos.setOnClickListener(v -> Toast.makeText(this, "یک سرویس اضطراری را از پایین انتخاب کنید", Toast.LENGTH_SHORT).show());
    body.addView(sos);
    addTwoColumnActions(body, new String[][] {
        {"🚑","اورژانس پزشکی","تماس ۱۱۵","dial:115"}, {"🚒","آتش‌نشانی","تماس ۱۲۵","dial:125"},
        {"👮","پلیس","تماس ۱۱۰","dial:110"}, {"🗺","نقشه","نمایش موقعیت","map"}
    });
    info(body, "توجه", "شماره‌ها در ایران رایج‌اند. پیش از تماس، از شماره اضطراری معتبر منطقه‌ای که در آن هستید مطمئن شوید.", AMBER);
  }

  private void renderTravelChat(LinearLayout body) {
    sectionTitle(body, "چت هوشمند سفر", "دستیار محلی برای انتخاب سناریوی سفر");
    LinearLayout chat = new LinearLayout(this); chat.setOrientation(LinearLayout.VERTICAL); chat.setPadding(dp(10), dp(10), dp(10), dp(10)); chat.setBackground(rounded(PANEL, OUTLINE, 18));
    TextView assistant = bubble("NV: مقصد و اولویت شما چیست؟ زمان، هزینه یا راحتی؟", false); chat.addView(assistant);
    body.addView(chat);
    chipRow(body, new String[] {"عجله دارم", "کم‌هزینه", "بدون خودرو", "پیاده کمتر"}, new Runnable[] {
        () -> addChatReply(chat, "عجله دارم", "پیشنهاد: صفحه «عجله دارم» را باز کن و سپس مقصد را روی نقشه مشخص کن.", 14),
        () -> addChatReply(chat, "کم‌هزینه", "پیشنهاد: مقایسه زمان و هزینه را بررسی کن؛ حمل‌ونقل عمومی و پیاده را در اولویت بگذار.", 20),
        () -> addChatReply(chat, "بدون خودرو", "پیشنهاد: مسیر ترکیبی مترو + پیاده را بررسی کن.", 15),
        () -> addChatReply(chat, "پیاده کمتر", "پیشنهاد: مسیر ترکیبی با تاکسی برای بخش ابتدایی/انتهایی مناسب‌تر است.", 15)
    });
    EditText input = new EditText(this); input.setHint("پیام سفر…"); input.setHintTextColor(MUTED); input.setTextColor(WHITE); input.setSingleLine(true); input.setBackground(rounded(PANEL_2, OUTLINE, 16)); input.setPadding(dp(12), dp(10), dp(12), dp(10)); body.addView(input);
    body.addView(primaryButton("ارسال", BLUE, () -> {
      String q = input.getText().toString().trim(); if (q.isEmpty()) return;
      addChatReply(chat, q, "برای نتیجه دقیق مقصد را در نقشه انتخاب کنید. NV مسیر واقعی را به Organic Maps می‌سپارد.", 10); input.setText("");
    }));
  }

  private void renderHurry(LinearLayout body) {
    sectionTitle(body, "من عجله دارم", "سریع‌ترین تصمیم ممکن با حداقل مراحل");
    info(body, "⚡ حالت سریع", "یک مقصد انتخاب کنید؛ سپس کوتاه‌ترین گزینه زمانی موجود را از مسیرهای واقعی برگزینید.", CYAN);
    routeOption(body, "A", "خودرو", "مناسب برای مسیر مستقیم", "🚗", BLUE);
    routeOption(body, "B", "مترو + تاکسی", "مناسب برای ازدحام شهری", "🚇", GREEN);
    routeOption(body, "C", "تاکسی", "سرویس درب تا درب", "🚕", AMBER);
    body.addView(primaryButton("انتخاب فوری مقصد", RED, () -> openSearch("")));
  }

  private void renderCombinedRoute(LinearLayout body) {
    sectionTitle(body, "مسیر ترکیبی", "نمای زمانی مرحله‌به‌مرحله");
    timeline(body, "۱", "🚕 تاکسی تا ایستگاه", "بخش اول سفر", BLUE);
    timeline(body, "۲", "🚇 مترو", "بخش اصلی مسیر", CYAN);
    timeline(body, "۳", "🚶 پیاده تا مقصد", "بخش پایانی", GREEN);
    info(body, "محاسبه واقعی", "اتصال کامل حمل‌ونقل عمومی به موجود بودن داده ترانزیت در منطقه وابسته است.", AMBER);
    body.addView(primaryButton("جستجوی ایستگاه و مقصد", BLUE, () -> openSearch("مترو")));
  }

  private void renderSmartTransfer(LinearLayout body) {
    sectionTitle(body, "تعویض هوشمند ایستگاه", "پیشنهاد خروجی و ادامه مسیر");
    info(body, "✅ خروجی پیشنهادی", "خروجی‌ای را انتخاب کنید که کمترین پیاده‌روی و ساده‌ترین ادامه مسیر را دارد.", GREEN);
    placeActionCard(body, "🚇", "ایستگاه بعدی", "برای جزئیات و ورودی/خروجی‌ها روی نقشه بررسی کنید", CYAN, () -> openSearch("ایستگاه مترو"));
    info(body, "پیاده‌روی بعد از خروج", "راهنمای پیاده در صفحه ۲۱ برای بخش پایانی طراحی شده است.", BLUE);
  }

  private void renderMetro(LinearLayout body) {
    sectionTitle(body, "حرکت زنده مترو", "نمای مرجع وضعیت خط و ادامه سفر");
    info(body, "🚇 وضعیت خط", "اطلاعات زنده فقط در صورت وجود منبع معتبر حمل‌ونقل شهری قابل نمایش است.", GREEN);
    addMiniStats(body, new String[][] {{"🚉","ایستگاه بعد"},{"🔁","تعویض خط"},{"🚶","پیاده"},{"⏱","ETA"}});
    placeActionCard(body, "M", "ایستگاه مترو", "یافتن نزدیک‌ترین ایستگاه روی نقشه", BLUE, () -> openSearch("مترو"));
    info(body, "بدون داده ساختگی", "اگر فید زنده شهر در دسترس نباشد، NV زمان قطار جعلی نمایش نمی‌دهد.", AMBER);
  }

  private void renderTaxi(LinearLayout body) {
    sectionTitle(body, "هماهنگی تاکسی", "پایان مسیر را به سرویس تاکسی متصل کنید");
    info(body, "🚕 نقطه سوار شدن", "ابتدا روی نقشه نقطه مناسب سوار شدن و مقصد را بررسی کنید.", BLUE);
    info(body, "مسیر مشترک", "آدرس/مختصات مقصد از نقشه قابل اشتراک با اپ‌های تاکسی نصب‌شده روی گوشی است.", CYAN);
    body.addView(primaryButton("باز کردن مقصد روی نقشه", BLUE, this::openMap));
    body.addView(secondaryButton("اشتراک مقصد از داخل نقشه", "پس از انتخاب مکان از گزینه Share استفاده کنید"));
  }

  private void renderEta(LinearLayout body) {
    sectionTitle(body, "اطمینان زمان رسیدن", "نمایش ETA و عوامل اثرگذار");
    TextView score = label("۸۶٪\nاطمینان", 30, WHITE, true); score.setGravity(Gravity.CENTER); score.setMinHeight(dp(145)); score.setBackground(rounded(BLUE, CYAN, 72)); LinearLayout.LayoutParams p = matchWrap(); p.setMargins(dp(65),dp(8),dp(65),dp(12)); score.setLayoutParams(p); body.addView(score);
    alertCard(body, "🚦", "ترافیک و کندی مسیر", "عامل احتمالی تغییر ETA", AMBER);
    alertCard(body, "🌧", "وضعیت آب‌وهوا", "در صورت اتصال منبع آب‌وهوا قابل بررسی", CYAN);
    alertCard(body, "↻", "بازطراحی مسیر", "در صورت انحراف، مسیر مجدداً محاسبه می‌شود", GREEN);
    info(body, "عدد نمایشی", "۸۶٪ فقط عنصر رابط مطابق تصویر مرجع است؛ ETA واقعی داخل نقشه از مسیر واقعی محاسبه می‌شود.", AMBER);
  }

  private void renderCostCompare(LinearLayout body) {
    sectionTitle(body, "مقایسه زمان و هزینه", "چهار شیوه سفر را کنار هم ببینید");
    compareMode(body, "🚗", "خودرو", "زمان واقعی از مسیر نقشه", "هزینه سوخت وابسته به تنظیمات", BLUE);
    compareMode(body, "🚇", "حمل‌ونقل عمومی", "وابسته به داده ترانزیت", "معمولاً اقتصادی‌تر", GREEN);
    compareMode(body, "🚕", "تاکسی", "زمان وابسته به سرویس", "کرایه از اپ تاکسی", AMBER);
    compareMode(body, "🚶", "پیاده", "مناسب فاصله کوتاه", "بدون هزینه", CYAN);
    info(body, "شفافیت هزینه", "NV کرایه یا قیمت لحظه‌ای را بدون اتصال رسمی به ارائه‌دهنده تخمین قطعی نمی‌زند.", GREEN);
  }

  private void renderWalk(LinearLayout body) {
    sectionTitle(body, "راهنمای پیاده", "مرحله پایانی تا مقصد");
    navigationBanner(body, "↱", "۱۵۰ متر جلو بروید", "نمونه رابط مرجع — دستور واقعی پس از شروع مسیر نمایش داده می‌شود", CYAN);
    addMiniStats(body, new String[][] {{"🚶","پیاده"},{"📏","فاصله"},{"⏱","زمان"},{"🗺","نقشه"}});
    info(body, "مسیریابی پیاده", "برای مسیر واقعی، مقصد را انتخاب و حالت پیاده را در موتور نقشه فعال کنید.", GREEN);
    body.addView(primaryButton("باز کردن نقشه پیاده", BLUE, this::openMap));
  }

  private void renderPreferences(LinearLayout body) {
    sectionTitle(body, "ترجیحات سفر هوشمند", "انتخاب‌های شما روی دستگاه ذخیره می‌شوند");
    addPrefSwitch(body, "استفاده از مترو در مسیرهای ترکیبی", "pref_metro", true, "🚇");
    addPrefSwitch(body, "استفاده از تاکسی", "pref_taxi", true, "🚕");
    addPrefSwitch(body, "به حداقل رساندن هزینه", "pref_cost", false, "💰");
    addPrefSwitch(body, "سریع‌ترین مسیر را پیشنهاد بده", "pref_fast", true, "⚡");
    addPrefSwitch(body, "پیاده‌روی کمتر", "pref_walk_less", false, "🚶");
    addPrefSwitch(body, "مسیرهای کم‌ترافیک", "pref_traffic", true, "🛣");
    addPrefSwitch(body, "هشدارهای ایمنی", "pref_safety", true, "🛡");
    body.addView(primaryButton("ذخیره تنظیمات", BLUE, () -> Toast.makeText(this, "تنظیمات روی دستگاه ذخیره شد", Toast.LENGTH_SHORT).show()));
  }

  private LinearLayout pageRoot() {
    LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(NAVY); root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); root.setFitsSystemWindows(true); return root;
  }

  private LinearLayout bodyColumn() {
    LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(8), dp(8), dp(8), dp(18)); return body;
  }

  private ScrollView scroll(View child) {
    ScrollView sc = new ScrollView(this); sc.setFillViewport(true); sc.setBackgroundColor(NAVY); sc.addView(child); return sc;
  }

  private View topHeader(String title, String sub, boolean back) {
    LinearLayout bar = new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(8),dp(9),dp(8),dp(9)); bar.setBackground(rounded(NAVY_2, OUTLINE, 0));
    if (back) {
      Button b = tinyButton("‹"); b.setOnClickListener(v -> showHome()); bar.addView(b, new LinearLayout.LayoutParams(dp(48),dp(48)));
    }
    LinearLayout texts = new LinearLayout(this); texts.setOrientation(LinearLayout.VERTICAL); texts.setGravity(Gravity.RIGHT); texts.setPadding(dp(8),0,dp(8),0);
    TextView t = label(title, 20, WHITE, true); TextView s = label(sub, 12, MUTED, false); texts.addView(t); texts.addView(s); bar.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    Button map = tinyButton("🗺"); map.setOnClickListener(v -> openMap()); bar.addView(map, new LinearLayout.LayoutParams(dp(48),dp(48)));
    return bar;
  }

  private View searchBar(String hint, Runnable action) {
    LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.HORIZONTAL); box.setGravity(Gravity.CENTER_VERTICAL); box.setPadding(dp(12),dp(11),dp(12),dp(11)); box.setBackground(rounded(Color.WHITE, Color.rgb(220,230,238), 20)); LinearLayout.LayoutParams p = matchWrap(); p.setMargins(dp(4),dp(3),dp(4),dp(9)); box.setLayoutParams(p);
    TextView mic = label("🎙",22,NAVY,true); box.addView(mic); TextView q = label(hint,15,Color.rgb(50,65,78),true); q.setPadding(dp(8),0,dp(8),0); box.addView(q,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)); TextView sr = label("🔎",22,BLUE,true); box.addView(sr); box.setOnClickListener(v -> action.run()); return box;
  }

  private View heroCard() {
    LinearLayout hero = new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setPadding(dp(16),dp(18),dp(16),dp(18)); hero.setBackground(gradient(PANEL_2, NAVY_2, CYAN, 24)); LinearLayout.LayoutParams p = matchWrap(); p.setMargins(dp(4),dp(3),dp(4),dp(12)); hero.setLayoutParams(p);
    TextView title = label("🧭  NV — نقشه و مسیریابی هوشمند",21,WHITE,true); hero.addView(title); TextView sub = label("هسته واقعی Organic Maps + رابط فارسی مرجع",13,MUTED,false); sub.setPadding(0,dp(5),0,dp(10)); hero.addView(sub);
    LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER);
    row.addView(statPill("آفلاین", "✓"), new LinearLayout.LayoutParams(0,dp(62),1f)); row.addView(statPill("GPS", "●"), new LinearLayout.LayoutParams(0,dp(62),1f)); row.addView(statPill("مسیریابی", "↗"), new LinearLayout.LayoutParams(0,dp(62),1f)); hero.addView(row);
    Button open = primaryButton("باز کردن نقشه اصلی", BLUE, this::openMap); hero.addView(open); return hero;
  }

  private View screenHero(int id, String title, String sub) {
    LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16),dp(14),dp(16),dp(14)); int accent = id==12||id==3?RED:(id==7||id==8?GREEN:(id==14?AMBER:CYAN)); card.setBackground(gradient(PANEL_2,NAVY_2,accent,22)); LinearLayout.LayoutParams p=matchWrap();p.setMargins(dp(4),dp(3),dp(4),dp(12));card.setLayoutParams(p);
    TextView n=label(String.format("%02d",id),12,accent,true); card.addView(n); TextView t=label(title,24,WHITE,true); card.addView(t); TextView s=label(sub,14,MUTED,false); s.setPadding(0,dp(5),0,0); card.addView(s); return card;
  }

  private View menuCard(int n, String icon, String title, String sub, Runnable action) {
    LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(12),dp(12),dp(12),dp(12)); c.setMinHeight(dp(134)); c.setBackground(rounded(PANEL, OUTLINE, 18)); LinearLayout.LayoutParams mp=weightCard();mp.setMargins(dp(4),dp(4),dp(4),dp(4));c.setLayoutParams(mp);
    TextView top=label(String.format("%02d   %s",n,icon),18,CYAN,true);c.addView(top); TextView t=label(title,16,WHITE,true);t.setPadding(0,dp(7),0,0);c.addView(t); TextView s=label(sub,11,MUTED,false);s.setMaxLines(2);s.setEllipsize(TextUtils.TruncateAt.END);s.setPadding(0,dp(4),0,0);c.addView(s); c.setOnClickListener(v->action.run()); return c;
  }

  private void sectionTitle(LinearLayout body, String title, String sub) {
    TextView t=label(title,18,WHITE,true);t.setPadding(dp(6),dp(10),dp(6),0);body.addView(t); TextView s=label(sub,12,MUTED,false);s.setPadding(dp(6),dp(2),dp(6),dp(7));body.addView(s);
  }

  private void info(LinearLayout body, String title, String text, int accent) {
    LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(13),dp(12),dp(13),dp(12));c.setBackground(rounded(PANEL,accent,16));LinearLayout.LayoutParams p=matchWrap();p.setMargins(dp(4),dp(5),dp(4),dp(5));c.setLayoutParams(p);c.addView(label(title,15,WHITE,true));TextView d=label(text,13,MUTED,false);d.setPadding(0,dp(5),0,0);c.addView(d);body.addView(c);
  }

  private void alertCard(LinearLayout body,String icon,String title,String text,int accent){info(body,icon+"  "+title,text,accent);}

  private void placeActionCard(LinearLayout body,String icon,String title,String text,int accent,Runnable action){
    LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(12),dp(12),dp(12));row.setBackground(rounded(PANEL,accent,17));LinearLayout.LayoutParams p=matchWrap();p.setMargins(dp(4),dp(5),dp(4),dp(5));row.setLayoutParams(p);TextView ic=label(icon,28,WHITE,true);ic.setGravity(Gravity.CENTER);row.addView(ic,new LinearLayout.LayoutParams(dp(52),dp(52)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.addView(label(title,16,WHITE,true));tx.addView(label(text,12,MUTED,false));row.addView(tx,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));TextView arrow=label("‹",30,accent,true);row.addView(arrow);row.setOnClickListener(v->action.run());body.addView(row);
  }

  private void routeOption(LinearLayout body,String num,String title,String text,String icon,int accent){placeActionCard(body,icon,title,text,accent,()->Toast.makeText(this,"گزینه «"+title+"» انتخاب شد",Toast.LENGTH_SHORT).show());}

  private void compareMode(LinearLayout body,String icon,String title,String line1,String line2,int accent){
    LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(12),dp(12),dp(12),dp(12));c.setBackground(rounded(PANEL,accent,16));LinearLayout.LayoutParams p=matchWrap();p.setMargins(dp(4),dp(5),dp(4),dp(5));c.setLayoutParams(p);c.addView(label(icon+"  "+title,17,WHITE,true));c.addView(label(line1,12,MUTED,false));c.addView(label(line2,12,accent,true));body.addView(c);
  }

  private void navigationBanner(LinearLayout body,String arrow,String title,String sub,int accent){
    LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(14),dp(14),dp(14),dp(14));r.setBackground(rounded(PANEL_2,accent,20));LinearLayout.LayoutParams p=matchWrap();p.setMargins(dp(4),dp(6),dp(4),dp(8));r.setLayoutParams(p);TextView a=label(arrow,48,WHITE,true);a.setGravity(Gravity.CENTER);r.addView(a,new LinearLayout.LayoutParams(dp(78),dp(78)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.addView(label(title,19,WHITE,true));tx.addView(label(sub,12,MUTED,false));r.addView(tx,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));body.addView(r);
  }

  private void timeline(LinearLayout body,String n,String title,String sub,int accent){
    LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);TextView num=label(n,16,NAVY,true);num.setGravity(Gravity.CENTER);num.setBackground(rounded(accent,accent,24));r.addView(num,new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.setPadding(dp(10),0,dp(10),0);tx.addView(label(title,15,WHITE,true));tx.addView(label(sub,12,MUTED,false));r.addView(tx,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));LinearLayout.LayoutParams p=matchWrap();p.setMargins(dp(8),dp(7),dp(8),dp(7));r.setLayoutParams(p);body.addView(r);
  }

  private void addMiniStats(LinearLayout body,String[][] items){
    LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER);for(String[] it:items){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(5),dp(8),dp(5),dp(8));c.setBackground(rounded(PANEL,OUTLINE,14));c.addView(label(it[0],20,WHITE,true));TextView t=label(it[1],10,MUTED,true);t.setGravity(Gravity.CENTER);c.addView(t);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(78),1f);p.setMargins(dp(3),dp(4),dp(3),dp(4));row.addView(c,p);}body.addView(row);
  }

  private void chipRow(LinearLayout body,String[] labels,Runnable[] acts){
    HorizontalScrollView sc=new HorizontalScrollView(this);sc.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(dp(2),dp(2),dp(2),dp(4));for(int i=0;i<labels.length;i++){Button b=tinyChip(labels[i]);final int j=i;b.setOnClickListener(v->{if(acts!=null&&j<acts.length&&acts[j]!=null)acts[j].run();});row.addView(b);}sc.addView(row);body.addView(sc);
  }

  private void addSearchGrid(LinearLayout body,String[][] cats){
    for(int i=0;i<cats.length;i+=2){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);for(int j=0;j<2;j++){if(i+j<cats.length){String[] c=cats[i+j];LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);card.setPadding(dp(8),dp(12),dp(8),dp(12));card.setBackground(rounded(PANEL,OUTLINE,16));card.addView(label(c[0],26,WHITE,true));TextView t=label(c[1],13,WHITE,true);t.setGravity(Gravity.CENTER);card.addView(t);card.setOnClickListener(v->openSearch(c[2]));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(94),1f);p.setMargins(dp(4),dp(4),dp(4),dp(4));row.addView(card,p);}else{row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(94),1f));}}body.addView(row);}
  }

  private void addTwoColumnActions(LinearLayout body,String[][] items){
    for(int i=0;i<items.length;i+=2){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);for(int j=0;j<2;j++){if(i+j<items.length){String[] it=items[i+j];LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(8),dp(10),dp(8),dp(10));c.setBackground(rounded(PANEL,OUTLINE,16));c.addView(label(it[0],28,WHITE,true));TextView t=label(it[1],14,WHITE,true);t.setGravity(Gravity.CENTER);c.addView(t);TextView s=label(it[2],10,MUTED,false);s.setGravity(Gravity.CENTER);c.addView(s);final String action=it[3];c.setOnClickListener(v->{if(action.startsWith("dial:"))dial(action.substring(5));else if(action.equals("map"))openMap();else{try{showScreen(Integer.parseInt(action));}catch(Exception e){openMap();}}});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(102),1f);p.setMargins(dp(4),dp(4),dp(4),dp(4));row.addView(c,p);}else row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(102),1f));}body.addView(row);}
  }

  private void addPrefSwitch(LinearLayout body,String title,String key,boolean def,String icon){
    LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(10),dp(12),dp(10));row.setBackground(rounded(PANEL,OUTLINE,15));LinearLayout.LayoutParams p=matchWrap();p.setMargins(dp(4),dp(4),dp(4),dp(4));row.setLayoutParams(p);row.addView(label(icon,22,WHITE,true));TextView t=label(title,14,WHITE,true);t.setPadding(dp(8),0,dp(8),0);row.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));Switch sw=new Switch(this);sw.setChecked(prefs.getBoolean(key,def));sw.setOnCheckedChangeListener((buttonView,isChecked)->prefs.edit().putBoolean(key,isChecked).apply());row.addView(sw);body.addView(row);
  }

  private TextView bubble(String text,boolean user){TextView v=label(text,13,WHITE,false);v.setPadding(dp(12),dp(9),dp(12),dp(9));v.setBackground(rounded(user?BLUE:PANEL_2,user?CYAN:OUTLINE,15));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.gravity=user?Gravity.LEFT:Gravity.RIGHT;p.setMargins(dp(4),dp(4),dp(4),dp(4));v.setLayoutParams(p);return v;}

  private void addChatReply(LinearLayout chat,String q,String answer,int screen){chat.addView(bubble("شما: "+q,true));TextView a=bubble("NV: "+answer,false);chat.addView(a);a.setOnClickListener(v->{if(screen>0)showScreen(screen);});}

  private View statPill(String label,String value){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setBackground(rounded(PANEL,OUTLINE,14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(60),1f);p.setMargins(dp(3),dp(2),dp(3),dp(2));c.setLayoutParams(p);TextView v=label(value,17,CYAN,true);v.setGravity(Gravity.CENTER);c.addView(v);TextView l=label(label,10,MUTED,true);l.setGravity(Gravity.CENTER);c.addView(l);return c;}

  private View attributionCard(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(12),dp(12),dp(12),dp(12));c.setBackground(rounded(Color.rgb(8,32,48),OUTLINE,16));LinearLayout.LayoutParams p=matchWrap();p.setMargins(dp(4),dp(16),dp(4),dp(4));c.setLayoutParams(p);c.addView(label("NV v0.21 — رابط مرجع + Organic Maps Core",13,WHITE,true));c.addView(label("داده نقشه: OpenStreetMap contributors • موتور: Organic Maps Project",11,MUTED,false));return c;}

  private View bottomDock(){
    LinearLayout dock=new LinearLayout(this);dock.setOrientation(LinearLayout.HORIZONTAL);dock.setGravity(Gravity.CENTER);dock.setPadding(dp(4),dp(5),dp(4),dp(5));dock.setBackground(rounded(Color.rgb(4,18,31),OUTLINE,0));String[] ls={"خانه","نقشه","جستجو","SOS","تنظیمات"};String[] is={"⌂","🗺","⌕","SOS","⚙"};for(int i=0;i<ls.length;i++){final int n=i;LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setGravity(Gravity.CENTER);TextView ic=label(is[i],18,n==3?RED:CYAN,true);ic.setGravity(Gravity.CENTER);b.addView(ic);TextView tx=label(ls[i],9,MUTED,true);tx.setGravity(Gravity.CENTER);b.addView(tx);b.setOnClickListener(v->{if(n==0)showHome();else if(n==1)openMap();else if(n==2)showScreen(9);else if(n==3)showScreen(12);else showScreen(22);});dock.addView(b,new LinearLayout.LayoutParams(0,dp(58),1f));}return dock;
  }

  private Button primaryButton(String text,int color,Runnable action){Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextColor(WHITE);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(rounded(color,color,16));LinearLayout.LayoutParams p=matchWrap();p.height=dp(52);p.setMargins(dp(4),dp(9),dp(4),dp(5));b.setLayoutParams(p);b.setOnClickListener(v->action.run());return b;}
  private View secondaryButton(String title,String sub){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(12),dp(10),dp(12),dp(10));c.setBackground(rounded(PANEL,OUTLINE,14));c.addView(label(title,14,WHITE,true));c.addView(label(sub,11,MUTED,false));LinearLayout.LayoutParams p=matchWrap();p.setMargins(dp(4),dp(5),dp(4),dp(5));c.setLayoutParams(p);return c;}
  private Button tinyButton(String text){Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextColor(WHITE);b.setTextSize(20);b.setPadding(0,0,0,0);b.setBackground(rounded(PANEL,OUTLINE,14));return b;}
  private Button tinyChip(String text){Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextColor(WHITE);b.setTextSize(12);b.setBackground(rounded(PANEL_2,OUTLINE,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(42));p.setMargins(dp(4),dp(2),dp(4),dp(2));b.setLayoutParams(p);return b;}

  private TextView label(String text,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(text);v.setTextColor(color);v.setTextSize(size);v.setTextDirection(View.TEXT_DIRECTION_RTL);v.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setLineSpacing(0f,1.12f);return v;}

  private void openMap(){
    try { Intent i=new Intent();i.setComponent(new ComponentName(getPackageName(),"app.organicmaps.DownloadResourcesActivity"));startActivity(i); }
    catch(Exception e){ try{startActivity(new Intent(this,SplashActivity.class));}catch(Exception ignored){Toast.makeText(this,"نقشه در دسترس نیست",Toast.LENGTH_SHORT).show();} }
  }

  private void openSearch(String query){
    try { String q=query==null?"":query.trim();Uri uri=Uri.parse("geo:0,0"+(q.isEmpty()?"":"?q="+Uri.encode(q)));Intent i=new Intent(Intent.ACTION_VIEW,uri);i.setPackage(getPackageName());startActivity(i); }
    catch(Exception e){openMap();}
  }

  private void dial(String number){try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+number)));}catch(Exception e){Toast.makeText(this,"شماره‌گیر در دسترس نیست",Toast.LENGTH_SHORT).show();}}

  private GradientDrawable rounded(int fill,int stroke,int radiusDp){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radiusDp));g.setStroke(dp(1),stroke);return g;}
  private GradientDrawable gradient(int start,int end,int stroke,int radiusDp){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{start,end});g.setCornerRadius(dp(radiusDp));g.setStroke(dp(1),stroke);return g;}
  private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
  private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
  private LinearLayout.LayoutParams weightCard(){return new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);}
}
