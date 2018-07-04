package com.disarm.surakshit.collectgis.Util;

import android.os.Environment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;

/**
 * Created by bishakh on 7/2/18.
 */

public class ReportGenerator {
    public static void generateReport(String log, String fileName) {
        File reportFolder = Environment.getExternalStoragePublicDirectory(Constants.REPORT_DIRECTORY);
        if (!reportFolder.exists()) {
            reportFolder.mkdir();
        }
        File reportFile = Environment.getExternalStoragePublicDirectory(Constants.REPORT_DIRECTORY + fileName);
        log += "\n";
        try {
            FileOutputStream fos = new FileOutputStream(reportFile, true);
            fos.write(log.getBytes());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
