[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$wc = New-Object System.Net.WebClient
$wc.Encoding = [System.Text.Encoding]::UTF8

$outputMap = [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::OrdinalIgnoreCase)
$letters = 'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','Y','Z'

# 1. Essential English Words
foreach ($l in $letters) {
    try {
        $url = "https://raw.githubusercontent.com/VahidN/EnglishToPersianDictionaries/master/Dictionaries/essential-english-words/$l.json"
        $data = $wc.DownloadString($url) | ConvertFrom-Json
        foreach ($w in $data.Words) {
            $word = $w.EnglishWord.Trim()
            $meanings = ($w.Meanings -join '، ').Trim()
            if ($word.Length -gt 0 -and $meanings.Length -gt 0 -and -not $outputMap.ContainsKey($word)) {
                $outputMap[$word] = "$meanings`tعمومی"
            }
        }
    } catch {}
}

# 2. Idioms 1
foreach ($l in $letters) {
    try {
        $url = "https://raw.githubusercontent.com/VahidN/EnglishToPersianDictionaries/master/Dictionaries/idioms-1/$l.json"
        $data = $wc.DownloadString($url) | ConvertFrom-Json
        foreach ($w in $data.Words) {
            $word = $w.EnglishWord.Trim()
            $meanings = ($w.Meanings -join '، ').Trim()
            if ($word.Length -gt 0 -and $meanings.Length -gt 0 -and -not $outputMap.ContainsKey($word)) {
                $outputMap[$word] = "$meanings`tاصطلاح"
            }
        }
    } catch {}
}

# 3. Idioms 2
foreach ($l in $letters) {
    try {
        $url = "https://raw.githubusercontent.com/VahidN/EnglishToPersianDictionaries/master/Dictionaries/idioms-2/$l.json"
        $data = $wc.DownloadString($url) | ConvertFrom-Json
        foreach ($w in $data.Words) {
            $word = $w.EnglishWord.Trim()
            $meanings = ($w.Meanings -join '، ').Trim()
            if ($word.Length -gt 0 -and $meanings.Length -gt 0 -and -not $outputMap.ContainsKey($word)) {
                $outputMap[$word] = "$meanings`tاصطلاح"
            }
        }
    } catch {}
}

# 4. Chat Slang
foreach ($l in $letters) {
    try {
        $url = "https://raw.githubusercontent.com/VahidN/EnglishToPersianDictionaries/master/Dictionaries/chat-slang/$l.json"
        $data = $wc.DownloadString($url) | ConvertFrom-Json
        foreach ($w in $data.Words) {
            $word = $w.EnglishWord.Trim()
            $meanings = ($w.Meanings -join '، ').Trim()
            if ($word.Length -gt 0 -and $meanings.Length -gt 0 -and -not $outputMap.ContainsKey($word)) {
                $outputMap[$word] = "$meanings`tاسلنگ و گیمینگ"
            }
        }
    } catch {}
}

# 5. Manhwa Universe & Solo Leveling & Sound Effects
$manhwaData = @(
    # Solo Leveling
    @("E-RANK HUNTER", "شکارچی رتبه E", "مانهوا"),
    @("E-RANK", "رتبه E", "مانهوا"),
    @("S-RANK HUNTER", "شکارچی رتبه S", "مانهوا"),
    @("S-RANK", "رتبه S", "مانهوا"),
    @("A-RANK HUNTER", "شکارچی رتبه A", "مانهوا"),
    @("B-RANK HUNTER", "شکارچی رتبه B", "مانهوا"),
    @("C-RANK HUNTER", "شکارچی رتبه C", "مانهوا"),
    @("D-RANK HUNTER", "شکارچی رتبه D", "مانهوا"),
    @("THE HUNTER GUILD'S", "انجمن شکارچیان", "مانهوا"),
    @("THE HUNTER GUILD", "انجمن شکارچیان", "مانهوا"),
    @("HUNTER GUILD", "انجمن شکارچیان", "مانهوا"),
    @("HUNTERS ASSOCIATION", "انجمن شکارچیان", "مانهوا"),
    @("SHADOW MONARCH", "پادشاه سایه‌ها", "مانهوا"),
    @("SHADOW SOLDIER", "سرباز سایه", "مانهوا"),
    @("SHADOW ARMY", "سپاه سایه‌ها", "مانهوا"),
    @("ARISE", "برخیز (ARISE)", "مانهوا"),
    @("DUNGEON BREAK", "شکست دانجن (خروج هیولاها)", "مانهوا"),
    @("RED GATE", "دروازه سرخ (رد گیت)", "مانهوا"),
    @("DOUBLE DUNGEON", "دانجن دوگانه", "مانهوا"),
    @("NATIONAL LEVEL HUNTER", "شکارچی سطح ملی", "مانهوا"),
    @("MAGIC BEAST", "هیولای جادویی", "مانهوا"),
    @("BOSS MONSTER", "هیولای رئیس (باس)", "مانهوا"),
    @("STATUS WINDOW", "پنجره وضعیت", "مانهوا"),
    @("DAILY QUEST", "ماموریت روزانه", "مانهوا"),
    @("PENALTY QUEST", "ماموریت مجازات", "مانهوا"),
    @("PENALTY ZONE", "منطقه مجازات", "مانهوا"),
    @("SUNG JIN-WOO", "سونگ جین‌وو", "مانهوا"),
    @("CHA HAE-IN", "چا هه‌این", "مانهوا"),
    @("GO GUN-HEE", "گو گان‌هی (رئیس انجمن)", "مانهوا"),
    @("ALWAYS GETTING HURT AND EVEN HAD NEAR DEATH EXPERIENCES", "برعکس، من همیشه صدمه می‌دیدم و حتی تجربه‌های دمِ مرگ هم داشتم.", "مانهوا"),
    @("ALWAYS GETTING HURT", "همیشه صدمه می‌دیدم", "مانهوا"),
    @("AND EVEN HAD NEAR DEATH EXPERIENCES", "و حتی تجربه‌های دمِ مرگ هم داشتم", "مانهوا"),
    @("THE JOB WHERE YOUR LIFE'S ON THE LINE, THE HUNTER", "شغلی که جونت کفِ دستته، هانتر بودن...", "مانهوا"),
    @("THE JOB WHERE YOUR LIFE'S ON THE LINE", "شغلی که جونت کفِ دستته", "مانهوا"),
    @("YOUR LIFE'S ON THE LINE", "جونت کفِ دستته", "مانهوا"),
    @("LIFE'S ON THE LINE", "جونت کفِ دستته", "مانهوا"),
    @("BECAUSE I LIKE IT", "چون دوستش دارم؟! هه...", "مانهوا"),
    @("BECAUSE I LIKE IT!", "چون دوستش دارم؟! هه...", "مانهوا"),
    @("NEAR DEATH", "دم مرگ", "مانهوا"),
    @("ON THE CONTRARY", "برعکس", "مانهوا"),
    @("ON THE OTHER HAND", "از طرف دیگه", "مانهوا"),
    @("AS A HUNTER", "به عنوان یه هانتر", "مانهوا"),
    
    # ORV
    @("CONSTELLATION", "صورت فلکی", "مانهوا"),
    @("STAR STREAM", "استار استریم", "مانهوا"),
    @("DOKKAEBI", "دوکه‌بی (جن کره‌ای)", "مانهوا"),
    @("INCARNATION", "تجسم زمینی", "مانهوا"),
    @("SCENARIO", "سناریو / ماجرا", "مانهوا"),
    @("MAIN SCENARIO", "سناریوی اصلی", "مانهوا"),
    @("SUB SCENARIO", "سناریوی فرعی", "مانهوا"),
    @("KIM DOKJA", "کیم دوکجا", "مانهوا"),
    @("YOO JOONGHYUK", "یو جونگهیوک", "مانهوا"),
    @("DEMON KING OF SALVATION", "پادشاه دیو رستگاری", "مانهوا"),
    
    # Tower of God
    @("REGULAR", "رگولار (صعودکننده برج)", "مانهوا"),
    @("IRREGULAR", "ایرگولار (غیرمعمول برگزیده)", "مانهوا"),
    @("RANKER", "رنکر برتر", "مانهوا"),
    @("HIGH RANKER", "های‌رنکر", "مانهوا"),
    @("SHINSU", "شینسو (آب الهی برج)", "مانهوا"),
    @("ZAHARD", "زاهارد (پادشاه برج)", "مانهوا"),
    @("TWENTY-FIFTH BAAM", "بیست و پنجمین بام", "مانهوا"),
    
    # Murim
    @("MOUNT HUA SECT", "فرقه کوه هوا (هواشان)", "مانهوا"),
    @("HEAVENLY DEMON", "شیطان آسمانی (چون‌ما)", "مانهوا"),
    @("DEMONIC CULT", "فرقه شیطانی", "مانهوا"),
    @("DANTIAN", "دانتیان (مرکز انرژی چی)", "مانهوا"),
    @("QI", "انرژی چی (نیروی درونی)", "مانهوا"),
    @("INTERNAL ARTS", "هنرهای درونی رزمی", "مانهوا"),
    @("MERIDIAN", "نقاط عبور انرژی (مریدیان)", "مانهوا"),
    @("QI DEVIATION", "انحراف چی (جنون رزمی)", "مانهوا"),

    # Korean SFX & Terms
    @("하아", "هااه... (نفس عمیق)", "کره‌ای"),
    @("하...", "ها...", "کره‌ای"),
    @("콰앙", "کوااانگ! (برخورد شدید)", "کره‌ای"),
    @("쾅", "بوم!", "کره‌ای"),
    @("쿵", "گرومپ! (کوبش سنگین)", "کره‌ای"),
    @("쉭", "ووش! (حرکت سریع)", "کره‌ای"),
    @("슥", "فشست! (تیزی)", "کره‌ای"),
    @("탁", "تپ! (گام محکم)", "کره‌ای"),
    @("크윽", "کوووه... (درد)", "کره‌ای"),
    @("큭", "هه... (پوزخند)", "کره‌ای"),
    @("으윽", "اوووف... (آه)", "کره‌ای"),
    @("헌터", "شکارچی (هانتر)", "کره‌ای"),
    @("성진우", "سونگ جین‌وو", "کره‌ای"),
    @("그림자 군주", "پادشاه سایه‌ها", "کره‌ای"),
    @("일어나라", "برخیز (ARISE)", "کره‌ای"),
    @("상태창", "پنجره وضعیت", "کره‌ای"),
    @("게이트", "گیت / دروازه", "کره‌ای"),
    @("던전", "دانجن (سیاه‌چال)", "کره‌ای"),
    @("레벨업", "افزایش سطح (لول‌آپ)!", "کره‌ای"),

    # Chinese SFX & Terms
    @("轰", "هوووم! (غرش و انفجار)", "چینی"),
    @("砰", "پنگ! (ضربه محکم)", "چینی"),
    @("呼", "هووف... (تنفس/باد)", "چینی"),
    @("斩", "شلااااش! (برش شمشیر)", "چینی"),
    @("啊", "آاااه!", "چینی"),
    @("哈", "هاه...", "چینی"),
    @("切", "چِه! (پوزخند)", "چینی"),
    @("猎人", "شکارچی", "چینی"),
    @("宗主", "رهبر فرقه", "چینی"),
    @("长老", "بزرگ‌تر / ریش‌سفید", "چینی"),
    @("丹田", "دانتیان", "چینی"),
    @("灵气", "انرژی درونی چی", "چینی")
)

foreach ($item in $manhwaData) {
    $outputMap[$item[0]] = "$($item[1])`t$($item[2])"
}

$tsvPath = "c:\Users\01\Desktop\dubleapk\app\src\main\assets\offline_dictionary.tsv"
$lines = [System.Collections.Generic.List[string]]::new()
foreach ($k in $outputMap.Keys) {
    $lines.Add("$k`t$($outputMap[$k])")
}

[System.IO.File]::WriteAllLines($tsvPath, $lines, [System.Text.Encoding]::UTF8)
Write-Output "Successfully generated $tsvPath with $($lines.Count) words!"
