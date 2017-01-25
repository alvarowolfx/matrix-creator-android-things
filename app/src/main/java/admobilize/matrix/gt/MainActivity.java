/*
 * Copyright 2016 <Admobilize>
 * MATRIX Labs  [http://creator.matrix.one]
 * This file is part of MATRIX Creator Google Things (GT)
 *
 * MATRIX Creator GT is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package admobilize.matrix.gt;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.android.things.pio.SpiDevice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import admobilize.matrix.gt.XC3Sprog.JNIPrimitives;
import admobilize.matrix.gt.matrix.Everloop;
import admobilize.matrix.gt.matrix.Humidity;
import admobilize.matrix.gt.matrix.IMU;
import admobilize.matrix.gt.matrix.MicArray;
import admobilize.matrix.gt.matrix.Pressure;
import admobilize.matrix.gt.matrix.UV;
import admobilize.matrix.gt.matrix.Wishbone;

import static admobilize.matrix.gt.matrix.Everloop.*;

/**
 * Sample usage of the Matrix-Creator sensors and GPIO calls
 *
 * REQUIREMENTS:
 *
 * - MatrixCreator Google Things image on RaspberryPi3
 * - MatrixCreator hat
 *
 * Created by Antonio Vanegas @hpsaturn on 12/19/16.
 */


public class MainActivity extends Activity implements JNIPrimitives.OnSystemLoadListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final boolean DEBUG = Config.DEBUG;

    private boolean SHOW_EVERLOOP_PROGRESS = false;
    private boolean SHOW_SENSORS_OUTPUT = false;
    private static final int INTERVAL_POLLING_MS = 50;

    private Handler mHandler = new Handler();
    private SpiDevice spiDevice;
    private Wishbone wb;
    private Everloop everloop;
    private Pressure pressure;
    private Humidity humidity;
    private IMU imuSensor;
    private UV uvSensor;
    private boolean toggleColor;
    private JNIPrimitives jni;
    private MicArray micArray;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Starting Matrix-Creator device config..");

        PeripheralManagerService service = new PeripheralManagerService();
        configSPI(service);
        initDevices(spiDevice);
        configMicDataInterrupt(service);
/**
 *      ** ATTENTION**
 *      TODO: Automatic FPGA initialization not work because Google Things has bad performance, issue:
 *      https://code.google.com/p/android/issues/detail?id=231484
 *      testing branch: https://github.com/matrix-io/matrix-creator-android-things/tree/av/xc3sprog
 *
 *      startFPGAflashing();
 */
        // Runnable that continuously update sensors and LED (Matrix LED on GPIO21)
//        mHandler.post(mPollingRunnable);
    }

    private void startFPGAflashing(PeripheralManagerService service){
        jni=new JNIPrimitives(this,service,spiDevice);
        jni.init();
        jni.burnFirmware();
    }

    private void initDevices(SpiDevice spiDevice) {
        wb=new Wishbone(spiDevice);
        uvSensor = new UV(wb);
        pressure = new Pressure(wb);
        humidity = new Humidity(wb);
        imuSensor = new IMU(wb);
        micArray = new MicArray(wb);
        everloop = new Everloop(wb);
        everloop.clear();
        everloop.write(everloop.ledImage);
    }

    private void configSPI(PeripheralManagerService service){
        try {
            List<String> deviceList = service.getSpiBusList();
            if (deviceList.isEmpty()) {
                Log.i(TAG, "No SPI bus available");
            } else {
                Log.i(TAG, "List of available devices: " + deviceList);
            }
            spiDevice = service.openSpiDevice(BoardDefaults.getSpiBus());
            spiDevice.setMode(SpiDevice.MODE3);
            spiDevice.setFrequency(18000000);     // 18MHz
            spiDevice.setBitsPerWord(8);          // 8 BPW
            spiDevice.setBitJustification(false); // MSB first

        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API (SPI)", e);
            e.printStackTrace();
        }
    }

    public void configMicDataInterrupt(PeripheralManagerService service){
        try {
            Gpio gpio = service.openGpio(BoardDefaults.getGPIO_MIC_DATA());
            gpio.setDirection(Gpio.DIRECTION_IN);
            gpio.setActiveType(Gpio.ACTIVE_LOW);
            // Register for all state changes
            gpio.setEdgeTriggerType(Gpio.EDGE_BOTH);
            gpio.registerGpioCallback(onMicDataCallback);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    int max_irq_samples=2000;
    int irq_samples=0;
    boolean sendData=false;
    private GpioCallback onMicDataCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            if(irq_samples<max_irq_samples){
                irq_samples++;
                micArray.read();
            }
            else if (!sendData){
                Log.i(TAG,"onMicDataCallback "+max_irq_samples+" samples reached!");
                micArray.sendDataToDebugIp();
                sendData=true;
            }
            return super.onGpioEdge(gpio);
        }
        @Override
        public void onGpioError(Gpio gpio, int error) {
            super.onGpioError(gpio, error);
            Log.w(TAG, "onMicDataCallback error event: "+gpio + "==>" + error);
        }
    };

    void setColor(ArrayList<LedValue>leds, int pos, int r, int g, int b, int w) {
        leds.get(pos % 35).red   = (byte) r;
        leds.get(pos % 35).green = (byte) g;
        leds.get(pos % 35).blue  = (byte) b;
        leds.get(pos % 35).white = (byte) w;
    }

    void drawProgress(ArrayList<LedValue>leds, int counter) {
        if(counter % 35 ==0) toggleColor=!toggleColor;
        int min = counter % 35;
        int solid = 35;
        for (int i = 0; i <= min; i++) {
            if(toggleColor) setColor(leds, i, i/3, solid/5, 0, 0);
            else setColor(leds, i, solid/5, i/3, 0, 0);
            solid=35-i;
        }
    }

    private Runnable mPollingRunnable = new Runnable() {

        private long counter=0;

        @Override
        public void run() {

            // Exit Runnable if devices is already closed
            if (wb == null) return;
            // mLedGpio.setValue(!mLedGpio.getValue());

            if(SHOW_SENSORS_OUTPUT) {
                String output;
                // Read UVsensor
                output = "UV: " + uvSensor.read() + "\t";
                // Read Pressure device values
                pressure.read();
                output = output + "AL: " + pressure.getAltitude() + "\t";
                output = output + "PR: " + pressure.getPressure() + "\t";
                output = output + "TP: " + pressure.getTemperature() + "\t";
                // Read Humidity device values
                humidity.read();
                output = output + "HM: " + humidity.getHumidity() + "\t";
                output = output + "TP: " + humidity.getTemperature() + "\t";
                // Read IMU device values
                imuSensor.read();
                output = output + "YW: " + imuSensor.getYaw() + "\t";
                output = output + "PT: " + imuSensor.getPitch() + "\t";
                output = output + "RL: " + imuSensor.getRoll() + "\t";
                if (DEBUG) Log.d(TAG, output);
            }

            if(SHOW_EVERLOOP_PROGRESS) {
                drawProgress(everloop.ledImage, (int) counter);
                everloop.write(everloop.ledImage);
                counter++;
            }
            // Reschedule the same runnable in {#INTERVAL_POLLING_MS} milliseconds
            mHandler.postDelayed(mPollingRunnable, INTERVAL_POLLING_MS);

        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove pending polling Runnable from the handler.
        mHandler.removeCallbacks(mPollingRunnable);
        if(DEBUG)Log.i(TAG, "Closing devices and GPIO");
        try {
            SHOW_EVERLOOP_PROGRESS=false;
            everloop.clear();
            everloop.write(everloop.ledImage);
            spiDevice.close();
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        } finally {
            spiDevice = null;
        }
    }

    @Override
    public void onSuccess(int msg) {
        if(DEBUG)Log.i(TAG, "Load firmware!->"+msg);

    }

    @Override
    public void onError(String err) {
        if(DEBUG)Log.i(TAG, err);

    }

}
