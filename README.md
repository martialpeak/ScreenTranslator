# 📱 مترجم هوشمند روی صفحه و خوانشگر مانهوا (Screen Translator)

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" />
  <img src="https://img.shields.io/badge/Support-Donito-FF4081?style=for-the-badge&logo=heart&logoColor=white" />
</p>

یک اپلیکیشن متن‌باز و مدرن اندروید برای **ترجمه زنده و آنی صفحات بازی‌ها، کمیک‌ها و مانهواها** به زبان شیرین فارسی، با استفاده از OCR پیشرفته، جایگزینی بالن‌های دیالوگ و اتصال به موتورهای آفلاین و آنلاین هوش مصنوعی.

---

## 💖 حمایت مالی از پروژه (Donation)

توسعه و نگهداری این پروژه به صورت کاملاً متن‌باز و مستقل انجام می‌شود. در صورتی که این برنامه برای شما مفید بوده است، با حمایت مالی از طریق لینک زیر می‌توانید به توسعه قابلیت‌های جدید و سرورهای هوش مصنوعی کمک کنید:

<p align="center">
  <a href="https://donito.me/M_Alone">
    <img src="https://img.shields.io/badge/❤️_حمایت_مالی_در_دونیتو-donito.me/M__Alone-E11D48?style=for-the-badge" alt="Donation" />
  </a>
</p>

🔗 **لینک حمایت مستقیم:** [https://donito.me/M_Alone](https://donito.me/M_Alone)

---

## 🚀 ویژگی‌های کلیدی

### 1. ⚡ ترجمه آنی روی صفحه با حباب شناور (Screen Overlay)
- اسکن لحظه‌ای متن صفحه با فشردن حباب شناور
- تشخیص دیالوگ‌ها و جایگذاری بالن‌های تمیز فارسی دقیقاً روی محل متن اصلی
- حالت **پایش اسکرول هوشمند** با کمترین مصرف باتری و رم (بدون لگ و فریم‌دراپ)

### 2. 📖 کتابخوان و خوانشگر اختصاصی مانهوا (Manhwa Reader)
- **📄 فایل‌های PDF:** انتخاب مستقیم چپتر کمیک با فرمت PDF، اسکن خودکار دیالوگ‌ها، ترجمه و بالن‌گذاری فارسی
- **🖼️ تصاویر گالری:** انتخاب دسته‌ای تصاویر چپتر مانهوا (JPG, PNG, WEBP) با مرتب‌سازی عددی-طبیعی (Natural Sorting)
- **🌐 وب‌تون با لینک آنلاین:** مرورگر داخلی وب‌تون با قابلیت ترجمه لایو هر بخش با یک لمس و افزودن به چپتر نهایی

### 3. 📦 ابزارهای خروجی و تبدیل فرمت پیشرفته (WebP / ZIP / PDF)
- **خروجی WebP در ZIP:** فشرده‌سازی صفحات ترجمه‌شده به فرمت کم‌حجم WebP و بسته‌بندی در فایل ZIP
- **تبدیل مستقیم PDF به WebP+ZIP:** بدون نیاز به ابزارهای جانبی، هر صفحه PDF با وضوح بالا به WebP تبدیل و در یک ZIP ذخیره می‌شود
- **ورود مستقیم فایل ZIP حاوی تصاویر:** استخراج و ترجمه خودکار تمام تصاویر WebP/JPG/PNG از درون آرشیو ZIP

### 4. 🤖 پشتیبانی از تنوع گسترده موتورهای هوش مصنوعی
- **آفلاین:** مدل سبک و بدون نیاز به اینترنت Google ML Kit On-Device
- **آنلاین رایگان و تجاری:**
  - Google Gemini Flash ⚡ (با ورودی مستقیم تصویر و درک دیالوگ‌ها)
  - DeepSeek V3 / R1 🚀
  - OpenRouter 🌐 (با پشتیبانی از ده‌ها مدل رایگان و پرقدرت)
  - Grok xAI 🛸
  - Groq LPU ⚡
  - ChatGPT (OpenAI) 🤖
  - سرورهای سفارشی سازگار با استاندارد OpenAI API

### 5. 📚 پایگاه داده محلی و اصطلاح‌نامه تخصصی مانهوا
- لغت‌نامه و اصطلاح‌نامه با بیش از ۶۰۰۰ اصطلاح سبک مانهوا/مانگا (Murim, Cultivation, System, Dungeon)
- سیستم کش هوشمند دو سطحی (حافظه رم LRU + دیتابیس Room FTS5) جهت جلوگیری از ترجمه مجدد و مصرف اعتبار API
- تبدیل خودکار لحن ترجمه به سبک محاوره‌ای و روان کمیک‌های فارسی

---

## 🛠️ پیش‌نیازها و راهنمای ساخت (Build)

- **Android SDK:** حداقل نسخه ۲۶ (Android 8.0) تا نسخه ۳۴ (Android 14)
- **Gradle:** نسخه ۸.۷
- **Java:** نسخه ۱۷

```bash
# کلون مخزن
git clone https://github.com/martialpeak/ScreenTranslator.git
cd ScreenTranslator

# ساخت نسخه دیباگ
gradle assembleDebug
```

---

## 📄 اطلاعات مخزن

- **مخزن رسمی:** [https://github.com/martialpeak/ScreenTranslator](https://github.com/martialpeak/ScreenTranslator)
- **لینک حمایت:** [https://donito.me/M_Alone](https://donito.me/M_Alone)
- **توسعه‌دهنده:** [martialpeak](https://github.com/martialpeak)
