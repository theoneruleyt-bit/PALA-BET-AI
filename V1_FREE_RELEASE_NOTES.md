# PALA BET AI V1.0 Free — genişletilmiş kaynak paketi

## Gerçekten eklenenler
- OpenLigaDB 2026 kataloğunda görünen 12 açık lig/kupa kısayolu.
- Android lig seçiminde bu kaynaklar.
- Mevcut Poisson 1X2/KG/2.5 analizleri.
- Yeni `/research/{competition}/{match_id}` yanıtında `markets`: 0.5,1.5,2.5,3.5,4.5 gol çizgileri, çifte şans, takım 0.5/1.5 golleri, 5 olası skor.
- Mevcut backend testlerine yeni pazar tutarlılığı testi.

## Açık sınırlamalar
- İddaa bültenindeki TÜM ligler desteklenmiyor; Türkiye ligleri için güvenilir güncel ücretsiz kaynak entegrasyonu yok.
- OpenLigaDB topluluk verileri eksik/yanlış olabilir; lig bulunması o sezonda fikstür ve yeterli tarihçe olduğu anlamına gelmez.
- Kadro, sakatlık, gerçek xG, korner, kart, ilk yarı, canlı bahis, bahis oranları ve otomatik değerli bahis analizi YOK.
- Yeni `/markets` verileri backend'de mevcut; Android arayüzünde henüz ayrı pazar paneli YOK.
- APK derlemesi bu ortamda çalıştırılmadı; GitHub Actions ile doğrulanmalı.
- Ücretsiz futbol-data.org ek ligleri için kullanıcı sunucu API anahtarı gerekir.
- Araştırma amaçlı olasılıklar; yatırım/bahis başarısı vaadi değildir.

## Yükleme
Klasör içeriğini GitHub deposunun KÖK dizinine kopyalayın, `git add -A`, `git commit`, `git push`. Render otomatik deploy tamamlanmalı.
