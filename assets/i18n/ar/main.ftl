# common
common-left = يسار
common-right = يمين
common-search = بحث
common-unknown = غير معروف
common-not-provided = غير متوفر
common-not-available = غير متاح
common-cancel = إلغاء
common-save = حفظ
common-delete = حذف
common-play = تشغيل
common-more = المزيد
common-previous = السابق
common-next = التالي
common-dismiss = إغلاق
common-clear = مسح
number-group = { "," }

# navigation
nav-history = السجل
nav-home = الرئيسية
nav-search = بحث
nav-settings = الإعدادات
nav-songs = الأغاني
nav-albums = الألبومات
nav-playlists = قوائم التشغيل
nav-artists = الفنانون
nav-local = مكتبتك
nav-radio = الراديو
radio-country = المغرب
radio-reload = إعادة التحميل
radio-not-loaded = تعذّر تحميل المحطات. تحقق من اتصالك ثم أعد التحميل.
radio-empty = لا توجد محطات حاليًا.
filter-radio = البحث في المحطات
count-stations =
    { $count ->
        [one] محطة واحدة
        [two] محطتان
       *[other] { $count } محطات
    }
nav-back = رجوع
nav-forward = تقدّم
nav-sidebar = إظهار الشريط الجانبي أو إخفاؤه
nav-sidebar-right = إظهار الكلمات وقائمة الانتظار أو إخفاؤها
nav-pinned = المثبّتة
nav-unpin = إلغاء التثبيت
nav-pin-hint = أفلِت هنا للتثبيت
library-liked-songs = المفضلة
library-play-liked-songs = تشغيل
library-no-local-songs = لم يُعثر على أغانٍ مستوردة
library-no-local-albums = لم يُعثر على ألبومات مستوردة
library-no-local-artists = لم يُعثر على فنانين مستوردين
library-no-local-playlists = لا توجد قوائم تشغيل محلية بعد
library-no-matches = لا توجد نتائج مطابقة
library-not-loaded = تعذّر تحميل مكتبتك
library-scanning = جارٍ البحث في مجلداتك…
library-scanning-progress = جارٍ قراءة موسيقاك… { $read } من { $found }
library-part-not-loaded = تعذّر تحميل هذا الجزء من مكتبتك
library-local-unconfigured = اضبط مكتبتك المحلية

# app menu
app-refresh = تحديث
app-quit = إنهاء
app-settings = الإعدادات…
app-hide = إخفاء Encore
app-hide-others = إخفاء الآخرين
app-show-all = إظهار الكل
app-edit = تحرير
app-cut = قص
app-copy = نسخ
app-paste = لصق
app-select-all = تحديد الكل
app-window = نافذة
app-close-window = إغلاق النافذة
app-minimize = تصغير
app-zoom = تكبير

# tray menu
tray-show = إظهار Encore
tray-play = تشغيل
tray-pause = إيقاف مؤقت

# table columns
column-played-at = وقت التشغيل
column-index = #
column-title = العنوان
column-artist = الفنان
column-album = الألبوم
column-date-added = تاريخ الإضافة
column-date-modified = تاريخ التعديل
column-format = الصيغة
format-value = { $codec } · { $quality }
filter-lossless = بدون فقدان فقط
column-added-by = أضافه
column-modified = آخر تعديل
column-length = المدة
column-plays = مرات التشغيل
column-name = الاسم
column-owner = المالك
column-year = السنة
column-tracks = المقاطع

# track menu
menu-add-to-playlist = إضافة إلى قائمة تشغيل
menu-add-tracks-to-playlist = { $count ->
    [one] إضافة مقطع واحد إلى قائمة تشغيل
   *[other] إضافة { $count } مقاطع إلى قائمة تشغيل
}
menu-new-playlist = قائمة تشغيل جديدة
menu-edit-tags = تعديل الوسوم
menu-no-playlists = لا توجد قوائم تشغيل
menu-search-playlists = البحث عن قائمة تشغيل
menu-add-to-library = إضافة إلى المفضلة
menu-add-tracks-to-library = { $count ->
    [one] إضافة مقطع واحد إلى المفضلة
   *[other] إضافة { $count } مقاطع إلى المفضلة
}
menu-remove-from-library = إزالة من المفضلة
menu-remove-tracks-from-library = { $count ->
    [one] إزالة مقطع واحد من المفضلة
   *[other] إزالة { $count } مقاطع من المفضلة
}
menu-remove-from-playlist = إزالة من قائمة التشغيل
menu-remove-tracks-from-playlist = { $count ->
    [one] إزالة مقطع واحد من قائمة التشغيل
   *[other] إزالة { $count } مقاطع من قائمة التشغيل
}
menu-remove-from-history = إزالة من السجل
menu-remove-tracks-from-history = { $count ->
    [one] إزالة مقطع واحد من السجل
   *[other] إزالة { $count } مقاطع من السجل
}
menu-delete-track-file = حذف ملف المقطع
menu-delete-track-files = حذف { $count } من ملفات المقاطع
menu-play-next = تشغيل تاليًا
menu-play-tracks-next = { $count ->
    [one] تشغيل مقطع واحد تاليًا
   *[other] تشغيل { $count } مقاطع تاليًا
}
menu-add-to-queue = إضافة إلى قائمة الانتظار
menu-add-tracks-to-queue = { $count ->
    [one] إضافة مقطع واحد إلى قائمة الانتظار
   *[other] إضافة { $count } مقاطع إلى قائمة الانتظار
}
menu-play-last = تشغيل أخيرًا
menu-play-tracks-last = { $count ->
    [one] تشغيل مقطع واحد أخيرًا
   *[other] تشغيل { $count } مقاطع أخيرًا
}
menu-song-radio = الانتقال إلى راديو الأغنية
menu-go-to-album = الانتقال إلى الألبوم
menu-go-to-artist = الانتقال إلى الفنان
menu-view-details = عرض التفاصيل
menu-copy-link = نسخ الرابط
menu-cut = قص
menu-copy = نسخ
menu-paste = لصق
menu-select-all = تحديد الكل
menu-remove-from-queue = إزالة من قائمة الانتظار
menu-rename-playlist = إعادة تسمية قائمة التشغيل
menu-delete-playlist = حذف قائمة التشغيل
menu-add-playlist-to-library = إضافة إلى المكتبة
menu-remove-playlist-from-library = إزالة من المكتبة
menu-library-add = إضافة إلى المكتبة
menu-library-add-tracks = { $count ->
    [one] إضافة مقطع واحد إلى المكتبة
   *[other] إضافة { $count } مقاطع إلى المكتبة
}
menu-library-remove = إزالة من المكتبة
menu-library-remove-tracks = { $count ->
    [one] إزالة مقطع واحد من المكتبة
   *[other] إزالة { $count } مقاطع من المكتبة
}
menu-make-playlist-public = جعلها عامة
menu-make-playlist-private = جعلها خاصة
menu-play-artist = تشغيل الفنان

# playlist editor
playlist-name-placeholder = اسم قائمة التشغيل
playlist-create-title = إنشاء قائمة تشغيل
playlist-rename-title = إعادة تسمية قائمة التشغيل
playlist-delete-title = حذف قائمة التشغيل
playlist-delete-confirm = حذف «{ $name }»؟ لا يمكن التراجع عن ذلك.
playlist-again-title = إضافته مرة أخرى؟
playlist-again-confirm = هذا المقطع موجود بالفعل في «{ $name }». هل تريد إضافة نسخة أخرى؟
playlist-again-add = إضافة مرة أخرى

# confirm
confirm-remove-library-title = إزالة من المكتبة
confirm-remove-playlist-title = إزالة من قائمة التشغيل
confirm-remove-history-title = إزالة من السجل
confirm-remove-songs = { $count ->
    [one] إزالة هذه الأغنية من مكتبتك؟
   *[other] إزالة { $count } أغانٍ من مكتبتك؟
}
confirm-remove-playlist-songs = { $count ->
    [one] إزالة هذه الأغنية من قائمة التشغيل؟
   *[other] إزالة { $count } أغانٍ من قائمة التشغيل؟
}
confirm-remove-history-songs = { $count ->
    [one] إزالة هذه الأغنية من سجل الاستماع؟
   *[other] إزالة { $count } أغانٍ من سجل الاستماع؟
}
confirm-remove-albums = { $count ->
    [one] إزالة هذا الألبوم من مكتبتك؟
   *[other] إزالة { $count } ألبومات من مكتبتك؟
}
confirm-remove-artists = { $count ->
    [one] إزالة هذا الفنان من المفضلة؟
   *[other] إزالة { $count } فنانين من المفضلة؟
}
confirm-remove-playlists = { $count ->
    [one] إزالة قائمة التشغيل هذه من مكتبتك؟
   *[other] إزالة { $count } قوائم تشغيل من مكتبتك؟
}
confirm-delete-track-files-title = حذف ملفات المقاطع؟
confirm-delete-track-files = ستُحذف الملفات من القرص نهائيًا. لا يمكن التراجع عن ذلك.

# queue panel
queue-title = قائمة الانتظار
queue-history = السجل
queue-now-playing = قيد التشغيل الآن
queue-from = من
queue-from-radio = راديو { $name }
queue-next-in-queue = التالي في قائمة الانتظار
queue-up-next = التالي
queue-reset = إعادة تعيين
queue-clear = مسح
queue-added-by = أضافه { $name }
queue-empty = قائمة الانتظار فارغة
queue-similar = مقاطع مشابهة
queue-radio = تشغيل مقاطع مشابهة تلقائيًا
queue-return-playing = العودة إلى ما يُشغَّل الآن

# player bar
player-nothing-playing = لا شيء قيد التشغيل
player-percent = { $value }٪
player-shuffle = ترتيب عشوائي
player-repeat = تكرار
player-repeat-all = تكرار الكل
player-repeat-one = تكرار مقطع واحد
player-mute = كتم الصوت
player-unmute = إلغاء كتم الصوت
player-previous = المقطع السابق
player-next = المقطع التالي
player-fullscreen = قيد التشغيل الآن
player-mini = المشغّل المصغّر
mini-player = المشغّل المصغّر
mini-player-progress = تقدّم الأغنية
app-mini-player = المشغّل المصغّر
app-check-updates = البحث عن تحديثات…
player-fullscreen-leave = مغادرة شاشة التشغيل
devices-jam-code-menu = إظهار رمز QR
fullscreen-artwork = الغلاف

# filters
filter-history = تصفية سجل الاستماع
history-empty = ستظهر هنا المقاطع التي تشغّلها.
history-not-loaded = تعذّر تحميل سجل الاستماع.
history-clear = مسح السجل
history-clear-title = مسح سجل الاستماع
history-clear-confirm = ستُزال كل عمليات التشغيل من هذا الجهاز. لا يمكن التراجع عن ذلك.
filter-library = تصفية مكتبتك
filter-album = تصفية مقاطع الألبوم
filter-reset = إعادة تعيين عوامل التصفية
filter-duration = المدة
filter-year = السنة
filter-hide-explicit = إخفاء المحتوى الصريح
filter-playable = القابلة للتشغيل فقط
filter-favorites = المفضلة فقط
filter-owned = من إنشائك

# view
view-list = قائمة
view-cards = شبكة

# toolbar
tool-sort = الترتيب
tool-filters = عوامل التصفية

# login

# album and playlist pages
detail-playlist = قائمة تشغيل
detail-play-album = تشغيل الألبوم
detail-play-playlist = تشغيل قائمة التشغيل

# play button
play-pause = إيقاف مؤقت
play-resume = استئناف
play-shuffle = ترتيب عشوائي

# artist page
artist-eyebrow = فنان
artist-monthly-listeners = { $count ->
    [one] { $value } مستمع شهري
   *[other] { $value } مستمعًا شهريًا
}
artist-play = تشغيل الآن
artist-popular = الأشهر
artist-popular-eyebrow = استكشف هذا الفنان
artist-popular-empty = لا يوجد ما يُشغَّل لهذا الفنان بعد
artist-popular-more = عرض الكل
artist-popular-less = عرض أقل
artist-releases = الإصدارات
artist-releases-more = عرض الكل
artist-releases-less = عرض أقل
artist-filter-all = الكل
artist-filter-albums = الألبومات
artist-filter-singles = الأغاني المنفردة
artist-filter-eps = الأسطوانات القصيرة
artist-appears-on = يظهر في
album-also-like = قد يعجبك أيضًا
album-tab-albums = الألبومات
album-tab-artists = الفنانون
album-label = ℗ { $label }

# user profile page
user-playlists-empty = لا توجد قوائم تشغيل عامة بعد

# release kinds
release-album = ألبوم
release-single = أغنية منفردة
release-compilation = مجموعة
release-ep = أسطوانة قصيرة
release-audiobook = كتاب صوتي
release-podcast = بودكاست

# home page
home-quick-picks = اختيارات سريعة
home-quick-picks-empty = أعجب ببعض الأغاني وستظهر هنا
home-recently-added = أُضيفت مؤخرًا
home-playlists = قوائم التشغيل
home-favorite-albums = الألبومات المفضلة
home-artists = الفنانون
home-collection-albums = ألبومات من مجموعتك

# search page
search-placeholder = ماذا تريد أن تسمع؟
genre-empty = لا يوجد ما يُعرض هنا بعد
search-best-match = أفضل تطابق
search-no-matches = لا توجد نتائج مطابقة
search-results = النتائج
search-songs = الأغاني
search-artists = الفنانون
search-albums-playlists = الألبومات وقوائم التشغيل
search-tag = { $kind } ·
search-saved =
    { $count ->
        [one] أغنية واحدة في المكتبة
       *[other] { $count } أغانٍ في المكتبة
    }
kind-song = أغنية
kind-artist = فنان
kind-album = ألبوم
kind-playlist = قائمة تشغيل

# song page
song-eyebrow = أغنية
song-play = تشغيل الأغنية
song-view-album = عرض الألبوم
song-about = عن هذه الأغنية
song-album = الألبوم
song-released = تاريخ الإصدار
song-streams = مرات الاستماع
song-position = الموضع
song-label = شركة الإنتاج
song-popularity = الشعبية
song-popularity-value = { $value }٪
song-disc-track = القرص { $disc }، المقطع { $track }
song-track = المقطع { $track }
song-credits = المساهمون
song-performed-by = أداء
song-details = الأنواع والتفاصيل
song-genres = الأنواع
song-language = اللغة
song-content = المحتوى
song-explicit = صريح
song-clean = نظيف
artist-about = عن الفنان
artist-about-fallback = استكشف أشهر أغاني الفنان وإصداراته.
artist-about-open = الانتقال إلى الفنان
song-copyright = © { $notice }

# song languages
language-ar = العربية
language-de = الألمانية
language-en = الإنجليزية
language-es = الإسبانية
language-fr = الفرنسية
language-hi = الهندية
language-it = الإيطالية
language-ja = اليابانية
language-ko = الكورية
language-pt = البرتغالية
language-ru = الروسية
language-tr = التركية
language-uk = الأوكرانية
language-zh = الصينية
language-zxx = بلا محتوى لغوي

# counts
count-songs =
    { $count ->
        [one] أغنية واحدة
        [two] أغنيتان
       *[other] { $count } أغانٍ
    }
count-tracks =
    { $count ->
        [one] مقطع واحد
        [two] مقطعان
       *[other] { $count } مقاطع
    }

# running times
runtime-seconds = { $seconds } ث
runtime-minutes = { $minutes } د { $seconds } ث
runtime-hours = { $hours } س { $minutes } د

# dates
date-just-now = الآن
date-minute-ago = قبل دقيقة
date-minutes-ago = قبل { $count } دقائق
date-today = اليوم في { $time }
date-yesterday = أمس في { $time }
date-time = { $date }، { $time }
date-full = { $day } { $month } { $year }
month-1 = يناير
month-2 = فبراير
month-3 = مارس
month-4 = أبريل
month-5 = مايو
month-6 = يونيو
month-7 = يوليو
month-8 = أغسطس
month-9 = سبتمبر
month-10 = أكتوبر
month-11 = نوفمبر
month-12 = ديسمبر

# settings
settings-search = البحث في الإعدادات
settings-search-results = نتائج البحث
# where a search result lives: a drill-in page under the page it opens from
settings-result-place = { $parent } › { $page }
settings-page-providers = المصادر
settings-page-appearance = المظهر
settings-page-player-bar = شريط المشغّل
settings-player-bar-layout = التخطيط
settings-player-bar-layout-detail = ترتيب معلومات المقطع وأزرار التشغيل وشريط التقدّم
settings-player-bar-classic = كلاسيكي
settings-player-bar-compact = مضغوط
settings-player-bar-centered = في المنتصف
settings-player-bar-surface = السطح
settings-player-bar-transparent = شريط مشغّل شفاف
settings-player-bar-transparent-detail = السماح للصفحات والأشرطة الجانبية بالتمرير أسفل شريط مشغّل ضبابي
settings-player-bar-transparent-needs-blur = فعّل «تمويه الواجهة» لتمرير الصفحات أسفل شريط مشغّل ضبابي
settings-page-now-playing = قيد التشغيل الآن
settings-page-lyrics = الكلمات
settings-page-playback = التشغيل
settings-page-sound = الصوت
settings-page-storage = التخزين
settings-page-discord = ديسكورد
settings-page-general = عام
settings-page-about = حول
settings-tab-integrations = التكاملات
settings-tab-library = المكتبة
settings-about-name = Encore
settings-page-lyrics-sources = مصادر الكلمات
settings-page-music-folders = مجلدات الموسيقى
settings-group-services = خدمات البث
settings-group-local-files = الملفات المحلية
settings-group-background = الخلفية
settings-group-controls = عناصر التحكم
settings-group-display = العرض
settings-group-sources = المصادر
settings-group-lyrics-order = الأولوية
settings-group-playing = التشغيل
settings-group-cache = المقاطع المُشغَّلة
settings-group-downloads = التنزيلات
settings-group-protected = التشغيل المحمي
settings-group-output = الإخراج
settings-group-status = الحالة
settings-group-startup = بدء التشغيل
settings-group-context-menus = القوائم المنبثقة
key-escape = Esc
key-enter = Enter
key-space = مسافة
key-tab = Tab
key-backspace = Backspace
key-delete = Delete
settings-lyrics-sources = مصادر الكلمات
settings-local-folder-count = { $count ->
    [one] مجلد واحد
    [two] مجلدان
   *[other] { $count } مجلدات
    }
settings-local-folder-none = لا توجد مجلدات بعد
settings-license-short = GPL-3.0 أو أحدث
settings-theme = السمة
settings-theme-detail = اختر لوحة ألوان التطبيق
settings-opacity = التعتيم
settings-opacity-detail = اضبط تعتيم خلفية التطبيق
settings-opacity-value = { $percent }٪
settings-theme-folder = فتح المجلد
settings-theme-unavailable = لا يمكن تطبيق هذه السمة إلا عند تعطيل السمة التكيّفية
settings-adaptive = سمة تكيّفية
settings-adaptive-detail = تلوين اللوحة بغلاف الألبوم قيد التشغيل
settings-ambient = خلفية محيطة
settings-ambient-detail = ملء شاشة التشغيل بألوان مأخوذة من الغلاف
settings-ambient-motion = حركة محيطة
settings-ambient-motion-detail = تحريك الألوان المحيطة ببطء بدلًا من تثبيتها
settings-visualizer = المؤثر المرئي
settings-visualizer-detail = طريقة رسم الطيف خلف الغلاف في شاشة التشغيل
settings-visualizer-style-none = إيقاف
settings-visualizer-style-bars = أعمدة
settings-visualizer-style-wave = موجة
settings-visualizer-style-both = أعمدة وموجة
settings-visualizer-absolute = تجاهل مستوى الصوت
settings-visualizer-absolute-detail = رسم الطيف بمستوى المقطع نفسه مهما كان ارتفاع صوت Encore
settings-preview-title = عنوان الأغنية
settings-preview-artist = الفنان
settings-preview-album = الألبوم
settings-fullscreen-controls-autohide = إخفاء عناصر التحكم
settings-fullscreen-controls-autohide-detail = إخفاء أزرار التشغيل تدريجيًا في شاشة التشغيل عند توقف المؤشر
settings-os-fullscreen = استخدام ملء الشاشة في النظام
settings-os-fullscreen-detail = جعل شاشة التشغيل تملأ الشاشة كاملة كما يفعل F11
settings-icons = حزمة الأيقونات
settings-icons-detail = اختر مجموعة الأيقونات التي تستخدمها الواجهة
settings-motion = تقليل الحركة
settings-motion-detail = تخطي الحركات والانتقالات في الواجهة
settings-pace = سرعة الحركة
settings-pace-detail = مدى سرعة حركات الواجهة
settings-saver = تخفيف معدل الإطارات للنافذة غير النشطة
settings-saver-detail = تُطبَّق التغييرات بعد إعادة تشغيل Encore.
settings-corners = الزوايا
settings-corners-detail = مدى استدارة الأسطح وعناصر التحكم
settings-blur = تمويه الواجهة
settings-blur-detail = تمويه القوائم وعناصر التحكم العائمة فوق ما تغطيه
settings-blur-window = تمويه النافذة
settings-blur-window-detail = رسم النافذة فوق سطح مكتب ضبابي. يتطلب تعتيمًا أقل من 100٪
settings-font = حجم الخط
settings-font-detail = حجم النص الأساسي، ويتبعه كل ما سواه
settings-font-value = { $size } بكسل
settings-startup = البدء على
settings-startup-detail = الشاشة التي يفتح عليها Encore عند التشغيل
settings-startup-no-guest = غير متاح في وضع الضيف
settings-entries = عناصر الشريط الجانبي
settings-entries-detail = الأقسام المعروضة في الشريط الجانبي
settings-entries-pick = اختر العناصر
settings-language = اللغة
settings-language-detail = اللغة التي يستخدمها Encore في الواجهة
settings-language-system = النظام
settings-language-search = البحث عن لغة
settings-language-none = لم يُعثر على لغات
settings-typeface = الخط
settings-typeface-detail = الخط الذي يستخدمه Encore في الواجهة
settings-typeface-system = الافتراضي
settings-typeface-search = البحث عن خط
settings-typeface-none = لم يُعثر على خطوط
settings-server-side-decorations = زخارف من جهة الخادم
settings-server-side-decorations-detail = ترك مدير الرسومات يرسم شريط العنوان والإطار والظل
settings-typeface-loading = جارٍ التحميل…
settings-window-controls-detail = رسم أزرار التصغير والتكبير والإغلاق في شريط العنوان
settings-traffic-light-controls = أزرار بنمط إشارة المرور
settings-traffic-light-controls-detail = رسم أزرار التصغير والتكبير والإغلاق نقاطًا ملونة
settings-window-rounding = زوايا النافذة
settings-window-rounding-detail = مدى استدارة زوايا النافذة نفسها
settings-window-controls = أزرار النافذة
settings-window-controls-detail = رسم أزرار التصغير والتكبير والإغلاق في شريط العنوان
settings-controls-side-detail = طرف شريط العنوان الذي توضع فيه الأزرار
settings-close-to-tray = الاستمرار في العمل في الخلفية
settings-close-to-tray-detail = إبقاء Encore يعمل ويشغّل بعد إغلاق نافذته
settings-tray-icon = الإظهار في شريط النظام
settings-tray-icon-detail = وضع أيقونة بأزرار التشغيل في شريط النظام
settings-spotify-connect = الظهور كجهاز Spotify
settings-spotify-connect-name = اسم الجهاز
settings-spotify-connect-name-detail = اسم Encore في قائمة أجهزة تطبيقات Spotify
settings-spotify-connect-name-both = Encore (اسم الحاسوب)
settings-spotify-connect-name-computer = اسم الحاسوب
settings-spotify-connect-name-custom = مخصص
settings-spotify-connect-custom = اسم مخصص
settings-spotify-connect-custom-detail = الاسم الذي يُعرض. إذا تُرك فارغًا يُستخدم Encore واسم الحاسوب
settings-spotify-connect-custom-hint = اسم الجهاز
settings-spotify-connect-detail = إدراج Encore في منتقي أجهزة تطبيقات Spotify
settings-discord = الإظهار على ديسكورد
settings-discord-detail = عرض المقطع الذي تشغّله في ملفك الشخصي على ديسكورد
settings-discord-name = اسم الحالة
settings-discord-name-detail = ما يظهر بعد «يستمع إلى» الذي يراه أصدقاؤك
settings-discord-name-encore = Encore
settings-discord-name-provider = المصدر
settings-discord-name-music = موسيقى
settings-discord-name-title = العنوان
settings-discord-name-artist = الفنان
settings-discord-name-artist-title = الفنان - العنوان
settings-discord-show-paused = الإظهار عند الإيقاف المؤقت
settings-discord-show-paused-detail = إبقاء الحالة في ملفك على ديسكورد أثناء إيقاف المقطع مؤقتًا
settings-discord-badge = إظهار شارة المصدر
settings-discord-badge-detail = تمييز الحالة بأيقونة صغيرة للمصدر الذي جاء منه المقطع
settings-discord-anonymous = إخفاء التفاصيل
settings-discord-anonymous-detail = الاكتفاء بأن هناك موسيقى قيد التشغيل، دون العنوان أو الفنان أو الغلاف
settings-discord-hide-on-connect = الإخفاء أثناء Spotify Connect
settings-discord-hide-on-connect-detail = ترك الحالة لـ Spotify أثناء تشغيل جهاز آخر
settings-discord-buttons = الأزرار
settings-discord-buttons-detail = روابط أسفل الحالة يفتحها أصدقاؤك
settings-discord-buttons-pick = اختر الأزرار
settings-discord-preview-listening = يستمع إلى { $name }
settings-discord-preview-music = موسيقى
settings-discord-preview-elapsed = مضى { $time }
settings-discord-preview-listen-on = استمع على { $provider }
# the Discord status when the track is left out of it
discord-listening = يستمع إلى الموسيقى
settings-fullscreen-controls-autohide-automatic = تلقائي
settings-fullscreen-controls-autohide-always-hidden = مخفية دائمًا
settings-fullscreen-controls-autohide-always-shown = ظاهرة دائمًا
settings-normalisation = توحيد مستوى الصوت
settings-normalisation-detail = إبقاء المقاطع عند مستوى صوت ثابت
settings-gapless = تشغيل دون فواصل
settings-gapless-detail = وصل المقطع بالتالي دون توقف، كما رُتّب الألبوم
settings-crossfade = التلاشي المتداخل
settings-crossfade-detail = دمج كل أغنية في التالية تدريجيًا على مدى بضع ثوانٍ.
settings-crossfade-off = إيقاف
settings-crossfade-seconds = { $count } ث
settings-keep-tracks = الاحتفاظ بالمقاطع المُشغَّلة
settings-keep-tracks-detail = حفظ ما تشغّله على هذا الحاسوب حتى لا يُنزَّل مرة أخرى
settings-cache-size = حجم ذاكرة التخزين المؤقت
settings-cache-size-detail = المساحة التي قد تشغلها المقاطع التي شغّلتها
settings-cache-size-value = { $count } غيغابايت
settings-cache-used = المساحة المستخدمة
settings-cache-used-detail = ما تشغله المقاطع المحفوظة بعد تشغيلها على هذا الحاسوب
settings-cache-clear = مسح
settings-downloads-used = المساحة المستخدمة
settings-downloads-used-detail = ما تشغله الأغاني التي نزّلتها على هذا الحاسوب
settings-offline-mode = وضع عدم الاتصال
settings-offline-mode-detail = إبقاء Encore بعيدًا عن الإنترنت حتى تعيد تشغيل الاتصال
settings-downloads-remove = إزالة الكل
settings-size-gb = { $size } غيغابايت
settings-size-mb = { $size } ميغابايت
settings-size-measuring = جارٍ القياس…
settings-stream-quality = جودة البث
settings-stream-quality-detail = المستويات الأعلى أفضل صوتًا وتستهلك بيانات أكثر
settings-stream-quality-hi-res = عالي الدقة
settings-stream-quality-lossless = بدون فقدان
settings-stream-quality-high = عالية
settings-stream-quality-normal = عادية
settings-stream-quality-low = منخفضة
settings-stream-quality-flac = FLAC، { $depth } بت { $rate } كيلوهرتز
settings-stream-quality-flac-up-to = FLAC، حتى { $depth } بت { $rate } كيلوهرتز
settings-stream-quality-lossy = { $codec }، { $kbps } كيلوبت/ث
settings-stream-quality-lossy-up-to = { $codec }، حتى { $kbps } كيلوبت/ث
settings-stream-format = صيغة البث
settings-stream-format-detail = الصيغة التي يحوّل إليها الخادم كل مقطع قبل إرساله
settings-stream-format-original = الأصلية
settings-stream-format-mp3 = MP3
settings-stream-format-opus = Opus
settings-stream-format-aac = AAC
settings-stream-bitrate = أقصى معدل بت
settings-stream-bitrate-detail = يخفّض الخادم كل ما هو أعلى إلى هذا المعدل
settings-stream-bitrate-unlimited = بلا حد
settings-stream-bitrate-value = { $kbps } كيلوبت/ث
settings-stream-bitrate-hourly = نحو { $megabytes } ميغابايت في الساعة
settings-stream-bitrate-unlimited-detail = كل ملف بمعدل البت الخاص به
settings-output-device = جهاز الإخراج
settings-output-device-detail = جهاز الصوت الذي يشغّل عليه Encore
settings-output-device-system = افتراضي النظام
settings-output-device-missing = الجهاز المحفوظ (غير متصل)
settings-output-device-direct = مباشرة إلى العتاد
settings-sample-rate = معدل العينات
settings-sample-rate-detail = المعدل الذي يعمل به الجهاز. يحوّل Encore كل مقطع إليه
settings-sample-rate-value = { $khz } كيلوهرتز
settings-sample-rate-change = تغيير
settings-buffer = حجم المخزن المؤقت
settings-buffer-detail = مقدار الصوت الذي يسلّمه Encore للجهاز في كل مرة
settings-output-idle = لا تشغيل
settings-output-device-loading = جارٍ تحميل الأجهزة…
settings-output-failed = الجهاز غير مدعوم
settings-output-failed-detail = خطأ: { $error }
settings-signal-source = المصدر
settings-signal-normalized = الجهارة
settings-signal-gain = { $db } ديسيبل
settings-signal-resampled = إعادة أخذ العينات
settings-signal-khz = { $khz } كيلوهرتز
settings-signal-kbps = { $kbps } كيلوبت/ث
settings-signal-bits = { $depth } بت
settings-signal-source-detail = { $rate } · { $quality }
settings-sleep = مؤقت النوم
settings-sleep-detail = يوقف الموسيقى تلقائيًا بعد مدة محددة لتغفو على أنغامها
settings-stay-awake = البقاء مستيقظًا أثناء التشغيل
settings-stay-awake-detail = منع السكون أثناء التشغيل وإبقاء الشاشة مضاءة عند فتح شاشة التشغيل
settings-sleep-configure = ضبط…
settings-sleep-off = إيقاف
settings-sleep-end-of-track = نهاية المقطع
settings-sleep-minutes = { $count } دقيقة
settings-widevine = وحدة Widevine
settings-widevine-detail = وحدة فك تشفير لم تعد يحتاجها Encore.
settings-widevine-none = وحدة فك تشفير لم تعد يحتاجها Encore.
settings-widevine-looking = جارٍ البحث…
settings-widevine-asking = في انتظار ردك
settings-widevine-fetching = جارٍ التنزيل…
settings-widevine-installing = جارٍ التثبيت…
settings-widevine-installed = موجودة في متصفح
settings-widevine-fetched = نُزّلت من Google
settings-widevine-missing = غير مثبتة
settings-widevine-configured = مضبوطة من البيئة
settings-widevine-fetch = تنزيل
settings-widevine-uninstall = إلغاء التثبيت
settings-equalizer = المعادل الصوتي
settings-equalizer-detail = تشكيل الصوت بإعداد مسبق أو منحنى خاص بك
settings-equalizer-preset-detail = صوت جاهز أو أحد ملفاتك الشخصية
settings-equalizer-custom = مخصص
settings-equalizer-flat = مستوٍ
settings-equalizer-bass-boost = تعزيز الجهير
settings-equalizer-bass-reducer = تخفيف الجهير
settings-equalizer-treble-boost = تعزيز الحاد
settings-equalizer-vocal = غناء
settings-equalizer-rock = روك
settings-equalizer-pop = بوب
settings-equalizer-jazz = جاز
settings-equalizer-classical = كلاسيكي
settings-equalizer-electronic = إلكتروني
settings-equalizer-acoustic = صوتي
settings-equalizer-loudness = جهارة
equalizer-advanced = متقدم
equalizer-simple = بسيط
equalizer-sound = الصوت
equalizer-profile-default = الافتراضي
equalizer-profile-new = الملف { $number }
equalizer-profile-pasted = ملف ملصوق
equalizer-profile-name-hint = اسم الملف
equalizer-save-as = الحفظ كملف
equalizer-rename = إعادة تسمية الملف
equalizer-delete = حذف الملف
equalizer-delete-confirm = حذف «{ $name }»؟ لا يمكن التراجع عن ذلك.
equalizer-undo = تراجع
equalizer-redo = إعادة
equalizer-bass = الجهير
equalizer-treble = الحاد
equalizer-tone-reset = إعادة التعيين إلى 0 ديسيبل
equalizer-preamp = التضخيم المسبق
equalizer-preamp-auto = تلقائي
equalizer-preamp-manual = يدوي
equalizer-decibels = ديسيبل
equalizer-decibels-value = { $db } ديسيبل
equalizer-hertz = هرتز
equalizer-q = Q
equalizer-octaves = أوكتاف
equalizer-no-gain = —
equalizer-filters = المرشحات
equalizer-no-filters = لا توجد مرشحات بعد. أضف واحدًا أو اختر إعدادًا مسبقًا أعلاه أو استورد ملفًا.
equalizer-add-filter = إضافة مرشح
equalizer-remove-filter = إزالة المرشح
equalizer-kind-peak = ذروة
equalizer-kind-low-shelf = رف منخفض
equalizer-kind-high-shelf = رف مرتفع
equalizer-kind-low-pass = تمرير منخفض
equalizer-kind-high-pass = تمرير مرتفع
equalizer-kind-notch = حزّ
equalizer-kind-band-pass = تمرير نطاق
equalizer-kind-all-pass = تمرير الكل
equalizer-transfer = الاستيراد والتصدير
equalizer-import = استيراد
equalizer-export = تصدير
equalizer-import-file = استيراد ملف…
equalizer-paste = لصق
equalizer-copy = نسخ
equalizer-export-file = تصدير ملف…
equalizer-imported = تم استيراد { $name }
equalizer-imported-skipped = تم استيراد { $name } دون { $count ->
        [one] سطر واحد
       *[other] { $count } أسطر
    }
equalizer-import-nothing = لم يُعثر على إعدادات معادل لاستيرادها
equalizer-import-too-many = لم يُستورد: { $count ->
        [one] مرشح واحد مفعّل
       *[other] { $count } مرشحات مفعّلة
    }، أكثر مما يتسع له الملف
equalizer-import-too-large = لم يُستورد: النص أكبر من 1 ميغابايت، وهو كبير جدًا لملف معادل
equalizer-filter-limit = يتسع الملف لما يصل إلى { $max } مرشحات.
equalizer-paste-empty = لا تحتوي الحافظة على نص
equalizer-copied = تم نسخ الملف كنص Equalizer APO
equalizer-exported = تم تصدير الملف
equalizer-dialog-failed = تعذّر فتح نافذة الملفات
equalizer-file-unreadable = تعذّرت قراءة الملف
equalizer-file-unwritable = تعذّرت كتابة الملف
equalizer-value-refused = تم الإبقاء على القيمة السابقة
equalizer-skipped-line = السطر { $line }: { $reason }
equalizer-skipped-more = و{ $count ->
        [one] واحد آخر
       *[other] { $count } أخرى
    }
equalizer-skip-unknown-type = نوع مرشح غير معروف { $name }
equalizer-skip-unreadable = ليس مرشحًا يستطيع Encore قراءته
equalizer-skip-no-frequency = لا يوجد تردد
equalizer-skip-no-gain = لا يوجد كسب
equalizer-skip-no-width = لا يوجد Q أو عرض نطاق
equalizer-skip-expression = قيمة مكتوبة كتعبير
equalizer-skip-no-preamp = التضخيم المسبق ليس رقمًا
equalizer-skip-unsupported = { $name } غير مدعوم
equalizer-rules = قواعد الأجهزة
equalizer-rule-other = أي جهاز آخر
equalizer-rule-playing = قيد التشغيل الآن
equalizer-rule-disconnected = غير متصل
equalizer-rule-replaces = يستبدل قاعدته
equalizer-rule-choose-device = اختر جهازًا
equalizer-rule-choose-sound = اختر صوتًا
equalizer-rule-add = إضافة قاعدة
equalizer-rule-remove = إزالة القاعدة
equalizer-output-port = { $device } · { $port }
signal-path-codec-other = ترميز آخر
equalizer-invalid-frequency = يجب أن يكون التردد بين { $low } و{ $high } هرتز
equalizer-invalid-gain = يجب أن يكون الكسب بين { $low } و{ $high } ديسيبل
equalizer-invalid-q = يجب أن يكون Q بين { $low } و{ $high }
equalizer-invalid-octaves = يجب أن يكون عرض النطاق بين { $low } و{ $high } أوكتاف
equalizer-invalid-shelf-octaves = يأخذ الرف قيمة Q لا عرض نطاق
equalizer-invalid-preamp = يجب أن يكون التضخيم المسبق بين { $low } و{ $high } ديسيبل
equalizer-overload = تشبّع بمقدار { $db } ديسيبل
equalizer-overload-slight = تشبّع بأقل من 0.1 ديسيبل
equalizer-graph-preamp = { $db } ديسيبل
equalizer-lower-preamp = خفض التضخيم المسبق
equalizer-level = المستوى
equalizer-status-bit-perfect = مطابق بالبت
equalizer-status-bit-perfect-detail = مطابق بالبت من Encore
equalizer-status-server = ثم يحوّله خادم الصوت إلى { $khz } كيلوهرتز
equalizer-status-altered = غير مطابق بالبت بسبب:
equalizer-status-separator = {"، "}
equalizer-status-more = +{ $count }
equalizer-status-equalized = المعادل
equalizer-status-volume = مستوى الصوت
equalizer-status-normalized = موحّد
equalizer-status-rate = { $from } → { $to } كيلوهرتز
equalizer-status-bits = { $from } → { $to } بت
equalizer-status-float = عائم → { $to } بت
equalizer-status-channels = { $from ->
    [1] أحادي
    [2] ستيريو
   *[other] { $from } قنوات
} → { $to ->
    [1] أحادي
    [2] ستيريو
   *[other] { $to } قنوات
}
settings-panel-lyrics-size = حجم النص في اللوحة
settings-panel-lyrics-size-detail = حجم نص الكلمات في اللوحة الجانبية، فوق حجم الخط الأساسي
settings-fullscreen-lyrics-size = حجم النص في شاشة التشغيل
settings-fullscreen-lyrics-size-detail = حجم نص الكلمات في شاشة التشغيل، فوق حجم الخط الأساسي
settings-lyrics-size-value = { $size }٪
settings-lyrics-for-local-files = البحث عن كلمات الملفات المحلية
settings-lyrics-for-local-files-detail = استخدام بيانات الملفات المحلية لجلب الكلمات من الإنترنت
settings-artwork-for-local-files = أغلفة الملفات المحلية
settings-artwork-for-local-files-detail = استخدام بيانات الملفات المحلية للعثور على غلاف من Deezer لحالتك على ديسكورد
settings-karaoke-lyrics = كاريوكي
settings-karaoke-lyrics-detail = إبراز الكلمات كلمةً كلمة عند توفر التوقيت
settings-blur-lyrics = تمويه الأسطر غير النشطة
settings-blur-lyrics-detail = تمويه الأسطر القادمة والسابقة في لوحة الكلمات
settings-lyrics-sample-first = بيانات عشوائية
settings-lyrics-sample-second = ضع نصًا طريفًا هنا
settings-lyrics-sample-japanese = ここに面白い文章を入れてください
settings-lyrics-sample-romanized = koko ni omoshiroi bunshou o irete kudasai
settings-lyrics-sample-fourth = أتمنى أن تكون هذه اليابانية صحيحة
settings-lyrics-sample-fifth = السطر الخامس
settings-romanized-lyrics = كلمات بالحروف اللاتينية
settings-romanized-lyrics-detail = إظهار النطق المولَّد محليًا لأنظمة الكتابة المختارة
settings-romanization-writing-systems = أنظمة الكتابة
settings-lyrics-priority = أولوية المصادر
settings-lyrics-priority-detail = من بين الكلمات من النوع نفسه، يعرض Encore المصدر الأعلى
settings-lyrics-providers-detail = اختر الخدمات التي يُبحث فيها عن الكلمات
settings-lyrics-providers-selected = { $count ->
    [one] تم اختيار واحد
   *[other] تم اختيار { $count }
    }
settings-lyrics-provider-local = الملفات المحلية
settings-lyrics-provider-subsonic = Subsonic
settings-lyrics-provider-spotify = Spotify
settings-lyrics-provider-youtube = YouTube Music
settings-lyrics-provider-apple-music = Apple Music
settings-lyrics-provider-musixmatch = Musixmatch
settings-lyrics-provider-lrclib = LRCLIB
settings-lyrics-provider-kugou = Kugou
settings-lyrics-provider-netease = NetEase
settings-romanization-japanese = اليابانية
settings-romanization-chinese = الصينية
settings-romanization-korean = الكورية
settings-romanization-cyrillic = السيريلية
settings-romanization-greek = اليونانية
settings-romanization-arabic = العربية
settings-romanization-other = أنظمة كتابة أخرى
settings-group-window = النافذة
settings-group-text = النص
settings-group-motion = الحركة
settings-group-streaming = البث
settings-group-spotify-connect = Spotify Connect
settings-adaptive-menu = قائمة منبثقة تكيّفية
settings-adaptive-menu-detail = إغفال العناصر التي يعرضها الصف بالفعل، مثل الألبوم أو الفنان
settings-accounts = خدمات البث
settings-accounts-detail = الخدمات التي يمكن لهذا الجهاز التشغيل منها
settings-provider-current = قيد الاستخدام
settings-provider-sign-in = تسجيل الدخول
settings-profile-account = { $provider } · { $account }
settings-sign-out = تسجيل الخروج
settings-local-folder = مجلدات الموسيقى
settings-local-folder-empty = غير مضبوطة
settings-choose-folder = اختر مجلدًا…
settings-add-folder = إضافة مجلد
settings-remove-folder = إزالة المجلد
settings-rescan = إعادة الفحص
settings-scan-walking = جارٍ الفحص…
settings-scan-progress = { $percent }٪
settings-scan-done = اكتمل في { $seconds } ث
settings-version = الإصدار
settings-version-detail = إصدار Encore الذي تستخدمه
settings-license = الترخيص
settings-license-detail = رخصة جنو العمومية، الإصدار 3 أو أحدث
settings-source = الشيفرة المصدرية
settings-source-detail = المصدر المقابل لهذا الإصدار
settings-team = الفريق
settings-role-lead-maintainer = المشرف الرئيسي
settings-notice = حقوق النشر © 2026 للمساهمين في Encore. يأتي Encore دون أي ضمان على الإطلاق. إنه برنامج حر، ولك أن تعيد توزيعه وفق شروط رخصة جنو العمومية، الإصدار 3 أو أحدث.

# themes
theme-system = النظام
theme-dark = داكن
theme-light = فاتح
theme-unavailable = { $name } (غير متاح)

# corners
corners-square = مربعة
corners-subtle = خفيفة
corners-rounded = مستديرة
corners-round = دائرية

# motion
motion-system = النظام
motion-always = دائمًا
motion-never = أبدًا
pace-slow = بطيئة
pace-base = عادية
pace-quick = سريعة
saver-off = إيقاف
saver-fps = { $fps } إطارًا/ث

toast-playlist-created = تم إنشاء قائمة التشغيل
toast-playlist-renamed = تمت إعادة تسمية قائمة التشغيل
toast-playlist-deleted = تم حذف قائمة التشغيل
toast-local-delete-failed = تعذّر حذف بعض ملفات المقاطع
toast-playlist-added = تمت إضافة قائمة التشغيل إلى مكتبتك
toast-playlist-removed = تمت إزالة قائمة التشغيل من مكتبتك
toast-playlist-visibility = تم تغيير ظهور قائمة التشغيل
toast-track-added = تمت الإضافة إلى { $name }
toast-track-removed = تمت الإزالة من { $name }
toast-track-removed-unnamed = تمت الإزالة من قائمة التشغيل
toast-playlist-failed = تعذّر حفظ هذا التغيير
toast-playlist-busy = لا يزال تغيير آخر قيد التنفيذ
toast-playlist-signed-out = سجّل الدخول لتغيير قوائم التشغيل
toast-queued-track = تمت إضافة { $name } إلى قائمة الانتظار
toast-next-track = يُشغَّل { $name } تاليًا
toast-last-track = يُشغَّل { $name } أخيرًا
toast-queued-album = تمت إضافة الألبوم إلى قائمة الانتظار
toast-next-album = يُشغَّل الألبوم تاليًا
toast-last-album = يُشغَّل الألبوم أخيرًا
toast-queued-playlist = تمت إضافة قائمة التشغيل إلى قائمة الانتظار
toast-next-playlist = تُشغَّل قائمة التشغيل تاليًا
toast-last-playlist = تُشغَّل قائمة التشغيل أخيرًا
toast-queued-artist = تمت إضافة الفنان إلى قائمة الانتظار
toast-next-artist = يُشغَّل الفنان تاليًا
toast-last-artist = يُشغَّل الفنان أخيرًا
toast-queue-failed = تعذّرت الإضافة إلى قائمة الانتظار
toast-queued-tracks = تمت إضافة { $count } مقاطع إلى قائمة الانتظار
toast-next-tracks = تُشغَّل { $count } مقاطع تاليًا
toast-last-tracks = تُشغَّل { $count } مقاطع أخيرًا
toast-queue-cleared = تم مسح قائمة الانتظار
toast-queue-removed-track = تمت إزالة { $name } من قائمة الانتظار
toast-queue-removed-tracks = تمت إزالة { $count } مقاطع من قائمة الانتظار
toast-sleep-ended = انتهى مؤقت النوم
toast-radio-failed = تعذّر بدء الراديو لـ { $name }
toast-similar-failed = تعذّر العثور على مقاطع مشابهة لـ { $name }
toast-open-failed = تعذّر فتح { $name }
toast-open-unsupported = ‏{ $name } ليس ملفًا صوتيًا يستطيع Encore تشغيله
toast-open-all-failed = تعذّر فتح { $count } ملفات
toast-open-some-failed = تعذّر فتح بعض الملفات
toast-track-unplayable = تعذّر تشغيل { $name }
toast-playback-stopped = توقف التشغيل بعد تعذّر تشغيل عدة مقاطع متتالية
toast-library-add-failed = تعذّرت إضافة { $name } إلى مكتبتك
toast-library-remove-failed = تعذّرت إزالة { $name } من مكتبتك
toast-library-added = تمت الإضافة إلى مكتبتك
toast-library-removed-one = تمت إزالة { $name } من مكتبتك
toast-library-songs-removed = تمت إزالة { $count ->
        [one] أغنية واحدة
       *[other] { $count } أغانٍ
    } من مكتبتك
toast-library-albums-removed = تمت إزالة { $count ->
        [one] ألبوم واحد
       *[other] { $count } ألبومات
    } من مكتبتك
toast-playlists-removed = تمت إزالة { $count ->
        [one] قائمة تشغيل واحدة
       *[other] { $count } قوائم تشغيل
    } من مكتبتك
toast-favorite-removed = تمت إزالة { $name } من المفضلة
toast-favorite-songs-removed = تمت إزالة { $count ->
        [one] أغنية واحدة
       *[other] { $count } أغانٍ
    } من المفضلة
toast-favorite-albums-removed = تمت إزالة { $count ->
        [one] ألبوم واحد
       *[other] { $count } ألبومات
    } من المفضلة
toast-favorite-artists-removed = تمت إزالة { $count ->
        [one] فنان واحد
       *[other] { $count } فنانين
    } من المفضلة
toast-tracks-removed = تمت إزالة { $count ->
        [one] أغنية واحدة
       *[other] { $count } أغانٍ
    } من { $name }
toast-tracks-removed-unnamed = تمت إزالة { $count ->
        [one] أغنية واحدة
       *[other] { $count } أغانٍ
    } من قائمة التشغيل
toast-unpinned = تم إلغاء تثبيت { $name }

# lyrics
lyrics-title = الكلمات
lyrics-idle = شغّل شيئًا لترى كلماته
lyrics-loading = جارٍ البحث عن الكلمات…
lyrics-missing = لم يُعثر على كلمات، عذرًا!
lyrics-instrumental = هذه الأغنية موسيقى فقط
lyrics-failed = تعذّر الوصول إلى خدمة الكلمات
lyrics-follow = متابعة الأغنية مرة أخرى
lyrics-source = الكلمات من { $source }
lyrics-source-local = الكلمات من الملف المحلي
lyrics-writers = كتابة { $writers }

update-available = صدر Encore { $version }
update-detail = إصدارك الحالي { $running }. اطّلع على التغييرات أو حدّث الآن.
update-detail-notes = إصدارك الحالي { $running }. اطّلع على التغييرات ثم حدّث Encore بالطريقة التي ثبّته بها.
update-notes = ما الجديد
update-now = تحديث
update-working = جارٍ تنزيل التحديث…
update-failed = تعذّر تثبيت التحديث. أعد المحاولة من صفحة الإصدارات.
update-never = عدم البحث عن تحديثات
update-current = لديك أحدث إصدار من Encore
update-check-failed = تعذّر البحث عن تحديثات. تحقق من اتصالك ثم أعد المحاولة.
notice-never = عدم الإظهار مرة أخرى
requirement-audio-bridge-title = لا يستطيع Encore الوصول إلى خادم الصوت
requirement-audio-output-title = لا يوجد إخراج صوتي
requirement-audio-output = تعذّر على Encore فتح جهاز الصوت، لذا لا يُسمع ما تشغّله. تأكد من توصيل جهاز وأنه غير مستخدم من تطبيق آخر.
settings-check-updates = البحث عن تحديثات
settings-check-updates-detail = سؤال GitHub مرة عند بدء التشغيل عن وجود إصدار أحدث. يثبّته Encore بنفسه
settings-log = ملف السجل
settings-log-detail = ما كتبه Encore أثناء عمله. أرفقه ببلاغ الأخطاء
settings-log-open = فتح السجل

# tags
tags-edit-title = تعديل الوسوم
tags-sheet-song = الأغنية
tags-sheet-album = الألبوم
tags-sheet-details = التفاصيل
tags-title = العنوان
tags-artist = الفنان
tags-artists = الفنانون
tags-add-artist = إضافة فنان
tags-remove-artist = إزالة هذا الفنان
tags-track = رقم المقطع
tags-track-total = عدد المقاطع في الإصدار
tags-disc = رقم القرص
tags-disc-total = عدد الأقراص في الإصدار
tags-album = الألبوم
tags-album-artist = فنان الألبوم
tags-year = السنة
tags-genre = النوع
tags-composer = الملحّن
tags-publisher = الناشر
tags-isrc = ISRC
tags-comment = تعليق
toast-tags-saved = تم حفظ وسوم { $name }
toast-tags-failed = تعذّر حفظ الوسوم
toast-output-select-failed = تعذّر اختيار { $name }
toast-output-default-failed = تعذّر اختيار الجهاز الافتراضي للنظام

nav-pin = تثبيت
nav-nothing-pinned = لا شيء هنا
nav-pins-alphabetical = أبجديًا
nav-pins-kind = حسب النوع
nav-show-full-library = إظهار المكتبة كاملة
nav-return-top = العودة إلى الأعلى

# trouble
trouble-offline = لا يوجد اتصال
trouble-offline-detail = تحقق من اتصالك بالإنترنت ثم أعد المحاولة.
trouble-not-loaded = تعذّر التحميل
trouble-retry = إعادة المحاولة
offline-title = أنت غير متصل.
offline-go-online = الاتصال بالإنترنت
toast-percent = { $percent }٪
toast-settings-broken = أصلح السطر { $name } في settings.json لحفظ التغييرات
toast-tray-unavailable = لا يوجد شريط نظام لوضع الأيقونة فيه
toast-undo = تراجع
toast-link-copied = تم نسخ الرابط
toast-link-unreadable = هذا الرابط لا يؤدي إلى شيء يستطيع Encore فتحه
menu-copy-link-none = لا يوجد رابط لمشاركته
menu-copy-link-local = لا روابط للفنانين وقوائم التشغيل المحلية
toast-settings-save-failed = تعذّر حفظ الإعدادات
toast-settings-overwritten = تغيّر ملف settings.json على القرص، لذا لم يُحفظ تغييرك الأخير
toast-library-scanned = { $count ->
    [one] تم فحص المكتبة، وعُثر على أغنية واحدة
   *[other] تم فحص المكتبة، وعُثر على { $count } أغانٍ
}
toast-scan-failed = تعذّر فحص مجلدات الموسيقى
toast-folder-removed = تمت إزالة { $name }
toast-folder-missing = لم يعد { $name } موجودًا على القرص
toast-history-removed = { $count ->
    [one] تمت الإزالة من السجل
   *[other] تمت إزالة { $count } أغانٍ من السجل
}
toast-history-remove-failed = تعذّرت إزالة أغنية من السجل
toast-history-restore-failed = تعذّرت استعادة السجل
toast-history-clear-failed = تعذّر مسح السجل

# power
wake-reason = الموسيقى قيد التشغيل

toast-sample-plain = تم إنشاء الحدث
toast-sample-done = تم الحفظ في مكتبتك
toast-sample-info = احضر قبل الحدث بعشر دقائق.
toast-sample-warning = تنتهي جلستك خلال خمس دقائق
toast-sample-failed = تعذّر الوصول إلى الخادم
toast-sample-loading = جارٍ رفع مقاطعك
toast-sample-event = تم إنشاء الحدث
toast-sample-event-detail = الأحد، 3 ديسمبر الساعة 9:00 صباحًا
toast-sample-delete = تم حذف { $name }
toast-sample-undo = تراجع
toast-sample-cancel = إلغاء
toast-sample-undone = تم التراجع
toast-sample-queued = تمت إضافة { $name } إلى قائمة الانتظار
toast-sample-long = يقول هذا الإشعار أكثر بكثير من غيره، لذا يجب أن يلتف النص إلى سطر ثانٍ وربما ثالث دون دفع زر الإغلاق خارج البطاقة
toast-sample-saving = جارٍ حفظ { $name }
toast-sample-saved = تم حفظ { $name }
toast-sample-save-failed = تعذّر حفظ { $name }
toast-sample-save-failed-detail = أجاب الخادم بالرمز 503، الخدمة غير متاحة
toast-sample-lasting = يبقى هذا الإشعار حتى تغلقه
toast-sample-burst = الإشعار رقم { $name }

# Frequency response graph
response-graph-hz = { $value } هرتز
response-graph-khz = { $value } كيلوهرتز
response-graph-db = { $value } ديسيبل
response-graph-readout = { $frequency } · { $direction ->
        [up] +{ $gain }
        [down] -{ $gain }
       *[flat] { $gain }
    } ديسيبل

settings-theme-explore = استكشاف سمات المجتمع
settings-theme-explore-detail = اعثر على سمات صنعها آخرون وثبّتها بنقرة واحدة
settings-theme-delete = حذف السمة
settings-theme-delete-title = حذف { $name }؟
settings-theme-delete-detail = ستُزال من مجلد السمات. لا يمكن التراجع عن ذلك.
theme-install-title = تثبيت { $name }؟
theme-install-detail = ستُضاف إلى مجلد السمات.
theme-install-confirm = تثبيت
toast-theme-link-unreadable = رابط السمة هذا تالف أو لا يحتوي على سمة
toast-theme-install-failed = تعذّر تثبيت { $name }
toast-theme-delete-failed = تعذّر حذف { $name }
