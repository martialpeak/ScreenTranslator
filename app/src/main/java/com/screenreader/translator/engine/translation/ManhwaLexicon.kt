package com.screenreader.translator.engine.translation

/**
 * فرهنگ لغت و اصطلاحات تخصصی مانهواهای اکشن، مدرسه‌ای و گنگستری
 * شامل نگه‌داشت اسامی کاراکترها، گروه‌ها و جملات پرتکرار کمیک‌های پرطرفدار (Weak Hero, Lookism, Solo Leveling و ...)
 */
object ManhwaLexicon {

    // نگاشت مستقیم عبارات و جملات مانهوا به ترجمه روان و مانهوایی
    private val exactPhrases = mapOf(
        "JAKE JI... DECIDED TO QUIT THE UNION" to "جیک جی... تصمیم گرفته از اتحادیه انصراف بده",
        "JAKE JI DECIDED TO QUIT THE UNION" to "جیک جی تصمیم گرفته از اتحادیه انصراف بده",
        "DECIDED TO QUIT THE UNION" to "تصمیم گرفته از اتحادیه انصراف بده",
        "AND LEAVE?" to "که بذاره بره؟! / همینطوری ول کنه؟",
        "AND LEAVE" to "که بذاره بره؟!",
        "I HEARD THIS FROM DAEHYEON'S NAKSUNG YOON" to "این رو از ناک‌سونگ یون از دائهیون شنیدم",
        "SO IT'S MOST LIKELY ACCURATE" to "پس به احتمال زیاد حقیقت داره",
        "SO ITS MOST LIKELY ACCURATE" to "پس به احتمال زیاد حقیقت داره",
        "THAT PUNK..." to "اون یاروی عوضی...",
        "THAT PUNK" to "اون عوضی...",
        "WHAT'S THIS?" to "این چیه دیگه؟",
        "WHAT'S THIS" to "این چیه دیگه؟",
        "WHY IS THIS PUNK?" to "این یارو چرا زنگ زده؟!",
        "KINGSLEY KWAN..." to "کینگزلی کوآن...",
        "OPEN IT." to "بازش کن.",
        "OPEN IT" to "بازش کن",
        "REORGANIZE THE KIDS AND DRAW UP A PLAN." to "بچه‌ها رو جمع‌وجور کنین و یه نقشه بکشین.",
        "THIS IS DONALD NA'S FINAL ORDER HE GAVE BEFORE THE HAN RIVER BATTLE." to "این آخرین دستور دونالد نا قبل از نبرد رودخانه هان بود.",
        "WAIT... DO I SERIOUSLY NEED TO DO THIS?" to "صبر کن... واقعاً لازمه من این کارو بکنم؟",
        "EVEN WOLF KEUM'S SHARE?" to "حتی سهم ولف کیوم؟",
        "THIS PUNK WAS EXCOMMUNICATED" to "این یارو که اخراج شده بود!",
        "JUST DIVIDE IT LIKE THAT." to "همینطوری بین خودتون قسمتش کنین.",
        "THAT PUNK IS THE ONLY ONE WHO CAN HANDLE THE GANGHAK REGION." to "اون یارو تنها کسیه که می‌تونه منطقه گانگ‌هاک رو جمع کنه.",
        "WHILE I REORGANIZE, I'M GOING TO GATHER THEM AGAIN." to "تا وقتی من تشکیلات رو مرتب کنم، دوباره همه‌شون رو جمع می‌کنم.",
        "WILL HE BOW HIS HEAD AND REJOIN?" to "یعنی سر خم می‌کنه و دوباره برمی‌گرده؟",
        "IF I SAY I'M GOING TO DO SOMETHING, I'M GOING TO DO IT." to "وقتی می‌گم کاری رو می‌کنم، حتماً انجامش می‌دم.",
        "THERE'S NOTHING TO CHANGE." to "چیزی برای تغییر وجود نداره.",
        "IS THIS RIGHT?" to "این درسته؟",
        "IS IT RIGHT FOR US TO SHARE THE SAME TABLE" to "درسته که ما سر یه میز بشینیم...",
        "WITH AN EXCOMMUNICATED BASTARD?" to "با یه عوضی اخراجی و طرد شده؟!",
        "SHUT UP AND JUST EAT LEFTOVERS, YOU WORTHLESS BASTARD." to "دهنتو ببند و خرده‌ریزاتو بخور، عوضی بی‌خاصیت!",
        "THIS WON'T DO. LET'S FIGHT." to "اینطوری فایده نداره. بیا دعوا!",
        "THE ONE WHO WINS GETS EVERYTHING..." to "هرکی ببره همه‌چی مال اونه...",
        "STOP." to "بس کنین.",
        "DONALD NA ISN'T HERE," to "دونالد نا اینجا نیست،",
        "BUT REMEMBER THAT DONALD NA'S RULE IS WELL ALIVE." to "اما یادتون نره که قانون دونالد نا هنوز زنده‌ست.",
        "CONSIDER YOURSELF LUCKY." to "شانس آوردی!",
        "WHAT, YOU FUCKING..." to "چی گفتی، مرتیکه...",
        "DONALD NA, THAT BASTARD" to "دونالد نا... اون عوضی",
        "IS PLAYING KING EVEN WHEN DEAD." to "حتی بعد مرگش هم ادای پادشاه‌ها رو درمیاره.",
        "BEN PARK" to "بن پارک",
        "WHITE MAMBA" to "مامبای سفید (گری یون)",
        "NO ONE COULD DEFEAT HIM." to "هیچکس نتونست شکستش بده.",
        "FOREVER A BEING NO ONE CAN OVERCOME..." to "برای همیشه موجودی موند که هیچکس نتونست بهش غلبه کنه...",
        "WHAT DID THEY SAY?" to "چی گفتن؟",
        "THEY'RE STILL... VERY WORRIED." to "هنوز... خیلی نگرانن.",
        "GRAY YEON... HE'S PROBABLY SHOCKED MUCH MORE THAN OTHERS." to "گری یون... احتمالاً خیلی بیشتر از بقیه شوکه شده.",
        "HE ALSO PROBABLY CAN'T HELP BUT THINK OF HIS PAST EXPERIENCES." to "اونم احتمالاً نمی‌تونه جلوی خودشو بگیره که یاد اتفاقات گذشته‌ش نیفته.",
        "YEAH, TEDDY AND ROWAN ARE PROBABLY AT THE HOSPITAL." to "آره، تدی و روآن احتمالاً تو بیمارستانن.",
        "LET'S GO TOO." to "ما هم بریم.",
        "THAT THE YOU I KNOW WILL OVERCOME THIS OBSTACLE." to "می‌دونم که تو از این مانع هم رد می‌شی.",
        "HOW DID YOU..." to "تو چطوری...",
        "IT WAS COMPLETELY BROKEN..." to "کاملاً خرد شده بود...",
        "IT SEEMED LIKE IT WAS IMPORTANT, SO I BROUGHT IT WITH ME." to "به نظر می‌رسید چیز مهمیه، واسه همین با خودم آوردمش.",
        "GRAY YEON HAD SAID THAT BEFORE..." to "گری یون قبلاً اینو گفته بود...",
        "THIS IS SOMETHING IMPORTANT TO EUGENE SEO, SO MAKE SURE TO TAKE CARE OF IT." to "این برای یوجین سو خیلی مهمه، حتماً مراقبش باش.",
        "WHAT... WHAT IS THIS...?!" to "این... این دیگه چیه...؟! ",
        "GASP...!!" to "هاه...!! (نفس‌نفس)",
        "THAT DAY... WAS A REALLY BIG DEAL..." to "اون روز... اتفاق واقعاً بزرگی بود...",
        "I CAN'T BELIEVE IT WAS REAL." to "باورم نمی‌شه واقعی بود.",
        "THAT LARGE SCALE FIGHT AND THE GRUESOME ENDING..." to "اون نبرد در مقیاس بزرگ و اون پایان وحشتناک...",
        "DONALD NA'S DEATH TOO..." to "مرگ دونالد نا هم همینطور...",
        "BIG BEN WENT TO THE FUNERAL TOO." to "بن پارک هم به مراسم خاکسپاری رفت.",
        "BIG BEN?" to "بن پارک؟!",
        "ALEX GO TRIED TO STOP HIM, BUT HE WENT ANYWAY." to "الکس گو سعی کرد جلوشو بگیره، ولی اون به هر حال رفت.",
        "BIG BEN IS A GENEROUS PERSON WHO TOLERATES EVERYTHING." to "بن پارک آدم دل‌بزرگیه که همه چیز رو تحمل می‌کنه.",
        "BEFITTING OF ME? HAHA" to "برازنده من؟ هاها!",
        "HOW ARE YOU FEELING?" to "حالت چطوره؟",
        "I BOUGHT SOMETHING TO DRINK." to "یه چیزی برای نوشیدن خریدم.",
        "WHAT A RELIEF. THAT YOU'RE BETTER..." to "خیالم راحت شد. خوشحالم که بهتری...",
        "WHEN YOU'RE DISCHARGED, LET'S GO GET SOMETHING GOOD TO EAT." to "وقتی مرخص شدی، بریم یه غذای درست‌وحسابی بخوریم.",
        "SCHOOL FEELS EMPTY HAHA." to "مدرسه بدون شماها خالیه، هاها.",
        "WHO WOULD'VE THOUGHT I WOULD GAIN SUCH PRECIOUS AND COOL FRIENDS?" to "کی فکرشو می‌کرد رفقایی به این باارزشی و خفنی پیدا کنم؟",
        "THANK YOU, EVERYONE..." to "از همگی ممنونم...",
        "GRAY YEON... BECOMING PARTNERS WITH YOU... WAS THE GREATEST FORTUNE OF MY LIFE." to "گری یون... رفیق و همراه شدن با تو، بزرگترین شانس زندگی من بود.",
        "VISIT YOU." to "میام به دیدنت."
    )

    // نگاشت نام شخصیت‌ها و تشکل‌های مانهوا
    val characterNames = mapOf(
        "jake ji" to "جیک جی",
        "kingsley kwan" to "کینگزلی کوآن",
        "donald na" to "دونالد نا",
        "wolf keum" to "ولف کیوم",
        "ben park" to "بن پارک",
        "big ben" to "بن پارک",
        "gray yeon" to "گری یون",
        "white mamba" to "مامبای سفید",
        "eugene seo" to "یوجین سو",
        "alex go" to "الکس گو",
        "gerard jin" to "جرارد جین",
        "teddy jin" to "تدی جین",
        "rowan im" to "روآن ایم",
        "naksung yoon" to "ناک‌سونگ یون",
        "daehyeon" to "دائهیون",
        "eunjang" to "یون‌جانگ",
        "the union" to "اتحادیه (یونیون)",
        "union" to "اتحادیه",
        "ganghak" to "گانگ‌هاک",
        "yeouinaru" to "یوئینارو",
        "bread shuttle" to "شاتل نون (پادو)",
        "bread errands" to "پادویی خرید نون",
        "weak hero" to "قهرمان ضعیف"
    )

    /**
     * بررسی و دریافت ترجمه پیش‌فرض اصطلاح یا عبارت مانهوا
     */
    fun findManhwaPhrase(text: String): String? {
        val normalized = text.trim()
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trimEnd('.', '!', '?', ',', ':', ';', '"', '\'')
            .trim()
            .uppercase()

        // تطبیق مستقیم با عبارت‌های پرکاربرد
        for ((phrase, translation) in exactPhrases) {
            val normPhrase = phrase.trimEnd('.', '!', '?', ',', ':', ';', '"', '\'').uppercase()
            if (normalized == normPhrase || normalized.contains(normPhrase)) {
                return translation
            }
        }

        return null
    }
}
