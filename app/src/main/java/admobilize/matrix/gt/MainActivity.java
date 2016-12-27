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

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.android.things.pio.SpiDevice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import admobilize.matrix.gt.XC3Sprog.JNIPrimitives;
import admobilize.matrix.gt.matrix.Everloop;
import admobilize.matrix.gt.matrix.Humidity;
import admobilize.matrix.gt.matrix.IMU;
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

    private boolean SHOW_EVERLOOP_PROGRESS = true;
    private static final int INTERVAL_POLLING_MS = 10;

    private Handler mHandler = new Handler();
    private Gpio mLedGpio;
    private SpiDevice spiDevice;
    private Wishbone wb;
    private Everloop everloop;
    private Pressure pressure;
    private Humidity humidity;
    private IMU imuSensor;
    private UV uvSensor;
    private boolean toggleColor;
    private JNIPrimitives jni;

    private Gpio mXCProgTDI;
    private Gpio mXCProgTMS;
    private Gpio mXCProgTCK;
    private Gpio mXCProgTDO;
    private Gpio mXCProgSAM;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Starting Matrix-Creator device config..");
        PeripheralManagerService service = new PeripheralManagerService();
        configSPI(service);
        configGPIO(service);
        initDevices(spiDevice);

        resetSAM();
        jni=new JNIPrimitives(this);
        jni.init(this);
        jni.burnFirmware();
//        while(jni.burnFirmware()!=1);
        // Runnable that continuously update sensors and LED (Matrix LED on GPIO21)
//        mHandler.post(mPollingRunnable);
    }

    private void initDevices(SpiDevice spiDevice) {
        wb=new Wishbone(spiDevice);
        uvSensor = new UV(wb);
        pressure = new Pressure(wb);
        humidity = new Humidity(wb);
        imuSensor = new IMU(wb);
        everloop = new Everloop(wb);
        everloop.clear();
        everloop.write(everloop.ledImage);
    }

    private void configGPIO(PeripheralManagerService service){
        try {
            Log.i(TAG, "Available GPIO: " + service.getGpioList());
            String pinName = BoardDefaults.getGPIOForLED();
            mLedGpio = service.openGpio(pinName);
            mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mXCProgTDI = service.openGpio(BoardDefaults.getGPIO_TDI());
            mXCProgTMS = service.openGpio(BoardDefaults.getGPIO_TMS());
            mXCProgTCK = service.openGpio(BoardDefaults.getGPIO_TCK());
            mXCProgTDO = service.openGpio(BoardDefaults.getGPIO_TDO());
            mXCProgSAM = service.openGpio(BoardDefaults.getGPIO_SAM());
            mXCProgTDI.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mXCProgTMS.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mXCProgTCK.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mXCProgSAM.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mXCProgTDO.setDirection(Gpio.DIRECTION_IN);
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }
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
            if (mLedGpio == null || wb == null) return;
            try {
                mLedGpio.setValue(!mLedGpio.getValue());
                String output;
                // Read UVsensor
                output="UV: "+ uvSensor.read()+"\t";
                // Read Pressure device values
                pressure.read();
                output=output+"AL: "+ pressure.getAltitude()+"\t";
                output=output+"PR: "+ pressure.getPressure()+"\t";
                output=output+"TP: "+ pressure.getTemperature()+"\t";
                // Read Humidity device values
                humidity.read();
                output=output+"HM: "+ humidity.getHumidity()+"\t";
                output=output+"TP: "+ humidity.getTemperature()+"\t";
                // Read IMU device values
                imuSensor.read();
                output=output+"YW: "+ imuSensor.getYaw()+"\t";
                output=output+"PT: "+ imuSensor.getPitch()+"\t";
                output=output+"RL: "+ imuSensor.getRoll()+"\t";
                if(DEBUG)Log.d(TAG,output);

                if(SHOW_EVERLOOP_PROGRESS) {
                    drawProgress(everloop.ledImage, (int) counter);
                    everloop.write(everloop.ledImage);
                    counter++;
                }
                // Reschedule the same runnable in {#INTERVAL_POLLING_MS} milliseconds
                mHandler.postDelayed(mPollingRunnable, INTERVAL_POLLING_MS);

            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }
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
            mLedGpio.close();
            spiDevice.close();
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        } finally {
            mLedGpio = null;
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

    public void writeTDI(boolean state) {
        try {
            mXCProgTDI.setValue(state);
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
            e.printStackTrace();
        }
    }

    public void writeTMS(boolean state) {
        try {
            mXCProgTMS.setValue(state);
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
            e.printStackTrace();
        }
    }

    public void writeTCK(boolean state) {
        try {
            mXCProgTCK.setValue(state);
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
            e.printStackTrace();
        }
    }

    public boolean readTDO() {
        try {
            return mXCProgTDO.getValue();
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
            e.printStackTrace();
        }
        return false;
    }

    public void writeLED(boolean state) {
        try {
            mLedGpio.setValue(state);
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
            e.printStackTrace();
        }
    }

    public boolean readLED() {
        try {
            return mLedGpio.getValue();
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
            e.printStackTrace();
        }
        return false;
    }

    public void resetSAM(){
        try {
            mXCProgSAM.setValue(true);
            mXCProgSAM.setValue(false);
            mXCProgSAM.setValue(true);

        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
            e.printStackTrace();
        }
    }
}
