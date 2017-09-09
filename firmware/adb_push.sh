adb push matrix_system.bit /data/local/ 
adb push matrix-xc3sprog /data/local/
adb push matrix-firmware-loader.sh /data/local/
adb push matrix-sensors-status /data/local/

adb shell chmod 571 /data/local/matrix-*
