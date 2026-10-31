# GaunCraft_Resourcepack-4

Resource pack'i (`src/main/resources/GaunCraft_Resourcepack-4.zip`) oyunculara otomatik gonderen Paper/Purpur 1.21.1 eklentisi.

## Kullanim
1. Bu repoyu GitHub'a yukle (public olmali, yoksa oyuncular indiremez).
2. **Actions** sekmesi -> `Build plugin` calisir -> bitince **Artifacts -> GaunCraft_Resourcepack-4** icinden jar'i indir.
   (Ya da **Releases -> Create release** ile yayinla, jar release'e otomatik eklenir.)
3. Jar'i sunucunda `plugins/` klasorune at, yeniden baslat.

Derleme sirasinda `public-url`, bu reponun `raw.githubusercontent.com` adresine otomatik ayarlanir,
yani sunucuda port acmana veya IP yazmana gerek yok.

## Paketi guncelleme
`src/main/resources/GaunCraft_Resourcepack-4.zip` dosyasini yenisiyle degistirip commit et.
Actions yeni jar'i uretir; SHA-1 otomatik hesaplanir.

## Komut
`/gcpack4 info|send [oyuncu]|reload` — yetki: `gauncraftpack4.admin`

## Yerelde derleme
`mvn package` -> `target/GaunCraft_Resourcepack-4.jar` (public-url bos kalir, yerlesik web sunucu 8767 portunda calisir)
