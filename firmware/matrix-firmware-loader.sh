#!/system/bin/sh
echo -n "disable Matrix Creator microcontroller.."
echo 18 > /sys/class/gpio/export
echo out > /sys/class/gpio/gpio18/direction
echo 1 > /sys/class/gpio/gpio18/value
echo 0 > /sys/class/gpio/gpio18/value
echo 1 > /sys/class/gpio/gpio18/value
echo "done"

# enable LED and turn OFF
echo 21 > /sys/class/gpio/export
echo out > /sys/class/gpio/gpio21/direction
echo 0 > /sys/class/gpio/gpio21/value


echo "reconfigurate FPGA and Micro.."
/data/local/matrix-xc3sprog -v -p 1 -c sysfsgpio /data/local/matrix_system.bit
echo "done"

# blink LED 
echo 1 > /sys/class/gpio/gpio21/value
sleep 1
echo 0 > /sys/class/gpio/gpio21/value
sleep 1
echo 1 > /sys/class/gpio/gpio21/value
sleep 1
echo 0 > /sys/class/gpio/gpio21/value

