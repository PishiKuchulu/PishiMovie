<div align="center">

# 🎬 PishiMovie

### سینمای جیبی تو — Your pocket cinema

[![Build APK](https://github.com/PishiKuchulu/PishiMovie/actions/workflows/build-apk.yml/badge.svg)](https://github.com/PishiKuchulu/PishiMovie/actions/workflows/build-apk.yml)
![Version](https://img.shields.io/badge/version-1.2.2-blue)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.09-4285F4?logo=jetpackcompose&logoColor=white)
![Media3](https://img.shields.io/badge/Media3%20ExoPlayer-1.4.1-FF6F00)

**📥 [دانلود آخرین APK از بخش Releases](https://github.com/PishiKuchulu/PishiMovie/releases/tag/latest)**

</div>

---

<div dir="rtl">

## ✨ درباره

**پیشی‌مووی** یک اپلیکیشن اندرویدی مدرن برای کشف، جستجو و تماشای فیلم و سریال است؛
فارسی‌محور، راست‌به‌چپ، و ساخته‌شده با جدیدترین تکنولوژی‌های اندروید
(Kotlin + Jetpack Compose + Media3). از گوشی تا **Android TV**، سینما همیشه همراه توست. 🍿

## 🌟 ویژگی‌ها

| | |
|---|---|
| 🎭 | **کشف و مرور** — دسته‌بندی‌ها، ژانرها، کشورها، مودها و کالکشن‌های دست‌چین |
| 🔍 | **جستجوی پیشرفته** — با فیلتر ژانر، سال، امتیاز و کشور |
| ▶️ | **پخش قدرتمند** — ExoPlayer/Media3 با پشتیبانی از HLS و DASH |
| 💬 | **زیرنویس فارسی‌دوست** — SRT/ASS/VTT و زیرنویس داخلی، با فونت **وزیرمتن** و تنظیمات جداگانه برای هر ویدیو |
| 📥 | **دانلود** — برای تماشای آفلاین |
| 📺 | **پشتیبانی کامل Android TV** — ناوبری سایدبار و کنترل با ریموت |
| 🖼️ | **تصویر‌در‌تصویر (PiP)** — تماشا حین کار با گوشی |
| 🌓 | **تم‌های سینمایی** — استایل Netflix/Pishi + حالت AMOLED مشکی واقعی |
| 📊 | **آمار و ادامه تماشا** — تاریخچه، علاقه‌مندی‌ها، واچ‌لیست و آمار تماشای شخصی |
| 🌍 | **دوزبانه** — فارسی و انگلیسی، با پشتیبانی کامل راست‌به‌چپ |

## 🛠️ تکنولوژی‌ها

- **Kotlin 2.0** + **Jetpack Compose (Material3)** — رابط کاربری مدرن و declarative
- **Media3 ExoPlayer** — موتور پخش ویدیو
- **OkHttp** + **Kotlinx Serialization** — شبکه و مدل داده
- **Coil** — بارگذاری هوشمند تصاویر
- **DataStore** — تنظیمات سبک و سریع

## 🚀 ساخت و اجرا

```bash
git clone https://github.com/PishiKuchulu/PishiMovie.git
cd PishiMovie
./gradlew assembleDebug
# خروجی: app/build/outputs/apk/debug/app-debug.apk
```

پیش‌نیازها: **JDK 17** و Android SDK (compileSdk 35).
هر push به برنچ `main` به‌صورت خودکار APK می‌سازد و در [Releases](https://github.com/PishiKuchulu/PishiMovie/releases/tag/latest) منتشر می‌کند. 🤖

## 📸 اسکرین‌شات‌ها

> به‌زودی اضافه می‌شود… 🎞️

## 📜 قانون اساسی پروژه

این پروژه تحت چارچوب حاکمیتی **PFP** توسعه داده می‌شود — اصول، گیت‌های انتشار و
قواعد کدنویسی در [`PFP.md`](PFP.md) و پوشه [`pfp/`](pfp/) مستند شده است.

</div>

---

## 🇬🇧 English

**PishiMovie** is a modern Android app for discovering, searching and watching movies & TV shows —
Persian-first, RTL-ready, and built with Kotlin + Jetpack Compose + Media3. From phone to **Android TV**.

**Highlights:** curated discovery (genres, countries, moods, collections) · advanced search with filters ·
Media3 playback with HLS/DASH · Persian-friendly subtitles (SRT/ASS/VTT/embedded) with the **Vazirmatn** typeface
and per-video settings · downloads for offline watching · full Android TV support · Picture-in-Picture ·
cinematic themes (Netflix/Pishi + true AMOLED black) · watch statistics, continue-watching, favorites & watchlist.

**Build:** `./gradlew assembleDebug` (JDK 17 required) → `app/build/outputs/apk/debug/app-debug.apk`.
Every push to `main` auto-builds and publishes the APK to [Releases](https://github.com/PishiKuchulu/PishiMovie/releases/tag/latest). 🤖

<div align="center">

ساخته‌شده با ❤️ برای عاشقان سینما — *Made with love for movie lovers*

</div>
