# PALA BET AI — Ana Aşama 1 / V0.1

## Durum
Bu bir **gerçek kaynak kodu başlangıç paketidir**. Android uygulaması demo fikstürleriyle yerel Poisson olasılıkları hesaplar. Python backend ayrı bir araştırma API'si sağlar. Android henüz backend'e bağlanmaz; canlı veri, kadro, oran ve bahis işlevleri yoktur. Elo fonksiyonu backend'de vardır, Android ekranındaki 1X2 tahmini henüz Elo ile birleştirilmez.

## Android APK (GitHub Actions)
1. ZIP içeriğini GitHub'da yeni bir depoya yükleyin (dosyalar depo kökünde olmalı).
2. Actions → Android Debug APK → Run workflow.
3. İşlem başarılıysa Artifacts bölümündeki PALA-BET-AI-v0.1-debug dosyasını indirin.
4. APK test sürümüdür; Play Store için imzalı AAB ve ayrı uyumluluk çalışması gerekir.

## Backend
```bash
cd backend
python -m venv .venv
pip install -r requirements.txt
python -m pytest -q
uvicorn app.main:app --reload
```
Swagger: http://127.0.0.1:8000/docs

## Önemli sınırlamalar
- Ekrandaki maçlar **uydurma örnek maçlardır**, gerçek maç gibi kullanılmamalıdır.
- Android uygulaması ağ izni istemez, API anahtarı içermez.
- Android Poisson toplamı 0..12 golle sınırlıdır; Python modeli 0..18 kullanır. İleride ortak sayısal testlerle eşitlenecek.
- Henüz cihaz üzerinde APK derlemesi doğrulanmadı. CI yapılandırması sağlandı, derleme sonucu GitHub'da görülmelidir.
- Gerçek zamanlı veri, ticari kullanım lisansları ve Türkiye/Google Play uyumluluğu ayrıca değerlendirilecektir.
- Kumar/bahis işlemleri ve kazanç garantisi yoktur.
