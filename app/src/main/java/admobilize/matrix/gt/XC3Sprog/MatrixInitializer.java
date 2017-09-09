package admobilize.matrix.gt.XC3Sprog;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.android.things.pio.SpiDevice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

import admobilize.matrix.gt.BoardDefaults;
import admobilize.matrix.gt.Config;
import admobilize.matrix.gt.R;


/**
 * Created by Alvaro Viebrantz @alvarowolfx on 09/07/17.
 */

public class MatrixInitializer {

    private static final String TAG = MatrixInitializer.class.getSimpleName();
    private static final boolean DEBUG = Config.DEBUG;

    private static final String XC3SPROG_ASSETS_PATH = "matrix-xc3sprog";
    private static final String MATRIX_FIRWARE_ASSETS_PATH = "matrix_system.bit";

    private final Context ctx;
    private PeripheralManagerService service;
    private SpiDevice spiDevice;

    /*
    private Gpio mXCProgTDI;
    private Gpio mXCProgTMS;
    private Gpio mXCProgTCK;
    private Gpio mXCProgTDO;
    */
    private Gpio mXCProgSAM;
    private Gpio mLedGpio;


    public MatrixInitializer(Context ctx, PeripheralManagerService service, SpiDevice spiDevice) {
        this.ctx = ctx;
        this.service = service;
        this.spiDevice = spiDevice;
    }

    public MatrixInitializer(Context ctx) throws IOException {
        this.ctx = ctx;
        this.service = new PeripheralManagerService();

        List<String> spiBusList = service.getSpiBusList();
        if (spiBusList.isEmpty()) {
            throw new IllegalStateException("No SPI device found");
        } else {
            this.spiDevice = service.openSpiDevice(spiBusList.get(0));
        }
    }

    private void copyAssets(String filename) {
        String appFileDirectory = this.ctx.getFilesDir().getPath();
        AssetManager assetManager = this.ctx.getAssets();

        InputStream in = null;
        OutputStream out = null;
        Log.d(TAG, "Attempting to copy this file: " + filename);

        try {

            File outFile = new File(appFileDirectory, filename);
            /*
            if (outFile.exists()) {
                Log.d(TAG, "File already exists in app directory: " + filename);
                return;
            }
            */

            out = new FileOutputStream(outFile);

            in = assetManager.open(filename);
            Log.d(TAG, "outDir: " + appFileDirectory);

            copyFile(in, out);

            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy asset file: " + filename, e);
        }

        Log.d(TAG, "Copy success: " + filename);
    }

    private void copyFile(InputStream in, OutputStream os) throws IOException {
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
        }
        in.close();
        os.close();
    }

    private void copyAssetsToDataFolder() throws IOException {
        this.copyAssets(XC3SPROG_ASSETS_PATH);
        this.copyAssets(MATRIX_FIRWARE_ASSETS_PATH);
    }

    public void writeLED(boolean state) {
        try {
            mLedGpio.setValue(state);
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
            e.printStackTrace();
        }
    }

    public void resetSAM() {
        try {
            Log.d(TAG, "disable Matrix Creator microcontroller...");

            mXCProgSAM.setValue(true);
            mXCProgSAM.setValue(false);
            mXCProgSAM.setValue(true);

            Log.d(TAG, "done");

        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
            e.printStackTrace();
        }
    }

    private void configurePins() throws IOException {

        mLedGpio = this.service.openGpio(BoardDefaults.getGPIOForLED());
        mXCProgSAM = this.service.openGpio(BoardDefaults.getGPIO_SAM());
        /*
        mXCProgTDI = this.service.openGpio(BoardDefaults.getGPIO_TDI());
        mXCProgTMS = this.service.openGpio(BoardDefaults.getGPIO_TMS());
        mXCProgTCK = this.service.openGpio(BoardDefaults.getGPIO_TCK());
        mXCProgTDO = this.service.openGpio(BoardDefaults.getGPIO_TDO());
        */

        mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
        mXCProgSAM.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
        /*
        mXCProgTDI.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
        mXCProgTMS.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
        mXCProgTDO.setDirection(Gpio.DIRECTION_IN);
        mXCProgTCK.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
        */

    }

    public void init() {
        try {
            copyAssetsToDataFolder();

            configurePins();
            this.writeLED(false);

            resetSAM();

            loadFirmware();

            this.writeLED(true);
            Thread.sleep(1000l);
            this.writeLED(false);
            Thread.sleep(1000l);
            this.writeLED(true);
            Thread.sleep(1000l);
            this.writeLED(false);

        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
            e.printStackTrace();
        } catch (InterruptedException e) {
            Log.e(TAG, "Error on Sleeping", e);
            e.printStackTrace();
        }
    }

    private void loadFirmware() {
        String appFileDirectory = this.ctx.getFilesDir().getPath();

        File xc3sprogFile = new File(appFileDirectory, XC3SPROG_ASSETS_PATH);
        File matrixFirmwareFile = new File(appFileDirectory, MATRIX_FIRWARE_ASSETS_PATH);
        xc3sprogFile.setExecutable(true);

        Log.d(TAG, "reconfigurate FPGA and Micro...");

        //"/data/local/matrix-xc3sprog -p 1 -c sysfsgpio /data/local/matrix_system.bit"
        // String cmd = String.format("%s -p 1 -c sysfsgpio %s", xc3sprogFile.getPath(), matrixFirmwareFile.getPath());
        ProcessBuilder pb = new ProcessBuilder(xc3sprogFile.getPath(), "-v", "-p", "1","-c","sysfsgpio", matrixFirmwareFile.getPath());
        List<String> cmd = pb.command();
        try {
            Log.d(TAG, "Exec command : " + cmd);
            //Process process = Runtime.getRuntime().exec(cmd);
            Process process = pb.start();

            InputStream errorStream = process.getErrorStream();
            Log.d(TAG, "Error stream: ");
            printStream(errorStream);

            process.waitFor();

            InputStream inputStream = process.getInputStream();
            Log.d(TAG, "Out stream: ");
            printStream(inputStream);

            Log.d(TAG, "done");

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void printStream(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line;

        StringBuffer output = new StringBuffer();
        while ((line = reader.readLine()) != null) {
            Log.d(TAG, line);
        }
    }

    /*
     * TODO: Add listener events when finished loading firmware
    public interface OnSystemLoadListener {
        void onSuccess(int msg);
        void onError(String err);
    }
    */

}
