package com.screenreader.translator.engine.ocr

/**
 * موتور تخصصی پاکسازی، تفکیک کلمات چسبیده و نرمال‌سازی متون انگلیسی کمیک و مانهوا
 * این کلاس خطاهای ناشی از فاصله کم حروف در فونت‌های کمیک (Kerning) نظیر "SOI" -> "SO I"
 * و شکستگی کلمات در انتهای خطوط نظیر "SOME- THING" -> "SOMETHING" را پیش از ارسال به مترجم برطرف می‌سازد.
 */
object ComicTextNormalizer {

    // الگوهای کلمات پرکاربرد که در فونت‌های کمیک به ضمیر I چسبیده می‌شوند
    private val gluedReplacements = listOf(
        // حل مشکل گزارش‌شده کاربر: "SO I" -> "SOI"
        Regex("\\bSOI\\b") to "SO I",
        Regex("\\bSoi\\b") to "So I",
        Regex("\\bAMI\\b") to "AM I",
        Regex("\\bAmi\\b") to "Am I",
        Regex("\\bANDI\\b") to "AND I",
        Regex("\\bAndi\\b") to "And I",
        Regex("\\bBUTI\\b") to "BUT I",
        Regex("\\bButi\\b") to "But I",
        Regex("\\bIFI\\b") to "IF I",
        Regex("\\bIfi\\b") to "If I",
        Regex("\\bASIF\\b") to "AS IF",
        Regex("\\bAsif\\b") to "As If",
        Regex("\\bCANI\\b") to "CAN I",
        Regex("\\bCani\\b") to "Can I",
        Regex("\\bMAYI\\b") to "MAY I",
        Regex("\\bMayi\\b") to "May I",
        Regex("\\bWILLI\\b") to "WILL I",
        Regex("\\bWilli\\b") to "Will I",
        Regex("\\bSHALLI\\b") to "SHALL I",
        Regex("\\bShalli\\b") to "Shall I",
        Regex("\\bDIDI\\b") to "DID I",
        Regex("\\bDidi\\b") to "Did I",
        Regex("\\bHOWI\\b") to "HOW I",
        Regex("\\bHowi\\b") to "How I",
        Regex("\\bWHYI\\b") to "WHY I",
        Regex("\\bWhyi\\b") to "Why I",
        Regex("\\bSHOULDI\\b") to "SHOULD I",
        Regex("\\bShouldi\\b") to "Should I",
        Regex("\\bCOULDI\\b") to "COULD I",
        Regex("\\bCouldi\\b") to "Could I",
        Regex("\\bWOULDI\\b") to "WOULD I",
        Regex("\\bWouldi\\b") to "Would I",
        Regex("\\bMIGHTI\\b") to "MIGHT I",
        Regex("\\bMighti\\b") to "Might I",
        Regex("\\bMUSTI\\b") to "MUST I",
        Regex("\\bMusti\\b") to "Must I",

        // چسبیدن ضمیر I به ابتدای افعال رایج
        Regex("\\bIAM\\b") to "I AM",
        Regex("\\bIam\\b") to "I am",
        Regex("\\bIWAS\\b") to "I WAS",
        Regex("\\bIwas\\b") to "I was",
        Regex("\\bIHAVE\\b") to "I HAVE",
        Regex("\\bIhave\\b") to "I have",
        Regex("\\bIHAD\\b") to "I HAD",
        Regex("\\bIhad\\b") to "I had",
        Regex("\\bICAN\\b") to "I CAN",
        Regex("\\bIcan\\b") to "I can",
        Regex("\\bICANT\\b") to "I CAN'T",
        Regex("\\bIcant\\b") to "I can't",
        Regex("\\bIDID\\b") to "I DID",
        Regex("\\bIdid\\b") to "I did",
        Regex("\\bIDIDNT\\b") to "I DIDN'T",
        Regex("\\bIdidnt\\b") to "I didn't",
        Regex("\\bIDONT\\b") to "I DON'T",
        Regex("\\bIdont\\b") to "I don't",
        Regex("\\bIWONT\\b") to "I WON'T",
        Regex("\\bIwont\\b") to "I won't",
        Regex("\\bIWILL\\b") to "I WILL",
        Regex("\\bIwill\\b") to "I will",
        Regex("\\bIWISH\\b") to "I WISH",
        Regex("\\bIwish\\b") to "I wish",
        Regex("\\bIWANT\\b") to "I WANT",
        Regex("\\bIwant\\b") to "I want",
        Regex("\\bIKNOW\\b") to "I KNOW",
        Regex("\\bIknow\\b") to "I know",
        Regex("\\bITHINK\\b") to "I THINK",
        Regex("\\bIthink\\b") to "I think",
        Regex("\\bIHOPE\\b") to "I HOPE",
        Regex("\\bIhope\\b") to "I hope",
        Regex("\\bISEE\\b") to "I SEE",
        Regex("\\bIsee\\b") to "I see",
        Regex("\\bIMEAN\\b") to "I MEAN",
        Regex("\\bImean\\b") to "I mean",

        // چسبیدن افعال و ضمایر اشاره
        Regex("\\bTHISIS\\b") to "THIS IS",
        Regex("\\bThisis\\b") to "This is",
        Regex("\\bWHATIS\\b") to "WHAT IS",
        Regex("\\bWhatis\\b") to "What is",
        Regex("\\bTHATIS\\b") to "THAT IS",
        Regex("\\bThatis\\b") to "That is",
        Regex("\\bTHEREIS\\b") to "THERE IS",
        Regex("\\bThereis\\b") to "There is",
        Regex("\\bITIS\\b") to "IT IS",
        Regex("\\bItis\\b") to "It is",
        Regex("\\bITWAS\\b") to "IT WAS",
        Regex("\\bItwas\\b") to "It was",
        Regex("\\bITWILL\\b") to "IT WILL",
        Regex("\\bItwill\\b") to "It will",
        Regex("\\bYOUARE\\b") to "YOU ARE",
        Regex("\\bYouare\\b") to "You are",
        Regex("\\bWEARE\\b") to "WE ARE",
        Regex("\\bWeare\\b") to "We are",
        Regex("\\bTHEYARE\\b") to "THEY ARE",
        Regex("\\bTheyare\\b") to "They are",
        Regex("\\bWILLBE\\b") to "WILL BE",
        Regex("\\bWillbe\\b") to "Will be",
        Regex("\\bCANBE\\b") to "CAN BE",
        Regex("\\bCanbe\\b") to "Can be",

        // چسبیدن ضمیر ME به افعال دستوری
        Regex("\\bWITHME\\b") to "WITH ME",
        Regex("\\bWithme\\b") to "With me",
        Regex("\\bFORME\\b") to "FOR ME",
        Regex("\\bForme\\b") to "For me",
        Regex("\\bTOME\\b") to "TO ME",
        Regex("\\bTome\\b") to "To me",
        Regex("\\bLETME\\b") to "LET ME",
        Regex("\\bLetme\\b") to "Let me",
        Regex("\\bTELLME\\b") to "TELL ME",
        Regex("\\bTellme\\b") to "Tell me",
        Regex("\\bGIVEME\\b") to "GIVE ME",
        Regex("\\bGiveme\\b") to "Give me",
        Regex("\\bHELPME\\b") to "HELP ME",
        Regex("\\bHelpme\\b") to "Help me",
        Regex("\\bKILLME\\b") to "KILL ME",
        Regex("\\bKillme\\b") to "Kill me",
        Regex("\\bLEAVEME\\b") to "LEAVE ME",
        Regex("\\bLeaveme\\b") to "Leave me",
        Regex("\\bSAVEME\\b") to "SAVE ME",
        Regex("\\bSaveme\\b") to "Save me",
        Regex("\\bSHOWME\\b") to "SHOW ME",
        Regex("\\bShowme\\b") to "Show me",
        Regex("\\bHEARME\\b") to "HEAR ME",
        Regex("\\bHearme\\b") to "Hear me",

        // چسبیدن ضمیر YOU
        Regex("\\bWITHYOU\\b") to "WITH YOU",
        Regex("\\bWithyou\\b") to "With you",
        Regex("\\bFORYOU\\b") to "FOR YOU",
        Regex("\\bForyou\\b") to "For you",
        Regex("\\bTOYOU\\b") to "TO YOU",
        Regex("\\bToyou\\b") to "To you",
        Regex("\\bAREYOU\\b") to "ARE YOU",
        Regex("\\bAreyou\\b") to "Are you",
        Regex("\\bDIDYOU\\b") to "DID YOU",
        Regex("\\bDidyou\\b") to "Did you",
        Regex("\\bCANYOU\\b") to "CAN YOU",
        Regex("\\bCanyou\\b") to "Can you",
        Regex("\\bWILLYOU\\b") to "WILL YOU",
        Regex("\\bWillyou\\b") to "Will you",
        Regex("\\bHAVEYOU\\b") to "HAVE YOU",
        Regex("\\bHaveyou\\b") to "Have you",
        Regex("\\bDOYOU\\b") to "DO YOU",
        Regex("\\bDoyou\\b") to "Do you",
        Regex("\\bTHANKYOU\\b") to "THANK YOU",
        Regex("\\bThankyou\\b") to "Thank you",

        // چسبیدن ضمیر IT
        Regex("\\bINIT\\b") to "IN IT",
        Regex("\\bInit\\b") to "In it",
        Regex("\\bONIT\\b") to "ON IT",
        Regex("\\bOnit\\b") to "On it",
        Regex("\\bTOIT\\b") to "TO IT",
        Regex("\\bToit\\b") to "To it",
        Regex("\\bFORIT\\b") to "FOR IT",
        Regex("\\bForit\\b") to "For it",
        Regex("\\bWITHIT\\b") to "WITH IT",
        Regex("\\bWithit\\b") to "With it",
        Regex("\\bDIDIT\\b") to "DID IT",
        Regex("\\bDidit\\b") to "Did it",
        Regex("\\bDOIT\\b") to "DO IT",
        Regex("\\bDoit\\b") to "Do it",
        Regex("\\bISIT\\b") to "IS IT",
        Regex("\\bIsit\\b") to "Is it",
        Regex("\\bWASIT\\b") to "WAS IT",
        Regex("\\bWasit\\b") to "Was it",
        Regex("\\bGETIT\\b") to "GET IT",
        Regex("\\bGetit\\b") to "Get it",
        Regex("\\bTAKEIT\\b") to "TAKE IT",
        Regex("\\bTakeit\\b") to "Take it",
        Regex("\\bSTOPIT\\b") to "STOP IT",
        Regex("\\bStopit\\b") to "Stop it",
        Regex("\\bLIKEIT\\b") to "LIKE IT",
        Regex("\\bLikeit\\b") to "Like it",
        Regex("\\bGOTIT\\b") to "GOT IT",
        Regex("\\bGotit\\b") to "Got it"
    )

    // اصلاح انقباض‌های انگلیسی با فاصله اشتباه یا آپاستروف گم‌شده
    private val contractionFixes = listOf(
        Regex("\\b([A-Za-z]+)\\s+['’`]?\\s*(T|t)\\b") to "$1'$2",   // DON T -> DON'T
        Regex("\\b([A-Za-z]+)\\s+['’`]?\\s*(S|s)\\b") to "$1'$2",   // IT S -> IT'S
        Regex("\\b([A-Za-z]+)\\s+['’`]?\\s*(M|m)\\b") to "$1'$2",   // I M -> I'M
        Regex("\\b([A-Za-z]+)\\s+['’`]?\\s*(D|d)\\b") to "$1'$2",   // I D -> I'D
        Regex("\\b([A-Za-z]+)\\s+['’`]?\\s*(RE|re)\\b") to "$1'$2", // YOU RE -> YOU'RE
        Regex("\\b([A-Za-z]+)\\s+['’`]?\\s*(VE|ve)\\b") to "$1'$2", // WE VE -> WE'VE
        Regex("\\b([A-Za-z]+)\\s+['’`]?\\s*(LL|ll)\\b") to "$1'$2"  // I LL -> I'LL
    )

    // خطاهای بسیار شایع OCR ناشی از شباهت کاراکترها در فونت‌های کمیک (نظیر عدد 1 به جای حرف I یا P به جای D)
    private val comicOcrFixes = listOf(
        Regex("\\b1S\\b") to "IS",
        Regex("\\b1s\\b") to "is",
        Regex("\\b1SN'T\\b") to "ISN'T",
        Regex("\\b1sn't\\b") to "isn't",
        Regex("\\b1T\\b") to "IT",
        Regex("\\b1t\\b") to "it",
        Regex("\\b1TS\\b") to "IT'S",
        Regex("\\b1ts\\b") to "it's",
        Regex("\\b1F\\b") to "IF",
        Regex("\\b1f\\b") to "if",
        Regex("\\b1N\\b") to "IN",
        Regex("\\b1n\\b") to "in",
        Regex("\\b1M\\b") to "I'M",
        Regex("\\b1m\\b") to "i'm",
        Regex("\\b1LL\\b") to "I'LL",
        Regex("\\b1ll\\b") to "i'll",
        Regex("\\b1\\s+(AM|WAS|HAVE|HAD|CAN|WILL|WOULD|COULD|SHOULD|DID|DO|DONT|DON'T)\\b", RegexOption.IGNORE_CASE) to "I $1",
        Regex("\\bPEFEAT\\b") to "DEFEAT",
        Regex("\\bpefeat\\b") to "defeat",
        Regex("\\bPEFEATED\\b") to "DEFEATED",
        Regex("\\bpefeated\\b") to "defeated",
        Regex("\\bFlGHT\\b") to "FIGHT",
        Regex("\\bflght\\b") to "fight",
        Regex("\\bRUNNINGYOUR\\b") to "RUNNING YOUR",
        Regex("\\brunningyour\\b") to "running your"
    )

    /**
     * اجرای کامل پایپ‌لاین تمیزکاری و نرمال‌سازی متن بالون کمیک
     */
    fun normalize(rawText: String): String {
        if (rawText.isBlank()) return ""

        var text = rawText

        // ۱. حل شکستگی خطوط با خط تیره (مانند SOME-\nTHING یا SOME- THING -> SOMETHING)
        text = text.replace(Regex("([A-Za-z]+)-\\s+([A-Za-z]+)"), "$1$2")
        text = text.replace(Regex("([A-Za-z]+)-[\\r\\n]+([A-Za-z]+)"), "$1$2")

        // ۲. نرمال‌سازی اینترها و فاصله‌های اضافه
        text = text.replace(Regex("[\\r\\n]+"), " ")
        text = text.replace(Regex("\\s+"), " ").trim()

        // ۳. تفکیک کلمات چسبیده ناشی از فونت‌های کمیک (مانند SOI -> SO I)
        for ((pattern, replacement) in gluedReplacements) {
            text = text.replace(pattern, replacement)
        }

        // ۳.۵. اصلاح خطاهای متداول OCR در فونت‌های کمیک
        for ((pattern, replacement) in comicOcrFixes) {
            text = text.replace(pattern, replacement)
        }

        // ۴. اصلاح آپاستروف‌های جدا شده
        for ((pattern, replacement) in contractionFixes) {
            text = text.replace(pattern, replacement)
        }

        // ۵. تمیزکاری نقطه‌چین‌ها و علائم تعجب و سوال
        text = text.replace(Regex("\\.{2,}"), "...")
        text = text.replace(Regex("\\s+([.,!?:;])"), "$1")
        text = text.replace(Regex("\\s+"), " ").trim()

        return text
    }
}
