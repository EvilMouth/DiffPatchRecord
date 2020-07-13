apksPath="../../../app/assets/apk/"
patchPath="../../../app/assets/apkdiff/"
apks=("3.9" "3.9.1" "4.0" "4.1" "4.2" "4.3" "4.3.1")
#apks=("4.3.zlib" "4.3.1.zlib")
size=${#apks[*]}

for ((i = 1; i <= size; i++)); do
  for ((j = i + 1; j <= size; j++)); do
    apkOld=${apks[i]}
    apkNew=${apks[j]}
    patch="($apkOld-$apkNew).patch"
#    echo "$patch"
    ./ZipDiff "$apksPath$apkOld.apk" "$apksPath$apkNew.apk" "$patchPath$patch"
  done
done
