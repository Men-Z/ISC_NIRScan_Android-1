package com.isctechnologies.NanoScan;

/**
 * Created by iris.lin on 2018/2/6.
 */

import android.content.Context;
import android.content.res.Resources;
import java.util.ArrayList;

public class ScanListDictionary {
    private Context context;

    public ScanListDictionary(Context context) {
        this.context = context;
    }

    public ArrayList<NIRScanSDK.ScanListManager> getScanList(String fileName) {
        Resources res = this.context.getResources();
        ArrayList graphList;
      /*  if(fileName.equals(res.getString(string.aspirin))) {
            graphList = new ArrayList();
            graphList.add(new NIRScanSDK.ScanListManager("Method", "standard_scan"));
            graphList.add(new NIRScanSDK.ScanListManager("Timestamp", "2/3/2015 @ 14:43:06"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range Start (nm)", "850.804993"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range End (nm)", "1779.879761"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Wavelength Points", "85"));
            graphList.add(new NIRScanSDK.ScanListManager("Digital Resolution", "85"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Scans to Average", "1"));
            graphList.add(new NIRScanSDK.ScanListManager("Total Measurement Time (s)", "2.00"));
            return graphList;
        } else if(fileName.equals(res.getString(string.bc))) {
            graphList = new ArrayList();
            graphList.add(new NIRScanSDK.ScanListManager("Method", "standard_scan"));
            graphList.add(new NIRScanSDK. ScanListManager("Timestamp", "2/3/2015 @ 14:47:41"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range Start (nm)", "850.804993"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range End (nm)", "1779.879761"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Wavelength Points", "85"));
            graphList.add(new NIRScanSDK.ScanListManager("Digital Resolution", "85"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Scans to Average", "1"));
            graphList.add(new NIRScanSDK.ScanListManager("Total Measurement Time (s)", "1.00"));
            return graphList;
        } else if(fileName.equals(res.getString(string.bellpepper))) {
            graphList = new ArrayList();
            graphList.add(new NIRScanSDK.ScanListManager("Method", "col8"));
            graphList.add(new NIRScanSDK.ScanListManager("Timestamp", "3/3/2015 @ 15:6:32"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range Start (nm)", "852.15979"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range End (nm)", "1780.73645"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Wavelength Points", "106"));
            graphList.add(new NIRScanSDK.ScanListManager("Digital Resolution", "106"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Scans to Average", "1"));
            graphList.add(new NIRScanSDK.ScanListManager("Total Measurement Time (s)", "2.00"));
            return graphList;
        } else if(fileName.equals(res.getString(string.coconutoil))) {
            graphList = new ArrayList();
            graphList.add(new NIRScanSDK.ScanListManager("Method", "standard_scan"));
            graphList.add(new NIRScanSDK.ScanListManager("Timestamp", "5/3/2015 @ 11:38:43"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range Start (nm)", "853.104553"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range End (nm)", "1794.033813"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Wavelength Points", "106"));
            graphList.add(new NIRScanSDK.ScanListManager("Digital Resolution", "106"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Scans to Average", "1"));
            graphList.add(new NIRScanSDK.ScanListManager("Total Measurement Time (s)", "1.00"));
            return graphList;
        } else if(fileName.equals(res.getString(string.coffee))) {
            graphList = new ArrayList();
            graphList.add(new NIRScanSDK.ScanListManager("Method", "standard_scan"));
            graphList.add(new NIRScanSDK.ScanListManager("Timestamp", "2/3/2015 @ 14:43:06"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range Start (nm)", "850.804993"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range End (nm)", "1779.879761"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Wavelength Points", "85"));
            graphList.add(new NIRScanSDK.ScanListManager("Digital Resolution", "85"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Scans to Average", "1"));
            graphList.add(new NIRScanSDK.ScanListManager("Total Measurement Time (s)", "2.00"));
            return graphList;
        } else if(fileName.equals(res.getString(string.corn_starch))) {
            graphList = new ArrayList();
            graphList.add(new NIRScanSDK.ScanListManager("Method", "standard_scan"));
            graphList.add(new NIRScanSDK.ScanListManager("Timestamp", "2/3/2015 @ 14:44:27"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range Start (nm)", "850.804993"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range End (nm)", "1779.879761"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Wavelength Points", "85"));
            graphList.add(new NIRScanSDK.ScanListManager("Digital Resolution", "85"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Scans to Average", "1"));
            graphList.add(new NIRScanSDK.ScanListManager("Total Measurement Time (s)", "1.00"));
            return graphList;
        } else if(fileName.equals(res.getString(string.eucerin_lotion))) {
            graphList = new ArrayList();
            graphList.add(new NIRScanSDK.ScanListManager("Method", "standard_scan"));
            graphList.add(new NIRScanSDK.ScanListManager("Timestamp", "4/3/2015 @ 21:32:00"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range Start (nm)", "853.104553"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range End (nm)", "1794.033813"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Wavelength Points", "106"));
            graphList.add(new NIRScanSDK.ScanListManager("Digital Resolution", "106"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Scans to Average", "1"));
            graphList.add(new NIRScanSDK.ScanListManager("Total Measurement Time (s)", "2.00"));
            return graphList;
        } else if(fileName.equals(res.getString(string.flour))) {
            graphList = new ArrayList();
            graphList.add(new NIRScanSDK.ScanListManager("Method", "col8"));
            graphList.add(new NIRScanSDK.ScanListManager("Timestamp", "3/3/2015 @ 15:01:58"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range Start (nm)", "852.15979"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range End (nm)", "1780.73645"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Wavelength Points", "106"));
            graphList.add(new NIRScanSDK.ScanListManager("Digital Resolution", "106"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Scans to Average", "1"));
            graphList.add(new NIRScanSDK.ScanListManager("Total Measurement Time (s)", "2.00"));
            return graphList;
        } else if(fileName.equals(res.getString(string.glucose))) {
            graphList = new ArrayList();
            graphList.add(new NIRScanSDK.ScanListManager("Method", "col8"));
            graphList.add(new NIRScanSDK.ScanListManager("Timestamp", "3/3/2015 @ 15:01:14"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range Start (nm)", "852.15979"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range End (nm)", "1780.73645"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Wavelength Points", "106"));
            graphList.add(new NIRScanSDK.ScanListManager("Digital Resolution", "106"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Scans to Average", "1"));
            graphList.add(new NIRScanSDK.ScanListManager("Total Measurement Time (s)", "3.00"));
            return graphList;
        } else if(fileName.equals(res.getString(string.out_of_spec_aspirin))) {
            graphList = new ArrayList();
            graphList.add(new NIRScanSDK.ScanListManager("Method", "standard_scan"));
            graphList.add(new NIRScanSDK.ScanListManager("Timestamp", "2/3/2015 @ 14:46:55"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range Start (nm)", "850.804993"));
            graphList.add(new NIRScanSDK.ScanListManager("Spectral Range End (nm)", "1779.879761"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Wavelength Points", "85"));
            graphList.add(new NIRScanSDK.ScanListManager("Digital Resolution", "85"));
            graphList.add(new NIRScanSDK.ScanListManager("Number of Scans to Average", "1"));
            graphList.add(new NIRScanSDK.ScanListManager("Total Measurement Time (s)", "1.00"));
            return graphList;
        } else {
            return null;
        }*/
        return null;
    }
}

