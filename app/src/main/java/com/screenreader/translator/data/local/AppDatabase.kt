package com.screenreader.translator.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.screenreader.translator.data.local.dao.GlossaryDao
import com.screenreader.translator.data.local.dao.HistoryDao
import com.screenreader.translator.data.local.dao.OfflineDictionaryDao
import com.screenreader.translator.data.local.dao.TranslationCacheDao
import com.screenreader.translator.data.local.entity.GlossaryEntity
import com.screenreader.translator.data.local.entity.HistoryEntity
import com.screenreader.translator.data.local.entity.OfflineDictionaryEntity
import com.screenreader.translator.data.local.entity.TranslationCacheEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        TranslationCacheEntity::class,
        HistoryEntity::class,
        GlossaryEntity::class,
        OfflineDictionaryEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun translationCacheDao(): TranslationCacheDao
    abstract fun historyDao(): HistoryDao
    abstract fun glossaryDao(): GlossaryDao
    abstract fun offlineDictionaryDao(): OfflineDictionaryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "screen_translator.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                val dbInstance = getInstance(context)
                                populateInitialData(dbInstance)
                                populateFromAssets(context, dbInstance)
                            }
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                val dbInstance = getInstance(context)
                                if (dbInstance.offlineDictionaryDao().getWordCount() < 4950) {
                                    populateInitialData(dbInstance)
                                    populateFromAssets(context, dbInstance)
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun populateFromAssets(context: Context, database: AppDatabase) {
            try {
                val inputStream = context.assets.open("offline_dictionary.tsv")
                val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8))
                val wordsToInsert = mutableListOf<OfflineDictionaryEntity>()
                val glossaryToInsert = mutableListOf<GlossaryEntity>()

                var line = reader.readLine()
                while (line != null) {
                    val parts = line.split("\t")
                    if (parts.size >= 2) {
                        val word = parts[0].trim()
                        val meaning = parts[1].trim()
                        val category = if (parts.size >= 3) parts[2].trim() else "عمومی"

                        if (category == "مانهوا" || category == "کره‌ای" || category == "چینی" || category == "اصطلاح" || category.contains("اسلنگ")) {
                            glossaryToInsert.add(
                                GlossaryEntity(
                                    originalTerm = word,
                                    customTranslation = meaning,
                                    category = category
                                )
                            )
                        }
                        wordsToInsert.add(
                            OfflineDictionaryEntity(
                                word = word,
                                persianMeanings = meaning,
                                partOfSpeech = category
                            )
                        )
                    }
                    line = reader.readLine()
                }
                reader.close()

                wordsToInsert.chunked(500).forEach { chunk ->
                    database.offlineDictionaryDao().insertAll(chunk)
                }
                glossaryToInsert.chunked(500).forEach { chunk ->
                    database.glossaryDao().insertAll(chunk)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private suspend fun populateInitialData(database: AppDatabase) {
            // ۱. دیکشنری لغات عمومی و واژگان کمیک
            val initialWords = listOf(
                OfflineDictionaryEntity(word = "Settings", persianMeanings = "تنظیمات", partOfSpeech = "noun"),
                OfflineDictionaryEntity(word = "Cancel", persianMeanings = "انصراف / لغو", partOfSpeech = "verb"),
                OfflineDictionaryEntity(word = "Confirm", persianMeanings = "تایید", partOfSpeech = "verb"),
                OfflineDictionaryEntity(word = "Save", persianMeanings = "ذخیره", partOfSpeech = "verb"),
                OfflineDictionaryEntity(word = "Delete", persianMeanings = "حذف", partOfSpeech = "verb"),
                OfflineDictionaryEntity(word = "Start", persianMeanings = "شروع", partOfSpeech = "verb"),
                OfflineDictionaryEntity(word = "Exit", persianMeanings = "خروج", partOfSpeech = "noun"),
                OfflineDictionaryEntity(word = "Loading", persianMeanings = "در حال بارگذاری...", partOfSpeech = "verb"),
                OfflineDictionaryEntity(word = "Success", persianMeanings = "موفقیت‌آمیز", partOfSpeech = "noun"),
                OfflineDictionaryEntity(word = "Error", persianMeanings = "خطا", partOfSpeech = "noun"),
                OfflineDictionaryEntity(word = "Hunter", persianMeanings = "شکارچی", partOfSpeech = "noun"),
                OfflineDictionaryEntity(word = "Guild", persianMeanings = "انجمن / صنف", partOfSpeech = "noun"),
                OfflineDictionaryEntity(word = "Dungeon", persianMeanings = "سیاه‌چال / دانجن", partOfSpeech = "noun"),
                OfflineDictionaryEntity(word = "Monarch", persianMeanings = "پادشاه / حاکم اعظم", partOfSpeech = "noun"),
                OfflineDictionaryEntity(word = "Shadow", persianMeanings = "سایه", partOfSpeech = "noun"),
                OfflineDictionaryEntity(word = "Sovereign", persianMeanings = "حاکم مطلق", partOfSpeech = "noun"),
                OfflineDictionaryEntity(word = "Awakening", persianMeanings = "بیداری", partOfSpeech = "noun"),
                OfflineDictionaryEntity(word = "Constellation", persianMeanings = "صورت فلکی", partOfSpeech = "noun"),
                OfflineDictionaryEntity(word = "Incarnation", persianMeanings = "تجسم زمینی", partOfSpeech = "noun"),
                OfflineDictionaryEntity(word = "Scenario", persianMeanings = "سناریو / ماجرا", partOfSpeech = "noun"),
                OfflineDictionaryEntity(word = "Ranker", persianMeanings = "رنکر (صعودکننده برتر)", partOfSpeech = "noun"),
                OfflineDictionaryEntity(word = "Cultivation", persianMeanings = "تهذیب / پرورش انرژی", partOfSpeech = "noun"),
                OfflineDictionaryEntity(word = "Dantian", persianMeanings = "دانتیان (مرکز انرژی چی)", partOfSpeech = "noun")
            )
            database.offlineDictionaryDao().insertAll(initialWords)

            // ۲. اصطلاح‌نامه تخصصی مانهوا و مانگا (Solo Leveling, ORV, Tower of God, Murim)
            val manhwaGlossary = listOf(
                // سولو لولینگ (Solo Leveling)
                GlossaryEntity(originalTerm = "E-RANK HUNTER", customTranslation = "شکارچی رتبه E", category = "مانهوا"),
                GlossaryEntity(originalTerm = "E-RANK", customTranslation = "رتبه E", category = "مانهوا"),
                GlossaryEntity(originalTerm = "S-RANK HUNTER", customTranslation = "شکارچی رتبه S", category = "مانهوا"),
                GlossaryEntity(originalTerm = "S-RANK", customTranslation = "رتبه S", category = "مانهوا"),
                GlossaryEntity(originalTerm = "A-RANK HUNTER", customTranslation = "شکارچی رتبه A", category = "مانهوا"),
                GlossaryEntity(originalTerm = "B-RANK HUNTER", customTranslation = "شکارچی رتبه B", category = "مانهوا"),
                GlossaryEntity(originalTerm = "C-RANK HUNTER", customTranslation = "شکارچی رتبه C", category = "مانهوا"),
                GlossaryEntity(originalTerm = "D-RANK HUNTER", customTranslation = "شکارچی رتبه D", category = "مانهوا"),
                GlossaryEntity(originalTerm = "THE HUNTER GUILD'S", customTranslation = "انجمن شکارچیان", category = "مانهوا"),
                GlossaryEntity(originalTerm = "THE HUNTER GUILD", customTranslation = "انجمن شکارچیان", category = "مانهوا"),
                GlossaryEntity(originalTerm = "HUNTER GUILD", customTranslation = "انجمن شکارچیان", category = "مانهوا"),
                GlossaryEntity(originalTerm = "HUNTERS ASSOCIATION", customTranslation = "انجمن شکارچیان", category = "مانهوا"),
                GlossaryEntity(originalTerm = "SHADOW MONARCH", customTranslation = "پادشاه سایه‌ها", category = "مانهوا"),
                GlossaryEntity(originalTerm = "SHADOW SOLDIER", customTranslation = "سرباز سایه", category = "مانهوا"),
                GlossaryEntity(originalTerm = "SHADOW ARMY", customTranslation = "سپاه سایه‌ها", category = "مانهوا"),
                GlossaryEntity(originalTerm = "ARISE", customTranslation = "برخیز (ARISE)", category = "مانهوا"),
                GlossaryEntity(originalTerm = "DUNGEON BREAK", customTranslation = "شکست دانجن (خروج هیولاها)", category = "مانهوا"),
                GlossaryEntity(originalTerm = "RED GATE", customTranslation = "دروازه سرخ (رد گیت)", category = "مانهوا"),
                GlossaryEntity(originalTerm = "DOUBLE DUNGEON", customTranslation = "دانجن دوگانه", category = "مانهوا"),
                GlossaryEntity(originalTerm = "NATIONAL LEVEL HUNTER", customTranslation = "شکارچی سطح ملی", category = "مانهوا"),
                GlossaryEntity(originalTerm = "MAGIC BEAST", customTranslation = "هیولای جادویی", category = "مانهوا"),
                GlossaryEntity(originalTerm = "BOSS MONSTER", customTranslation = "هیولای رئیس (باس)", category = "مانهوا"),
                GlossaryEntity(originalTerm = "STATUS WINDOW", customTranslation = "پنجره وضعیت", category = "مانهوا"),
                GlossaryEntity(originalTerm = "DAILY QUEST", customTranslation = "ماموریت روزانه", category = "مانهوا"),
                GlossaryEntity(originalTerm = "PENALTY QUEST", customTranslation = "ماموریت مجازات", category = "مانهوا"),
                GlossaryEntity(originalTerm = "PENALTY ZONE", customTranslation = "منطقه مجازات", category = "مانهوا"),
                GlossaryEntity(originalTerm = "SUNG JIN-WOO", customTranslation = "سونگ جین‌وو", category = "مانهوا"),
                GlossaryEntity(originalTerm = "CHA HAE-IN", customTranslation = "چا هه‌این", category = "مانهوا"),
                GlossaryEntity(originalTerm = "BERU", customTranslation = "برو (فرمانده مورچه‌ها)", category = "مانهوا"),
                GlossaryEntity(originalTerm = "IGRIT", customTranslation = "ایگریت (شوالیه سرخ)", category = "مانهوا"),

                // دیدگاه دانای کل (Omniscient Reader's Viewpoint)
                GlossaryEntity(originalTerm = "CONSTELLATION", customTranslation = "صورت فلکی", category = "مانهوا"),
                GlossaryEntity(originalTerm = "INCARNATION", customTranslation = "اینکارنیشن (تجسم زمینی)", category = "مانهوا"),
                GlossaryEntity(originalTerm = "DOKKAEBI", customTranslation = "دوکه‌بی (مدیر استریم)", category = "مانهوا"),
                GlossaryEntity(originalTerm = "MAIN SCENARIO", customTranslation = "سناریوی اصلی", category = "مانهوا"),
                GlossaryEntity(originalTerm = "SUB SCENARIO", customTranslation = "سناریوی فرعی", category = "مانهوا"),
                GlossaryEntity(originalTerm = "STIGMA", customTranslation = "استیگما (نشان قدرت)", category = "مانهوا"),
                GlossaryEntity(originalTerm = "FABLE", customTranslation = "داستان باستانی (فیبل)", category = "مانهوا"),
                GlossaryEntity(originalTerm = "KIM DOKJA", customTranslation = "کیم دوکجا", category = "مانهوا"),
                GlossaryEntity(originalTerm = "YOO JOONGHYUK", customTranslation = "یو جونگهیوک", category = "مانهوا"),
                GlossaryEntity(originalTerm = "HAN SOOYOUNG", customTranslation = "هان سویونگ", category = "مانهوا"),
                GlossaryEntity(originalTerm = "REGRESSOR", customTranslation = "بازگشت‌کننده در زمان", category = "مانهوا"),

                // برج خدا (Tower of God)
                GlossaryEntity(originalTerm = "REGULAR", customTranslation = "رگولار (صعودکننده)", category = "مانهوا"),
                GlossaryEntity(originalTerm = "IRREGULAR", customTranslation = "ایرگولار (ناهنجار)", category = "مانهوا"),
                GlossaryEntity(originalTerm = "RANKER", customTranslation = "رنکر", category = "مانهوا"),
                GlossaryEntity(originalTerm = "HIGH RANKER", customTranslation = "های رنکر", category = "مانهوا"),
                GlossaryEntity(originalTerm = "SHINSU", customTranslation = "شینسو (جوهر الهی)", category = "مانهوا"),
                GlossaryEntity(originalTerm = "ZAHARD", customTranslation = "زاهارد", category = "مانهوا"),
                GlossaryEntity(originalTerm = "TWENTY-FIFTH BAAM", customTranslation = "بیست و پنجمین باام", category = "مانهوا"),
                GlossaryEntity(originalTerm = "KHUN", customTranslation = "کون", category = "مانهوا"),
                GlossaryEntity(originalTerm = "RAK", customTranslation = "راک", category = "مانهوا"),

                // سبک موریم و هنرهای رزمی (Murim / Mount Hua Sect)
                GlossaryEntity(originalTerm = "MOUNT HUA SECT", customTranslation = "فرقه کوه هوا", category = "مانهوا"),
                GlossaryEntity(originalTerm = "HEAVENLY DEMON", customTranslation = "شیطان آسمانی", category = "مانهوا"),
                GlossaryEntity(originalTerm = "DEMONIC CULT", customTranslation = "فرقه شیطانی", category = "مانهوا"),
                GlossaryEntity(originalTerm = "PLUM BLOSSOM SWORD", customTranslation = "شمشیر شکوفه آلو", category = "مانهوا"),
                GlossaryEntity(originalTerm = "QI DEVIATION", customTranslation = "انحراف چی (آشفتگی درونی)", category = "مانهوا"),
                GlossaryEntity(originalTerm = "INNER QI", customTranslation = "چی درونی (انرژی حیات)", category = "مانهوا"),
                GlossaryEntity(originalTerm = "CHUNG MYUNG", customTranslation = "چونگ میونگ", category = "مانهوا"),
                GlossaryEntity(originalTerm = "SECT LEADER", customTranslation = "رهبر فرقه", category = "مانهوا"),
                GlossaryEntity(originalTerm = "ELDER", customTranslation = "ریش‌سفید / بزرگ‌تر", category = "مانهوا"),

                // اصوات، نفس‌ها و افکت‌های کمیک و مانهوا (Sound Effects)
                GlossaryEntity(originalTerm = "HAAH", customTranslation = "هااه... (نفس عمیق)", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "HUFF", customTranslation = "هاف... (نفس نفس زدن)", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "HUFF... HUFF...", customTranslation = "هاف... هاف... (نفس‌نفس)", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "PANT", customTranslation = "پنت... (نفس‌زنان)", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "PANT... PANT...", customTranslation = "پنت... پنت...", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "TCH", customTranslation = "چِه! (ابراز ناامیدی)", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "GASP", customTranslation = "وای! (حبس شدن نفس)", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "GUH", customTranslation = "گووه! (صدای درد ضربه)", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "UGH", customTranslation = "اوووف... (درد و خستگی)", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "ARGH", customTranslation = "آااارغ! (فریاد درد)", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "WHOOSH", customTranslation = "ووووش! (حرکت سریع باد)", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "SLASH", customTranslation = "شلااااش! (ضربه شمشیر)", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "BOOM", customTranslation = "بووووم! (انفجار)", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "CLANG", customTranslation = "کلنگ! (برخورد شمشیرها)", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "THUD", customTranslation = "تاپ! (افتادن سنگین روی زمین)", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "CRACK", customTranslation = "تق! (شکستن استخوان/سنگ)", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "GULP", customTranslation = "قولپ... (قورت دادن آب دهان)", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "SMIRK", customTranslation = "پوزخند زدن", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "GLARE", customTranslation = "خیره شدن با خشم", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "RUMBLE", customTranslation = "غرش زمین / لرزیدن سنگ‌ها", category = "افکت صوتی"),
                GlossaryEntity(originalTerm = "SIGH", customTranslation = "آه کشیدن...", category = "افکت صوتی"),

                // اصطلاحات و افکت‌های صوتی کره‌ای مانهوا (Hangul)
                GlossaryEntity(originalTerm = "하아", customTranslation = "هااه... (نفس عمیق)", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "하악", customTranslation = "هاک... (نفس‌نفس)", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "헉", customTranslation = "هک! (جا خوردن)", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "콰앙", customTranslation = "کوااانگ! (برخورد شدید)", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "쿠구구", customTranslation = "کوگوگو... (لرزش زمین)", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "슥", customTranslation = "سِک... (حرکت نرم)", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "팟", customTranslation = "پات! (ظهور ناگهانی)", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "피식", customTranslation = "فیشیک... (پوزخند)", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "꿀꺽", customTranslation = "قولپ... (قورت دادن آب دهان)", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "털썩", customTranslation = "تالسوک... (افتادن روی زمین)", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "쳇", customTranslation = "چت! (ابراز ناامیدی)", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "으윽", customTranslation = "اوووک... (ناله از درد)", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "끼에엑", customTranslation = "کیه‌اِک! (جیغ هیولا)", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "헌터", customTranslation = "شکارچی", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "상태창", customTranslation = "پنجره وضعیت", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "일어나라", customTranslation = "برخیز (ARISE)", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "그림자 군주", customTranslation = "پادشاه سایه‌ها", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "성진우", customTranslation = "سونگ جین‌وو", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "게이트", customTranslation = "گیت / دروازه", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "던전", customTranslation = "دانجن (سیاه‌چال)", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "마수", customTranslation = "هیولای جادویی", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "레벨업", customTranslation = "افزایش سطح (لول‌آپ)!", category = "کره‌ای"),
                GlossaryEntity(originalTerm = "퀘스트", customTranslation = "ماموریت (Quest)", category = "کره‌ای"),

                // اصطلاحات و افکت‌های صوتی چینی مانهوا (Hanzi)
                GlossaryEntity(originalTerm = "轰", customTranslation = "هوووم! (صدای غرش و انفجار)", category = "چینی"),
                GlossaryEntity(originalTerm = "砰", customTranslation = "پنگ! (صدای ضربه محکم)", category = "چینی"),
                GlossaryEntity(originalTerm = "哈", customTranslation = "هاه...", category = "چینی"),
                GlossaryEntity(originalTerm = "啊", customTranslation = "آاااه!", category = "چینی"),
                GlossaryEntity(originalTerm = "斩", customTranslation = "شلااااش! (برش شمشیر)", category = "چینی"),
                GlossaryEntity(originalTerm = "切", customTranslation = "چِه! (پوزخند)", category = "چینی"),
                GlossaryEntity(originalTerm = "猎人", customTranslation = "شکارچی", category = "چینی"),
                GlossaryEntity(originalTerm = "宗主", customTranslation = "رهبر فرقه", category = "چینی"),
                GlossaryEntity(originalTerm = "长老", customTranslation = "بزرگ‌تر / ریش‌سفید", category = "چینی"),
                GlossaryEntity(originalTerm = "丹田", customTranslation = "دانتیان", category = "چینی"),
                GlossaryEntity(originalTerm = "灵气", customTranslation = "انرژی درونی چی", category = "چینی"),

                // عبارات Solo Leveling و دیالوگ‌های کمیک
                GlossaryEntity(originalTerm = "ALWAYS GETTING HURT AND EVEN HAD NEAR DEATH EXPERIENCES", customTranslation = "برعکس، من همیشه صدمه می‌دیدم و حتی تجربه‌های دمِ مرگ هم داشتم.", category = "مانهوا"),
                GlossaryEntity(originalTerm = "ALWAYS GETTING HURT", customTranslation = "همیشه صدمه می‌دیدم", category = "مانهوا"),
                GlossaryEntity(originalTerm = "AND EVEN HAD NEAR DEATH EXPERIENCES", customTranslation = "و حتی تجربه‌های دمِ مرگ هم داشتم", category = "مانهوا"),
                GlossaryEntity(originalTerm = "THE JOB WHERE YOUR LIFE'S ON THE LINE, THE HUNTER", customTranslation = "شغلی که جونت کفِ دستته، هانتر بودن...", category = "مانهوا"),
                GlossaryEntity(originalTerm = "THE JOB WHERE YOUR LIFE'S ON THE LINE", customTranslation = "شغلی که جونت کفِ دستته", category = "مانهوا"),
                GlossaryEntity(originalTerm = "YOUR LIFE'S ON THE LINE", customTranslation = "جونت کفِ دستته", category = "مانهوا"),
                GlossaryEntity(originalTerm = "LIFE'S ON THE LINE", customTranslation = "جونت کفِ دستته", category = "مانهوا"),
                GlossaryEntity(originalTerm = "BECAUSE I LIKE IT", customTranslation = "چون دوستش دارم؟! هه...", category = "مانهوا"),
                GlossaryEntity(originalTerm = "NEAR DEATH", customTranslation = "دم مرگ", category = "مانهوا"),
                GlossaryEntity(originalTerm = "ON THE CONTRARY", customTranslation = "برعکس", category = "مانهوا")
            )
            for (item in manhwaGlossary) {
                database.glossaryDao().insert(item)
            }

            // ۳. پیش‌بارگذاری کش دیتابیس با جملات مانهوا جهت بارگذاری آنی (0ms)
            val preloadedCache = listOf(
                Pair("e-rank hunter", "شکارچی رتبه E"),
                Pair("the hunter guild's", "انجمن شکارچیان"),
                Pair("the hunter guild", "انجمن شکارچیان"),
                Pair("hunter guild", "انجمن شکارچیان"),
                Pair("always getting hurt and even had near death experiences.", "برعکس، من همیشه صدمه می‌دیدم و حتی تجربه‌های دمِ مرگ هم داشتم."),
                Pair("always getting hurt and even had near death experiences", "برعکس، من همیشه صدمه می‌دیدم و حتی تجربه‌های دمِ مرگ هم داشتم."),
                Pair("always getting hurt", "همیشه صدمه می‌دیدم"),
                Pair("and even had near death experiences.", "و حتی تجربه‌های دمِ مرگ هم داشتم."),
                Pair("and even had near death experiences", "و حتی تجربه‌های دمِ مرگ هم داشتم."),
                Pair("the job where your life's on the line, the hunter", "شغلی که جونت کفِ دستته، هانتر بودن..."),
                Pair("the job where your life's on the line, the hunter.", "شغلی که جونت کفِ دستته، هانتر بودن..."),
                Pair("the job where your life's on the line", "شغلی که جونت کفِ دستته"),
                Pair("your life's on the line", "جونت کفِ دستته"),
                Pair("life's on the line", "جونت کفِ دستته"),
                Pair("because i like it!", "چون دوستش دارم؟! هه..."),
                Pair("because i like it?!", "چون دوستش دارم؟!"),
                Pair("because i like it", "چون دوستش دارم؟!"),
                Pair("haah", "هااه..."),
                Pair("huff... huff...", "هاف... هاف..."),
                Pair("pant... pant...", "پنت... پنت..."),
                Pair("what the hell?!", "این دیگه چه جهنمیه؟!"),
                Pair("damn it!", "لعنتی!"),
                Pair("i have to survive.", "باید زنده بمونم."),
                Pair("is this... a boss monster?!", "این... هیولای رئیس (باس) هست؟!"),
                Pair("level up!", "افزایش سطح (لول آپ)!"),
                Pair("quest completed.", "ماموریت با موفقیت انجام شد."),
                Pair("penalty quest has begun.", "ماموریت مجازات آغاز گردید.")
            )

            for ((source, trans) in preloadedCache) {
                val hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(source.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                database.translationCacheDao().insertOrUpdate(
                    TranslationCacheEntity(
                        sourceHash = hash,
                        originalText = source,
                        translatedText = trans,
                        hitCount = 5
                    )
                )
            }
        }
    }
}
