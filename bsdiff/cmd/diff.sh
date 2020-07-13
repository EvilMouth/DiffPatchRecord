#需要先安装bsdiff
#brew install bsdiff

apksPath="../../app/assets/apk/"
patchPath="../../app/assets/bsdiff/"
apks=("3.9" "3.9.1" "4.0" "4.1" "4.2" "4.3" "4.3.1")
#apks=("4.3 "4.3.1")
size=${#apks[*]}

start_time=$(date +%s)
for ((i = 1; i <= size; i++)); do
  for ((j = i + 1; j <= size; j++)); do
#  for (( i = 1, j = size; i < size; i++ )); do
    apkOld=${apks[i]}
    apkNew=${apks[j]}
    patch="($apkOld-$apkNew).patch"
#        echo "$patch"
    bsdiff "$apksPath$apkOld.apk" "$apksPath$apkNew.apk" "$patchPath$patch"
  done
done
stop_time=$(date +%s)
echo "TIME:$(expr "$stop_time" - "$start_time")"
